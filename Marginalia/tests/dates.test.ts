import { describe, expect, it } from 'vitest';
import { addDays, addMonths, daysBetween, eachDay, formatDate, isISODate, relativeDay, startOfWeek } from '../src/lib/dates';

describe('dates', () => {
  it('adds days across month and year ends', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('clamps to the last day of shorter months', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(addMonths('2026-03-31', -1)).toBe('2026-02-28');
    expect(addMonths('2026-11-06', -12)).toBe('2025-11-06');
  });

  it('counts whole days regardless of DST changes', () => {
    expect(daysBetween('2026-03-28', '2026-03-30')).toBe(2);
    expect(daysBetween('2026-10-24', '2026-10-26')).toBe(2);
    expect(daysBetween('2026-01-10', '2026-01-01')).toBe(-9);
    expect(eachDay('2026-03-28', '2026-03-31')).toHaveLength(4);
  });

  it('finds the start of the week', () => {
    expect(startOfWeek('2026-11-06', 1)).toBe('2026-11-02'); // Friday → Monday
    expect(startOfWeek('2026-11-06', 0)).toBe('2026-11-01'); // Friday → Sunday
  });

  it('formats in each style', () => {
    expect(formatDate('2026-11-06', 'medium')).toBe('6 Nov 2026');
    expect(formatDate('2026-11-06', 'long')).toBe('6 November 2026');
    expect(formatDate('2026-11-06', 'us')).toBe('Nov 6, 2026');
    expect(formatDate('2026-11-06', 'dmy')).toBe('06/11/2026');
    expect(formatDate('2026-11-06', 'mdy')).toBe('11/06/2026');
    expect(formatDate(undefined)).toBe('—');
  });

  it('validates ISO dates strictly', () => {
    expect(isISODate('2026-02-29')).toBe(false);
    expect(isISODate('2028-02-29')).toBe(true);
    expect(isISODate('2026-1-1')).toBe(false);
  });

  it('describes relative days', () => {
    expect(relativeDay('2026-11-06', '2026-11-06')).toBe('today');
    expect(relativeDay('2026-11-05', '2026-11-06')).toBe('yesterday');
    expect(relativeDay('2026-11-01', '2026-11-06')).toBe('5 days ago');
  });
});
