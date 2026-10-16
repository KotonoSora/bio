# Share and SEO - Exception Handling and Errors (feature 10)

Phase 1 BA · Path types: Happy / Alternative / Exception. The public error surface is deliberately minimal: `200`, `301`, `404`, `503` - codes exist internally, never as oracle signal.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Cold load of a public profile | Happy | Edge hit or single-flight render; full tags + content | page renders without JS |
| Rename hit through the cache layer | Happy | `301 /{newUsername}` (cacheable as permanent) | browser lands on the new page |
| Unknown slug | Alternative | Parity `404`, `private, no-store`, same shape/timing as private | branded 404 page |
| Private / suspended / retired slug | Alternative | Identical parity `404`; zero distinction from missing | branded 404 page |
| `/cv` without `showResume` or snapshot | Alternative | Parity `404` (snapshot contract owned by features 17-21; absent until then) | branded 404 page |
| Publish, purge delivered | Happy | Slug keys purged; chip clears when live serves fresh content | chip clears |
| Purge event lost | Alternative | 60 s service TTL + SWR revalidate; <= 2 min total | chip clears within 2 min |
| Still pending after 2 min | Alternative | Poll stops; honest copy; change is queued server-side | "Still publishing - safe to leave." |
| Cold-miss storm on one slug | Alternative | Single-flight: one render, rest coalesce | none (all visitors get the page) |
| Origin/cache failure with stale available | Alternative | `stale-while-revalidate` window serves stale <= 600 s | none (possibly older content) |
| Origin failure without stale | Exception | Branded `503`; never a tagless SPA fallback | "Temporarily unavailable - retry in a moment." |
| Render deadline exceeded (internal calls slow) | Exception | Stale served if present; else `503`; render attributed in metrics | "Temporarily unavailable." |
| Preview without session | Exception | `401` -> re-login; returns to preview | "Please sign in to continue." |
| Preview of a non-owned profile | Exception | Parity `404` (identical to unknown) | branded 404 page |
| Crawler fetches sitemap while a profile is private | Alternative | Slug absent; sitemap valid with remaining public slugs | none |
| Sitemap with zero public profiles | Alternative | Valid empty `urlset` (never an error) | none |
| Missing description in checklist | Alternative | Amber row + deep link to the `seoDesc` field | "No description yet." |
| Abuse burst on public routes | Exception | WAF/rate rules -> `429` | "Too many requests. Try again shortly." |
| Forged stored payload (markup in stored fields) | Alternative | Escaped at render; CSP + escaping tests hold; authoring rejected it at write | none |
| Client probes for a purge endpoint | Exception | `404` (no such route exists) | branded 404 page |
