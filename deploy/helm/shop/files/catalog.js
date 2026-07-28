// Copy of infra/k6/catalog.js for chart self-containment (Helm .Files.Get only reads inside the
// chart dir). Single source of truth is infra/k6/catalog.js — keep this in sync if that changes.
//
// In-cluster load: hammer the catalog read path (GET /api/products) to drive product's CPU up and
// watch the HorizontalPodAutoscaler scale it out. Token via client_credentials from the auth
// service (fetched once in setup(), shared by every VU).

import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';

const PRODUCT = __ENV.BASE_URL_PRODUCT || 'http://product:8082';
const AUTH = __ENV.BASE_URL_AUTH || 'http://auth:9000';
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
        { duration: __ENV.STEADY || '2m', target: VUS },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
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
