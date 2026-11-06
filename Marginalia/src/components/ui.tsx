import { useEffect, useId, useRef, useState, type ReactNode, type KeyboardEvent, type PointerEvent } from 'react';
import type { Status } from '../lib/types';
import { useSettings } from '../data/store';
import { STATUS_LABELS } from '../stats/engine';
import { Icon } from './Icon';

/* ------------------------------------------------------------------ */
/* Rule headings: text with pale lines vertically centred on it.       */
/* ------------------------------------------------------------------ */

type RuleLevel = 'page' | 'section' | 'panel' | 'minor';

export function Rule({
  children,
  level = 'section',
  as,
  sub,
  id,
  className = '',
}: {
  children: ReactNode;
  level?: RuleLevel;
  as?: 'h1' | 'h2' | 'h3' | 'h4' | 'div';
  sub?: ReactNode;
  id?: string;
  className?: string;
}) {
  const Tag = as ?? (level === 'page' ? 'h1' : level === 'section' ? 'h2' : 'h3');
  return (
    <Tag className={`rule rule--${level} ${className}`} id={id}>
      <span className="rule__orn" aria-hidden />
      <span className="rule__text">
        {children}
        {sub && <span className="rule__sub">{sub}</span>}
      </span>
      <span className="rule__orn" aria-hidden />
    </Tag>
  );
}

/** A panel: a loose leaf on the page, with its own little stack of leaves. */
export function Leaf({
  title,
  sub,
  children,
  className = '',
  flush,
  quiet,
  id,
  actions,
}: {
  title?: ReactNode;
  sub?: ReactNode;
  children: ReactNode;
  className?: string;
  flush?: boolean;
  quiet?: boolean;
  id?: string;
  actions?: ReactNode;
}) {
  return (
    <section className={`leaf ${flush ? 'leaf--flush' : ''} ${quiet ? 'leaf--quiet' : ''} ${className}`} id={id} aria-label={typeof title === 'string' ? title : undefined}>
      {title && (
        <Rule level="panel" as="h2" sub={sub}>
          {title}
        </Rule>
      )}
      {actions && <div className="leaf__actions">{actions}</div>}
      {children}
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* Ratings                                                             */
/* ------------------------------------------------------------------ */

const STAR = 'M12 3.2l2.62 5.48 6.02.78-4.4 4.17 1.1 5.97L12 16.7l-5.34 2.9 1.1-5.97-4.4-4.17 6.02-.78z';
const HEART = 'M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z';

function Glyph({ fill, shape }: { fill: number; shape: string }) {
  return (
    <span className="glyph" style={{ position: 'relative', display: 'inline-block', width: '1em', height: '1em' }}>
      <svg viewBox="0 0 24 24" className="stars__empty" aria-hidden style={{ position: 'absolute', inset: 0 }}>
        <path d={shape} fill="currentColor" />
      </svg>
      {fill > 0 && (
        <span style={{ position: 'absolute', inset: 0, width: `${fill * 100}%`, overflow: 'hidden' }}>
          <svg viewBox="0 0 24 24" aria-hidden style={{ width: '1em', height: '1em' }}>
            <path d={shape} fill="currentColor" />
          </svg>
        </span>
      )}
    </span>
  );
}

export function Stars({ value, size = '1rem', showNumber }: { value: number | undefined; size?: string; showNumber?: boolean }) {
  const settings = useSettings();
  const v = value ?? 0;
  if (settings.ratingStyle === 'numeric') {
    return <span className="rating-num" aria-label={value ? `${v} out of 5` : 'Not rated'}>{value ? `${v.toFixed(1)} / 5` : '—'}</span>;
  }
  const shape = settings.ratingStyle === 'hearts' ? HEART : STAR;
  return (
    <span className="stars" style={{ fontSize: size }} role="img" aria-label={value ? `${Math.round(v * 10) / 10} out of 5` : 'Not rated'}>
      {[0, 1, 2, 3, 4].map((i) => (
        <Glyph key={i} shape={shape} fill={Math.max(0, Math.min(1, v - i))} />
      ))}
      {showNumber && value ? <span className="rating-num" style={{ marginLeft: 6 }}>{v.toFixed(2).replace(/0$/, '')}</span> : null}
    </span>
  );
}

/** Accessible rating input: a slider over five stars, in half or whole steps. */
export function StarInput({ value, onChange, label = 'Your rating' }: { value: number | undefined; onChange: (v: number | undefined) => void; label?: string }) {
  const settings = useSettings();
  const step = settings.halfStars ? 0.5 : 1;
  const [hover, setHover] = useState<number | null>(null);
  const ref = useRef<HTMLDivElement>(null);
  const shape = settings.ratingStyle === 'hearts' ? HEART : STAR;
  const shown = hover ?? value ?? 0;

  const fromPointer = (e: PointerEvent) => {
    const r = ref.current!.getBoundingClientRect();
    const x = Math.max(0, Math.min(r.width, e.clientX - r.left));
    const raw = (x / r.width) * 5;
    return Math.max(step, Math.ceil(raw / step) * step);
  };
  const set = (v: number | undefined) => onChange(v && v > 0 ? Math.min(5, v) : undefined);
  const onKey = (e: KeyboardEvent) => {
    const cur = value ?? 0;
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') set(cur + step);
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') set(cur - step);
    else if (e.key === 'Home') set(step);
    else if (e.key === 'End') set(5);
    else if (e.key === 'Delete' || e.key === 'Backspace' || e.key === '0') set(undefined);
    else return;
    e.preventDefault();
  };

  return (
    <div className="row" style={{ gap: 10 }}>
      <div
        ref={ref}
        className="stars stars--input"
        role="slider"
        tabIndex={0}
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={5}
        aria-valuenow={value ?? 0}
        aria-valuetext={value ? `${value} out of 5` : 'Not rated'}
        onKeyDown={onKey}
        onPointerMove={(e) => setHover(fromPointer(e))}
        onPointerLeave={() => setHover(null)}
        onPointerDown={(e) => {
          const v = fromPointer(e);
          set(v === value ? undefined : v);
        }}
      >
        {[0, 1, 2, 3, 4].map((i) => (
          <Glyph key={i} shape={shape} fill={Math.max(0, Math.min(1, shown - i))} />
        ))}
      </div>
      <span className="rating-num nums">{value ? `${value} / 5` : 'Not rated'}</span>
      {value ? (
        <button type="button" className="btn btn--ghost btn--sm" onClick={() => set(undefined)}>
          Clear
        </button>
      ) : null}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Small pieces                                                        */
/* ------------------------------------------------------------------ */

export function Stamp({ status, flat, label }: { status: Status; flat?: boolean; label?: string }) {
  const short: Record<Status, string> = { want: 'Want to read', reading: 'Reading', finished: 'Finished', dnf: 'DNF' };
  return (
    <span className={`stamp stamp--${status} ${flat ? 'stamp--flat' : ''}`} title={STATUS_LABELS[status]}>
      {label ?? short[status]}
    </span>
  );
}

export function Progress({ value, max, thin, label }: { value: number; max: number; thin?: boolean; label?: string }) {
  const pct = max > 0 ? Math.max(0, Math.min(100, (value / max) * 100)) : 0;
  return (
    <div className={`progress ${thin ? 'progress--thin' : ''}`} role="progressbar" aria-valuemin={0} aria-valuemax={max || 100} aria-valuenow={value} aria-label={label ?? 'Progress'}>
      <div className="progress__fill" style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Seg<T extends string>({
  value,
  options,
  onChange,
  label,
  size,
}: {
  value: T;
  options: { value: T; label: ReactNode; icon?: string; title?: string }[];
  onChange: (v: T) => void;
  label: string;
  size?: 'sm';
}) {
  return (
    <div className="seg" role="group" aria-label={label} style={size === 'sm' ? { fontSize: '0.92em' } : undefined}>
      {options.map((o) => (
        <button key={o.value} type="button" aria-pressed={o.value === value} title={o.title} onClick={() => onChange(o.value)}>
          {o.icon && <Icon name={o.icon} />}
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Switch({ checked, onChange, label, hint }: { checked: boolean; onChange: (v: boolean) => void; label: ReactNode; hint?: ReactNode }) {
  return (
    <label className="switch">
      <input type="checkbox" role="switch" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span>
        {label}
        {hint && <span className="field__hint" style={{ display: 'block' }}>{hint}</span>}
      </span>
    </label>
  );
}

export function RangeField({
  label,
  value,
  min,
  max,
  step = 1,
  onChange,
  format = (v) => String(v),
  hint,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (v: number) => void;
  format?: (v: number) => string;
  hint?: ReactNode;
}) {
  const id = useId();
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      <div className="range">
        <input id={id} type="range" min={min} max={max} step={step} value={value} onChange={(e) => onChange(Number(e.target.value))} />
        <output htmlFor={id}>{format(value)}</output>
      </div>
      {hint && <span className="field__hint">{hint}</span>}
    </div>
  );
}

export function SelectField<T extends string>({
  label,
  value,
  options,
  onChange,
  hint,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
  hint?: ReactNode;
}) {
  const id = useId();
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      <select id={id} className="select" value={value} onChange={(e) => onChange(e.target.value as T)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {hint && <span className="field__hint">{hint}</span>}
    </div>
  );
}

export function Empty({ icon = 'book', title, children, action }: { icon?: string; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty__art">
        <Icon name={icon} className="icon" />
      </div>
      <h3>{title}</h3>
      {children && <p className="small">{children}</p>}
      {action && <div className="row" style={{ justifyContent: 'center', marginTop: 12 }}>{action}</div>}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Dialog: a loose sheet laid over the book                            */
/* ------------------------------------------------------------------ */

export function Dialog({ open, onClose, title, children, wide }: { open: boolean; onClose: () => void; title: string; children: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      className="sheet-dialog"
      style={wide ? { width: 'min(760px, calc(100vw - 24px))' } : undefined}
      onClose={onClose}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      aria-label={title}
    >
      {open && (
        <div className="sheet-dialog__inner">
          <button type="button" className="btn btn--ghost btn--icon sheet-dialog__close" onClick={onClose} aria-label="Close">
            <Icon name="x" />
          </button>
          <Rule level="panel" as="h2">
            {title}
          </Rule>
          <div style={{ marginTop: 14 }}>{children}</div>
        </div>
      )}
    </dialog>
  );
}
