#!/bin/sh
# G1 supply-chain gate: scan CycloneDX SBOMs for High/Critical findings.
# Grype image is resolved at runtime (GitHub latest release -> digest pin) so no
# scanner version is ever guessed; the digest lands in the gate report.
# Waivers: deploy/waivers/*.txt lines `CVE-ID|YYYY-MM-DD|reason`; unexpired
# entries downgrade to WARN (signed, time-boxed per the gate table).
set -e
cd "$(dirname "$0")/.."
GRYPE_TAG="${GRYPE_TAG:-$(curl -sf https://api.github.com/repos/anchore/grype/releases/latest | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')}"
[ -n "$GRYPE_TAG" ] || { echo "FAIL: cannot resolve grype release"; exit 1; }
DIGEST="$(docker buildx imagetools inspect "anchore/grype:$GRYPE_TAG" --format '{{json .Manifest}}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["digest"])')"
echo "grype $GRYPE_TAG @ $DIGEST"
echo "$DIGEST" > deploy/.grype-digest
BOMS="$(ls services/*/build/reports/bom.json 2>/dev/null || true)"
[ -n "$BOMS" ] || { echo "FAIL: no SBOMs (run ./gradlew cyclonedxSbom)"; exit 1; }
fail=0
for bom in $BOMS; do
  name="$(echo "$bom" | tr '/' '_')"
  if ! docker run --rm -v "$PWD:/w" "anchore/grype:$GRYPE_TAG@$DIGEST" "sbom:/w/$bom" -o json > "/tmp/grype-$name.json" 2>/dev/null; then
    echo "FAIL scan: $bom"; fail=1; continue
  fi
  HITS="$(python3 -c '
import json, sys, glob, datetime
rep = json.load(open(sys.argv[1]), strict=False)
waived = {}
for f in glob.glob("deploy/waivers/*.txt"):
    for line in open(f):
        parts = [p.strip() for p in line.split("|")]
        if len(parts) == 3 and parts[1] >= datetime.date.today().isoformat():
            waived[parts[0]] = parts[2]
bad = []
for m in rep.get("matches", []):
    sev = (m.get("vulnerability") or {}).get("severity", "")
    cve = (m.get("vulnerability") or {}).get("id", "")
    if sev in ("High", "Critical") and cve not in waived:
        bad.append(f"{cve}({sev})")
print(" ".join(bad))' "/tmp/grype-$name.json")"
  if [ -n "$HITS" ]; then echo "FAIL $bom: $HITS"; fail=1; else echo "OK $bom"; fi
done
[ "$fail" = "0" ] || exit 1
echo "PASS sbom-scan (High/Critical clear or waived)"
