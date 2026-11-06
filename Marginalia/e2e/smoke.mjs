// End-to-end smoke test: node e2e/smoke.mjs [baseUrl]
//
// Drives the built app in Chromium through the main journey: browse → book page → shelve →
// choose an edition → read and log pages → finish → library → statistics → settings → review.
// Open Library is replaced by fixtures that follow its documented JSON shapes, so the test
// needs no network. Requires Playwright (`npm i -D playwright && npx playwright install chromium`).
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { mkdirSync } from 'node:fs';

const require = createRequire(import.meta.url);
let chromium;
try {
  ({ chromium } = require('playwright'));
} catch {
  console.error('Playwright is not installed. Run: npm i -D playwright && npx playwright install chromium');
  process.exit(2);
}

/* ------------------------------------------------------------ fixtures */

const docs = [
  {
    key: '/works/OL1001W', title: 'The Glass Orchard', author_name: ['Mira Holt'], author_key: ['OL501A'], first_publish_year: 2004,
    cover_i: 9999, edition_count: 3, number_of_pages_median: 352, subject: ['Fiction', 'Gardens', 'Mystery', 'Accessible book'],
    ratings_average: 4.1, ratings_count: 120, want_to_read_count: 300, currently_reading_count: 12, already_read_count: 210,
  },
  { key: '/works/OL1002W', title: 'Orchard Hours', author_name: ['Ben Ward'], author_key: ['OL502A'], first_publish_year: 1999, edition_count: 1 },
];
const fixtures = {
  '/search.json': { numFound: 2, start: 0, docs },
  '/trending/weekly.json': { works: docs },
  '/works/OL1001W.json': {
    key: '/works/OL1001W', title: 'The Glass Orchard',
    description: { type: '/type/text', value: 'A gardener finds a greenhouse that grows [memories](https://example.org).\r\n\r\nNothing in it will stay buried.\n\n----------\nAlso contained in:\n[An omnibus](https://example.org)' },
    authors: [{ author: { key: '/authors/OL501A' }, type: { key: '/type/author_role' } }],
    subjects: ['Fiction', 'Gardens', 'Mystery'], subject_places: ['Hollow Lane'], covers: [9999], first_publish_date: '2004',
  },
  '/works/OL1001W/editions.json': {
    size: 3,
    entries: [
      { key: '/books/OL1M', publishers: ['North House'], publish_date: '2004', number_of_pages: 352, physical_format: 'Hardcover', isbn_13: ['9780000000019'], languages: [{ key: '/languages/eng' }] },
      { key: '/books/OL2M', publishers: ['North House'], publish_date: 'May 2006', pagination: 'xii, 368 p.', physical_format: 'Paperback', languages: [{ key: '/languages/eng' }] },
      { key: '/books/OL3M', publishers: ['Tern Audio'], publish_date: '2010', physical_format: 'Audio CD' },
    ],
  },
  '/works/OL1001W/ratings.json': { summary: { average: 4.1, count: 120 }, counts: { 1: 2, 2: 6, 3: 20, 4: 42, 5: 50 } },
  '/works/OL1001W/bookshelves.json': { counts: { want_to_read: 300, currently_reading: 12, already_read: 210 } },
  '/authors/OL501A.json': { key: '/authors/OL501A', name: 'Mira Holt', bio: 'Mira Holt writes about gardens and the people who keep them.', birth_date: '1970' },
};

/* -------------------------------------------------------------- server */

let server;
let base = process.argv[2];
if (!base) {
  base = 'http://127.0.0.1:4179/';
  server = spawn('npx', ['vite', 'preview', '--port', '4179', '--strictPort', '--host', '127.0.0.1'], { stdio: 'ignore' });
  for (let i = 0; i < 60; i++) {
    try {
      if ((await fetch(base)).ok) break;
    } catch {
      /* not up yet */
    }
    await new Promise((r) => setTimeout(r, 250));
  }
}

/* ---------------------------------------------------------------- run */

const out = 'e2e/out';
mkdirSync(out, { recursive: true });
const results = [];
const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
let olOnline = true;
await context.route(/openlibrary\.org/, async (route) => {
  const url = new URL(route.request().url());
  if (url.hostname === 'covers.openlibrary.org' || !olOnline) return route.abort();
  const body = fixtures[url.pathname];
  if (!body) return route.fulfill({ status: 404, body: '{}' });
  return route.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify(body) });
});

async function step(name, fn) {
  try {
    await fn();
    results.push(['PASS', name]);
  } catch (err) {
    results.push(['FAIL', name, err.message.split('\n')[0]]);
    await page.screenshot({ path: `${out}/fail-${results.length}.png` }).catch(() => {});
  }
}
const expectText = async (text, opts = {}) => page.getByText(text, opts).first().waitFor({ timeout: 6000 });
const settle = () => page.waitForTimeout(900);

await page.goto(base);

await step('first run shows the welcome sheet', async () => {
  await expectText('Welcome to Marginalia');
  await page.getByRole('button', { name: 'Start my own library' }).click();
});

await step('search Open Library by title and see title + author', async () => {
  await page.goto(base + '#/browse');
  await settle();
  await page.getByRole('button', { name: 'Title', exact: true }).click();
  await page.getByLabel('Search for books').fill('glass orchard');
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  await expectText('The Glass Orchard');
  await expectText('Mira Holt');
  await expectText('2 books');
});

await step('shelve a result as Want to Read', async () => {
  await page.locator('.result').first().getByRole('button', { name: 'Want to Read' }).click();
  await expectText('Added to Want to Read');
});

await step('book page shows synopsis, editions with page counts, ratings and author', async () => {
  await page.locator('.result').first().getByRole('link', { name: 'The Glass Orchard', exact: true }).click();
  await expectText('A gardener finds a greenhouse that grows memories.');
  if (await page.getByText('Also contained in').count()) throw new Error('description link list was not cleaned');
  await page.locator('table.editions td', { hasText: '368' }).first().waitFor({ timeout: 6000 }); // from "xii, 368 p."
  await page.locator('table.editions td', { hasText: '352' }).first().waitFor({ timeout: 6000 });
  await expectText('4.10');
  await expectText('Mira Holt writes about gardens');
  await expectText('Hollow Lane');
});

await step('choose an edition and its page count follows', async () => {
  await page.locator('tr', { hasText: 'Paperback' }).getByRole('button', { name: 'Use this' }).click();
  const pages = await page.getByLabel('Pages in your copy').inputValue();
  if (pages !== '368') throw new Error(`page count is ${pages}`);
});

await step('start reading and log a page on the Reading page', async () => {
  await page.getByRole('radio', { name: 'Currently Reading' }).click();
  await page.goto(base + '#/reading');
  await settle();
  const input = page.getByLabel('Current page').first();
  await input.fill('120');
  await page.getByRole('button', { name: 'Log' }).first().click();
  await expectText('33%');
  await expectText('248 pages left');
});

await step('finish the book; it moves to the Finished shelf', async () => {
  await page.getByRole('button', { name: 'Finished' }).first().click();
  await expectText('Moved to Finished');
  await page.goto(base + '#/library?status=finished');
  await settle();
  await page.locator('.book-card', { hasText: 'The Glass Orchard' }).waitFor();
});

await step('statistics count the finished book and pages', async () => {
  await page.goto(base + '#/stats');
  await settle();
  const tile = page.locator('.tile', { hasText: 'Books finished in' });
  const value = (await tile.locator('.tile__value').innerText()).trim();
  if (value !== '1') throw new Error(`books finished tile shows ${value}`);
  await page.locator('.chart__plot > svg').waitFor();
});

await step('the chart builder regroups and offers a table view', async () => {
  await page.getByLabel('Group by').selectOption('genre');
  await page.getByRole('button', { name: 'Table' }).click();
  await page.locator('.chart__table', { hasText: 'Mystery' }).waitFor();
});

await step('rate and review the book, and it persists after reload', async () => {
  await page.goto(base + '#/book/' + encodeURIComponent('ol:OL1001W'));
  await settle();
  const slider = page.getByRole('slider', { name: 'Your rating' });
  await slider.focus();
  for (let i = 0; i < 9; i++) await page.keyboard.press('ArrowRight');
  await page.getByRole('button', { name: 'Write a review' }).click();
  await page.getByLabel('Your review').fill('Strange and lovely.');
  await page.getByRole('button', { name: 'Save review' }).click();
  await page.waitForTimeout(500);
  await page.reload();
  await expectText('Strange and lovely.');
  const now = await page.getByRole('slider', { name: 'Your rating' }).getAttribute('aria-valuenow');
  if (now !== '4.5') throw new Error(`rating is ${now}`);
});

await step('settings: theme and dark mode apply to the page', async () => {
  await page.goto(base + '#/settings');
  await settle();
  await page.getByRole('radio', { name: /Bottle Green/ }).click();
  const cover = await page.evaluate(() => document.documentElement.style.getPropertyValue('--cover'));
  if (cover.trim() !== '#24433a') throw new Error(`cover colour is ${cover}`);
  await page.getByRole('button', { name: 'Dark' }).click();
  const theme = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  if (theme !== 'dark') throw new Error(`data-theme is ${theme}`);
});

await step('export a JSON backup containing the book', async () => {
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Export backup (.json)' }).click()]);
  const path = await download.path();
  const json = JSON.parse((await import('node:fs')).readFileSync(path, 'utf8'));
  if (!json.state.library['ol:OL1001W']) throw new Error('book missing from backup');
});

await step('offline: search falls back to local books with a notice', async () => {
  olOnline = false;
  await page.goto(base + '#/browse?q=lantern');
  await settle();
  await expectText('Showing matches from your library and the sample catalogue instead');
  await expectText('The Lantern Cartographer');
  olOnline = true;
});

await step('no uncaught errors in the page', async () => {
  if (errors.length) throw new Error(errors.join(' | '));
});

await page.screenshot({ path: `${out}/final.png` });
await browser.close();
server?.kill();

for (const [status, name, why] of results) console.log(`${status === 'PASS' ? '✓' : '✗'} ${name}${why ? ` — ${why}` : ''}`);
const failed = results.filter((r) => r[0] === 'FAIL').length;
console.log(`\n${results.length - failed}/${results.length} steps passed`);
process.exit(failed ? 1 : 0);
