# Invoices and Receipts - Step-by-Step Implementation Plan (feature 37)

Phase 2 TL · Order matters: migration, renderers, tokens, proformas, wiring, cross-edits, drills. Package `com.app.billing.evidence`. BA sources: `docs/01-requirements-ba/37-invoices-and-receipts/`.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V4__proformas.sql` per `02-database-schema-design.md` + drift test (target CHECK, open-scope uniques) | 36's V3 applied | V4 drills 1-3 green |
| T-02 | BE | Renderer core: `ReceiptFacts` composition + HTML template + A4 print CSS + `AmountWords` + `NonVatDisclaimer` (one source each) | T-01 | AC-02, AC-12 (formatter half) |
| T-03 | BE | Receipt endpoint: owner session + guest token verify (`RECEIPT_LINK_KEY` from env) + `no-store` + `receipt_unavailable` states | T-02 | AC-01 (render), AC-03 |
| T-04 | BE | Resend endpoint: Turnstile + always-202 + match-only send + throttles | T-03 | AC-04 |
| T-05 | BE | Receipt email enqueue: template `billing.receipt`, key `rcpt:{orderCode}`, recipient resolution (01/orders), failure metric | T-03 | AC-01 (email), AC-05, AC-06 |
| T-06 | BE | Proforma issue/list/print: pricing paths (35/22), ref minting, scope dedupe, seller config gate, validity config | T-01 | AC-10, AC-11 |
| T-07 | BE | Conversion wiring: 33's create (`proformaRef`, drift supersede, `proformaRepriced`) + 34's grant flip (`proforma.converted`) | T-06; 33/34 landed | AC-07, AC-08 |
| T-08 | BE | Proforma expiry pass (`02:20 UTC`, advisory lock, batched, audited, rerun-safe) | T-06 | AC-09 (server half) |
| T-09 | BE | Cross-edits per `01-architecture-and-tech-stack.md` (33, 34, 32) | T-05..T-08 | Diff review green |
| T-10 | FE | History page (34's list + receipt links + need-help) + receipt/proforma print views + issue dialog + guest resend page | T-03, T-06 | AC-01..AC-04 UI, AC-10 UI |
| T-11 | DevOps | Metrics (`receipt_sent`, `receipt_notify_failed`, `proforma.*` counters) + no-PAN/PDF greps in CI + secrets (`RECEIPT_LINK_KEY` via env/vault only) | T-02..T-08 | NFR-06, NFR-09 |
| T-12 | BE/DevOps | Drills: grant→receipt round trip, guest expiry+resend, drift supersede, conversion race, expiry rerun, unsubscribe class, pipeline outage | T-03..T-11 | AC-01..AC-13 complete |

## Sequencing notes

1. T-01 before T-06: scope uniques gate duplicate issuance; `orders.proforma_id` gates the 33/34 edits.
2. T-02's formatter + disclaimer are single-source modules - T-06 and 38's future credit notes reuse them unchanged.
3. T-07 edits 33's create and 34's grant: land the guards (`status='open'` writes) first; the checkout/charge behavior is untouched when no ref is present (byte-identical path).
4. T-12's conversion-race drill runs parallel grants against one linked proforma before rollout.
5. Rollout: no feature flag; proformas start unavailable until `billing.seller` is set by ops (honest 409), which is the launch switch by construction.

## Definition of done

AC-01..AC-13 green in CI (AC-13 activates with 38); full-tree validation (word caps + backticked path resolution) green; greps: zero PAN identifiers, zero PDF libraries, single DDL owner, single disclaimer constant; `MIGRATION.md` row 37 flips to `done` at the batch tail with legacy `monetize/invoices/*` + `monetize/billing/invoices-receipts.md` deleted.
