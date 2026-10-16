# Resume Templates - Exception Handling and Errors (feature 18)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/18-resume-templates/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Unknown `templateId` on write | API | `422 unknown_template`; selection unchanged | "This template isn't available any more - the gallery was refreshed." |
| Premium template selected without entitlement | API | `403 template_forbidden` (server-side gate; fail-closed on snapshot miss); selection rolled back | "This template is part of a plan - see upgrade options." |
| Renamed/removed id on read | API | Fallback to `classic` + `template.fallback` metric; page renders | "Showing the classic layout - this template is no longer available." |
| CSS/JS injection attempt via any resume field | Render | Stripped at capture (feature 17) and neutralized in renderers; style attributes from data dropped; CSP `default-src 'self'` holds | No visible error; content renders as text |
| Print without a saved snapshot | UI | Print button unavailable on draft; `Save Snapshot first` flow | "Save a snapshot first - printing always uses a frozen version." |
| Save lands mid-preview | UI | Preview keeps its bound version; notice shows | "Snapshot changed - re-open preview to print the new version." |
| Version deleted while preview is open | UI | Preview invalidated; timeline jump offered | "Version no longer exists." |
| Print dialog cancelled | UI | Nothing persisted or lost | No message |
| Registry id without renderer/thumbnail at build | CI | Build fails (consistency gate); never reaches runtime | Build error: "registry id {id} has no renderer/thumbnail in this bundle" |
| PDF dependency proposed (server or client) | CI | Grep gate fails the build; no exception path | Build error: "PDF dependency detected - export is window.print() only" |
| cv requested for private/unpublished document | Public | Feature 10 parity `404`; template never renders draft or private content | "This site isn't available." (feature 10 copy) |
| Thumbnail pipeline fed a real snapshot | CI/test | Rejected by construction (placeholder input type); pixel-scan catches regressions | Build/test error: "thumbnail input contains user data" |
| Entitlement snapshot unreachable at selection | API | Fail-closed to free: non-premium selection proceeds; premium denied | "Couldn't verify your plan - premium templates are unavailable right now." |
| Boot validation failure (duplicate id, unused token) | Boot | Service fails to start (fail-fast); caught in CI before release | Boot error naming the offending token/id |
