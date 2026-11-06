import { useState, type FormEvent } from 'react';
import { useSettings, useStore, useEntries } from '../data/store';
import { Pages } from '../components/Shell';
import { Rule, Empty, Stars, Seg, Stamp } from '../components/ui';
import { Cover } from '../components/Cover';
import { Icon } from '../components/Icon';
import { ShelveButton } from '../components/books';
import { useAsync } from '../lib/useAsync';
import { search, remember, snapshotFrom } from '../api/catalog';
import { getTrending, SORT_LABELS, type SearchParams, type SearchResult, type SortKey } from '../api/openlibrary';
import { BROWSE_SUBJECTS } from '../lib/genres';
import { bookHref, hrefWith, navigate } from '../router';
import { formatNumber } from '../lib/util';
import { searchSample, SAMPLE_BOOKS, sampleResult } from '../data/sample';
import { AddManualButton } from './AddManual';

type Field = 'all' | 'title' | 'author' | 'isbn';

const LANGUAGES = [
  { value: '', label: 'Any language' },
  { value: 'eng', label: 'English' },
  { value: 'fre', label: 'French' },
  { value: 'ger', label: 'German' },
  { value: 'spa', label: 'Spanish' },
  { value: 'ita', label: 'Italian' },
  { value: 'por', label: 'Portuguese' },
  { value: 'dut', label: 'Dutch' },
  { value: 'jpn', label: 'Japanese' },
];

function paramsFrom(p: URLSearchParams, defaults: { language: string; limit: number }): SearchParams & { field: Field; text: string } {
  const field = (p.get('in') as Field) || 'all';
  const text = p.get('q') ?? '';
  const author = p.get('author') ?? (field === 'author' ? text : '');
  const title = field === 'title' ? text : '';
  return {
    field,
    text,
    q: field === 'all' ? text : undefined,
    title: title || undefined,
    author: author || undefined,
    isbn: field === 'isbn' ? text : undefined,
    subject: p.get('subject') ?? undefined,
    sort: (p.get('sort') as SortKey) || undefined,
    page: Number(p.get('page')) || 1,
    language: p.get('lang') ?? defaults.language,
    yearFrom: Number(p.get('from')) || undefined,
    yearTo: Number(p.get('to')) || undefined,
    limit: defaults.limit,
  };
}

export function BrowsePage({ params }: { params: URLSearchParams }) {
  const settings = useSettings();
  const { state } = useStore();
  const entries = useEntries();
  const sp = paramsFrom(params, { language: settings.searchLanguage, limit: settings.searchPageSize });
  const hasQuery = !!(sp.q || sp.title || sp.author || sp.isbn || sp.subject || sp.yearFrom || sp.yearTo);
  const online = settings.onlineLookup;

  const [text, setText] = useState(sp.text || sp.author || '');
  const [field, setField] = useState<Field>(sp.author && !sp.text ? 'author' : sp.field);
  const [showMore, setShowMore] = useState(!!(sp.yearFrom || sp.yearTo || params.get('lang')));
  const [yearFrom, setYearFrom] = useState(params.get('from') ?? '');
  const [yearTo, setYearTo] = useState(params.get('to') ?? '');
  const [lang, setLang] = useState(params.get('lang') ?? settings.searchLanguage);

  const key = hasQuery ? `${online}|${params.toString()}|${sp.limit}` : null;
  const results = useAsync(key, async () => {
    try {
      return await search(sp, online, entries);
    } catch (err) {
      // Fall back to local books so the page is still useful offline.
      const local = await search(sp, false, entries);
      return { ...local, fallback: (err as Error).message };
    }
  });
  const trending = useAsync(!hasQuery && online ? 'trending' : null, async () => {
    const r = await getTrending('weekly', 18);
    remember(r);
    return r;
  });

  const submit = (e?: FormEvent) => {
    e?.preventDefault();
    const q: Record<string, string | undefined> = {
      q: text.trim() || undefined,
      in: field !== 'all' ? field : undefined,
      sort: params.get('sort') ?? undefined,
      lang: lang && lang !== settings.searchLanguage ? lang : undefined,
      from: yearFrom || undefined,
      to: yearTo || undefined,
      subject: params.get('subject') ?? undefined,
    };
    navigate(hrefWith('#/browse', q));
  };

  const setParam = (k: string, v: string | undefined) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k !== 'page') next.delete('page');
    navigate(`#/browse${next.toString() ? `?${next.toString()}` : ''}`);
  };

  const data = results.data as (Awaited<ReturnType<typeof search>> & { fallback?: string }) | undefined;
  const library = state.library;
  const totalPages = data ? Math.ceil(data.total / (sp.limit ?? 24)) : 0;

  const searchPanel = (
    <>
      <Rule level="page">Browse</Rule>
      <p className="page-intro">{online ? 'Every book on Open Library, by title or by author.' : 'Online lookups are off — searching your library and the sample catalogue.'}</p>
      <form className="browse-form" onSubmit={submit} role="search">
        <div className="searchbox">
          <Icon name="search" />
          <input
            type="search"
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={field === 'author' ? 'Author name…' : field === 'title' ? 'Book title…' : field === 'isbn' ? 'ISBN-10 or ISBN-13…' : 'Title, author, subject or ISBN…'}
            aria-label="Search for books"
            enterKeyHint="search"
          />
          <button type="submit" className="btn btn--primary btn--sm">
            Search
          </button>
        </div>
        <div className="row row--between" style={{ marginTop: 10 }}>
          <Seg<Field>
            label="Search in"
            value={field}
            onChange={setField}
            size="sm"
            options={[
              { value: 'all', label: 'Everything' },
              { value: 'title', label: 'Title' },
              { value: 'author', label: 'Author' },
              { value: 'isbn', label: 'ISBN' },
            ]}
          />
          <button type="button" className="btn btn--ghost btn--sm" aria-expanded={showMore} onClick={() => setShowMore((s) => !s)}>
            <Icon name="filter" /> {showMore ? 'Fewer options' : 'More options'}
          </button>
        </div>
        {showMore && (
          <div className="browse-more">
            <label className="field">
              <span>First published from</span>
              <input className="input" type="number" inputMode="numeric" placeholder="e.g. 1900" value={yearFrom} onChange={(e) => setYearFrom(e.target.value)} />
            </label>
            <label className="field">
              <span>to</span>
              <input className="input" type="number" inputMode="numeric" placeholder="e.g. 1950" value={yearTo} onChange={(e) => setYearTo(e.target.value)} />
            </label>
            <label className="field">
              <span>Language</span>
              <select className="select" value={lang} onChange={(e) => setLang(e.target.value)}>
                {LANGUAGES.map((l) => (
                  <option key={l.value} value={l.value}>
                    {l.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
      </form>

      <Rule level="minor" as="h2">
        Browse by subject
      </Rule>
      <div className="chips">
        {BROWSE_SUBJECTS.map((s) => (
          <button
            key={s.subject}
            type="button"
            className="chip"
            aria-pressed={sp.subject === s.subject}
            onClick={() => navigate(hrefWith('#/browse', { subject: sp.subject === s.subject ? undefined : s.subject, sort: 'readinglog' }))}
          >
            {s.label}
          </button>
        ))}
      </div>

      <Rule level="minor" as="h2">
        Not finding it?
      </Rule>
      <p className="small muted ui" style={{ marginBottom: 10 }}>
        Add any book by hand: a proof copy, a zine, a family memoir.
      </p>
      <AddManualButton />
    </>
  );

  let resultsPanel;
  if (!hasQuery) {
    const shelf = online ? trending.data : SAMPLE_BOOKS.slice(0, 12).map(sampleResult);
    resultsPanel = (
      <>
        <Rule level="section" sub={online ? 'Popular on Open Library this week' : 'From the sample catalogue'}>
          {online ? 'Trending' : 'Sample catalogue'}
        </Rule>
        {online && trending.loading && !trending.data && <ResultSkeleton />}
        {online && trending.error && (
          <OfflineNotice message={trending.error.message}>
            <ResultGrid results={searchSample({}).slice(0, 12)} library={library} />
          </OfflineNotice>
        )}
        {shelf && <ResultGrid results={shelf} library={library} />}
      </>
    );
  } else {
    resultsPanel = (
      <>
        <Rule level="section" sub={describeQuery(sp)}>
          Results
        </Rule>
        <div className="row row--between results-bar">
          <span className="small muted ui" aria-live="polite">
            {results.loading && !data ? 'Searching…' : data ? `${formatNumber(data.total)} ${data.total === 1 ? 'book' : 'books'}${data.source === 'local' ? ' (offline)' : ''}` : ''}
          </span>
          {data?.source !== 'local' && (
            <label className="row small ui">
              <span className="muted">Sort</span>
              <select className="select" style={{ width: 'auto' }} value={sp.sort ?? 'relevance'} onChange={(e) => setParam('sort', e.target.value === 'relevance' ? undefined : e.target.value)}>
                {(Object.keys(SORT_LABELS) as SortKey[]).map((k) => (
                  <option key={k} value={k}>
                    {SORT_LABELS[k]}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
        {data?.fallback && <div className="notice notice--info" style={{ marginBottom: 14 }}>{data.fallback}. Showing matches from your library and the sample catalogue instead.</div>}
        {results.loading && !data && <ResultSkeleton />}
        {data && data.results.length === 0 && (
          <Empty icon="search" title="No books found" action={<AddManualButton />}>
            Try fewer words, check the spelling, or search by author only.
          </Empty>
        )}
        {data && data.results.length > 0 && (
          <div style={{ opacity: results.loading ? 0.55 : 1, transition: 'opacity .2s' }}>
            <ResultGrid results={data.results} library={library} onAuthor={(name) => navigate(hrefWith('#/browse', { q: name, in: 'author' }))} />
          </div>
        )}
        {data && data.source === 'openlibrary' && totalPages > 1 && (
          <nav className="pager" aria-label="Result pages">
            <button type="button" className="btn btn--sm" disabled={(sp.page ?? 1) <= 1} onClick={() => setParam('page', String((sp.page ?? 1) - 1))}>
              <Icon name="left" /> Previous
            </button>
            <span className="small muted ui nums">
              Page {sp.page ?? 1} of {formatNumber(Math.min(totalPages, 400))}
            </span>
            <button type="button" className="btn btn--sm" disabled={(sp.page ?? 1) >= totalPages} onClick={() => setParam('page', String((sp.page ?? 1) + 1))}>
              Next <Icon name="right" />
            </button>
          </nav>
        )}
      </>
    );
  }

  return <Pages title="Browse" tab="browse" verso={searchPanel} recto={resultsPanel} />;
}

function describeQuery(sp: SearchParams & { field: Field; text: string }): string {
  const parts: string[] = [];
  if (sp.q) parts.push(`“${sp.q}”`);
  if (sp.title) parts.push(`title “${sp.title}”`);
  if (sp.author) parts.push(`by ${sp.author}`);
  if (sp.isbn) parts.push(`ISBN ${sp.isbn}`);
  if (sp.subject) parts.push(`in ${BROWSE_SUBJECTS.find((s) => s.subject === sp.subject)?.label ?? sp.subject.replace(/_/g, ' ')}`);
  if (sp.yearFrom || sp.yearTo) parts.push(`published ${sp.yearFrom ?? '…'}–${sp.yearTo ?? '…'}`);
  return parts.join(' · ');
}

function OfflineNotice({ message, children }: { message: string; children?: React.ReactNode }) {
  return (
    <>
      <div className="notice notice--info" style={{ marginBottom: 16 }}>
        <Icon name="globe" />
        <span>
          {message}. Showing the sample catalogue instead — you can switch online lookups off in <a href="#/settings">Settings</a>.
        </span>
      </div>
      {children}
    </>
  );
}

function ResultSkeleton() {
  return (
    <div className="book-grid" aria-hidden>
      {Array.from({ length: 8 }, (_, i) => (
        <div key={i} className="stack stack--tight">
          <div className="skeleton" style={{ aspectRatio: '2 / 3' }} />
          <div className="skeleton" style={{ height: 14, width: '80%' }} />
          <div className="skeleton" style={{ height: 11, width: '55%' }} />
        </div>
      ))}
    </div>
  );
}

function ResultGrid({ results, library, onAuthor }: { results: SearchResult[]; library: Record<string, import('../lib/types').LibraryEntry>; onAuthor?: (name: string) => void }) {
  remember(results);
  return (
    <ul className="result-grid">
      {results.map((r) => (
        <ResultCard key={r.id} r={r} entry={library[r.id]} onAuthor={onAuthor} />
      ))}
    </ul>
  );
}

function ResultCard({ r, entry, onAuthor }: { r: SearchResult; entry?: import('../lib/types').LibraryEntry; onAuthor?: (name: string) => void }) {
  const href = bookHref(r.id) + '?from=browse';
  const snapshot = snapshotFrom(r);
  return (
    <li className="result">
      <a
        className="result__cover"
        href={href}
        onClick={(e) => {
          if (e.metaKey || e.ctrlKey || e.button !== 0) return;
          e.preventDefault();
          navigate(href);
        }}
        aria-label={`${r.title} — details`}
      >
        <Cover book={snapshot} size="fill" />
        {entry && (
          <span className="book-card__stamp">
            <Stamp status={entry.status} />
          </span>
        )}
      </a>
      <div className="result__body">
        <a
          className="book-card__title clamp-2"
          href={href}
          onClick={(e) => {
            if (e.metaKey || e.ctrlKey || e.button !== 0) return;
            e.preventDefault();
            navigate(href);
          }}
        >
          {r.title}
        </a>
        <span className="book-card__author">
          {r.authors.length
            ? r.authors.slice(0, 2).map((a, i) => (
                <span key={a.name + i}>
                  {i > 0 && ', '}
                  {onAuthor ? (
                    <button type="button" className="linkish" onClick={() => onAuthor(a.name)}>
                      {a.name}
                    </button>
                  ) : (
                    a.name
                  )}
                </span>
              ))
            : 'Unknown author'}
        </span>
        <span className="book-card__meta">
          {r.firstPublishYear && <span>{r.firstPublishYear}</span>}
          {r.pagesMedian ? <span>· {formatNumber(r.pagesMedian)} pp.</span> : null}
          {r.editionCount ? <span>· {formatNumber(r.editionCount)} {r.editionCount === 1 ? 'edition' : 'editions'}</span> : null}
        </span>
        {r.ratingAverage ? (
          <span className="book-card__meta">
            <Stars value={r.ratingAverage} size="0.78rem" /> <span className="nums">{r.ratingAverage.toFixed(1)}</span>
            {r.ratingCount ? <span className="faint">({formatNumber(r.ratingCount)})</span> : null}
          </span>
        ) : null}
        <div className="result__actions">
          <ShelveButton book={snapshot} entry={entry} />
        </div>
      </div>
    </li>
  );
}
