import { useEffect, useState, useSyncExternalStore } from 'react';
import { themeAttributes, themeVariables, type Settings } from './data/settings';

const darkQuery = () => (typeof matchMedia !== 'undefined' ? matchMedia('(prefers-color-scheme: dark)') : null);

/*
 * When the app is embedded (for example as a preview), the host page may set
 * data-theme="light" | "dark" on <html> to pass on the reader's own choice. In "match
 * device" mode we follow that choice instead of the OS setting, and never remove it.
 */
let hostTheme: 'light' | 'dark' | null = null;
let lastWritten: string | null = null;
const listeners = new Set<() => void>();

if (typeof document !== 'undefined') {
  const root = document.documentElement;
  const initial = root.getAttribute('data-theme');
  if ((initial === 'light' || initial === 'dark') && !root.hasAttribute('data-app-theme')) hostTheme = initial;
  if (root.hasAttribute('data-app-theme')) lastWritten = initial;
  if (typeof MutationObserver !== 'undefined') {
    new MutationObserver(() => {
      const v = root.getAttribute('data-theme');
      if (v === lastWritten) return; // our own write
      const next = v === 'light' || v === 'dark' ? v : null;
      if (next !== hostTheme) {
        hostTheme = next;
        listeners.forEach((l) => l());
      }
    }).observe(root, { attributes: true, attributeFilter: ['data-theme'] });
  }
}

function subscribeScheme(cb: () => void) {
  const q = darkQuery();
  q?.addEventListener('change', cb);
  listeners.add(cb);
  return () => {
    q?.removeEventListener('change', cb);
    listeners.delete(cb);
  };
}

/** True when the surroundings (host page choice, else the device) prefer dark. */
export function useSystemDark(): boolean {
  return useSyncExternalStore(subscribeScheme, () => (hostTheme ? hostTheme === 'dark' : !!darkQuery()?.matches), () => false);
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
    if (settings.mode === 'system') {
      if (!hostTheme) {
        lastWritten = null;
        root.removeAttribute('data-theme');
      }
    } else {
      lastWritten = settings.mode;
      root.setAttribute('data-theme', settings.mode);
    }
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
