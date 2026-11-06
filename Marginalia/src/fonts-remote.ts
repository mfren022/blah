// Used by the single-file build: load the same families from Google Fonts instead of bundling them.
const families = [
  'Fraunces:ital,opsz,wght@0,9..144,300..900;1,9..144,300..900',
  'Literata:ital,opsz,wght@0,7..72,300..800;1,7..72,300..800',
  'EB+Garamond:ital,wght@0,400..800;1,400..800',
  'Cormorant+Garamond:ital,wght@0,300..700;1,300..700',
  'Playfair+Display:ital,wght@0,400..900;1,400..900',
  'Source+Serif+4:ital,opsz,wght@0,8..60,300..800;1,8..60,300..800',
  'Inter:wght@300..800',
  'Atkinson+Hyperlegible:ital,wght@0,400;0,700;1,400',
];
const link = document.createElement('link');
link.rel = 'stylesheet';
link.href = `https://fonts.googleapis.com/css2?${families.map((f) => `family=${f}`).join('&')}&display=swap`;
document.head.appendChild(link);
export {};
