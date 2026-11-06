import {
  FORMATS,
  STATUSES,
  type BookFormat,
  type BookSnapshot,
  type ChartConfig,
  type EditionInfo,
  type LibraryEntry,
  type ProgressLog,
  type ReadSession,
  type Shelf,
  type Status,
} from '../lib/types';
import { isISODate, isLocalDateTime, nowLocal, today } from '../lib/dates';
import { isHexColor, uid } from '../lib/util';
import { sanitizeSettings, type Settings } from './settings';
import { DEFAULT_CHART, DEFAULT_FILTERS } from '../stats/presets';

export const STORAGE_KEY = 'marginalia:v1';
export const STATE_VERSION = 1;

export interface AppState {
  version: number;
  library: Record<string, LibraryEntry>;
  shelves: Shelf[];
  settings: Settings;
  /** The chart currently being edited on the statistics page. */
  chart: ChartConfig;
  /** Charts the reader saved. */
  chartPresets: ChartConfig[];
  onboarded: boolean;
}

export const DEFAULT_SHELVES: Shelf[] = [
  { id: 'favourites', name: 'Favourites', color: '#b8923a', createdAt: '2026-01-01' },
  { id: 'owned', name: 'On my shelves', color: '#2f5b9a', createdAt: '2026-01-01' },
];

export function initialState(): AppState {
  return {
    version: STATE_VERSION,
    library: {},
    shelves: DEFAULT_SHELVES.map((s) => ({ ...s, createdAt: today() })),
    settings: sanitizeSettings({}),
    chart: structuredClone(DEFAULT_CHART),
    chartPresets: [],
    onboarded: false,
  };
}

/* ------------------------------------------------------------------ */
/* Sanitising untrusted data (localStorage, imports)                    */
/* ------------------------------------------------------------------ */

const str = (v: unknown, max = 5000): string | undefined => (typeof v === 'string' && v.trim() ? v.slice(0, max) : undefined);
const num = (v: unknown, lo = 0, hi = 1e7): number | undefined => (typeof v === 'number' && Number.isFinite(v) && v >= lo && v <= hi ? v : undefined);
const date = (v: unknown) => (isISODate(v) ? v : undefined);

function sanitizeBook(raw: unknown, fallbackId: string): BookSnapshot | null {
  if (!raw || typeof raw !== 'object') return null;
  const b = raw as Record<string, unknown>;
  const title = str(b.title, 500);
  if (!title) return null;
  const source = b.source === 'openlibrary' || b.source === 'sample' || b.source === 'manual' ? b.source : 'manual';
  const authors = Array.isArray(b.authors)
    ? b.authors
        .map((a) => (a && typeof a === 'object' ? { name: str((a as Record<string, unknown>).name, 200), key: str((a as Record<string, unknown>).key, 40) } : null))
        .filter((a): a is { name: string; key: string | undefined } => !!a?.name)
        .map((a) => (a.key ? { name: a.name, key: a.key } : { name: a.name }))
    : [];
  return {
    id: str(b.id, 200) ?? fallbackId,
    source,
    workKey: str(b.workKey, 40),
    title,
    subtitle: str(b.subtitle, 500),
    authors,
    coverId: num(b.coverId, 1, 1e12),
    coverUrl: typeof b.coverUrl === 'string' && /^(https?:|data:image\/)/.test(b.coverUrl) ? b.coverUrl.slice(0, 200000) : undefined,
    firstPublishYear: num(b.firstPublishYear, -3000, 3000),
    subjects: Array.isArray(b.subjects) ? b.subjects.filter((s): s is string => typeof s === 'string').slice(0, 30) : undefined,
    pagesMedian: num(b.pagesMedian, 1, 100000),
    editionCount: num(b.editionCount, 0, 1e6),
  };
}

function sanitizeEdition(raw: unknown): EditionInfo | undefined {
  if (!raw || typeof raw !== 'object') return undefined;
  const e = raw as Record<string, unknown>;
  const key = str(e.key, 80);
  if (!key) return undefined;
  return {
    key,
    title: str(e.title, 500),
    publisher: str(e.publisher, 300),
    publishDate: str(e.publishDate, 60),
    year: num(e.year, -3000, 3000),
    pages: num(e.pages, 1, 100000),
    format: str(e.format, 60),
    isbn13: str(e.isbn13, 20),
    isbn10: str(e.isbn10, 20),
    language: str(e.language, 10),
    coverId: num(e.coverId, 1, 1e12),
  };
}

function sanitizeSession(raw: unknown): ReadSession | null {
  if (!raw || typeof raw !== 'object') return null;
  const s = raw as Record<string, unknown>;
  const outcome = s.outcome === 'reading' || s.outcome === 'finished' || s.outcome === 'dnf' ? s.outcome : null;
  if (!outcome) return null;
  const progress: ProgressLog[] = Array.isArray(s.progress)
    ? s.progress
        .map((p) => p as Record<string, unknown>)
        .filter((p) => p && isLocalDateTime(p.at) && num(p.page, 0, 100000) !== undefined)
        .map((p) => ({ id: str(p.id, 40) ?? uid('p'), at: p.at as string, page: Math.round(p.page as number), note: str(p.note, 1000) }))
        .sort((a, b) => a.at.localeCompare(b.at))
    : [];
  return {
    id: str(s.id, 40) ?? uid('s'),
    startedAt: date(s.startedAt),
    endedAt: outcome === 'reading' ? undefined : date(s.endedAt),
    outcome,
    progress,
  };
}

export function sanitizeEntry(raw: unknown): LibraryEntry | null {
  if (!raw || typeof raw !== 'object') return null;
  const e = raw as Record<string, unknown>;
  const id = str(e.id, 200);
  if (!id) return null;
  const book = sanitizeBook(e.book, id);
  if (!book) return null;
  book.id = id;
  const status: Status = STATUSES.includes(e.status as Status) ? (e.status as Status) : 'want';
  let sessions = Array.isArray(e.sessions) ? e.sessions.map(sanitizeSession).filter((s): s is ReadSession => !!s) : [];
  // Only the last session may be in progress.
  sessions = sessions.map((s, i) => (s.outcome === 'reading' && i < sessions.length - 1 ? { ...s, outcome: 'dnf' as const, endedAt: s.endedAt ?? s.startedAt } : s));
  const entry: LibraryEntry = {
    id,
    book,
    status,
    addedAt: date(e.addedAt) ?? today(),
    queuedAt: date(e.queuedAt),
    shelves: Array.isArray(e.shelves) ? e.shelves.filter((x): x is string => typeof x === 'string').slice(0, 50) : [],
    edition: sanitizeEdition(e.edition),
    pageCount: num(e.pageCount, 1, 100000),
    format: FORMATS.includes(e.format as BookFormat) ? (e.format as BookFormat) : undefined,
    genre: str(e.genre, 60),
    sessions,
    rating: num(e.rating, 0.5, 5),
    review: str(e.review, 20000),
    reviewSpoilers: e.reviewSpoilers === true || undefined,
    reviewedAt: date(e.reviewedAt),
    notes: str(e.notes, 20000),
    favorite: e.favorite === true || undefined,
    sample: e.sample === true || undefined,
    updatedAt: isLocalDateTime(e.updatedAt) ? e.updatedAt : nowLocal(),
  };
  // Keep status consistent with sessions.
  const last = sessions.at(-1);
  if (last?.outcome === 'reading') entry.status = 'reading';
  else if (!last) entry.status = 'want';
  else if (entry.status === 'reading') entry.status = last.outcome;
  return entry;
}

function sanitizeShelf(raw: unknown): Shelf | null {
  if (!raw || typeof raw !== 'object') return null;
  const s = raw as Record<string, unknown>;
  const id = str(s.id, 60);
  const name = str(s.name, 60);
  if (!id || !name) return null;
  return { id, name, color: isHexColor(s.color) ? s.color : '#7b6a4a', createdAt: date(s.createdAt) ?? today() };
}

export function sanitizeChart(raw: unknown, fallback: ChartConfig = DEFAULT_CHART): ChartConfig {
  if (!raw || typeof raw !== 'object') return structuredClone(fallback);
  const c = raw as Record<string, unknown>;
  const pick = <T extends string>(v: unknown, allowed: readonly T[], def: T): T => (allowed.includes(v as T) ? (v as T) : def);
  const f = (c.filters ?? {}) as Record<string, unknown>;
  const strList = (v: unknown) => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string').slice(0, 100) : []);
  return {
    id: str(c.id, 60) ?? uid('chart'),
    name: str(c.name, 80) ?? 'Chart',
    metric: pick(c.metric, ['finished', 'pages', 'started', 'added', 'dnf', 'books', 'rating', 'days', 'pace'] as const, fallback.metric),
    groupBy: pick(c.groupBy, ['day', 'week', 'month', 'quarter', 'year', 'genre', 'author', 'rating', 'status', 'format', 'length', 'decade', 'shelf', 'weekday'] as const, fallback.groupBy),
    chartType: pick(c.chartType, ['column', 'bar', 'line', 'area'] as const, fallback.chartType),
    splitBy: pick(c.splitBy, ['none', 'status', 'format', 'genre', 'shelf', 'length'] as const, fallback.splitBy),
    cumulative: c.cumulative === true,
    showValues: c.showValues === true,
    showAverage: c.showAverage === true,
    showGoal: c.showGoal === true,
    topN: num(c.topN, 3, 30) ?? 10,
    filters: {
      range: pick(f.range, ['all', 'ytd', 'last12', 'last90', 'last30', 'lastYear', 'custom'] as const, DEFAULT_FILTERS.range),
      from: date(f.from),
      to: date(f.to),
      statuses: strList(f.statuses).filter((s): s is Status => STATUSES.includes(s as Status)),
      shelves: strList(f.shelves),
      genres: strList(f.genres),
      formats: strList(f.formats).filter((s): s is BookFormat => FORMATS.includes(s as BookFormat)),
      authors: strList(f.authors),
      minRating: num(f.minRating, 0, 5),
      minPages: num(f.minPages, 0, 100000),
      maxPages: num(f.maxPages, 0, 100000),
    },
  };
}

export function sanitizeState(raw: unknown): AppState {
  const base = initialState();
  if (!raw || typeof raw !== 'object') return base;
  const r = raw as Record<string, unknown>;
  const library: Record<string, LibraryEntry> = {};
  const rawLib = r.library;
  const items = Array.isArray(rawLib) ? rawLib : rawLib && typeof rawLib === 'object' ? Object.values(rawLib) : [];
  for (const item of items) {
    const e = sanitizeEntry(item);
    if (e) library[e.id] = e;
  }
  const shelves = Array.isArray(r.shelves) ? r.shelves.map(sanitizeShelf).filter((s): s is Shelf => !!s) : base.shelves;
  return {
    version: STATE_VERSION,
    library,
    shelves,
    settings: sanitizeSettings(r.settings),
    chart: sanitizeChart(r.chart),
    chartPresets: Array.isArray(r.chartPresets) ? r.chartPresets.slice(0, 50).map((c) => sanitizeChart(c)) : [],
    onboarded: r.onboarded === true,
  };
}
