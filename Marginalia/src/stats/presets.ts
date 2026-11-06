import type { ChartConfig, ChartFilters } from '../lib/types';

export const DEFAULT_FILTERS: ChartFilters = {
  range: 'last12',
  statuses: [],
  shelves: [],
  genres: [],
  formats: [],
  authors: [],
};

function preset(id: string, name: string, patch: Partial<ChartConfig>, filters: Partial<ChartFilters> = {}): ChartConfig {
  return {
    id,
    name,
    metric: 'finished',
    groupBy: 'month',
    chartType: 'column',
    splitBy: 'none',
    cumulative: false,
    showValues: false,
    showAverage: true,
    showGoal: false,
    topN: 10,
    ...patch,
    filters: { ...DEFAULT_FILTERS, ...filters },
  };
}

/** Built-in starting points for the chart builder. */
export const BUILTIN_PRESETS: ChartConfig[] = [
  preset('builtin-monthly', 'Books finished per month', { metric: 'finished', groupBy: 'month', chartType: 'column', splitBy: 'format' }),
  preset('builtin-pages-week', 'Pages read per week', { metric: 'pages', groupBy: 'week', chartType: 'area', showAverage: true }, { range: 'last90' }),
  preset('builtin-goal', 'Year-to-date vs. goal', { metric: 'finished', groupBy: 'month', chartType: 'line', cumulative: true, showGoal: true, showAverage: false }, { range: 'ytd' }),
  preset('builtin-genres', 'Genres you finish', { metric: 'finished', groupBy: 'genre', chartType: 'bar', showValues: true, showAverage: false }, { range: 'all' }),
  preset('builtin-ratings', 'How you rate', { metric: 'finished', groupBy: 'rating', chartType: 'column', showValues: true, showAverage: false }, { range: 'all' }),
  preset('builtin-pace', 'Pace by book length', { metric: 'pace', groupBy: 'length', chartType: 'column', showValues: true, showAverage: false }, { range: 'all' }),
  preset('builtin-library', 'Library by status', { metric: 'books', groupBy: 'status', chartType: 'bar', showValues: true, showAverage: false }, { range: 'all' }),
  preset('builtin-weekday', 'Pages by weekday', { metric: 'pages', groupBy: 'weekday', chartType: 'column', showAverage: true }, { range: 'last90' }),
];

export const DEFAULT_CHART: ChartConfig = { ...BUILTIN_PRESETS[0], id: 'working', name: 'My chart' };
