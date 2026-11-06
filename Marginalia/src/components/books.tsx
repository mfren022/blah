import type { BookSnapshot, EditionInfo, LibraryEntry, Status } from '../lib/types';
import { STATUSES } from '../lib/types';
import { useStore } from '../data/store';
import { useToast } from './Toasts';
import { bookHref, navigate } from '../router';
import { Cover } from './Cover';
import { Stamp, Stars, Progress } from './ui';
import { Icon } from './Icon';
import { Menu } from './Menu';
import { currentPage } from '../data/reducer';
import { STATUS_LABELS } from '../stats/engine';
import { formatDate, today } from '../lib/dates';
import { formatNumber } from '../lib/util';

export const STATUS_ICONS: Record<Status, string> = { want: 'bookmark', reading: 'book', finished: 'check', dnf: 'stop' };

export const SHORT_STATUS: Record<Status, string> = { want: 'Want to read', reading: 'Reading', finished: 'Finished', dnf: 'Did not finish' };

export const authorNames = (b: Pick<BookSnapshot, 'authors'>) => b.authors.map((a) => a.name).join(', ') || 'Unknown author';

/** Library actions with confirmation toasts and Undo. */
export function useBookActions() {
  const { state, dispatch } = useStore();
  const toast = useToast();
  const undoable = (message: string, run: () => void) => {
    const before = state;
    run();
    toast({ message, action: { label: 'Undo', run: () => dispatch({ type: 'hydrate', state: before }) } });
  };
  return {
    add(book: BookSnapshot, status: Status, extra: { edition?: EditionInfo; pageCount?: number } = {}) {
      const existed = !!state.library[book.id];
      undoable(existed ? `Moved to ${STATUS_LABELS[status]}` : `Added to ${STATUS_LABELS[status]}`, () =>
        dispatch({ type: 'addBook', book, status, date: today(), ...extra }),
      );
    },
    setStatus(id: string, status: Status, date?: string) {
      undoable(`Moved to ${STATUS_LABELS[status]}`, () => dispatch({ type: 'setStatus', id, status, date }));
    },
    remove(id: string) {
      const title = state.library[id]?.book.title ?? 'Book';
      undoable(`Removed “${title}” from your library`, () => dispatch({ type: 'removeBook', id }));
    },
  };
}

/** Button + menu that shelves a book (adding it to the library if needed). */
export function ShelveButton({ book, entry, size = 'sm', primary = true }: { book: BookSnapshot; entry?: LibraryEntry; size?: 'sm' | 'md'; primary?: boolean }) {
  const actions = useBookActions();
  const current = entry?.status;
  return (
    <div className="btn-group">
      {!entry ? (
        <button type="button" className={`btn ${primary ? 'btn--primary' : ''} ${size === 'sm' ? 'btn--sm' : ''}`} onClick={() => actions.add(book, 'want')}>
          <Icon name="bookmark" /> Want to Read
        </button>
      ) : (
        <a className={`btn ${size === 'sm' ? 'btn--sm' : ''}`} href={bookHref(book.id)}>
          <Icon name={STATUS_ICONS[entry.status]} /> <span className="ellipsis">{SHORT_STATUS[entry.status]}</span>
        </a>
      )}
      <Menu
        label="Choose a shelf"
        align="right"
        trigger={({ open, toggle, id }) => (
          <button
            type="button"
            className={`btn btn--icon ${!entry && primary ? 'btn--primary' : ''} ${size === 'sm' ? 'btn--sm' : ''}`}
            aria-haspopup="menu"
            aria-expanded={open}
            aria-controls={id}
            aria-label="More shelves"
            onClick={toggle}
          >
            <Icon name="down" />
          </button>
        )}
      >
        {(close) => (
          <>
            {STATUSES.map((s) => (
              <button
                key={s}
                type="button"
                role="menuitemradio"
                aria-checked={current === s}
                className="menu__item"
                onClick={() => {
                  close();
                  if (!entry) actions.add(book, s);
                  else if (entry.status !== s) actions.setStatus(entry.id, s);
                }}
              >
                <Icon name={STATUS_ICONS[s]} /> {STATUS_LABELS[s]}
                {current === s && <Icon name="check" className="icon menu__check" />}
              </button>
            ))}
            {entry && (
              <>
                <hr className="menu__sep" />
                <button type="button" role="menuitem" className="menu__item" onClick={() => (close(), navigate(bookHref(book.id)))}>
                  <Icon name="edit" /> Dates, edition & review…
                </button>
              </>
            )}
          </>
        )}
      </Menu>
    </div>
  );
}

export function entryDateLine(e: LibraryEntry, fmt: Parameters<typeof formatDate>[1]): string {
  const s = e.sessions.at(-1);
  switch (e.status) {
    case 'want':
      return `Added ${formatDate(e.queuedAt ?? e.addedAt, fmt)}`;
    case 'reading':
      return `Started ${formatDate(s?.startedAt, fmt)}`;
    case 'finished':
      return `Finished ${formatDate(s?.endedAt, fmt)}`;
    case 'dnf':
      return `Stopped ${formatDate(s?.endedAt, fmt)}`;
  }
}

export function BookCard({ entry, book, meta, showStamp = true, from }: { entry?: LibraryEntry; book: BookSnapshot; meta?: React.ReactNode; showStamp?: boolean; from?: string }) {
  const href = bookHref(book.id) + (from ? `?from=${from}` : '');
  return (
    <a
      className="book-card"
      href={href}
      onClick={(e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
        e.preventDefault();
        navigate(href);
      }}
    >
      <Cover book={book} size="fill" />
      {entry && showStamp && (
        <span className="book-card__stamp">
          <Stamp status={entry.status} />
        </span>
      )}
      <span className="book-card__title clamp-2">{book.title}</span>
      <span className="book-card__author ellipsis">{authorNames(book)}</span>
      {meta && <span className="book-card__meta">{meta}</span>}
      {entry?.status === 'reading' && entry.pageCount ? <Progress thin value={currentPage(entry)} max={entry.pageCount} label={`${book.title} progress`} /> : null}
    </a>
  );
}

export function BookRow({ entry, fmt, from }: { entry: LibraryEntry; fmt: Parameters<typeof formatDate>[1]; from?: string }) {
  const href = bookHref(entry.id) + (from ? `?from=${from}` : '');
  const s = entry.sessions.at(-1);
  return (
    <a
      className="book-row"
      href={href}
      onClick={(e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
        e.preventDefault();
        navigate(href);
      }}
    >
      <Cover book={entry.book} size="xs" />
      <span className="stack stack--tight" style={{ gap: 2, minWidth: 0 }}>
        <span className="book-card__title ellipsis">{entry.book.title}</span>
        <span className="book-card__author ellipsis">
          {authorNames(entry.book)}
          {entry.book.firstPublishYear ? ` · ${entry.book.firstPublishYear}` : ''}
        </span>
        <span className="book-card__meta">
          <Stamp status={entry.status} flat />
          <span>{entryDateLine(entry, fmt)}</span>
          {entry.status === 'finished' && s?.startedAt && s.endedAt ? <span>· {formatDate(s.startedAt, fmt)} → {formatDate(s.endedAt, fmt)}</span> : null}
          {entry.pageCount ? <span>· {formatNumber(entry.pageCount)} pp.</span> : null}
        </span>
      </span>
      <span className="stack stack--tight" style={{ alignItems: 'flex-end', gap: 4 }}>
        {entry.rating ? <Stars value={entry.rating} size="0.85rem" /> : null}
        {entry.status === 'reading' && entry.pageCount ? (
          <span className="tiny muted nums">{Math.round((currentPage(entry) / entry.pageCount) * 100)}%</span>
        ) : null}
      </span>
    </a>
  );
}
