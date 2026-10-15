# Scale Out (execution plan E5 — replicas, ISR, gates, drills)

PLAN ref: P5-1 (read replicas, Hyperdrive read/write split) + P5-2 (ISR pre-render top slugs, TTL 300s) + P5-3 (k6 10k→100k→1M+100k, p95 <500ms reads / <1s checkout) + P5-4 (quarterly restore + rotation) + §0 leave-Cloudflare signals. BA source: `docs/PLAN.md` P5; consumed increments E0–E4. GATED: only after the P3 100k-mixed gate passes. DEVIATION CARRIED: container-native Postgres replicas + cache/CDN pre-render replace Neon/Hyperdrive/Workers-ISR defaults; signal thresholds and gate numbers retained; contract frozen.

## Purpose

Scale the monolith without re-architecting it: read replicas for public reads, pre-rendered top slugs with longer TTL, staged k6 gates with teeth, and quarterly restore/rotation drills that prove durability. E5 exits with 1M read-heavy + 100k mixed green and a logged restore — or the phase does not ship.

## Users

- Platform engineers: provision replicas, publish pre-renders, run k6, execute restore drills.
- Ops: watches replica lag, cache-hit, queue lag, and_dates rotation runbooks.
- All feature teams: no code changes required — E5 is topology + caching + proof.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for E5 — scaled monolith (horizontally replicated stateless app + read replicas), not microservices. Monolith pros at scale: writes stay on the primary (no distributed write conflicts), public reads split by datasource role (primary for dashboard/money, replica for anonymous SSR/shop reads), one migration pipeline, one backup/restore story. Microservices cons avoided: decomposing for scale alone would trade proven cache/replica levers for distributed transactions and multi-log audit with no ownership/isolation trigger firing. Split carve-outs allowed only per the recorded P4a/P4b triggers (fan-out/watcher/reporting read-model) with their mitigations — E5 itself adds no service boundaries. Leave-signal responses: queue backlog sustained → consumer parallelism/partitions first; primary CPU >70% p95 → replicas for public reads (writes stay primary); top-1% slugs hot → ISR pre-render + longer TTL.
2. Read path: replica set for anonymous SSR/shop/link reads with lag monitoring (target <1s at 1M concurrent reads); datasource routing by role (read/write split in config, audited flips); sticky-primary for owner/money paths; replica-lag header/metric consumed by the render path (stale-beyond-budget falls back to primary rather than serving wrong prices/entitlements).
3. Pre-render path: top-10k slugs pre-rendered to blob storage with TTL raised to 300s; publish/snapshot events revalidate affected keys; KV-era single-flight already in B1 carries over to the cache layer; cache-hit >95% with origin req/s flat across 10x traffic.
4. Load gates (k6, staging mirror, blocking): P1 exit 10k concurrent reads p95 <500ms (already held — re-proven); P3 exit 100k normal-use mix; P5 exit 1M read-heavy + 100k mixed with p95 <500ms reads / <1s checkout. Red anywhere blocks the phase; gate reports attach numbers, not pass/fail.
5. Durability drills (quarterly, logged): base backup + WAL replay point-in-time restore on a clean volume (RPO/RTO recorded before first prod deploy); secret rotation (`TOKEN_ENC_KEY`-equivalent, PSP keys, datasource creds) without downtime, old credentials dead; rotation + restore runbook green each quarter.
6. Container per release: E5 manifests (app replicas + primary + replica set + cache) boot via compose/orchestrator in both Docker and Podman families per `docs/platform/containers/container-lifecycle.md`; same-digest promotion dev → staging → prod per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Topology: one primary + N replicas (PG `18.6` throughout); per-module schemas unchanged; no cross-writes introduced; pre-render blobs addressed by slug + revision with purge parity.
- Observability: Micrometer + Prometheus per instance; dashboards for replica lag, cache-hit, origin req/s, consumer lag, k6 percentiles; slow-query log reviewed at each gate; size math re-checked (20–50KB/user → 200–500GB at 10M; overflow paths evidenced).
- Config: read/write routing, TTL profiles, pre-render top-N list, k6 profiles — all versioned config, never code constants.

## Acceptance criteria

- Replica drill: lag <1s at 1M concurrent reads; money/owner paths never served stale from replicas.
- ISR drill: top-slug traffic served pre-rendered with >95% hit; publish visible within SLA; origin req/s flat as traffic 10x.
- Gate drill: full k6 sequence green with attached numbers; any red blocks promotion by tooling.
- Restore drill: point-in-time recovery succeeds on clean volume; quarterly log signed.
- Rotation drill: all money/session/vault secrets rotated without downtime; old credentials dead.
- Container drill: full E5 topology boots under both runtimes; previous-tag rollback green; no E4-backlog or growth-major tables/jobs appear (migration + grep audit).

## Out of scope

- Feature behavior (all owning majors — E5 changes topology, never semantics); P4 split executions (see `docs/platform/delivery/publish-automate.md`, `docs/platform/delivery/agency-teams.md`); growth majors (affiliate/advertising/sales/marketing/data-analysis/user-behavior — build-gated backlog needing plan updates, never smuggled into scale work).

## Open questions

- Q1: K8s vs Compose for first prod topology — minimal supported orchestrator in v1?
- Q2: Managed PG allowed for staging replicas, or container-primary everywhere (decided once with the PG-baseline Q2)?
