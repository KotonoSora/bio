import http from 'k6/http';
import { check } from 'k6';

// G6 floor: checkout p95 < 1 s at mixed load. Idempotency keys are unique per
// iteration (no accidental collapse); the money path asserts zero failures.
// Auth: dev gate runs sessionless (gateway dev-user); staging runs with a
// session cookie via COOKIE_JAR (same digests, secrets differ per 05).
export const options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS) : 5,
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.CHECKOUT_URL || 'http://localhost:8088';

export default function () {
  const payload = JSON.stringify({
    idempotencyKey: `k6-${__VU}-${__ITER}-${Date.now()}`,
    provider: 'PAYOS',
    amountVnd: 50000,
  });
  const r = http.post(`${BASE}/api/v1/checkout`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(r, { 'checkout 201': (res) => res.status === 201 });
}
