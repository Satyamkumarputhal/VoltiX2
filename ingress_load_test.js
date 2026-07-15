import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 1000,
      timeUnit: '1s',
      duration: '10m', // Sustained for 10 minutes
      preAllocatedVUs: 100,
      maxVUs: 1000,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<20'], // p99 ingress latency bounds (<20ms)
    http_req_failed: ['rate<0.001'], // HTTP failure rate < 0.1%
  },
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
  
  // Randomly distribute across the 1000 seeded meters (SM-0 to SM-999)
  const meterId = `SM-${Math.floor(Math.random() * 1000)}`;
  
  const payload = JSON.stringify({
    meterId: meterId,
    tenantId: 1,
    zoneId: 1,
    transactionId: uuidv4(),
    voltage: 230.0 + (Math.random() * 10.0 - 5.0), // 225V to 235V normal range
    current: 5.0 + (Math.random() * 4.0 - 2.0),     // 3A to 7A normal range
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
  
  check(res, {
    'status is 202': (r) => r.status === 202,
  });
}
