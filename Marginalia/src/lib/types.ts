/** Calendar date in local time, `YYYY-MM-DD`. */
export type ISODate = string;
/** Wall-clock local date-time, `YYYY-MM-DDTHH:mm`. */
export type LocalDateTime = string;

export type Status = 'want' | 'reading' | 'finished' | 'dnf';
export const STATUSES: Status[] = ['want', 'reading', 'finished', 'dnf'];

export type BookFormat = 'paperback' | 'hardcover' | 'ebook' | 'audiobook' | 'other';
export const FORMATS: BookFormat[] = ['paperback', 'hardcover', 'ebook', 'audiobook', 'other'];

export type BookSource = 'openlibrary' | 'sample' | 'manual';

export interface AuthorRef {
  name: string;
  /** Open Library author key, e.g. `OL23919A`. */
  key?: string;
}

/** Metadata about a work, cached in the library so it works offline. */
export interface BookSnapshot {
  /** `ol:OL45804W`, `sample:<slug>` or `manual:<id>`. */
  id: string;
  source: BookSource;
  /** Open Library work key, e.g. `OL45804W`. */
  workKey?: string;
  title: string;
  subtitle?: string;
  authors: AuthorRef[];
  coverId?: number;
  coverUrl?: string;
  firstPublishYear?: number;
  subjects?: string[];
  pagesMedian?: number;
  editionCount?: number;
}

export interface EditionInfo {
  key: string;
  title?: string;
  publisher?: string;
  publishDate?: string;
  year?: number;
  pages?: number;
  /** Raw physical format, e.g. "Paperback", "Hardcover", "Audio CD". */
  format?: string;
  isbn13?: string;
  isbn10?: string;
  language?: string;
  coverId?: number;
}

export interface ProgressLog {
  id: string;
  at: LocalDateTime;
  page: number;
  note?: string;
}

export type SessionOutcome = 'reading' | 'finished' | 'dnf';

/** One read-through of a book: start → finish / stop. */
export interface ReadSession {
  id: string;
  startedAt?: ISODate;
  /** Date finished (outcome `finished`) or stopped (outcome `dnf`). */
  endedAt?: ISODate;
  outcome: SessionOutcome;
  progress: ProgressLog[];
}

export interface LibraryEntry {
  /** Same as `book.id`. */
  id: string;
  book: BookSnapshot;
  status: Status;
  /** Date first added to the library. */
  addedAt: ISODate;
  /** Date most recently put on Want to Read. */
  queuedAt?: ISODate;
  shelves: string[];
  edition?: EditionInfo;
  /** Page count used for progress (edition pages, or a manual override). */
  pageCount?: number;
  format?: BookFormat;
  /** Manual genre override (otherwise derived from subjects). */
  genre?: string;
  sessions: ReadSession[];
  rating?: number;
  review?: string;
  reviewSpoilers?: boolean;
  reviewedAt?: ISODate;
  notes?: string;
  favorite?: boolean;
  /** True for entries created by the sample library. */
  sample?: boolean;
  updatedAt: LocalDateTime;
}

export interface Shelf {
  id: string;
  name: string;
  color: string;
  createdAt: ISODate;
}

/* ---------------- Charts ---------------- */

export type Metric =
  | 'finished'
  | 'pages'
  | 'started'
  | 'added'
  | 'dnf'
  | 'books'
  | 'rating'
  | 'days'
  | 'pace';

export type TimeGroup = 'day' | 'week' | 'month' | 'quarter' | 'year';
export type CategoryGroup =
  | 'genre'
  | 'author'
  | 'rating'
  | 'status'
  | 'format'
  | 'length'
  | 'decade'
  | 'shelf'
  | 'weekday';
export type GroupBy = TimeGroup | CategoryGroup;
export type SplitBy = 'none' | 'status' | 'format' | 'genre' | 'shelf' | 'length';
export type ChartType = 'column' | 'bar' | 'line' | 'area';
export type RangePreset = 'all' | 'ytd' | 'last12' | 'last90' | 'last30' | 'lastYear' | 'custom';

export interface ChartFilters {
  range: RangePreset;
  from?: ISODate;
  to?: ISODate;
  statuses: Status[];
  shelves: string[];
  genres: string[];
  formats: BookFormat[];
  authors: string[];
  minRating?: number;
  minPages?: number;
  maxPages?: number;
}

export interface ChartConfig {
  id: string;
  name: string;
  metric: Metric;
  groupBy: GroupBy;
  chartType: ChartType;
  splitBy: SplitBy;
  cumulative: boolean;
  showValues: boolean;
  showAverage: boolean;
  showGoal: boolean;
  topN: number;
  filters: ChartFilters;
}
