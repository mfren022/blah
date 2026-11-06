import { describe, expect, it } from 'vitest';
import type { ChartConfig, LibraryEntry } from '../src/lib/types';
import { computeChart, computeOverview, heatmap, sessionPageEvents, streaks, readingForecast, type StatsContext } from '../src/stats/engine';
import { DEFAULT_FILTERS } from '../src/stats/presets';
import { CATEGORICAL } from '../src/stats/palette';
import { buildSampleLibrary } from '../src/data/sample';

const TODAY = '2026-11-06';
const ctx: StatsContext = { today: TODAY, weekStart: 1, shelves: [], goalBooks: 24, goalPages: 8000, mode: 'light' };

function entry(id: string, patch: Partial<LibraryEntry>): LibraryEntry {
  return {
    id,
    book: { id, source: 'manual', title: id, authors: [{ name: 'Author ' + id }], subjects: ['Fantasy'] },
    status: 'finished',
    addedAt: '2026-01-01',
    shelves: [],
    sessions: [],
    updatedAt: '2026-01-01T00:00',
    pageCount: 300,
    format: 'paperback',
    ...patch,
  };
}

const chart = (patch: Partial<ChartConfig>, filters = {}): ChartConfig => ({
  id: 't', name: 't', metric: 'finished', groupBy: 'month', chartType: 'column', splitBy: 'none',
  cumulative: false, showValues: false, showAverage: false, showGoal: false, topN: 5,
  ...patch, filters: { ...DEFAULT_FILTERS, ...filters },
});

describe('pages read', () => {
  it('uses high-water-mark deltas from progress logs', () => {
    const e = entry('a', {
      status: 'reading',
      sessions: [{ id: 's', startedAt: '2026-11-01', outcome: 'reading', progress: [
        { id: '1', at: '2026-11-01T20:00', page: 40 },
        { id: '2', at: '2026-11-02T20:00', page: 30 }, // correction backwards
        { id: '3', at: '2026-11-03T20:00', page: 90 },
      ] }],
    });
    const ev = sessionPageEvents(e, e.sessions[0]);
    expect(ev.map((x) => [x.date, x.pages])).toEqual([['2026-11-01', 40], ['2026-11-03', 50]]);
  });

  it('spreads an unlogged finished read evenly across its dates', () => {
    const e = entry('b', { sessions: [{ id: 's', startedAt: '2026-10-01', endedAt: '2026-10-10', outcome: 'finished', progress: [] }] });
    const ev = sessionPageEvents(e, e.sessions[0]);
    expect(ev).toHaveLength(10);
    expect(ev.reduce((a, x) => a + x.pages, 0)).toBeCloseTo(300);
    expect(ev.every((x) => !x.logged)).toBe(true);
  });

  it('puts the rest of a partly logged read after the last log', () => {
    const e = entry('c', { sessions: [{ id: 's', startedAt: '2026-10-01', endedAt: '2026-10-05', outcome: 'finished', progress: [{ id: '1', at: '2026-10-02T09:00', page: 100 }] }] });
    const ev = sessionPageEvents(e, e.sessions[0]);
    expect(ev[0]).toMatchObject({ date: '2026-10-02', pages: 100, logged: true });
    expect(ev.slice(1).map((x) => x.date)).toEqual(['2026-10-03', '2026-10-04', '2026-10-05']);
    expect(ev.reduce((a, x) => a + x.pages, 0)).toBeCloseTo(300);
  });
});

describe('streaks', () => {
  it('counts the current run through yesterday and the longest run', () => {
    const days = new Set(['2026-11-01', '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-10-01', '2026-10-02']);
    expect(streaks(days, TODAY)).toEqual({ current: 5, longest: 5 });
    expect(streaks(new Set(['2026-11-01']), TODAY).current).toBe(0);
  });
});

describe('computeChart', () => {
  const lib = [
    entry('a', { sessions: [{ id: '1', startedAt: '2026-09-01', endedAt: '2026-09-10', outcome: 'finished', progress: [] }], rating: 4, format: 'ebook' }),
    entry('b', { sessions: [{ id: '1', startedAt: '2026-10-02', endedAt: '2026-10-08', outcome: 'finished', progress: [] }], rating: 5 }),
    entry('c', { sessions: [{ id: '1', startedAt: '2026-10-12', endedAt: '2026-10-20', outcome: 'finished', progress: [] }], book: { id: 'c', source: 'manual', title: 'c', authors: [{ name: 'Z' }], subjects: ['Mystery'] } }),
    entry('d', { status: 'dnf', sessions: [{ id: '1', startedAt: '2026-10-01', endedAt: '2026-10-03', outcome: 'dnf', progress: [{ id: 'p', at: '2026-10-02T10:00', page: 50 }] }] }),
    entry('e', { status: 'want', addedAt: '2026-11-02' }),
  ];

  it('counts finished books per month over the last 12 months', () => {
    const d = computeChart(chart({}), lib, ctx);
    expect(d.categories).toHaveLength(12);
    expect(d.categories[0].key).toBe('2025-12');
    expect(d.categories.at(-1)!.key).toBe('2026-11');
    const byKey = Object.fromEntries(d.categories.map((c, i) => [c.key, d.totals[i]]));
    expect(byKey['2026-09']).toBe(1);
    expect(byKey['2026-10']).toBe(2);
    expect(d.series).toHaveLength(1);
    expect(d.series[0].color).toBe(CATEGORICAL.light[0]);
  });

  it('cumulates and draws a goal pace', () => {
    const d = computeChart(chart({ cumulative: true, showGoal: true }, { range: 'ytd' }), lib, ctx);
    expect(d.totals.at(-1)).toBe(3);
    expect(d.goal).toHaveLength(11);
    expect(d.goal!.at(-1)).toBeCloseTo((24 * 310) / 365, 1);
  });

  it('stacks by format with colours that follow the entity, not the filter', () => {
    const all = computeChart(chart({ splitBy: 'format' }), lib, { ...ctx, allEntries: lib });
    const onlyEbooks = computeChart(chart({ splitBy: 'format' }, { formats: ['ebook'] }), lib, { ...ctx, allEntries: lib });
    const ebookAll = all.series.find((s) => s.key === 'ebook')!;
    const ebookFiltered = onlyEbooks.series.find((s) => s.key === 'ebook')!;
    expect(onlyEbooks.series).toHaveLength(1);
    expect(ebookFiltered.color).toBe(ebookAll.color);
  });

  it('groups by genre with a folded tail', () => {
    const d = computeChart(chart({ groupBy: 'genre', topN: 1 }, { range: 'all' }), lib, ctx);
    expect(d.categories.map((c) => c.label)).toEqual(['Fantasy', 'Other (1)']);
    expect(d.totals).toEqual([2, 1]);
  });

  it('averages ratings and refuses to stack averages', () => {
    const d = computeChart(chart({ metric: 'rating', groupBy: 'year', splitBy: 'format' }, { range: 'all' }), lib, ctx);
    expect(d.series).toHaveLength(1);
    expect(d.totals.at(-1)).toBeCloseTo(4.5);
    expect(d.notes.length).toBeGreaterThan(0);
  });

  it('library size is a running total', () => {
    const d = computeChart(chart({ metric: 'books', groupBy: 'month' }, { range: 'ytd' }), lib, ctx);
    expect(d.totals.at(-1)).toBe(5);
    expect(d.totals[0]).toBe(4);
  });

  it('pages by weekday fills all seven days', () => {
    const d = computeChart(chart({ metric: 'pages', groupBy: 'weekday' }, { range: 'all' }), lib, ctx);
    expect(d.categories.map((c) => c.label)).toEqual(['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']);
    expect(d.totals.reduce((a, b) => a + b, 0)).toBeCloseTo(950);
  });
});

describe('overview and heatmap on the sample library', () => {
  const sample = buildSampleLibrary(TODAY);

  it('summarises the library', () => {
    const o = computeOverview(sample, ctx);
    expect(o.counts).toEqual({ want: 4, reading: 3, finished: 14, dnf: 2 });
    expect(o.finishedAllTime).toBe(15); // includes one re-read
    expect(o.currentStreak).toBeGreaterThan(0);
    expect(o.longest?.pages).toBe(880);
    expect(o.avgRating).toBeGreaterThan(3);
  });

  it('builds a 53-week calendar', () => {
    const h = heatmap(sample, TODAY, 1);
    expect(h.weeks).toHaveLength(53);
    expect(h.weeks.every((w) => w.length === 7)).toBe(true);
    expect(h.weeks.at(-1)!.some((c) => c.date === TODAY)).toBe(true);
    expect(h.total).toBeGreaterThan(1000);
  });

  it('forecasts a finish date for current reads', () => {
    const reading = sample.find((e) => e.status === 'reading')!;
    const f = readingForecast(reading, TODAY);
    expect(f.pagesPerDay).toBeGreaterThan(0);
    expect(f.eta! >= TODAY).toBe(true);
  });
});
