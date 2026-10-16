# Resume Templates - Context and Scope (feature 18)

Phase 1 BA · Feature 18 of 53 (resume) · Implemented by `docs/02-architecture-techlead/18-resume-templates/` (5 files); extends `resume-service` (registry + render contract) and the SPA (screen/print renderers). Selection rides feature 17's document update; cv SSR parity lands on feature 10's `/cv`.

## Purpose

Define how resumes look on screen and on paper from a single frozen snapshot. Screen preview is interactive dashboard UX; print output is a strict A4 rendering used for PDF-via-print and recruiter review. Templates are token-only and ship as code - styling work can never corrupt saved data, and no template can smuggle CSS/JS into a user's page.

## Objectives

- O-1 Frozen source: preview and print always bind the frozen snapshot, never draft state; printing from unsaved drafts is impossible by construction.
- O-2 Text parity: the same snapshot across every template extracts identical text; only layout changes.
- O-3 Print honesty: `@media print` A4 flow, exact colors, no dashboard chrome, experience entries not split; tested in Chrome + Safari print preview.
- O-4 No server PDF: export is `window.print()` and nothing else; PDF dependencies fail the build on both server and client (grep gates, structural).
- O-5 Safe growth: a token-based template can be added without touching user-data code; unknown ids never break pages (write refusal, read fallback).

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Job seeker | Read: gallery/registry, preview, print; Update: own document's `templateId` | Previews templates, prints/saves PDF for applications from a frozen snapshot. |
| Recruiter / viewer | Read: public `/:username/cv` SSR and printed PDFs | Reviews the resume; sees text parity with the featured template. |
| Template contributor (internal) | Create: registry entry + renderers + thumbnail (code change) | Adds a token-based template; never touches user-data code. |
| Attacker (adversarial) | none | Cannot inject CSS/JS via any field or template; cannot bypass the premium gate. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 v1 template count | Three token-based layouts at launch (at least one single-column); the registry contract freezes, the exact set ships with the first release. Density remains a token/view toggle, not a combinator explosion. |
| Q-02 Cover-letter print | Cover letters are their own documents (feature 17's template-variant model); print runs per document - a second-page-of-same-template mode is not built. |
| Q-03 Premium templates | The mechanism is registered (registry `premium` flag + server-side gate), but zero premium templates ship at launch - enabling one requires a plan-claims update in feature 03's registry first. |
| Q-04 Gallery placement | Embedded strip in the resume editor (feature 17's picker becomes real); no separate full-page gallery route in v1. |
| Q-05 Density toggle | View-only, like zoom; persisting per-template density requires a flagged amendment. |

## In scope

- Template registry (code), catalog read endpoint, boot/CI validation (unique ids; every token consumed; every id has thumbnail + renderer).
- Screen renderer contract (interactive: collapse, zoom, density - view-only) and print renderer contract (read-only tree, A4 rules).
- Selection flow via feature 17's document update with registry validation and the premium gate.
- cv SSR text-parity cues; thumbnail pipeline (placeholder-only); the no-PDF structural guardrails.

## Out of scope

- Snapshot versioning, publish pointers, document CRUD (feature 17); sharing formats (feature 19); share links (feature 20); portability (feature 21).
- Per-user CSS/JS uploads (never exists), server-side PDF rendering (never exists), print pixel-parity with SSR (text parity is the contract).
- AI-tailored template suggestions (feature 16, gated); theme system for bio pages (feature 09 - separate token registry, not reused here).

Related: rules in `docs/01-requirements-ba/18-resume-templates/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/18-resume-templates/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/18-resume-templates/05-exception-handling-and-errors.md`.
