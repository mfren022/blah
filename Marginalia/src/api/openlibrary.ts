/**
 * Open Library client (https://openlibrary.org/developers/api).
 *
 * Every field in Open Library records is optional and loosely typed, so all mapping
 * is defensive. Results are mapped to compact shapes and cached (memory + localStorage)
 * so revisiting a book is instant and recently viewed books still load offline.
 */
import type { AuthorRef, BookSnapshot, EditionInfo } from '../lib/types';
import { cleanSubjects } from '../lib/genres';

const BASE = 'https://openlibrary.org';
const COVERS = 'https://covers.openlibrary.org';

export type SortKey = 'relevance' | 'readinglog' | 'rating' | 'editions' | 'new' | 'old';
export const SORT_LABELS: Record<SortKey, string> = {
  relevance: 'Relevance',
  readinglog: 'Most read',
  rating: 'Highest rated',
  editions: 'Most editions',
  new: 'Newest',
  old: 'Oldest',
};

export interface SearchParams {
  q?: string;
  title?: string;
  author?: string;
  subject?: string;
  isbn?: string;
  language?: string;
  yearFrom?: number;
  yearTo?: number;
  sort?: SortKey;
  page?: number;
  limit?: number;
}

export interface SearchResult {
  id: string;
  workKey: string;
  title: string;
  subtitle?: string;
  authors: AuthorRef[];
  coverId?: number;
  firstPublishYear?: number;
  editionCount?: number;
  pagesMedian?: number;
  subjects?: string[];
  ratingAverage?: number;
  ratingCount?: number;
  readers?: ShelfCounts;
  source: 'openlibrary' | 'sample' | 'manual';
}

export interface SearchPage {
  total: number;
  results: SearchResult[];
}

export interface WorkDetails {
  workKey: string;
  title: string;
  subtitle?: string;
  description?: string;
  subjects: string[];
  places: string[];
  people: string[];
  times: string[];
  coverIds: number[];
  authorKeys: string[];
  firstPublishDate?: string;
  excerpt?: string;
  links: { title: string; url: string }[];
}

export interface RatingsSummary {
  average?: number;
  count: number;
  /** counts[0] = 1 star … counts[4] = 5 stars */
  counts: [number, number, number, number, number];
}

export interface ShelfCounts {
  want: number;
  reading: number;
  read: number;
}

export interface AuthorDetails {
  key: string;
  name: string;
  bio?: string;
  birthDate?: string;
  deathDate?: string;
  photoId?: number;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public kind: 'offline' | 'http' | 'parse' | 'timeout' | 'notfound',
    public status?: number,
  ) {
    super(message);
  }
}

/* ------------------------------------------------------------------ */
/* Low-level fetching + caching                                        */
/* ------------------------------------------------------------------ */

interface CacheRecord {
  t: number;
  v: unknown;
}

const CACHE_KEY = 'marginalia:olcache';
const CACHE_LIMIT = 120;
const memory = new Map<string, CacheRecord>();
const inflight = new Map<string, Promise<unknown>>();
let persistent: Record<string, CacheRecord> | null = null;
let persistTimer: ReturnType<typeof setTimeout> | undefined;

function persistentCache(): Record<string, CacheRecord> {
  if (persistent) return persistent;
  try {
    persistent = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}') ?? {};
  } catch {
    persistent = {};
  }
  return persistent!;
}

function schedulePersist() {
  try {
    if (typeof localStorage === 'undefined') return;
  } catch {
    return; // storage is blocked (e.g. a sandboxed frame)
  }
  clearTimeout(persistTimer);
  persistTimer = setTimeout(() => {
    const cache = persistentCache();
    const keys = Object.keys(cache).sort((a, b) => cache[b].t - cache[a].t);
    for (const k of keys.slice(CACHE_LIMIT)) delete cache[k];
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
    } catch {
      // Quota exceeded: drop the older half and try once more.
      for (const k of keys.slice(CACHE_LIMIT / 2)) delete cache[k];
      try {
        localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
      } catch {
        /* give up quietly — the cache is an optimisation */
      }
    }
  }, 500);
}

export function clearApiCache() {
  memory.clear();
  persistent = {};
  try {
    localStorage.removeItem(CACHE_KEY);
  } catch {
    /* ignore */
  }
}

async function fetchJSON(url: string, timeoutMs = 15000): Promise<unknown> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (res.status === 404) throw new ApiError('Not found on Open Library', 'notfound', 404);
    if (!res.ok) throw new ApiError(`Open Library returned ${res.status}`, 'http', res.status);
    try {
      return await res.json();
    } catch {
      throw new ApiError('Open Library sent an unreadable response', 'parse');
    }
  } catch (err) {
    if (err instanceof ApiError) throw err;
    if ((err as Error)?.name === 'AbortError') throw new ApiError('Open Library took too long to answer', 'timeout');
    throw new ApiError('Could not reach Open Library — you may be offline', 'offline');
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Fetch + map with caching. Fresh cache hits skip the network; on network failure a
 * stale cached value is returned instead of an error.
 */
async function cached<T>(key: string, ttlMs: number, load: () => Promise<T>, persist = true): Promise<T> {
  const now = Date.now();
  const hit = memory.get(key) ?? (persist ? persistentCache()[key] : undefined);
  if (hit && now - hit.t < ttlMs) return hit.v as T;
  const pending = inflight.get(key);
  if (pending) return pending as Promise<T>;
  const p = (async () => {
    try {
      const v = await load();
      const rec = { t: Date.now(), v };
      memory.set(key, rec);
      if (persist) {
        persistentCache()[key] = rec;
        schedulePersist();
      }
      return v;
    } catch (err) {
      if (hit && !(err instanceof ApiError && err.kind === 'notfound')) return hit.v as T;
      throw err;
    } finally {
      inflight.delete(key);
    }
  })();
  inflight.set(key, p);
  return p;
}

const HOUR = 3_600_000;
const DAY = 24 * HOUR;

/* ------------------------------------------------------------------ */
/* Mapping helpers (exported for tests)                                */
/* ------------------------------------------------------------------ */

const asString = (v: unknown): string | undefined => (typeof v === 'string' && v.trim() ? v.trim() : undefined);
const asNumber = (v: unknown): number | undefined => (typeof v === 'number' && Number.isFinite(v) ? v : undefined);
const asStrings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);

/** `/works/OL45804W` → `OL45804W` */
export const stripKey = (key: unknown): string => (typeof key === 'string' ? key.split('/').filter(Boolean).pop() ?? '' : '');

export function coverUrl(coverId: number | undefined, size: 'S' | 'M' | 'L' = 'M'): string | undefined {
  return coverId && coverId > 0 ? `${COVERS}/b/id/${coverId}-${size}.jpg` : undefined;
}

export function authorPhotoUrl(photoId: number | undefined, size: 'S' | 'M' | 'L' = 'M'): string | undefined {
  return photoId && photoId > 0 ? `${COVERS}/a/id/${photoId}-${size}.jpg` : undefined;
}

export const workUrl = (workKey: string) => `${BASE}/works/${workKey}`;

/** Open Library text fields are either a string or `{ type: '/type/text', value }`. */
export function textValue(v: unknown): string | undefined {
  if (typeof v === 'string') return v;
  if (v && typeof v === 'object' && typeof (v as { value?: unknown }).value === 'string') return (v as { value: string }).value;
  return undefined;
}

/** Strip Markdown links, link lists and stray HTML from a description. */
export function cleanDescription(raw: unknown): string | undefined {
  let t = textValue(raw);
  if (!t) return undefined;
  t = t.replace(/\r\n?/g, '\n');
  t = t.split(/\n\s*(?:-{3,}|\*{3,}|_{3,})\s*\n/)[0];
  t = t
    .replace(/\[([^\]]+)\]\[\d+\]/g, '$1')
    .replace(/\[([^\]]+)\]\((?:[^)]+)\)/g, '$1')
    .replace(/^\s*\[\d+\]:\s*\S+.*$/gm, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/\(\[source\]\)/gi, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  return t || undefined;
}

export function yearFrom(s: unknown): number | undefined {
  const m = typeof s === 'string' ? s.match(/\b(1[0-9]{3}|20[0-9]{2})\b/) : null;
  return m ? Number(m[1]) : undefined;
}

/** "xii, 320 p." → 320 */
export function pagesFromPagination(s: unknown): number | undefined {
  if (typeof s !== 'string') return undefined;
  const nums = [...s.matchAll(/(\d{1,5})\s*(?:p\b|pp\b|pages?\b|S\.|$)/gi)].map((m) => Number(m[1]));
  const n = nums.length ? Math.max(...nums) : Number(s.match(/\d{2,5}/)?.[0]);
  return n && n > 0 && n < 100000 ? n : undefined;
}

export function mapSearchDoc(doc: unknown): SearchResult | null {
  if (!doc || typeof doc !== 'object') return null;
  const d = doc as Record<string, unknown>;
  const workKey = stripKey(d.key);
  const title = asString(d.title);
  if (!workKey || !title || !workKey.endsWith('W')) return null;
  const names = asStrings(d.author_name);
  const keys = asStrings(d.author_key);
  const authors: AuthorRef[] = names.map((name, i) => (keys[i] ? { name, key: stripKey(keys[i]) } : { name }));
  const want = asNumber(d.want_to_read_count);
  const reading = asNumber(d.currently_reading_count);
  const read = asNumber(d.already_read_count);
  return {
    id: `ol:${workKey}`,
    workKey,
    title,
    subtitle: asString(d.subtitle),
    authors,
    coverId: asNumber(d.cover_i) ?? asNumber(d.cover_id),
    firstPublishYear: asNumber(d.first_publish_year),
    editionCount: asNumber(d.edition_count),
    pagesMedian: asNumber(d.number_of_pages_median),
    subjects: cleanSubjects(asStrings(d.subject), 10),
    ratingAverage: asNumber(d.ratings_average),
    ratingCount: asNumber(d.ratings_count),
    readers: want !== undefined || reading !== undefined || read !== undefined ? { want: want ?? 0, reading: reading ?? 0, read: read ?? 0 } : undefined,
    source: 'openlibrary',
  };
}

export function mapWork(raw: unknown): WorkDetails {
  const w = (raw ?? {}) as Record<string, unknown>;
  const authorKeys = Array.isArray(w.authors)
    ? w.authors
        .map((a) => {
          const r = a as Record<string, unknown>;
          return stripKey((r?.author as Record<string, unknown>)?.key ?? r?.key);
        })
        .filter(Boolean)
    : [];
  const excerpts = Array.isArray(w.excerpts) ? w.excerpts : [];
  const firstExcerpt = excerpts.map((e) => textValue((e as Record<string, unknown>)?.excerpt)).find((x) => x && x.length > 20);
  const links = Array.isArray(w.links)
    ? w.links
        .map((l) => l as Record<string, unknown>)
        .filter((l) => typeof l?.url === 'string' && /^https?:\/\//.test(l.url as string))
        .map((l) => ({ title: asString(l.title) ?? (l.url as string), url: l.url as string }))
        .slice(0, 6)
    : [];
  return {
    workKey: stripKey(w.key),
    title: asString(w.title) ?? 'Untitled',
    subtitle: asString(w.subtitle),
    description: cleanDescription(w.description),
    subjects: cleanSubjects(asStrings(w.subjects), 16),
    places: cleanSubjects(asStrings(w.subject_places), 8),
    people: cleanSubjects(asStrings(w.subject_people), 8),
    times: cleanSubjects(asStrings(w.subject_times), 6),
    coverIds: (Array.isArray(w.covers) ? w.covers : []).filter((c): c is number => typeof c === 'number' && c > 0),
    authorKeys,
    firstPublishDate: asString(w.first_publish_date),
    excerpt: firstExcerpt ? cleanDescription(firstExcerpt) : undefined,
    links,
  };
}

const LANGUAGE_NAMES: Record<string, string> = {
  eng: 'English', fre: 'French', ger: 'German', spa: 'Spanish', ita: 'Italian', por: 'Portuguese', dut: 'Dutch',
  swe: 'Swedish', dan: 'Danish', nor: 'Norwegian', fin: 'Finnish', pol: 'Polish', rus: 'Russian', jpn: 'Japanese',
  chi: 'Chinese', kor: 'Korean', ara: 'Arabic', heb: 'Hebrew', tur: 'Turkish', gre: 'Greek', hun: 'Hungarian',
  cze: 'Czech', hin: 'Hindi', lat: 'Latin', ukr: 'Ukrainian', cat: 'Catalan', ice: 'Icelandic', wel: 'Welsh', gle: 'Irish',
};
export const languageName = (code?: string) => (code ? LANGUAGE_NAMES[code] ?? code.toUpperCase() : undefined);

export function mapEdition(raw: unknown): EditionInfo | null {
  if (!raw || typeof raw !== 'object') return null;
  const e = raw as Record<string, unknown>;
  const key = stripKey(e.key);
  if (!key) return null;
  const languages = Array.isArray(e.languages) ? e.languages.map((l) => stripKey((l as Record<string, unknown>)?.key)).filter(Boolean) : [];
  const covers = (Array.isArray(e.covers) ? e.covers : []).filter((c): c is number => typeof c === 'number' && c > 0);
  const publishDate = asString(e.publish_date);
  return {
    key,
    title: asString(e.title),
    publisher: asStrings(e.publishers)[0],
    publishDate,
    year: yearFrom(publishDate),
    pages: asNumber(e.number_of_pages) && (e.number_of_pages as number) > 0 ? (e.number_of_pages as number) : pagesFromPagination(e.pagination),
    format: asString(e.physical_format),
    isbn13: asStrings(e.isbn_13)[0],
    isbn10: asStrings(e.isbn_10)[0],
    language: languages[0],
    coverId: covers[0],
  };
}

export function mapRatings(raw: unknown): RatingsSummary | null {
  const r = (raw ?? {}) as Record<string, unknown>;
  const summary = (r.summary ?? {}) as Record<string, unknown>;
  const countsRaw = (r.counts ?? {}) as Record<string, unknown>;
  const counts = [1, 2, 3, 4, 5].map((n) => asNumber(countsRaw[String(n)]) ?? 0) as RatingsSummary['counts'];
  const total = asNumber(summary.count) ?? counts.reduce((a, b) => a + b, 0);
  if (!total) return { count: 0, counts };
  return { average: asNumber(summary.average), count: total, counts };
}

export function mapShelfCounts(raw: unknown): ShelfCounts | null {
  const c = ((raw ?? {}) as Record<string, unknown>).counts as Record<string, unknown> | undefined;
  if (!c) return null;
  return { want: asNumber(c.want_to_read) ?? 0, reading: asNumber(c.currently_reading) ?? 0, read: asNumber(c.already_read) ?? 0 };
}

export function mapAuthor(raw: unknown): AuthorDetails | null {
  const a = (raw ?? {}) as Record<string, unknown>;
  const name = asString(a.name) ?? asString(a.personal_name);
  if (!name) return null;
  const photos = (Array.isArray(a.photos) ? a.photos : []).filter((p): p is number => typeof p === 'number' && p > 0);
  return {
    key: stripKey(a.key),
    name,
    bio: cleanDescription(a.bio),
    birthDate: asString(a.birth_date),
    deathDate: asString(a.death_date),
    photoId: photos[0],
  };
}

export function snapshotFromResult(r: SearchResult): BookSnapshot {
  return {
    id: r.id,
    source: r.source,
    workKey: r.source === 'openlibrary' ? r.workKey : undefined,
    title: r.title,
    subtitle: r.subtitle,
    authors: r.authors,
    coverId: r.coverId,
    firstPublishYear: r.firstPublishYear,
    subjects: r.subjects,
    pagesMedian: r.pagesMedian,
    editionCount: r.editionCount,
  };
}

/* ------------------------------------------------------------------ */
/* Endpoints                                                           */
/* ------------------------------------------------------------------ */

const SEARCH_FIELDS = [
  'key', 'title', 'subtitle', 'author_name', 'author_key', 'first_publish_year', 'cover_i', 'edition_count',
  'number_of_pages_median', 'subject', 'ratings_average', 'ratings_count', 'want_to_read_count',
  'currently_reading_count', 'already_read_count',
].join(',');

export function buildSearchUrl(p: SearchParams): string {
  const params = new URLSearchParams();
  const q: string[] = [];
  if (p.q?.trim()) q.push(p.q.trim());
  if (p.isbn?.trim()) q.push(`isbn:${p.isbn.replace(/[^0-9Xx]/g, '')}`);
  if (p.yearFrom || p.yearTo) q.push(`first_publish_year:[${p.yearFrom ?? '*'} TO ${p.yearTo ?? '*'}]`);
  if (q.length) params.set('q', q.join(' '));
  if (p.title?.trim()) params.set('title', p.title.trim());
  if (p.author?.trim()) params.set('author', p.author.trim());
  if (p.subject?.trim()) params.set('subject', p.subject.trim());
  if (p.language) params.set('language', p.language);
  if (p.sort && p.sort !== 'relevance') params.set('sort', p.sort);
  params.set('page', String(Math.max(1, p.page ?? 1)));
  params.set('limit', String(p.limit ?? 24));
  params.set('fields', SEARCH_FIELDS);
  return `${BASE}/search.json?${params.toString()}`;
}

export function searchBooks(p: SearchParams): Promise<SearchPage> {
  const url = buildSearchUrl(p);
  return cached(
    url,
    6 * HOUR,
    async () => {
      const data = (await fetchJSON(url)) as Record<string, unknown>;
      const docs = Array.isArray(data?.docs) ? data.docs : [];
      return {
        total: asNumber(data?.numFound) ?? asNumber(data?.num_found) ?? docs.length,
        results: docs.map(mapSearchDoc).filter((r): r is SearchResult => !!r),
      };
    },
    false,
  );
}

export function getTrending(period: 'daily' | 'weekly' | 'monthly' | 'yearly' = 'weekly', limit = 18): Promise<SearchResult[]> {
  const url = `${BASE}/trending/${period}.json?limit=${limit}`;
  return cached(url, 6 * HOUR, async () => {
    const data = (await fetchJSON(url)) as Record<string, unknown>;
    const works = Array.isArray(data?.works) ? data.works : [];
    return works.map(mapSearchDoc).filter((r): r is SearchResult => !!r);
  });
}

export function getWork(workKey: string): Promise<WorkDetails> {
  const url = `${BASE}/works/${encodeURIComponent(workKey)}.json`;
  return cached(url, 7 * DAY, async () => mapWork(await fetchJSON(url)));
}

export function getEditions(workKey: string, limit = 50, offset = 0): Promise<{ total: number; editions: EditionInfo[] }> {
  const url = `${BASE}/works/${encodeURIComponent(workKey)}/editions.json?limit=${limit}&offset=${offset}`;
  return cached(url, 7 * DAY, async () => {
    const data = (await fetchJSON(url)) as Record<string, unknown>;
    const entries = Array.isArray(data?.entries) ? data.entries : [];
    return {
      total: asNumber(data?.size) ?? entries.length,
      editions: entries.map(mapEdition).filter((e): e is EditionInfo => !!e),
    };
  });
}

export function getRatings(workKey: string): Promise<RatingsSummary | null> {
  const url = `${BASE}/works/${encodeURIComponent(workKey)}/ratings.json`;
  return cached(url, DAY, async () => mapRatings(await fetchJSON(url)));
}

export function getShelfCounts(workKey: string): Promise<ShelfCounts | null> {
  const url = `${BASE}/works/${encodeURIComponent(workKey)}/bookshelves.json`;
  return cached(url, DAY, async () => mapShelfCounts(await fetchJSON(url)));
}

export function getAuthor(authorKey: string): Promise<AuthorDetails | null> {
  const url = `${BASE}/authors/${encodeURIComponent(authorKey)}.json`;
  return cached(url, 14 * DAY, async () => mapAuthor(await fetchJSON(url)));
}
