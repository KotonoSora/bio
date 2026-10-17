import { defineConfig } from '@playwright/test';

// E2E runs against the running compose stack through the edge (nginx :80) -
// the same entry users hit, mirroring deploy/smoke.sh. Tests never boot the
// stack themselves; start it with docker compose first, then:
//   cd e2e && npm test               (headless)
//   cd e2e && npm run test:headed    (visible browser, 400 ms slow-mo)
// Overrides: EDGE_URL, HEADED=1, SLOWMO=<ms>.
const baseURL = process.env.EDGE_URL ?? 'http://localhost';
const headed = process.env.HEADED === '1';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    headless: !headed,
    locale: 'en-US',
    launchOptions: { slowMo: Number(process.env.SLOWMO ?? 0) },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: headed ? 'on' : 'retain-on-failure',
  },
});
