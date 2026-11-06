import { titleCase, uniq } from './util';

/**
 * Canonical genres, in priority order: the first one whose keywords match a book's
 * subjects becomes its primary genre for statistics.
 */
export const GENRES: { name: string; keywords: string[] }[] = [
  { name: 'Graphic Novels', keywords: ['graphic novel', 'comic', 'manga'] },
  { name: 'Poetry', keywords: ['poetry', 'poems', 'verse'] },
  { name: 'Drama', keywords: ['drama', 'plays', 'tragedies', 'comedies (drama)'] },
  { name: "Children's", keywords: ["children's", 'juvenile fiction', 'picture book', 'juvenile literature'] },
  { name: 'Young Adult', keywords: ['young adult', 'teen', 'coming of age'] },
  { name: 'Science Fiction', keywords: ['science fiction', 'sci-fi', 'space opera', 'dystopia', 'cyberpunk', 'time travel', 'robots'] },
  { name: 'Fantasy', keywords: ['fantasy', 'magic', 'dragons', 'wizards', 'fairy tales', 'mythical'] },
  { name: 'Horror', keywords: ['horror', 'ghost', 'haunted', 'gothic', 'supernatural', 'vampires'] },
  { name: 'Mystery', keywords: ['mystery', 'detective', 'whodunit', 'crime fiction', 'murder'] },
  { name: 'Thriller', keywords: ['thriller', 'suspense', 'espionage', 'spy'] },
  { name: 'Romance', keywords: ['romance', 'love stories', 'love story'] },
  { name: 'Historical Fiction', keywords: ['historical fiction', 'history fiction'] },
  { name: 'True Crime', keywords: ['true crime'] },
  { name: 'Biography & Memoir', keywords: ['biography', 'memoir', 'autobiography', 'diaries', 'letters'] },
  { name: 'History', keywords: ['history', 'historical'] },
  { name: 'Science', keywords: ['science', 'physics', 'biology', 'chemistry', 'astronomy', 'mathematics', 'evolution'] },
  { name: 'Nature', keywords: ['nature', 'natural history', 'ecology', 'environment', 'birds', 'wildlife'] },
  { name: 'Philosophy', keywords: ['philosophy', 'ethics', 'metaphysics'] },
  { name: 'Psychology', keywords: ['psychology', 'mind', 'cognition'] },
  { name: 'Religion', keywords: ['religion', 'theology', 'spirituality', 'bible'] },
  { name: 'Self-Help', keywords: ['self-help', 'self help', 'personal development', 'success'] },
  { name: 'Business', keywords: ['business', 'economics', 'management', 'finance'] },
  { name: 'Food & Cooking', keywords: ['cooking', 'food', 'recipes', 'cookery'] },
  { name: 'Travel', keywords: ['travel', 'voyages', 'description and travel'] },
  { name: 'Art & Music', keywords: ['art', 'music', 'painting', 'photography', 'design'] },
  { name: 'Essays', keywords: ['essays'] },
  { name: 'Short Stories', keywords: ['short stories', 'short story'] },
  { name: 'Humor', keywords: ['humor', 'humour', 'satire'] },
  { name: 'Classics', keywords: ['classic', 'classics'] },
  { name: 'Literary Fiction', keywords: ['literary fiction', 'literature', 'fiction'] },
  { name: 'Nonfiction', keywords: ['nonfiction', 'non-fiction'] },
];

export const GENRE_NAMES = GENRES.map((g) => g.name);
export const UNKNOWN_GENRE = 'Uncategorised';

const JUNK = [
  /^accessible book$/i,
  /^protected daisy$/i,
  /^in library$/i,
  /^lending library$/i,
  /^large type books$/i,
  /^overdrive$/i,
  /^open library staff picks$/i,
  /^nyt:/i,
  /^collectionid:/i,
  /^award:/i,
  /^reading level/i,
  /^long now manual/i,
  /^internet archive/i,
  /^english language/i,
  /^translations into/i,
  /^fiction, general$/i,
  /^general$/i,
  /^textbooks$/i,
  /^\d+$/,
];

/** Clean Open Library subjects: drop catalogue noise, dedupe, tidy casing. */
export function cleanSubjects(subjects: string[] | undefined, limit = 14): string[] {
  if (!subjects?.length) return [];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of subjects) {
    if (typeof raw !== 'string') continue;
    let s = raw.replace(/\s+/g, ' ').trim();
    if (!s || s.length > 48 || JUNK.some((re) => re.test(s))) continue;
    s = s.replace(/^Fiction, /i, '').replace(/, fiction$/i, '').replace(/ \(fictitious character\)$/i, '');
    const key = s.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(/[A-Z]/.test(s.slice(1)) ? s : titleCase(s));
    if (out.length >= limit) break;
  }
  return out;
}

export function primaryGenre(subjects: string[] | undefined, override?: string): string {
  if (override?.trim()) return override.trim();
  const lower = (subjects ?? []).map((s) => s.toLowerCase());
  for (const g of GENRES) {
    if (lower.some((s) => g.keywords.some((k) => s.includes(k)))) return g.name;
  }
  return UNKNOWN_GENRE;
}

/** All canonical genres that a subject list touches (for filters). */
export function matchingGenres(subjects: string[] | undefined): string[] {
  const lower = (subjects ?? []).map((s) => s.toLowerCase());
  return uniq(GENRES.filter((g) => lower.some((s) => g.keywords.some((k) => s.includes(k)))).map((g) => g.name));
}

/** Subjects shown as browse shelves on the Browse page. */
export const BROWSE_SUBJECTS: { label: string; subject: string }[] = [
  { label: 'Fantasy', subject: 'fantasy' },
  { label: 'Science Fiction', subject: 'science_fiction' },
  { label: 'Mystery', subject: 'mystery_and_detective_stories' },
  { label: 'Romance', subject: 'romance' },
  { label: 'Thrillers', subject: 'thrillers' },
  { label: 'Horror', subject: 'horror' },
  { label: 'Historical Fiction', subject: 'historical_fiction' },
  { label: 'Classics', subject: 'classic_literature' },
  { label: 'Poetry', subject: 'poetry' },
  { label: 'Biography', subject: 'biography' },
  { label: 'History', subject: 'history' },
  { label: 'Science', subject: 'science' },
  { label: 'Philosophy', subject: 'philosophy' },
  { label: 'Young Adult', subject: 'young_adult_fiction' },
  { label: 'Graphic Novels', subject: 'graphic_novels' },
  { label: 'Cooking', subject: 'cooking' },
];
