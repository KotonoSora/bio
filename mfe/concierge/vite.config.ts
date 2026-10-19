import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';

// Independent MFE build (platform doc 10): one self-contained ES module
// (`entry.js`) + one stylesheet (`style.css`); React is bundled per MFE on
// purpose (isolation, no shared runtime). The shell lazy-imports the pair from
// the versioned /app/mfe/<name>/<version>/ path listed in the manifest.
export default defineConfig({
  base: './',
  publicDir: false,
  // Lib mode preserves `process.env.NODE_ENV` for bundler consumers; this MFE
  // is loaded unbundled at runtime by the shell, so pin the production branch.
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  plugins: [react(), tailwindcss()],
  resolve: {
    tsconfigPaths: true,
  },
  server: { port: 5176, proxy: { '/api': 'http://localhost:8080' } },
  build: {
    sourcemap: false,
    cssCodeSplit: false,
    lib: {
      entry: 'src/entry.tsx',
      formats: ['es'],
      fileName: () => 'entry.js',
      cssFileName: 'style',
    },
  },
});
