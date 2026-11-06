import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';

// `vite build --mode single` produces one self-contained index.html (fonts come from
// Google Fonts instead of being bundled) for quick sharing/previewing.
export default defineConfig(({ mode }) => {
  const single = mode === 'single';
  return {
    base: './',
    plugins: [react(), ...(single ? [viteSingleFile()] : [])],
    resolve: single
      ? { alias: { './fonts': fileURLToPath(new URL('./src/fonts-remote.ts', import.meta.url)) } }
      : undefined,
    build: single ? { outDir: 'dist-single', assetsInlineLimit: 100_000_000 } : { outDir: 'dist' },
    test: { environment: 'node', include: ['tests/**/*.test.ts'] },
  };
});
