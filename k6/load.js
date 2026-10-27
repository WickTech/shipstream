/**
 * Load test — ramp to 50 VUs over 2 min, hold 5 min, ramp down 1 min.
 * Run: k6 run --env BASE_URL=http://localhost:8090 k6/load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const p99Lat    = new Trend('order_p99_latency', true);

export const options = {
  stages: [
    { duration: '2m',  target: 50 },   // ramp up
    { duration: '5m',  target: 50 },   // hold
    { duration: '1m',  target: 0  },   // ramp down
  ],
  thresholds: {
    http_req_failed:       ['rate<0.01'],
    http_req_duration:     ['p(95)<800', 'p(99)<1500'],
    errors:                ['rate<0.01'],
    order_p99_latency:     ['p(99)<1500'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8090';

export default function () {
  const res = http.post(
    `${BASE}/api/orders`,
    JSON.stringify({ customerId: `vu-${__VU}`, items: [{ sku: 'LOAD-SKU', qty: 1, price: 1.00 }] }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const ok = check(res, { 'status 201': (r) => r.status === 201 });
  errorRate.add(!ok);
  p99Lat.add(res.timings.duration);
  sleep(0.5);
}
