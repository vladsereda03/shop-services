// Р5 baseline load script: hammer the catalog read path — GET /api/products — which today
// returns every good together with its full base64 image bytes. Measures http_req_duration
// (p95/p99) and payload size, so the same script re-run after the MinIO and Redis tracks shows
// the before/after delta.
//
// Auth: a client_credentials token from the auth server, using the existing `cart-service`
// registration (it already holds the products.read scope) — no security change to auth. The
// token lives 5 minutes by default, comfortably longer than a baseline run, so it is fetched
// once in setup() and shared by every VU.
//
// Run (compose):   docker compose --profile load run --rm k6
// Run (host mode):  k6 run -e BASE_URL_PRODUCT=http://localhost:8082 infra/k6/catalog.js
// Tunables via -e:  VUS, RAMP, STEADY (see below).

import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';

const PRODUCT = __ENV.BASE_URL_PRODUCT || 'http://product:8082';
const AUTH = __ENV.BASE_URL_AUTH || 'http://auth.local:9000';
const CLIENT_ID = __ENV.CLIENT_ID || 'cart-service';
const CLIENT_SECRET = __ENV.CLIENT_SECRET || 'cart-service-secret';
const VUS = Number(__ENV.VUS || 50);

export const options = {
  scenarios: {
    catalog_read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '30s', target: VUS },
        { duration: __ENV.STEADY || '1m', target: VUS },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    // informational on the baseline; tightened once MinIO + Redis land (5.9)
    'http_req_duration{endpoint:catalog}': ['p(95)<2000', 'p(99)<4000'],
  },
};

export function setup() {
  const basic = encoding.b64encode(`${CLIENT_ID}:${CLIENT_SECRET}`);
  const res = http.post(`${AUTH}/oauth2/token`, 'grant_type=client_credentials&scope=products.read', {
    headers: {
      Authorization: `Basic ${basic}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });
  check(res, { 'token obtained (200)': (r) => r.status === 200 });
  const token = res.json('access_token');
  if (!token) {
    throw new Error(`no access_token from ${AUTH}/oauth2/token (status ${res.status}): ${res.body}`);
  }
  return { token };
}

export default function (data) {
  const res = http.get(`${PRODUCT}/api/products`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { endpoint: 'catalog' },
  });
  check(res, { 'catalog 200': (r) => r.status === 200 });
}
