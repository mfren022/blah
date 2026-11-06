import { useMemo, useState, type CSSProperties } from 'react';
import type { BookFormat, LibraryEntry, Status } from '../lib/types';
import { FORMATS, STATUSES } from '../lib/types';
import { useEntries, useSettings, useStore } from '../data/store';
import { Pages, useSpread } from '../components/Shell';
import { Rule, Empty, Seg, Stars, Stamp } from '../components/ui';
import { Icon } from '../components/Icon';
import { BookCard, BookRow, authorNames, entryDateLine } from '../components/books';
import { AddManualButton } from './AddManual';
import { SHELF_COLORS } from './BookPage';
import { bookHref, hrefWith, navigate } from '../router';
import { entryGenre, FORMAT_LABELS, STATUS_LABELS } from '../stats/engine';
import { clothFor, downloadFile, hashString, uid } from '../lib/util';
import { formatDate, today } from '../lib/dates';
import type { LibrarySort, LibraryView } from '../data/settings';

const SORTS: { value: LibrarySort; label: string }[] = [
  { value: 'updated', label: 'Recently updated' },
  { value: 'added', label: 'Date added' },
  { value: 'finished', label: 'Date finished' },
  { value: 'title', label: 'Title' },
  { value: 'author', label: 'Author' },
  { value: 'rating', label: 'Your rating' },
  { value: 'pages', label: 'Length' },
];

const lastEnd = (e: LibraryEntry) => [...e.sessions].reverse().find((s) => s.endedAt && s.outcome === 'finished')?.endedAt ?? '';
const surname = (e: LibraryEntry) => (e.book.authors[0]?.name ?? '~').split(' ').pop()!.toLowerCase();

export function sortEntries(list: LibraryEntry[], sort: LibrarySort): LibraryEntry[] {
  const s = [...list];
  switch (sort) {
    case 'added':
      return s.sort((a, b) => b.addedAt.localeCompare(a.addedAt));
    case 'finished':
      return s.sort((a, b) => lastEnd(b).localeCompare(lastEnd(a)) || b.updatedAt.localeCompare(a.updatedAt));
    case 'title':
      return s.sort((a, b) => a.book.title.replace(/^(the|a|an) /i, '').localeCompare(b.book.title.replace(/^(the|a|an) /i, '')));
    case 'author':
      return s.sort((a, b) => surname(a).localeCompare(surname(b)) || a.book.title.localeCompare(b.book.title));
    case 'rating':
      return s.sort((a, b) => (b.rating ?? 0) - (a.rating ?? 0) || a.book.title.localeCompare(b.book.title));
    case 'pages':
      return s.sort((a, b) => (b.pageCount ?? 0) - (a.pageCount ?? 0));
    default:
      return s.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }
}

export function LibraryPage({ params }: { params: URLSearchParams }) {
  const { state, dispatch } = useStore();
  const settings = useSettings();
  const entries = useEntries();
  const status = params.get('status') as Status | null;
  const shelf = params.get('shelf');
  const [q, setQ] = useState(params.get('q') ?? '');
  const [genre, setGenre] = useState('');
  const [format, setFormat] = useState<BookFormat | ''>('');
  const [minRating, setMinRating] = useState(0);
  const [newShelf, setNewShelf] = useState('');

  const spread = useSpread();
  const view = settings.libraryView;
  const sort = settings.librarySort;

  const scoped = entries.filter((e) => (!status || e.status === status) && (!shelf || e.shelves.includes(shelf)));
  const genres = useMemo(() => [...new Set(entries.map(entryGenre))].sort(), [entries]);
  const list = sortEntries(
    scoped.filter((e) => {
      if (genre && entryGenre(e) !== genre) return false;
      if (format && (e.format ?? 'other') !== format) return false;
      if (minRating && (e.rating ?? 0) < minRating) return false;
      if (q.trim()) {
        const hay = `${e.book.title} ${e.book.subtitle ?? ''} ${authorNames(e.book)} ${(e.book.subjects ?? []).join(' ')} ${e.notes ?? ''}`.toLowerCase();
        if (!q.toLowerCase().split(/\s+/).every((w) => hay.includes(w))) return false;
      }
      return true;
    }),
    sort,
  );

  const counts = Object.fromEntries(STATUSES.map((s) => [s, entries.filter((e) => e.status === s).length])) as Record<Status, number>;
  const title = status ? STATUS_LABELS[status] : shelf ? state.shelves.find((s) => s.id === shelf)?.name ?? 'Shelf' : 'All books';
  const filtersOn = !!(genre || format || minRating || q.trim());

  const go = (p: Record<string, string | undefined>) => navigate(hrefWith('#/library', p), { replace: true });

  const searchBox = (
    <div className="searchbox">
      <Icon name="search" />
      <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Title, author, subject, notes…" aria-label="Search your library" />
    </div>
  );
  const filterGrid = (
          <div className="form-grid" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))' }}>
            <label className="field">
              <span>Genre</span>
              <select className="select" value={genre} onChange={(e) => setGenre(e.target.value)}>
                <option value="">Any</option>
                {genres.map((g) => (
                  <option key={g}>{g}</option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Format</span>
              <select className="select" value={format} onChange={(e) => setFormat(e.target.value as BookFormat | '')}>
                <option value="">Any</option>
                {FORMATS.map((f) => (
                  <option key={f} value={f}>
                    {FORMAT_LABELS[f]}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Rated at least</span>
              <select className="select" value={minRating} onChange={(e) => setMinRating(Number(e.target.value))}>
                <option value={0}>Any</option>
                {[1, 2, 3, 3.5, 4, 4.5, 5].map((r) => (
                  <option key={r} value={r}>
                    {r} ★
                  </option>
                ))}
              </select>
            </label>
          </div>
  );
  const tools = (
    <div className="row">
      <a className="btn btn--sm" href="#/browse">
        <Icon name="search" /> Find books
      </a>
      <AddManualButton label="Add by hand" />
      <button type="button" className="btn btn--sm btn--ghost" onClick={() => exportCsv(list, state.shelves)} disabled={!list.length}>
        <Icon name="download" /> CSV
      </button>
    </div>
  );

  const toc = (
    <>
      <Rule level="page">Library</Rule>
      <nav className="toc" aria-label="Shelves">
        <TocLine label="All books" count={entries.length} active={!status && !shelf} onClick={() => go({})} />
        {STATUSES.map((s) => (
          <TocLine key={s} label={STATUS_LABELS[s]} count={counts[s]} active={status === s} onClick={() => go({ status: s })} dot={`var(--status-${s})`} />
        ))}
      </nav>

      <Rule level="minor" as="h2">
        Your shelves
      </Rule>
      <nav className="toc" aria-label="Custom shelves">
        {state.shelves.map((s) => (
          <TocLine key={s.id} label={s.name} count={entries.filter((e) => e.shelves.includes(s.id)).length} active={shelf === s.id} onClick={() => go({ shelf: s.id })} dot={s.color} />
        ))}
        {state.shelves.length === 0 && <p className="small muted ui">No custom shelves yet.</p>}
      </nav>
      <form
        className="row toc-add"
        onSubmit={(e) => {
          e.preventDefault();
          const name = newShelf.trim();
          if (!name) return;
          const id = `shelf-${uid()}`;
          dispatch({ type: 'createShelf', shelf: { id, name, color: SHELF_COLORS[state.shelves.length % SHELF_COLORS.length], createdAt: today() } });
          setNewShelf('');
          go({ shelf: id });
        }}
      >
        <input className="input" value={newShelf} onChange={(e) => setNewShelf(e.target.value)} placeholder="New shelf name" aria-label="New shelf name" />
        <button type="submit" className="btn btn--sm" disabled={!newShelf.trim()}>
          <Icon name="plus" /> Add shelf
        </button>
      </form>
      <p className="tiny faint ui">Rename, recolour or delete shelves in Settings → Shelves.</p>

      <Rule level="minor" as="h2">
        Filter
      </Rule>
      <div className="stack stack--tight">
        {searchBox}
        {filterGrid}
        {filtersOn && (
          <button type="button" className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start' }} onClick={() => (setQ(''), setGenre(''), setFormat(''), setMinRating(0))}>
            <Icon name="x" /> Clear filters
          </button>
        )}
      </div>

      <Rule level="minor" as="h2">
        Add & export
      </Rule>
      {tools}
    </>
  );

  const shelfView = (
    <>
      <Rule level="section" sub={`${list.length} ${list.length === 1 ? 'book' : 'books'}${filtersOn ? ' (filtered)' : ''}`}>
        {title}
      </Rule>
      <div className="row row--between library-bar">
        <Seg<LibraryView>
          label="View"
          size="sm"
          value={view}
          onChange={(v) => dispatch({ type: 'updateSettings', patch: { libraryView: v } })}
          options={[
            { value: 'covers', label: 'Covers', icon: 'grid' },
            { value: 'list', label: 'List', icon: 'list' },
            { value: 'spines', label: 'Shelf', icon: 'spines' },
          ]}
        />
        <label className="row small ui">
          <span className="muted">Sort</span>
          <select className="select" style={{ width: 'auto' }} value={sort} onChange={(e) => dispatch({ type: 'updateSettings', patch: { librarySort: e.target.value as LibrarySort } })}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {entries.length === 0 ? (
        <Empty
          icon="shelf"
          title="Your library is empty"
          action={
            <>
              <a className="btn btn--primary" href="#/browse">
                <Icon name="search" /> Browse books
              </a>
              <AddManualButton />
            </>
          }
        >
          Find books on Open Library and shelve them as Want to Read, Currently Reading, Finished or Did Not Finish.
        </Empty>
      ) : list.length === 0 ? (
        <Empty icon="filter" title="Nothing on this shelf">
          {filtersOn ? 'No books match these filters.' : 'Books you shelve here will appear on this page.'}
        </Empty>
      ) : view === 'list' ? (
        <div className="book-list">
          {list.map((e) => (
            <BookRow key={e.id} entry={e} fmt={settings.dateFormat} from="library" />
          ))}
        </div>
      ) : view === 'spines' ? (
        <SpineShelves list={list} grouped={!status && !shelf && sort === 'updated'} />
      ) : (
        <div className="book-grid">
          {list.map((e) => (
            <BookCard
              key={e.id}
              entry={e}
              book={e.book}
              from="library"
              meta={
                <>
                  <span>{entryDateLine(e, settings.dateFormat)}</span>
                  {e.rating ? <Stars value={e.rating} size="0.72rem" /> : null}
                </>
              }
            />
          ))}
        </div>
      )}
    </>
  );

  if (spread) return <Pages title="Library" tab="library" verso={toc} recto={shelfView} />;

  const compact = (
    <>
      <Rule level="page">Library</Rule>
      <div className="shelf-picker" role="group" aria-label="Shelves">
        <button type="button" className="chip" aria-pressed={!status && !shelf} onClick={() => go({})}>
          All <span className="nums faint">{entries.length}</span>
        </button>
        {STATUSES.map((s) => (
          <button key={s} type="button" className="chip" aria-pressed={status === s} onClick={() => go({ status: s })}>
            <span className="chip__dot" style={{ background: `var(--status-${s})` }} />
            {STATUS_LABELS[s]} <span className="nums faint">{counts[s]}</span>
          </button>
        ))}
        {state.shelves.map((s) => (
          <button key={s.id} type="button" className="chip" aria-pressed={shelf === s.id} onClick={() => go({ shelf: s.id })}>
            <span className="chip__dot" style={{ background: s.color }} />
            {s.name}
          </button>
        ))}
      </div>
      {searchBox}
      <details className="fold" style={{ margin: '10px 0 18px' }} open={filtersOn && !q.trim()}>
        <summary>Filters, new shelves & export</summary>
        <div className="stack stack--tight" style={{ marginTop: 10 }}>
          {filterGrid}
          {filtersOn && (
            <button type="button" className="btn btn--ghost btn--sm" style={{ alignSelf: 'flex-start' }} onClick={() => (setQ(''), setGenre(''), setFormat(''), setMinRating(0))}>
              <Icon name="x" /> Clear filters
            </button>
          )}
          {tools}
        </div>
      </details>
      {shelfView}
    </>
  );
  return <Pages title="Library" tab="library" verso={compact} />;
}

function TocLine({ label, count, active, onClick, dot }: { label: string; count: number; active: boolean; onClick: () => void; dot?: string }) {
  return (
    <button type="button" className="toc__line" aria-current={active ? 'true' : undefined} onClick={onClick}>
      <span className="chip__dot" style={{ background: dot ?? 'var(--ink-faint)' }} />
      <span className="toc__label">{label}</span>
      <span className="toc__leader" aria-hidden />
      <span className="toc__count nums">{count}</span>
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* Spines on wooden shelves                                            */
/* ------------------------------------------------------------------ */

function SpineShelves({ list, grouped }: { list: LibraryEntry[]; grouped: boolean }) {
  if (!grouped) return <Shelf entries={list} />;
  return (
    <div className="stack">
      {(['reading', 'want', 'finished', 'dnf'] as Status[]).map((s) => {
        const group = list.filter((e) => e.status === s);
        if (!group.length) return null;
        return (
          <div key={s}>
            <div className="shelf-label">
              <Stamp status={s} flat label={STATUS_LABELS[s]} />
            </div>
            <Shelf entries={group} />
          </div>
        );
      })}
    </div>
  );
}

function Shelf({ entries }: { entries: LibraryEntry[] }) {
  const settings = useSettings();
  return (
    <div className="bookshelf">
      {entries.map((e) => {
        const h = hashString(e.id);
        const pages = e.pageCount ?? e.book.pagesMedian ?? 280;
        const style = {
          '--w': `${Math.round(Math.min(58, Math.max(18, 12 + pages / 18)))}px`,
          '--h': `${150 + (h % 46)}px`,
          '--cloth': clothFor(e.book.title + (e.book.authors[0]?.name ?? '')),
          '--tilt': h % 11 === 0 ? '-3deg' : '0deg',
        } as CSSProperties;
        const href = bookHref(e.id) + '?from=library';
        return (
          <a
            key={e.id}
            className={`spine-book spine-book--${e.status}`}
            style={style}
            href={href}
            onClick={(ev) => {
              if (ev.metaKey || ev.ctrlKey || ev.button !== 0) return;
              ev.preventDefault();
              navigate(href);
            }}
            title={`${e.book.title} — ${authorNames(e.book)}\n${entryDateLine(e, settings.dateFormat)}`}
          >
            <span className="spine-book__title">{e.book.title}</span>
            <span className="spine-book__author">{(e.book.authors[0]?.name ?? '').split(' ').pop()}</span>
            <span className="spine-book__status" aria-label={STATUS_LABELS[e.status]} />
          </a>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ */

function csvCell(v: unknown): string {
  const s = v === undefined || v === null ? '' : String(v);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function exportCsv(list: LibraryEntry[], shelves: { id: string; name: string }[]) {
  const head = ['Title', 'Author', 'Status', 'Date added', 'Date started', 'Date finished', 'Pages', 'Current page', 'Format', 'Publisher', 'ISBN', 'First published', 'Rating', 'Review', 'Shelves', 'Read count', 'Genre'];
  const rows = list.map((e) => {
    const s = e.sessions.at(-1);
    return [
      e.book.title,
      authorNames(e.book),
      STATUS_LABELS[e.status],
      e.addedAt,
      s?.startedAt ?? '',
      e.status === 'finished' || e.status === 'dnf' ? s?.endedAt ?? '' : '',
      e.pageCount ?? '',
      s?.progress.at(-1)?.page ?? '',
      e.format ? FORMAT_LABELS[e.format] : '',
      e.edition?.publisher ?? '',
      e.edition?.isbn13 ?? e.edition?.isbn10 ?? '',
      e.book.firstPublishYear ?? '',
      e.rating ?? '',
      e.review ?? '',
      e.shelves.map((id) => shelves.find((x) => x.id === id)?.name ?? id).join('; '),
      e.sessions.filter((x) => x.outcome === 'finished').length,
      entryGenre(e),
    ];
  });
  const csv = [head, ...rows].map((r) => r.map(csvCell).join(',')).join('\n');
  downloadFile(`marginalia-library-${formatDate(today(), 'iso')}.csv`, csv, 'text/csv');
}
