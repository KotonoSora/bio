import { defineConfig } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: '/app/',
  plugins: [react(), tailwindcss()],
  // Single source of truth: `paths` from tsconfig.json (also used by tsc);
  // no manual `resolve.alias` needed for `@/*`.
  resolve: {
    tsconfigPaths: true,
  },
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } },
  build: { outDir: 'dist', sourcemap: false },
  test: {
    // Vitest 4+ no longer mirrors `base` into `import.meta.env.BASE_URL`
    // (vitest-dev/vitest#9156); pin the production SPA base so manifest-path
    // contracts resolve exactly as they do under Nginx.
    env: { BASE_URL: '/app/' },
  },
});
