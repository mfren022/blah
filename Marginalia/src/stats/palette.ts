/**
 * Chart colours. Categorical slots are the validated default palette (fixed order,
 * never cycled); the ordinal ramps were re-stepped and validated against this app's
 * paper surfaces (light end >= 2:1). See README → "Charts".
 */
export const CATEGORICAL = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'],
} as const;

/** Ordinal (ordered tiers, e.g. book length): one hue, light → dark. */
export const ORDINAL = {
  light: ['#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b'],
  dark: ['#1c5cab', '#2a78d6', '#5598e7', '#86b6ef', '#b7d3f6'],
} as const;

/** Sequential heat levels 1–4 (0 = empty cell, drawn in a paper tone). */
export const HEAT = {
  light: ['#b7d3f6', '#6da7ec', '#2a78d6', '#184f95'],
  dark: ['#1c5cab', '#2a78d6', '#5598e7', '#9ec5f4'],
} as const;

export const MAX_SERIES = 8;

/** De-emphasis grey for the folded "Other" series (>= 3:1 on both paper modes). */
export const OTHER_GREY = '#8a877f';
