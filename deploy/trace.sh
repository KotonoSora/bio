#!/bin/sh
# Traceability (05-delivery-phasing-and-scaffold.md): feature number -> images
# (tags) -> Flyway versions -> drill results. Prints the mapping from repo state
# plus archived release records, so support answers "which build shipped
# feature X" from the tag alone.
set -e
cd "$(dirname "$0")/.."
echo "== feature -> service -> image =="
echo "01-03,05,45  account-service       image=resume/account-service       db=account_db"
echo "04,06-09,27,28 profile-service     image=resume/profile-service       db=profile_db"
echo "29-30        link-service          image=resume/link-service          db=links_db"
echo "10-11        public-site-service   image=resume/public-site-service   db=(stateless)"
echo "12-15,31,47  analytics-service     image=resume/analytics-service     db=analytics_db"
echo "17-21        resume-service        image=resume/resume-service        db=resume_db"
echo "22-26        shop-service          image=resume/shop-service          db=shop_db"
echo "32-40,46     billing-service       image=resume/billing-service       db=billing_db"
echo "41-44        publish-service       image=resume/publish-service       db=publish_db"
echo "edge         gateway               image=resume/gateway               db=(none)"
echo ""
echo "== service -> flyway versions (forward-only) =="
for d in services/*/ gateway/; do
  [ -d "$d/src" ] || continue
  name="$(basename "$d")"
  if [ -d "$d/src/main/resources/db/migration" ]; then
    vers="$(ls "$d/src/main/resources/db/migration"/V*.sql 2>/dev/null | xargs -n1 basename 2>/dev/null | tr '\n' ' ')"
    echo "$name: $vers"
  else
    echo "$name: (no database)"
  fi
done
echo ""
echo "== archived release records =="
ls deploy/releases/ 2>/dev/null || echo "(none yet - sh deploy/tag.sh [semver])"
echo ""
echo "== archived drill logs =="
ls drill-log/ deploy/drill-log/ 2>/dev/null || echo "(none yet - sh deploy/run-drill.sh <drill>)"
