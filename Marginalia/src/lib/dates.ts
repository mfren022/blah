import type { ISODate, LocalDateTime } from './types';

export type DateFormat = 'medium' | 'long' | 'us' | 'iso' | 'dmy' | 'mdy';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
export const MONTHS_SHORT = MONTHS.map((m) => m.slice(0, 3));
export const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

const pad = (n: number) => String(n).padStart(2, '0');

export function toISODate(d: Date): ISODate {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function toLocalDateTime(d: Date): LocalDateTime {
  return `${toISODate(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export const today = (): ISODate => toISODate(new Date());
export const nowLocal = (): LocalDateTime => toLocalDateTime(new Date());

export function parseISODate(s: string): Date {
  const [y, m, d] = s.slice(0, 10).split('-').map(Number);
  return new Date(y, (m || 1) - 1, d || 1);
}

export function isISODate(s: unknown): s is ISODate {
  if (typeof s !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const d = parseISODate(s);
  return !Number.isNaN(d.getTime()) && toISODate(d) === s;
}

export function isLocalDateTime(s: unknown): s is LocalDateTime {
  return typeof s === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s) && isISODate(s.slice(0, 10));
}

/** The calendar date part of a date or date-time string. */
export const dateOf = (s: string): ISODate => s.slice(0, 10);

export function addDays(s: ISODate, n: number): ISODate {
  const d = parseISODate(s);
  d.setDate(d.getDate() + n);
  return toISODate(d);
}

export function addMonths(s: ISODate, n: number): ISODate {
  const d = parseISODate(s);
  const day = d.getDate();
  d.setDate(1);
  d.setMonth(d.getMonth() + n);
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(day, last));
  return toISODate(d);
}

/** Whole days from `a` to `b` (positive when b is later). DST-safe. */
export function daysBetween(a: ISODate, b: ISODate): number {
  const da = parseISODate(a);
  const db = parseISODate(b);
  return Math.round((Date.UTC(db.getFullYear(), db.getMonth(), db.getDate()) - Date.UTC(da.getFullYear(), da.getMonth(), da.getDate())) / 86_400_000);
}

export function startOfWeek(s: ISODate, weekStart: 0 | 1): ISODate {
  const dow = parseISODate(s).getDay();
  return addDays(s, -((dow - weekStart + 7) % 7));
}

export const monthKey = (s: ISODate) => s.slice(0, 7);
export const yearKey = (s: ISODate) => s.slice(0, 4);
export function quarterKey(s: ISODate) {
  return `${s.slice(0, 4)}-Q${Math.ceil(Number(s.slice(5, 7)) / 3)}`;
}

export function eachDay(from: ISODate, to: ISODate): ISODate[] {
  const out: ISODate[] = [];
  if (from > to) return out;
  let cur = from;
  // Guard against absurd ranges.
  for (let i = 0; i < 40_000 && cur <= to; i++) {
    out.push(cur);
    cur = addDays(cur, 1);
  }
  return out;
}

export function formatDate(s: string | undefined, fmt: DateFormat = 'medium'): string {
  if (!s || !isISODate(s.slice(0, 10))) return '—';
  const y = s.slice(0, 4);
  const m = Number(s.slice(5, 7));
  const d = Number(s.slice(8, 10));
  switch (fmt) {
    case 'long':
      return `${d} ${MONTHS[m - 1]} ${y}`;
    case 'us':
      return `${MONTHS_SHORT[m - 1]} ${d}, ${y}`;
    case 'iso':
      return s.slice(0, 10);
    case 'dmy':
      return `${pad(d)}/${pad(m)}/${y}`;
    case 'mdy':
      return `${pad(m)}/${pad(d)}/${y}`;
    default:
      return `${d} ${MONTHS_SHORT[m - 1]} ${y}`;
  }
}

/** Short form without the year when it is the current year. */
export function formatDateShort(s: string | undefined, fmt: DateFormat = 'medium'): string {
  if (!s || !isISODate(s.slice(0, 10))) return '—';
  if (s.slice(0, 4) !== today().slice(0, 4) || fmt === 'iso' || fmt === 'dmy' || fmt === 'mdy') return formatDate(s, fmt);
  const m = Number(s.slice(5, 7));
  const d = Number(s.slice(8, 10));
  return fmt === 'us' ? `${MONTHS_SHORT[m - 1]} ${d}` : `${d} ${MONTHS_SHORT[m - 1]}`;
}

export function formatTime(dt: LocalDateTime): string {
  return dt.length >= 16 ? dt.slice(11, 16) : '';
}

export function relativeDay(s: ISODate, ref: ISODate = today()): string {
  const n = daysBetween(s, ref);
  if (n === 0) return 'today';
  if (n === 1) return 'yesterday';
  if (n === -1) return 'tomorrow';
  if (n > 1 && n < 14) return `${n} days ago`;
  if (n < -1 && n > -14) return `in ${-n} days`;
  if (n >= 14 && n < 60) return `${Math.round(n / 7)} weeks ago`;
  if (n <= -14 && n > -60) return `in ${Math.round(-n / 7)} weeks`;
  if (n >= 60 && n < 365) return `${Math.round(n / 30)} months ago`;
  if (n <= -60 && n > -365) return `in ${Math.round(-n / 30)} months`;
  return n > 0 ? `${Math.round(n / 365)} years ago` : `in ${Math.round(-n / 365)} years`;
}

export function monthLabel(key: string, long = false): string {
  const m = Number(key.slice(5, 7));
  return long ? `${MONTHS[m - 1]} ${key.slice(0, 4)}` : `${MONTHS_SHORT[m - 1]} ${key.slice(2, 4)}`;
}
