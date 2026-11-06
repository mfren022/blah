import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useEntries, useSettings, useStore } from '../data/store';
import { Pages, useMode } from '../components/Shell';
import { Rule, Leaf, Seg, Switch, RangeField, SelectField, Dialog } from '../components/ui';
import { Icon } from '../components/Icon';
import { useToast } from '../components/Toasts';
import {
  FONTS,
  FONT_IDS,
  PALETTE_KEYS,
  PALETTE_LABELS,
  THEMES,
  THEME_IDS,
  resolvePalette,
  type Settings,
  type ThemeId,
  type FontId,
} from '../data/settings';
import { FORMATS } from '../lib/types';
import { FORMAT_LABELS } from '../stats/engine';
import { contrastRatio, downloadFile, formatNumber, plural, uid } from '../lib/util';
import { formatDate, today, type DateFormat } from '../lib/dates';
import { buildSampleLibrary, SAMPLE_SHELVES } from '../data/sample';
import { STORAGE_KEY } from '../data/model';
import { importReadingCsv } from '../data/importCsv';
import { clearApiCache } from '../api/openlibrary';
import { SHELF_COLORS } from './BookPage';
import { Stars } from '../components/ui';

const SAMPLE_LINE = 'Quietly folding a waxed map, the jovial cartographer bids you good night.';

export function SettingsPage({ section }: { section?: string }) {
  const settings = useSettings();
  const { dispatch } = useStore();
  const mode = useMode();
  const set = (patch: Partial<Settings>) => dispatch({ type: 'updateSettings', patch });

  useEffect(() => {
    if (section) document.getElementById(`settings-${section}`)?.scrollIntoView({ behavior: 'smooth' });
  }, [section]);

  const verso = (
    <>
      <Rule level="page">Settings</Rule>
      <p className="page-intro">Everything here changes the book in front of you as you go.</p>
      <div className="stack settings">
        <ThemeSection settings={settings} set={set} mode={mode} />
        <ColourSection settings={settings} set={set} mode={mode} />
        <TypeSection settings={settings} set={set} />
        <LayerSection settings={settings} set={set} />
        <RuleSection settings={settings} set={set} />
        <LayoutSection settings={settings} set={set} />
      </div>
    </>
  );

  const recto = (
    <div className="stack settings">
      <ReadingSection settings={settings} set={set} />
      <GoalsSection settings={settings} set={set} />
      <ShelvesSection />
      <LookupSection settings={settings} set={set} />
      <DataSection />
      <Leaf title="About">
        <p className="small ui" style={{ marginTop: 0 }}>
          Marginalia keeps your library in this browser only. Book details, editions, covers and community ratings come from{' '}
          <a href="https://openlibrary.org" target="_blank" rel="noreferrer">
            Open Library
          </a>
          , an open, editable library catalogue run by the Internet Archive.
        </p>
        <button type="button" className="btn btn--sm btn--ghost" onClick={() => dispatch({ type: 'resetSettings' })}>
          <Icon name="refresh" /> Restore default settings
        </button>
      </Leaf>
    </div>
  );

  return <Pages title="Settings" tab="settings" verso={verso} recto={recto} />;
}

type SectionProps = { settings: Settings; set: (p: Partial<Settings>) => void; mode?: 'light' | 'dark' };

/* ------------------------------------------------------------------ */
/* Appearance                                                          */
/* ------------------------------------------------------------------ */

function ThemeSection({ settings, set, mode }: SectionProps) {
  const toast = useToast();
  const hasCustom = Object.keys(settings.custom.light).length + Object.keys(settings.custom.dark).length > 0;
  return (
    <Leaf title="Binding" sub="Theme presets" id="settings-theme">
      <div className="theme-grid" role="radiogroup" aria-label="Theme">
        {THEME_IDS.map((id) => {
          const p = THEMES[id][mode ?? 'light'];
          return (
            <button
              key={id}
              type="button"
              role="radio"
              aria-checked={settings.theme === id}
              className="theme-card"
              onClick={() => {
                set({ theme: id as ThemeId, custom: { light: {}, dark: {} } });
                if (hasCustom && id !== settings.theme) toast({ message: 'Custom colours were cleared for the new binding' });
              }}
              style={{ '--t-desk': p.desk, '--t-cover': p.cover, '--t-paper': p.paper, '--t-ink': p.ink, '--t-accent': p.accent, '--t-gilt': p.gilt, '--t-cover-ink': p.coverInk } as CSSProperties}
            >
              <span className="theme-card__book" aria-hidden>
                <span className="theme-card__page" />
                <span className="theme-card__ribbon" />
                <span className="theme-card__title">Aa</span>
              </span>
              <span className="theme-card__label">{THEMES[id].label}</span>
              <span className="theme-card__blurb">{THEMES[id].blurb}</span>
            </button>
          );
        })}
      </div>
      <div className="setting-row">
        <span className="field__label">Light or dark</span>
        <Seg
          label="Colour mode"
          size="sm"
          value={settings.mode}
          onChange={(m) => set({ mode: m })}
          options={[
            { value: 'system', label: 'Match device' },
            { value: 'light', label: 'Light', icon: 'sun' },
            { value: 'dark', label: 'Dark', icon: 'moon' },
          ]}
        />
      </div>
    </Leaf>
  );
}

function ColourSection({ settings, set, mode = 'light' }: SectionProps) {
  const pal = resolvePalette(settings, mode);
  const custom = settings.custom[mode];
  const contrast = contrastRatio(pal.ink, pal.paper);
  const setColour = (key: string, value: string | undefined) => {
    const next = { ...custom } as Record<string, string>;
    if (value) next[key] = value;
    else delete next[key];
    set({ custom: { ...settings.custom, [mode]: next } });
  };
  return (
    <Leaf title="Colours" sub={`Your own colours for ${mode} mode`} id="settings-colours">
      <div className="colour-grid">
        {PALETTE_KEYS.map((k) => (
          <label key={k} className="colour-field">
            <input type="color" value={pal[k]} onChange={(e) => setColour(k, e.target.value)} />
            <span className="colour-field__text">
              <span>{PALETTE_LABELS[k]}</span>
              <span className="tiny faint nums">{pal[k]}</span>
            </span>
            {custom[k] && (
              <button type="button" className="btn btn--ghost btn--sm btn--icon" aria-label={`Reset ${PALETTE_LABELS[k]}`} onClick={() => setColour(k, undefined)}>
                <Icon name="refresh" />
              </button>
            )}
          </label>
        ))}
      </div>
      <div className="row row--between" style={{ marginTop: 12 }}>
        <span className={`small ui ${contrast < 4.5 ? 'warn' : 'muted'}`}>
          {contrast < 4.5 ? <Icon name="info" /> : <Icon name="check" />} Text contrast {contrast.toFixed(1)}:1 {contrast < 4.5 ? '— hard to read; pick a darker ink or lighter paper' : ''}
        </span>
        {Object.keys(custom).length > 0 && (
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => set({ custom: { ...settings.custom, [mode]: {} } })}>
            Reset colours
          </button>
        )}
      </div>
    </Leaf>
  );
}

function FontSelect({ label, value, onChange }: { label: string; value: FontId; onChange: (f: FontId) => void }) {
  return (
    <div className="field">
      <SelectField label={label} value={value} onChange={onChange} options={FONT_IDS.map((f) => ({ value: f, label: FONTS[f].label }))} />
      <span className="font-sample" style={{ fontFamily: FONTS[value].stack }}>
        {SAMPLE_LINE}
      </span>
    </div>
  );
}

function TypeSection({ settings, set }: SectionProps) {
  return (
    <Leaf title="Type" id="settings-type">
      <div className="stack stack--tight">
        <FontSelect label="Headings" value={settings.headingFont} onChange={(f) => set({ headingFont: f })} />
        <FontSelect label="Reading text (synopses, reviews, notes)" value={settings.bodyFont} onChange={(f) => set({ bodyFont: f })} />
        <FontSelect label="Interface (buttons, labels, figures)" value={settings.uiFont} onChange={(f) => set({ uiFont: f })} />
        <div className="form-grid">
          <RangeField label="Text size" value={settings.fontScale} min={0.8} max={1.35} step={0.05} onChange={(v) => set({ fontScale: v })} format={(v) => `${Math.round(v * 100)}%`} />
          <RangeField label="Line spacing" value={settings.lineHeight} min={1.3} max={2} step={0.05} onChange={(v) => set({ lineHeight: v })} format={(v) => v.toFixed(2)} />
        </div>
      </div>
    </Leaf>
  );
}

function LayerSection({ settings, set }: SectionProps) {
  return (
    <Leaf title="Pages & layers" sub="How the book is built" id="settings-layers">
      <div className="form-grid">
        <RangeField label="Leaves under the page" value={settings.pageLayers} min={0} max={7} onChange={(v) => set({ pageLayers: v })} format={(v) => (v ? String(v) : 'none')} />
        <RangeField label="Leaf spacing" value={settings.layerOffset} min={1} max={6} step={0.5} onChange={(v) => set({ layerOffset: v })} format={(v) => `${v}px`} />
        <RangeField label="Leaves under panels" value={settings.panelLayers} min={0} max={3} onChange={(v) => set({ panelLayers: v })} format={(v) => (v ? String(v) : 'none')} />
        <RangeField label="Shadow depth" value={settings.shadowDepth} min={0} max={1} step={0.05} onChange={(v) => set({ shadowDepth: v })} format={(v) => `${Math.round(v * 100)}%`} />
        <SelectField
          label="Page edges"
          value={settings.pageEdge}
          onChange={(v) => set({ pageEdge: v })}
          options={[
            { value: 'plain', label: 'Plain' },
            { value: 'gilded', label: 'Gilded (gold)' },
            { value: 'sprayed', label: 'Sprayed (ribbon colour)' },
          ]}
        />
        <SelectField
          label="Paper"
          value={settings.paperTexture}
          onChange={(v) => set({ paperTexture: v })}
          options={[
            { value: 'none', label: 'Smooth' },
            { value: 'grain', label: 'Grain' },
            { value: 'laid', label: 'Laid lines' },
            { value: 'ruled', label: 'Ruled notebook' },
            { value: 'dotted', label: 'Dot grid' },
          ]}
        />
        <SelectField
          label="Cover"
          value={settings.coverTexture}
          onChange={(v) => set({ coverTexture: v })}
          options={[
            { value: 'cloth', label: 'Buckram cloth' },
            { value: 'linen', label: 'Fine linen' },
            { value: 'leather', label: 'Leather' },
            { value: 'smooth', label: 'Smooth board' },
          ]}
        />
        <SelectField
          label="Corners"
          value={settings.cornerStyle}
          onChange={(v) => set({ cornerStyle: v })}
          options={[
            { value: 'rounded', label: 'Rounded' },
            { value: 'square', label: 'Square' },
            { value: 'dogear', label: 'Dog-eared panels' },
          ]}
        />
      </div>
      <div className="row switches">
        <Switch checked={settings.showRibbon} onChange={(v) => set({ showRibbon: v })} label="Ribbon bookmark" />
        <Switch checked={settings.showRunningHead} onChange={(v) => set({ showRunningHead: v })} label="Running heads" />
        <Switch checked={settings.showFolio} onChange={(v) => set({ showFolio: v })} label="Page numbers" />
      </div>
    </Leaf>
  );
}

function RuleSection({ settings, set }: SectionProps) {
  return (
    <Leaf title="Headings & rules" sub="The pale lines through every heading" id="settings-rules">
      <div className="rule-preview" aria-hidden>
        <Rule level="section" as="div">
          Chapter One
        </Rule>
        <Rule level="minor" as="div">
          A section of a panel
        </Rule>
      </div>
      <div className="form-grid">
        <SelectField
          label="Line style"
          value={settings.ruleStyle}
          onChange={(v) => set({ ruleStyle: v })}
          options={[
            { value: 'solid', label: 'Solid' },
            { value: 'double', label: 'Double' },
            { value: 'dashed', label: 'Dashed' },
            { value: 'dotted', label: 'Dotted' },
            { value: 'fade', label: 'Fading' },
          ]}
        />
        <SelectField
          label="Line colour"
          value={settings.ruleColor}
          onChange={(v) => set({ ruleColor: v })}
          options={[
            { value: 'ink', label: 'Ink' },
            { value: 'accent', label: 'Ribbon colour' },
            { value: 'gilt', label: 'Gilt' },
          ]}
        />
        <RangeField label="Line weight" value={settings.ruleWeight} min={1} max={4} step={0.5} onChange={(v) => set({ ruleWeight: v })} format={(v) => `${v}px`} />
        <RangeField label="Paleness" value={settings.ruleOpacity} min={0.06} max={0.7} step={0.02} onChange={(v) => set({ ruleOpacity: v })} format={(v) => `${Math.round((1 - v) * 100)}%`} />
        <SelectField
          label="Heading position"
          value={settings.ruleAlign}
          onChange={(v) => set({ ruleAlign: v })}
          options={[
            { value: 'center', label: 'Centred between lines' },
            { value: 'left', label: 'Left, line trailing' },
          ]}
        />
        <SelectField
          label="Lettering"
          value={settings.headingCase}
          onChange={(v) => set({ headingCase: v })}
          options={[
            { value: 'smallcaps', label: 'Small capitals' },
            { value: 'uppercase', label: 'Capitals' },
            { value: 'normal', label: 'As written' },
          ]}
        />
      </div>
      <div className="row switches">
        <Switch checked={settings.ruleOrnament} onChange={(v) => set({ ruleOrnament: v })} label="Diamond ornaments" />
      </div>
    </Leaf>
  );
}

function LayoutSection({ settings, set }: SectionProps) {
  return (
    <Leaf title="Layout & motion" id="settings-layout">
      <div className="form-grid">
        <SelectField
          label="Navigation"
          value={settings.navPosition}
          onChange={(v) => set({ navPosition: v })}
          options={[
            { value: 'fore-edge', label: 'Index tabs on the fore-edge' },
            { value: 'spine', label: 'Labels on the spine' },
          ]}
        />
        <SelectField
          label="Wide screens"
          value={settings.spreadMode}
          onChange={(v) => set({ spreadMode: v })}
          options={[
            { value: 'auto', label: 'Open as a two-page spread' },
            { value: 'off', label: 'Always one page' },
          ]}
        />
        <SelectField
          label="Book size"
          value={settings.pageWidth}
          onChange={(v) => set({ pageWidth: v })}
          options={[
            { value: 'narrow', label: 'Octavo (narrow)' },
            { value: 'standard', label: 'Quarto (standard)' },
            { value: 'wide', label: 'Folio (wide)' },
            { value: 'full', label: 'Fill the screen' },
          ]}
        />
        <SelectField
          label="Spacing"
          value={settings.density}
          onChange={(v) => set({ density: v })}
          options={[
            { value: 'compact', label: 'Compact' },
            { value: 'cozy', label: 'Comfortable' },
            { value: 'roomy', label: 'Roomy' },
          ]}
        />
        <SelectField
          label="Turning pages"
          value={settings.pageTurn}
          onChange={(v) => set({ pageTurn: v })}
          options={[
            { value: 'turn', label: 'Page turn' },
            { value: 'slide', label: 'Slide' },
            { value: 'fade', label: 'Fade' },
            { value: 'none', label: 'Instant' },
          ]}
          hint="Animations are skipped when your device asks for reduced motion."
        />
      </div>
    </Leaf>
  );
}

/* ------------------------------------------------------------------ */
/* Reading                                                             */
/* ------------------------------------------------------------------ */

function ReadingSection({ settings, set }: SectionProps) {
  const fmts: DateFormat[] = ['medium', 'long', 'us', 'iso', 'dmy', 'mdy'];
  return (
    <Leaf title="Reading" id="settings-reading">
      <div className="form-grid">
        <SelectField
          label="Show progress as"
          value={settings.progressDisplay}
          onChange={(v) => set({ progressDisplay: v })}
          options={[
            { value: 'page', label: 'Page numbers' },
            { value: 'percent', label: 'Percentages' },
          ]}
        />
        <SelectField label="Usual format" value={settings.defaultFormat} onChange={(v) => set({ defaultFormat: v })} options={FORMATS.map((f) => ({ value: f, label: FORMAT_LABELS[f] }))} />
        <SelectField label="Dates" value={settings.dateFormat} onChange={(v) => set({ dateFormat: v })} options={fmts.map((f) => ({ value: f, label: formatDate(today(), f) }))} />
        <SelectField
          label="Weeks start on"
          value={String(settings.weekStart) as '0' | '1'}
          onChange={(v) => set({ weekStart: v === '0' ? 0 : 1 })}
          options={[
            { value: '1', label: 'Monday' },
            { value: '0', label: 'Sunday' },
          ]}
        />
        <SelectField
          label="Ratings shown as"
          value={settings.ratingStyle}
          onChange={(v) => set({ ratingStyle: v })}
          options={[
            { value: 'stars', label: 'Stars' },
            { value: 'hearts', label: 'Hearts' },
            { value: 'numeric', label: 'Numbers' },
          ]}
        />
        <div className="field">
          <span className="field__label">Preview</span>
          <span style={{ paddingTop: 6 }}>
            <Stars value={3.5} size="1.1rem" />
          </span>
        </div>
      </div>
      <div className="row switches">
        <Switch checked={settings.halfStars} onChange={(v) => set({ halfStars: v })} label="Half-star ratings" />
        <Switch checked={settings.autoFinishPrompt} onChange={(v) => set({ autoFinishPrompt: v })} label="Offer to finish on the last page" />
        <Switch checked={settings.hideSpoilers} onChange={(v) => set({ hideSpoilers: v })} label="Blur reviews marked as spoilers" />
      </div>
    </Leaf>
  );
}

function GoalsSection({ settings, set }: SectionProps) {
  return (
    <Leaf title="Goals" sub={`For ${today().slice(0, 4)}`} id="settings-goals">
      <div className="form-grid">
        <label className="field">
          <span>Books a year</span>
          <input className="input" type="number" min={0} max={1000} value={settings.yearlyBookGoal} onChange={(e) => set({ yearlyBookGoal: Math.max(0, Number(e.target.value) || 0) })} />
        </label>
        <label className="field">
          <span>Pages a year</span>
          <input className="input" type="number" min={0} step={500} value={settings.yearlyPageGoal} onChange={(e) => set({ yearlyPageGoal: Math.max(0, Number(e.target.value) || 0) })} />
        </label>
      </div>
      <p className="tiny faint ui" style={{ margin: '10px 0 0' }}>
        That is about {settings.yearlyBookGoal ? `${formatNumber(settings.yearlyBookGoal / 12, 1)} books a month` : 'no book goal'}
        {settings.yearlyPageGoal ? ` and ${formatNumber(Math.round(settings.yearlyPageGoal / 365))} pages a day` : ''}. Set 0 to hide a goal.
      </p>
    </Leaf>
  );
}

function ShelvesSection() {
  const { state, dispatch } = useStore();
  const entries = useEntries();
  const toast = useToast();
  const [name, setName] = useState('');
  return (
    <Leaf title="Shelves" sub="Your own shelves, alongside the four reading shelves" id="settings-shelves">
      {state.shelves.length === 0 && <p className="small muted ui">No custom shelves yet.</p>}
      <ul className="shelf-list">
        {state.shelves.map((s) => {
          const count = entries.filter((e) => e.shelves.includes(s.id)).length;
          return (
            <li key={s.id}>
              <input type="color" value={s.color} aria-label={`Colour of ${s.name}`} onChange={(e) => dispatch({ type: 'updateShelf', shelfId: s.id, patch: { color: e.target.value } })} />
              <input
                className="input"
                defaultValue={s.name}
                aria-label="Shelf name"
                onBlur={(e) => {
                  const v = e.target.value.trim();
                  if (v && v !== s.name) dispatch({ type: 'updateShelf', shelfId: s.id, patch: { name: v } });
                  else e.target.value = s.name;
                }}
                onKeyDown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).blur()}
              />
              <span className="tiny faint ui nowrap">{plural(count, 'book')}</span>
              <button
                type="button"
                className="btn btn--ghost btn--sm btn--icon"
                aria-label={`Delete shelf ${s.name}`}
                onClick={() => {
                  const before = state;
                  dispatch({ type: 'deleteShelf', shelfId: s.id });
                  toast({
                    message: count ? `Deleted “${s.name}” — its ${plural(count, 'book')} stay in your library` : `Deleted “${s.name}”`,
                    action: { label: 'Undo', run: () => dispatch({ type: 'hydrate', state: before }) },
                  });
                }}
              >
                <Icon name="trash" />
              </button>
            </li>
          );
        })}
      </ul>
      <form
        className="row toc-add"
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) return;
          dispatch({ type: 'createShelf', shelf: { id: `shelf-${uid()}`, name: name.trim(), color: SHELF_COLORS[state.shelves.length % SHELF_COLORS.length], createdAt: today() } });
          setName('');
        }}
      >
        <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="New shelf name" aria-label="New shelf name" />
        <button type="submit" className="btn btn--sm" disabled={!name.trim()}>
          <Icon name="plus" /> Add
        </button>
      </form>
    </Leaf>
  );
}

function LookupSection({ settings, set }: SectionProps) {
  const toast = useToast();
  return (
    <Leaf title="Books & covers" sub="Where book information comes from" id="settings-lookup">
      <div className="stack stack--tight">
        <Switch checked={settings.onlineLookup} onChange={(v) => set({ onlineLookup: v })} label="Look books up on Open Library" hint="Off: browse only your own library and the sample catalogue." />
        <div className="form-grid" style={{ marginTop: 8 }}>
          <SelectField
            label="Covers"
            value={settings.coverSource}
            onChange={(v) => set({ coverSource: v })}
            options={[
              { value: 'online', label: 'Real covers when available' },
              { value: 'generated', label: 'Always cloth bindings' },
            ]}
          />
          <SelectField
            label="Search language"
            value={settings.searchLanguage}
            onChange={(v) => set({ searchLanguage: v })}
            options={[
              { value: '', label: 'Any language' },
              { value: 'eng', label: 'English' },
              { value: 'fre', label: 'French' },
              { value: 'ger', label: 'German' },
              { value: 'spa', label: 'Spanish' },
              { value: 'ita', label: 'Italian' },
              { value: 'por', label: 'Portuguese' },
              { value: 'dut', label: 'Dutch' },
              { value: 'jpn', label: 'Japanese' },
            ]}
          />
          <SelectField
            label="Results per page"
            value={String(settings.searchPageSize)}
            onChange={(v) => set({ searchPageSize: Number(v) })}
            options={['12', '24', '36', '48'].map((v) => ({ value: v, label: v }))}
          />
          <SelectField
            label="Chart colours"
            value={settings.chartColors}
            onChange={(v) => set({ chartColors: v })}
            options={[
              { value: 'validated', label: 'Colour-blind-safe palette' },
              { value: 'accent', label: 'Ribbon colour (single series)' },
            ]}
            hint="Multi-series charts always use the colour-blind-safe palette."
          />
        </div>
        <button
          type="button"
          className="btn btn--ghost btn--sm"
          style={{ alignSelf: 'flex-start' }}
          onClick={() => {
            clearApiCache();
            toast({ message: 'Cleared saved Open Library responses' });
          }}
        >
          <Icon name="refresh" /> Clear cached book data
        </button>
      </div>
    </Leaf>
  );
}

/* ------------------------------------------------------------------ */
/* Data                                                                */
/* ------------------------------------------------------------------ */

function DataSection() {
  const { state, dispatch } = useStore();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const csvRef = useRef<HTMLInputElement>(null);
  const [pending, setPending] = useState<{ kind: 'json' | 'csv'; data: unknown; summary: ReactNode } | null>(null);
  const [eraseOpen, setEraseOpen] = useState(false);
  const [eraseText, setEraseText] = useState('');
  const sampleCount = Object.values(state.library).filter((e) => e.sample).length;
  const total = Object.keys(state.library).length;

  const exportJson = () => {
    const payload = { app: 'marginalia', version: state.version, exportedAt: new Date().toISOString(), state };
    downloadFile(`marginalia-backup-${today()}.json`, JSON.stringify(payload, null, 2));
    // In the Android app the system save dialog confirms instead.
    if (!window.MarginaliaAndroid?.saveFile) toast({ message: `Exported ${plural(total, 'book')}` });
  };

  const readFile = (file: File, kind: 'json' | 'csv') => {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const text = String(reader.result);
        if (kind === 'json') {
          const parsed = JSON.parse(text);
          const data = parsed?.state ?? parsed;
          const n = data?.library ? Object.keys(data.library).length : 0;
          if (!data || typeof data !== 'object' || !data.library) throw new Error('This is not a Marginalia backup.');
          setPending({ kind, data, summary: <>A backup with {plural(n, 'book')}{parsed?.exportedAt ? <>, made {formatDate(String(parsed.exportedAt).slice(0, 10))}</> : null}.</> });
        } else {
          const result = importReadingCsv(text);
          setPending({
            kind,
            data: result,
            summary: (
              <>
                A {result.source === 'goodreads' ? 'Goodreads' : 'StoryGraph'} export with {plural(result.entries.length, 'book')} and {plural(result.shelves.length, 'shelf', 'shelves')}
                {result.skipped ? ` (${result.skipped} rows skipped)` : ''}.
              </>
            ),
          });
        }
      } catch (err) {
        toast({ message: `Could not read that file: ${(err as Error).message}` }, 6000);
      }
    };
    reader.readAsText(file);
  };

  const apply = (mode: 'merge' | 'replace') => {
    if (!pending) return;
    if (pending.kind === 'json') {
      dispatch({ type: 'importState', state: pending.data, mode });
    } else {
      const r = pending.data as ReturnType<typeof importReadingCsv>;
      const incoming = { library: Object.fromEntries(r.entries.map((e) => [e.id, e])), shelves: [...state.shelves, ...r.shelves.filter((s) => !state.shelves.some((x) => x.id === s.id))], settings: state.settings };
      dispatch({ type: 'importState', state: incoming, mode });
    }
    toast({ message: mode === 'merge' ? 'Imported and merged into your library' : 'Library replaced with the import' });
    setPending(null);
  };

  return (
    <Leaf title="Your data" sub={`${plural(total, 'book')} stored in this browser`} id="settings-data">
      <div className="data-actions">
        <button type="button" className="btn btn--sm" onClick={exportJson}>
          <Icon name="download" /> Export backup (.json)
        </button>
        <button type="button" className="btn btn--sm" onClick={() => fileRef.current?.click()}>
          <Icon name="upload" /> Import backup
        </button>
        <button type="button" className="btn btn--sm" onClick={() => csvRef.current?.click()}>
          <Icon name="upload" /> Import Goodreads / StoryGraph CSV
        </button>
        <input ref={fileRef} type="file" accept="application/json,.json" hidden onChange={(e) => (e.target.files?.[0] && readFile(e.target.files[0], 'json'), (e.target.value = ''))} />
        <input ref={csvRef} type="file" accept=".csv,text/csv" hidden onChange={(e) => (e.target.files?.[0] && readFile(e.target.files[0], 'csv'), (e.target.value = ''))} />
      </div>

      <Rule level="minor" as="h3">
        Sample library
      </Rule>
      <p className="small muted ui" style={{ marginTop: 0 }}>
        Twenty-three invented books with a year of made-up reading, for trying things out.
      </p>
      <div className="data-actions">
        {sampleCount === 0 ? (
          <button type="button" className="btn btn--sm" onClick={() => (dispatch({ type: 'loadSample', entries: buildSampleLibrary(), shelves: SAMPLE_SHELVES }), toast({ message: 'Sample library added' }))}>
            <Icon name="sparkle" /> Add sample books
          </button>
        ) : (
          <button type="button" className="btn btn--sm" onClick={() => (dispatch({ type: 'clearSample' }), toast({ message: `Removed ${plural(sampleCount, 'sample book')}` }))}>
            <Icon name="trash" /> Remove {plural(sampleCount, 'sample book')}
          </button>
        )}
      </div>

      <Rule level="minor" as="h3">
        Start over
      </Rule>
      <button type="button" className="btn btn--sm btn--danger" onClick={() => setEraseOpen(true)}>
        <Icon name="trash" /> Erase everything…
      </button>

      <Dialog open={!!pending} onClose={() => setPending(null)} title="Import">
        <p className="ui small" style={{ marginTop: 0 }}>
          {pending?.summary}
        </p>
        <p className="ui small muted">
          <strong>Merge</strong> adds these books and keeps yours (the newer copy of a book wins). <strong>Replace</strong> swaps your whole library for this one.
        </p>
        <div className="row row--end">
          <button type="button" className="btn btn--ghost" onClick={() => setPending(null)}>
            Cancel
          </button>
          <button type="button" className="btn" onClick={() => apply('replace')}>
            Replace
          </button>
          <button type="button" className="btn btn--primary" onClick={() => apply('merge')}>
            Merge
          </button>
        </div>
      </Dialog>

      <Dialog open={eraseOpen} onClose={() => (setEraseOpen(false), setEraseText(''))} title="Erase everything?">
        <p className="ui small" style={{ marginTop: 0 }}>
          This deletes all {plural(total, 'book')}, your shelves, reviews, progress and settings from this browser. Export a backup first if you might want them back.
        </p>
        <label className="field">
          <span>Type ERASE to confirm</span>
          <input className="input" value={eraseText} onChange={(e) => setEraseText(e.target.value)} autoComplete="off" />
        </label>
        <div className="row row--end" style={{ marginTop: 14 }}>
          <button type="button" className="btn btn--ghost" onClick={() => (setEraseOpen(false), setEraseText(''))}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn--primary"
            disabled={eraseText.trim().toUpperCase() !== 'ERASE'}
            onClick={() => {
              dispatch({ type: 'resetAll' });
              try {
                localStorage.removeItem(STORAGE_KEY);
              } catch {
                /* ignore */
              }
              clearApiCache();
              setEraseOpen(false);
              setEraseText('');
              toast({ message: 'Everything was erased' });
            }}
          >
            Erase
          </button>
        </div>
      </Dialog>
    </Leaf>
  );
}
