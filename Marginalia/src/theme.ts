import { useEffect, useState, useSyncExternalStore } from 'react';
import { themeAttributes, themeVariables, type Settings } from './data/settings';

const darkQuery = () => (typeof matchMedia !== 'undefined' ? matchMedia('(prefers-color-scheme: dark)') : null);

function subscribeScheme(cb: () => void) {
  const q = darkQuery();
  q?.addEventListener('change', cb);
  return () => q?.removeEventListener('change', cb);
}

export function useSystemDark(): boolean {
  return useSyncExternalStore(subscribeScheme, () => !!darkQuery()?.matches, () => false);
}

export function resolvedMode(settings: Settings, systemDark: boolean): 'light' | 'dark' {
  return settings.mode === 'system' ? (systemDark ? 'dark' : 'light') : settings.mode;
}

/** Writes the theme to <html> as custom properties and data attributes. */
export function useThemeApplier(settings: Settings): 'light' | 'dark' {
  const systemDark = useSystemDark();
  const mode = resolvedMode(settings, systemDark);
  useEffect(() => {
    const root = document.documentElement;
    const vars = themeVariables(settings, mode);
    for (const [k, v] of Object.entries(vars)) root.style.setProperty(k, v);
    for (const [k, v] of Object.entries(themeAttributes(settings))) root.setAttribute(k, v);
    if (settings.mode === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', settings.mode);
    root.setAttribute('data-mode', mode);
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', vars['--cover']);
  }, [settings, mode]);
  return mode;
}

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => (typeof matchMedia !== 'undefined' ? matchMedia(query).matches : false));
  useEffect(() => {
    const q = matchMedia(query);
    const on = () => setMatches(q.matches);
    on();
    q.addEventListener('change', on);
    return () => q.removeEventListener('change', on);
  }, [query]);
  return matches;
}
