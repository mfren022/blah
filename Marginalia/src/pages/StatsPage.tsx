import { useMemo, useState } from 'react';
import type { BookFormat, ChartConfig, ChartFilters, ChartType, GroupBy, Metric, RangePreset, SplitBy, Status } from '../lib/types';
import { FORMATS, STATUSES } from '../lib/types';
import { useEntries, useSettings, useStore } from '../data/store';
import { Pages, useMode } from '../components/Shell';
import { Rule, Leaf, Seg, Switch, Empty } from '../components/ui';
import { Icon } from '../components/Icon';
import { Chart, type ChartChrome } from '../components/Chart';
import { Heatmap } from '../components/Heatmap';
import { useToast } from '../components/Toasts';
import {
  computeChart,
  computeOverview,
  entryGenre,
  FORMAT_LABELS,
  GROUP_LABELS,
  isTimeGroup,
  METRICS,
  SPLIT_LABELS,
  splitAllowed,
  STATUS_LABELS,
  TIME_GROUPS,
  type StatsContext,
} from '../stats/engine';
import { BUILTIN_PRESETS, DEFAULT_FILTERS } from '../stats/presets';
import { CATEGORICAL } from '../stats/palette';
import { chartSingleColor, heatColors } from '../stats/colors';
import { themeVariables } from '../data/settings';
import { formatDate, monthLabel, today } from '../lib/dates';
import { formatNumber, plural, round, uid, uniq } from '../lib/util';
import { bookHref } from '../router';

const RANGES: { value: RangePreset; label: string }[] = [
  { value: 'last30', label: 'Last 30 days' },
  { value: 'last90', label: 'Last 90 days' },
  { value: 'ytd', label: 'This year' },
  { value: 'last12', label: 'Last 12 months' },
  { value: 'lastYear', label: 'Last year' },
  { value: 'all', label: 'All time' },
  { value: 'custom', label: 'Custom…' },
];

export function StatsPage() {
  const { state, dispatch } = useStore();
  const settings = useSettings();
  const entries = useEntries();
  const mode = useMode();
  const toast = useToast();
  const t = today();

  const ctx: StatsContext = useMemo(
    () => ({
      today: t,
      weekStart: settings.weekStart,
      shelves: state.shelves,
      goalBooks: settings.yearlyBookGoal,
      goalPages: settings.yearlyPageGoal,
      mode,
      singleColor: chartSingleColor(settings, mode),
      allEntries: entries,
    }),
    [t, settings, state.shelves, mode, entries],
  );
  const overview = useMemo(() => computeOverview(entries, ctx), [entries, ctx]);
  const chart = state.chart;
  const data = useMemo(() => computeChart(chart, entries, ctx), [chart, entries, ctx]);
  const vars = themeVariables(settings, mode);
  const chrome: ChartChrome = {
    ink: vars['--ink'],
    inkSoft: vars['--ink-soft'],
    inkFaint: vars['--ink-faint'],
    grid: vars['--grid'],
    baseline: vars['--edge'],
    surface: vars['--leaf'],
    font: vars['--font-ui'],
  };

  const set = (patch: Partial<ChartConfig>) => dispatch({ type: 'setChart', chart: { ...chart, ...patch } });
  const setFilters = (patch: Partial<ChartFilters>) => set({ filters: { ...chart.filters, ...patch } });

  const year = t.slice(0, 4);
  const delta = overview.finishedThisYear - overview.finishedLastYear;

  /* --------------------------------------------------------------- verso */
  const verso = (
    <>
      <Rule level="page">Statistics</Rule>
      {entries.length === 0 ? (
        <Empty icon="chart" title="No reading to count yet" action={<a className="btn btn--primary" href="#/browse">Find a book</a>}>
          Shelve some books and log your progress — your statistics will gather here.
        </Empty>
      ) : (
        <div className="stack">
          <div className="tiles">
            <Tile label={`Books finished in ${year}`} value={formatNumber(overview.finishedThisYear)} note={`${delta >= 0 ? '+' : '−'}${Math.abs(delta)} vs ${Number(year) - 1}`} />
            <Tile label={`Pages read in ${year}`} value={formatNumber(Math.round(overview.pagesThisYear))} note={`${formatNumber(Math.round(overview.pagesAllTime))} all time`} />
            <Tile label="Reading streak" value={String(overview.currentStreak)} unit={overview.currentStreak === 1 ? 'day' : 'days'} note={`Longest ${plural(overview.longestStreak, 'day')}`} />
            <Tile label="Pace, last 30 days" value={formatNumber(round(overview.pagesLast30 / 30, 1), 1)} unit="pages/day" note={`Read on ${overview.readingDays30} of 30 days`} />
            <Tile label="Average rating" value={overview.avgRating ? overview.avgRating.toFixed(2) : '—'} unit={overview.avgRating ? '★' : ''} note={`${overview.ratedCount} rated`} />
            <Tile label="Days per book" value={overview.medianDaysToFinish ? formatNumber(overview.medianDaysToFinish, 1) : '—'} note="median, start to finish" />
            <Tile label="Did not finish" value={overview.dnfRate !== undefined ? `${Math.round(overview.dnfRate * 100)}%` : '—'} note={`${overview.counts.dnf} abandoned`} />
            <Tile label="In your library" value={formatNumber(overview.total)} note={`${overview.counts.want} waiting to be read`} />
          </div>

          <Leaf title="Goals" sub={`${year} · change them in Settings`}>
            <Goal label="Books" done={overview.goalBooks.done} target={overview.goalBooks.target} expected={overview.goalBooks.expected} />
            <Goal label="Pages" done={Math.round(overview.goalPages.done)} target={overview.goalPages.target} expected={overview.goalPages.expected} />
          </Leaf>

          <Leaf title="Your library" sub={`${overview.total} books by shelf`}>
            <Segmented counts={overview.counts} mode={mode} />
          </Leaf>

          <Leaf title="Highlights">
            <dl className="facts highlights">
              {overview.topGenre && (
                <>
                  <dt>Favourite genre</dt>
                  <dd>
                    {overview.topGenre.name} <span className="faint">· {plural(overview.topGenre.count, 'book')}</span>
                  </dd>
                </>
              )}
              {overview.topAuthor && (
                <>
                  <dt>Most-read author</dt>
                  <dd>
                    {overview.topAuthor.name} <span className="faint">· {plural(overview.topAuthor.count, 'book')}</span>
                  </dd>
                </>
              )}
              {overview.busiestMonth && (
                <>
                  <dt>Busiest month</dt>
                  <dd>
                    {monthLabel(overview.busiestMonth.key, true)} <span className="faint">· {plural(overview.busiestMonth.count, 'book')}</span>
                  </dd>
                </>
              )}
              {overview.longest && (
                <>
                  <dt>Longest finished</dt>
                  <dd>
                    <a href={bookHref(overview.longest.entry.id)}>{overview.longest.entry.book.title}</a> <span className="faint">· {formatNumber(overview.longest.pages)} pp.</span>
                  </dd>
                </>
              )}
              {overview.shortest && (
                <>
                  <dt>Shortest finished</dt>
                  <dd>
                    <a href={bookHref(overview.shortest.entry.id)}>{overview.shortest.entry.book.title}</a> <span className="faint">· {formatNumber(overview.shortest.pages)} pp.</span>
                  </dd>
                </>
              )}
              {overview.avgPace && (
                <>
                  <dt>Typical pace</dt>
                  <dd>{formatNumber(overview.avgPace, 1)} pages a day while reading a book</dd>
                </>
              )}
            </dl>
          </Leaf>

          <Leaf title="Reading calendar" sub="Pages read each day">
            <Heatmap entries={entries} weekStart={settings.weekStart} colors={heatColors(settings, mode)} empty={vars['--paper-3']} />
          </Leaf>
        </div>
      )}
    </>
  );

  /* --------------------------------------------------------------- recto */
  const metric = METRICS[chart.metric];
  const groupIsTime = isTimeGroup(chart.groupBy);
  const rangeLabel = RANGES.find((r) => r.value === chart.filters.range)?.label ?? '';
  const chartTitle = `${metric.label} by ${GROUP_LABELS[chart.groupBy].toLowerCase()}${chart.splitBy !== 'none' && splitAllowed(chart, chart.splitBy) ? `, split by ${SPLIT_LABELS[chart.splitBy].toLowerCase()}` : ''}`;
  const allGenres = useMemo(() => uniq(entries.map(entryGenre)).sort(), [entries]);
  const allAuthors = useMemo(() => uniq(entries.flatMap((e) => e.book.authors.map((a) => a.name))).sort(), [entries]);
  const activeFilters = countFilters(chart.filters);
  const [moreOpen, setMoreOpen] = useState(activeFilters > 0);
  const [saveName, setSaveName] = useState<string | null>(null);

  const toggleIn = <T extends string>(list: T[], v: T): T[] => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v]);

  const metricWords: Record<Metric, string> = {
    finished: 'books finished',
    pages: 'pages read',
    started: 'books started',
    added: 'books added',
    dnf: 'books abandoned',
    books: 'library size',
    rating: 'average rating',
    days: 'days to finish',
    pace: 'pages per day',
  };
  const rangeWords: Record<RangePreset, string> = {
    last30: 'over the last 30 days',
    last90: 'over the last 90 days',
    ytd: 'so far this year',
    last12: 'over the last 12 months',
    lastYear: 'during last year',
    all: 'across all time',
    custom: 'between two dates',
  };
  const splitOk = (sp: SplitBy) => splitAllowed(chart, sp);

  const recto = (
    <div className="stack">
      <section aria-labelledby="builder">
        <Rule level="section" id="builder" sub="Change any underlined phrase to redraw the chart">
          Chart your reading
        </Rule>

        <p className="sentence">
          Show{' '}
          <InlineSelect
            label="Measure"
            value={chart.metric}
            onChange={(m) => set({ metric: m, splitBy: splitAllowed({ metric: m, groupBy: chart.groupBy }, chart.splitBy) ? chart.splitBy : 'none' })}
            options={(Object.keys(METRICS) as Metric[]).map((m) => ({ value: m, label: metricWords[m] }))}
          />{' '}
          by{' '}
          <InlineSelect
            label="Group by"
            value={chart.groupBy}
            onChange={(g) => {
              const time = isTimeGroup(g);
              set({
                groupBy: g,
                chartType: time ? (chart.chartType === 'bar' ? 'column' : chart.chartType) : chart.chartType === 'line' || chart.chartType === 'area' ? (['rating', 'length', 'weekday', 'decade'].includes(g) ? 'column' : 'bar') : chart.chartType,
                splitBy: chart.splitBy === g ? 'none' : chart.splitBy,
                cumulative: time ? chart.cumulative : false,
              });
            }}
            groups={[
              { label: 'Over time', options: TIME_GROUPS.map((g) => ({ value: g, label: GROUP_LABELS[g].toLowerCase() })) },
              {
                label: 'By category',
                options: (['genre', 'author', 'rating', 'status', 'format', 'length', 'decade', 'shelf', 'weekday'] as GroupBy[]).map((g) => ({ value: g, label: GROUP_LABELS[g].toLowerCase() })),
              },
            ]}
          />
          ,{' '}
          <InlineSelect
            label="Split"
            value={splitOk(chart.splitBy) ? chart.splitBy : 'none'}
            onChange={(sp) => set({ splitBy: sp })}
            options={(Object.keys(SPLIT_LABELS) as SplitBy[]).map((sp) => ({ value: sp, label: sp === 'none' ? 'not split' : `split by ${SPLIT_LABELS[sp].toLowerCase()}`, disabled: !splitOk(sp) }))}
          />
          ,{' '}
          <InlineSelect label="Date range" value={chart.filters.range} onChange={(r) => setFilters({ range: r })} options={RANGES.map((r) => ({ value: r.value, label: rangeWords[r.value] }))} />
          {chart.filters.range === 'custom' && (
            <>
              {' '}
              <input className="inline-date" type="date" aria-label="From" value={chart.filters.from ?? ''} max={chart.filters.to ?? t} onChange={(e) => setFilters({ from: e.target.value || undefined })} /> and{' '}
              <input className="inline-date" type="date" aria-label="To" value={chart.filters.to ?? ''} min={chart.filters.from} onChange={(e) => setFilters({ to: e.target.value || undefined })} />
            </>
          )}
          .
        </p>

        {activeFilters > 0 && (
          <div className="chips active-filters">
            {chart.filters.statuses.map((x) => (
              <FilterTag key={x} label={STATUS_LABELS[x]} onRemove={() => setFilters({ statuses: chart.filters.statuses.filter((y) => y !== x) })} />
            ))}
            {chart.filters.formats.map((x) => (
              <FilterTag key={x} label={FORMAT_LABELS[x]} onRemove={() => setFilters({ formats: chart.filters.formats.filter((y) => y !== x) })} />
            ))}
            {chart.filters.shelves.map((x) => (
              <FilterTag key={x} label={state.shelves.find((s) => s.id === x)?.name ?? x} onRemove={() => setFilters({ shelves: chart.filters.shelves.filter((y) => y !== x) })} />
            ))}
            {chart.filters.genres.map((x) => (
              <FilterTag key={x} label={x} onRemove={() => setFilters({ genres: chart.filters.genres.filter((y) => y !== x) })} />
            ))}
            {chart.filters.authors.map((x) => (
              <FilterTag key={x} label={x} onRemove={() => setFilters({ authors: [] })} />
            ))}
            {chart.filters.minRating ? <FilterTag label={`≥ ${chart.filters.minRating} ★`} onRemove={() => setFilters({ minRating: undefined })} /> : null}
            {chart.filters.minPages ? <FilterTag label={`≥ ${chart.filters.minPages} pp.`} onRemove={() => setFilters({ minPages: undefined })} /> : null}
            {chart.filters.maxPages ? <FilterTag label={`≤ ${chart.filters.maxPages} pp.`} onRemove={() => setFilters({ maxPages: undefined })} /> : null}
          </div>
        )}

        <figure className="leaf chart-leaf">
          {data.notes.length > 0 && (
            <ul className="chart__notes ui">
              {data.notes.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
          )}
          <Chart data={data} type={chart.chartType} metric={chart.metric} chrome={chrome} showValues={chart.showValues} title={chartTitle} />
          <figcaption className="chart-caption">
            <span className="chart-caption__fig">Fig. 1</span> {chartTitle},{' '}
            {chart.filters.range === 'custom' ? `${formatDate(data.range.from, settings.dateFormat)} – ${formatDate(data.range.to, settings.dateFormat)}` : rangeLabel.toLowerCase()}
            {activeFilters ? ` (${plural(activeFilters, 'filter')})` : ''}. <span className="faint">{metric.help}</span>
          </figcaption>
        </figure>

        <div className="row row--between builder-row">
          <Seg<ChartType>
            label="Chart type"
            size="sm"
            value={chart.chartType}
            onChange={(v) => set({ chartType: v })}
            options={[
              { value: 'column', label: 'Columns', icon: 'chart' },
              { value: 'bar', label: 'Bars', icon: 'list' },
              { value: 'line', label: 'Line', icon: 'refresh' },
              { value: 'area', label: 'Area', icon: 'layers' },
            ]}
          />
          {!groupIsTime && ['genre', 'author', 'shelf'].includes(chart.groupBy) && (
            <label className="row small ui">
              <span className="muted">Show top</span>
              <select className="select" style={{ width: 'auto' }} value={chart.topN} onChange={(e) => set({ topN: Number(e.target.value) })}>
                {[5, 8, 10, 15, 20].map((n) => (
                  <option key={n}>{n}</option>
                ))}
              </select>
            </label>
          )}
        </div>
        <div className="row builder-toggles">
          {groupIsTime && METRICS[chart.metric].kind !== 'average' && chart.metric !== 'books' && <Switch checked={chart.cumulative} onChange={(v) => set({ cumulative: v })} label="Running total" />}
          <Switch checked={chart.showValues} onChange={(v) => set({ showValues: v })} label="Label values" />
          <Switch checked={chart.showAverage} onChange={(v) => set({ showAverage: v })} label="Average line" />
          {groupIsTime && (chart.metric === 'finished' || chart.metric === 'pages') && <Switch checked={chart.showGoal} onChange={(v) => set({ showGoal: v })} label="Goal pace" />}
        </div>

        <details className="fold filters" open={moreOpen} onToggle={(e) => setMoreOpen((e.target as HTMLDetailsElement).open)}>
          <summary>Filter which books count{activeFilters ? ` (${activeFilters} active)` : ''}</summary>
          <div className="filters__body">
            <FilterChips label="Shelf status" items={STATUSES.map((s) => ({ key: s, label: STATUS_LABELS[s] }))} selected={chart.filters.statuses} onToggle={(k) => setFilters({ statuses: toggleIn(chart.filters.statuses, k as Status) })} />
            <FilterChips label="Format" items={FORMATS.map((f) => ({ key: f, label: FORMAT_LABELS[f] }))} selected={chart.filters.formats} onToggle={(k) => setFilters({ formats: toggleIn(chart.filters.formats, k as BookFormat) })} />
            {state.shelves.length > 0 && <FilterChips label="Custom shelves" items={state.shelves.map((s) => ({ key: s.id, label: s.name, color: s.color }))} selected={chart.filters.shelves} onToggle={(k) => setFilters({ shelves: toggleIn(chart.filters.shelves, k) })} />}
            {allGenres.length > 0 && <FilterChips label="Genres" items={allGenres.map((g) => ({ key: g, label: g }))} selected={chart.filters.genres} onToggle={(k) => setFilters({ genres: toggleIn(chart.filters.genres, k) })} />}
            <div className="form-grid">
              <label className="field">
                <span>Author</span>
                <select className="select" value={chart.filters.authors[0] ?? ''} onChange={(e) => setFilters({ authors: e.target.value ? [e.target.value] : [] })}>
                  <option value="">Any author</option>
                  {allAuthors.map((a) => (
                    <option key={a}>{a}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Rated at least</span>
                <select className="select" value={chart.filters.minRating ?? 0} onChange={(e) => setFilters({ minRating: Number(e.target.value) || undefined })}>
                  <option value={0}>Any</option>
                  {[1, 2, 3, 3.5, 4, 4.5, 5].map((r) => (
                    <option key={r} value={r}>
                      {r} ★
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Min pages</span>
                <input className="input" type="number" min={0} value={chart.filters.minPages ?? ''} onChange={(e) => setFilters({ minPages: Number(e.target.value) || undefined })} />
              </label>
              <label className="field">
                <span>Max pages</span>
                <input className="input" type="number" min={0} value={chart.filters.maxPages ?? ''} onChange={(e) => setFilters({ maxPages: Number(e.target.value) || undefined })} />
              </label>
            </div>
            {activeFilters > 0 && (
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => setFilters({ ...DEFAULT_FILTERS, range: chart.filters.range, from: chart.filters.from, to: chart.filters.to })}>
                <Icon name="x" /> Clear filters
              </button>
            )}
          </div>
        </details>

        <Rule level="minor" as="h3">
          Starting points
        </Rule>
        <div className="presets" role="group" aria-label="Chart presets">
          {[...BUILTIN_PRESETS, ...state.chartPresets].map((p) => {
            const isMine = state.chartPresets.some((x) => x.id === p.id);
            return (
              <span key={p.id} className="preset">
                <button type="button" className="chip" onClick={() => set({ ...structuredClone(p), id: 'working', name: p.name })}>
                  {isMine && <Icon name="bookmark" />}
                  {p.name}
                </button>
                {isMine && (
                  <button type="button" className="preset__x" aria-label={`Delete preset ${p.name}`} onClick={() => dispatch({ type: 'deleteChartPreset', presetId: p.id })}>
                    <Icon name="x" />
                  </button>
                )}
              </span>
            );
          })}
          {saveName === null && (
            <button type="button" className="btn btn--sm" onClick={() => setSaveName(chartTitle)}>
              <Icon name="plus" /> Save this chart
            </button>
          )}
        </div>
        {saveName !== null && (
          <form
            className="row save-chart"
            onSubmit={(e) => {
              e.preventDefault();
              const name = saveName.trim();
              if (!name) return;
              dispatch({ type: 'saveChartPreset', chart: { ...structuredClone(chart), id: `preset-${uid()}`, name } });
              toast({ message: `Saved “${name}” to your starting points` });
              setSaveName(null);
            }}
          >
            <input className="input grow" value={saveName} onChange={(e) => setSaveName(e.target.value)} aria-label="Name for this chart" autoFocus />
            <button type="submit" className="btn btn--primary btn--sm" disabled={!saveName.trim()}>
              <Icon name="save" /> Save
            </button>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setSaveName(null)}>
              Cancel
            </button>
          </form>
        )}
      </section>
    </div>
  );

  return <Pages title="Statistics" tab="stats" verso={verso} recto={recto} />;
}

function countFilters(f: ChartFilters): number {
  return f.statuses.length + f.shelves.length + f.genres.length + f.formats.length + f.authors.length + (f.minRating ? 1 : 0) + (f.minPages ? 1 : 0) + (f.maxPages ? 1 : 0);
}

function Tile({ label, value, unit, note }: { label: string; value: string; unit?: string; note?: string }) {
  return (
    <div className="tile">
      <div className="tile__label">{label}</div>
      <div className="tile__value">
        {value}
        {unit && <small>{unit}</small>}
      </div>
      {note && <div className="tile__note">{note}</div>}
    </div>
  );
}

function Goal({ label, done, target, expected }: { label: string; done: number; target: number; expected: number }) {
  if (!target) return <p className="small muted ui">No {label.toLowerCase()} goal set.</p>;
  const pct = Math.min(100, (done / target) * 100);
  const diff = done - expected;
  return (
    <div className="goal">
      <div className="row row--between small ui">
        <span>
          <strong>{formatNumber(done)}</strong> of {formatNumber(target)} {label.toLowerCase()} <span className="faint">({Math.round((done / target) * 100)}%)</span>
        </span>
        <span className="muted">{diff >= 0 ? `${formatNumber(Math.round(diff))} ahead of pace` : `${formatNumber(Math.round(-diff))} behind pace`}</span>
      </div>
      <div className="meter" aria-hidden>
        <div className="meter__fill" style={{ width: `${pct}%` }} />
        <div className="meter__mark" style={{ left: `${Math.min(100, (expected / target) * 100)}%` }} />
      </div>
    </div>
  );
}

/** One whole (the library) split by shelf: a tall segmented bar with shares printed inside. */
function Segmented({ counts, mode }: { counts: Record<Status, number>; mode: 'light' | 'dark' }) {
  const total = STATUSES.reduce((a, s) => a + counts[s], 0);
  if (!total) return null;
  const colors = CATEGORICAL[mode];
  // Label colour inside a fill: white, except near-black on yellow in light mode.
  const labelInk = (i: number) => (i === 3 && mode === 'light' ? '#0b0b0b' : '#ffffff');
  return (
    <div>
      <div className="segbar" role="img" aria-label={STATUSES.map((s) => `${STATUS_LABELS[s]} ${counts[s]}`).join(', ')}>
        {STATUSES.map((s, i) => {
          const share = counts[s] / total;
          if (!counts[s]) return null;
          return (
            <div key={s} className="segbar__part" style={{ flexGrow: counts[s], background: colors[i], color: labelInk(i) }} title={`${STATUS_LABELS[s]}: ${counts[s]} (${Math.round(share * 100)}%)`}>
              {share >= 0.09 && <span>{Math.round(share * 100)}%</span>}
            </div>
          );
        })}
      </div>
      <ul className="legend legend--static">
        {STATUSES.map((s, i) => (
          <li key={s}>
            <span className="legend__swatch" style={{ background: colors[i] }} />
            {STATUS_LABELS[s]} <span className="faint nums">{counts[s]}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function FilterChips({ label, items, selected, onToggle }: { label: string; items: { key: string; label: string; color?: string }[]; selected: string[]; onToggle: (k: string) => void }) {
  return (
    <div className="filter-row">
      <span className="field__label">{label}</span>
      <div className="chips">
        {items.map((i) => (
          <button key={i.key} type="button" className="chip" aria-pressed={selected.includes(i.key)} onClick={() => onToggle(i.key)}>
            {i.color && <span className="chip__dot" style={{ background: i.color }} />}
            {i.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function InlineSelect<T extends string>({
  label,
  value,
  onChange,
  options,
  groups,
}: {
  label: string;
  value: T;
  onChange: (v: T) => void;
  options?: { value: T; label: string; disabled?: boolean }[];
  groups?: { label: string; options: { value: T; label: string }[] }[];
}) {
  const all = options ?? groups!.flatMap((g) => g.options);
  const current = all.find((o) => o.value === value)?.label ?? value;
  return (
    <span className="inline-select">
      <span className="inline-select__text" aria-hidden>
        {current}
      </span>
      <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value as T)}>
        {options?.map((o) => (
          <option key={o.value} value={o.value} disabled={o.disabled}>
            {o.label}
          </option>
        ))}
        {groups?.map((g) => (
          <optgroup key={g.label} label={g.label}>
            {g.options.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
    </span>
  );
}

function FilterTag({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <button type="button" className="chip" onClick={onRemove} aria-label={`Remove filter ${label}`}>
      {label} <Icon name="x" />
    </button>
  );
}
