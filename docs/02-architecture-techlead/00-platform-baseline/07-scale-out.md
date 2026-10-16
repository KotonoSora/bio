# Platform Baseline - Scale Out (E5)

Phase 2 TL · Cross-cutting baseline. GATED: runs only after the E3 100k-mixed load gate passes (`05-delivery-phasing-and-scaffold.md`); before that, the horizontal numbers are targets, not promises. Scale-out never re-architects services: it adds replicas, read/write splits, and pre-render - the compose slice stays the contract (`04-containers-and-compose.md`).

## Replicas and read/write split

| Path | Scale move | Trigger |
|---|---|---|
| `public-site-service` (SSR + assets) | N stateless replicas + edge cache; cache hit >= 95% on profile paths | p95 > 300 ms or CPU > 70% sustained |
| `link-service` redirects | N replicas; redirect path stays cache-first with TTL-only correctness | p95 > 200 ms on `/go/*` |
| `analytics-service` reads | Replica set for owner reads; intake stays write-path isolated | read p95 > 300 ms at recorded load |
| `profile-service` writes | Vertical first; replicas only after a doctrine review (writes are the contended path) | write p95 > 300 ms or lock wait growth |
| PostgreSQL | Per-service read replicas with lag-aware routing for read-only endpoints; writes stay on primary | replica lag <= 5 s alert bound; read routing only where the spec allows staleness |
| Redis queues | Consumer parallelism per stream; backlog responses documented per service | queue lag > 60 s sustained on any stream |

- Read replicas serve only endpoints whose specs declare staleness tolerable (owner reads with lag labels); session/money writes never route to replicas.
- Replica lag is a first-class metric; exceeding the bound stops read routing (fail back to primary) instead of serving stale money.

## Pre-render (public reads)

- Pre-render top slugs (top 10k by traffic) at publish/purge time; CDN/edge TTL 300 s; publish purges by tag pattern; rename/retire purges all prior slugs.
- Cache correctness rule: TTLs bound staleness, purge events shorten it; a purge miss heals within 60 s (platform default).
- Pre-render failure degrades to origin SSR - never to stale variants of a removed profile (410/404 parity preserved).

## Load gates (k6, seeded fixtures)

| Stage | Load | Acceptance |
|---|---|---|
| 10k reads + 1k mixed | warm caches | p95 < 500 ms reads; checkout p95 < 1 s; zero 5xx |
| 100k reads + 10k mixed | includes beacon burst | p95 < 500 ms reads; queue lag bounded; no PG write amplification beyond spec |
| 1M read-heavy + 100k mixed | production-shaped | p95 < 500 ms reads; checkout p95 < 1 s; replica lag <= 5 s; restore drill logged in the same quarter |

- Each stage runs on the same digests as production candidates; failures block the phase, not just the release.
- Beacon/intake paths assert the spec'd invariants under burst (uniform 202, dedupe collapse, zero per-hit PG writes for analytics).

## Durability and operations

- Quarterly restore-to-scratch (RPO <= 15 min, RTO <= 4 h proof) and secret rotation drills (`06-release-gates.md`).
- Capacity review per stage: recorded headroom >= 30% on CPU/memory/connections at gate load; the next stage's provision plan cites the review.
- Degrade ladder: shed non-critical features first (realtime, exports, heavy reads), protect income paths (checkout, grants, redirects), then public SSR; each service spec names its first shed candidate.
- Leave/up-scale signals: sustained breach of any gate for 30 min triggers a capacity action item; repeated breach across two quarters triggers an architecture review against the extraction doctrine (`01-architecture-and-service-topology.md`).
