#!/usr/bin/env node
// Feature 03 drift drill (T-07; AC-01, AC-02, BR-03, BR-11): the pricing copy
// is claim-registry-driven (patterns live only in plans-claims.json and render
// through mfe/plans/src/lib/claims.ts), so CI checks four things mechanically:
//   1. every registry row is complete (claim id, text pattern, enforcing spec
//      path, known config/catalog key),
//   2. tier copy still renders through the registry choke points (the MFE's
//      claimRows -> row.textPattern rendering and the claims module importing
//      the registry),
//   3. every claim id is referenced by a copy site - CLAIM_IDS in the SPA or a
//      claims list in the billing catalog (stale row) - and every tier claim
//      the catalog advertises has a registry row (unrenderable copy, BR-11),
//   4. no hard-typed pricing literals (amount/percent/day-count digits next to
//      claim wording) exist in the vertical's source.
// Release archiving (NFR-08/G7) appends the export + rendered snapshot at
// deploy/tag time; this gate runs first.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();

const SOURCE_DIRS = ['mfe/plans/src'];
const KNOWN_KEYS = new Set([
  'yearlyDiscountPct',
  'trialDays',
  'profiles.limit',
  'plansResponse.version',
  'plansResponse.tiers',
  'plansResponse.prices',
  'plansResponse.trialDays',
]);

function walkSourceFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) out.push(...walkSourceFiles(p));
    else if (/\.(tsx?|css)$/.test(entry)) out.push(p);
  }
  return out;
}

function fail(message) {
  console.error(`drift: ${message}`);
  process.exitCode = 1;
}

const registry = JSON.parse(readFileSync(join(root, 'plans-claims.json'), 'utf8'));
const sourceFiles = SOURCE_DIRS.flatMap((d) => walkSourceFiles(join(root, d)));
const contents = sourceFiles.map((p) => ({ file: p, text: readFileSync(p, 'utf8') }));

// 1. Registry integrity.
for (const row of registry) {
  if (!row.claimId || !row.textPattern) fail(`row incomplete: ${JSON.stringify(row)}`);
  if (typeof row.enforcement !== 'string' || !row.enforcement.startsWith('docs/')) {
    fail(`claim ${row.claimId}: enforcement must name its enforcing spec path`);
  }
  if (!KNOWN_KEYS.has(row.key)) fail(`claim ${row.claimId}: key "${row.key}" is not a known config/catalog field`);
}
const seen = new Set(registry.map((r) => r.claimId));
if (seen.size !== registry.length) fail('duplicate claimId in registry');

// 2. Copy renders through the registry choke points (BR-11): the claims module
// must load plans-claims.json directly, and the pricing page must resolve tier
// sentences via the registryRows filter (rendering a hand-typed copy table
// again silently reintroduces the drift this drill guards).
const claimsModule = contents.find((c) => c.file === join(root, 'mfe/plans/src/lib/claims.ts'));
const pricingPage = contents.find((c) => c.file === join(root, 'mfe/plans/src/PricingPage.tsx'));
if (!claimsModule || !claimsModule.text.includes('plans-claims.json')) {
  fail('mfe/plans/src/lib/claims.ts no longer imports plans-claims.json (copy must render through the registry, BR-11)');
}
if (!pricingPage || !pricingPage.text.includes('row.textPattern') || !pricingPage.text.includes('claimRows')) {
  fail('mfe/plans/src/PricingPage.tsx must render tier claims through claimRows.textPattern (BR-11)');
}

// 3. Two-sided claim cross-reference (stale rows / unrenderable copy): every
// registry row's id must appear at a copy site - the CLAIM_IDS table of the SPA
// or a tier claims list in the catalog - and every claim id the catalog
// advertises must have a registry row (renderClaim would otherwise throw the
// sentence away at runtime).
const registryIds = new Set(registry.map((r) => r.claimId));
const CATALOG = 'services/billing-service/src/main/kotlin/com/resume/billing/domain/PlanCatalog.kt';
let catalog = null;
try {
  catalog = { file: join(root, CATALOG), text: readFileSync(join(root, CATALOG), 'utf8') };
} catch {
  // kept null -> layout-drift failures below; the drill still reports rows.
}
const siteTexts = catalog ? [...contents, catalog] : contents;
for (const row of registry) {
  const referenced = siteTexts.some((c) => c.text.includes(row.claimId));
  if (!referenced) fail(`claim ${row.claimId}: id is referenced by no copy site (stale registry row, S-8)`);
}
if (!catalog) fail('cannot read PlanCatalog.kt - tier claim ids are unreadable to this drill (layout drift)');
const tierClaimLists = catalog ? [...catalog.text.matchAll(/claims\s*=\s*listOf\(([^()[\]]*)\)/g)].map((m) => m[1]) : [];
if (tierClaimLists.length === 0) fail('PlanCatalog.kt: no tier claims lists parsed - extractor layout drift');
for (const list of tierClaimLists) {
  for (const id of [...list.matchAll(/"([a-z][a-z0-9_]*)"/g)].map((m) => m[1])) {
    if (!registryIds.has(id)) fail(`catalog tier claim "${id}" has no registry row (copy must move with the registry, S-8)`);
  }
}

// 4. No hard-typed pricing literals in copy (AC-02/BR-03 seeded-stale drill).
const LITERAL = /\b(save\b.*?\b\d|per\s+(month|year)\b.*?\b\d|\b\d+\s*%\s*(off|with)|\btry\b.*?\b\d+\s*days?\b|for\s+\d+\s+days?\b)/i;
for (const c of contents) {
  for (const [i, line] of c.text.split('\n').entries()) {
    const vs = line.replace(/['"`{}()/[\]#*_-]/g, ' ');
    if (LITERAL.test(vs) && !/test|mocha|describe|it\.|expect|docblock|BR-\d/.test(line)) {
      fail(`${c.file}:${i + 1}: suspicious hard-typed pricing literal -> ${line.trim()}`);
    }
  }
}

if (process.exitCode) {
  console.error('drift: pricing copy failed the claim drill; move copy changes and the registry row in one change (S-8).');
} else {
  console.log(`drift: ok - ${registry.length} claims resolve through the registry (${sourceFiles.length} source files scanned).`);
}
