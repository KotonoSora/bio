import http from 'k6/http';
import { check } from 'k6';

// G6 floor: beacon burst intake stays uniform 202 (no per-hit PG writes);
// queue lag bounded (asserted post-run by drill-load.sh via XLEN drain).
export const options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS) : 10,
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.BEACON_URL || 'http://localhost:8085';

export default function () {
  const payload = JSON.stringify({ visitorKey: `k6-${__VU}-${__ITER}` });
  const r = http.post(`${BASE}/api/t/k6seed`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(r, { 'beacon uniform 202': (res) => res.status === 202 });
}
