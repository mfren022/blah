import { useSyncExternalStore } from 'react';
import { flushSync } from 'react-dom';

/** Hash routes, so the app works from any static host (and from file://). */
export type Route =
  | { name: 'reading' }
  | { name: 'browse'; params: URLSearchParams }
  | { name: 'library'; params: URLSearchParams }
  | { name: 'book'; id: string; params: URLSearchParams }
  | { name: 'stats' }
  | { name: 'settings'; section?: string };

export type TabName = 'reading' | 'browse' | 'library' | 'stats' | 'settings';

export const TABS: { name: TabName; label: string; icon: string; title: string }[] = [
  { name: 'reading', label: 'Reading', icon: 'bookmark', title: 'Currently Reading' },
  { name: 'browse', label: 'Browse', icon: 'search', title: 'Browse' },
  { name: 'library', label: 'Library', icon: 'shelf', title: 'Library' },
  { name: 'stats', label: 'Statistics', icon: 'chart', title: 'Statistics' },
  { name: 'settings', label: 'Settings', icon: 'sliders', title: 'Settings' },
];

export function parseHash(hash: string): Route {
  const raw = hash.replace(/^#\/?/, '');
  const [path, query = ''] = raw.split('?');
  const params = new URLSearchParams(query);
  const parts = path.split('/').filter(Boolean).map((p) => {
    try {
      return decodeURIComponent(p);
    } catch {
      return p;
    }
  });
  switch (parts[0]) {
    case 'browse':
      return { name: 'browse', params };
    case 'library':
      return { name: 'library', params };
    case 'book':
      if (parts[1]) return { name: 'book', id: parts.slice(1).join('/'), params };
      return { name: 'library', params };
    case 'stats':
      return { name: 'stats' };
    case 'settings':
      return { name: 'settings', section: parts[1] };
    default:
      return { name: 'reading' };
  }
}

export const bookHref = (id: string) => `#/book/${encodeURIComponent(id)}`;

export function hrefWith(base: string, params: Record<string, string | number | undefined | null>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') q.set(k, String(v));
  const s = q.toString();
  return s ? `${base}?${s}` : base;
}

/** Position of a route in the book, used to decide which way pages turn. */
export function routeOrder(r: Route): number {
  if (r.name === 'book') return 2.5;
  return TABS.findIndex((t) => t.name === r.name);
}

export const routeKey = (r: Route) => (r.name === 'book' ? `book:${r.id}` : r.name);

export function tabOf(r: Route): TabName {
  if (r.name === 'book') return (r.params.get('from') as TabName) || 'library';
  return r.name;
}

/* ------------------------------------------------------------------ */

let current: Route = typeof location !== 'undefined' ? parseHash(location.hash) : { name: 'reading' };
let currentHash = typeof location !== 'undefined' ? location.hash : '';
const listeners = new Set<() => void>();

function emit(next: Route, hash: string) {
  current = next;
  currentHash = hash;
  listeners.forEach((l) => l());
}

let activeTransition: { skipTransition: () => void } | null = null;

function prefersReducedMotion() {
  return typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function apply(next: Route, hash: string) {
  const prev = current;
  const changesPage = routeKey(prev) !== routeKey(next);
  const turn = document.documentElement.dataset.turn;
  type VT = { ready: Promise<void>; finished: Promise<void>; updateCallbackDone: Promise<void>; skipTransition: () => void };
  const doc = document as Document & { startViewTransition?: (cb: () => void) => VT };
  if (changesPage && turn && turn !== 'none' && typeof doc.startViewTransition === 'function' && !prefersReducedMotion() && document.visibilityState === 'visible') {
    document.documentElement.dataset.dir = routeOrder(next) >= routeOrder(prev) ? 'fwd' : 'back';
    activeTransition?.skipTransition();
    const vt = doc.startViewTransition(() => flushSync(() => emit(next, hash)));
    activeTransition = vt;
    // A transition interrupted by a quicker navigation rejects these; that's expected.
    vt.ready.catch(() => {});
    vt.updateCallbackDone.catch(() => {});
    vt.finished.catch(() => {}).finally(() => {
      if (activeTransition === vt) activeTransition = null;
    });
  } else {
    emit(next, hash);
  }
}

export function navigate(to: string, opts: { replace?: boolean } = {}) {
  const hash = to.startsWith('#') ? to : `#${to.startsWith('/') ? to : `/${to}`}`;
  if (hash === location.hash) return;
  try {
    if (opts.replace) history.replaceState(null, '', hash);
    else history.pushState(null, '', hash);
  } catch {
    /* some sandboxed frames refuse history changes; keep the route in memory */
  }
  apply(parseHash(hash), hash);
}

/** Update query parameters of the current route without adding history or animating. */
export function setQuery(params: Record<string, string | number | undefined | null>) {
  const base = location.hash.split('?')[0] || '#/';
  const hash = hrefWith(base, params);
  if (hash === location.hash) return;
  try {
    history.replaceState(null, '', hash);
  } catch {
    /* see navigate() */
  }
  emit(parseHash(hash), hash);
}

if (typeof window !== 'undefined') {
  const onExternal = () => {
    if (location.hash !== currentHash) apply(parseHash(location.hash), location.hash);
  };
  window.addEventListener('popstate', onExternal);
  window.addEventListener('hashchange', onExternal);
}

function subscribe(fn: () => void) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function useRoute(): Route {
  return useSyncExternalStore(subscribe, () => current, () => current);
}
