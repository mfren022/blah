import { describe, expect, it } from 'vitest';
import {
  buildSearchUrl,
  cleanDescription,
  mapAuthor,
  mapEdition,
  mapRatings,
  mapSearchDoc,
  mapShelfCounts,
  mapWork,
  pagesFromPagination,
} from '../src/api/openlibrary';
import { cleanSubjects, primaryGenre } from '../src/lib/genres';

// Fixtures follow the documented Open Library JSON shapes (fields are optional there,
// so several are deliberately missing or in their alternative forms).

describe('search', () => {
  it('builds a title/author search with fields and paging', () => {
    const url = new URL(buildSearchUrl({ title: 'the hobbit', author: 'tolkien', sort: 'rating', page: 2, limit: 20 }));
    expect(url.origin + url.pathname).toBe('https://openlibrary.org/search.json');
    expect(url.searchParams.get('title')).toBe('the hobbit');
    expect(url.searchParams.get('author')).toBe('tolkien');
    expect(url.searchParams.get('sort')).toBe('rating');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('fields')).toContain('number_of_pages_median');
  });

  it('combines a free query with year and ISBN filters', () => {
    const url = new URL(buildSearchUrl({ q: 'dune', isbn: '978-0-441-17271-9', yearFrom: 1960, yearTo: 1970 }));
    expect(url.searchParams.get('q')).toBe('dune isbn:9780441172719 first_publish_year:[1960 TO 1970]');
    expect(url.searchParams.has('sort')).toBe(false);
  });

  it('maps a search doc', () => {
    const r = mapSearchDoc({
      key: '/works/OL1W',
      title: 'A Title',
      author_name: ['First Author', 'Second'],
      author_key: ['OL1A', 'OL2A'],
      first_publish_year: 1937,
      cover_i: 123,
      edition_count: 40,
      number_of_pages_median: 310,
      subject: ['Fantasy fiction', 'Accessible book', 'Protected DAISY', 'Dragons'],
      ratings_average: 4.21,
      ratings_count: 900,
      want_to_read_count: 10,
      already_read_count: 5,
    });
    expect(r).toMatchObject({
      id: 'ol:OL1W',
      workKey: 'OL1W',
      authors: [{ name: 'First Author', key: 'OL1A' }, { name: 'Second', key: 'OL2A' }],
      coverId: 123,
      pagesMedian: 310,
      subjects: ['Fantasy Fiction', 'Dragons'],
      readers: { want: 10, reading: 0, read: 5 },
    });
  });

  it('rejects docs that are not works', () => {
    expect(mapSearchDoc({ key: '/books/OL1M', title: 'x' })).toBeNull();
    expect(mapSearchDoc({ key: '/works/OL1W' })).toBeNull();
    expect(mapSearchDoc(null)).toBeNull();
  });
});

describe('works, editions, ratings, authors', () => {
  it('maps a work with a typed description and nested author keys', () => {
    const w = mapWork({
      key: '/works/OL1W',
      title: 'A Title',
      description: { type: '/type/text', value: 'First paragraph [with a link](https://x.y).\r\n\r\nSecond.\n\n----------\nAlso contained in:\n[Omnibus](https://z)' },
      authors: [{ author: { key: '/authors/OL9A' }, type: { key: '/type/author_role' } }, { key: '/authors/OL8A' }],
      subjects: ['Fiction', 'Hobbits'],
      subject_places: ['Middle-earth'],
      covers: [-1, 55],
      excerpts: [{ excerpt: 'In a hole in the ground there lived a creature of some sort.' }],
      links: [{ title: 'Wiki', url: 'https://example.org' }, { url: 'javascript:alert(1)' }],
    });
    expect(w.description).toBe('First paragraph with a link.\n\nSecond.');
    expect(w.authorKeys).toEqual(['OL9A', 'OL8A']);
    expect(w.coverIds).toEqual([55]);
    expect(w.places).toEqual(['Middle-earth']);
    expect(w.excerpt).toContain('hole in the ground');
    expect(w.links).toEqual([{ title: 'Wiki', url: 'https://example.org' }]);
  });

  it('maps an edition, falling back to pagination for the page count', () => {
    const e = mapEdition({
      key: '/books/OL5M',
      publishers: ['Some House'],
      publish_date: 'March 1999',
      pagination: 'xii, 320 p.',
      physical_format: 'Paperback',
      isbn_13: ['9780000000001'],
      languages: [{ key: '/languages/eng' }],
      covers: [77],
    });
    expect(e).toMatchObject({ key: 'OL5M', publisher: 'Some House', year: 1999, pages: 320, format: 'Paperback', isbn13: '9780000000001', language: 'eng', coverId: 77 });
    expect(mapEdition({ key: '/books/OL6M', number_of_pages: 288 })?.pages).toBe(288);
  });

  it('reads page counts out of pagination strings', () => {
    expect(pagesFromPagination('xii, 320 p.')).toBe(320);
    expect(pagesFromPagination('512 pages')).toBe(512);
    expect(pagesFromPagination('[8], 240 p. :')).toBe(240);
    expect(pagesFromPagination(undefined)).toBeUndefined();
  });

  it('maps ratings and reader counts', () => {
    expect(mapRatings({ summary: { average: 4.2, count: 10 }, counts: { 1: 1, 2: 0, 3: 1, 4: 3, 5: 5 } })).toEqual({ average: 4.2, count: 10, counts: [1, 0, 1, 3, 5] });
    expect(mapRatings({ summary: {}, counts: {} })).toEqual({ count: 0, counts: [0, 0, 0, 0, 0] });
    expect(mapShelfCounts({ counts: { want_to_read: 7, currently_reading: 2, already_read: 9 } })).toEqual({ want: 7, reading: 2, read: 9 });
  });

  it('maps an author with a plain-string bio', () => {
    expect(mapAuthor({ key: '/authors/OL9A', name: 'Someone', bio: 'Born somewhere.', birth_date: '1900', photos: [12] })).toEqual({
      key: 'OL9A', name: 'Someone', bio: 'Born somewhere.', birthDate: '1900', deathDate: undefined, photoId: 12,
    });
  });

  it('cleans reference-style links and HTML from descriptions', () => {
    expect(cleanDescription('See [the sequel][1].<br>Fine.\n\n[1]: https://x')).toBe('See the sequel.\nFine.');
    expect(cleanDescription({ value: '   ' })).toBeUndefined();
  });
});

describe('subjects and genres', () => {
  it('cleans catalogue noise and dedupes', () => {
    expect(cleanSubjects(['Fiction, general', 'nyt:hardcover-fiction=2020-01-01', 'Ghost stories', 'ghost stories', 'Large type books'])).toEqual(['Ghost Stories']);
  });

  it('picks a primary genre in priority order, honouring overrides', () => {
    expect(primaryGenre(['Fiction', 'Science fiction', 'Space opera'])).toBe('Science Fiction');
    expect(primaryGenre(['Detective and mystery stories'])).toBe('Mystery');
    expect(primaryGenre(['Fiction'])).toBe('Literary Fiction');
    expect(primaryGenre([], 'Poetry')).toBe('Poetry');
    expect(primaryGenre(undefined)).toBe('Uncategorised');
  });
});
