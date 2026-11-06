import { useStore } from './data/store';
import { useThemeApplier } from './theme';
import { routeKey, useRoute, type Route } from './router';
import { Shell } from './components/Shell';
import { ToastProvider } from './components/Toasts';
import { Dialog } from './components/ui';
import { Icon } from './components/Icon';
import { ReadingPage } from './pages/ReadingPage';
import { BrowsePage } from './pages/BrowsePage';
import { BookPage } from './pages/BookPage';
import { LibraryPage } from './pages/LibraryPage';
import { StatsPage } from './pages/StatsPage';
import { SettingsPage } from './pages/SettingsPage';
import { buildSampleLibrary, SAMPLE_SHELVES } from './data/sample';

function RouteView({ route }: { route: Route }) {
  switch (route.name) {
    case 'browse':
      return <BrowsePage params={route.params} />;
    case 'book':
      return <BookPage id={route.id} params={route.params} />;
    case 'library':
      return <LibraryPage params={route.params} />;
    case 'stats':
      return <StatsPage />;
    case 'settings':
      return <SettingsPage section={route.section} />;
    default:
      return <ReadingPage />;
  }
}

function Welcome() {
  const { state, dispatch } = useStore();
  const open = !state.onboarded && Object.keys(state.library).length === 0;
  return (
    <Dialog open={open} onClose={() => dispatch({ type: 'setOnboarded' })} title="Welcome to Marginalia">
      <div className="prose stack">
        <p className="dropcap">
          Marginalia is a reading journal laid out like a book. Browse any book on Open Library by title or author, shelve it as Want to Read, Currently
          Reading, Finished or Did Not Finish, log the page you are on, and watch your reading take shape in the statistics.
        </p>
        <p className="small muted ui">Everything you record stays in this browser. Export a backup from Settings whenever you like.</p>
      </div>
      <div className="row row--end" style={{ marginTop: 18 }}>
        <button type="button" className="btn" onClick={() => dispatch({ type: 'loadSample', entries: buildSampleLibrary(), shelves: SAMPLE_SHELVES })}>
          <Icon name="sparkle" /> Explore with sample books
        </button>
        <button type="button" className="btn btn--primary" onClick={() => dispatch({ type: 'setOnboarded' })}>
          Start my own library
        </button>
      </div>
      <p className="tiny faint ui" style={{ marginTop: 12, marginBottom: 0 }}>
        The sample library uses invented books and authors, and can be removed in Settings → Data.
      </p>
    </Dialog>
  );
}

export function App() {
  const { state } = useStore();
  const mode = useThemeApplier(state.settings);
  const route = useRoute();
  return (
    <ToastProvider>
      <Shell mode={mode}>
        <RouteView route={route} key={routeKey(route)} />
      </Shell>
      <Welcome />
    </ToastProvider>
  );
}
