#!/usr/bin/env node
// Builds every workspace under mfe/ and collects the dists into the shell's
// public dir as versioned directories, then writes the runtime manifest the
// shell discovers at /app/mfe.manifest.json (platform doc 10).
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const mfeRoot = join(root, 'mfe');
const outRoot = join(root, 'web', 'public', 'mfe');
const manifestPath = join(root, 'web', 'public', 'mfe.manifest.json');

const names = existsSync(mfeRoot)
  ? readdirSync(mfeRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory() && !entry.name.startsWith('.'))
      .map((entry) => entry.name)
      .sort()
  : [];

rmSync(outRoot, { recursive: true, force: true });
mkdirSync(outRoot, { recursive: true });

const manifest = {};
for (const name of names) {
  const pkgPath = join(mfeRoot, name, 'package.json');
  if (!existsSync(pkgPath)) continue;
  const pkg = JSON.parse(readFileSync(pkgPath, 'utf8'));
  const tag = pkg.mfe?.tag;
  if (!tag) throw new Error(`${name}: package.json needs "mfe": { "tag": "..." }`);
  console.log(`== building mfe/${name} (${tag}) ==`);
  execFileSync('npm', ['run', 'build', '--workspace', `mfe/${name}`], { cwd: root, stdio: 'inherit' });
  const dist = join(mfeRoot, name, 'dist');
  for (const file of ['entry.js', 'style.css']) {
    if (!existsSync(join(dist, file))) throw new Error(`${name}: missing dist/${file}`);
  }
  cpSync(dist, join(outRoot, name, pkg.version), { recursive: true });
  manifest[name] = {
    tag,
    version: pkg.version,
    entry: `mfe/${name}/${pkg.version}/entry.js`,
    css: `mfe/${name}/${pkg.version}/style.css`,
  };
}

writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`mfe manifest -> web/public/mfe.manifest.json (${Object.keys(manifest).join(', ') || 'empty'})`);
