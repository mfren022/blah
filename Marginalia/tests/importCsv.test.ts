import { describe, expect, it } from 'vitest';
import { csvDate, importReadingCsv, parseCsv } from '../src/data/importCsv';
import { sanitizeState } from '../src/data/model';

describe('CSV parsing', () => {
  it('handles quotes, escaped quotes, commas and newlines inside fields', () => {
    expect(parseCsv('a,b\r\n"x, y","he said ""hi""\nthen left"\n')).toEqual([
      ['a', 'b'],
      ['x, y', 'he said "hi"\nthen left'],
    ]);
  });

  it('reads the common date styles', () => {
    expect(csvDate('2023/05/14')).toBe('2023-05-14');
    expect(csvDate('2023-5-4')).toBe('2023-05-04');
    expect(csvDate('05/14/2023')).toBe('2023-05-14');
    expect(csvDate('')).toBeUndefined();
    expect(csvDate('2023/02/30')).toBeUndefined();
  });
});

describe('Goodreads export', () => {
  const csv = [
    'Book Id,Title,Author,Author l-f,Additional Authors,ISBN,ISBN13,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Bookshelves with positions,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Owned Copies',
    '1,"A Test, Novel",Jane Doe,"Doe, Jane",,"=""0000000000""","=""9780000000002""",4,3.9,House,Paperback,320,2020,2019,2024/03/02,2024/01/15,"favourites, book-club","favourites (#1)",read,"Loved it.<br/>Truly.",,,1,0',
    '2,Unread Thing,Sam Roe,"Roe, Sam",,,,0,4.1,,Kindle Edition,,,,,2025/06/01,to-read,,to-read,,,,0,0',
    '3,Abandoned,Pat Poe,"Poe, Pat",,,,0,3,,Hardcover,500,,,,2025/02/01,did-not-finish,,did-not-finish,,,,0,0',
  ].join('\n');

  it('maps shelves, dates, ratings, reviews and formats', () => {
    const r = importReadingCsv(csv);
    expect(r.source).toBe('goodreads');
    expect(r.entries).toHaveLength(3);
    const [read, want, dnf] = r.entries;
    expect(read.book.title).toBe('A Test, Novel');
    expect(read.status).toBe('finished');
    expect(read.sessions[0]).toMatchObject({ endedAt: '2024-03-02', outcome: 'finished' });
    expect(read.rating).toBe(4);
    expect(read.review).toBe('Loved it.\nTruly.');
    expect(read.pageCount).toBe(320);
    expect(read.format).toBe('paperback');
    expect(read.edition?.isbn13).toBe('9780000000002');
    expect(read.book.firstPublishYear).toBe(2019);
    expect(read.shelves).toEqual(['shelf-favourites', 'shelf-book-club']);
    expect(want).toMatchObject({ status: 'want', addedAt: '2025-06-01', format: 'ebook' });
    expect(want.rating).toBeUndefined();
    expect(dnf.status).toBe('dnf');
    expect(r.shelves.map((s) => s.name)).toEqual(['favourites', 'book club']);
  });

  it('produces entries that survive sanitising', () => {
    const r = importReadingCsv(csv);
    const state = sanitizeState({ library: r.entries, shelves: r.shelves });
    expect(Object.keys(state.library)).toHaveLength(3);
  });

  it('gives stable ids so re-importing does not duplicate', () => {
    expect(importReadingCsv(csv).entries.map((e) => e.id)).toEqual(importReadingCsv(csv).entries.map((e) => e.id));
  });
});

describe('StoryGraph export', () => {
  it('reads every dated read-through', () => {
    const csv = [
      'Title,Authors,Contributors,ISBN/UID,Format,Read Status,Date Added,Last Date Read,Dates Read,Read Count,Star Rating,Review,Tags,Owned?',
      'Twice Read,"Ann Lee, Bo Kim",,9781111111111,paperback,read,2023/01/01,2025/04/10,"2023/02/01-2023/02/20, 2025/04/01-2025/04/10",2,4.5,,classics,Yes',
    ].join('\n');
    const r = importReadingCsv(csv);
    expect(r.source).toBe('storygraph');
    const e = r.entries[0];
    expect(e.book.authors.map((a) => a.name)).toEqual(['Ann Lee', 'Bo Kim']);
    expect(e.sessions.map((s) => [s.startedAt, s.endedAt])).toEqual([
      ['2023-02-01', '2023-02-20'],
      ['2025-04-01', '2025-04-10'],
    ]);
    expect(e.rating).toBe(4.5);
  });

  it('rejects unrelated files', () => {
    expect(() => importReadingCsv('name,age\nx,1')).toThrow(/Goodreads or StoryGraph/);
  });
});
