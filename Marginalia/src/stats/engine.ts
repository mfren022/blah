/**
 * Reading statistics. Pure functions over library entries so they are easy to test.
 *
 * Pages read are attributed to days from progress logs (high-water-mark deltas). When a
 * finished read has no (or incomplete) logs, the missing pages are spread evenly across
 * its start→finish dates, or put on the finish date when the start is unknown.
 */
import type {
  BookFormat,
  ChartConfig,
  ChartFilters,
  GroupBy,
  ISODate,
  LibraryEntry,
  Metric,
  ReadSession,
  Shelf,
  SplitBy,
  Status,
  TimeGroup,
} from '../lib/types';
import { FORMATS, STATUSES } from '../lib/types';
import {
  addDays,
  addMonths,
  dateOf,
  daysBetween,
  eachDay,
  monthKey,
  monthLabel,
  parseISODate,
  quarterKey,
  startOfWeek,
  WEEKDAYS,
  yearKey,
  formatDate,
} from '../lib/dates';
import { primaryGenre, UNKNOWN_GENRE } from '../lib/genres';
import { formatNumber, mean, median, round, sum } from '../lib/util';
import { CATEGORICAL, MAX_SERIES, ORDINAL, OTHER_GREY } from './palette';

export const STATUS_LABELS: Record<Status, string> = {
  want: 'Want to Read',
  reading: 'Currently Reading',
  finished: 'Finished',
  dnf: 'Did Not Finish',
};

export const FORMAT_LABELS: Record<BookFormat, string> = {
  paperback: 'Paperback',
  hardcover: 'Hardcover',
  ebook: 'Ebook',
  audiobook: 'Audiobook',
  other: 'Other',
};

export const METRICS: Record<Metric, { label: string; unit: string; kind: 'count' | 'sum' | 'average'; help: string }> = {
  finished: { label: 'Books finished', unit: 'books', kind: 'count', help: 'Read-throughs completed (re-reads count again).' },
  pages: { label: 'Pages read', unit: 'pages', kind: 'sum', help: 'From your progress logs; unlogged reads are spread across their dates.' },
  started: { label: 'Books started', unit: 'books', kind: 'count', help: 'Read-throughs begun.' },
  added: { label: 'Books added', unit: 'books', kind: 'count', help: 'Books added to your library.' },
  dnf: { label: 'Books abandoned', unit: 'books', kind: 'count', help: 'Reads marked Did Not Finish.' },
  books: { label: 'Library size', unit: 'books', kind: 'count', help: 'Books in your library (as of the end of the range).' },
  rating: { label: 'Average rating', unit: '★', kind: 'average', help: 'Your average rating of books finished.' },
  days: { label: 'Days to finish', unit: 'days', kind: 'average', help: 'Average days from start to finish.' },
  pace: { label: 'Pages per day', unit: 'pages/day', kind: 'average', help: 'Average reading pace of finished books.' },
};

export const GROUP_LABELS: Record<GroupBy, string> = {
  day: 'Day',
  week: 'Week',
  month: 'Month',
  quarter: 'Quarter',
  year: 'Year',
  genre: 'Genre',
  author: 'Author',
  rating: 'Rating',
  status: 'Shelf status',
  format: 'Format',
  length: 'Book length',
  decade: 'Publication decade',
  shelf: 'Custom shelf',
  weekday: 'Day of week',
};

export const SPLIT_LABELS: Record<SplitBy, string> = {
  none: 'No split',
  status: 'Status',
  format: 'Format',
  genre: 'Genre',
  shelf: 'Custom shelf',
  length: 'Book length',
};

export const TIME_GROUPS: TimeGroup[] = ['day', 'week', 'month', 'quarter', 'year'];
export const isTimeGroup = (g: GroupBy): g is TimeGroup => (TIME_GROUPS as GroupBy[]).includes(g);

export const LENGTH_BUCKETS = [
  { key: 'short', label: 'Under 200 pages', max: 199 },
  { key: 'medium', label: '200–349 pages', max: 349 },
  { key: 'long', label: '350–499 pages', max: 499 },
  { key: 'xlong', label: '500–699 pages', max: 699 },
  { key: 'epic', label: '700+ pages', max: Infinity },
];

export interface StatsContext {
  today: ISODate;
  weekStart: 0 | 1;
  shelves: Shelf[];
  goalBooks: number;
  goalPages: number;
  mode: 'light' | 'dark';
  /** Colour for single-series charts (defaults to categorical slot 1). */
  singleColor?: string;
  /** All library entries (unfiltered), used to keep series colours stable under filters. */
  allEntries?: LibraryEntry[];
}

/* ------------------------------------------------------------------ */
/* Events                                                              */
/* ------------------------------------------------------------------ */

export interface PageEvent {
  date: ISODate;
  entry: LibraryEntry;
  pages: number;
  /** True when pages come from a log, false when spread/estimated. */
  logged: boolean;
}

export interface SessionEvent {
  date: ISODate;
  entry: LibraryEntry;
  session: ReadSession;
}

const MAX_SPREAD_DAYS = 400;

export function sessionPageEvents(entry: LibraryEntry, session: ReadSession): PageEvent[] {
  const out: PageEvent[] = [];
  let high = 0;
  for (const log of session.progress) {
    const delta = log.page - high;
    if (delta > 0) {
      out.push({ date: dateOf(log.at), entry, pages: delta, logged: true });
      high = log.page;
    }
  }
  if (session.outcome === 'finished' && session.endedAt && entry.pageCount && entry.pageCount > high) {
    const remaining = entry.pageCount - high;
    const lastLogDate = session.progress.length ? dateOf(session.progress[session.progress.length - 1].at) : undefined;
    const from = high === 0 ? session.startedAt : lastLogDate ? addDays(lastLogDate, 1) : undefined;
    if (from && from <= session.endedAt && daysBetween(from, session.endedAt) < MAX_SPREAD_DAYS) {
      const days = eachDay(from, session.endedAt);
      for (const d of days) out.push({ date: d, entry, pages: remaining / days.length, logged: false });
    } else {
      out.push({ date: session.endedAt, entry, pages: remaining, logged: false });
    }
  }
  return out;
}

export function allPageEvents(entries: LibraryEntry[]): PageEvent[] {
  return entries.flatMap((e) => e.sessions.flatMap((s) => sessionPageEvents(e, s)));
}

export function pagesByDay(entries: LibraryEntry[]): Map<ISODate, number> {
  const m = new Map<ISODate, number>();
  for (const ev of allPageEvents(entries)) m.set(ev.date, (m.get(ev.date) ?? 0) + ev.pages);
  return m;
}

/** Days with real logged activity (progress logs, starts, finishes). Used for streaks. */
export function activityDays(entries: LibraryEntry[]): Set<ISODate> {
  const days = new Set<ISODate>();
  for (const e of entries) {
    for (const s of e.sessions) {
      for (const p of s.progress) days.add(dateOf(p.at));
      if (s.startedAt && s.progress.length) days.add(s.startedAt);
      if (s.endedAt && s.outcome === 'finished') days.add(s.endedAt);
    }
  }
  return days;
}

export function streaks(days: Set<ISODate>, today: ISODate): { current: number; longest: number } {
  if (!days.size) return { current: 0, longest: 0 };
  const sorted = [...days].filter((d) => d <= today).sort();
  let longest = 0;
  let run = 0;
  let prev: ISODate | undefined;
  for (const d of sorted) {
    run = prev && daysBetween(prev, d) === 1 ? run + 1 : 1;
    longest = Math.max(longest, run);
    prev = d;
  }
  // The current streak survives until the end of the day after the last reading day.
  let cursor = days.has(today) ? today : addDays(today, -1);
  let current = 0;
  while (days.has(cursor)) {
    current++;
    cursor = addDays(cursor, -1);
  }
  return { current, longest };
}

export const finishedSessions = (e: LibraryEntry) => e.sessions.filter((s) => s.outcome === 'finished' && s.endedAt);

export function sessionDays(s: ReadSession): number | undefined {
  return s.startedAt && s.endedAt ? daysBetween(s.startedAt, s.endedAt) + 1 : undefined;
}

/* ------------------------------------------------------------------ */
/* Filters                                                             */
/* ------------------------------------------------------------------ */

export function earliestDate(entries: LibraryEntry[], fallback: ISODate): ISODate {
  let min = fallback;
  for (const e of entries) {
    if (e.addedAt < min) min = e.addedAt;
    for (const s of e.sessions) {
      if (s.startedAt && s.startedAt < min) min = s.startedAt;
      if (s.endedAt && s.endedAt < min) min = s.endedAt;
      for (const p of s.progress) if (dateOf(p.at) < min) min = dateOf(p.at);
    }
  }
  return min;
}

export function resolveRange(filters: ChartFilters, entries: LibraryEntry[], today: ISODate): { from: ISODate; to: ISODate } {
  const y = Number(today.slice(0, 4));
  switch (filters.range) {
    case 'ytd':
      return { from: `${y}-01-01`, to: today };
    case 'last12':
      // Twelve whole calendar months, ending with the current one.
      return { from: addMonths(`${monthKey(today)}-01`, -11), to: today };
    case 'last90':
      return { from: addDays(today, -89), to: today };
    case 'last30':
      return { from: addDays(today, -29), to: today };
    case 'lastYear':
      return { from: `${y - 1}-01-01`, to: `${y - 1}-12-31` };
    case 'custom': {
      const from = filters.from ?? earliestDate(entries, today);
      const to = filters.to ?? today;
      return from <= to ? { from, to } : { from: to, to: from };
    }
    default:
      return { from: earliestDate(entries, today), to: today };
  }
}

export function entryGenre(e: LibraryEntry): string {
  return primaryGenre(e.book.subjects, e.genre);
}

export function lengthBucket(pages: number | undefined): string {
  if (!pages) return 'unknown';
  return LENGTH_BUCKETS.find((b) => pages <= b.max)!.key;
}

export function filterEntries(entries: LibraryEntry[], f: ChartFilters): LibraryEntry[] {
  return entries.filter((e) => {
    if (f.statuses.length && !f.statuses.includes(e.status)) return false;
    if (f.shelves.length && !f.shelves.some((s) => e.shelves.includes(s))) return false;
    if (f.genres.length && !f.genres.includes(entryGenre(e))) return false;
    if (f.formats.length && !f.formats.includes(e.format ?? 'other')) return false;
    if (f.authors.length && !e.book.authors.some((a) => f.authors.includes(a.name))) return false;
    if (f.minRating && (e.rating ?? 0) < f.minRating) return false;
    if (f.minPages && (e.pageCount ?? 0) < f.minPages) return false;
    if (f.maxPages && (e.pageCount ?? Infinity) > f.maxPages) return false;
    return true;
  });
}

/* ------------------------------------------------------------------ */
/* Grouping                                                            */
/* ------------------------------------------------------------------ */

interface Category {
  key: string;
  label: string;
  longLabel: string;
}

function bucketKey(date: ISODate, g: TimeGroup, weekStart: 0 | 1): string {
  switch (g) {
    case 'day':
      return date;
    case 'week':
      return startOfWeek(date, weekStart);
    case 'month':
      return monthKey(date);
    case 'quarter':
      return quarterKey(date);
    case 'year':
      return yearKey(date);
  }
}

function bucketEnd(key: string, g: TimeGroup): ISODate {
  switch (g) {
    case 'day':
      return key;
    case 'week':
      return addDays(key, 6);
    case 'month':
      return addDays(addMonths(`${key}-01`, 1), -1);
    case 'quarter': {
      const q = Number(key.slice(-1));
      return addDays(addMonths(`${key.slice(0, 4)}-${String(q * 3 - 2).padStart(2, '0')}-01`, 3), -1);
    }
    case 'year':
      return `${key}-12-31`;
  }
}

function timeCategories(from: ISODate, to: ISODate, g: TimeGroup, weekStart: 0 | 1): Category[] {
  const out: Category[] = [];
  let cursor = from;
  const seen = new Set<string>();
  for (let i = 0; i < 4000 && cursor <= to; i++) {
    const key = bucketKey(cursor, g, weekStart);
    if (!seen.has(key)) {
      seen.add(key);
      out.push({ key, ...timeLabels(key, g) });
    }
    cursor = addDays(bucketEnd(key, g), 1);
  }
  return out;
}

function timeLabels(key: string, g: TimeGroup): { label: string; longLabel: string } {
  switch (g) {
    case 'day':
      return { label: `${Number(key.slice(8, 10))} ${monthLabel(key).slice(0, 3)}`, longLabel: formatDate(key, 'long') };
    case 'week':
      return { label: `${Number(key.slice(8, 10))} ${monthLabel(key).slice(0, 3)}`, longLabel: `Week of ${formatDate(key, 'long')}` };
    case 'month':
      return { label: monthLabel(key), longLabel: monthLabel(key, true) };
    case 'quarter':
      return { label: `${key.slice(5)} ’${key.slice(2, 4)}`, longLabel: `${key.slice(5)} ${key.slice(0, 4)}` };
    case 'year':
      return { label: key, longLabel: key };
  }
}

function weekdayOrder(weekStart: 0 | 1): number[] {
  return weekStart === 1 ? [1, 2, 3, 4, 5, 6, 0] : [0, 1, 2, 3, 4, 5, 6];
}

/** Category keys for an entry (several for multi-valued dimensions like shelves). */
function entryKeys(e: LibraryEntry, g: GroupBy | SplitBy, date: ISODate | undefined, ctx: StatsContext): string[] {
  switch (g) {
    case 'genre':
      return [entryGenre(e)];
    case 'author':
      return [e.book.authors[0]?.name ?? 'Unknown author'];
    case 'rating':
      return [e.rating ? String(e.rating) : 'unrated'];
    case 'status':
      return [e.status];
    case 'format':
      return [e.format ?? 'other'];
    case 'length':
      return [lengthBucket(e.pageCount)];
    case 'decade':
      return [e.book.firstPublishYear ? `${Math.floor(e.book.firstPublishYear / 10) * 10}` : 'unknown'];
    case 'shelf': {
      const own = ctx.shelves.filter((s) => e.shelves.includes(s.id)).map((s) => s.id);
      return own.length ? own : ['none'];
    }
    case 'weekday':
      return [String(parseISODate(date ?? e.addedAt).getDay())];
    default:
      return [];
  }
}

/** Series keys use the first value only, so stacks never double count. */
function seriesKeyOf(e: LibraryEntry, split: SplitBy, ctx: StatsContext): string {
  if (split === 'none') return 'all';
  return entryKeys(e, split, undefined, ctx)[0];
}

function categoryLabel(g: GroupBy | SplitBy, key: string, ctx: StatsContext): string {
  switch (g) {
    case 'rating':
      return key === 'unrated' ? 'Unrated' : `${key} ★`;
    case 'status':
      return STATUS_LABELS[key as Status] ?? key;
    case 'format':
      return FORMAT_LABELS[key as BookFormat] ?? key;
    case 'length':
      return key === 'unknown' ? 'Unknown length' : LENGTH_BUCKETS.find((b) => b.key === key)?.label ?? key;
    case 'decade':
      return key === 'unknown' ? 'Unknown' : `${key}s`;
    case 'shelf':
      return key === 'none' ? 'No custom shelf' : ctx.shelves.find((s) => s.id === key)?.name ?? key;
    case 'weekday':
      return WEEKDAYS[Number(key)]?.slice(0, 3) ?? key;
    default:
      return key;
  }
}

/** Natural order for ordered dimensions; undefined means "sort by value". */
function fixedOrder(g: GroupBy | SplitBy, ctx: StatsContext): string[] | undefined {
  switch (g) {
    case 'status':
      return [...STATUSES];
    case 'format':
      return [...FORMATS];
    case 'length':
      return [...LENGTH_BUCKETS.map((b) => b.key), 'unknown'];
    case 'rating':
      return ['0.5', '1', '1.5', '2', '2.5', '3', '3.5', '4', '4.5', '5', 'unrated'];
    case 'weekday':
      return weekdayOrder(ctx.weekStart).map(String);
    case 'shelf':
      return [...ctx.shelves.map((s) => s.id), 'none'];
    default:
      return undefined;
  }
}

/* ------------------------------------------------------------------ */
/* Observations: (entry, date, value) rows for a metric               */
/* ------------------------------------------------------------------ */

interface Observation {
  entry: LibraryEntry;
  date: ISODate;
  value: number;
}

function observations(metric: Metric, entries: LibraryEntry[], from: ISODate, to: ISODate): Observation[] {
  const inRange = (d?: ISODate) => !!d && d >= from && d <= to;
  const out: Observation[] = [];
  switch (metric) {
    case 'pages':
      for (const ev of allPageEvents(entries)) if (inRange(ev.date)) out.push({ entry: ev.entry, date: ev.date, value: ev.pages });
      break;
    case 'finished':
      for (const e of entries) for (const s of finishedSessions(e)) if (inRange(s.endedAt)) out.push({ entry: e, date: s.endedAt!, value: 1 });
      break;
    case 'started':
      for (const e of entries) for (const s of e.sessions) if (inRange(s.startedAt)) out.push({ entry: e, date: s.startedAt!, value: 1 });
      break;
    case 'dnf':
      for (const e of entries) for (const s of e.sessions) if (s.outcome === 'dnf' && inRange(s.endedAt)) out.push({ entry: e, date: s.endedAt!, value: 1 });
      break;
    case 'added':
      for (const e of entries) if (inRange(e.addedAt)) out.push({ entry: e, date: e.addedAt, value: 1 });
      break;
    case 'books':
      for (const e of entries) if (e.addedAt <= to) out.push({ entry: e, date: e.addedAt, value: 1 });
      break;
    case 'rating':
      for (const e of entries) {
        if (!e.rating) continue;
        for (const s of finishedSessions(e)) if (inRange(s.endedAt)) out.push({ entry: e, date: s.endedAt!, value: e.rating });
      }
      break;
    case 'days':
      for (const e of entries)
        for (const s of finishedSessions(e)) {
          const d = sessionDays(s);
          if (d && inRange(s.endedAt)) out.push({ entry: e, date: s.endedAt!, value: d });
        }
      break;
    case 'pace':
      for (const e of entries)
        for (const s of finishedSessions(e)) {
          const d = sessionDays(s);
          if (d && e.pageCount && inRange(s.endedAt)) out.push({ entry: e, date: s.endedAt!, value: e.pageCount / d });
        }
      break;
  }
  return out;
}

/* ------------------------------------------------------------------ */
/* Chart data                                                          */
/* ------------------------------------------------------------------ */

export interface ChartSeries {
  key: string;
  label: string;
  color: string;
  values: number[];
}

export interface ChartData {
  categories: Category[];
  series: ChartSeries[];
  totals: number[];
  /** Number of observations behind each category (for averages). */
  counts: number[];
  kind: 'count' | 'sum' | 'average';
  unit: string;
  metricLabel: string;
  groupLabel: string;
  isTime: boolean;
  cumulative: boolean;
  average?: number;
  goal?: number[];
  range: { from: ISODate; to: ISODate };
  empty: boolean;
  notes: string[];
}

export function formatMetricValue(value: number, metric: Metric): string {
  if (!Number.isFinite(value)) return '—';
  switch (metric) {
    case 'rating':
      return value.toFixed(2);
    case 'days':
    case 'pace':
      return formatNumber(round(value, 1), 1);
    case 'pages':
      return formatNumber(Math.round(value));
    default:
      return formatNumber(round(value, 1), 1);
  }
}

/** Which split options make sense for a configuration. */
export function splitAllowed(config: Pick<ChartConfig, 'metric' | 'groupBy'>, split: SplitBy): boolean {
  if (split === 'none') return true;
  if (METRICS[config.metric].kind === 'average') return false;
  return split !== config.groupBy;
}

function stableSeriesOrder(split: SplitBy, entries: LibraryEntry[], ctx: StatsContext): string[] {
  const fixed = fixedOrder(split, ctx);
  if (fixed) return fixed;
  // Order by frequency in the WHOLE library so filtering never repaints a series.
  const counts = new Map<string, number>();
  for (const e of entries) {
    const k = seriesKeyOf(e, split, ctx);
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([k]) => k);
}

export function computeChart(config: ChartConfig, entriesAll: LibraryEntry[], ctx: StatsContext): ChartData {
  const metricDef = METRICS[config.metric];
  const isTime = isTimeGroup(config.groupBy);
  const split: SplitBy = splitAllowed(config, config.splitBy) ? config.splitBy : 'none';
  const entries = filterEntries(entriesAll, config.filters);
  const range = resolveRange(config.filters, ctx.allEntries ?? entriesAll, ctx.today);
  const notes: string[] = [];
  if (split !== config.splitBy && config.splitBy !== 'none') notes.push('Averages cannot be stacked, so the split is off.');

  let obs = observations(config.metric, entries, range.from, range.to);

  // Categories
  let categories: Category[];
  let keyFor: (o: Observation) => string[];
  if (isTime) {
    const g = config.groupBy as TimeGroup;
    let from = range.from;
    const days = daysBetween(from, range.to);
    if (g === 'day' && days > 400) notes.push('Showing the last 400 days — choose week or month for longer ranges.');
    if (g === 'day' && days > 400) from = addDays(range.to, -399);
    categories = timeCategories(from, range.to, g, ctx.weekStart);
    keyFor = (o) => [bucketKey(o.date, g, ctx.weekStart)];
    if (config.metric === 'books') {
      // Library size is cumulative by nature: count everything added before the first bucket too.
      obs = obs.map((o) => (o.date < from ? { ...o, date: from } : o));
    }
  } else {
    keyFor = (o) => entryKeys(o.entry, config.groupBy, o.date, ctx);
    const fixed = fixedOrder(config.groupBy, ctx);
    const present = new Set(obs.flatMap((o) => keyFor(o)));
    if (fixed) {
      const keys = fixed.filter((k) => present.has(k) || (config.groupBy === 'weekday' && obs.length > 0));
      categories = keys.map((k) => ({ key: k, label: categoryLabel(config.groupBy, k, ctx), longLabel: categoryLabel(config.groupBy, k, ctx) }));
    } else {
      // Rank by value; fold the tail into "Other".
      const totals = new Map<string, number[]>();
      for (const o of obs) for (const k of keyFor(o)) totals.set(k, [...(totals.get(k) ?? []), o.value]);
      const score = (vals: number[]) => (metricDef.kind === 'average' ? vals.length : sum(vals));
      const ranked = [...totals.entries()].sort((a, b) => score(b[1]) - score(a[1]) || a[0].localeCompare(b[0])).map(([k]) => k);
      const top = ranked.slice(0, config.topN);
      const rest = new Set(ranked.slice(config.topN));
      categories = top.map((k) => ({ key: k, label: categoryLabel(config.groupBy, k, ctx), longLabel: categoryLabel(config.groupBy, k, ctx) }));
      if (rest.size) {
        categories.push({ key: '__other', label: `Other (${rest.size})`, longLabel: `${rest.size} more` });
        const base = keyFor;
        keyFor = (o) => base(o).map((k) => (rest.has(k) ? '__other' : k));
      }
      if (config.groupBy === 'genre' && present.has(UNKNOWN_GENRE)) notes.push('“Uncategorised” books have no recognisable genre — set one on the book page.');
    }
  }

  const catIndex = new Map(categories.map((c, i) => [c.key, i]));

  // Series. Colour follows the entity: a series' slot is its position in a stable order
  // computed from the whole (unfiltered) library, so filters never repaint survivors.
  // Only the first seven stable entities get hues; everything else folds into grey "Other".
  let seriesKeys = ['all'];
  let seriesOf = (_e: LibraryEntry) => 'all';
  let named: string[] = [];
  if (split !== 'none') {
    const stable = stableSeriesOrder(split, ctx.allEntries ?? entriesAll, ctx);
    named = split === 'length' ? stable : stable.slice(0, MAX_SERIES - 1);
    const namedSet = new Set(named);
    seriesOf = (e) => {
      const k = seriesKeyOf(e, split, ctx);
      return namedSet.has(k) ? k : '__other';
    };
    const present = new Set(obs.map((o) => seriesOf(o.entry)));
    seriesKeys = [...named.filter((k) => present.has(k)), ...(present.has('__other') ? ['__other'] : [])];
  }
  const seriesIndex = new Map(seriesKeys.map((k, i) => [k, i]));
  const buckets: number[][][] = seriesKeys.map(() => categories.map(() => []));
  for (const o of obs) {
    const si = seriesIndex.get(seriesOf(o.entry));
    if (si === undefined) continue;
    for (const k of keyFor(o)) {
      const ci = catIndex.get(k);
      if (ci !== undefined) buckets[si][ci].push(o.value);
    }
  }

  const agg = (vals: number[]) => (metricDef.kind === 'average' ? (vals.length ? mean(vals)! : NaN) : sum(vals));
  const lengthOrder = LENGTH_BUCKETS.map((b) => b.key);
  const colorFor = (k: string): string => {
    if (split === 'none') return ctx.singleColor ?? CATEGORICAL[ctx.mode][0];
    if (k === '__other' || (split === 'length' && k === 'unknown')) return OTHER_GREY;
    if (split === 'length') return ORDINAL[ctx.mode][Math.max(0, lengthOrder.indexOf(k))];
    return CATEGORICAL[ctx.mode][Math.max(0, named.indexOf(k))];
  };
  let series: ChartSeries[] = seriesKeys.map((k, i) => ({
    key: k,
    label: k === 'all' ? metricDef.label : k === '__other' ? 'Other' : categoryLabel(split, k, ctx),
    color: colorFor(k),
    values: buckets[i].map(agg),
  }));

  if (config.metric === 'books' && isTime) {
    series = series.map((s) => ({ ...s, values: runningSum(s.values) }));
  } else if (config.cumulative && isTime && metricDef.kind !== 'average') {
    series = series.map((s) => ({ ...s, values: runningSum(s.values) }));
  }

  const counts = categories.map((_, ci) => buckets.reduce((n, b) => n + b[ci].length, 0));
  const totals =
    metricDef.kind === 'average'
      ? categories.map((_, ci) => agg(buckets.flatMap((b) => b[ci])))
      : categories.map((_, ci) => sum(series.map((s) => s.values[ci] || 0)));

  const finite = totals.filter((t) => Number.isFinite(t));
  const average = config.showAverage && !(config.cumulative && isTime) && config.metric !== 'books' && finite.length ? mean(finite) : undefined;

  let goal: number[] | undefined;
  if (config.showGoal && isTime && (config.metric === 'finished' || config.metric === 'pages')) {
    const perYear = config.metric === 'finished' ? ctx.goalBooks : ctx.goalPages;
    if (perYear > 0) {
      const g = config.groupBy as TimeGroup;
      const firstStart = range.from;
      goal = categories.map((c) => {
        const end = bucketEnd(c.key, g) > range.to ? range.to : bucketEnd(c.key, g);
        if (config.cumulative) return (perYear * (daysBetween(firstStart, end) + 1)) / 365;
        const start = c.key.length === 10 ? c.key : g === 'month' ? `${c.key}-01` : g === 'year' ? `${c.key}-01-01` : undefined;
        const days = start ? daysBetween(start, bucketEnd(c.key, g)) + 1 : g === 'quarter' ? 91 : 30;
        return (perYear * days) / 365;
      });
    } else notes.push('Set a yearly goal in Settings to see your goal pace.');
  }

  const empty = obs.length === 0;
  return {
    categories,
    series,
    totals,
    counts,
    kind: metricDef.kind,
    unit: metricDef.unit,
    metricLabel: metricDef.label,
    groupLabel: GROUP_LABELS[config.groupBy],
    isTime,
    cumulative: (config.cumulative && isTime && metricDef.kind !== 'average') || (config.metric === 'books' && isTime),
    average,
    goal,
    range,
    empty,
    notes,
  };
}

function runningSum(values: number[]): number[] {
  let acc = 0;
  return values.map((v) => (acc += Number.isFinite(v) ? v : 0));
}

/* ------------------------------------------------------------------ */
/* Overview                                                            */
/* ------------------------------------------------------------------ */

export interface Overview {
  counts: Record<Status, number>;
  total: number;
  finishedThisYear: number;
  finishedLastYear: number;
  finishedAllTime: number;
  pagesThisYear: number;
  pagesAllTime: number;
  pagesLast30: number;
  avgRating?: number;
  ratedCount: number;
  avgDaysToFinish?: number;
  medianDaysToFinish?: number;
  avgPace?: number;
  dnfRate?: number;
  currentStreak: number;
  longestStreak: number;
  longest?: { entry: LibraryEntry; pages: number };
  shortest?: { entry: LibraryEntry; pages: number };
  topGenre?: { name: string; count: number };
  topAuthor?: { name: string; count: number };
  busiestMonth?: { key: string; count: number };
  monthlyThisYear: number[];
  goalBooks: { target: number; done: number; expected: number };
  goalPages: { target: number; done: number; expected: number };
  readingDays30: number;
}

export function computeOverview(entries: LibraryEntry[], ctx: StatsContext): Overview {
  const y = ctx.today.slice(0, 4);
  const lastY = String(Number(y) - 1);
  const counts: Record<Status, number> = { want: 0, reading: 0, finished: 0, dnf: 0 };
  for (const e of entries) counts[e.status]++;

  const finishes = entries.flatMap((e) => finishedSessions(e).map((s) => ({ e, s })));
  const dnfs = entries.flatMap((e) => e.sessions.filter((s) => s.outcome === 'dnf'));
  const byDay = pagesByDay(entries);
  let pagesThisYear = 0;
  let pagesAllTime = 0;
  let pagesLast30 = 0;
  const since30 = addDays(ctx.today, -29);
  let readingDays30 = 0;
  for (const [d, p] of byDay) {
    if (d > ctx.today) continue;
    pagesAllTime += p;
    if (d.startsWith(y)) pagesThisYear += p;
    if (d >= since30) {
      pagesLast30 += p;
      if (p > 0) readingDays30++;
    }
  }

  const rated = entries.filter((e) => e.rating);
  const days = finishes.map(({ s }) => sessionDays(s)).filter((d): d is number => !!d);
  const paces = finishes
    .map(({ e, s }) => (e.pageCount && sessionDays(s) ? e.pageCount / sessionDays(s)! : undefined))
    .filter((p): p is number => !!p);
  const { current, longest } = streaks(activityDays(entries), ctx.today);

  const finishedEntries = entries.filter((e) => finishedSessions(e).length && e.pageCount);
  const byPages = [...finishedEntries].sort((a, b) => (b.pageCount ?? 0) - (a.pageCount ?? 0));

  const tally = (keys: string[]) => {
    const m = new Map<string, number>();
    for (const k of keys) m.set(k, (m.get(k) ?? 0) + 1);
    const best = [...m.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0];
    return best ? { name: best[0], count: best[1] } : undefined;
  };
  const topGenre = tally(finishes.map(({ e }) => entryGenre(e)).filter((g) => g !== UNKNOWN_GENRE));
  const topAuthor = tally(finishes.map(({ e }) => e.book.authors[0]?.name).filter((a): a is string => !!a));
  const busiest = tally(finishes.map(({ s }) => monthKey(s.endedAt!)));

  const monthlyThisYear = Array.from({ length: 12 }, (_, i) => finishes.filter(({ s }) => s.endedAt!.startsWith(`${y}-${String(i + 1).padStart(2, '0')}`)).length);
  const dayOfYear = daysBetween(`${y}-01-01`, ctx.today) + 1;
  const yearDays = daysBetween(`${y}-01-01`, `${y}-12-31`) + 1;
  const finishedThisYear = finishes.filter(({ s }) => s.endedAt!.startsWith(y)).length;

  return {
    counts,
    total: entries.length,
    finishedThisYear,
    finishedLastYear: finishes.filter(({ s }) => s.endedAt!.startsWith(lastY)).length,
    finishedAllTime: finishes.length,
    pagesThisYear,
    pagesAllTime,
    pagesLast30,
    avgRating: mean(rated.map((e) => e.rating!)),
    ratedCount: rated.length,
    avgDaysToFinish: mean(days),
    medianDaysToFinish: median(days),
    avgPace: mean(paces),
    dnfRate: finishes.length + dnfs.length ? dnfs.length / (finishes.length + dnfs.length) : undefined,
    currentStreak: current,
    longestStreak: longest,
    longest: byPages[0] ? { entry: byPages[0], pages: byPages[0].pageCount! } : undefined,
    shortest: byPages.length > 1 ? { entry: byPages[byPages.length - 1], pages: byPages[byPages.length - 1].pageCount! } : undefined,
    topGenre,
    topAuthor,
    busiestMonth: busiest ? { key: busiest.name, count: busiest.count } : undefined,
    monthlyThisYear,
    goalBooks: { target: ctx.goalBooks, done: finishedThisYear, expected: (ctx.goalBooks * dayOfYear) / yearDays },
    goalPages: { target: ctx.goalPages, done: pagesThisYear, expected: (ctx.goalPages * dayOfYear) / yearDays },
    readingDays30,
  };
}

/* ------------------------------------------------------------------ */
/* Calendar heatmap                                                    */
/* ------------------------------------------------------------------ */

export interface HeatCell {
  date: ISODate;
  pages: number;
  level: 0 | 1 | 2 | 3 | 4;
  future: boolean;
}

export function heatmap(entries: LibraryEntry[], today: ISODate, weekStart: 0 | 1, weeks = 53): { weeks: HeatCell[][]; max: number; thresholds: number[]; total: number } {
  const byDay = pagesByDay(entries);
  const lastWeekStart = startOfWeek(today, weekStart);
  const first = addDays(lastWeekStart, -(weeks - 1) * 7);
  const values = eachDay(first, today).map((d) => byDay.get(d) ?? 0).filter((v) => v > 0).sort((a, b) => a - b);
  const q = (p: number) => values[Math.min(values.length - 1, Math.floor(p * values.length))] ?? 0;
  const thresholds = [q(0.25), q(0.5), q(0.75)];
  const level = (v: number): HeatCell['level'] => (v <= 0 ? 0 : v <= thresholds[0] ? 1 : v <= thresholds[1] ? 2 : v <= thresholds[2] ? 3 : 4);
  const grid: HeatCell[][] = [];
  for (let w = 0; w < weeks; w++) {
    const col: HeatCell[] = [];
    for (let d = 0; d < 7; d++) {
      const date = addDays(first, w * 7 + d);
      const pages = byDay.get(date) ?? 0;
      col.push({ date, pages, level: date > today ? 0 : level(pages), future: date > today });
    }
    grid.push(col);
  }
  return { weeks: grid, max: values[values.length - 1] ?? 0, thresholds, total: sum(values) };
}

/** Pace & projection for a book being read. */
export function readingForecast(entry: LibraryEntry, today: ISODate): { pagesPerDay?: number; daysLeft?: number; eta?: ISODate; daysReading: number } {
  const s = entry.sessions.at(-1);
  const start = s?.startedAt ?? today;
  const daysReading = Math.max(1, daysBetween(start, today) + 1);
  const page = s?.progress.at(-1)?.page ?? 0;
  if (!entry.pageCount || page <= 0) return { daysReading };
  const pagesPerDay = page / daysReading;
  const left = Math.max(0, entry.pageCount - page);
  const daysLeft = pagesPerDay > 0 ? Math.ceil(left / pagesPerDay) : undefined;
  return { pagesPerDay, daysLeft, eta: daysLeft !== undefined ? addDays(today, daysLeft) : undefined, daysReading };
}
