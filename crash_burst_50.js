import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// Fire exactly 50 concurrent requests: 50 VUs, 1 iteration each.
const status202 = new Counter('status_202');
const statusOther = new Counter('status_other');
const connErrors = new Counter('connection_errors');

export const options = {
  scenarios: {
    crash_burst: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

// Same valid HS256 token used by the other load scripts.
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
  const meterId = `SM-${__VU}`;

  const payload = JSON.stringify({
    meterId: meterId,
    tenantId: 1,
    zoneId: 1,
    transactionId: uuidv4(),
    voltage: 230.0,
    current: 5.0,
    kwConsumed: 1.15,
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
  else if (res.status === 0) connErrors.add(1);
  else statusOther.add(1);

  check(res, { 'status is 202': (r) => r.status === 202 });
}
