import type { BookFormat } from '../lib/types';
import type { DateFormat } from '../lib/dates';
import { clamp, contrastRatio, hexToRgb, isHexColor, mixHex } from '../lib/util';

/* ------------------------------------------------------------------ */
/* Palettes                                                            */
/* ------------------------------------------------------------------ */

export interface Palette {
  /** Surface the book rests on. */
  desk: string;
  /** Cloth of the cover boards. */
  cover: string;
  /** Lettering stamped on the cover/spine. */
  coverInk: string;
  /** Page colour. */
  paper: string;
  /** Text colour. */
  ink: string;
  /** Ribbon / primary action colour. */
  accent: string;
  /** Gold used for gilt edges and highlights. */
  gilt: string;
}

export type PaletteKey = keyof Palette;
export const PALETTE_KEYS: PaletteKey[] = ['cover', 'paper', 'ink', 'accent', 'desk', 'coverInk', 'gilt'];
export const PALETTE_LABELS: Record<PaletteKey, string> = {
  cover: 'Cover cloth',
  paper: 'Paper',
  ink: 'Ink',
  accent: 'Ribbon & accent',
  desk: 'Desk',
  coverInk: 'Cover lettering',
  gilt: 'Gilt',
};

export interface ThemeDef {
  label: string;
  blurb: string;
  light: Palette;
  dark: Palette;
}

export const THEMES = {
  oxblood: {
    label: 'Oxblood Cloth',
    blurb: 'Deep red buckram, cream laid paper.',
    light: { desk: '#d6cfc3', cover: '#5b2a2a', coverInk: '#e8d6a8', paper: '#fbf7ef', ink: '#2a2420', accent: '#9b3426', gilt: '#b8923a' },
    dark: { desk: '#0e0c0c', cover: '#3d1c1c', coverInk: '#d9c38a', paper: '#211d1b', ink: '#ece4d6', accent: '#e0806b', gilt: '#d1ad5b' },
  },
  bottle: {
    label: 'Bottle Green',
    blurb: 'Library-green boards with gold tooling.',
    light: { desk: '#d3d6cc', cover: '#24433a', coverInk: '#e3d7a9', paper: '#f8f6ee', ink: '#1f2622', accent: '#2e6b52', gilt: '#b39345' },
    dark: { desk: '#0b0f0d', cover: '#16302a', coverInk: '#d4c48f', paper: '#1c211f', ink: '#e4e8e1', accent: '#6fc0a0', gilt: '#cfae60' },
  },
  atlas: {
    label: 'Navy Atlas',
    blurb: 'Navy cloth like an old map case.',
    light: { desk: '#d2d5db', cover: '#22324d', coverInk: '#e6dcc0', paper: '#f7f6f2', ink: '#1e2330', accent: '#2f5ea8', gilt: '#b8995a' },
    dark: { desk: '#0b0d12', cover: '#18233a', coverInk: '#d8cba3', paper: '#1b1f29', ink: '#e3e6ee', accent: '#86aef0', gilt: '#d2b46e' },
  },
  kraft: {
    label: 'Kraft Paperback',
    blurb: 'Sepia pages and a brown-paper wrapper.',
    light: { desk: '#cdbfa8', cover: '#9c7448', coverInk: '#fbf1dc', paper: '#f4ead6', ink: '#3a2e22', accent: '#8e4f1f', gilt: '#a67c32' },
    dark: { desk: '#120e09', cover: '#4a3622', coverInk: '#f0dcb4', paper: '#26201a', ink: '#eadcc4', accent: '#e39b5f', gilt: '#d0a75a' },
  },
  midnight: {
    label: 'Midnight Edition',
    blurb: 'Near-black boards, violet ribbon.',
    light: { desk: '#cfcdd6', cover: '#23202e', coverInk: '#e2c784', paper: '#f8f7fb', ink: '#1f1d27', accent: '#6d4fb0', gilt: '#b48f3c' },
    dark: { desk: '#08070b', cover: '#17151f', coverInk: '#e2c784', paper: '#1d1b24', ink: '#e8e5ef', accent: '#b9a2f0', gilt: '#e2c784' },
  },
  ivory: {
    label: 'Ink & Ivory',
    blurb: 'High contrast, black board, red ribbon.',
    light: { desk: '#e4e2dc', cover: '#1c1c1c', coverInk: '#f3efe4', paper: '#fffdf8', ink: '#121212', accent: '#b42318', gilt: '#8a7a52' },
    dark: { desk: '#000000', cover: '#141414', coverInk: '#f3efe4', paper: '#161616', ink: '#f5f3ee', accent: '#ff6b5b', gilt: '#c9b37e' },
  },
  lavender: {
    label: 'Lavender Folio',
    blurb: 'Soft violet cloth, pale lilac pages.',
    light: { desk: '#d9d4e0', cover: '#54446f', coverInk: '#efe2c4', paper: '#fbf9fd', ink: '#29243a', accent: '#7b55b8', gilt: '#b4975c' },
    dark: { desk: '#0d0b12', cover: '#2c2440', coverInk: '#e2d2a8', paper: '#201d29', ink: '#ebe6f3', accent: '#c3a6f2', gilt: '#d4b878' },
  },
  sea: {
    label: 'Sea Glass',
    blurb: 'Teal linen and cool white paper.',
    light: { desk: '#cfdcda', cover: '#2f5f63', coverInk: '#f1e6c8', paper: '#f6faf8', ink: '#1d2a2b', accent: '#237a7f', gilt: '#b39b5c' },
    dark: { desk: '#081011', cover: '#1b393c', coverInk: '#e1d3a6', paper: '#182224', ink: '#e1ecea', accent: '#6cd0cb', gilt: '#d0b672' },
  },
} satisfies Record<string, ThemeDef>;

export type ThemeId = keyof typeof THEMES;
export const THEME_IDS = Object.keys(THEMES) as ThemeId[];

/* ------------------------------------------------------------------ */
/* Fonts                                                               */
/* ------------------------------------------------------------------ */

export const FONTS = {
  fraunces: { label: 'Fraunces', stack: "'Fraunces Variable', 'Fraunces', Georgia, serif" },
  cormorant: { label: 'Cormorant Garamond', stack: "'Cormorant Garamond Variable', 'Cormorant Garamond', Garamond, serif" },
  playfair: { label: 'Playfair Display', stack: "'Playfair Display Variable', 'Playfair Display', Georgia, serif" },
  literata: { label: 'Literata', stack: "'Literata Variable', 'Literata', Georgia, serif" },
  garamond: { label: 'EB Garamond', stack: "'EB Garamond Variable', 'EB Garamond', Garamond, serif" },
  sourceSerif: { label: 'Source Serif', stack: "'Source Serif 4 Variable', 'Source Serif 4', Georgia, serif" },
  inter: { label: 'Inter (sans-serif)', stack: "'Inter Variable', 'Inter', system-ui, sans-serif" },
  atkinson: { label: 'Atkinson Hyperlegible', stack: "'Atkinson Hyperlegible', system-ui, sans-serif" },
  systemSerif: { label: 'System serif', stack: "ui-serif, Georgia, 'Times New Roman', serif" },
  systemSans: { label: 'System sans-serif', stack: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif" },
} as const;

export type FontId = keyof typeof FONTS;
export const FONT_IDS = Object.keys(FONTS) as FontId[];

/* ------------------------------------------------------------------ */
/* Settings                                                            */
/* ------------------------------------------------------------------ */

export type Mode = 'system' | 'light' | 'dark';
export type PageEdge = 'plain' | 'gilded' | 'sprayed';
export type PaperTexture = 'none' | 'grain' | 'laid' | 'ruled' | 'dotted';
export type CoverTexture = 'cloth' | 'linen' | 'leather' | 'smooth';
export type CornerStyle = 'square' | 'rounded' | 'dogear';
export type RuleStyle = 'solid' | 'double' | 'dashed' | 'dotted' | 'fade';
export type RuleAlign = 'center' | 'left';
export type RuleColor = 'ink' | 'accent' | 'gilt';
export type HeadingCase = 'smallcaps' | 'uppercase' | 'normal';
export type NavPosition = 'fore-edge' | 'spine';
export type SpreadMode = 'auto' | 'off';
export type Density = 'compact' | 'cozy' | 'roomy';
export type PageTurn = 'turn' | 'slide' | 'fade' | 'none';
export type PageWidth = 'narrow' | 'standard' | 'wide' | 'full';
export type LibraryView = 'covers' | 'list' | 'spines';
export type LibrarySort = 'updated' | 'added' | 'title' | 'author' | 'rating' | 'finished' | 'pages';
export type CoverSource = 'online' | 'generated';
export type ProgressDisplay = 'page' | 'percent';
export type RatingStyle = 'stars' | 'hearts' | 'numeric';
export type ChartColors = 'validated' | 'accent';

export interface Settings {
  theme: ThemeId;
  mode: Mode;
  custom: { light: Partial<Palette>; dark: Partial<Palette> };
  headingFont: FontId;
  bodyFont: FontId;
  uiFont: FontId;
  fontScale: number;
  lineHeight: number;
  pageLayers: number;
  layerOffset: number;
  pageEdge: PageEdge;
  paperTexture: PaperTexture;
  coverTexture: CoverTexture;
  shadowDepth: number;
  cornerStyle: CornerStyle;
  panelLayers: number;
  showRibbon: boolean;
  showFolio: boolean;
  showRunningHead: boolean;
  ruleStyle: RuleStyle;
  ruleWeight: number;
  ruleOpacity: number;
  ruleAlign: RuleAlign;
  ruleColor: RuleColor;
  ruleOrnament: boolean;
  headingCase: HeadingCase;
  navPosition: NavPosition;
  spreadMode: SpreadMode;
  density: Density;
  pageTurn: PageTurn;
  pageWidth: PageWidth;
  libraryView: LibraryView;
  librarySort: LibrarySort;
  coverSource: CoverSource;
  progressDisplay: ProgressDisplay;
  autoFinishPrompt: boolean;
  defaultFormat: BookFormat;
  dateFormat: DateFormat;
  weekStart: 0 | 1;
  yearlyBookGoal: number;
  yearlyPageGoal: number;
  ratingStyle: RatingStyle;
  halfStars: boolean;
  chartColors: ChartColors;
  hideSpoilers: boolean;
  onlineLookup: boolean;
  searchLanguage: string;
  searchPageSize: number;
}

export const DEFAULT_SETTINGS: Settings = {
  theme: 'oxblood',
  mode: 'system',
  custom: { light: {}, dark: {} },
  headingFont: 'fraunces',
  bodyFont: 'literata',
  uiFont: 'inter',
  fontScale: 1,
  lineHeight: 1.55,
  pageLayers: 4,
  layerOffset: 3,
  pageEdge: 'plain',
  paperTexture: 'grain',
  coverTexture: 'cloth',
  shadowDepth: 0.6,
  cornerStyle: 'rounded',
  panelLayers: 2,
  showRibbon: true,
  showFolio: true,
  showRunningHead: true,
  ruleStyle: 'solid',
  ruleWeight: 1,
  ruleOpacity: 0.24,
  ruleAlign: 'center',
  ruleColor: 'ink',
  ruleOrnament: true,
  headingCase: 'smallcaps',
  navPosition: 'fore-edge',
  spreadMode: 'auto',
  density: 'cozy',
  pageTurn: 'turn',
  pageWidth: 'standard',
  libraryView: 'covers',
  librarySort: 'updated',
  coverSource: 'online',
  progressDisplay: 'page',
  autoFinishPrompt: true,
  defaultFormat: 'paperback',
  dateFormat: 'medium',
  weekStart: 1,
  yearlyBookGoal: 24,
  yearlyPageGoal: 8000,
  ratingStyle: 'stars',
  halfStars: true,
  chartColors: 'validated',
  hideSpoilers: true,
  onlineLookup: true,
  searchLanguage: '',
  searchPageSize: 24,
};

/** Allowed values for every enum-like setting (used to sanitise stored/imported data). */
export const SETTING_CHOICES = {
  theme: THEME_IDS,
  mode: ['system', 'light', 'dark'],
  headingFont: FONT_IDS,
  bodyFont: FONT_IDS,
  uiFont: FONT_IDS,
  pageEdge: ['plain', 'gilded', 'sprayed'],
  paperTexture: ['none', 'grain', 'laid', 'ruled', 'dotted'],
  coverTexture: ['cloth', 'linen', 'leather', 'smooth'],
  cornerStyle: ['square', 'rounded', 'dogear'],
  ruleStyle: ['solid', 'double', 'dashed', 'dotted', 'fade'],
  ruleAlign: ['center', 'left'],
  ruleColor: ['ink', 'accent', 'gilt'],
  headingCase: ['smallcaps', 'uppercase', 'normal'],
  navPosition: ['fore-edge', 'spine'],
  spreadMode: ['auto', 'off'],
  density: ['compact', 'cozy', 'roomy'],
  pageTurn: ['turn', 'slide', 'fade', 'none'],
  pageWidth: ['narrow', 'standard', 'wide', 'full'],
  libraryView: ['covers', 'list', 'spines'],
  librarySort: ['updated', 'added', 'title', 'author', 'rating', 'finished', 'pages'],
  coverSource: ['online', 'generated'],
  progressDisplay: ['page', 'percent'],
  defaultFormat: ['paperback', 'hardcover', 'ebook', 'audiobook', 'other'],
  dateFormat: ['medium', 'long', 'us', 'iso', 'dmy', 'mdy'],
  ratingStyle: ['stars', 'hearts', 'numeric'],
  chartColors: ['validated', 'accent'],
} as const satisfies Partial<Record<keyof Settings, readonly string[]>>;

const NUMBER_RANGES: Partial<Record<keyof Settings, [number, number]>> = {
  fontScale: [0.8, 1.35],
  lineHeight: [1.3, 2],
  pageLayers: [0, 7],
  layerOffset: [1, 6],
  shadowDepth: [0, 1],
  panelLayers: [0, 3],
  ruleWeight: [1, 4],
  ruleOpacity: [0.06, 0.7],
  yearlyBookGoal: [0, 1000],
  yearlyPageGoal: [0, 500000],
  searchPageSize: [8, 60],
};

/** Merge untrusted settings onto the defaults, keeping only valid values. */
export function sanitizeSettings(input: unknown): Settings {
  const out: Settings = structuredClone(DEFAULT_SETTINGS);
  if (!input || typeof input !== 'object') return out;
  const src = input as Record<string, unknown>;
  for (const key of Object.keys(DEFAULT_SETTINGS) as (keyof Settings)[]) {
    const v = src[key];
    if (v === undefined) continue;
    const choices = (SETTING_CHOICES as Partial<Record<keyof Settings, readonly string[]>>)[key];
    const range = NUMBER_RANGES[key];
    const def = DEFAULT_SETTINGS[key];
    if (choices) {
      if (typeof v === 'string' && choices.includes(v)) (out as unknown as Record<string, unknown>)[key] = v;
    } else if (range) {
      if (typeof v === 'number' && Number.isFinite(v)) (out as unknown as Record<string, unknown>)[key] = clamp(v, range[0], range[1]);
    } else if (typeof def === 'boolean') {
      if (typeof v === 'boolean') (out as unknown as Record<string, unknown>)[key] = v;
    } else if (key === 'weekStart') {
      if (v === 0 || v === 1) out.weekStart = v;
    } else if (key === 'searchLanguage') {
      if (typeof v === 'string' && /^[a-z]{0,3}$/.test(v)) out.searchLanguage = v;
    } else if (key === 'custom') {
      const c = v as Record<string, unknown>;
      for (const mode of ['light', 'dark'] as const) {
        const pal = (c?.[mode] ?? {}) as Record<string, unknown>;
        for (const k of PALETTE_KEYS) if (isHexColor(pal[k])) out.custom[mode][k] = pal[k] as string;
      }
    }
  }
  return out;
}

/* ------------------------------------------------------------------ */
/* Theme → CSS variables                                               */
/* ------------------------------------------------------------------ */

export function resolvePalette(settings: Settings, mode: 'light' | 'dark'): Palette {
  return { ...THEMES[settings.theme][mode], ...settings.custom[mode] };
}

function rgba(hex: string, a: number) {
  const [r, g, b] = hexToRgb(hex);
  return `rgba(${r}, ${g}, ${b}, ${Math.round(a * 1000) / 1000})`;
}

/** Readable text colour for an arbitrary background. */
export function inkOn(bg: string, light = '#ffffff', dark = '#161311'): string {
  return contrastRatio(bg, light) >= contrastRatio(bg, dark) ? light : dark;
}

/** Box-shadow describing a stack of paper layers below an element. */
export function stackShadow(opts: {
  layers: number;
  offset: number;
  fills: (i: number) => string;
  edge: (i: number) => string;
  drop: string;
  /** -1 fans the layers out to the left (left-hand page of a spread). */
  direction?: 1 | -1;
}): string {
  const parts: string[] = [];
  const dir = opts.direction ?? 1;
  for (let i = 1; i <= opts.layers; i++) {
    const o = i * opts.offset;
    parts.push(`${dir * o}px ${o}px 0 -1px ${opts.fills(i)}`, `${dir * o}px ${o}px 0 0 ${opts.edge(i)}`);
  }
  parts.push(opts.drop);
  return parts.join(', ');
}

export function themeVariables(settings: Settings, mode: 'light' | 'dark'): Record<string, string> {
  const p = resolvePalette(settings, mode);
  const dark = mode === 'dark';
  const paper2 = mixHex(p.paper, p.ink, dark ? 0.07 : 0.045);
  const paper3 = mixHex(p.paper, p.ink, dark ? 0.12 : 0.085);
  const edge = mixHex(p.paper, p.ink, dark ? 0.3 : 0.24);
  const leaf = dark ? mixHex(p.paper, '#ffffff', 0.035) : mixHex(p.paper, '#ffffff', 0.55);
  const ruleBase = settings.ruleColor === 'accent' ? p.accent : settings.ruleColor === 'gilt' ? p.gilt : p.ink;
  const depth = settings.shadowDepth;

  let fill = (i: number) => (i % 2 ? paper2 : paper3);
  let edgeFn = (i: number) => mixHex(edge, p.ink, i * 0.03);
  if (settings.pageEdge === 'gilded') {
    fill = (i) => mixHex(p.gilt, p.paper, i % 2 ? 0.45 : 0.3);
    edgeFn = () => mixHex(p.gilt, p.ink, 0.3);
  } else if (settings.pageEdge === 'sprayed') {
    fill = (i) => mixHex(p.accent, p.paper, i % 2 ? 0.35 : 0.22);
    edgeFn = () => mixHex(p.accent, p.ink, 0.25);
  }

  const stackDepth = settings.pageLayers * settings.layerOffset;
  const dropShadow = (dir: 1 | -1) => `${dir * (stackDepth + 2)}px ${stackDepth + 6}px ${18 + 10 * depth}px ${rgba('#000000', 0.12 + 0.3 * depth)}`;
  const pageStack = stackShadow({ layers: settings.pageLayers, offset: settings.layerOffset, fills: fill, edge: edgeFn, drop: dropShadow(1) });
  const pageStackLeft = stackShadow({ layers: settings.pageLayers, offset: settings.layerOffset, fills: fill, edge: edgeFn, drop: dropShadow(-1), direction: -1 });
  const pageStackCompact = stackShadow({
    layers: Math.min(settings.pageLayers, 2),
    offset: Math.min(settings.layerOffset, 2.5),
    fills: fill,
    edge: edgeFn,
    drop: `0 4px 10px ${rgba('#000000', 0.1 + 0.2 * depth)}`,
  });
  const panelStack = stackShadow({
    layers: settings.panelLayers,
    offset: 2.5,
    fills: (i) => (i % 2 ? paper2 : paper3),
    edge: () => edge,
    drop: `0 ${1 + 2 * depth}px ${4 + 8 * depth}px ${rgba('#000000', 0.04 + 0.1 * depth)}`,
  });

  const scale = settings.fontScale;
  const density = settings.density === 'compact' ? 0.78 : settings.density === 'roomy' ? 1.25 : 1;

  return {
    '--desk': p.desk,
    '--cover': p.cover,
    '--cover-ink': p.coverInk,
    '--cover-shade': mixHex(p.cover, '#000000', 0.38),
    '--cover-light': mixHex(p.cover, '#ffffff', 0.14),
    '--paper': p.paper,
    '--paper-2': paper2,
    '--paper-3': paper3,
    '--edge': edge,
    '--leaf': leaf,
    '--leaf-2': mixHex(leaf, p.ink, dark ? 0.05 : 0.03),
    '--ink': p.ink,
    '--ink-soft': mixHex(p.ink, p.paper, 0.3),
    '--ink-faint': mixHex(p.ink, p.paper, 0.5),
    '--accent': p.accent,
    '--accent-ink': inkOn(p.accent),
    '--accent-soft': mixHex(p.accent, p.paper, 0.82),
    '--accent-strong': mixHex(p.accent, p.ink, 0.25),
    '--gilt': p.gilt,
    '--rule': rgba(ruleBase, settings.ruleOpacity),
    '--rule-strong': rgba(ruleBase, Math.min(1, settings.ruleOpacity * 2.2)),
    '--rule-weight': `${settings.ruleWeight}px`,
    '--grid': rgba(p.ink, dark ? 0.12 : 0.1),
    '--focus': mixHex(p.accent, p.ink, 0.1),
    '--page-stack': pageStack,
    '--page-stack-left': pageStackLeft,
    '--page-stack-compact': pageStackCompact,
    '--panel-stack': panelStack,
    '--shadow-soft': `0 ${2 + 6 * depth}px ${10 + 20 * depth}px ${rgba('#000000', 0.08 + 0.22 * depth)}`,
    '--cover-shadow': `0 ${6 + 18 * depth}px ${20 + 40 * depth}px ${rgba('#000000', 0.18 + 0.35 * depth)}`,
    '--font-heading': FONTS[settings.headingFont].stack,
    '--font-body': FONTS[settings.bodyFont].stack,
    '--font-ui': FONTS[settings.uiFont].stack,
    '--font-scale': String(scale),
    '--line-height': String(settings.lineHeight),
    '--space': `${density}`,
    '--layer-offset': `${settings.layerOffset}px`,
    '--stack-depth': `${settings.pageLayers * settings.layerOffset}px`,
    '--status-want': dark ? '#8fb3e6' : '#2f5b9a',
    '--status-reading': dark ? '#e3b45c' : '#9a6412',
    '--status-finished': dark ? '#7cc49a' : '#2c7a4b',
    '--status-dnf': dark ? '#e08a8a' : '#a33a3a',
    'color-scheme': mode,
  };
}

export function themeAttributes(settings: Settings): Record<string, string> {
  return {
    'data-texture': settings.paperTexture,
    'data-cover-texture': settings.coverTexture,
    'data-corners': settings.cornerStyle,
    'data-rule-style': settings.ruleStyle,
    'data-rule-align': settings.ruleAlign,
    'data-rule-ornament': settings.ruleOrnament ? 'on' : 'off',
    'data-heading-case': settings.headingCase,
    'data-nav': settings.navPosition,
    'data-density': settings.density,
    'data-edge': settings.pageEdge,
    'data-width': settings.pageWidth,
    'data-turn': settings.pageTurn,
  };
}
