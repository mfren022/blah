import { useEffect, useMemo, useState } from 'react';
import type { BookFormat, BookSnapshot, EditionInfo, LibraryEntry, ReadSession, SessionOutcome, Status } from '../lib/types';
import { FORMATS, STATUSES } from '../lib/types';
import { useEntry, useSettings, useStore } from '../data/store';
import { Pages } from '../components/Shell';
import { Rule, Leaf, Stars, StarInput, Empty } from '../components/ui';
import { Cover } from '../components/Cover';
import { Icon } from '../components/Icon';
import { STATUS_ICONS, useBookActions } from '../components/books';
import { ReadingCard } from './ReadingPage';
import { useAsync } from '../lib/useAsync';
import { loadEditions, loadRatings, loadReaders, loadWork, medianPages, seenResult, snapshotFrom, sourceOf } from '../api/catalog';
import { authorPhotoUrl, languageName, workUrl, type AuthorDetails, type RatingsSummary, type ShelfCounts } from '../api/openlibrary';
import { formatDate, today } from '../lib/dates';
import { formatNumber, hashString, plural, uid } from '../lib/util';
import { GENRE_NAMES, primaryGenre } from '../lib/genres';
import { FORMAT_LABELS, STATUS_LABELS, sessionDays } from '../stats/engine';
import { formatFromEdition } from '../data/reducer';
import { hrefWith, navigate } from '../router';
import { isSampleId } from '../data/sample';
import { useMode } from '../components/Shell';
import { useToast } from '../components/Toasts';
import { chartSingleColor } from '../stats/colors';

export function BookPage({ id, params }: { id: string; params: URLSearchParams }) {
  const entry = useEntry(id);
  const { dispatch } = useStore();
  const settings = useSettings();
  const known: BookSnapshot | undefined = entry?.book ?? (seenResult(id) ? snapshotFrom(seenResult(id)!) : undefined);
  const source = sourceOf(id);
  const online = settings.onlineLookup || source !== 'openlibrary';

  const work = useAsync(online ? `work:${id}` : null, () => loadWork(id, known));
  const editions = useAsync(online ? `ed:${id}` : null, () => loadEditions(id, entry));
  const ratings = useAsync(online ? `rt:${id}` : null, () => loadRatings(id));
  const readers = useAsync(online ? `rd:${id}` : null, () => loadReaders(id));

  const snapshot: BookSnapshot | undefined = useMemo(() => {
    const base = work.data?.snapshot ?? known;
    if (!base) return undefined;
    const pages = base.pagesMedian ?? (editions.data ? medianPages(editions.data.editions) : undefined);
    return { ...base, pagesMedian: pages, editionCount: base.editionCount ?? editions.data?.total };
  }, [work.data, known, editions.data]);

  // Keep the library's cached copy of the book fresh (cover, subjects, authors…).
  useEffect(() => {
    if (!entry || !work.data || source !== 'openlibrary') return;
    const fresh = work.data.snapshot;
    const patch: Partial<BookSnapshot> = {};
    if (!entry.book.coverId && fresh.coverId) patch.coverId = fresh.coverId;
    if (!entry.book.subjects?.length && fresh.subjects?.length) patch.subjects = fresh.subjects;
    if (!entry.book.firstPublishYear && fresh.firstPublishYear) patch.firstPublishYear = fresh.firstPublishYear;
    if (!entry.book.authors.length && fresh.authors.length) patch.authors = fresh.authors;
    if (!entry.book.pagesMedian && snapshot?.pagesMedian) patch.pagesMedian = snapshot.pagesMedian;
    if (Object.keys(patch).length) dispatch({ type: 'updateBook', id, book: patch });
  }, [entry, work.data, snapshot?.pagesMedian, source, id, dispatch]);

  if (!snapshot) {
    const loading = work.loading;
    return (
      <Pages
        title="Book"
        tab="book"
        verso={
          loading ? (
            <div className="stack" aria-busy>
              <div className="skeleton" style={{ height: 34, width: '60%', margin: '0 auto' }} />
              <div className="skeleton" style={{ height: 300, width: 200, margin: '20px auto' }} />
            </div>
          ) : (
            <Empty
              icon="book"
              title={work.error ? 'This book could not be loaded' : 'Book not found'}
              action={
                <>
                  <button type="button" className="btn" onClick={() => history.back()}>
                    <Icon name="left" /> Go back
                  </button>
                  <a className="btn" href="#/browse">
                    Browse
                  </a>
                </>
              }
            >
              {work.error?.message ?? (!settings.onlineLookup ? 'Online lookups are switched off in Settings.' : '')}
            </Empty>
          )
        }
      />
    );
  }

  const from = params.get('from');
  const back = (
    <div className="row row--between book-topbar">
      <button type="button" className="btn btn--ghost btn--sm" onClick={() => (history.length > 1 ? history.back() : navigate(`#/${from ?? 'library'}`))}>
        <Icon name="left" /> Back
      </button>
      {source === 'openlibrary' && (
        <a className="btn btn--ghost btn--sm" href={workUrl(id.replace(/^ol:/, ''))} target="_blank" rel="noreferrer">
          Open Library <Icon name="external" />
        </a>
      )}
      {isSampleId(id) && <span className="chip">Sample book · fictional</span>}
    </div>
  );

  const authors = work.data?.authors ?? [];
  const w = work.data?.work;

  const verso = (
    <>
      {back}
      <div className="book-hero">
        <Cover book={snapshot} size="xl" label />
        <Rule level="page" className="book-hero__title">
          {snapshot.title}
        </Rule>
        {snapshot.subtitle && <p className="book-hero__subtitle">{snapshot.subtitle}</p>}
        <p className="book-hero__by">
          by{' '}
          {snapshot.authors.length
            ? snapshot.authors.map((a, i) => (
                <span key={a.name + i}>
                  {i > 0 && (i === snapshot.authors.length - 1 ? ' & ' : ', ')}
                  <a href={hrefWith('#/browse', { q: a.name, in: 'author' })}>{a.name}</a>
                </span>
              ))
            : 'Unknown author'}
        </p>
        <BookFacts snapshot={snapshot} ratings={ratings.data ?? undefined} editionsTotal={editions.data?.total} />
      </div>

      {entry ? <YourCopy entry={entry} editions={editions.data?.editions ?? []} /> : <NotShelved book={snapshot} />}
      {entry?.status === 'reading' && (
        <div className="section">
          <ReadingCard entry={entry} compact />
        </div>
      )}
      {entry && <History entry={entry} />}
    </>
  );

  const recto = (
    <>
      <section className="section" aria-labelledby="synopsis">
        <Rule level="section" id="synopsis">
          Synopsis
        </Rule>
        {work.loading && !w ? (
          <div className="stack stack--tight">
            {[90, 96, 88, 70].map((x, i) => (
              <div key={i} className="skeleton" style={{ height: 14, width: `${x}%` }} />
            ))}
          </div>
        ) : w?.description ? (
          <Synopsis text={w.description} />
        ) : (
          <p className="muted small ui">{source === 'manual' ? 'You added this book by hand — write your own notes below.' : 'No synopsis on Open Library yet.'}</p>
        )}
        {w && (w.subjects.length > 0 || w.places.length > 0 || w.people.length > 0 || w.times.length > 0) && (
          <div className="subjects">
            {w.subjects.length > 0 && <SubjectRow label="Subjects" items={w.subjects} />}
            {w.people.length > 0 && <SubjectRow label="Characters" items={w.people} plain />}
            {w.places.length > 0 && <SubjectRow label="Places" items={w.places} plain />}
            {w.times.length > 0 && <SubjectRow label="Times" items={w.times} plain />}
          </div>
        )}
        {w?.excerpt && (
          <blockquote className="excerpt">
            <p>{w.excerpt}</p>
            <cite>First sentence</cite>
          </blockquote>
        )}
      </section>

      <Reviews entry={entry} book={snapshot} ratings={ratings.data ?? undefined} ratingsLoading={ratings.loading} readers={readers.data ?? undefined} />

      <Editions id={id} entry={entry} book={snapshot} editions={editions.data?.editions} total={editions.data?.total} loading={editions.loading} error={editions.error} />

      {entry && <Notes entry={entry} />}

      {authors.length > 0 && authors.some((a) => a.bio || a.birthDate || a.photoId) && <Authors authors={authors} />}
    </>
  );

  return <Pages title={snapshot.title} tab="book" verso={verso} recto={recto} seed={hashString(id)} />;
}

/* ------------------------------------------------------------------ */

function BookFacts({ snapshot, ratings, editionsTotal }: { snapshot: BookSnapshot; ratings?: RatingsSummary; editionsTotal?: number }) {
  const settings = useSettings();
  return (
    <dl className="facts book-facts">
      {snapshot.firstPublishYear && (
        <>
          <dt>First published</dt>
          <dd>{snapshot.firstPublishYear}</dd>
        </>
      )}
      {snapshot.pagesMedian && (
        <>
          <dt>Length</dt>
          <dd>
            about {formatNumber(snapshot.pagesMedian)} pages <span className="faint">(median of editions)</span>
          </dd>
        </>
      )}
      {(editionsTotal ?? snapshot.editionCount) ? (
        <>
          <dt>Editions</dt>
          <dd>
            <a href="#editions" onClick={(e) => (e.preventDefault(), document.getElementById('editions')?.scrollIntoView({ behavior: 'smooth' }))}>
              {plural(editionsTotal ?? snapshot.editionCount ?? 0, 'edition')}
            </a>
          </dd>
        </>
      ) : null}
      {ratings?.average ? (
        <>
          <dt>Readers rate it</dt>
          <dd className="row" style={{ gap: 6 }}>
            <Stars value={ratings.average} size="0.9rem" /> <span className="nums">{ratings.average.toFixed(2)}</span>
            <span className="faint">({formatNumber(ratings.count)})</span>
          </dd>
        </>
      ) : null}
      <dt>Genre</dt>
      <dd>{primaryGenre(snapshot.subjects)}</dd>
      {settings.onlineLookup && snapshot.source === 'openlibrary' && snapshot.workKey && (
        <>
          <dt>Work ID</dt>
          <dd className="nums">{snapshot.workKey}</dd>
        </>
      )}
    </dl>
  );
}

function Synopsis({ text }: { text: string }) {
  const paras = text.split(/\n{2,}|\n(?=[A-Z“"])/).map((p) => p.trim()).filter(Boolean);
  const long = text.length > 900;
  const [open, setOpen] = useState(!long);
  const shown = open ? paras : paras.slice(0, 2);
  return (
    <div className="prose synopsis">
      {shown.map((p, i) => (
        <p key={i} className={i === 0 ? 'dropcap' : undefined}>
          {p}
        </p>
      ))}
      {long && (
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => setOpen((o) => !o)}>
          {open ? 'Show less' : 'Read the full synopsis'}
        </button>
      )}
    </div>
  );
}

function SubjectRow({ label, items, plain }: { label: string; items: string[]; plain?: boolean }) {
  return (
    <div className="subject-row">
      <span className="field__label">{label}</span>
      <div className="chips">
        {items.slice(0, 12).map((s) =>
          plain ? (
            <span key={s} className="chip">
              {s}
            </span>
          ) : (
            <a key={s} className="chip" href={hrefWith('#/browse', { subject: s.toLowerCase().replace(/\s+/g, '_') })}>
              {s}
            </a>
          ),
        )}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Shelf controls                                                      */
/* ------------------------------------------------------------------ */

function NotShelved({ book }: { book: BookSnapshot }) {
  const actions = useBookActions();
  return (
    <Leaf title="Add to your shelves">
      <div className="status-grid">
        {STATUSES.map((s) => (
          <button key={s} type="button" className={`status-btn status-btn--${s}`} onClick={() => actions.add(book, s)}>
            <Icon name={STATUS_ICONS[s]} />
            <span>{STATUS_LABELS[s]}</span>
          </button>
        ))}
      </div>
      <p className="tiny faint ui" style={{ margin: '10px 0 0' }}>
        Dates are recorded as today; you can change them afterwards.
      </p>
    </Leaf>
  );
}

function statusDateLabel(s: Status) {
  return s === 'want' ? 'On the list since' : s === 'reading' ? 'Started' : s === 'finished' ? 'Finished' : 'Stopped';
}

function YourCopy({ entry, editions }: { entry: LibraryEntry; editions: EditionInfo[] }) {
  const { state, dispatch } = useStore();
  const settings = useSettings();
  const actions = useBookActions();
  const [newShelf, setNewShelf] = useState('');
  const last = entry.sessions.at(-1);
  const statusDate = entry.status === 'want' ? entry.queuedAt ?? entry.addedAt : entry.status === 'reading' ? last?.startedAt : last?.endedAt;
  const ed = entry.edition;

  const setStatusDate = (d: string) => {
    if (!d) return;
    if (entry.status === 'want') dispatch({ type: 'updateEntry', id: entry.id, patch: { queuedAt: d } });
    else if (last) dispatch({ type: 'updateSession', id: entry.id, sessionId: last.id, patch: entry.status === 'reading' ? { startedAt: d } : { endedAt: d } });
  };

  return (
    <Leaf title="On your shelves" sub={`In your library since ${formatDate(entry.addedAt, settings.dateFormat)}`} className="section">
      <div className="status-grid" role="radiogroup" aria-label="Shelf">
        {STATUSES.map((s) => (
          <button
            key={s}
            type="button"
            role="radio"
            aria-checked={entry.status === s}
            className={`status-btn status-btn--${s}`}
            onClick={() => entry.status !== s && actions.setStatus(entry.id, s)}
          >
            <Icon name={STATUS_ICONS[s]} />
            <span>{STATUS_LABELS[s]}</span>
          </button>
        ))}
      </div>

      <div className="form-grid" style={{ marginTop: 14 }}>
        <label className="field">
          <span>{statusDateLabel(entry.status)}</span>
          <input className="input" type="date" value={statusDate ?? ''} max={today()} onChange={(e) => setStatusDate(e.target.value)} />
        </label>
        {entry.status !== 'want' && entry.status !== 'reading' && last && (
          <label className="field">
            <span>Started</span>
            <input
              className="input"
              type="date"
              value={last.startedAt ?? ''}
              max={last.endedAt ?? today()}
              onChange={(e) => dispatch({ type: 'updateSession', id: entry.id, sessionId: last.id, patch: { startedAt: e.target.value || null } })}
            />
          </label>
        )}
        <label className="field">
          <span>Added to library</span>
          <input className="input" type="date" value={entry.addedAt} max={today()} onChange={(e) => e.target.value && dispatch({ type: 'updateEntry', id: entry.id, patch: { addedAt: e.target.value } })} />
        </label>
      </div>

      <Rule level="minor" as="h3">
        Your copy
      </Rule>
      <div className="form-grid">
        <label className="field" style={{ gridColumn: '1 / -1' }}>
          <span>Edition</span>
          <select
            className="select"
            value={ed?.key ?? ''}
            onChange={(e) => {
              const pick = editions.find((x) => x.key === e.target.value) ?? (ed?.key === e.target.value ? ed : undefined);
              dispatch({ type: 'setEdition', id: entry.id, edition: pick });
            }}
          >
            <option value="">Not chosen</option>
            {ed && !editions.some((x) => x.key === ed.key) && <option value={ed.key}>{editionLabel(ed)}</option>}
            {editions.map((x) => (
              <option key={x.key} value={x.key}>
                {editionLabel(x)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Pages in your copy</span>
          <input
            className="input"
            type="number"
            min={1}
            inputMode="numeric"
            value={entry.pageCount ?? ''}
            placeholder={entry.book.pagesMedian ? String(entry.book.pagesMedian) : ''}
            onChange={(e) => dispatch({ type: 'updateEntry', id: entry.id, patch: { pageCount: Number(e.target.value) > 0 ? Math.round(Number(e.target.value)) : undefined } })}
          />
        </label>
        <label className="field">
          <span>Format</span>
          <select className="select" value={entry.format ?? ''} onChange={(e) => dispatch({ type: 'updateEntry', id: entry.id, patch: { format: (e.target.value || undefined) as BookFormat | undefined } })}>
            <option value="">—</option>
            {FORMATS.map((f) => (
              <option key={f} value={f}>
                {FORMAT_LABELS[f]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Genre (for statistics)</span>
          <select className="select" value={entry.genre ?? ''} onChange={(e) => dispatch({ type: 'updateEntry', id: entry.id, patch: { genre: e.target.value || undefined } })}>
            <option value="">Automatic: {primaryGenre(entry.book.subjects)}</option>
            {GENRE_NAMES.map((g) => (
              <option key={g}>{g}</option>
            ))}
          </select>
        </label>
      </div>

      <Rule level="minor" as="h3">
        Custom shelves
      </Rule>
      <div className="chips">
        {state.shelves.map((s) => (
          <button key={s.id} type="button" className="chip" aria-pressed={entry.shelves.includes(s.id)} onClick={() => dispatch({ type: 'toggleShelf', id: entry.id, shelfId: s.id })}>
            <span className="chip__dot" style={{ background: s.color }} />
            {s.name}
          </button>
        ))}
        <form
          className="chip chip--input"
          onSubmit={(e) => {
            e.preventDefault();
            const name = newShelf.trim();
            if (!name) return;
            const existing = state.shelves.find((s) => s.name.toLowerCase() === name.toLowerCase());
            const shelfId = existing?.id ?? `shelf-${uid()}`;
            if (!existing) dispatch({ type: 'createShelf', shelf: { id: shelfId, name, color: SHELF_COLORS[state.shelves.length % SHELF_COLORS.length], createdAt: today() } });
            if (!entry.shelves.includes(shelfId)) dispatch({ type: 'toggleShelf', id: entry.id, shelfId });
            setNewShelf('');
          }}
        >
          <Icon name="plus" />
          <input value={newShelf} onChange={(e) => setNewShelf(e.target.value)} placeholder="New shelf" aria-label="New shelf name" size={10} />
        </form>
      </div>

      <div className="row row--end" style={{ marginTop: 16 }}>
        <button type="button" className="btn btn--danger btn--ghost btn--sm" onClick={() => (actions.remove(entry.id), navigate('#/library', { replace: true }))}>
          <Icon name="trash" /> Remove from library
        </button>
      </div>
    </Leaf>
  );
}

export const SHELF_COLORS = ['#2e6b52', '#2f5b9a', '#8e4f1f', '#6d4fb0', '#a33a3a', '#237a7f', '#b8923a', '#5a5226'];

function editionLabel(e: EditionInfo): string {
  return [e.year ?? e.publishDate, e.publisher, e.format, e.pages ? `${e.pages} pp.` : 'pages unknown'].filter(Boolean).join(' · ');
}

/* ------------------------------------------------------------------ */
/* Reading history (read-throughs with editable dates)                 */
/* ------------------------------------------------------------------ */

function History({ entry }: { entry: LibraryEntry }) {
  const { dispatch } = useStore();
  const settings = useSettings();
  const [adding, setAdding] = useState(false);
  const [start, setStart] = useState('');
  const [end, setEnd] = useState(today());
  const [outcome, setOutcome] = useState<'finished' | 'dnf'>('finished');
  const sessions = [...entry.sessions].reverse();

  return (
    <Leaf title="Reading history" sub={entry.sessions.length ? plural(entry.sessions.length, 'read-through') : 'Not started yet'} className="section">
      {sessions.length === 0 && <p className="small muted ui">Start reading, or log a read from the past.</p>}
      <ol className="history">
        {sessions.map((s, i) => (
          <SessionRow key={s.id} entry={entry} s={s} n={entry.sessions.length - i} isLatest={i === 0} fmt={settings.dateFormat} />
        ))}
      </ol>
      {adding ? (
        <form
          className="history-add"
          onSubmit={(e) => {
            e.preventDefault();
            dispatch({ type: 'addSession', id: entry.id, startedAt: start || undefined, endedAt: end || today(), outcome });
            setAdding(false);
            setStart('');
          }}
        >
          <label className="field">
            <span>Started</span>
            <input className="input" type="date" value={start} max={end || today()} onChange={(e) => setStart(e.target.value)} />
          </label>
          <label className="field">
            <span>{outcome === 'finished' ? 'Finished' : 'Stopped'}</span>
            <input className="input" type="date" value={end} max={today()} min={start || undefined} onChange={(e) => setEnd(e.target.value)} required />
          </label>
          <label className="field">
            <span>Outcome</span>
            <select className="select" value={outcome} onChange={(e) => setOutcome(e.target.value as 'finished' | 'dnf')}>
              <option value="finished">Finished</option>
              <option value="dnf">Did not finish</option>
            </select>
          </label>
          <div className="row">
            <button type="submit" className="btn btn--primary btn--sm">
              Save
            </button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setAdding(false)}>
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <button type="button" className="btn btn--sm" onClick={() => setAdding(true)} style={{ marginTop: 8 }}>
          <Icon name="plus" /> Log a past read
        </button>
      )}
    </Leaf>
  );
}

function SessionRow({ entry, s, n, isLatest, fmt }: { entry: LibraryEntry; s: ReadSession; n: number; isLatest: boolean; fmt: Parameters<typeof formatDate>[1] }) {
  const { state, dispatch } = useStore();
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const days = sessionDays(s);
  const lastPage = s.progress.at(-1)?.page;
  const label = s.outcome === 'reading' ? 'Reading now' : s.outcome === 'finished' ? 'Finished' : 'Did not finish';
  return (
    <li className={`history__item history__item--${s.outcome}`}>
      <div className="row row--between">
        <span className="history__title">
          <span className="history__n">Read {n}</span> · {label}
        </span>
        <span className="row" style={{ gap: 2 }}>
          <button type="button" className="btn btn--ghost btn--sm btn--icon" aria-label={editing ? 'Done editing' : 'Edit dates'} aria-pressed={editing} onClick={() => setEditing((x) => !x)}>
            <Icon name={editing ? 'check' : 'edit'} />
          </button>
          <button
            type="button"
            className="btn btn--ghost btn--sm btn--icon"
            aria-label="Delete this read-through"
            onClick={() => {
              const before = state;
              dispatch({ type: 'deleteSession', id: entry.id, sessionId: s.id });
              toast({ message: `Deleted read ${n}${s.progress.length ? ' and its progress log' : ''}`, action: { label: 'Undo', run: () => dispatch({ type: 'hydrate', state: before }) } });
            }}
          >
            <Icon name="trash" />
          </button>
        </span>
      </div>
      {editing ? (
        <div className="form-grid">
          <label className="field">
            <span>Started</span>
            <input className="input" type="date" value={s.startedAt ?? ''} max={s.endedAt ?? today()} onChange={(e) => dispatch({ type: 'updateSession', id: entry.id, sessionId: s.id, patch: { startedAt: e.target.value || null } })} />
          </label>
          {s.outcome !== 'reading' && (
            <label className="field">
              <span>{s.outcome === 'finished' ? 'Finished' : 'Stopped'}</span>
              <input className="input" type="date" value={s.endedAt ?? ''} max={today()} onChange={(e) => e.target.value && dispatch({ type: 'updateSession', id: entry.id, sessionId: s.id, patch: { endedAt: e.target.value } })} />
            </label>
          )}
          <label className="field">
            <span>Outcome</span>
            <select className="select" value={s.outcome} onChange={(e) => dispatch({ type: 'updateSession', id: entry.id, sessionId: s.id, patch: { outcome: e.target.value as SessionOutcome } })}>
              {isLatest && <option value="reading">Reading now</option>}
              <option value="finished">Finished</option>
              <option value="dnf">Did not finish</option>
            </select>
          </label>
        </div>
      ) : (
        <div className="history__dates ui">
          {s.startedAt ? formatDate(s.startedAt, fmt) : 'Start unknown'} → {s.outcome === 'reading' ? 'now' : formatDate(s.endedAt, fmt)}
          {days ? <span className="faint"> · {plural(days, 'day')}</span> : null}
          {s.outcome === 'dnf' && lastPage ? <span className="faint"> · stopped at p. {lastPage}{entry.pageCount ? ` (${Math.round((lastPage / entry.pageCount) * 100)}%)` : ''}</span> : null}
          {s.progress.length ? <span className="faint"> · {plural(s.progress.length, 'log entry', 'log entries')}</span> : null}
        </div>
      )}
    </li>
  );
}

/* ------------------------------------------------------------------ */
/* Reviews & ratings                                                   */
/* ------------------------------------------------------------------ */

function Reviews({ entry, book, ratings, ratingsLoading, readers }: { entry?: LibraryEntry; book: BookSnapshot; ratings?: RatingsSummary; ratingsLoading: boolean; readers?: ShelfCounts }) {
  const { dispatch } = useStore();
  const settings = useSettings();
  const actions = useBookActions();
  const mode = useMode();
  const [draft, setDraft] = useState<string | null>(null);
  const [spoilers, setSpoilers] = useState(!!entry?.reviewSpoilers);
  const [reveal, setReveal] = useState(false);
  const color = chartSingleColor(settings, mode);
  const editing = draft !== null;
  const max = ratings ? Math.max(...ratings.counts, 1) : 1;

  return (
    <section className="section" aria-labelledby="reviews">
      <Rule level="section" id="reviews">
        Ratings & reviews
      </Rule>
      <div className="reviews-grid">
        <div className="leaf">
          <Rule level="panel" as="h3" sub={ratings?.count ? `${formatNumber(ratings.count)} ratings on ${book.source === 'sample' ? 'the sample catalogue' : 'Open Library'}` : undefined}>
            Community
          </Rule>
          {ratingsLoading && !ratings && <div className="skeleton" style={{ height: 110 }} />}
          {ratings && ratings.count > 0 ? (
            <>
              <div className="row" style={{ alignItems: 'baseline', gap: 10 }}>
                <span className="big-number">{ratings.average?.toFixed(2)}</span>
                <Stars value={ratings.average} size="1.05rem" />
              </div>
              <div className="histo" role="table" aria-label="Rating distribution">
                {[4, 3, 2, 1, 0].map((i) => (
                  <div key={i} className="histo__row" role="row">
                    <span role="rowheader" className="histo__label">{i + 1} ★</span>
                    <span className="histo__track" role="cell">
                      <span className="histo__bar" style={{ width: `${(ratings.counts[i] / max) * 100}%`, background: color }} />
                    </span>
                    <span role="cell" className="histo__val nums">
                      {formatNumber(ratings.counts[i])}
                    </span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            !ratingsLoading && <p className="small muted ui">{book.source === 'manual' ? 'Community ratings are only available for books found on Open Library.' : 'No community ratings yet.'}</p>
          )}
          {readers && (readers.want + readers.reading + readers.read > 0) && (
            <div className="readers ui">
              <span><strong className="nums">{formatNumber(readers.read)}</strong> have read it</span>
              <span><strong className="nums">{formatNumber(readers.reading)}</strong> reading</span>
              <span><strong className="nums">{formatNumber(readers.want)}</strong> want to</span>
            </div>
          )}
        </div>

        <div className="leaf review">
          <Rule level="panel" as="h3" sub={entry?.reviewedAt ? `Updated ${formatDate(entry.reviewedAt, settings.dateFormat)}` : undefined}>
            Your review
          </Rule>
          {!entry ? (
            <div>
              <p className="small muted ui">Shelve this book to rate and review it.</p>
              <button type="button" className="btn btn--sm" onClick={() => actions.add(book, 'finished')}>
                <Icon name="check" /> I've read it
              </button>
            </div>
          ) : (
            <>
              <StarInput value={entry.rating} onChange={(v) => dispatch({ type: 'updateEntry', id: entry.id, patch: { rating: v } })} />
              {editing ? (
                <form
                  className="stack stack--tight"
                  style={{ marginTop: 10 }}
                  onSubmit={(e) => {
                    e.preventDefault();
                    dispatch({ type: 'updateEntry', id: entry.id, patch: { review: draft!.trim() || undefined, reviewSpoilers: spoilers || undefined } });
                    setDraft(null);
                  }}
                >
                  <textarea className="textarea" value={draft!} onChange={(e) => setDraft(e.target.value)} rows={6} placeholder="What did you make of it?" aria-label="Your review" autoFocus />
                  <div className="row row--between">
                    <label className="check">
                      <input type="checkbox" checked={spoilers} onChange={(e) => setSpoilers(e.target.checked)} /> Contains spoilers
                    </label>
                    <span className="row">
                      <button type="button" className="btn btn--ghost btn--sm" onClick={() => setDraft(null)}>
                        Cancel
                      </button>
                      <button type="submit" className="btn btn--primary btn--sm">
                        <Icon name="save" /> Save review
                      </button>
                    </span>
                  </div>
                </form>
              ) : entry.review ? (
                <div className="review__text">
                  <div className={`prose ${entry.reviewSpoilers && settings.hideSpoilers && !reveal ? 'spoiler' : ''}`}>
                    {entry.review.split(/\n+/).map((p, i) => (
                      <p key={i}>{p}</p>
                    ))}
                  </div>
                  {entry.reviewSpoilers && settings.hideSpoilers && !reveal && (
                    <button type="button" className="btn btn--sm spoiler__reveal" onClick={() => setReveal(true)}>
                      Contains spoilers — show
                    </button>
                  )}
                  <button type="button" className="btn btn--ghost btn--sm" onClick={() => (setDraft(entry.review ?? ''), setSpoilers(!!entry.reviewSpoilers))}>
                    <Icon name="edit" /> Edit review
                  </button>
                </div>
              ) : (
                <button type="button" className="btn btn--sm" style={{ marginTop: 10 }} onClick={() => setDraft('')}>
                  <Icon name="feather" /> Write a review
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Editions with page counts                                           */
/* ------------------------------------------------------------------ */

type EdSort = 'year' | 'pages' | 'publisher';

function Editions({
  id,
  entry,
  book,
  editions,
  total,
  loading,
  error,
}: {
  id: string;
  entry?: LibraryEntry;
  book: BookSnapshot;
  editions?: EditionInfo[];
  total?: number;
  loading: boolean;
  error?: Error;
}) {
  const { dispatch } = useStore();
  const actions = useBookActions();
  const [sort, setSort] = useState<EdSort>('year');
  const [withPages, setWithPages] = useState(false);
  const [extra, setExtra] = useState<EditionInfo[]>([]);
  const [moreLoading, setMoreLoading] = useState(false);
  const all = [...(editions ?? []), ...extra];
  const list = all
    .filter((e) => !withPages || e.pages)
    .sort((a, b) =>
      sort === 'pages' ? (b.pages ?? -1) - (a.pages ?? -1) : sort === 'publisher' ? (a.publisher ?? '~').localeCompare(b.publisher ?? '~') : (b.year ?? 0) - (a.year ?? 0),
    );
  const pages = all.map((e) => e.pages).filter((p): p is number => !!p);
  return (
    <section className="section" id="editions" aria-labelledby="editions-h">
      <Rule level="section" id="editions-h" sub={total ? `${plural(total, 'edition')}${pages.length ? ` · ${Math.min(...pages)}–${Math.max(...pages)} pages` : ''}` : undefined}>
        Editions
      </Rule>
      {loading && !editions && <div className="skeleton" style={{ height: 160 }} />}
      {error && <p className="small muted ui">Editions could not be loaded: {error.message}</p>}
      {editions && all.length === 0 && <p className="small muted ui">No editions listed.</p>}
      {all.length > 0 && (
        <>
          <div className="row row--between" style={{ marginBottom: 8 }}>
            <label className="check">
              <input type="checkbox" checked={withPages} onChange={(e) => setWithPages(e.target.checked)} /> Only editions with a page count
            </label>
            <label className="row small ui">
              <span className="muted">Sort</span>
              <select className="select" style={{ width: 'auto' }} value={sort} onChange={(e) => setSort(e.target.value as EdSort)}>
                <option value="year">Newest</option>
                <option value="pages">Most pages</option>
                <option value="publisher">Publisher</option>
              </select>
            </label>
          </div>
          <div className="table-wrap">
            <table className="table editions">
              <thead>
                <tr>
                  <th>Year</th>
                  <th>Publisher</th>
                  <th>Format</th>
                  <th className="num">Pages</th>
                  <th>ISBN</th>
                  <th>
                    <span className="sr-only">Use</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {list.map((e) => {
                  const mine = entry?.edition?.key === e.key;
                  return (
                    <tr key={e.key} className={mine ? 'is-selected' : undefined}>
                      <td className="nums">{e.year ?? e.publishDate ?? '—'}</td>
                      <td>{e.publisher ?? '—'}</td>
                      <td>
                        {e.format ?? (formatFromEdition(e) ? FORMAT_LABELS[formatFromEdition(e)!] : '—')}
                        {e.language && <span className="faint tiny" style={{ display: 'block' }}>{languageName(e.language)}</span>}
                      </td>
                      <td className="num">{e.pages ? formatNumber(e.pages) : '—'}</td>
                      <td className="nums tiny">{e.isbn13 ?? e.isbn10 ?? '—'}</td>
                      <td className="num">
                        {mine ? (
                          <span className="tiny" style={{ color: 'var(--accent)' }}>
                            Your copy
                          </span>
                        ) : (
                          <button
                            type="button"
                            className="linklike"
                            onClick={() => (entry ? dispatch({ type: 'setEdition', id: entry.id, edition: e }) : actions.add(book, 'want', { edition: e }))}
                          >
                            {entry ? 'Use this' : 'Want this one'}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {total !== undefined && all.length < total && sourceOf(id) === 'openlibrary' && (
            <button
              type="button"
              className="btn btn--sm"
              style={{ marginTop: 10 }}
              disabled={moreLoading}
              onClick={async () => {
                setMoreLoading(true);
                try {
                  const next = await loadEditions(id, entry, all.length);
                  setExtra((x) => [...x, ...next.editions]);
                } finally {
                  setMoreLoading(false);
                }
              }}
            >
              {moreLoading ? 'Loading…' : `Show more editions (${formatNumber(total - all.length)} more)`}
            </button>
          )}
        </>
      )}
    </section>
  );
}

/* ------------------------------------------------------------------ */

function Notes({ entry }: { entry: LibraryEntry }) {
  const { dispatch } = useStore();
  const [text, setText] = useState(entry.notes ?? '');
  const dirty = text !== (entry.notes ?? '');
  return (
    <section className="section" aria-labelledby="notes">
      <Rule level="section" id="notes" sub="Private — quotes, thoughts, marginalia">
        Notes
      </Rule>
      <textarea className="textarea" rows={5} value={text} onChange={(e) => setText(e.target.value)} aria-label="Notes" placeholder="Jot something in the margin…" />
      <div className="row row--end" style={{ marginTop: 6 }}>
        {dirty && <span className="tiny faint ui">Unsaved</span>}
        <button type="button" className="btn btn--sm" disabled={!dirty} onClick={() => dispatch({ type: 'updateEntry', id: entry.id, patch: { notes: text.trim() || undefined } })}>
          <Icon name="save" /> Save notes
        </button>
      </div>
    </section>
  );
}

function Authors({ authors }: { authors: AuthorDetails[] }) {
  return (
    <section className="section" aria-labelledby="authors">
      <Rule level="section" id="authors">
        {authors.length > 1 ? 'About the authors' : 'About the author'}
      </Rule>
      <div className="stack">
        {authors.map((a) => (
          <div key={a.key || a.name} className="author">
            {a.photoId ? <img className="author__photo" src={authorPhotoUrl(a.photoId, 'M')} alt="" loading="lazy" /> : <div className="author__photo author__photo--blank" aria-hidden><Icon name="user" /></div>}
            <div>
              <h3 className="author__name">
                <a href={hrefWith('#/browse', { q: a.name, in: 'author' })}>{a.name}</a>
              </h3>
              {(a.birthDate || a.deathDate) && (
                <div className="small muted ui">
                  {a.birthDate ?? '?'} – {a.deathDate ?? ''}
                </div>
              )}
              {a.bio && <p className="prose small clamp-3" style={{ marginTop: 6 }}>{a.bio}</p>}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
