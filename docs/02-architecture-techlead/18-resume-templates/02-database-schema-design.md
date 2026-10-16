# Resume Templates - Database Schema Design (feature 18)

Phase 2 TL · **Zero migrations, zero new tables**: the registry is code; the only storage touched is feature 17's `resume.documents.template_id`. BA sources: `docs/01-requirements-ba/18-resume-templates/` (BR-01, BR-02, BR-07, BR-09).

## No-DDL assertion

- `grep -rn "CREATE TABLE" docs/02-architecture-techlead/18-resume-templates/` returns nothing; schema diff before/after shows zero tables, zero columns.
- Consumed column: `resume.documents.template_id TEXT NOT NULL DEFAULT 'classic'` (defined once in `docs/02-architecture-techlead/17-resume-documents/02-database-schema-design.md`); written only through feature 17's document update; render paths read it.
- No template FK exists by design: templates ship as code, and a FK would couple deploys to data (legacy rationale preserved).

## Registry contract (code, validated at boot)

| Field | Rule |
|---|---|
| `id` | Unique, lowercase, stable forever (renames forbidden; removal keeps read fallback); plain text in `template_id` |
| `name` | Display name (brand-neutral); localization handled in UI copy |
| `tokens{font, scale, accent, density}` | Every token must be consumed by the screen renderer and the print renderer (unused token fails the registry test) |
| `premium` | Boolean; default `false`; `true` requires the feature 03 claims-registry update first (Q-03) |
| Renderers + thumbnail | Same bundle as the registry entry (CI consistency gate: every id has a screen component, print stylesheet, thumbnail) |

## Storage touches (all existing)

| Storage | Touch | Owner |
|---|---|---|
| `resume.documents.template_id` | read (render) + write (selection) | Feature 17 |
| SPA template assets `templates/{id}/` | screen component + print stylesheet, fingerprinted, immutable (1-year cache) | This feature |
| Thumbnail assets | placeholder-rendered, `<= 100 KB`, fingerprinted | This feature (fixture-only pipeline) |
| Redis | none | - |

## Verification drills

1. No-table drill: schema diff empty; grep assertion above holds.
2. Registry drill: boot fails on duplicate id or an unused token; catalog lists every id with renderer + thumbnail (nonempty set).
3. Fallback drill: a renamed id on read resolves `classic` + `template.fallback` metric; no 500 and no blank render.
4. Thumbnail drill: pipeline input type is the placeholder fixture (a real snapshot cannot be passed); pixel-scan finds no PII; size cap holds.
5. Parity drill: text extraction across all templates identical for a fixed snapshot (automated comparison).
6. Consistency drill: removing a renderer for a listed id fails CI naming the id.
