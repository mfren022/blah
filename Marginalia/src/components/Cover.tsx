import { useState, type CSSProperties } from 'react';
import type { BookSnapshot } from '../lib/types';
import { coverUrl } from '../api/openlibrary';
import { clothFor } from '../lib/util';
import { useSettings } from '../data/store';

type Size = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | 'fill';

/**
 * A book cover. A cloth binding with the title stamped on it is always drawn first; the
 * Open Library cover image (if any) loads on top, and falls back to the binding on error.
 */
export function Cover({ book, size = 'md', label, coverId }: { book: Pick<BookSnapshot, 'title' | 'authors' | 'coverId' | 'coverUrl'>; size?: Size; label?: boolean; coverId?: number }) {
  const settings = useSettings();
  const [failed, setFailed] = useState(false);
  const id = coverId ?? book.coverId;
  const src = settings.coverSource === 'online' && !failed ? book.coverUrl ?? coverUrl(id, size === 'lg' || size === 'xl' ? 'L' : 'M') : undefined;
  const author = book.authors.map((a) => a.name).join(', ');
  const style = { '--gen-cloth': clothFor(book.title + (book.authors[0]?.name ?? '')) } as CSSProperties;
  return (
    <div className={`cover cover--${size}`} style={style} role={label ? 'img' : undefined} aria-label={label ? `Cover of ${book.title}` : undefined} aria-hidden={label ? undefined : true}>
      <div className="cover__gen">
        <div className="cover__title">{book.title}</div>
        <div className="cover__orn" />
        <div className="cover__author">{author}</div>
      </div>
      {src && (
        <img
          src={src}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          onLoad={(e) => {
            // Open Library answers unknown covers with a 1×1 placeholder.
            if (e.currentTarget.naturalWidth < 8) setFailed(true);
          }}
        />
      )}
    </div>
  );
}
