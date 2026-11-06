import { useState } from 'react';
import type { BookFormat, Status } from '../lib/types';
import { FORMATS, STATUSES } from '../lib/types';
import { useSettings, useStore } from '../data/store';
import { Dialog } from '../components/ui';
import { Icon } from '../components/Icon';
import { useToast } from '../components/Toasts';
import { GENRE_NAMES } from '../lib/genres';
import { today } from '../lib/dates';
import { uid } from '../lib/util';
import { STATUS_LABELS, FORMAT_LABELS } from '../stats/engine';
import { bookHref, navigate } from '../router';

/** "Add a book by hand" for anything Open Library doesn't have (or when offline). */
export function AddManualButton({ label = 'Add a book by hand' }: { label?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" className="btn btn--sm" onClick={() => setOpen(true)}>
        <Icon name="plus" /> {label}
      </button>
      <Dialog open={open} onClose={() => setOpen(false)} title="Add a book by hand">
        {open && <ManualForm onDone={() => setOpen(false)} />}
      </Dialog>
    </>
  );
}

function ManualForm({ onDone }: { onDone: () => void }) {
  const { dispatch } = useStore();
  const settings = useSettings();
  const toast = useToast();
  const [title, setTitle] = useState('');
  const [author, setAuthor] = useState('');
  const [pages, setPages] = useState('');
  const [year, setYear] = useState('');
  const [genre, setGenre] = useState('');
  const [format, setFormat] = useState<BookFormat>(settings.defaultFormat);
  const [status, setStatus] = useState<Status>('want');
  const [date, setDate] = useState(today());
  const [coverUrl, setCoverUrl] = useState('');

  const valid = title.trim().length > 0;
  return (
    <form
      className="stack"
      onSubmit={(e) => {
        e.preventDefault();
        if (!valid) return;
        const id = `manual:${uid()}`;
        dispatch({
          type: 'addBook',
          book: {
            id,
            source: 'manual',
            title: title.trim(),
            authors: author
              .split(/\s*[,;&]\s*|\s+and\s+/)
              .map((a) => a.trim())
              .filter(Boolean)
              .map((name) => ({ name })),
            firstPublishYear: Number(year) || undefined,
            subjects: genre ? [genre] : undefined,
            coverUrl: /^https?:\/\//.test(coverUrl.trim()) ? coverUrl.trim() : undefined,
            pagesMedian: Number(pages) || undefined,
          },
          status,
          date,
          pageCount: Number(pages) || undefined,
          format,
        });
        if (genre) dispatch({ type: 'updateEntry', id, patch: { genre } });
        toast({ message: `Added “${title.trim()}” to ${STATUS_LABELS[status]}` });
        onDone();
        navigate(bookHref(id));
      }}
    >
      <label className="field">
        <span>Title *</span>
        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
      </label>
      <label className="field">
        <span>Author(s)</span>
        <input className="input" value={author} onChange={(e) => setAuthor(e.target.value)} placeholder="Separate several with commas" />
      </label>
      <div className="form-grid">
        <label className="field">
          <span>Pages</span>
          <input className="input" type="number" min={1} inputMode="numeric" value={pages} onChange={(e) => setPages(e.target.value)} />
        </label>
        <label className="field">
          <span>First published</span>
          <input className="input" type="number" inputMode="numeric" value={year} onChange={(e) => setYear(e.target.value)} placeholder="Year" />
        </label>
        <label className="field">
          <span>Genre</span>
          <select className="select" value={genre} onChange={(e) => setGenre(e.target.value)}>
            <option value="">—</option>
            {GENRE_NAMES.map((g) => (
              <option key={g}>{g}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Format</span>
          <select className="select" value={format} onChange={(e) => setFormat(e.target.value as BookFormat)}>
            {FORMATS.map((f) => (
              <option key={f} value={f}>
                {FORMAT_LABELS[f]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Shelf</span>
          <select className="select" value={status} onChange={(e) => setStatus(e.target.value as Status)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>{status === 'want' ? 'Added on' : status === 'reading' ? 'Started on' : status === 'finished' ? 'Finished on' : 'Stopped on'}</span>
          <input className="input" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value || today())} />
        </label>
      </div>
      <label className="field">
        <span>Cover image URL (optional)</span>
        <input className="input" type="url" value={coverUrl} onChange={(e) => setCoverUrl(e.target.value)} placeholder="https://…" />
      </label>
      <div className="row row--end">
        <button type="button" className="btn btn--ghost" onClick={onDone}>
          Cancel
        </button>
        <button type="submit" className="btn btn--primary" disabled={!valid}>
          <Icon name="plus" /> Add book
        </button>
      </div>
    </form>
  );
}
