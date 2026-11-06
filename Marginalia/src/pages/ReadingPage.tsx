import { useMemo, useState } from 'react';
import type { LibraryEntry } from '../lib/types';
import { useSettings, useStore, useEntries } from '../data/store';
import { Pages } from '../components/Shell';
import { Rule, Leaf, Progress, Empty, Stars } from '../components/ui';
import { Cover } from '../components/Cover';
import { Icon } from '../components/Icon';
import { authorNames, useBookActions, BookCard } from '../components/books';
import { useToast } from '../components/Toasts';
import { currentPage, activeSession } from '../data/reducer';
import { computeOverview, readingForecast, pagesByDay, type StatsContext } from '../stats/engine';
import { dateOf, formatDate, formatDateShort, formatTime, nowLocal, relativeDay, today } from '../lib/dates';
import { formatNumber, plural } from '../lib/util';
import { bookHref, navigate } from '../router';
import { formatFromEdition } from '../data/reducer';
import { FORMAT_LABELS } from '../stats/engine';

export function ReadingPage() {
  const settings = useSettings();
  const entries = useEntries();
  const { state } = useStore();
  const reading = entries.filter((e) => e.status === 'reading').sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  const upNext = entries
    .filter((e) => e.status === 'want')
    .sort((a, b) => (b.queuedAt ?? b.addedAt).localeCompare(a.queuedAt ?? a.addedAt))
    .slice(0, 6);
  const recent = entries
    .filter((e) => e.status === 'finished')
    .sort((a, b) => (b.sessions.at(-1)?.endedAt ?? '').localeCompare(a.sessions.at(-1)?.endedAt ?? ''))
    .slice(0, 4);

  const t = today();
  const ctx: StatsContext = { today: t, weekStart: settings.weekStart, shelves: state.shelves, goalBooks: settings.yearlyBookGoal, goalPages: settings.yearlyPageGoal, mode: 'light' };
  const overview = useMemo(() => computeOverview(entries, ctx), [entries, settings.yearlyBookGoal, settings.yearlyPageGoal]); // eslint-disable-line react-hooks/exhaustive-deps
  const todayPages = useMemo(() => pagesByDay(entries).get(t) ?? 0, [entries, t]);

  const verso = (
    <>
      <Rule level="page">Currently Reading</Rule>
      {reading.length === 0 ? (
        <Empty
          icon="bookmark"
          title="Nothing on the go"
          action={
            <>
              <a className="btn btn--primary" href="#/browse">
                <Icon name="search" /> Find a book
              </a>
              {upNext.length > 0 && (
                <a className="btn" href="#/library?status=want">
                  Pick from Want to Read
                </a>
              )}
            </>
          }
        >
          Start a book and track it here page by page.
        </Empty>
      ) : (
        <div className="stack">
          {reading.map((e) => (
            <ReadingCard key={e.id} entry={e} />
          ))}
        </div>
      )}
    </>
  );

  const goal = overview.goalBooks;
  const recto = (
    <div className="stack">
      <Leaf title="Today" sub={formatDate(t, 'long')}>
        <div className="tiles" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))' }}>
          <div className="tile">
            <div className="tile__label">Pages today</div>
            <div className="tile__value">{formatNumber(Math.round(todayPages))}</div>
          </div>
          <div className="tile">
            <div className="tile__label">
              <Icon name="flame" /> Reading streak
            </div>
            <div className="tile__value">
              {overview.currentStreak}
              <small>{overview.currentStreak === 1 ? 'day' : 'days'}</small>
            </div>
            <div className="tile__note">Longest {plural(overview.longestStreak, 'day')}</div>
          </div>
          <div className="tile">
            <div className="tile__label">Last 30 days</div>
            <div className="tile__value">
              {formatNumber(Math.round(overview.pagesLast30))}
              <small>pages</small>
            </div>
            <div className="tile__note">on {plural(overview.readingDays30, 'day')}</div>
          </div>
        </div>
        {goal.target > 0 && (
          <div style={{ marginTop: 16 }}>
            <div className="row row--between small ui">
              <span>
                <Icon name="target" /> {t.slice(0, 4)} goal: <strong>{goal.done}</strong> of {goal.target} books
              </span>
              <span className="muted">
                {goal.done >= Math.floor(goal.expected) ? `${Math.max(0, goal.done - Math.floor(goal.expected))} ahead of pace` : `${Math.ceil(goal.expected) - goal.done} behind pace`}
              </span>
            </div>
            <div className="meter" style={{ marginTop: 6 }} aria-hidden>
              <div className="meter__fill" style={{ width: `${Math.min(100, (goal.done / goal.target) * 100)}%` }} />
              <div className="meter__mark" style={{ left: `${Math.min(100, (goal.expected / goal.target) * 100)}%` }} title="Where you'd be at an even pace" />
            </div>
          </div>
        )}
      </Leaf>

      <Leaf title="Up next" sub="From your Want to Read shelf">
        {upNext.length === 0 ? (
          <p className="small muted ui">
            Your Want to Read shelf is empty. <a href="#/browse">Browse for something</a>.
          </p>
        ) : (
          <div className="up-next">
            {upNext.map((e) => (
              <UpNextRow key={e.id} entry={e} />
            ))}
          </div>
        )}
      </Leaf>

      {recent.length > 0 && (
        <Leaf title="Recently finished">
          <div className="book-grid book-grid--small">
            {recent.map((e) => (
              <BookCard key={e.id} entry={e} book={e.book} showStamp={false} from="reading" meta={<Stars value={e.rating} size="0.8rem" />} />
            ))}
          </div>
        </Leaf>
      )}
    </div>
  );

  return <Pages title="Currently Reading" tab="reading" verso={verso} recto={recto} />;
}

function UpNextRow({ entry }: { entry: LibraryEntry }) {
  const actions = useBookActions();
  const settings = useSettings();
  return (
    <div className="up-next__row">
      <a href={bookHref(entry.id)} className="up-next__book" onClick={(e) => (e.preventDefault(), navigate(bookHref(entry.id)))}>
        <Cover book={entry.book} size="xs" />
        <span className="stack" style={{ gap: 0, minWidth: 0 }}>
          <span className="book-card__title ellipsis">{entry.book.title}</span>
          <span className="book-card__author ellipsis">
            {authorNames(entry.book)} · added {formatDateShort(entry.queuedAt ?? entry.addedAt, settings.dateFormat)}
          </span>
        </span>
      </a>
      <button type="button" className="btn btn--sm" onClick={() => actions.setStatus(entry.id, 'reading')}>
        <Icon name="play" /> Start
      </button>
    </div>
  );
}

/* ------------------------------------------------------------------ */

export function ReadingCard({ entry, compact }: { entry: LibraryEntry; compact?: boolean }) {
  const { dispatch } = useStore();
  const settings = useSettings();
  const actions = useBookActions();
  const toast = useToast();
  const page = currentPage(entry);
  const total = entry.pageCount;
  const percentMode = settings.progressDisplay === 'percent' && !!total;
  const [draft, setDraft] = useState<string>('');
  const [logDate, setLogDate] = useState<string>(today());
  const [pagesDraft, setPagesDraft] = useState('');
  const session = activeSession(entry);
  const fc = readingForecast(entry, today());
  const pct = total ? Math.round((page / total) * 100) : undefined;

  const shownValue = draft !== '' ? draft : percentMode ? String(pct ?? 0) : String(page);

  const commit = (raw: string) => {
    const n = Number(raw);
    if (!Number.isFinite(n) || raw.trim() === '') return;
    const target = percentMode && total ? Math.round((n / 100) * total) : Math.round(n);
    if (target === page && logDate === today()) {
      setDraft('');
      return;
    }
    const at = logDate === today() ? nowLocal() : `${logDate}T12:00`;
    dispatch({ type: 'logProgress', id: entry.id, page: target, at });
    setDraft('');
    if (total && target >= total && settings.autoFinishPrompt) {
      toast({ message: `Last page of “${entry.book.title}”! Mark it finished?`, action: { label: 'Finished', run: () => actions.setStatus(entry.id, 'finished', logDate) } }, 8000);
    } else {
      toast({ message: `Logged ${percentMode ? `${n}%` : `page ${target}`} of “${entry.book.title}”` });
    }
  };

  const bump = (delta: number) => {
    const base = Number(shownValue) || 0;
    const max = percentMode ? 100 : total ?? 100000;
    setDraft(String(Math.max(0, Math.min(max, base + delta))));
  };

  const logs = session?.progress ?? [];
  const edition = entry.edition;
  const format = entry.format ?? formatFromEdition(edition);

  return (
    <article className="leaf reading-card">
      <a href={bookHref(entry.id)} onClick={(e) => (e.preventDefault(), navigate(bookHref(entry.id) + '?from=reading'))} className="reading-card__cover">
        <Cover book={entry.book} size={compact ? 'sm' : 'md'} />
      </a>
      <div className="reading-card__head">
        <h2 className="reading-card__title">
          <a href={bookHref(entry.id)} onClick={(e) => (e.preventDefault(), navigate(bookHref(entry.id) + '?from=reading'))}>
            {entry.book.title}
          </a>
        </h2>
        <div className="book-card__author">
          {authorNames(entry.book)}
          {format ? ` · ${FORMAT_LABELS[format]}` : ''}
          {edition?.publisher ? ` · ${edition.publisher}${edition.year ? ` ${edition.year}` : ''}` : ''}
        </div>
      </div>
      <div className="reading-card__main">

        {total ? (
          <>
            <div className="reading-card__progress">
              <Progress value={page} max={total} label={`Progress through ${entry.book.title}`} />
              <span className="reading-card__pct nums">{pct}%</span>
            </div>
            <form
              className="reading-card__log"
              onSubmit={(e) => {
                e.preventDefault();
                commit(shownValue);
              }}
            >
              <label className="reading-card__pageinput">
                <span className="small muted">{percentMode ? 'At' : 'Page'}</span>
                <input
                  className="input input--boxed input--num"
                  inputMode="numeric"
                  type="number"
                  min={0}
                  max={percentMode ? 100 : total}
                  value={shownValue}
                  onChange={(e) => setDraft(e.target.value)}
                  aria-label={percentMode ? 'Percent read' : 'Current page'}
                />
                <span className="small muted nums">{percentMode ? '%' : `of ${formatNumber(total)}`}</span>
              </label>
              <span className="btn-group">
                <button type="button" className="btn btn--sm" onClick={() => bump(percentMode ? 5 : 10)} aria-label={percentMode ? 'Add 5 percent' : 'Add 10 pages'}>
                  +{percentMode ? 5 : 10}
                </button>
                <button type="button" className="btn btn--sm" onClick={() => bump(percentMode ? 10 : 25)} aria-label={percentMode ? 'Add 10 percent' : 'Add 25 pages'}>
                  +{percentMode ? 10 : 25}
                </button>
              </span>
              <input
                type="date"
                className="input input--boxed reading-card__date"
                value={logDate}
                max={today()}
                min={session?.startedAt}
                onChange={(e) => setLogDate(e.target.value || today())}
                aria-label="Date of this reading"
              />
              <button type="submit" className="btn btn--primary btn--sm">
                <Icon name="check" /> Log
              </button>
            </form>
          </>
        ) : (
          <form
            className="reading-card__log"
            onSubmit={(e) => {
              e.preventDefault();
              const n = Number(pagesDraft);
              if (n > 0) dispatch({ type: 'updateEntry', id: entry.id, patch: { pageCount: Math.round(n) } });
            }}
          >
            <span className="small muted ui">How many pages does your edition have?</span>
            <input className="input input--boxed input--num" type="number" min={1} value={pagesDraft} onChange={(e) => setPagesDraft(e.target.value)} aria-label="Page count" />
            <button type="submit" className="btn btn--sm">
              Set pages
            </button>
          </form>
        )}

        <div className="reading-card__facts ui">
          <span>
            <Icon name="calendar" /> Started {formatDateShort(session?.startedAt, settings.dateFormat)} · day {fc.daysReading}
          </span>
          {fc.pagesPerDay ? <span>{formatNumber(fc.pagesPerDay, 1)} pages/day</span> : null}
          {fc.eta && fc.daysLeft !== undefined && fc.daysLeft > 0 ? (
            <span title="At your average pace so far">
              <Icon name="clock" /> finish ≈ {formatDateShort(fc.eta, settings.dateFormat)}
            </span>
          ) : null}
          {total ? <span>{formatNumber(Math.max(0, total - page))} pages left</span> : null}
        </div>

        <div className="row reading-card__actions">
          <button type="button" className="btn btn--sm" onClick={() => actions.setStatus(entry.id, 'finished')}>
            <Icon name="check" /> Finished
          </button>
          <button type="button" className="btn btn--sm btn--ghost" onClick={() => actions.setStatus(entry.id, 'dnf')}>
            <Icon name="stop" /> Did not finish
          </button>
          <span className="grow" />
          {logs.length > 0 && <Sparkline entry={entry} />}
        </div>

        {logs.length > 0 && !compact && (
          <details className="fold reading-card__history">
            <summary>Progress log ({logs.length})</summary>
            <ol className="log-list">
              {[...logs].reverse().map((p, i, arr) => {
                const prev = arr[i + 1]?.page ?? 0;
                return (
                  <li key={p.id}>
                    <span className="nums">
                      {relativeDay(dateOf(p.at)) === 'today' || relativeDay(dateOf(p.at)) === 'yesterday' ? relativeDay(dateOf(p.at)) : formatDateShort(dateOf(p.at), settings.dateFormat)}
                      {formatTime(p.at) && <span className="faint"> · {formatTime(p.at)}</span>}
                    </span>
                    <span className="nums">
                      p. {p.page}
                      {p.page - prev > 0 && <span className="faint"> (+{p.page - prev})</span>}
                    </span>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm btn--icon"
                      aria-label={`Delete log entry for page ${p.page}`}
                      onClick={() => dispatch({ type: 'deleteProgress', id: entry.id, sessionId: session!.id, logId: p.id })}
                    >
                      <Icon name="x" />
                    </button>
                  </li>
                );
              })}
            </ol>
          </details>
        )}
      </div>
    </article>
  );
}

/** Cumulative pages over the current read-through (de-emphasised line, current point in accent). */
function Sparkline({ entry }: { entry: LibraryEntry }) {
  const s = activeSession(entry);
  const logs = s?.progress ?? [];
  if (logs.length < 2 || !entry.pageCount) return null;
  const w = 120;
  const h = 30;
  const t0 = new Date(logs[0].at).getTime();
  const t1 = Math.max(t0 + 1, new Date(logs[logs.length - 1].at).getTime());
  const pts = logs.map((p) => [((new Date(p.at).getTime() - t0) / (t1 - t0)) * (w - 8) + 4, h - 4 - (p.page / entry.pageCount!) * (h - 8)]);
  const last = pts[pts.length - 1];
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} className="sparkline" role="img" aria-label={`Progress over ${logs.length} sessions`}>
      <line x1={4} x2={w - 4} y1={4} y2={4} stroke="var(--grid)" />
      <line x1={4} x2={w - 4} y1={h - 4} y2={h - 4} stroke="var(--grid)" />
      <polyline points={pts.map((p) => p.join(',')).join(' ')} fill="none" stroke="var(--ink-faint)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={last[0]} cy={last[1]} r={4} fill="var(--accent)" stroke="var(--leaf)" strokeWidth={2} />
    </svg>
  );
}
