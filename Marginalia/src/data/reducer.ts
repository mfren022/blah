import type {
  BookFormat,
  BookSnapshot,
  ChartConfig,
  EditionInfo,
  ISODate,
  LibraryEntry,
  LocalDateTime,
  ReadSession,
  SessionOutcome,
  Shelf,
  Status,
} from '../lib/types';
import { dateOf, nowLocal, today } from '../lib/dates';
import { clamp, uid } from '../lib/util';
import { initialState, sanitizeState, type AppState } from './model';
import { DEFAULT_SETTINGS, sanitizeSettings, type Settings } from './settings';

export type EntryPatch = Partial<
  Pick<
    LibraryEntry,
    'pageCount' | 'format' | 'genre' | 'rating' | 'review' | 'reviewSpoilers' | 'notes' | 'favorite' | 'addedAt' | 'queuedAt' | 'shelves'
  >
>;

export type Action =
  | {
      type: 'addBook';
      book: BookSnapshot;
      status: Status;
      date?: ISODate;
      edition?: EditionInfo;
      pageCount?: number;
      format?: BookFormat;
      shelves?: string[];
    }
  | { type: 'setStatus'; id: string; status: Status; date?: ISODate }
  | { type: 'logProgress'; id: string; page: number; at?: LocalDateTime; note?: string }
  | { type: 'deleteProgress'; id: string; sessionId: string; logId: string }
  | { type: 'updateSession'; id: string; sessionId: string; patch: { startedAt?: ISODate | null; endedAt?: ISODate | null; outcome?: SessionOutcome } }
  | { type: 'addSession'; id: string; startedAt?: ISODate; endedAt?: ISODate; outcome: 'finished' | 'dnf' }
  | { type: 'deleteSession'; id: string; sessionId: string }
  | { type: 'updateEntry'; id: string; patch: EntryPatch }
  | { type: 'setEdition'; id: string; edition: EditionInfo | undefined }
  | { type: 'updateBook'; id: string; book: Partial<BookSnapshot> }
  | { type: 'removeBook'; id: string }
  | { type: 'toggleShelf'; id: string; shelfId: string }
  | { type: 'createShelf'; shelf: Shelf }
  | { type: 'updateShelf'; shelfId: string; patch: Partial<Pick<Shelf, 'name' | 'color'>> }
  | { type: 'deleteShelf'; shelfId: string }
  | { type: 'updateSettings'; patch: Partial<Settings> }
  | { type: 'resetSettings' }
  | { type: 'setChart'; chart: ChartConfig }
  | { type: 'saveChartPreset'; chart: ChartConfig }
  | { type: 'deleteChartPreset'; presetId: string }
  | { type: 'importState'; state: unknown; mode: 'merge' | 'replace' }
  | { type: 'loadSample'; entries: LibraryEntry[]; shelves: Shelf[] }
  | { type: 'clearSample' }
  | { type: 'resetAll' }
  | { type: 'hydrate'; state: AppState }
  | { type: 'setOnboarded' };

/* ------------------------------------------------------------------ */
/* Entry-level helpers (pure, exported for tests)                      */
/* ------------------------------------------------------------------ */

/** A timestamp for an event on `date`: now if it is today, otherwise midday. */
export function stampFor(date: ISODate): LocalDateTime {
  return date === today() ? nowLocal() : `${date}T12:00`;
}

export function activeSession(entry: LibraryEntry): ReadSession | undefined {
  const last = entry.sessions.at(-1);
  return last?.outcome === 'reading' ? last : undefined;
}

export function currentPage(entry: LibraryEntry): number {
  const s = activeSession(entry) ?? entry.sessions.at(-1);
  return s?.progress.at(-1)?.page ?? 0;
}

const sessionKey = (s: ReadSession) => s.startedAt ?? s.endedAt ?? '0000-00-00';

function sortSessions(sessions: ReadSession[]): ReadSession[] {
  const active = sessions.filter((s) => s.outcome === 'reading');
  const done = sessions.filter((s) => s.outcome !== 'reading').sort((a, b) => sessionKey(a).localeCompare(sessionKey(b)));
  // At most one in-progress session; it always comes last.
  return [...done, ...active.slice(-1)];
}

function withFinalPage(session: ReadSession, pageCount: number | undefined, date: ISODate): ReadSession {
  if (!pageCount) return session;
  const last = session.progress.at(-1)?.page ?? 0;
  if (last >= pageCount) return session;
  return { ...session, progress: [...session.progress, { id: uid('p'), at: stampFor(date), page: pageCount }] };
}

const laterOf = (a: ISODate, b?: ISODate) => (b && b > a ? b : a);

export function applyStatus(entry: LibraryEntry, status: Status, date: ISODate = today()): LibraryEntry {
  const sessions = entry.sessions.map((s) => ({ ...s }));
  const last = sessions.at(-1);
  const active = last?.outcome === 'reading' ? last : undefined;

  switch (status) {
    case 'want': {
      // Moving back to the queue discards an in-progress read-through.
      if (active) sessions.pop();
      return { ...entry, status: 'want', queuedAt: date, sessions };
    }
    case 'reading': {
      if (active) return { ...entry, status: 'reading' };
      if (last && last.outcome === 'dnf' && entry.status === 'dnf') {
        // Resume an abandoned read.
        sessions[sessions.length - 1] = { ...last, outcome: 'reading', endedAt: undefined };
        return { ...entry, status: 'reading', sessions };
      }
      sessions.push({ id: uid('s'), startedAt: date, outcome: 'reading', progress: [] });
      return { ...entry, status: 'reading', sessions };
    }
    case 'finished': {
      if (active) {
        sessions[sessions.length - 1] = withFinalPage({ ...active, outcome: 'finished', endedAt: laterOf(date, active.startedAt) }, entry.pageCount, laterOf(date, active.startedAt));
      } else if (last && last.outcome === 'dnf' && entry.status === 'dnf') {
        sessions[sessions.length - 1] = withFinalPage({ ...last, outcome: 'finished', endedAt: laterOf(date, last.startedAt) }, entry.pageCount, laterOf(date, last.startedAt));
      } else if (entry.status === 'finished') {
        return entry;
      } else {
        sessions.push({ id: uid('s'), endedAt: date, outcome: 'finished', progress: [] });
      }
      return { ...entry, status: 'finished', sessions: sortSessions(sessions) };
    }
    case 'dnf': {
      if (active) {
        sessions[sessions.length - 1] = { ...active, outcome: 'dnf', endedAt: laterOf(date, active.startedAt) };
      } else if (entry.status === 'dnf') {
        return entry;
      } else {
        sessions.push({ id: uid('s'), startedAt: date, endedAt: date, outcome: 'dnf', progress: [] });
      }
      return { ...entry, status: 'dnf', sessions: sortSessions(sessions) };
    }
  }
}

export function applyProgress(entry: LibraryEntry, page: number, at: LocalDateTime = nowLocal(), note?: string): LibraryEntry {
  let e = entry.status === 'reading' && activeSession(entry) ? entry : applyStatus(entry, 'reading', dateOf(at));
  const sessions = e.sessions.map((s) => ({ ...s }));
  const idx = sessions.length - 1;
  const max = e.pageCount ?? 100000;
  const p = clamp(Math.round(page), 0, max);
  const active = sessions[idx];
  active.progress = [...active.progress, { id: uid('p'), at, page: p, ...(note ? { note } : {}) }].sort((a, b) => a.at.localeCompare(b.at));
  if (!active.startedAt || dateOf(at) < active.startedAt) active.startedAt = dateOf(at);
  e = { ...e, sessions };
  return e;
}

function statusAfterSessionChange(prev: LibraryEntry, sessions: ReadSession[], touchedLatest: boolean): Status {
  const last = sessions.at(-1);
  if (!last) return 'want';
  if (last.outcome === 'reading') return 'reading';
  if (!touchedLatest) return prev.status === 'reading' ? last.outcome : prev.status;
  if (prev.status === 'want' && prev.sessions.at(-1)?.outcome !== 'reading') return 'want';
  return last.outcome;
}

export function applySessionPatch(
  entry: LibraryEntry,
  sessionId: string,
  patch: { startedAt?: ISODate | null; endedAt?: ISODate | null; outcome?: SessionOutcome },
): LibraryEntry {
  const idx = entry.sessions.findIndex((s) => s.id === sessionId);
  if (idx < 0) return entry;
  const isLatest = idx === entry.sessions.length - 1;
  const s = { ...entry.sessions[idx] };
  if (patch.outcome) {
    if (patch.outcome === 'reading' && !isLatest) return entry;
    s.outcome = patch.outcome;
  }
  if (patch.startedAt !== undefined) s.startedAt = patch.startedAt ?? undefined;
  if (patch.endedAt !== undefined) s.endedAt = patch.endedAt ?? undefined;
  if (s.outcome === 'reading') s.endedAt = undefined;
  else if (!s.endedAt) s.endedAt = s.startedAt ?? today();
  if (s.startedAt && s.endedAt && s.endedAt < s.startedAt) {
    // Keep the range valid: move whichever end was not edited.
    if (patch.endedAt !== undefined) s.startedAt = s.endedAt;
    else s.endedAt = s.startedAt;
  }
  const sessions = entry.sessions.slice();
  sessions[idx] = s.outcome === 'finished' && entry.sessions[idx].outcome !== 'finished' ? withFinalPage(s, entry.pageCount, s.endedAt ?? today()) : s;
  const sorted = sortSessions(sessions);
  return { ...entry, sessions: sorted, status: statusAfterSessionChange(entry, sorted, isLatest) };
}

/* ------------------------------------------------------------------ */
/* Reducer                                                             */
/* ------------------------------------------------------------------ */

function touch(entry: LibraryEntry): LibraryEntry {
  return { ...entry, updatedAt: nowLocal() };
}

function mapEntry(state: AppState, id: string, fn: (e: LibraryEntry) => LibraryEntry): AppState {
  const entry = state.library[id];
  if (!entry) return state;
  const next = fn(entry);
  if (next === entry) return state;
  return { ...state, library: { ...state.library, [id]: touch(next) } };
}

export function createEntry(a: Extract<Action, { type: 'addBook' }>, defaultFormat: BookFormat): LibraryEntry {
  const date = a.date ?? today();
  const pageCount = a.pageCount ?? a.edition?.pages ?? a.book.pagesMedian;
  const base: LibraryEntry = {
    id: a.book.id,
    book: a.book,
    status: 'want',
    addedAt: date,
    queuedAt: date,
    shelves: a.shelves ?? [],
    edition: a.edition,
    pageCount,
    format: a.format ?? formatFromEdition(a.edition) ?? defaultFormat,
    sessions: [],
    updatedAt: nowLocal(),
  };
  return a.status === 'want' ? base : applyStatus(base, a.status, date);
}

export function formatFromEdition(edition?: EditionInfo): BookFormat | undefined {
  const f = edition?.format?.toLowerCase() ?? '';
  if (!f) return undefined;
  if (/audio|cd|cassette|mp3/.test(f)) return 'audiobook';
  if (/e-?book|kindle|epub|electronic|digital/.test(f)) return 'ebook';
  if (/hard|cloth|library binding/.test(f)) return 'hardcover';
  if (/paper|soft|mass market|trade/.test(f)) return 'paperback';
  return 'other';
}

function mergeStates(current: AppState, incoming: AppState): AppState {
  const library = { ...current.library };
  for (const [id, entry] of Object.entries(incoming.library)) {
    const mine = library[id];
    if (!mine || entry.updatedAt >= mine.updatedAt) library[id] = entry;
  }
  const shelfIds = new Set(current.shelves.map((s) => s.id));
  const presetIds = new Set(current.chartPresets.map((c) => c.id));
  return {
    ...current,
    library,
    shelves: [...current.shelves, ...incoming.shelves.filter((s) => !shelfIds.has(s.id))],
    chartPresets: [...current.chartPresets, ...incoming.chartPresets.filter((c) => !presetIds.has(c.id))],
  };
}

export function reducer(state: AppState, action: Action): AppState {
  switch (action.type) {
    case 'addBook': {
      if (state.library[action.book.id]) {
        return mapEntry(state, action.book.id, (e) => applyStatus(e, action.status, action.date));
      }
      const entry = createEntry(action, state.settings.defaultFormat);
      return { ...state, library: { ...state.library, [entry.id]: entry } };
    }
    case 'setStatus':
      return mapEntry(state, action.id, (e) => (e.status === action.status ? e : applyStatus(e, action.status, action.date)));
    case 'logProgress':
      return mapEntry(state, action.id, (e) => applyProgress(e, action.page, action.at, action.note));
    case 'deleteProgress':
      return mapEntry(state, action.id, (e) => ({
        ...e,
        sessions: e.sessions.map((s) => (s.id === action.sessionId ? { ...s, progress: s.progress.filter((p) => p.id !== action.logId) } : s)),
      }));
    case 'updateSession':
      return mapEntry(state, action.id, (e) => applySessionPatch(e, action.sessionId, action.patch));
    case 'addSession':
      return mapEntry(state, action.id, (e) => {
        let session: ReadSession = {
          id: uid('s'),
          startedAt: action.startedAt,
          endedAt: action.endedAt ?? action.startedAt ?? today(),
          outcome: action.outcome,
          progress: [],
        };
        if (session.startedAt && session.endedAt && session.endedAt < session.startedAt) session = { ...session, startedAt: session.endedAt };
        if (action.outcome === 'finished') session = withFinalPage(session, e.pageCount, session.endedAt!);
        const sessions = sortSessions([...e.sessions, session]);
        const isLatestDone = sessions.filter((s) => s.outcome !== 'reading').at(-1)?.id === session.id;
        let status = e.status;
        if (!activeSession({ ...e, sessions }) && isLatestDone && (e.status !== 'want' || e.sessions.length === 0)) status = action.outcome;
        if (activeSession({ ...e, sessions })) status = 'reading';
        return { ...e, sessions, status };
      });
    case 'deleteSession':
      return mapEntry(state, action.id, (e) => {
        const idx = e.sessions.findIndex((s) => s.id === action.sessionId);
        if (idx < 0) return e;
        const sessions = e.sessions.filter((s) => s.id !== action.sessionId);
        return { ...e, sessions, status: statusAfterSessionChange(e, sessions, idx === e.sessions.length - 1) };
      });
    case 'updateEntry':
      return mapEntry(state, action.id, (e) => {
        const next: LibraryEntry = { ...e, ...action.patch };
        if ('rating' in action.patch || 'review' in action.patch) next.reviewedAt = today();
        return next;
      });
    case 'setEdition':
      return mapEntry(state, action.id, (e) => ({
        ...e,
        edition: action.edition,
        pageCount: action.edition?.pages ?? e.pageCount,
        format: formatFromEdition(action.edition) ?? e.format,
      }));
    case 'updateBook':
      return mapEntry(state, action.id, (e) => ({ ...e, book: { ...e.book, ...action.book, id: e.book.id } }));
    case 'removeBook': {
      if (!state.library[action.id]) return state;
      const library = { ...state.library };
      delete library[action.id];
      return { ...state, library };
    }
    case 'toggleShelf':
      return mapEntry(state, action.id, (e) => ({
        ...e,
        shelves: e.shelves.includes(action.shelfId) ? e.shelves.filter((s) => s !== action.shelfId) : [...e.shelves, action.shelfId],
      }));
    case 'createShelf':
      if (state.shelves.some((s) => s.id === action.shelf.id)) return state;
      return { ...state, shelves: [...state.shelves, action.shelf] };
    case 'updateShelf':
      return { ...state, shelves: state.shelves.map((s) => (s.id === action.shelfId ? { ...s, ...action.patch } : s)) };
    case 'deleteShelf': {
      const library: Record<string, LibraryEntry> = {};
      for (const [id, e] of Object.entries(state.library)) {
        library[id] = e.shelves.includes(action.shelfId) ? { ...e, shelves: e.shelves.filter((s) => s !== action.shelfId) } : e;
      }
      return {
        ...state,
        library,
        shelves: state.shelves.filter((s) => s.id !== action.shelfId),
        chart: { ...state.chart, filters: { ...state.chart.filters, shelves: state.chart.filters.shelves.filter((s) => s !== action.shelfId) } },
      };
    }
    case 'updateSettings':
      return { ...state, settings: sanitizeSettings({ ...state.settings, ...action.patch }) };
    case 'resetSettings':
      return {
        ...state,
        settings: {
          ...structuredClone(DEFAULT_SETTINGS),
          // Keep reading preferences that are not about appearance.
          yearlyBookGoal: state.settings.yearlyBookGoal,
          yearlyPageGoal: state.settings.yearlyPageGoal,
          onlineLookup: state.settings.onlineLookup,
        },
      };
    case 'setChart':
      return { ...state, chart: action.chart };
    case 'saveChartPreset': {
      const exists = state.chartPresets.some((c) => c.id === action.chart.id);
      return {
        ...state,
        chartPresets: exists ? state.chartPresets.map((c) => (c.id === action.chart.id ? action.chart : c)) : [...state.chartPresets, action.chart],
      };
    }
    case 'deleteChartPreset':
      return { ...state, chartPresets: state.chartPresets.filter((c) => c.id !== action.presetId) };
    case 'importState': {
      const incoming = sanitizeState(action.state);
      if (action.mode === 'replace') return { ...incoming, onboarded: true };
      return mergeStates(state, incoming);
    }
    case 'loadSample': {
      const library = { ...state.library };
      for (const e of action.entries) if (!library[e.id]) library[e.id] = e;
      const ids = new Set(state.shelves.map((s) => s.id));
      return { ...state, library, shelves: [...state.shelves, ...action.shelves.filter((s) => !ids.has(s.id))], onboarded: true };
    }
    case 'clearSample': {
      const library: Record<string, LibraryEntry> = {};
      for (const [id, e] of Object.entries(state.library)) if (!e.sample) library[id] = e;
      const used = new Set(Object.values(library).flatMap((e) => e.shelves));
      return { ...state, library, shelves: state.shelves.filter((s) => !s.id.startsWith('sample-') || used.has(s.id)) };
    }
    case 'resetAll':
      return { ...initialState(), onboarded: true };
    case 'hydrate':
      return action.state;
    case 'setOnboarded':
      return { ...state, onboarded: true };
  }
}
