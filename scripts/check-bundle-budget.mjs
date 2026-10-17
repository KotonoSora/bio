#!/usr/bin/env node
// Frontend performance budget (platform doc 10): keeps the shell and every
// vertical under the documented gzip ceilings. Runs as the final step of
// `npm run build:web`, so the CI frontend gate fails on regressions such as a
// development React copy slipping into a vertical bundle.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { gzipSync } from 'node:zlib';

export const BUDGETS = {
  shellJs: 150 * 1024,
  shellCss: 16 * 1024,
  verticalJs: 175 * 1024,
  verticalCss: 20 * 1024,
};

const kb = (bytes) => `${(bytes / 1024).toFixed(1)} KB`;

export function check({
  distDir = 'web/dist/assets',
  mfeDir = 'web/public/mfe',
  budgets = BUDGETS,
} = {}) {
  const checks = [];
  const shell = readdirSync(distDir);
  const total = (ext) =>
    shell
      .filter((f) => f.endsWith(ext))
      .reduce((n, f) => n + gzipSync(readFileSync(join(distDir, f))).length, 0);
  checks.push({ label: 'shell js (dist/assets/*.js)', actual: total('.js'), max: budgets.shellJs });
  checks.push({ label: 'shell css (dist/assets/*.css)', actual: total('.css'), max: budgets.shellCss });

  for (const name of readdirSync(mfeDir)) {
    for (const version of readdirSync(join(mfeDir, name))) {
      const dir = join(mfeDir, name, version);
      const entry = join(dir, 'entry.js');
      if (!existsSync(entry)) throw new Error(`missing ${entry} (a vertical must ship an entry.js)`);
      checks.push({
        label: `${name}@${version} entry.js`,
        actual: gzipSync(readFileSync(entry)).length,
        max: budgets.verticalJs,
      });
      const css = join(dir, 'style.css');
      if (existsSync(css)) {
        checks.push({
          label: `${name}@${version} style.css`,
          actual: gzipSync(readFileSync(css)).length,
          max: budgets.verticalCss,
        });
      }
    }
  }

  const failures = checks.filter((c) => c.actual > c.max);
  return { checks, failures };
}

function main() {
  const { checks, failures } = check();
  for (const c of checks) {
    console.log(`${c.actual > c.max ? 'OVER' : 'ok  '} ${c.label}: ${kb(c.actual)} / ${kb(c.max)}`);
  }
  if (failures.length > 0) {
    console.error(`\nfrontend budget exceeded by ${failures.length} artifact(s) - see platform doc 10`);
    process.exit(1);
  }
  console.log('frontend budget ok');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
