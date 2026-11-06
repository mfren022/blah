import { createContext, useContext, useEffect, useMemo, useReducer, useRef, type Dispatch, type ReactNode } from 'react';
import { initialState, sanitizeState, STORAGE_KEY, type AppState } from './model';
import { reducer, type Action } from './reducer';
import type { LibraryEntry } from '../lib/types';

interface StoreValue {
  state: AppState;
  dispatch: Dispatch<Action>;
}

const StoreContext = createContext<StoreValue | null>(null);

function loadState(): AppState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? sanitizeState(JSON.parse(raw)) : initialState();
  } catch {
    return initialState();
  }
}

export type SaveStatus = 'saved' | 'error';
let saveListener: ((s: SaveStatus) => void) | null = null;
export function onSaveStatus(fn: ((s: SaveStatus) => void) | null) {
  saveListener = fn;
}

export function StoreProvider({ children, initial }: { children: ReactNode; initial?: AppState }) {
  const [state, dispatch] = useReducer(reducer, initial, (init) => init ?? loadState());
  const lastWritten = useRef<string>('');
  const latest = useRef(state);
  latest.current = state;

  useEffect(() => {
    const write = () => {
      const json = JSON.stringify(latest.current);
      if (json === lastWritten.current) return;
      try {
        localStorage.setItem(STORAGE_KEY, json);
        lastWritten.current = json;
        saveListener?.('saved');
      } catch {
        saveListener?.('error');
      }
    };
    const timer = window.setTimeout(write, 250);
    window.addEventListener('pagehide', write);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('pagehide', write);
    };
  }, [state]);

  // Keep several open tabs in sync.
  useEffect(() => {
    const onStorage = (ev: StorageEvent) => {
      if (ev.key !== STORAGE_KEY || !ev.newValue || ev.newValue === lastWritten.current) return;
      try {
        lastWritten.current = ev.newValue;
        dispatch({ type: 'hydrate', state: sanitizeState(JSON.parse(ev.newValue)) });
      } catch {
        /* ignore malformed writes from other tabs */
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const value = useMemo(() => ({ state, dispatch }), [state]);
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore(): StoreValue {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore must be used inside <StoreProvider>');
  return ctx;
}

export function useSettings() {
  return useStore().state.settings;
}

export function useEntry(id: string | undefined): LibraryEntry | undefined {
  const { state } = useStore();
  return id ? state.library[id] : undefined;
}

export function useEntries(): LibraryEntry[] {
  const { state } = useStore();
  return useMemo(() => Object.values(state.library), [state.library]);
}
