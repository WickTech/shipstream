/**
 * Smoke test — 5 VUs for 30 s, all p99 < 500 ms, error rate < 1 %.
 * Run: k6 run --env BASE_URL=http://localhost:8090 k6/smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate  = new Rate('errors');
const orderLat   = new Trend('order_latency',    true);
const shipLat    = new Trend('shipment_latency', true);
const trackLat   = new Trend('tracking_latency', true);

export const options = {
  vus:      5,
  duration: '30s',
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(99)<500'],
    errors:            ['rate<0.01'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8090';

export default function () {
  // Create order
  const createRes = http.post(
    `${BASE}/api/orders`,
    JSON.stringify({ customerId: 'k6-smoke', items: [{ sku: 'SKU-1', qty: 1, price: 9.99 }] }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  const ok = check(createRes, { 'order created 201': (r) => r.status === 201 });
  errorRate.add(!ok);
  orderLat.add(createRes.timings.duration);

  if (!ok) { sleep(1); return; }

  const orderId = createRes.json('id');

  // Get shipment
  const shipRes = http.get(`${BASE}/api/shipments/${orderId}`);
  check(shipRes, { 'shipment 200 or 404': (r) => r.status === 200 || r.status === 404 });
  shipLat.add(shipRes.timings.duration);

  // Get tracking (may be empty)
  const trackRes = http.get(`${BASE}/api/tracking/${orderId}`);
  check(trackRes, { 'tracking 200': (r) => r.status === 200 });
  trackLat.add(trackRes.timings.duration);

  sleep(1);
}
