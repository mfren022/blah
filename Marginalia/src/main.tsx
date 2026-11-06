import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './fonts';
import './styles/base.css';
import './styles/book.css';
import './styles/components.css';
import './styles/pages.css';
import { StoreProvider } from './data/store';
import { App } from './App';
import { initialState, STORAGE_KEY, type AppState } from './data/model';
import { reducer } from './data/reducer';
import { buildSampleLibrary, SAMPLE_SHELVES } from './data/sample';

/** The single-file preview opens on the (clearly labelled) sample library rather than an empty book. */
function previewState(): AppState | undefined {
  if (import.meta.env.MODE !== 'single') return undefined;
  try {
    if (localStorage.getItem(STORAGE_KEY)) return undefined;
  } catch {
    /* storage unavailable: fall through to the sample */
  }
  return reducer(initialState(), { type: 'loadSample', entries: buildSampleLibrary(), shelves: SAMPLE_SHELVES });
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <StoreProvider initial={previewState()}>
      <App />
    </StoreProvider>
  </StrictMode>,
);

// Offline support for the installed app (not in dev or the single-file build).
if (import.meta.env.PROD && import.meta.env.MODE !== 'single' && 'serviceWorker' in navigator && location.protocol.startsWith('http')) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch(() => {
      /* offline support is optional */
    });
  });
}
