/**
 * Import reading history from a Goodreads or StoryGraph CSV export.
 * Produces library entries and custom shelves; nothing touches the store directly.
 */
import type { BookFormat, LibraryEntry, ReadSession, Shelf, Status } from '../lib/types';
import { isISODate, nowLocal, today } from '../lib/dates';
import { hashString } from '../lib/util';

/** RFC 4180-ish CSV parser (quoted fields, escaped quotes, CRLF/LF, embedded newlines). */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let i = 0;
  let quoted = false;
  const src = text.replace(/^﻿/, '');
  while (i < src.length) {
    const c = src[i];
    if (quoted) {
      if (c === '"') {
        if (src[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        quoted = false;
        i++;
        continue;
      }
      field += c;
      i++;
      continue;
    }
    if (c === '"') {
      quoted = true;
      i++;
    } else if (c === ',') {
      row.push(field);
      field = '';
      i++;
    } else if (c === '\n' || c === '\r') {
      row.push(field);
      field = '';
      if (row.some((f) => f !== '')) rows.push(row);
      row = [];
      i += c === '\r' && src[i + 1] === '\n' ? 2 : 1;
    } else {
      field += c;
      i++;
    }
  }
  row.push(field);
  if (row.some((f) => f !== '')) rows.push(row);
  return rows;
}

/** "2023/05/14", "2023-05-14", "05/14/2023" → ISO date (or undefined). */
export function csvDate(s: string | undefined): string | undefined {
  if (!s) return undefined;
  const t = s.trim();
  let m = t.match(/^(\d{4})[/-](\d{1,2})[/-](\d{1,2})/);
  if (m) {
    const iso = `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`;
    return isISODate(iso) ? iso : undefined;
  }
  m = t.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (m) {
    const iso = `${m[3]}-${m[1].padStart(2, '0')}-${m[2].padStart(2, '0')}`;
    return isISODate(iso) ? iso : undefined;
  }
  return undefined;
}

const unexcel = (s: string | undefined) => (s ?? '').replace(/^="?|"$/g, '').trim();
const stripHtml = (s: string) => s.replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;/g, "'").trim();
const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

function formatOf(s: string | undefined): BookFormat | undefined {
  const f = (s ?? '').toLowerCase();
  if (!f) return undefined;
  if (/audio/.test(f)) return 'audiobook';
  if (/kindle|ebook|e-book|digital|nook/.test(f)) return 'ebook';
  if (/hard/.test(f)) return 'hardcover';
  if (/paper|mass market|print/.test(f)) return 'paperback';
  return 'other';
}

function statusOf(s: string): Status {
  const v = s.toLowerCase().trim();
  if (v === 'read') return 'finished';
  if (v === 'currently-reading' || v === 'reading') return 'reading';
  if (/dnf|did-not-finish|abandon|gave-up/.test(v)) return 'dnf';
  return 'want';
}

export interface CsvImport {
  source: 'goodreads' | 'storygraph';
  entries: LibraryEntry[];
  shelves: Shelf[];
  skipped: number;
}

const SHELF_COLORS = ['#2e6b52', '#2f5b9a', '#8e4f1f', '#6d4fb0', '#a33a3a', '#237a7f', '#b8923a', '#5a5226'];

export function importReadingCsv(text: string): CsvImport {
  const rows = parseCsv(text);
  if (rows.length < 2) throw new Error('That file has no rows to import.');
  const head = rows[0].map((h) => h.trim().toLowerCase());
  const col = (name: string) => head.indexOf(name.toLowerCase());
  const get = (r: string[], name: string) => {
    const i = col(name);
    return i >= 0 ? r[i] ?? '' : '';
  };
  const isGoodreads = col('exclusive shelf') >= 0;
  const isStoryGraph = col('read status') >= 0;
  if (!isGoodreads && !isStoryGraph) throw new Error('This does not look like a Goodreads or StoryGraph export.');

  const shelves = new Map<string, Shelf>();
  const entries: LibraryEntry[] = [];
  let skipped = 0;
  const stamp = nowLocal();

  for (const r of rows.slice(1)) {
    const title = get(r, 'title').trim();
    if (!title) {
      skipped++;
      continue;
    }
    const authorField = isGoodreads ? get(r, 'author') : get(r, 'authors');
    const extra = isGoodreads ? get(r, 'additional authors') : '';
    const authors = [...authorField.split(/\s*,\s*/), ...extra.split(/\s*,\s*/)].map((a) => a.trim()).filter(Boolean).map((name) => ({ name }));
    const isbn = unexcel(isGoodreads ? get(r, 'isbn13') || get(r, 'isbn') : get(r, 'isbn/uid'));
    const pages = Number(isGoodreads ? get(r, 'number of pages') : '') || undefined;
    const year = Number(isGoodreads ? get(r, 'original publication year') || get(r, 'year published') : '') || undefined;
    const ratingRaw = Number(isGoodreads ? get(r, 'my rating') : get(r, 'star rating'));
    const rating = ratingRaw > 0 ? Math.round(Math.min(5, ratingRaw) * 2) / 2 : undefined;
    const status = statusOf(isGoodreads ? get(r, 'exclusive shelf') : get(r, 'read status'));
    const added = csvDate(get(r, 'date added')) ?? today();
    const review = stripHtml(isGoodreads ? get(r, 'my review') : get(r, 'review')) || undefined;
    const format = formatOf(isGoodreads ? get(r, 'binding') : get(r, 'format'));

    // Read-throughs.
    const sessions: ReadSession[] = [];
    if (isStoryGraph && get(r, 'dates read')) {
      for (const span of get(r, 'dates read').split(/\s*,\s*/)) {
        const [a, b] = span.split('-').map((x) => csvDate(x));
        if (a || b) sessions.push({ id: `s${sessions.length}`, startedAt: b ? a : undefined, endedAt: b ?? a, outcome: 'finished', progress: [] });
      }
    }
    const lastRead = csvDate(isGoodreads ? get(r, 'date read') : get(r, 'last date read'));
    if (status === 'finished' && sessions.length === 0) sessions.push({ id: 's0', endedAt: lastRead ?? added, outcome: 'finished', progress: [] });
    if (status === 'reading') sessions.push({ id: 's0', startedAt: added, outcome: 'reading', progress: [] });
    if (status === 'dnf') sessions.push({ id: 's0', startedAt: added, endedAt: lastRead ?? added, outcome: 'dnf', progress: [] });
    if (status === 'finished' && sessions.length) {
      // Last read-through is the most recent; mark earlier spans finished too.
      sessions.sort((x, y) => (x.endedAt ?? '').localeCompare(y.endedAt ?? ''));
    }

    // Custom shelves (Goodreads "Bookshelves", StoryGraph "Tags").
    const shelfNames = (isGoodreads ? get(r, 'bookshelves') : get(r, 'tags'))
      .split(/\s*,\s*/)
      .map((s) => s.trim())
      .filter((s) => s && !['read', 'to-read', 'currently-reading'].includes(s.toLowerCase()) && statusOf(s) !== 'dnf');
    const shelfIds = shelfNames.map((name) => {
      const id = `shelf-${slug(name)}`;
      if (!shelves.has(id)) shelves.set(id, { id, name: name.replace(/-/g, ' '), color: SHELF_COLORS[shelves.size % SHELF_COLORS.length], createdAt: today() });
      return id;
    });

    const key = isGoodreads ? get(r, 'book id') || `${title}|${authorField}` : `${title}|${authorField}|${isbn}`;
    const id = `manual:${isGoodreads ? 'gr' : 'sg'}-${hashString(key).toString(36)}`;
    entries.push({
      id,
      book: { id, source: 'manual', title, authors, firstPublishYear: year, pagesMedian: pages },
      status,
      addedAt: added,
      queuedAt: added,
      shelves: shelfIds,
      edition: isbn || pages ? { key: `import-${hashString(id).toString(36)}`, publisher: (isGoodreads ? get(r, 'publisher') : '') || undefined, pages, isbn13: isbn.length === 13 ? isbn : undefined, isbn10: isbn.length === 10 ? isbn : undefined, format: isGoodreads ? get(r, 'binding') || undefined : get(r, 'format') || undefined } : undefined,
      pageCount: pages,
      format,
      sessions,
      rating,
      review,
      reviewedAt: review || rating ? lastRead ?? added : undefined,
      updatedAt: stamp,
    });
  }
  return { source: isGoodreads ? 'goodreads' : 'storygraph', entries, shelves: [...shelves.values()], skipped };
}
