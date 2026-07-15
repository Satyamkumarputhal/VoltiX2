import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Custom counters for status code breakdown
const status202 = new Counter('status_202');
const status429 = new Counter('status_429');
const status503 = new Counter('status_503');
const statusOther = new Counter('status_other');
const connectionErrors = new Counter('connection_errors');

export const options = {
  scenarios: {
    overload_burst: {
      executor: 'ramping-arrival-rate',
      startRate: 500,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 3000,
      stages: [
        { duration: '30s', target: 1000 },   // Warm up to baseline
        { duration: '30s', target: 1500 },   // Push beyond capacity
        { duration: '60s', target: 2000 },   // Sustained overload
        { duration: '30s', target: 500 },    // Cool down
      ],
    },
  },
  // No failure thresholds — we want to observe, not fail-fast
};

const token = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiZGV2aWNlLXNpbXVsYXRvciIsICJ0ZW5hbnRfaWQiOiAxLCAicm9sZXMiOiBbIm9wZXJhdG9yIl0sICJleHAiOiAxODgyNzI4MDAwfQ.c2lnbmF0dXJl";

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
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

  // Track status code breakdown
  if (res.status === 202) {
    status202.add(1);
  } else if (res.status === 429) {
    status429.add(1);
  } else if (res.status === 503) {
    status503.add(1);
  } else if (res.status === 0) {
    connectionErrors.add(1); // TCP connection refused/reset
  } else {
    statusOther.add(1);
  }

  check(res, {
    'status is 202 or 429': (r) => r.status === 202 || r.status === 429,
  });
}
