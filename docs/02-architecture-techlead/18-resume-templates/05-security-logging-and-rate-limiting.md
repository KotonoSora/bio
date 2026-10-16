# Resume Templates - Security, Logging, and Rate Limiting (feature 18)

Phase 2 TL · BA sources: `docs/01-requirements-ba/18-resume-templates/` (BR-01, BR-05, BR-08, BR-09). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Injection isolation: templates consume tokens + snapshot text only; user fields are escaped as text; style/class/markup from data is dropped by every renderer; CSP `default-src 'self'` on dashboard and cv | AC-05; hostile-field corpus across screen/print/SSR |
| S-2 | Premium gate server-side: registry `premium` checked against the 30 s entitlement snapshot at selection; fail-closed to free on miss; UI never the gate | AC-08; outage + forced-entry probes |
| S-3 | No-PDF structural: server dependency set and client bundle grep clean (PDF/headless packages absent); the print path is `window.print()` with zero network calls | AC-06; both grep gates + print hook spy |
| S-4 | Thumbnail PII: pipeline inputs are typed placeholder fixtures; real snapshots cannot be passed; pixel-scan + size cap per release | AC-09; pipeline type test |
| S-5 | Asset integrity: fingerprinted immutable assets; no user content in any gallery asset; catalog carries no user data | Asset review + fingerprint check |
| S-6 | Print locality: printing is client-local (no upload, no server render, no stored PDF bytes); mid-preview saves never alter the bound print tree | AC-03; network spy |
| S-7 | Selection audit: template switches audited (actor, document, old -> new id); read fallbacks counted (`template.fallback`) | Audit reconstruction |
| S-8 | Registry integrity: boot validation fail-fast; CI consistency gate blocks unrenderable ids; ids stable forever (no reuse) | AC-10; boot + build drills |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Catalog read | 600/min/user | `429 throttled` |
| Selection write | Rides feature 17's PUT envelope (60/h/user) | `429`; selection rolls back locally |
| Thumbnail/assets | Static edge cache, immutable | - |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Entitlement snapshot unreachable | Premium selection denied (fail-closed free); non-premium proceeds; notice |
| Catalog endpoint down | Gallery serves the last in-memory catalog; cold start disables selection but the current template still renders (renderer is bundled); retry banner |
| Unknown id reaches a render | Fallback `classic` + metric (no blank page, no 500) |
| Bundle/registry mismatch at runtime (should be impossible) | Unrenderable ids marked non-selectable + alert; CI gate is the primary defense |
| public-site deploy lag (cv cues not yet deployed) | cv renders text-parity without density classes - same text, plainer layout; no error |

## Gates

- G4: catalog additive-only; the two new codes are additive on feature 17's PUT row.
- G5: S-1, S-2, S-3, S-4 probes run per release.
- G7: drill set AC-01..AC-11 attaches to the tag; print fixtures (Chrome + Safari) archived per template per release.
