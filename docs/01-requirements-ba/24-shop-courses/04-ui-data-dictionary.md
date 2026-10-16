# Course Builder - UI Data Dictionary (feature 24)

Phase 1 BA · Builder (dashboard), player (SPA), public syllabus, certificate page.

## Curriculum builder (dashboard)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Section row | list item | yes | ≤ 50 sections; drag reorder (dense positions) | - |
| Section title | text input | yes | ≤ 120 chars | "Section {n}" |
| Lesson row | list item | yes | ≤ 200 lessons; drag across sections | - |
| Lesson title | text input | yes | ≤ 120 chars | - |
| Video upload | file input | no | MP4 ≤ 2 GB; readable; replaced on re-upload | duration shown once parsed |
| Lesson body | textarea | no | ≤ 50 000 chars; sanitized markdown | - |
| Materials | file list | no | ≤ 10; ≤ 500 MB each; digital allowlist | - |
| Preview toggle | switch | - | ≤ 10 per course | off |
| Completion rule | select | yes | `auto90` / `explicit` | `auto90` |
| Publish button | primary button | - | blocked with named lessons when incomplete | - |

## Player (SPA, `/app/courses/{productId}`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Video canvas | HTML5 video | - | ticket-based; range requests; no download button | poster from first frame |
| Resume banner | notice | - | visible when stored position > 5 s and < 95% | "Resume at {mm:ss}" |
| Lesson list | sidebar | - | completed checks, current highlight, preview badges | - |
| Progress bar | progress | - | per-lesson + course % | server truth |
| Mark complete | button | - | shown only under `explicit` rule | - |
| Materials list | list | - | per-item download (single-use link) | - |
| Certificate banner | notice | - | appears at 100% with the `/c/{code}` link | - |

## Public syllabus (product page, feature 23 route)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Syllabus accordion | list | - | section + lesson titles; durations; preview badges | SSR, SEO-visible |
| Preview player | inline player | - | only `is_preview` lessons; streams without enrollment | - |
| Locked lesson row | list item | - | lock icon; taps lead to buy/enroll | never streams |
| Enroll / Buy CTA | button | - | free → one-click enroll (session); paid → checkout | - |

## Certificate page (`/c/{code}`)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Holder name | text | - | display name at mint time | - |
| Course title | text | - | title at mint time (snapshot) | - |
| Completion date | text | - | `completed_at` date | - |
| Code | text | - | tamper-evident random code | copyable |
| Print | button | - | `window.print()` | - |

## Enrollment / progress views (dashboard, creator)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Enrollment count | text | - | aggregate only | - |
| Completion % | progress | - | aggregate; never named-student positions | - |
| Refunded count | text | - | aggregate | - |
