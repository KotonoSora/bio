import http from 'k6/http';
import { check } from 'k6';

// G6 floor (E1/E5): public reads p95 < 500 ms. Seeded fixtures via __ENV.
export const options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS) : 20,
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8084';
const LINK = __ENV.LINK_URL || 'http://localhost:8083';

export default function () {
  const user = `seed-user-${__VU % 10}`;
  const r1 = http.get(`${BASE}/${user}`);
  check(r1, { 'profile read 2xx/404': (r) => r.status === 200 || r.status === 404 });
  const r2 = http.get(`${LINK}/go/seed-${__VU % 10}`, { redirects: 0 });
  check(r2, { 'redirect 302/404': (r) => r.status === 302 || r.status === 404 });
}
