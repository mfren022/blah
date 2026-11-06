import { useEffect, useRef, useState } from 'react';
import type { LibraryEntry } from '../lib/types';
import { heatmap } from '../stats/engine';
import { formatDate, MONTHS_SHORT, today } from '../lib/dates';
import { formatNumber } from '../lib/util';

const CELL = 12;
const GAP = 2; // surface gap between cells

/** A year of reading, one cell per day (sequential, one hue). */
export function Heatmap({ entries, weekStart, colors, empty }: { entries: LibraryEntry[]; weekStart: 0 | 1; colors: string[]; empty: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(700);
  const [hover, setHover] = useState<{ date: string; pages: number; x: number; y: number } | null>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setWidth(e.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const left = 26;
  const weeksFit = Math.max(12, Math.min(53, Math.floor((width - left) / (CELL + GAP))));
  const { weeks, thresholds, total } = heatmap(entries, today(), weekStart, weeksFit);
  const days = weeks.flat().filter((c) => !c.future && c.pages > 0).length;
  const w = left + weeks.length * (CELL + GAP);
  const h = 16 + 7 * (CELL + GAP);
  const dayLabels = weekStart === 1 ? ['Mon', '', 'Wed', '', 'Fri', '', ''] : ['', 'Mon', '', 'Wed', '', 'Fri', ''];
  const fill = (level: number) => (level === 0 ? empty : colors[level - 1]);

  return (
    <div className="heat" ref={ref}>
      <div style={{ position: 'relative' }}>
        <svg width={w} height={h} role="img" aria-label={`Reading calendar for the last ${weeks.length} weeks: ${formatNumber(Math.round(total))} pages on ${days} days.`}>
          {weeks.map((col, wi) => {
            const first = col[0];
            const showMonth = wi === 0 ? Number(first.date.slice(8)) <= 7 : col.some((c) => c.date.slice(8) === '01');
            const monthOf = col.find((c) => c.date.slice(8) === '01')?.date ?? first.date;
            return (
              <g key={first.date} transform={`translate(${left + wi * (CELL + GAP)},16)`}>
                {showMonth && (
                  <text x={0} y={-5} fontSize={10} fill="var(--ink-faint)" fontFamily="var(--font-ui)">
                    {MONTHS_SHORT[Number(monthOf.slice(5, 7)) - 1]}
                  </text>
                )}
                {col.map((c, di) =>
                  c.future ? null : (
                    <rect
                      key={c.date}
                      x={0}
                      y={di * (CELL + GAP)}
                      width={CELL}
                      height={CELL}
                      rx={2.5}
                      fill={fill(c.level)}
                      stroke={c.date === today() ? 'var(--ink)' : 'none'}
                      strokeWidth={c.date === today() ? 1.2 : 0}
                      onPointerEnter={(e) => {
                        const r = (e.currentTarget.ownerSVGElement as SVGSVGElement).getBoundingClientRect();
                        const b = e.currentTarget.getBoundingClientRect();
                        setHover({ date: c.date, pages: c.pages, x: b.left - r.left, y: b.top - r.top });
                      }}
                      onPointerLeave={() => setHover(null)}
                    />
                  ),
                )}
              </g>
            );
          })}
          {dayLabels.map((l, i) =>
            l ? (
              <text key={i} x={0} y={16 + i * (CELL + GAP) + CELL - 2} fontSize={9.5} fill="var(--ink-faint)" fontFamily="var(--font-ui)">
                {l}
              </text>
            ) : null,
          )}
        </svg>
        {hover && (
          <div className="tip" style={{ left: Math.min(hover.x + 16, w - 170), top: hover.y - 8 }}>
            <div className="tip__title">{formatDate(hover.date, 'long')}</div>
            <div className="tip__row">
              <span className="tip__val">{formatNumber(Math.round(hover.pages))}</span>
              <span className="tip__name">pages</span>
            </div>
          </div>
        )}
      </div>
      <div className="heat__foot ui">
        <span>
          {formatNumber(Math.round(total))} pages on {days} {days === 1 ? 'day' : 'days'} · last {weeks.length} weeks
        </span>
        <span className="heat__legend" aria-hidden>
          Less
          {[0, 1, 2, 3, 4].map((l) => (
            <span key={l} className="heat__swatch" style={{ background: fill(l) }} title={l === 0 ? 'No reading' : `up to ${Math.round(thresholds[l - 1] ?? 0)}+ pages`} />
          ))}
          More
        </span>
      </div>
    </div>
  );
}
