/**
 * A small catalogue of FICTIONAL books (invented titles, authors, publishers and figures)
 * used for the optional sample library, offline browsing and tests. Nothing here
 * describes a real book.
 */
import type { BookFormat, BookSnapshot, EditionInfo, ISODate, LibraryEntry, ProgressLog, ReadSession, Shelf, Status } from '../lib/types';
import { addDays, eachDay, nowLocal, parseISODate, today } from '../lib/dates';
import { hashString, median, seededRandom } from '../lib/util';
import type { AuthorDetails, RatingsSummary, SearchResult, ShelfCounts, WorkDetails } from '../api/openlibrary';

interface SampleEdition {
  publisher: string;
  year: number;
  format: string;
  pages?: number;
  language?: string;
}

export interface SampleBook {
  slug: string;
  title: string;
  subtitle?: string;
  authors: string[];
  year: number;
  subjects: string[];
  synopsis: string;
  editions: SampleEdition[];
  ratings: [number, number, number, number, number];
  readers: [number, number, number];
  excerpt?: string;
  places?: string[];
  people?: string[];
}

export const SAMPLE_AUTHORS: Record<string, string> = {
  'Imogen Vale': 'Imogen Vale grew up above a print shop and now writes fantasy from a converted railway signal box. The Veyl novels are her first series.',
  'Corin Ashdown': 'Corin Ashdown was a coastguard radio operator for twelve years before turning to crime fiction set along wind-battered coasts.',
  'Priya Ramaswamy': 'Priya Ramaswamy is a gravitational-wave physicist who runs a long-running science column for curious non-scientists.',
  'Kofi Mensah-Byrne': 'Kofi Mensah-Byrne (1931–2004) wrote eleven short, philosophical science-fiction novels, of which The Patient Engine is the best known.',
};

const H = 'Harrow & Finch';
const LL = 'Lanternlight Press';
const BH = 'Blue Heron Books';
const QS = 'Quillstone';
const MB = 'Marrowbone & Co.';
const NL = 'Northlight Editions';
const PH = 'Pellucid House';
const TA = 'Tern Audio';

export const SAMPLE_BOOKS: SampleBook[] = [
  {
    slug: 'lantern-cartographer',
    title: 'The Lantern Cartographer',
    authors: ['Imogen Vale'],
    year: 2019,
    subjects: ['Fantasy', 'Maps', 'Cities', 'Fiction'],
    synopsis:
      'Every night at the eleventh bell, the city of Veyl grows three new streets, and every morning they are gone. Tamsin Orrell, an apprentice surveyor with a stammer and a borrowed lantern, is the only person who can map them before they vanish.\n\nWhen her charts begin predicting fires before they happen, the Guild of Lamplighters wants her silenced, and the streets themselves seem to want her to stay. A luminous, twisting fantasy about the places we are not supposed to see.',
    editions: [
      { publisher: LL, year: 2019, format: 'Hardcover', pages: 432 },
      { publisher: LL, year: 2020, format: 'Paperback', pages: 448 },
      { publisher: QS, year: 2019, format: 'Ebook', pages: 410 },
      { publisher: TA, year: 2019, format: 'Audiobook' },
    ],
    ratings: [12, 30, 140, 420, 610],
    readers: [5400, 820, 3900],
    excerpt: 'The streets that came at night smelled of lamp oil and rain that had not fallen yet.',
    places: ['Veyl'],
    people: ['Tamsin Orrell'],
  },
  {
    slug: 'daylight-atlas',
    title: 'The Daylight Atlas',
    authors: ['Imogen Vale'],
    year: 2023,
    subjects: ['Fantasy', 'Maps', 'Sequels', 'Fiction'],
    synopsis:
      "Tamsin Orrell's maps have made her famous, and fame has made her useful to dangerous people. Now the vanishing streets of Veyl have begun to appear in daylight, and the city is coming apart along the lines she drew.\n\nThe dazzling sequel to The Lantern Cartographer: bigger, stranger and kinder than the first.",
    editions: [
      { publisher: LL, year: 2023, format: 'Hardcover', pages: 512 },
      { publisher: LL, year: 2024, format: 'Paperback', pages: 528 },
    ],
    ratings: [8, 22, 96, 300, 380],
    readers: [3100, 940, 1600],
    places: ['Veyl'],
    people: ['Tamsin Orrell'],
  },
  {
    slug: 'salt-and-static',
    title: 'Salt and Static',
    authors: ['Teodor Anand'],
    year: 2021,
    subjects: ['Science fiction', 'Climate fiction', 'Radio', 'Time travel'],
    synopsis:
      'On a desalination rig drifting far out at sea, radio operator Mira Desai picks up a weather report for a storm that will not form for another forty years. The voice reading it is her own.\n\nAs the crew argue over whether to believe her, the signal keeps coming, each broadcast a little more desperate and each one rewriting what the future needs from them. Tense, humane and full of salt air.',
    editions: [
      { publisher: H, year: 2021, format: 'Hardcover', pages: 368 },
      { publisher: H, year: 2022, format: 'Paperback', pages: 384 },
      { publisher: BH, year: 2021, format: 'Ebook', pages: 352 },
    ],
    ratings: [20, 41, 160, 380, 290],
    readers: [2800, 300, 2400],
  },
  {
    slug: 'quiet-ledger',
    title: 'A Quiet Ledger',
    authors: ['Marguerite Okonkwo-Hale'],
    year: 2016,
    subjects: ['Literary fiction', 'Families', 'Grief', 'Fiction'],
    synopsis:
      "After her father's death, accountant Ada Ilori inherits forty years of his household ledgers: every loaf of bread, every bus fare, and an unexplained payment of eleven pounds on the first Thursday of every month.\n\nReading the columns like a diary, she reconstructs a man she barely knew, and a second family hidden in plain arithmetic. Spare, tender and quietly devastating.",
    editions: [
      { publisher: NL, year: 2016, format: 'Hardcover', pages: 288 },
      { publisher: NL, year: 2017, format: 'Paperback', pages: 304 },
    ],
    ratings: [9, 25, 110, 330, 420],
    readers: [3900, 210, 5100],
  },
  {
    slug: 'ninth-lighthouse',
    title: 'The Ninth Lighthouse',
    authors: ['Corin Ashdown'],
    year: 2018,
    subjects: ['Mystery', 'Detective and mystery stories', 'Lighthouses', 'Coasts'],
    synopsis:
      'The light at Skerrow Point was decommissioned decades ago. So why has it turned on every night this October?\n\nLighthouse inspector Ewan Strachan arrives expecting faulty wiring and finds a logbook updated in a dead keeper\'s handwriting, a village that will not talk, and a tide table that predicts a drowning. An atmospheric, slow-burning coastal mystery.',
    editions: [
      { publisher: MB, year: 2018, format: 'Paperback', pages: 352 },
      { publisher: MB, year: 2018, format: 'Hardcover', pages: 336 },
      { publisher: MB, year: 2019, format: 'Large print', pages: 512 },
    ],
    ratings: [15, 48, 210, 360, 200],
    readers: [2100, 160, 3300],
    places: ['Skerrow Point'],
  },
  {
    slug: 'lamplighters-wager',
    title: "The Lamplighter's Wager",
    authors: ['Corin Ashdown'],
    year: 2021,
    subjects: ['Mystery', 'Historical fiction', 'Novellas', 'Murder'],
    synopsis:
      'A short, sly Victorian puzzle. A lamplighter bets a magistrate that he can prove a murder using nothing but the order in which the gas lamps of Crane Street went out on the night in question.',
    editions: [{ publisher: MB, year: 2021, format: 'Hardcover', pages: 144 }],
    ratings: [6, 14, 70, 120, 60],
    readers: [900, 40, 1200],
  },
  {
    slug: 'orchard-small-hours',
    title: 'Orchard of Small Hours',
    authors: ['Lina Sørensen'],
    year: 2014,
    subjects: ['Historical fiction', 'World War, 1939-1945', 'Orchards', 'Courage'],
    synopsis:
      "In an occupied country, widow Karen Holm keeps her late husband's apple orchard alive and, with it, a secret: the grafts on her trees encode the names of families waiting for a boat across the strait.\n\nWhen a botanist billeted at the farm begins to study the orchard with unsettling care, every pruning becomes a risk. A patient, beautifully observed novel of courage measured in seasons.",
    editions: [
      { publisher: PH, year: 2014, format: 'Hardcover', pages: 464 },
      { publisher: PH, year: 2015, format: 'Paperback', pages: 480 },
    ],
    ratings: [11, 20, 120, 410, 470],
    readers: [4300, 260, 6100],
  },
  {
    slug: 'river-keeps',
    title: 'The River Keeps',
    subtitle: 'A Year on the Foreshore',
    authors: ['Ada Penhallow'],
    year: 2020,
    subjects: ['Nature', 'Natural history', 'Memoir', 'Archaeology', 'Rivers'],
    synopsis:
      'For one year, Ada Penhallow walked a tidal river foreshore at every low tide, collecting what the water gave back: clay pipes, Roman pins, a child\'s lead soldier, a ring engraved "think on me".\n\nPart natural history, part social history and part grief memoir, this is a meditation on what is lost, what returns, and the patience it takes to notice either.',
    editions: [
      { publisher: BH, year: 2020, format: 'Hardcover', pages: 320 },
      { publisher: BH, year: 2021, format: 'Paperback', pages: 336 },
      { publisher: TA, year: 2020, format: 'Audiobook' },
    ],
    ratings: [5, 16, 80, 260, 300],
    readers: [2600, 300, 2200],
  },
  {
    slug: 'gravity-for-beginners',
    title: 'Gravity for Beginners',
    authors: ['Priya Ramaswamy'],
    year: 2022,
    subjects: ['Science', 'Physics', 'Popular works', 'Gravity'],
    synopsis:
      'Why do you weigh less at the equator? How do you weigh a planet? What is a black hole actually made of?\n\nPhysicist Priya Ramaswamy answers the questions you were too embarrassed to ask, with kitchen-table experiments, unexpected history and a running joke about a very patient apple. A guided tour from rolling balls down ramps to gravitational waves.',
    editions: [
      { publisher: QS, year: 2022, format: 'Hardcover', pages: 272 },
      { publisher: QS, year: 2023, format: 'Paperback', pages: 288 },
      { publisher: QS, year: 2022, format: 'Ebook', pages: 260 },
    ],
    ratings: [7, 18, 90, 290, 330],
    readers: [3500, 610, 2900],
  },
  {
    slug: 'clockmakers-apprentice',
    title: "The Clockmaker's Apprentice",
    authors: ['Ezra Finch'],
    year: 2017,
    subjects: ['Young adult fiction', 'Fantasy', 'Clocks and watches', 'Coming of age'],
    synopsis:
      'Fifteen-year-old Pell Harrow is apprenticed to the last clockmaker in a city where time is rationed and sold by the hour. When a customer leaves behind a pocket watch that runs backwards, Pell discovers that the city\'s clocks are not measuring time; they are storing it.\n\nBrisk, inventive and full of ticking machinery: the first book of the Hourglass Quartet.',
    editions: [
      { publisher: LL, year: 2017, format: 'Paperback', pages: 336 },
      { publisher: LL, year: 2017, format: 'Hardcover', pages: 320 },
    ],
    ratings: [22, 60, 190, 280, 170],
    readers: [1900, 220, 2600],
  },
  {
    slug: 'paper-museum',
    title: 'Midnight at the Paper Museum',
    authors: ['Hana Morioka'],
    year: 2023,
    subjects: ['Mystery', 'Museums', 'Forgery', 'Cozy mysteries'],
    synopsis:
      'Night guard Saki Tomoda knows every exhibit in a tiny museum devoted to paper. So when a three-hundred-year-old folding screen is replaced with a perfect forgery, she is the first to notice and the last to be believed.\n\nA cosy-but-sharp mystery about craft, counterfeits and the people who care about small things.',
    editions: [
      { publisher: H, year: 2023, format: 'Paperback', pages: 304 },
      { publisher: H, year: 2023, format: 'Ebook', pages: 290 },
    ],
    ratings: [6, 15, 100, 280, 210],
    readers: [2500, 330, 1800],
  },
  {
    slug: 'glass-house-letters',
    title: 'Letters from the Glass House',
    authors: ['Beatrix Moreau'],
    year: 2015,
    subjects: ['Romance', 'Love stories', 'Epistolary fiction', 'Botany'],
    synopsis:
      'When a botanist and a reclusive glassblower are paired as pen pals by a wartime letter-writing scheme, neither expects much. Over six years of letters about orchids, kilns, rationing and loneliness, they build a friendship, and then something more, without ever meeting.\n\nWarm, witty and achingly romantic.',
    editions: [
      { publisher: PH, year: 2015, format: 'Paperback', pages: 400 },
      { publisher: PH, year: 2015, format: 'Hardcover', pages: 384 },
    ],
    ratings: [10, 22, 120, 350, 460],
    readers: [4100, 270, 4700],
  },
  {
    slug: 'weight-of-snowfall',
    title: 'The Weight of Snowfall',
    authors: ['Sigrid Halvorsen'],
    year: 2020,
    subjects: ['Thriller', 'Suspense', 'Avalanches', 'Murder'],
    synopsis:
      'A mountain resort is cut off by an avalanche with fourteen guests, one missing heiress and a killer who knows the slopes better than the rescuers.\n\nFormer mountain guide Tove Lund has forty-eight hours before the road reopens to find out who triggered the slide, and why the snow is hiding more than one body. Relentless and icy.',
    editions: [
      { publisher: MB, year: 2020, format: 'Hardcover', pages: 416 },
      { publisher: MB, year: 2021, format: 'Paperback', pages: 432 },
      { publisher: TA, year: 2020, format: 'Audiobook' },
    ],
    ratings: [18, 50, 230, 340, 190],
    readers: [2300, 190, 3800],
  },
  {
    slug: 'patron-saint-of-salt',
    title: 'The Patron Saint of Salt',
    subtitle: 'Essays from the Kitchen',
    authors: ['Rosa Delacroix-Mbeki'],
    year: 2019,
    subjects: ['Essays', 'Food', 'Cooking', 'Memoir'],
    synopsis:
      'Twenty essays on the rituals of the kitchen: the grandmother who salted everything twice, the restaurant that only served soup, the science of why bread forgives you.\n\nRosa Delacroix-Mbeki writes about food as memory, labour and inheritance, and includes the recipes, which are as generous as the prose.',
    editions: [
      { publisher: QS, year: 2019, format: 'Hardcover', pages: 256 },
      { publisher: QS, year: 2020, format: 'Paperback', pages: 272 },
    ],
    ratings: [4, 9, 60, 190, 240],
    readers: [2000, 120, 1500],
  },
  {
    slug: 'atlas-forgotten-sounds',
    title: 'The Atlas of Forgotten Sounds',
    authors: ['Felix Marrow'],
    year: 2021,
    subjects: ['Music', 'History', 'Sound', 'Nonfiction'],
    synopsis:
      'What did an ancient forum sound like? Or a Victorian factory, or a bird nobody has heard for a century?\n\nSound historian Felix Marrow travels from cathedral echoes to extinct birdsong in search of noises the world has lost, and the scientists, archivists and eccentrics trying to bring them back.',
    editions: [
      { publisher: NL, year: 2021, format: 'Hardcover', pages: 352 },
      { publisher: NL, year: 2022, format: 'Paperback', pages: 368 },
    ],
    ratings: [12, 30, 120, 160, 90],
    readers: [1700, 150, 900],
  },
  {
    slug: 'starlings-over-ashford',
    title: 'Starlings Over Ashford',
    authors: ['Wren Calloway'],
    year: 2018,
    subjects: ['Poetry', 'Birds', 'Towns'],
    synopsis:
      "Sixty-one poems about murmurations, motorway verges, night shifts and the small astonishments of an ordinary town. Calloway's second collection moves between the comic and the elegiac with a birdwatcher's patience and an ear for overheard speech.",
    editions: [
      { publisher: LL, year: 2018, format: 'Paperback', pages: 96 },
      { publisher: LL, year: 2019, format: 'Hardcover', pages: 104 },
    ],
    ratings: [3, 8, 40, 110, 120],
    readers: [800, 60, 700],
  },
  {
    slug: 'long-way-through-winter',
    title: 'The Long Way Through Winter',
    authors: ['Jonas Eberhart'],
    year: 2012,
    subjects: ['Travel', 'Walking', 'Description and travel', 'Memoir'],
    synopsis:
      'In the winter after his divorce, Jonas Eberhart walked fourteen hundred kilometres from a northern coast to the mountains, sleeping in monasteries, bus shelters and, once, a hayloft shared with a goat.\n\nThis is the book he wrote about it: funny, cold, lonely and full of the kindness of strangers.',
    editions: [
      { publisher: BH, year: 2012, format: 'Hardcover', pages: 320 },
      { publisher: BH, year: 2013, format: 'Paperback', pages: 336 },
    ],
    ratings: [5, 14, 70, 170, 130],
    readers: [1400, 80, 1300],
  },
  {
    slug: 'house-of-nine-doors',
    title: 'House of Nine Doors',
    authors: ['Odette Laurent'],
    year: 2022,
    subjects: ['Horror', 'Gothic fiction', 'Haunted houses'],
    synopsis:
      'The house on Rue Saint-Clair has nine doors, and the estate agent swears there were only eight when the Marchand family moved in. Each new door opens onto a room from a different decade of the house\'s history, and something in the oldest room has noticed them.\n\nA deliciously unsettling gothic for long nights.',
    editions: [
      { publisher: H, year: 2022, format: 'Hardcover', pages: 352 },
      { publisher: H, year: 2023, format: 'Paperback', pages: 368 },
      { publisher: H, year: 2022, format: 'Ebook', pages: 340 },
    ],
    ratings: [14, 35, 150, 300, 250],
    readers: [3600, 410, 2700],
  },
  {
    slug: 'field-guide-to-strangers',
    title: 'A Field Guide to Strangers',
    authors: ['Sam Okafor-Reyes'],
    year: 2024,
    subjects: ['Short stories', 'Literary fiction', 'Strangers'],
    synopsis:
      'Twelve stories about chance encounters: a night-bus driver and a runaway bride, two rivals stuck in a lift, a translator who invents a language to save a negotiation.\n\nOkafor-Reyes writes with wit and enormous warmth about the people we meet only once and remember forever.',
    editions: [
      { publisher: NL, year: 2024, format: 'Paperback', pages: 240 },
      { publisher: NL, year: 2024, format: 'Hardcover', pages: 224 },
    ],
    ratings: [3, 7, 45, 150, 160],
    readers: [2200, 190, 900],
  },
  {
    slug: 'iron-and-ivy',
    title: 'Iron & Ivy',
    authors: ['Delphine Ash'],
    year: 2025,
    subjects: ['Fantasy', 'Romance', 'Blacksmiths', 'Magic'],
    synopsis:
      "Blacksmith Maren has forged swords for the Iron Court for ten years without asking who they kill. When the queen's exiled gardener-mage is sentenced to death for treason, Maren is ordered to make the blade, and finds herself plotting his escape instead.\n\nSparks fly, literally and otherwise, in this swooning enemies-to-allies fantasy romance.",
    editions: [
      { publisher: QS, year: 2025, format: 'Paperback', pages: 512 },
      { publisher: QS, year: 2025, format: 'Hardcover', pages: 496 },
      { publisher: QS, year: 2025, format: 'Ebook', pages: 480 },
    ],
    ratings: [40, 70, 160, 300, 380],
    readers: [6100, 1200, 2700],
  },
  {
    slug: 'patient-engine',
    title: 'The Patient Engine',
    authors: ['Kofi Mensah-Byrne'],
    year: 1968,
    subjects: ['Science fiction', 'Robots', 'Space ships', 'Classics'],
    synopsis:
      "A generation ship's maintenance robot wakes every hundred years to repair what the sleeping crew cannot. On its ninth waking, it finds that the crew has stopped dreaming.\n\nA cult classic of quiet, philosophical science fiction, reissued with an introduction on its influence on later writers.",
    editions: [
      { publisher: 'Orbiter Books', year: 1968, format: 'Mass market paperback', pages: 192 },
      { publisher: QS, year: 2009, format: 'Paperback', pages: 224 },
      { publisher: QS, year: 2018, format: 'Hardcover', pages: 240 },
    ],
    ratings: [6, 12, 60, 230, 410],
    readers: [2900, 140, 5200],
    excerpt: 'On the ninth waking, the engine listened for dreams and heard only the hull.',
  },
  {
    slug: 'moths-lower-town',
    title: 'Moths of the Lower Town',
    authors: ['Ilse Varga'],
    year: 1954,
    subjects: ['Classics', 'Literary fiction', 'Translations', 'Moths'],
    synopsis:
      "In a river town after the war, a lepidopterist's widow takes in lodgers to survive: a pianist, a black-market dealer and a boy who collects moths. Over one humid summer their lives fold together like wings.\n\nTranslated into English decades after it first appeared, Varga's only novel is now regarded as a quiet masterpiece.",
    editions: [
      { publisher: PH, year: 1979, format: 'Paperback', pages: 260 },
      { publisher: PH, year: 2016, format: 'Paperback', pages: 288 },
    ],
    ratings: [4, 11, 60, 170, 210],
    readers: [1800, 70, 1900],
  },
  {
    slug: 'ninefold-crowns',
    title: 'Ninefold Crowns',
    authors: ['R. T. Castellan'],
    year: 2011,
    subjects: ['Fantasy', 'Epic fantasy', 'Kings and rulers', 'Fiction'],
    synopsis:
      'Nine kingdoms, nine crowns, and one coronation that goes catastrophically wrong. When the High Throne is shattered during the solstice ceremony, a disgraced herald, a soldier-turned-scribe and a princess who would rather be a cartographer must reassemble an empire, or let it fall for good.\n\nA sprawling, rewarding epic with maps, appendices and a cast list you will genuinely need.',
    editions: [
      { publisher: H, year: 2011, format: 'Hardcover', pages: 880 },
      { publisher: H, year: 2012, format: 'Paperback', pages: 912 },
      { publisher: H, year: 2011, format: 'Ebook', pages: 860 },
    ],
    ratings: [30, 60, 220, 520, 640],
    readers: [7200, 900, 6400],
  },
];

/* ------------------------------------------------------------------ */
/* Catalogue adapters                                                  */
/* ------------------------------------------------------------------ */

const bySlug = new Map(SAMPLE_BOOKS.map((b) => [b.slug, b]));

export const sampleId = (slug: string) => `sample:${slug}`;
export const isSampleId = (id: string) => id.startsWith('sample:');
export const getSampleBook = (id: string) => bySlug.get(id.replace(/^sample:/, ''));

export function sampleEditions(b: SampleBook): EditionInfo[] {
  return b.editions.map((e, i) => ({
    key: `${b.slug}-e${i + 1}`,
    title: b.title,
    publisher: e.publisher,
    publishDate: String(e.year),
    year: e.year,
    pages: e.pages,
    format: e.format,
    language: e.language ?? 'eng',
  }));
}

export function sampleSnapshot(b: SampleBook): BookSnapshot {
  return {
    id: sampleId(b.slug),
    source: 'sample',
    title: b.title,
    subtitle: b.subtitle,
    authors: b.authors.map((name) => ({ name })),
    firstPublishYear: b.year,
    subjects: b.subjects,
    pagesMedian: median(b.editions.map((e) => e.pages).filter((p): p is number => !!p)),
    editionCount: b.editions.length,
  };
}

export function sampleRatings(b: SampleBook): RatingsSummary {
  const count = b.ratings.reduce((a, c) => a + c, 0);
  const average = b.ratings.reduce((a, c, i) => a + c * (i + 1), 0) / count;
  return { average, count, counts: b.ratings };
}

export const sampleReaders = (b: SampleBook): ShelfCounts => ({ want: b.readers[0], reading: b.readers[1], read: b.readers[2] });

export function sampleResult(b: SampleBook): SearchResult {
  const r = sampleRatings(b);
  const snap = sampleSnapshot(b);
  return {
    id: snap.id,
    workKey: b.slug,
    title: b.title,
    subtitle: b.subtitle,
    authors: snap.authors,
    firstPublishYear: b.year,
    editionCount: b.editions.length,
    pagesMedian: snap.pagesMedian,
    subjects: b.subjects,
    ratingAverage: r.average,
    ratingCount: r.count,
    readers: sampleReaders(b),
    source: 'sample',
  };
}

export function sampleWork(b: SampleBook): WorkDetails {
  return {
    workKey: b.slug,
    title: b.title,
    subtitle: b.subtitle,
    description: b.synopsis,
    subjects: b.subjects,
    places: b.places ?? [],
    people: b.people ?? [],
    times: [],
    coverIds: [],
    authorKeys: [],
    firstPublishDate: String(b.year),
    excerpt: b.excerpt,
    links: [],
  };
}

export function sampleAuthor(name: string): AuthorDetails {
  return { key: '', name, bio: SAMPLE_AUTHORS[name] };
}

/** Very small local search used offline and for the sample catalogue. */
export function searchSample(query: { q?: string; title?: string; author?: string; subject?: string }): SearchResult[] {
  const norm = (s?: string) => (s ?? '').toLowerCase().trim();
  const q = norm(query.q);
  const t = norm(query.title);
  const a = norm(query.author);
  const s = norm(query.subject).replace(/_/g, ' ');
  return SAMPLE_BOOKS.filter((b) => {
    const authors = b.authors.join(' ').toLowerCase();
    const subjects = b.subjects.join(' ').toLowerCase();
    if (t && !b.title.toLowerCase().includes(t)) return false;
    if (a && !authors.includes(a)) return false;
    if (s && !subjects.includes(s.split(' ')[0])) return false;
    if (q) {
      const hay = `${b.title} ${b.subtitle ?? ''} ${authors} ${subjects}`.toLowerCase();
      return q.split(/\s+/).every((w) => hay.includes(w));
    }
    return true;
  }).map(sampleResult);
}

/* ------------------------------------------------------------------ */
/* Sample library                                                      */
/* ------------------------------------------------------------------ */

interface Plan {
  slug: string;
  status: Status;
  /** Days before today. */
  added: number;
  start?: number;
  end?: number;
  /** Current page for reading / stopping page for DNF. */
  page?: number;
  rating?: number;
  review?: string;
  shelves?: string[];
  format?: BookFormat;
  edition?: number;
  /** Earlier completed read-throughs: [startDaysAgo, endDaysAgo]. */
  earlier?: [number, number][];
  /** Whether day-by-day progress was logged (otherwise only dates). */
  logged?: boolean;
  notes?: string;
  favorite?: boolean;
}

const PLANS: Plan[] = [
  { slug: 'daylight-atlas', status: 'reading', added: 60, start: 9, page: 214, edition: 0, format: 'hardcover', shelves: ['owned'], logged: true },
  { slug: 'gravity-for-beginners', status: 'reading', added: 45, start: 20, page: 141, edition: 2, format: 'ebook', logged: true },
  { slug: 'river-keeps', status: 'reading', added: 30, start: 4, page: 58, edition: 1, format: 'paperback', shelves: ['sample-bookclub'], logged: true },
  {
    slug: 'lantern-cartographer', status: 'finished', added: 70, start: 40, end: 25, rating: 5, edition: 0, format: 'hardcover', logged: true,
    shelves: ['favourites', 'owned'], favorite: true,
    review: "Utterly transporting. The night-street chapters read like fever dreams, and Tamsin's stammer is written with real care. I want a map of Veyl on my wall.",
  },
  { slug: 'salt-and-static', status: 'finished', added: 90, start: 75, end: 61, rating: 4, edition: 2, format: 'ebook', logged: true },
  {
    slug: 'quiet-ledger', status: 'finished', added: 120, start: 100, end: 92, rating: 4.5, edition: 1, format: 'paperback', logged: true,
    shelves: ['sample-bookclub'],
    review: "I didn't expect a novel about bookkeeping to make me cry on a train. The ledger extracts are a stroke of genius.",
  },
  { slug: 'ninth-lighthouse', status: 'finished', added: 160, start: 130, end: 121, rating: 3.5, edition: 0, format: 'paperback', logged: true },
  { slug: 'orchard-small-hours', status: 'finished', added: 200, start: 170, end: 150, rating: 4, edition: 1, format: 'paperback', logged: true, shelves: ['owned'] },
  { slug: 'clockmakers-apprentice', status: 'finished', added: 210, start: 200, end: 193, rating: 3, edition: 0, format: 'paperback', logged: true },
  { slug: 'paper-museum', status: 'finished', added: 240, start: 230, end: 224, rating: 4, edition: 1, format: 'ebook', logged: true, shelves: ['sample-bookclub'] },
  {
    slug: 'glass-house-letters', status: 'finished', added: 300, start: 265, end: 250, rating: 4.5, edition: 0, format: 'paperback', logged: true,
    shelves: ['favourites'], favorite: true,
    review: "Cosy and clever. The glassblower's letters about kilns are secretly about grief, and the last letter undid me.",
  },
  { slug: 'weight-of-snowfall', status: 'finished', added: 310, start: 300, end: 294, rating: 3.5, edition: 0, format: 'audiobook', logged: false },
  { slug: 'starlings-over-ashford', status: 'finished', added: 330, start: 320, end: 318, rating: 4, edition: 0, format: 'paperback', logged: true, shelves: ['owned'] },
  {
    slug: 'patient-engine', status: 'finished', added: 700, start: 360, end: 352, rating: 5, edition: 1, format: 'paperback', logged: true,
    earlier: [[640, 630]], shelves: ['favourites', 'owned'], favorite: true,
    review: 'Second time through and it is even better. The final waking is one of the best endings in the genre.',
  },
  { slug: 'lamplighters-wager', status: 'finished', added: 420, start: 400, end: 399, rating: 3.5, edition: 0, format: 'hardcover', logged: false },
  { slug: 'ninefold-crowns', status: 'finished', added: 540, start: 520, end: 470, rating: 4.5, edition: 0, format: 'hardcover', logged: false, shelves: ['owned'] },
  { slug: 'moths-lower-town', status: 'finished', added: 600, start: 560, end: 545, rating: 4, edition: 1, format: 'paperback', logged: false },
  { slug: 'iron-and-ivy', status: 'dnf', added: 70, start: 58, end: 50, page: 140, edition: 0, format: 'paperback', logged: true, notes: 'The banter wore thin for me. Might try again in winter.' },
  { slug: 'atlas-forgotten-sounds', status: 'dnf', added: 230, start: 215, end: 205, page: 96, edition: 0, format: 'hardcover', logged: true },
  { slug: 'house-of-nine-doors', status: 'want', added: 12, edition: 1, format: 'paperback', shelves: ['sample-bookclub'] },
  { slug: 'field-guide-to-strangers', status: 'want', added: 30, edition: 0, format: 'paperback' },
  { slug: 'patron-saint-of-salt', status: 'want', added: 80, edition: 0, format: 'hardcover' },
  { slug: 'long-way-through-winter', status: 'want', added: 150, edition: 1, format: 'paperback' },
];

export const SAMPLE_SHELVES: Shelf[] = [{ id: 'sample-bookclub', name: 'Book club', color: '#2e6b52', createdAt: '2025-01-01' }];

function generateLogs(rand: () => number, start: ISODate, end: ISODate, fromPage: number, toPage: number, idSeed: string): ProgressLog[] {
  const days = eachDay(start, end);
  if (!days.length || toPage <= fromPage) return [];
  const chosen = days.filter((_, i) => i === 0 || i === days.length - 1 || rand() < 0.72);
  const weights = chosen.map((d) => {
    const dow = parseISODate(d).getDay();
    return (0.45 + rand()) * (dow === 0 || dow === 6 ? 1.5 : 1);
  });
  const total = weights.reduce((a, b) => a + b, 0);
  let acc = 0;
  let prev = fromPage;
  return chosen.map((d, i) => {
    acc += weights[i];
    const page = i === chosen.length - 1 ? toPage : Math.max(prev + 1, Math.round(fromPage + ((toPage - fromPage) * acc) / total));
    prev = Math.min(page, toPage);
    const hour = rand() < 0.3 ? 7 + Math.floor(rand() * 2) : 19 + Math.floor(rand() * 4);
    const minute = Math.floor(rand() * 60);
    return { id: `${idSeed}-${i}`, at: `${d}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`, page: prev };
  });
}

/** Build the sample library relative to `ref` (today by default). Deterministic for a given date. */
export function buildSampleLibrary(ref: ISODate = today()): LibraryEntry[] {
  const now = nowLocal();
  return PLANS.map((plan) => {
    const book = bySlug.get(plan.slug)!;
    const rand = seededRandom(hashString(plan.slug + ref));
    const editions = sampleEditions(book);
    const edition = editions[plan.edition ?? 0];
    const pageCount = edition.pages ?? sampleSnapshot(book).pagesMedian ?? 300;
    const ago = (n: number) => addDays(ref, -n);
    const sessions: ReadSession[] = [];

    for (const [s, e] of plan.earlier ?? []) {
      sessions.push({ id: `${plan.slug}-early-${s}`, startedAt: ago(s), endedAt: ago(e), outcome: 'finished', progress: [] });
    }
    if (plan.status !== 'want' && plan.start !== undefined) {
      const startedAt = ago(plan.start);
      const endedAt = plan.end !== undefined ? ago(plan.end) : undefined;
      const target = plan.status === 'finished' ? pageCount : Math.min(plan.page ?? 0, pageCount);
      const lastDay = endedAt ?? (plan.start > 1 && rand() < 0.5 ? ago(1) : ref);
      const progress = plan.logged ? generateLogs(rand, startedAt, lastDay, 0, target, `${plan.slug}-p`) : [];
      // Keep today's log in the past so the sample never sits in the future.
      const fixed = progress.map((p) => (p.at > now ? { ...p, at: `${p.at.slice(0, 10)}T${now.slice(11, 16)}` } : p));
      sessions.push({
        id: `${plan.slug}-s`,
        startedAt,
        endedAt: plan.status === 'reading' ? undefined : endedAt,
        outcome: plan.status === 'reading' ? 'reading' : plan.status === 'dnf' ? 'dnf' : 'finished',
        progress: fixed,
      });
    }

    return {
      id: sampleId(plan.slug),
      book: sampleSnapshot(book),
      status: plan.status,
      addedAt: ago(plan.added),
      queuedAt: ago(plan.added),
      shelves: plan.shelves ?? [],
      edition,
      pageCount,
      format: plan.format,
      sessions,
      rating: plan.rating,
      review: plan.review,
      reviewedAt: plan.review || plan.rating ? (plan.end !== undefined ? ago(plan.end) : undefined) : undefined,
      notes: plan.notes,
      favorite: plan.favorite,
      sample: true,
      updatedAt: plan.end !== undefined ? `${ago(plan.end)}T21:00` : plan.start !== undefined ? `${ago(Math.min(plan.start, 1))}T21:00` : `${ago(plan.added)}T12:00`,
    } satisfies LibraryEntry;
  });
}
