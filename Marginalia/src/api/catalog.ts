/**
 * One interface over the three places book data can come from:
 * Open Library (online), the fictional sample catalogue, and books the reader added by hand.
 */
import type { BookSnapshot, EditionInfo, LibraryEntry } from '../lib/types';
import { median } from '../lib/util';
import {
  getAuthor,
  getEditions,
  getRatings,
  getShelfCounts,
  getWork,
  searchBooks,
  yearFrom,
  type AuthorDetails,
  type RatingsSummary,
  type SearchPage,
  type SearchParams,
  type SearchResult,
  type ShelfCounts,
  type WorkDetails,
} from './openlibrary';
import { getSampleBook, sampleAuthor, sampleEditions, sampleRatings, sampleReaders, sampleSnapshot, sampleWork, searchSample } from '../data/sample';

/** Results seen in search lists, so book pages can render instantly. */
const seen = new Map<string, SearchResult>();
export function remember(results: SearchResult[]) {
  for (const r of results) seen.set(r.id, r);
}
export const seenResult = (id: string) => seen.get(id);

export const sourceOf = (id: string): 'openlibrary' | 'sample' | 'manual' =>
  id.startsWith('ol:') ? 'openlibrary' : id.startsWith('sample:') ? 'sample' : 'manual';

export const workKeyOf = (id: string) => id.replace(/^ol:/, '');

export interface LoadedWork {
  snapshot: BookSnapshot;
  work?: WorkDetails;
  authors: AuthorDetails[];
}

export async function loadWork(id: string, known?: BookSnapshot): Promise<LoadedWork> {
  const source = sourceOf(id);
  if (source === 'sample') {
    const b = getSampleBook(id);
    if (!b) throw new Error('This sample book no longer exists.');
    return { snapshot: sampleSnapshot(b), work: sampleWork(b), authors: b.authors.map(sampleAuthor) };
  }
  if (source === 'manual') {
    if (!known) throw new Error('This book is not in your library.');
    return { snapshot: known, authors: known.authors.map((a) => ({ key: '', name: a.name })) };
  }
  const workKey = workKeyOf(id);
  const work = await getWork(workKey);
  const hint = known ?? (seen.get(id) ? snapshotFrom(seen.get(id)!) : undefined);
  const authorKeys = work.authorKeys.slice(0, 4);
  const authors = (
    await Promise.all(authorKeys.map((k) => getAuthor(k).catch(() => null)))
  ).filter((a): a is AuthorDetails => !!a);
  const authorRefs = authors.length ? authors.map((a) => ({ name: a.name, key: a.key })) : hint?.authors ?? [];
  return {
    work,
    authors,
    snapshot: {
      id,
      source: 'openlibrary',
      workKey,
      title: work.title || hint?.title || 'Untitled',
      subtitle: work.subtitle ?? hint?.subtitle,
      authors: authorRefs,
      coverId: work.coverIds[0] ?? hint?.coverId,
      firstPublishYear: hint?.firstPublishYear ?? yearFrom(work.firstPublishDate),
      subjects: work.subjects.length ? work.subjects : hint?.subjects,
      pagesMedian: hint?.pagesMedian,
      editionCount: hint?.editionCount,
    },
  };
}

function snapshotFrom(r: SearchResult): BookSnapshot {
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
export { snapshotFrom };

export async function loadEditions(id: string, entry?: LibraryEntry, offset = 0): Promise<{ total: number; editions: EditionInfo[] }> {
  const source = sourceOf(id);
  if (source === 'sample') {
    const b = getSampleBook(id);
    const editions = b ? sampleEditions(b) : [];
    return { total: editions.length, editions };
  }
  if (source === 'manual') {
    const editions = entry?.edition ? [entry.edition] : [];
    return { total: editions.length, editions };
  }
  return getEditions(workKeyOf(id), 50, offset);
}

export async function loadRatings(id: string): Promise<RatingsSummary | null> {
  const source = sourceOf(id);
  if (source === 'sample') {
    const b = getSampleBook(id);
    return b ? sampleRatings(b) : null;
  }
  if (source === 'manual') return null;
  return getRatings(workKeyOf(id));
}

export async function loadReaders(id: string): Promise<ShelfCounts | null> {
  const source = sourceOf(id);
  if (source === 'sample') {
    const b = getSampleBook(id);
    return b ? sampleReaders(b) : null;
  }
  if (source === 'manual') return null;
  return getShelfCounts(workKeyOf(id));
}

export function medianPages(editions: EditionInfo[]): number | undefined {
  const m = median(editions.map((e) => e.pages).filter((p): p is number => !!p && p > 20));
  return m ? Math.round(m) : undefined;
}

/** Search the local sample catalogue plus the reader's own library. */
export function searchLocal(params: SearchParams, library: LibraryEntry[]): SearchPage {
  const sample = searchSample(params);
  const norm = (s?: string) => (s ?? '').toLowerCase().trim();
  const q = norm(params.q);
  const t = norm(params.title);
  const a = norm(params.author);
  const mine: SearchResult[] = library
    .filter((e) => e.book.source !== 'sample')
    .filter((e) => {
      const authors = e.book.authors.map((x) => x.name).join(' ').toLowerCase();
      if (t && !e.book.title.toLowerCase().includes(t)) return false;
      if (a && !authors.includes(a)) return false;
      if (q) {
        const hay = `${e.book.title} ${authors} ${(e.book.subjects ?? []).join(' ')}`.toLowerCase();
        return q.split(/\s+/).every((w) => hay.includes(w));
      }
      return !!(t || a || params.subject);
    })
    .map((e) => ({
      id: e.id,
      workKey: e.book.workKey ?? e.id,
      title: e.book.title,
      subtitle: e.book.subtitle,
      authors: e.book.authors,
      coverId: e.book.coverId,
      firstPublishYear: e.book.firstPublishYear,
      editionCount: e.book.editionCount,
      pagesMedian: e.pageCount ?? e.book.pagesMedian,
      subjects: e.book.subjects,
      source: e.book.source,
    }));
  const results = [...mine, ...sample];
  return { total: results.length, results };
}

export async function search(params: SearchParams, online: boolean, library: LibraryEntry[]): Promise<SearchPage & { source: 'openlibrary' | 'local' }> {
  if (!online) return { ...searchLocal(params, library), source: 'local' };
  const page = await searchBooks(params);
  remember(page.results);
  return { ...page, source: 'openlibrary' };
}
