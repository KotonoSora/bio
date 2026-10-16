# Deep Links and PWA - Exception Handling and Errors (feature 11)

Phase 1 BA · Copy is exact user-visible text (Vietnamese default, English fallback). `(owned)` marks messages defined by another feature and only referenced here.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Webview UA missing from the pattern list | Happy-path degrade | No banner; content fully usable; pattern list extended by release (advisory rule) | (none) |
| Clipboard write blocked by webview | Banner fallback | Selectable URL shown + manual instruction; action retryable | "Copy does not work here - select the link and copy it" |
| Escape action blocked (scheme ignored) | Banner primary | Stay on page; copy fallback remains; no redirect loop | "If nothing opens, copy this link" |
| Hostile scheme in block URL (`javascript:`, `data:`, custom) | Authoring | Rejected at save (`bad_url_scheme`) (owned by 08); renderer re-asserts allowlist, else renders plain text | (blocks-owned) |
| Deep-link rewrite target invalid (deleted video, private post) | Render | Emit canonical `https:` form; the destination app shows its own error | (none) |
| Service worker registration fails | PWA boot | Dashboard runs as a normal web app; install CTA hidden; single console warning, no toast spam | (none) |
| Missing PWA icon or manifest entry | Build/boot | Build fails; release blocked | (CI output) |
| Worker serves an `/api/*` response from cache (violation) | Runtime drill | Release blocked; interceptor default for unlisted paths is network | (CI output) |
| New worker version deployed | PWA runtime | Update pill; atomic cache swap on restart | "Update available - restart" |
| Offline | PWA runtime | Shell + stale warning attached to every figure | "Showing last known figures - offline" |
| Analytics API unreachable (online) | Widget | Standard analytics error/empty state (owned by 12-15); never fabricated numbers | (analytics-owned) |
| Session expired | PWA deep link | Redirect to SSO sign-in; return to the same route | (auth-owned) |
| iOS install (no `beforeinstallprompt`) | Install flow | One-time dismissable instruction card | "Add to Home Screen via Share" |
| Already installed | Install flow | CTA hidden; no nagging | (none) |
| Unknown public slug under SPA fallback | Routing | 404 page; SPA HTML is never served for slugs | (standard 404) |
| Device matrix red on any device | Release gate | Promotion blocked with the failing device recorded | (release notes) |
