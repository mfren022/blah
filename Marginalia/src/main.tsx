import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './fonts';
import './styles/base.css';
import './styles/book.css';
import './styles/components.css';
import './styles/pages.css';
import { StoreProvider } from './data/store';
import { App } from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <StoreProvider>
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
