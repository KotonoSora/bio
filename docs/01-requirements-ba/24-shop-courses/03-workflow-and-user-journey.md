# Course Builder - Workflow and User Journey (feature 24)

Phase 1 BA · System calls cite owning features; forward contracts cite 32-40.

## Creator: build → preview → publish

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | creates a `course` product (feature 22) and opens the builder | empty outline; curriculum saves as a full tree | BR-01 |
| 2 | Creator | adds sections + lessons, uploads video/materials | bounded + magic-byte validation; lesson named on failure | BR-02 |
| 3 | Creator | picks preview lessons (0-10) and the completion rule | flags stored; preview metadata lands in the SEO syllabus | BR-03/06 |
| 4 | Creator | preview-as-student | owner preview mode plays every lesson | BR-16 |
| 5 | Creator | publishes (feature 22 status → live) | precondition check; incomplete curricula refused naming lessons | BR-14 |
| 6 | System | publish event absorbed by public-site | product page purges ≤ 60 s; syllabus + previews render | BR-03 |

## Student: enroll → watch → resume → complete

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Student | taps Enroll on a free course | idempotent enrollment row created (source `free`) | BR-04 |
| 2 | Student | buys a paid course (checkout 32-40) | grant → enrollment (source `paid`) | BR-04 |
| 3 | Student | plays a gated lesson | stream ticket minted after an enrollment check (TTL ≤ 4 h) | BR-10 |
| 4 | Player | heartbeats every 15-30 s + on pause/seek | Redis-coalesced write; DB flush ≤ 30 s | BR-05 |
| 5 | Student | switches device | resume banner from the merged server position | BR-05 |
| 6 | Student | finishes the last lesson (rule-dependent) | completion 100%; certificate minted once; banner shown | BR-06/07 |
| 7 | Verifier | opens `/c/{code}` | name + course + date, or 404 when revoked | BR-07 |

## Materials

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Student | taps a material | single-use signed link (≤ 15 min) after the enrollment check | BR-10 |
| 2 | Non-student | requests the same link endpoint | 403 `enrollment_required`; zero bytes | BR-03/12 |

## Refund / courtesy / deletion

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | refund/chargeback (`order.refunded.v1`, `order.chargeback_reversed.v1`; forward 38/40) | enrollment + certificate revoked; progress kept 90 days | BR-08 |
| 2 | Student | re-enrolls within the window | progress resumes; a new enrollment row is created | BR-08 |
| 3 | Purge runner | window expires | progress purged by the sweep | BR-08 |
| 4 | Purge runner | buyer account deletion | enrollments + progress deleted; certificates revoked | BR-09 |
| 5 | Purge runner | seller account deletion | curriculum, materials, video files, dependent rows deleted with counts | BR-09 |
