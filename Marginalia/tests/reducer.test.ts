import { describe, expect, it } from 'vitest';
import { reducer, applyStatus, applyProgress, currentPage } from '../src/data/reducer';
import { initialState, sanitizeState, type AppState } from '../src/data/model';
import type { BookSnapshot, LibraryEntry } from '../src/lib/types';
import { today, addDays } from '../src/lib/dates';
import { buildSampleLibrary, SAMPLE_SHELVES } from '../src/data/sample';

const book: BookSnapshot = { id: 'manual:test', source: 'manual', title: 'Test Book', authors: [{ name: 'A. Writer' }] };

function withBook(status: 'want' | 'reading' | 'finished' | 'dnf' = 'want', pageCount = 300): AppState {
  return reducer(initialState(), { type: 'addBook', book, status, pageCount, date: '2026-01-10' });
}
const entry = (s: AppState): LibraryEntry => s.library[book.id];

describe('adding books', () => {
  it('adds to Want to Read with dates and no sessions', () => {
    const s = withBook('want');
    expect(entry(s).status).toBe('want');
    expect(entry(s).addedAt).toBe('2026-01-10');
    expect(entry(s).queuedAt).toBe('2026-01-10');
    expect(entry(s).sessions).toHaveLength(0);
    expect(entry(s).pageCount).toBe(300);
  });

  it('adding as Currently Reading starts a session on that date', () => {
    const s = withBook('reading');
    expect(entry(s).status).toBe('reading');
    expect(entry(s).sessions).toEqual([expect.objectContaining({ startedAt: '2026-01-10', outcome: 'reading' })]);
  });

  it('adding as Finished records the finish date and the last page', () => {
    const s = withBook('finished');
    const sess = entry(s).sessions[0];
    expect(sess.outcome).toBe('finished');
    expect(sess.endedAt).toBe('2026-01-10');
    expect(sess.startedAt).toBeUndefined();
  });
});

describe('status transitions', () => {
  it('want → reading → finished keeps one dated session and logs the final page', () => {
    let s = withBook('want');
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'reading', date: '2026-02-01' });
    s = reducer(s, { type: 'logProgress', id: book.id, page: 120, at: '2026-02-03T21:00' });
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'finished', date: '2026-02-10' });
    const e = entry(s);
    expect(e.status).toBe('finished');
    expect(e.sessions).toHaveLength(1);
    expect(e.sessions[0]).toMatchObject({ startedAt: '2026-02-01', endedAt: '2026-02-10', outcome: 'finished' });
    expect(e.sessions[0].progress.map((p) => p.page)).toEqual([120, 300]);
  });

  it('never lets a finish date fall before the start date', () => {
    let s = withBook('reading'); // started 2026-01-10
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'finished', date: '2026-01-01' });
    expect(entry(s).sessions[0].endedAt).toBe('2026-01-10');
  });

  it('finished → want queues a re-read without losing history; starting again opens a new session', () => {
    let s = withBook('finished');
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'want', date: '2026-03-01' });
    expect(entry(s).status).toBe('want');
    expect(entry(s).queuedAt).toBe('2026-03-01');
    expect(entry(s).sessions).toHaveLength(1);
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'reading', date: '2026-03-05' });
    expect(entry(s).sessions).toHaveLength(2);
    expect(entry(s).sessions[1]).toMatchObject({ startedAt: '2026-03-05', outcome: 'reading' });
  });

  it('DNF then reading resumes the same read-through', () => {
    let s = withBook('reading');
    s = reducer(s, { type: 'logProgress', id: book.id, page: 80, at: '2026-01-12T10:00' });
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'dnf', date: '2026-01-15' });
    expect(entry(s).sessions[0]).toMatchObject({ outcome: 'dnf', endedAt: '2026-01-15' });
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'reading' });
    expect(entry(s).sessions).toHaveLength(1);
    expect(entry(s).sessions[0]).toMatchObject({ outcome: 'reading', endedAt: undefined });
    expect(currentPage(entry(s))).toBe(80);
  });

  it('moving a current read back to Want to Read discards that read-through', () => {
    let s = withBook('reading');
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'want' });
    expect(entry(s).status).toBe('want');
    expect(entry(s).sessions).toHaveLength(0);
  });

  it('logging progress on an unread book starts it and clamps to the page count', () => {
    let s = withBook('want');
    s = reducer(s, { type: 'logProgress', id: book.id, page: 999, at: '2026-04-02T08:30' });
    const e = entry(s);
    expect(e.status).toBe('reading');
    expect(e.sessions[0].startedAt).toBe('2026-04-02');
    expect(currentPage(e)).toBe(300);
  });

  it('applyStatus / applyProgress are pure', () => {
    const s = withBook('want');
    const before = JSON.stringify(entry(s));
    applyProgress(applyStatus(entry(s), 'reading'), 50);
    expect(JSON.stringify(entry(s))).toBe(before);
  });
});

describe('editing history', () => {
  it('fixes an end date set before the start', () => {
    let s = withBook('reading');
    const sid = entry(s).sessions[0].id;
    s = reducer(s, { type: 'setStatus', id: book.id, status: 'finished', date: '2026-01-20' });
    s = reducer(s, { type: 'updateSession', id: book.id, sessionId: sid, patch: { endedAt: '2026-01-05' } });
    const sess = entry(s).sessions[0];
    expect(sess.endedAt).toBe('2026-01-05');
    expect(sess.startedAt).toBe('2026-01-05');
  });

  it('deleting the only read-through returns the book to Want to Read', () => {
    let s = withBook('finished');
    s = reducer(s, { type: 'deleteSession', id: book.id, sessionId: entry(s).sessions[0].id });
    expect(entry(s).status).toBe('want');
  });

  it('logging a past read on an unread book marks it finished', () => {
    let s = withBook('want');
    s = reducer(s, { type: 'addSession', id: book.id, startedAt: '2025-05-01', endedAt: '2025-05-20', outcome: 'finished' });
    expect(entry(s).status).toBe('finished');
    expect(entry(s).sessions[0].progress.at(-1)?.page).toBe(300);
  });

  it('a past read does not interrupt a current one', () => {
    let s = withBook('reading');
    s = reducer(s, { type: 'addSession', id: book.id, startedAt: '2024-05-01', endedAt: '2024-05-20', outcome: 'finished' });
    expect(entry(s).status).toBe('reading');
    expect(entry(s).sessions.map((x) => x.outcome)).toEqual(['finished', 'reading']);
  });
});

describe('shelves, settings and data', () => {
  it('deleting a shelf removes it from books and chart filters', () => {
    let s = withBook('want');
    s = reducer(s, { type: 'createShelf', shelf: { id: 'club', name: 'Club', color: '#123456', createdAt: today() } });
    s = reducer(s, { type: 'toggleShelf', id: book.id, shelfId: 'club' });
    expect(entry(s).shelves).toEqual(['club']);
    s = reducer(s, { type: 'setChart', chart: { ...s.chart, filters: { ...s.chart.filters, shelves: ['club'] } } });
    s = reducer(s, { type: 'deleteShelf', shelfId: 'club' });
    expect(entry(s).shelves).toEqual([]);
    expect(s.chart.filters.shelves).toEqual([]);
  });

  it('settings updates are sanitised', () => {
    const s = reducer(initialState(), { type: 'updateSettings', patch: { pageLayers: 99, theme: 'nope' as never, fontScale: 1.1 } });
    expect(s.settings.pageLayers).toBe(7);
    expect(s.settings.theme).toBe('oxblood');
    expect(s.settings.fontScale).toBe(1.1);
  });

  it('sanitizeState drops broken entries and repairs inconsistent status', () => {
    const s = sanitizeState({
      library: {
        a: { id: 'a', book: { title: 'Ok', authors: [{ name: 'X' }] }, status: 'finished', sessions: [] },
        b: { id: 'b', book: { authors: [] }, status: 'want' },
        c: 'garbage',
      },
      settings: { mode: 'dark', ruleOpacity: 'loud' },
    });
    expect(Object.keys(s.library)).toEqual(['a']);
    expect(s.library.a.status).toBe('want');
    expect(s.settings.mode).toBe('dark');
    expect(s.settings.ruleOpacity).toBe(0.24);
  });

  it('merge import keeps the newer copy of each book', () => {
    const base = withBook('want');
    const incoming = structuredClone(base);
    incoming.library[book.id] = { ...incoming.library[book.id], status: 'finished', updatedAt: '2999-01-01T00:00', sessions: [{ id: 'x', endedAt: '2026-01-01', outcome: 'finished', progress: [] }] };
    const merged = reducer(base, { type: 'importState', state: incoming, mode: 'merge' });
    expect(merged.library[book.id].status).toBe('finished');
  });

  it('sample library loads and clears without touching real books', () => {
    let s = withBook('want');
    s = reducer(s, { type: 'loadSample', entries: buildSampleLibrary('2026-11-06'), shelves: SAMPLE_SHELVES });
    expect(Object.keys(s.library).length).toBeGreaterThan(20);
    s = reducer(s, { type: 'clearSample' });
    expect(Object.keys(s.library)).toEqual([book.id]);
    expect(s.shelves.some((x) => x.id.startsWith('sample-'))).toBe(false);
  });

  it('sample library is internally consistent', () => {
    const ref = '2026-11-06';
    for (const e of buildSampleLibrary(ref)) {
      const clean = sanitizeState({ library: [e] }).library[e.id];
      expect(clean.status).toBe(e.status);
      for (const sess of e.sessions) {
        if (sess.startedAt && sess.endedAt) expect(sess.endedAt >= sess.startedAt).toBe(true);
        for (const p of sess.progress) expect(p.at.slice(0, 10) <= ref).toBe(true);
        const pages = sess.progress.map((p) => p.page);
        expect([...pages].sort((a, b) => a - b)).toEqual(pages);
      }
      if (e.status === 'reading') expect(e.sessions.at(-1)?.outcome).toBe('reading');
    }
    expect(addDays(ref, 1)).toBe('2026-11-07');
  });
});
