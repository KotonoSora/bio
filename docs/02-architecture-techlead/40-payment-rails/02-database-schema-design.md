# Payment Rails - Database Schema Design (feature 40)

Phase 2 TL · `billing_db` migration `V7__provider_rails.sql` (chain row in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`). BA sources: `docs/01-requirements-ba/40-payment-rails/`. Three tables; no `orders` DDL (V1's `psp_ref` already carries rail references; columns-only rule respected).

## Flyway V7 (`V7__provider_rails.sql`)

```sql
CREATE TABLE provider_events (
  id TEXT PRIMARY KEY,
  provider TEXT NOT NULL,
  event_ref TEXT NOT NULL,
  order_code BIGINT,
  outcome TEXT NOT NULL DEFAULT 'processing'
    CHECK (outcome IN ('processing','granted','duplicate','rejected','exception','ignored')),
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_pe_ref UNIQUE (provider, event_ref)
);
CREATE INDEX idx_pe_order ON provider_events (order_code) WHERE order_code IS NOT NULL;
CREATE INDEX idx_pe_recent ON provider_events (provider, received_at DESC);

CREATE TABLE rail_exceptions (
  id TEXT PRIMARY KEY,
  provider TEXT NOT NULL CHECK (provider IN ('sepay','napas')),
  rail_txn_ref TEXT NOT NULL,
  amount_vnd INT NOT NULL CHECK (amount_vnd > 0),
  content TEXT,
  reason TEXT NOT NULL CHECK (reason IN
    ('unknown_ref','amount_mismatch','order_cancelled','order_expired','late_transfer','duplicate_ref')),
  status TEXT NOT NULL DEFAULT 'open'
    CHECK (status IN ('open','linked','refunded','dismissed')),
  user_id TEXT,
  linked_order_code BIGINT,
  resolution_ref TEXT,
  resolved_by TEXT,
  resolved_at TIMESTAMPTZ,
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_rex_ref UNIQUE (provider, rail_txn_ref),
  CONSTRAINT chk_rex_resolved CHECK (status = 'open' OR (resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE INDEX idx_rex_open ON rail_exceptions (provider, opened_at) WHERE status = 'open';

CREATE TABLE dispute_cases (
  id TEXT PRIMARY KEY,
  provider TEXT NOT NULL CHECK (provider IN ('stripe','googlepay','applepay')),
  order_code BIGINT NOT NULL,
  rail_dispute_ref TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'open'
    CHECK (status IN ('open','evidence_submitted','won','lost')),
  deadline_at TIMESTAMPTZ,
  loss_vnd INT CHECK (loss_vnd > 0),
  evidence_ref TEXT,
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ,
  CONSTRAINT uniq_dc_ref UNIQUE (provider, rail_dispute_ref),
  CONSTRAINT chk_dc_deadline CHECK (status IN ('won','lost') OR deadline_at IS NOT NULL)
);
CREATE INDEX idx_dc_open ON dispute_cases (deadline_at) WHERE status IN ('open','evidence_submitted');
```

## RLS and grants

```sql
-- Policy pattern (own name per table): ops/service contexts only
CREATE POLICY pe_ops ON provider_events FOR ALL TO app_role
  USING (current_setting('app.context', true) IN ('ops','service'))
  WITH CHECK (current_setting('app.context', true) IN ('ops','service'));
```

Repeated for `rail_exceptions` (`rex_ops`) and `dispute_cases` (`dc_ops`); `rail_exceptions` adds an owner SELECT policy (`user_id = current_setting('app.user_id', true)` — null user rows stay invisible to owners). RLS enabled on all three. Grants: SELECT/INSERT/UPDATE (no DELETE anywhere).

## Semantics

- Insert-first anti-replay (D-1): the intake transaction starts with `INSERT ... ON CONFLICT (provider, event_ref) DO NOTHING`; a conflict reads the stored outcome and returns the same response with zero side effects; the unique index blocks concurrent duplicates until the first transaction resolves (rollback frees the slot).
- `outcome` progresses `processing → granted|duplicate|rejected|exception|ignored`; `order_code` fills on resolution; rows are the audit-grade evidence (24 mo retention) — payload bytes are never stored, only refs + hashes.
- Exceptions: opened by intake when binding fails (unknown ref, amount mismatch, cancelled, expired, duplicate content); `user_id` fills at link time from the matched order. One row per `(provider, rail_txn_ref)`; a second transfer reusing a ref lands `duplicate_ref` through the same unique index.
- Link (D-4): the ops route runs the guard with ops authority + reason; on success the same transaction flips the exception to `linked` + `linked_order_code` + resolver fields and writes `rail.exception_linked`. Refund/dismiss set status + `resolution_ref` with `chk_rex_resolved` forcing resolver data; all mutations audit.
- Dispute lifecycle (D-5): opening emits the guard edge to `disputed` + audit `dispute.opened`; evidence submission sets `evidence_submitted` + `evidence_ref` + audit; won/lost apply the guard edge + case status + audits (`dispute.won`, `dispute.lost`, `plan.chargeback_reversed` via 38's helper) with `order.chargeback_reversed.v1` enqueued post-commit on loss.
- Sweeps leave no new table: stale pendings come from `orders` (33/34 indexes); exception pulls upsert by `uniq_rex_ref`; the console's sweep log is derived from audits + metrics.
- No new Redis keys: per-order verify throttle reuses `bill:rl:verify:{order}`; wallet charge serialization uses the order row lock (34's locking order).

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V7 runs after V6; 3 tables + 5 indexes + RLS visible; zero `orders` DDL statements in V7 |
| Replay collapse | Same `(provider, event_ref)` posted 10× concurrently → 1 row, 1 outcome, identical responses |
| Guard ownership | grep: no `orders.status` writes outside 34's guard module (rail code included) |
| Exception uniqueness | Same rail txn twice → one row; second transfer lands `duplicate_ref`; resolved rows demand resolver data (`chk_rex_resolved`) |
| Deadline CHECK | Open case without `deadline_at` fails; `won`/`lost` without resolution fields fails |
| Grace precision | Link allowed at 23 h 59 m, refused at 24 h 01 m (`billing.rails.lateTransferGraceHours`) |
| Privacy scan | No payload bodies, tokens, PANs, or cryptograms in the three tables (fixture scan + log scan) |
| RLS probes | Ops/service contexts see queues; owner reads own exceptions only; disputes and events read zero rows outside ops/service |
