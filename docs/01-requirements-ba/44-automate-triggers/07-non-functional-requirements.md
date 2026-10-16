# Automate Triggers - Non-Functional Requirements (feature 44)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/44-automate-triggers/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Watcher latency | Webhook → match decision ≤ 5 s p95 (queue-mediated); trigger-log row written with the decision |
| NFR-02 | Matching determinism | Identical inputs → identical decisions (case-fold + trim only); first-match by rule creation order, recorded; zero fuzzy logic exists |
| NFR-03 | Delay precision | `delay_s` ≥ 60 enforced server-side (±2 s scheduling precision) |
| NFR-04 | Delivery | All-gates-pass → `dm_sent` ≤ 10 s p95 (provider-bound); public reply + DM are one intent, ordered reply-first |
| NFR-05 | Fire caps | Per automation+media ≤ `fireCapPerHour` (60 default, 10..200) rolling hour; overflow releases in order; cap state visible |
| NFR-06 | Pacing | Per account: burst ≤ 10/min, sustained ≤ 30/min (token bucket); caps compose with fire caps (both enforced) |
| NFR-07 | Quiet hours | Creator-local 22:00–08:00 default; release in order at window end ±60 s; no send inside the window |
| NFR-08 | Window | 24 h from the triggering comment, minute precision; `window_lapsed` is terminal (zero retries) |
| NFR-09 | Quotas | Contacts (43's function) and rule quotas enforced pre-send/save; unique recipient per period counts once |
| NFR-10 | Burst-abort | Trigger ≤ 60 s after the 10-minute window breaches (> 20% failures, min 20 attempts); page + creator notice ≤ 60 s |
| NFR-11 | Queue discipline | Streams `automate.watch`/`automate.delivery` with consumer groups; XAUTOCLAIM ≤ 60 s; max-delivery 5 → DLQ; replay-safe consumers |
| NFR-12 | Audit retention | Trigger-log rows (decisions) + intent transitions + pause/reinstate/review retained 24 months; comment bodies never stored (opaque refs + decisions only) |
| NFR-13 | Privacy | Opaque refs; no comment text in logs/metrics beyond the match decision; zero token material; RLS owner-scoped |
| NFR-14 | Degrade | Provider DM API down → intents retry bounded then `failed` honestly; Redis down → watcher/delivery halt loudly (no DB-poll fallback fork); 43's gate down → sends refuse (fail closed); webhook outage → nothing queues |
| NFR-15 | Observability | Metrics: matches by kind, holds by reason, sends, failures by class, cap overflow depth, auto-pauses, review queue age — zero message bodies |
