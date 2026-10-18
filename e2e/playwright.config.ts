import { defineConfig } from '@playwright/test';

// E2E runs against the running compose stack through the edge (nginx :80) -
// the same entry users hit, mirroring deploy/smoke.sh. Tests never boot the
// stack themselves; start it with docker compose first, then:
//   cd e2e && npm test               (headless)
//   cd e2e && npm run test:headed    (visible browser, 400 ms slow-mo)
//   cd e2e && npm run idp:on && npm run test:full   (opt-in full-login suite)
// Overrides: EDGE_URL, HEADED=1, SLOWMO=<ms>.
const baseURL = process.env.EDGE_URL ?? 'http://localhost';
const headed = process.env.HEADED === '1';
// Full-login runs need the mock IdP (e2e/mock-idp); the custom-domains
// serving suite also needs the mock DoH resolver (e2e/mock-doh, feature 04
// TXT verification). Playwright manages their lifecycle only when the
// suites are enabled; default runs never start them.
const fullLogin = process.env.E2E_FULL_LOGIN === '1';
const domainsPro = process.env.E2E_DOMAINS_PRO === '1';
const webServers = [
  ...(fullLogin ? [{ command: 'node mock-idp/server.mjs', url: 'http://localhost:9099/__health' }] : []),
  ...(domainsPro ? [{ command: 'node mock-doh/server.mjs', url: 'http://localhost:9097/__health' }] : []),
].map((server) => ({
  ...server,
  reuseExistingServer: true,
  stdout: 'ignore' as const,
  stderr: 'pipe' as const,
}));

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  webServer: webServers.length > 0 ? webServers : undefined,
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
