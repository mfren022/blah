import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactElement } from 'react';
import type { ChartType, Metric } from '../lib/types';
import type { ChartData } from '../stats/engine';
import { formatMetricValue } from '../stats/engine';
import { downloadFile, formatNumber } from '../lib/util';
import { Icon } from './Icon';

export interface ChartChrome {
  ink: string;
  inkSoft: string;
  inkFaint: string;
  grid: string;
  baseline: string;
  surface: string;
  font: string;
}

/* ------------------------------------------------------------------ */
/* Scales                                                              */
/* ------------------------------------------------------------------ */

export function niceTicks(max: number, count = 4, integer = false): number[] {
  if (!(max > 0)) return [0, 1];
  const raw = max / count;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const norm = raw / mag;
  let step = (norm >= 7.5 ? 10 : norm >= 3.5 ? 5 : norm >= 1.5 ? 2 : 1) * mag;
  if (integer) step = Math.max(1, Math.round(step));
  const top = Math.ceil(max / step - 1e-9) * step;
  const ticks: number[] = [];
  for (let v = 0; v <= top + step / 1e6; v += step) ticks.push(Math.round(v * 1e6) / 1e6);
  return ticks;
}

function tickLabel(v: number, max: number): string {
  if (max >= 10000) return v >= 1000 ? `${formatNumber(v / 1000, 1)}K` : formatNumber(v);
  return formatNumber(v, v % 1 ? 1 : 0);
}

const textWidth = (s: string, px = 11) => s.length * px * 0.56;

function truncate(s: string, maxPx: number, px = 11): string {
  if (textWidth(s, px) <= maxPx) return s;
  const n = Math.max(3, Math.floor(maxPx / (px * 0.56)) - 1);
  return s.slice(0, n).trimEnd() + '…';
}

/** Path for a bar with a 4px rounded data-end and a square baseline end. */
function barPath(x: number, y: number, w: number, h: number, end: 'top' | 'right' | 'none', r = 4): string {
  if (w <= 0 || h <= 0) return '';
  const rr = Math.min(r, end === 'top' ? Math.min(w / 2, h) : Math.min(h / 2, w));
  if (end === 'top') {
    return `M${x},${y + h}V${y + rr}Q${x},${y} ${x + rr},${y}H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h}Z`;
  }
  if (end === 'right') {
    return `M${x},${y}H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h - rr}Q${x + w},${y + h} ${x + w - rr},${y + h}H${x}Z`;
  }
  return `M${x},${y}h${w}v${h}h${-w}Z`;
}

/* ------------------------------------------------------------------ */

interface Props {
  data: ChartData;
  type: ChartType;
  metric: Metric;
  chrome: ChartChrome;
  showValues: boolean;
  title: string;
  height?: number;
}

export function Chart({ data, type, metric, chrome, showValues, title, height = 280 }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const [width, setWidth] = useState(640);
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const [active, setActive] = useState<number | null>(null);
  const [pointer, setPointer] = useState<{ x: number; y: number } | null>(null);
  const [table, setTable] = useState(false);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setWidth(Math.max(260, Math.floor(e.contentRect.width))));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => setHidden(new Set()), [data.series.map((s) => s.key).join('|')]); // eslint-disable-line react-hooks/exhaustive-deps

  const series = data.series.filter((s) => !hidden.has(s.key));
  const n = data.categories.length;
  const stacked = (type === 'column' || type === 'bar' || type === 'area') && data.kind !== 'average' && series.length > 1;
  const fmt = (v: number) => formatMetricValue(v, metric);
  const integer = data.kind === 'count';

  // Totals of visible series per category (used for stacks and the y domain).
  const visibleTotals = useMemo(
    () => data.categories.map((_, i) => (data.kind === 'average' ? series[0]?.values[i] ?? NaN : series.reduce((a, s) => a + (Number.isFinite(s.values[i]) ? s.values[i] : 0), 0))),
    [data, series], // eslint-disable-line react-hooks/exhaustive-deps
  );

  const maxVal = Math.max(
    0,
    ...(stacked ? visibleTotals : series.flatMap((s) => s.values)).filter(Number.isFinite),
    ...(data.goal ?? []),
    data.average ?? 0,
  );
  const ticks = metric === 'rating' ? [0, 1, 2, 3, 4, 5] : niceTicks(maxVal, type === 'bar' ? 4 : 4, integer);
  const top = ticks[ticks.length - 1] || 1;

  const horizontal = type === 'bar';
  const labelW = horizontal ? Math.min(width * 0.4, Math.max(60, Math.max(...data.categories.map((c) => textWidth(c.label, 11.5))) + 24)) : 0;
  const yLabelW = horizontal ? 0 : Math.max(...ticks.map((t) => textWidth(tickLabel(t, top)))) + 12;
  const margin = { top: 18, right: horizontal ? 46 : 18, bottom: horizontal ? 26 : 30, left: horizontal ? labelW : yLabelW };
  const rowH = 30;
  const plotH = horizontal ? Math.max(rowH, n * rowH) : height;
  const plotW = Math.max(60, width - margin.left - margin.right);
  const H = plotH + margin.top + margin.bottom;

  const step = n ? (horizontal ? plotH : plotW) / n : 1;
  const thickness = Math.max(3, Math.min(24, step * 0.62));
  const val = (v: number) => (horizontal ? (v / top) * plotW : plotH - (v / top) * plotH);
  const center = (i: number) => (i + 0.5) * step;

  // x-axis label thinning so labels never collide.
  const maxLabel = Math.max(...data.categories.map((c) => textWidth(c.label)), 1);
  const every = horizontal ? 1 : Math.max(1, Math.ceil((maxLabel + 10) / step));

  const onMove = (e: PointerEvent<SVGSVGElement>) => {
    const r = svgRef.current!.getBoundingClientRect();
    const x = e.clientX - r.left - margin.left;
    const y = e.clientY - r.top - margin.top;
    const idx = Math.floor((horizontal ? y : x) / step);
    if (idx >= 0 && idx < n && (horizontal ? x >= -labelW && x <= plotW + margin.right : y >= -margin.top && y <= plotH + 8)) {
      setActive(idx);
      setPointer({ x: e.clientX - r.left, y: e.clientY - r.top });
    } else {
      setActive(null);
    }
  };

  const onKey = (e: KeyboardEvent) => {
    if (!n) return;
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') setActive((a) => (a === null ? 0 : Math.min(n - 1, a + 1)));
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') setActive((a) => (a === null ? n - 1 : Math.max(0, a - 1)));
    else if (e.key === 'Home') setActive(0);
    else if (e.key === 'End') setActive(n - 1);
    else if (e.key === 'Escape') setActive(null);
    else return;
    setPointer(null);
    e.preventDefault();
  };

  // Which points get direct value labels: all bars when few, otherwise the max and the last.
  const labelled = new Set<number>();
  if (showValues && n) {
    if ((type === 'column' || type === 'bar') && n <= 16) data.categories.forEach((_, i) => labelled.add(i));
    else {
      let mi = 0;
      visibleTotals.forEach((v, i) => {
        if (Number.isFinite(v) && v > (visibleTotals[mi] ?? -Infinity)) mi = i;
      });
      labelled.add(mi);
      for (let i = n - 1; i >= 0; i--)
        if (Number.isFinite(visibleTotals[i])) {
          labelled.add(i);
          break;
        }
    }
  }

  const marks: ReactElement[] = [];
  if (type === 'column' || type === 'bar') {
    data.categories.forEach((c, i) => {
      let acc = 0;
      const visible = series.filter((s) => Number.isFinite(s.values[i]) && s.values[i] > 0);
      visible.forEach((s, si) => {
        const v = s.values[i];
        const start = stacked ? acc : 0;
        const end = start + v;
        acc = end;
        const outer = !stacked || si === visible.length - 1;
        const gap = stacked && si > 0 ? 2 : 0; // surface gap between stacked segments
        let d: string;
        if (horizontal) {
          const x0 = val(start) + gap;
          const x1 = val(end);
          d = barPath(x0, center(i) - thickness / 2, x1 - x0, thickness, outer ? 'right' : 'none');
        } else {
          const y1 = val(end);
          const y0 = val(start) - gap;
          d = barPath(center(i) - thickness / 2, y1, thickness, y0 - y1, outer ? 'top' : 'none');
        }
        if (!d) return;
        marks.push(<path key={`${c.key}-${s.key}`} d={d} fill={s.color} opacity={active === null || active === i ? 1 : 0.55} />);
      });
    });
  } else {
    // line / area
    const baseline = new Array(n).fill(0);
    series.forEach((s, si) => {
      const pts: [number, number][] = [];
      const lows: [number, number][] = [];
      s.values.forEach((v, i) => {
        if (!Number.isFinite(v)) return;
        const lo = stacked ? baseline[i] : 0;
        const hi = lo + v;
        pts.push([center(i), val(hi)]);
        lows.push([center(i), val(lo)]);
        if (stacked) baseline[i] = hi;
      });
      if (!pts.length) return;
      const line = pts.map((p, k) => `${k ? 'L' : 'M'}${p[0]},${p[1]}`).join('');
      if (type === 'area') {
        const area = `${line}${[...lows].reverse().map((p) => `L${p[0]},${p[1]}`).join('')}Z`;
        marks.push(<path key={`a-${s.key}`} d={area} fill={s.color} opacity={0.1} />);
      }
      marks.push(<path key={`l-${s.key}`} d={line} fill="none" stroke={s.color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />);
      const last = pts[pts.length - 1];
      marks.push(<circle key={`e-${s.key}-${si}`} cx={last[0]} cy={last[1]} r={4} fill={s.color} stroke={chrome.surface} strokeWidth={2} />);
      if (active !== null && Number.isFinite(s.values[active])) {
        const lo = stacked ? series.slice(0, si).reduce((a, x) => a + (Number.isFinite(x.values[active]) ? x.values[active] : 0), 0) : 0;
        marks.push(<circle key={`h-${s.key}`} cx={center(active)} cy={val(lo + s.values[active])} r={4.5} fill={s.color} stroke={chrome.surface} strokeWidth={2} />);
      }
    });
  }

  const summary = describe(data, fmt, title);
  const tooltip = active !== null && n ? (
    <Tooltip
      x={pointer?.x ?? (horizontal ? margin.left + plotW * 0.6 : margin.left + center(active))}
      y={pointer?.y ?? (horizontal ? margin.top + center(active) : margin.top + 20)}
      width={width}
      title={data.categories[active].longLabel}
      rows={series.map((s) => ({ key: s.key, label: s.label, color: s.color, value: s.values[active] }))}
      total={stacked ? visibleTotals[active] : undefined}
      count={data.kind === 'average' ? data.counts[active] : undefined}
      goal={data.goal?.[active]}
      fmt={fmt}
      unit={data.unit}
      line={type === 'line' || type === 'area'}
    />
  ) : null;

  return (
    <div className="chart">
      <div className="chart__plot" ref={wrapRef} style={{ display: table ? 'none' : undefined }}>
        {n === 0 || data.empty ? (
          <div className="chart__empty ui">
            <Icon name="chart" />
            <p>No reading matches this selection.</p>
            <p className="small faint">Try a longer date range or fewer filters.</p>
          </div>
        ) : (
          <svg
            ref={svgRef}
            width={width}
            height={H}
            viewBox={`0 0 ${width} ${H}`}
            role="img"
            aria-label={summary}
            tabIndex={0}
            onPointerMove={onMove}
            onPointerLeave={() => (setActive(null), setPointer(null))}
            onKeyDown={onKey}
            onBlur={() => setActive(null)}
            style={{ fontFamily: chrome.font, display: 'block', touchAction: 'pan-y' }}
          >
            <title>{summary}</title>
            <g transform={`translate(${margin.left},${margin.top})`}>
              {/* gridlines: pale hairlines */}
              {ticks.map((t) =>
                horizontal ? (
                  <g key={t}>
                    <line x1={val(t)} x2={val(t)} y1={0} y2={plotH} stroke={t === 0 ? chrome.baseline : chrome.grid} strokeWidth={1} />
                    <text x={val(t)} y={plotH + 17} textAnchor="middle" fontSize={11} fill={chrome.inkFaint}>
                      {tickLabel(t, top)}
                    </text>
                  </g>
                ) : (
                  <g key={t}>
                    <line x1={0} x2={plotW} y1={val(t)} y2={val(t)} stroke={t === 0 ? chrome.baseline : chrome.grid} strokeWidth={1} />
                    <text x={-8} y={val(t)} dy="0.32em" textAnchor="end" fontSize={11} fill={chrome.inkFaint} className="nums">
                      {tickLabel(t, top)}
                    </text>
                  </g>
                ),
              )}

              {/* hover band / crosshair */}
              {active !== null &&
                (type === 'line' || type === 'area' ? (
                  <line x1={center(active)} x2={center(active)} y1={0} y2={plotH} stroke={chrome.inkFaint} strokeWidth={1} />
                ) : horizontal ? (
                  <rect x={-labelW} y={active * step} width={plotW + labelW + margin.right} height={step} fill={chrome.grid} opacity={0.5} />
                ) : (
                  <rect x={active * step} y={0} width={step} height={plotH} fill={chrome.grid} opacity={0.5} />
                ))}

              {marks}

              {/* reference lines */}
              {data.average !== undefined && Number.isFinite(data.average) && (
                <g>
                  {horizontal ? (
                    <line x1={val(data.average)} x2={val(data.average)} y1={-6} y2={plotH} stroke={chrome.inkSoft} strokeWidth={1} strokeDasharray="4 3" />
                  ) : (
                    <line x1={0} x2={plotW} y1={val(data.average)} y2={val(data.average)} stroke={chrome.inkSoft} strokeWidth={1} strokeDasharray="4 3" />
                  )}
                  <text
                    x={horizontal ? val(data.average) + 4 : plotW}
                    y={horizontal ? -8 : val(data.average) - 5}
                    textAnchor={horizontal ? 'start' : 'end'}
                    fontSize={10.5}
                    fill={chrome.inkSoft}
                    stroke={chrome.surface}
                    strokeWidth={3}
                    paintOrder="stroke"
                  >
                    avg {fmt(data.average)}
                  </text>
                </g>
              )}
              {data.goal && !horizontal && (
                <g>
                  <path
                    d={data.goal.map((g, i) => `${i ? 'L' : 'M'}${center(i)},${val(g)}`).join('')}
                    fill="none"
                    stroke={chrome.ink}
                    strokeWidth={1.25}
                    strokeDasharray="5 4"
                    opacity={0.7}
                  />
                  <text
                    x={center(n - 1)}
                    y={val(data.goal[n - 1]) - 7}
                    textAnchor="end"
                    fontSize={10.5}
                    fill={chrome.inkSoft}
                    stroke={chrome.surface}
                    strokeWidth={3}
                    paintOrder="stroke"
                  >
                    goal pace {fmt(data.goal[n - 1])}
                  </text>
                </g>
              )}

              {/* direct value labels (selective) */}
              {[...labelled].map((i) => {
                const v = visibleTotals[i];
                if (!Number.isFinite(v) || (v === 0 && type !== 'bar')) return null;
                return horizontal ? (
                  <text key={`v${i}`} x={val(v) + 6} y={center(i)} dy="0.32em" fontSize={11} fontWeight={600} fill={chrome.ink}>
                    {fmt(v)}
                  </text>
                ) : (
                  <text key={`v${i}`} x={center(i)} y={val(v) - 7} textAnchor="middle" fontSize={11} fontWeight={600} fill={chrome.ink} stroke={chrome.surface} strokeWidth={3} paintOrder="stroke">
                    {fmt(v)}
                  </text>
                );
              })}

              {/* category axis */}
              {data.categories.map((c, i) =>
                horizontal ? (
                  <text key={c.key} x={-10} y={center(i)} dy="0.32em" textAnchor="end" fontSize={11.5} fill={active === i ? chrome.ink : chrome.inkSoft}>
                    {truncate(c.label, labelW - 14, 11.5)}
                  </text>
                ) : (n - 1 - i) % every === 0 ? (
                  // Stride anchored on the most recent category, so the last label is always shown.
                  <text key={c.key} x={center(i)} y={plotH + 18} textAnchor="middle" fontSize={11} fill={active === i ? chrome.ink : chrome.inkFaint}>
                    {c.label}
                  </text>
                ) : null,
              )}
            </g>
          </svg>
        )}
        {tooltip}
      </div>

      {table && <DataTable data={data} fmt={fmt} />}

      <div className="chart__foot">
        {data.series.length > 1 ? (
          <ul className="legend" aria-label="Legend — select to show or hide a series">
            {data.series.map((s) => (
              <li key={s.key}>
                <button type="button" aria-pressed={!hidden.has(s.key)} onClick={() => setHidden((h) => { const x = new Set(h); if (x.has(s.key)) x.delete(s.key); else x.add(s.key); return x; })}>
                  {type === 'line' ? <span className="legend__line" style={{ background: s.color }} /> : <span className="legend__swatch" style={{ background: s.color }} />}
                  {s.label}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <span />
        )}
        <div className="row chart__tools">
          <button type="button" className="btn btn--ghost btn--sm" aria-pressed={table} onClick={() => setTable((t) => !t)}>
            <Icon name={table ? 'chart' : 'table'} /> {table ? 'Chart' : 'Table'}
          </button>
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => downloadFile(`${slug(title)}.csv`, toCsv(data), 'text/csv')}>
            <Icon name="download" /> CSV
          </button>
          {!table && n > 0 && !data.empty && (
            <>
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => svgRef.current && exportSvg(svgRef.current, title, chrome)}>
                SVG
              </button>
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => svgRef.current && exportPng(svgRef.current, title, chrome)}>
                PNG
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function Tooltip({
  x,
  y,
  width,
  title,
  rows,
  total,
  count,
  goal,
  fmt,
  unit,
  line,
}: {
  x: number;
  y: number;
  width: number;
  title: string;
  rows: { key: string; label: string; color: string; value: number }[];
  total?: number;
  count?: number;
  goal?: number;
  fmt: (v: number) => string;
  unit: string;
  line: boolean;
}) {
  const left = Math.min(Math.max(8, x + 14), width - 200);
  return (
    <div className="tip" style={{ left, top: Math.max(4, y - 10) }} role="status">
      <div className="tip__title">{title}</div>
      {rows.map((r) => (
        <div key={r.key} className="tip__row">
          <span className="tip__key" style={{ background: r.color, height: line ? 2 : 8, width: line ? 12 : 8, borderRadius: line ? 2 : 2 }} />
          <span className="tip__val">{Number.isFinite(r.value) ? fmt(r.value) : '—'}</span>
          <span className="tip__name">{rows.length > 1 ? r.label : unitFor(r.value, unit)}</span>
        </div>
      ))}
      {total !== undefined && (
        <div className="tip__row" style={{ marginTop: 3, paddingTop: 3, borderTop: '1px solid var(--rule)' }}>
          <span className="tip__key" />
          <span className="tip__val">{fmt(total)}</span>
          <span className="tip__name">total</span>
        </div>
      )}
      {count !== undefined && <div className="tip__name" style={{ marginTop: 2 }}>from {count} {count === 1 ? 'book' : 'books'}</div>}
      {goal !== undefined && <div className="tip__name" style={{ marginTop: 2 }}>goal pace {fmt(goal)}</div>}
    </div>
  );
}

function DataTable({ data, fmt }: { data: ChartData; fmt: (v: number) => string }) {
  const multi = data.series.length > 1;
  return (
    <div className="table-wrap chart__table">
      <table className="table">
        <caption className="sr-only">
          {data.metricLabel} by {data.groupLabel}
        </caption>
        <thead>
          <tr>
            <th scope="col">{data.groupLabel}</th>
            {data.series.map((s) => (
              <th key={s.key} scope="col" className="num">
                {multi ? s.label : data.metricLabel}
              </th>
            ))}
            {multi && data.kind !== 'average' && (
              <th scope="col" className="num">
                Total
              </th>
            )}
            {data.goal && (
              <th scope="col" className="num">
                Goal pace
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {data.categories.map((c, i) => (
            <tr key={c.key}>
              <th scope="row" style={{ fontWeight: 400, color: 'var(--ink)' }}>
                {c.longLabel}
              </th>
              {data.series.map((s) => (
                <td key={s.key} className="num">
                  {Number.isFinite(s.values[i]) ? fmt(s.values[i]) : '—'}
                </td>
              ))}
              {multi && data.kind !== 'average' && <td className="num">{fmt(data.totals[i])}</td>}
              {data.goal && <td className="num">{fmt(data.goal[i])}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ------------------------------------------------------------------ */

/** "1 books" → "1 book". */
const unitFor = (v: number, unit: string) => (Math.abs(v) === 1 && unit.endsWith('s') && !unit.includes('/') ? unit.slice(0, -1) : unit);

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'chart';

function describe(data: ChartData, fmt: (v: number) => string, title: string): string {
  if (data.empty || !data.categories.length) return `${title}: no data.`;
  let mi = 0;
  data.totals.forEach((v, i) => {
    if (Number.isFinite(v) && v > data.totals[mi]) mi = i;
  });
  return `${title}. ${data.categories.length} ${data.groupLabel.toLowerCase()} values from ${data.categories[0].longLabel} to ${data.categories[data.categories.length - 1].longLabel}. Highest: ${data.categories[mi].longLabel}, ${fmt(data.totals[mi])} ${data.unit}.`;
}

export function toCsv(data: ChartData): string {
  const esc = (s: string) => (/[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s);
  const head = [data.groupLabel, ...data.series.map((s) => (data.series.length > 1 ? s.label : data.metricLabel))];
  if (data.series.length > 1 && data.kind !== 'average') head.push('Total');
  const rows = data.categories.map((c, i) => {
    const r = [c.longLabel, ...data.series.map((s) => (Number.isFinite(s.values[i]) ? String(Math.round(s.values[i] * 100) / 100) : ''))];
    if (data.series.length > 1 && data.kind !== 'average') r.push(String(Math.round(data.totals[i] * 100) / 100));
    return r;
  });
  return [head, ...rows].map((r) => r.map(esc).join(',')).join('\n');
}

function standaloneSvg(svg: SVGSVGElement, chrome: ChartChrome): string {
  const clone = svg.cloneNode(true) as SVGSVGElement;
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  const bg = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  bg.setAttribute('width', '100%');
  bg.setAttribute('height', '100%');
  bg.setAttribute('fill', chrome.surface);
  clone.insertBefore(bg, clone.firstChild);
  return new XMLSerializer().serializeToString(clone);
}

function exportSvg(svg: SVGSVGElement, title: string, chrome: ChartChrome) {
  downloadFile(`${slug(title)}.svg`, standaloneSvg(svg, chrome), 'image/svg+xml');
}

function exportPng(svg: SVGSVGElement, title: string, chrome: ChartChrome) {
  const w = svg.width.baseVal.value;
  const h = svg.height.baseVal.value;
  const img = new Image();
  img.onload = () => {
    const scale = 2;
    const canvas = document.createElement('canvas');
    canvas.width = w * scale;
    canvas.height = h * scale;
    const ctx = canvas.getContext('2d')!;
    ctx.scale(scale, scale);
    ctx.drawImage(img, 0, 0, w, h);
    canvas.toBlob((blob) => blob && downloadFile(`${slug(title)}.png`, blob));
  };
  img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(standaloneSvg(svg, chrome));
}
