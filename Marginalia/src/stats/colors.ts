import { contrastRatio, mixHex } from '../lib/util';
import { resolvePalette, type Settings } from '../data/settings';
import { CATEGORICAL, HEAT } from './palette';

/** Panel surface the charts sit on, per mode (mirrors themeVariables' --leaf). */
function surface(settings: Settings, mode: 'light' | 'dark'): string {
  const p = resolvePalette(settings, mode);
  return mode === 'dark' ? mixHex(p.paper, '#ffffff', 0.035) : mixHex(p.paper, '#ffffff', 0.55);
}

/**
 * Colour for single-series charts. Defaults to the validated palette's first slot; readers
 * may opt into their theme's ribbon colour, used only while it keeps 3:1 contrast on the page.
 */
export function chartSingleColor(settings: Settings, mode: 'light' | 'dark'): string {
  if (settings.chartColors === 'accent') {
    const accent = resolvePalette(settings, mode).accent;
    if (contrastRatio(accent, surface(settings, mode)) >= 3) return accent;
  }
  return CATEGORICAL[mode][0];
}

/** Four sequential steps (one hue, light → dark on paper; flipped on dark paper). */
export function heatColors(settings: Settings, mode: 'light' | 'dark'): string[] {
  if (settings.chartColors === 'accent') {
    const accent = chartSingleColor(settings, mode);
    if (accent !== CATEGORICAL[mode][0]) {
      const base = surface(settings, mode);
      return [0.3, 0.55, 0.78, 1].map((t) => mixHex(base, accent, t));
    }
  }
  return [...HEAT[mode]];
}
