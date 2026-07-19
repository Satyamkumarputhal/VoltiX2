import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Per-status-code breakdown counters (server responses + client-side transport failures)
const status202 = new Counter('status_202');
const status400 = new Counter('status_400');
const status429 = new Counter('status_429');
const status500 = new Counter('status_500');
const status503 = new Counter('status_503');
const statusOther = new Counter('status_other');
const connectionErrors = new Counter('connection_errors'); // status === 0 (TCP refused/reset/timeout)

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: __ENV.RATE ? parseInt(__ENV.RATE) : 4000,
      timeUnit: '1s',
      duration: __ENV.DURATION ? __ENV.DURATION : '45s',
      // High VU ceiling so the harness can actually deliver the target rate
      // even when per-request latency climbs under overload.
      preAllocatedVUs: 1000,
      maxVUs: 3000,
    },
  },
  // No thresholds: we want to OBSERVE the degradation mode, not fail-fast.
};

// Valid HS256 token (same one used by ingress_load_test.js), signed with the
// configured jwt-secret; tenant_id=1, roles=[operator], exp=1882728000.
const token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZXZpY2Utc2ltdWxhdG9yIiwidGVuYW50X2lkIjoxLCJyb2xlcyI6WyJvcGVyYXRvciJdLCJleHAiOjE4ODI3MjgwMDB9.hdRIJ1XV-K87Vg01ywYRF9xrR8eywJG_K4W6DSLk9eA";

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

export default function () {
  const url = 'http://localhost:8080/api/v1/telemetry/submit';
  const meterId = `SM-${Math.floor(Math.random() * 1000)}`;

  const payload = JSON.stringify({
    meterId: meterId,
    tenantId: 1,
    zoneId: 1,
    transactionId: uuidv4(),
    voltage: 230.0 + (Math.random() * 10.0 - 5.0),
    current: 5.0 + (Math.random() * 4.0 - 2.0),
    kwConsumed: 1.15 + (Math.random() * 0.5 - 0.25),
    recordedAt: new Date().toISOString()
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
  };

  const res = http.post(url, payload, params);

  if (res.status === 202) status202.add(1);
  else if (res.status === 400) status400.add(1);
  else if (res.status === 429) status429.add(1);
  else if (res.status === 500) status500.add(1);
  else if (res.status === 503) status503.add(1);
  else if (res.status === 0) connectionErrors.add(1);
  else statusOther.add(1);

  check(res, {
    'status is 202': (r) => r.status === 202,
  });
}
