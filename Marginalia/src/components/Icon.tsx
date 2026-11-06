import type { ReactNode } from 'react';

/** A small hand-drawn icon set (24×24, stroked). */
const PATHS: Record<string, ReactNode> = {
  bookmark: <path d="M7 3.5h10a1 1 0 0 1 1 1V21l-6-4.2L6 21V4.5a1 1 0 0 1 1-1z" />,
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="M15.5 15.5 21 21" />
    </>
  ),
  shelf: (
    <>
      <path d="M3 20.5h18" />
      <path d="M5 6.5h3v14H5zM9.5 4h3.2v16.5H9.5z" />
      <path d="m14.6 7.6 2.9-.8 3.3 13-2.9.8z" />
    </>
  ),
  chart: (
    <>
      <path d="M3.5 20.5h17" />
      <path d="M7 17v-5.5M12 17V7M17 17V9.5" />
    </>
  ),
  sliders: (
    <>
      <path d="M4 7h9M17 7h3M4 17h3M11 17h9" />
      <circle cx="15" cy="7" r="2.2" />
      <circle cx="9" cy="17" r="2.2" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  minus: <path d="M5 12h14" />,
  check: <path d="m5 12.5 4.2 4.2L19 7" />,
  x: <path d="M6 6l12 12M18 6 6 18" />,
  edit: (
    <>
      <path d="M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17v3z" />
      <path d="m14.5 7.5 3 3" />
    </>
  ),
  trash: (
    <>
      <path d="M4.5 7h15M10 7V4.5h4V7" />
      <path d="m6.5 7 1 13h9l1-13M10 11v5.5M14 11v5.5" />
    </>
  ),
  left: <path d="m14.5 5.5-6.5 6.5 6.5 6.5" />,
  right: <path d="m9.5 5.5 6.5 6.5-6.5 6.5" />,
  down: <path d="m5.5 9.5 6.5 6.5 6.5-6.5" />,
  up: <path d="m5.5 14.5 6.5-6.5 6.5 6.5" />,
  calendar: (
    <>
      <rect x="4" y="5.5" width="16" height="15" rx="1.5" />
      <path d="M4 10h16M8.5 3.5v4M15.5 3.5v4" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  download: <path d="M12 4v11m-4.5-4.5L12 15l4.5-4.5M5 19.5h14" />,
  upload: <path d="M12 15.5v-11M7.5 9 12 4.5 16.5 9M5 19.5h14" />,
  external: (
    <>
      <path d="M14 4.5h5.5V10M19.5 4.5 11 13" />
      <path d="M18 14v4.5a1 1 0 0 1-1 1H5.5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1H10" />
    </>
  ),
  heart: <path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z" />,
  grid: <path d="M4.5 4.5h6v6h-6zM13.5 4.5h6v6h-6zM4.5 13.5h6v6h-6zM13.5 13.5h6v6h-6z" />,
  list: <path d="M9 6.5h11M9 12h11M9 17.5h11M4.5 6.5h.01M4.5 12h.01M4.5 17.5h.01" />,
  spines: (
    <>
      <path d="M3.5 20.5h17" />
      <path d="M5 8h2.6v12.5H5zM9 5h2.6v15.5H9zM13 9.5h2.6v11H13zM17 6.5h2.6v14H17z" />
    </>
  ),
  filter: <path d="M4 5.5h16l-6.2 7.2v5.6l-3.6 1.8v-7.4z" />,
  flame: <path d="M12 3.5c.8 3.6 5.5 5.3 5.5 10a5.5 5.5 0 0 1-11 0c0-2.6 1.4-4.2 2.6-5.3.2 1.9 1.3 3 2.3 3.2-.4-2.9-.4-5.3.6-7.9z" />,
  target: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="4.8" />
      <circle cx="12" cy="12" r="1.2" />
    </>
  ),
  feather: (
    <>
      <path d="M19.5 4.5c-6.5 0-11.5 4-12.5 11.5" />
      <path d="M7 16c5 .5 9.5-2.5 12.5-11.5M4.5 20l3.5-4.5M9.5 12.5h5" />
    </>
  ),
  book: (
    <>
      <path d="M3.5 5.5c2.6-1.1 5.5-1.1 8.5.9 3-2 5.9-2 8.5-.9v13.3c-2.6-1-5.5-1-8.5 1-3-2-5.9-2-8.5-1z" />
      <path d="M12 6.4v13.4" />
    </>
  ),
  layers: (
    <>
      <path d="m12 4 8.5 4.5L12 13 3.5 8.5z" />
      <path d="m3.5 12.5 8.5 4.5 8.5-4.5M3.5 16.5 12 21l8.5-4.5" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4" />
    </>
  ),
  moon: <path d="M19.5 14.5A7.5 7.5 0 0 1 9.5 4.5a7.5 7.5 0 1 0 10 10z" />,
  table: <path d="M4 5h16v14H4zM4 10h16M4 14.5h16M10 5v14" />,
  refresh: (
    <>
      <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" />
      <path d="M19.5 4.5v4h-4" />
    </>
  ),
  sparkle: <path d="M12 3.5 13.8 10 20.5 12l-6.7 2-1.8 6.5-1.8-6.5L3.5 12l6.7-2z" />,
  save: (
    <>
      <path d="M5 4.5h11l3 3V19a.5.5 0 0 1-.5.5h-13A.5.5 0 0 1 5 19z" />
      <path d="M8 4.5v5h7v-5M8 19.5v-6h8v6" />
    </>
  ),
  image: (
    <>
      <rect x="4" y="5" width="16" height="14" rx="1.5" />
      <circle cx="9" cy="10" r="1.6" />
      <path d="m4.5 17.5 5-5 3.5 3.5 2.5-2.5 4 4" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8.5" r="3.8" />
      <path d="M4.5 20c.8-3.8 3.8-5.7 7.5-5.7s6.7 1.9 7.5 5.7" />
    </>
  ),
  stop: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M8.5 15.5l7-7" />
    </>
  ),
  play: <path d="M8 5.5v13l10-6.5z" />,
  info: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5.5M12 7.8h.01" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.5 12h17M12 3.5c2.4 2.4 3.5 5.2 3.5 8.5s-1.1 6.1-3.5 8.5c-2.4-2.4-3.5-5.2-3.5-8.5s1.1-6.1 3.5-8.5z" />
    </>
  ),
};

export const ICON_NAMES = Object.keys(PATHS);

export function Icon({ name, label, className = 'icon' }: { name: string; label?: string; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={label ? undefined : true}
      role={label ? 'img' : undefined}
      aria-label={label}
    >
      {PATHS[name] ?? PATHS.book}
    </svg>
  );
}
