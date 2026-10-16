# Account and Access - UI Data Dictionary (feature 01)

Phase 1 BA · Fields the SPA renders for auth, sessions, and account email. Contracts and validation hardened in `docs/02-architecture-techlead/01-account-and-access/`. Rules: `docs/01-requirements-ba/01-account-and-access/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Login screen - provider buttons | Button set | One click to proceed | Only enabled providers render (config-driven); no email/password or reset field exists on any screen | All four providers enabled at launch; disabled ones hidden |
| Login screen - `callbackURL` | Hidden request param | No | Same-origin allowlist only; off-list values rejected server-side | `/app` |
| Login screen - failure notice | Inline banner | Auto | Generic wording only (`auth_failed`); never reveals account existence or provider detail | Shown on failed callback redirect |
| Step-up dialog - action line | Text | Auto | Names the pending sensitive action; freshness window (10 min) stated | Appears before the re-auth redirect |
| Step-up dialog - re-auth buttons | Button set | One click | Same provider set as login | Returns to the interrupted action on success |
| Providers list (settings) | List rows | No | One row per provider: linked state, Link or Unlink action | Unlink disabled with hint when it is the last linked provider |
| Unlink confirmation | Dialog | Yes to unlink | States effect and that step-up is required when stale | Cancel is default focus |
| Sessions list - device label | Text | Auto | Coarse UA family label only; raw UA never stored | "Unknown device" when unparsed |
| Sessions list - ip class | Text | Auto | Masked class (`/24` v4, `/48` v6); stored hashed | Masked form only |
| Sessions list - last active | Text | Auto | Coarse buckets: Active now / Today / This week / Earlier | Derived from `last_active_at` |
| Sessions list - current marker | Badge | Auto | Marks the session issuing the request | Current session cannot be revoked via "all others" |
| Revoke / Revoke all others | Buttons | Confirm | Effect within 60 s stated; revoke-all-others never includes current | Single confirm step |
| Contact email form | Input + submit | No | Valid email syntax; uniqueness vs other accounts' verified contact emails (`409 email_conflict`); code mailed on submit | Empty until set; IdP email shown separately as read-only source |
| Verification code input | 6-digit field | Yes when verifying | Digits only; 15 min TTL, 5 attempts; resend allowed after 60 s | Cleared on failure; attempt counter shown after third miss |
| Email status chip (account screen) | Status chip | Auto | Values: verified / not verified / relay address / conflict; explains the checkout requirement when missing | Conflict variant pairs with the notice banner |
| Email conflict notice | Banner | Auto | States that the IdP email is already used by another account and offers the contact-email path | Dismissible per session; account remains fully usable |
| Onboarding redirect target | Route (no UI) | Auto | No profile yet -> choose-username screen (feature 06 owns rules); otherwise the requested page | `/app` for returning users |
| Recovery invitation landing | Route + status text | Auto | Single-use token; expired/reused shows generic invalid state with support reference | No credential fields exist on this screen, ever |
