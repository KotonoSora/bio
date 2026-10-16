# Checkout Session - Acceptance Criteria (feature 33)

Phase 1 BA · BDD; per-rail drill rows mirror the shared conformance matrix (32 BR-11).

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | No-default drill | a create with omitted `provider` and a quote with no `provider` | create and quote run | create returns 422 with the eligible list (never a silent fallback); quote previews all eligible without creating |
| AC-02 | Matrix drill | plan/product × registry-member config | each cell is exercised over the API on all eight paths | allowed cells pass, rejected cells fail, and no ineligible member is reachable |
| AC-03 | Display-order + capability drill | config present, then absent; an incapable and a capable device | `payment-methods` renders and eligibility evaluates | rendered order matches config (or API order verbatim when absent) with no re-sorting; wallet members are excluded server-side when incapable and included with correct `handoffKind` when capable |
| AC-04 | Quote-charged identity | quote across plan/product × provider × coupon cells | order is created, including with tampered client amounts | quote amount equals `amountVnd` to the VND; tamper is re-priced server-side and logged; a mismatched `quoteToken` refuses with 409 |
| AC-05 | Double-submit drill (per handoff kind) | same key/fingerprint, a key across intents, and a keyless double-click | creates run | same key/fp → single row + same handoff; key reuse across intents → 422; keyless → single row within the 90 s window |
| AC-06 | Validation drill | ineligible provider, bad coupon, malformed email, off-origin URL, missing ship-to for merch | create runs | each returns 422 with no order row and no catalog leakage |
| AC-07 | Expiry drill | an unpaid pending order | the sweep runs, including reruns, then a late confirmation arrives | flips to `expired` once; rerun no-op; expired UX offers new checkout only; late money grants nothing and logs |
| AC-08 | Cancel-race drill | cancel concurrent with provider confirmation | both run | exactly one terminal outcome per 34's edges; no resurrection; already-cancelled cancel is idempotent 200 |
| AC-09 | Return drill | forged `?status=paid` params with no provider confirmation | return handling runs | no row changes; all redirect rails converge on the one pending page with canonical rendering |
| AC-10 | IDOR/RLS probe | user A quotes/creates/reads/cancels user B orders; guest rows probed from sessions | operations run | 403 + RLS deny; guest rows are invisible to sessions by construction |
| AC-11 | Guest drill | a guest product checkout and a guest tip checkout via shop's endpoint | orders are created and paid | one order per intent with `kind` set; `guest:` owner id minted; no account created; receipt queued; no claim path exists |
| AC-12 | Baseline probes | cross-user access, rendered quote/pending/error output, anonymous endpoints, repo/logs/bundles under secrets grep | IDOR, XSS, abuse, and secrets probes run | 403 + RLS deny; XSS-stripped rendering with CSP holding; Turnstile + WAF throttling; secrets grep clean |
