#!/usr/bin/env node
// Feature 03 release artifacts (T-08; NFR-08, G7, S-8): exports the claim
// registry and the rendered pricing copy for a release tag, and reports the
// diff against the previous archived tag. The rendered copy mirrors the SPA
// exactly - registry patterns substitute server/config values, amounts render
// through the same VND display format, absence stays absent (BR-03, BR-12) -
// and the yearly amount is the server's exact VND math (BR-04, NFR-04). The
// drift gate (`scripts/check-plan-claims.mjs`) still owns pass/fail; this tool
// owns the release record.
//
// Usage: node scripts/plan-claims-release.mjs --tag <tag> [--out <dir>]
//        [--registry plans-claims.json] [--config-file .env]
//        [--monthly-vnd <n>] [--yearly-discount-pct <n>] [--trial-days <n>]
import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const VND_FORMAT = new Intl.NumberFormat('en', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

/** BR-12/NFR-04: the SPA's display format; the server owns the numbers. */
export function formatVnd(value) {
  if (!Number.isInteger(value)) throw new Error('VND amounts are integers end to end (NFR-04)');
  return VND_FORMAT.format(value);
}

/** Server-side yearly math half-up at the dong (BR-04, mirrors PlanCatalog.yearlyVnd). */
export function yearlyVnd(monthlyVnd, yearlyDiscountPct) {
  const yearly = 12 * monthlyVnd;
  if (yearlyDiscountPct == null) return yearly;
  return yearly - Math.floor((yearly * yearlyDiscountPct + 50) / 100);
}

/** Claims that render only when their config keys exist (PricingPage render conditions). */
const CLAIM_REQUIREMENTS = {
  monthly_price: ['monthlyVnd'],
  yearly_price: ['monthlyVnd'],
  yearly_saving_badge: ['monthlyVnd', 'yearlyDiscountPct'],
  trial_cta: ['trialDays'],
};

const PRICE_KEY_BY_CLAIM = { monthly_price: 'monthlyVnd', yearly_price: 'yearlyVnd' };

function integerOrThrow(value, label, { min, max } = {}) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || (min != null && parsed < min) || (max != null && parsed > max)) {
    throw new Error(`${label} must be a whole number${min != null ? ` >= ${min}` : ''}${max != null ? ` and <= ${max}` : ''}, got ${value}`);
  }
  return parsed;
}

/** Validates and completes the config fixture; unset keys stay null (absence is the design). */
export function normalizeConfig(config = {}) {
  const monthlyVnd = config.monthlyVnd == null ? null : integerOrThrow(config.monthlyVnd, 'monthlyVnd', { min: 0 });
  // NFR-05/S-3: the <= 44% ceiling is the only numeric guardrail; never archive an invalid one.
  const yearlyDiscountPct =
    config.yearlyDiscountPct == null ? null : integerOrThrow(config.yearlyDiscountPct, 'yearlyDiscountPct', { min: 0, max: 44 });
  const trialDays = config.trialDays == null ? null : integerOrThrow(config.trialDays, 'trialDays', { min: 1 });
  return {
    monthlyVnd,
    yearlyDiscountPct,
    trialDays,
    yearlyVnd: monthlyVnd == null ? null : yearlyVnd(monthlyVnd, yearlyDiscountPct),
  };
}

function tokenValue(claimId, token, config) {
  if (token === 'yearlyDiscountPct') return config.yearlyDiscountPct == null ? null : String(config.yearlyDiscountPct);
  if (token === 'trialDays') return config.trialDays == null ? null : String(config.trialDays);
  if (token === 'price') {
    const key = PRICE_KEY_BY_CLAIM[claimId];
    if (!key) throw new Error(`claim ${claimId}: {price} has no price key (registry/contract bug)`);
    const amount = config[key];
    return amount == null ? null : formatVnd(amount);
  }
  throw new Error(`claim ${claimId}: unknown token {${token}} (registry/contract bug)`);
}

/**
 * The rendered-copy snapshot: one row per registry claim with its rendered
 * sentence, or `null` + the unset reason when the claim is omitted (BR-03).
 */
export function renderSnapshot(registry, config, tag, now = new Date()) {
  const resolved = normalizeConfig(config);
  const claims = registry.map((row) => {
    const missing = (CLAIM_REQUIREMENTS[row.claimId] ?? []).find((key) => resolved[key] == null);
    if (missing) return { claimId: row.claimId, rendered: null, reason: `config-unset:${missing}` };
    const rendered = row.textPattern.replace(/\{(\w+)\}/g, (_, token) => {
      const value = tokenValue(row.claimId, token, resolved);
      return value;
    });
    if (/\{\w+\}/.test(rendered)) throw new Error(`claim ${row.claimId}: unsubstituted token remains`);
    return { claimId: row.claimId, rendered };
  });
  return { tag, renderedAt: now.toISOString(), config: resolved, claims };
}

/** Additive/removal/change report between two snapshots (NFR-08: diff vs previous tag). */
export function diffSnapshots(previous, current) {
  const before = new Map((previous?.claims ?? []).map((claim) => [claim.claimId, claim.rendered ?? null]));
  const after = new Map((current?.claims ?? []).map((claim) => [claim.claimId, claim.rendered ?? null]));
  const added = [];
  const removed = [];
  const changed = [];
  for (const [claimId, rendered] of after) {
    if (!before.has(claimId)) added.push(claimId);
    else if (before.get(claimId) !== rendered) changed.push({ claimId, from: before.get(claimId), to: rendered });
  }
  for (const claimId of before.keys()) if (!after.has(claimId)) removed.push(claimId);
  return { added: added.sort(), removed: removed.sort(), changed: changed.sort((a, b) => a.claimId.localeCompare(b.claimId)) };
}

/** `KEY=VALUE` reader for the deployed config file; comments and blanks are skipped. */
export function readEnvFile(file) {
  let text = '';
  try {
    text = readFileSync(file, 'utf8');
  } catch {
    return {};
  }
  const env = {};
  for (const line of text.split('\n')) {
    const match = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/.exec(line);
    if (!match || line.trimStart().startsWith('#')) continue;
    env[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
  }
  return env;
}

function previousTagDir(outDir, tag) {
  let names = [];
  try {
    names = readdirSync(outDir).filter((name) => name !== tag && statSync(join(outDir, name)).isDirectory());
  } catch {
    return null;
  }
  const candidates = names
    .map((name) => ({ name, stat: statSync(join(outDir, name)) }))
    .sort((a, b) => b.stat.mtimeMs - a.stat.mtimeMs);
  for (const candidate of candidates) {
    const snapshot = join(outDir, candidate.name, 'rendered.json');
    try {
      return { tag: candidate.name, snapshot: JSON.parse(readFileSync(snapshot, 'utf8')) };
    } catch {
      continue; // an archive without a rendered snapshot cannot be diffed against
    }
  }
  return null;
}

/** Writes `<outDir>/<tag>/{registry,rendered,diff}.json` and returns the report. */
export function archiveTag({ registry, outDir, tag, config, now = new Date() }) {
  const snapshot = renderSnapshot(registry, config, tag, now);
  const previous = previousTagDir(outDir, tag);
  const diff = diffSnapshots(previous?.snapshot ?? null, snapshot);
  const dir = join(outDir, tag);
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'registry.json'), `${JSON.stringify(registry, null, 2)}\n`);
  writeFileSync(join(dir, 'rendered.json'), `${JSON.stringify(snapshot, null, 2)}\n`);
  writeFileSync(join(dir, 'diff.json'), `${JSON.stringify({ tag, previous: previous?.tag ?? null, ...diff }, null, 2)}\n`);
  return { dir, snapshot, diff, previous: previous?.tag ?? null };
}

export function parseArgs(argv) {
  const args = {
    tag: 'untagged',
    out: 'deploy/releases/plan-claims',
    registry: 'plans-claims.json',
    configFile: '.env',
    monthlyVnd: null,
    yearlyDiscountPct: null,
    trialDays: null,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const [flag, inline] = argv[i].split('=');
    const value = inline ?? argv[i + 1];
    const take = () => {
      if (inline == null) i += 1;
      return value;
    };
    switch (flag) {
      case '--tag': args.tag = take(); break;
      case '--out': args.out = take(); break;
      case '--registry': args.registry = take(); break;
      case '--config-file': args.configFile = take(); break;
      case '--monthly-vnd': args.monthlyVnd = integerOrThrow(take(), 'monthlyVnd', { min: 0 }); break;
      case '--yearly-discount-pct': args.yearlyDiscountPct = integerOrThrow(take(), 'yearlyDiscountPct', { min: 0, max: 44 }); break;
      case '--trial-days': args.trialDays = integerOrThrow(take(), 'trialDays', { min: 1 }); break;
      default: throw new Error(`unknown argument ${flag}`);
    }
  }
  return args;
}

function main(argv) {
  const args = parseArgs(argv);
  const env = { ...readEnvFile(args.configFile), ...process.env };
  const numeric = (value) => (value == null || value === '' ? null : integerOrThrow(value, 'env config', { min: 0 }));
  const config = {
    monthlyVnd: args.monthlyVnd ?? numeric(env.BILLING_CATALOG_MONTHLY_VND),
    yearlyDiscountPct: args.yearlyDiscountPct ?? numeric(env.BILLING_CATALOG_YEARLY_DISCOUNT_PCT),
    trialDays: args.trialDays ?? numeric(env.BILLING_CATALOG_TRIAL_DAYS),
  };
  const registry = JSON.parse(readFileSync(args.registry, 'utf8'));
  const { dir, snapshot, diff, previous } = archiveTag({ registry, outDir: resolve(args.out), tag: args.tag, config });
  const omitted = snapshot.claims.filter((claim) => claim.rendered == null).length;
  console.log(`plan-claims: archived ${snapshot.claims.length} claims (${snapshot.claims.length - omitted} rendered, ${omitted} omitted) -> ${dir}`);
  console.log(`plan-claims: diff vs ${previous ?? 'no previous tag'}: +${diff.added.length} -${diff.removed.length} ~${diff.changed.length}`);
  return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (error) {
    console.error(`plan-claims: ${error.message}`);
    process.exitCode = 1;
  }
}
