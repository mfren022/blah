import { createContext, useContext, type ReactNode } from 'react';
import { TABS, navigate, tabOf, useRoute } from '../router';
import { useSettings, useStore } from '../data/store';
import { useMediaQuery } from '../theme';
import { Icon } from './Icon';

const ModeContext = createContext<'light' | 'dark'>('light');
export const useMode = () => useContext(ModeContext);

const ROMAN = ['i', 'ii', 'iii', 'iv', 'v', 'vi', 'vii', 'viii', 'ix', 'x'];

/** Folio (page numbers) for each section: settings is front matter, in roman numerals. */
export function folioFor(tab: string, side: 'single' | 'verso' | 'recto', seed = 0): string {
  if (tab === 'settings') return side === 'verso' ? ROMAN[1] : side === 'recto' ? ROMAN[2] : ROMAN[2];
  const base = { reading: 2, browse: 14, library: 36, stats: 58, book: 80 }[tab] ?? 2;
  const n = base + (seed % 400) * 2;
  return String(side === 'verso' ? n : n + 1);
}

function NavLink({ tab, className }: { tab: (typeof TABS)[number]; className: string }) {
  const route = useRoute();
  const active = tabOf(route) === tab.name;
  const { state } = useStore();
  const reading = tab.name === 'reading' ? Object.values(state.library).filter((e) => e.status === 'reading').length : 0;
  return (
    <a
      href={`#/${tab.name}`}
      className={className}
      aria-current={active ? 'page' : undefined}
      onClick={(e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
        e.preventDefault();
        navigate(`/${tab.name}`);
      }}
      title={reading ? `${tab.title} (${reading})` : tab.title}
    >
      <Icon name={tab.icon} />
      <span className="tab__label">{tab.label}</span>
    </a>
  );
}

export function Shell({ children, mode }: { children: ReactNode; mode: 'light' | 'dark' }) {
  const settings = useSettings();
  return (
    <ModeContext.Provider value={mode}>
      <div className="desk">
        <div className="book">
          <header className="titlebar">
            <a className="titlebar__name" href="#/reading">
              Marginalia
            </a>
            <span className="grow" />
            <a className="btn btn--ghost btn--icon" href="#/browse" aria-label="Search books">
              <Icon name="search" />
            </a>
          </header>

          <aside className="spine" aria-label="Spine">
            <a className="spine__mark" href="#/reading" aria-label="Marginalia — home">
              M
            </a>
            <div className="spine__band" />
            <nav className="spine-nav" aria-label="Sections">
              {settings.navPosition === 'spine' && TABS.map((t) => <NavLink key={t.name} tab={t} className="" />)}
            </nav>
            <div className="spine__fill" />
            <span className="spine__title">Marginalia</span>
            <div className="spine__fill" />
            <div className="spine__band" />
            <div className="spine__band" />
          </aside>

          <div className="block">{children}</div>

          <nav className="fore-edge" aria-label="Sections">
            {TABS.map((t) => (
              <NavLink key={t.name} tab={t} className="tab" />
            ))}
          </nav>
        </div>
      </div>
    </ModeContext.Provider>
  );
}

/** Two-page spreads on wide screens (when enabled), a single page otherwise. */
export function useSpread(): boolean {
  const settings = useSettings();
  const wide = useMediaQuery('(min-width: 1180px)');
  return settings.spreadMode === 'auto' && wide;
}

function Sheet({ side, head, folio, ribbon, children }: { side: 'single' | 'verso' | 'recto'; head: string; folio: string; ribbon?: boolean; children: ReactNode }) {
  const settings = useSettings();
  return (
    <section className={`sheet sheet--${side}`}>
      <div className="sheet__stack" aria-hidden />
      <div className="paper">
        {settings.showRunningHead && (
          <div className="running-head" aria-hidden>
            <span>{head}</span>
          </div>
        )}
        <div className="paper__scroll">{children}</div>
        {settings.showFolio && (
          <div className="folio" aria-hidden>
            {side === 'single' ? `— ${folio} —` : folio}
          </div>
        )}
      </div>
      {ribbon && settings.showRibbon && <div className="ribbon" aria-hidden />}
    </section>
  );
}

/**
 * The content area of a section. Pass `verso` and `recto` to lay the section out as an
 * open book on wide screens; on narrow screens (or with spreads off) they stack on one page.
 */
export function Pages({ title, tab, verso, recto, seed = 0 }: { title: string; tab: string; verso: ReactNode; recto?: ReactNode; seed?: number }) {
  const spread = useSpread() && !!recto;
  if (spread) {
    return (
      <div className="pages pages--spread">
        <Sheet side="verso" head="Marginalia" folio={folioFor(tab, 'verso', seed)}>
          {verso}
        </Sheet>
        <Sheet side="recto" head={title} folio={folioFor(tab, 'recto', seed)} ribbon>
          {recto}
        </Sheet>
      </div>
    );
  }
  return (
    <div className="pages">
      <Sheet side="single" head={title} folio={folioFor(tab, 'single', seed)} ribbon>
        {verso}
        {recto && <div className="section">{recto}</div>}
      </Sheet>
    </div>
  );
}
