# Custom Domains - UI Data Dictionary (feature 04)

Phase 1 BA · Fields the SPA renders in domain settings and the neutral error page. Contracts: `docs/02-architecture-techlead/04-custom-domains/`. Rules: `docs/01-requirements-ba/04-custom-domains/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Domains section (settings) | Panel | Auto | Pro-only surface: on free, shows the feature-03 upgrade path instead of a fake form | Hidden on free tier (honesty, not gate) |
| Add hostname input | Text field | Yes to add | Normalized lowercase; IDN accepted, punycode preview shown; wildcards rejected inline | Empty; add disabled until valid |
| TXT record display | Copy rows (name + value) | Auto | `_rb-challenge.<host>` + one-time token; copy buttons; value never re-shown after the round ends | Shown once per challenge round |
| Verify button | Button | No | On-demand check; rate-limited; disabled while a check runs | Enabled once the record is expected set |
| Regenerate challenge | Link | No | Issues a fresh round; old token dies | Visible after expiry or suspected leak |
| Status chip | Chip | Auto | Values: pending / verified / suspended / unverified; suspended shows the restore path; unverified shows re-verify + 14-day release note | Rendered per binding state |
| Lapse notice | Banner | Auto | DNS untouched; restore within 30 days needs no re-verify; states the `<= 60 s` serving-stop fact in plain words | Shown on suspended |
| Remove confirmation | Dialog | Yes to remove | States immediate deletion, no hold, serving halts at once | Cancel is default focus |
| Last recheck row | Text row | Auto | Last recheck time + result; no internal details | "Not checked yet" before first recheck |
| Serving preview link | Link | No | Opens the custom host in a new tab when verified | Hidden otherwise |
| Neutral error page (public) | Page | Auto | 404, neutral copy, no profile content, no open redirect, no host echo beyond the requested host | Served for unknown/unverified hosts |
| Reclaim landing (support) | Read-only rows | Auto | Dispute evidence reference + round status; ops-only, audited | Reached via support tooling |

Copy discipline: none of these fields promise anything beyond BR-01..BR-12; amount-free (plan facts come from feature 03).
