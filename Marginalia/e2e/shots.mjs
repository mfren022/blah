// Screenshot helper for visual review: node e2e/shots.mjs [baseUrl] [outDir] [routes...]
// Loads the sample library, blocks Open Library (no network needed), and captures each
// route at desktop and phone sizes.
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)('playwright');
import { mkdirSync } from 'node:fs';

const base = process.argv[2] ?? 'http://127.0.0.1:5173/';
const out = process.argv[3] ?? 'e2e/out';
const routes = process.argv.slice(4).length ? process.argv.slice(4) : ['reading', 'browse', 'library', 'stats', 'settings'];
const settings = JSON.parse(process.env.SETTINGS ?? '{}');
const sizes = (process.env.SIZES ?? 'desktop,phone').split(',');
mkdirSync(out, { recursive: true });

const VIEWPORTS = {
  desktop: { width: 1440, height: 900 },
  laptop: { width: 1180, height: 800 },
  tablet: { width: 900, height: 1100 },
  phone: { width: 390, height: 844 },
};

const browser = await chromium.launch();
for (const size of sizes) {
  const context = await browser.newContext({ viewport: VIEWPORTS[size], deviceScaleFactor: 1, colorScheme: process.env.SCHEME ?? 'light' });
  const page = await context.newPage();
  page.on('pageerror', (e) => console.log('PAGE ERROR', e.message));
  page.on('console', (m) => m.type() === 'error' && console.log('CONSOLE', m.text()));
  await page.route(/openlibrary\.org/, (r) => r.abort());
  await page.goto(base);
  const sample = page.getByRole('button', { name: /sample books/i });
  if (await sample.isVisible().catch(() => false)) await sample.click();
  if (Object.keys(settings).length) {
    await page.waitForTimeout(400);
    // The app saves on page hide, so apply the patch before its scripts run on reload.
    const patched = await page.evaluate((patch) => {
      const s = JSON.parse(localStorage.getItem('marginalia:v1'));
      s.settings = { ...s.settings, ...patch };
      return JSON.stringify(s);
    }, settings);
    await context.addInitScript((json) => localStorage.setItem('marginalia:v1', json), patched);
    await page.reload();
  }
  for (const r of routes) {
    await page.goto(base + '#/' + r);
    await page.waitForTimeout(300);
    // Let any page-turn transition finish before capturing.
    await page.waitForFunction(() => !document.getAnimations().some((a) => a.playState === 'running' && String(a.effect?.pseudoElement ?? '').startsWith('::view-transition')), null, { timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(250);
    await page.screenshot({ path: `${out}/${r.replace(/[^a-z0-9]+/gi, '_')}-${size}.png` });
  }
  await context.close();
}
await browser.close();
console.log('done');
