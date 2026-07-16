# Security & Integration Audit Report: VoltiX-2

## 1. Does JWT login actually work?
**CLAIM:** Yes. A minimal, functional login endpoint exists, and the JWT filter now strictly enforces cryptographic signature verification.

**EVIDENCE:**
- **Login Endpoint:** `AuthController.java` exposes `POST /api/v1/auth/login`. When called with valid seeded credentials (`operator` / `password`), it queries the database and signs a JWT containing the user's tenant context and roles.
```http
POST /api/v1/auth/login
Content-Type: application/json
{"username": "operator", "password": "password"}

HTTP/1.1 200 OK
{"token":"eyJhbGciOiJIUzI1NiJ9.eyJ0ZW5hbnRfaWQiOjEsInN1YiI6Im9wZXJhdG9yIiwiZXhwIjoxNzg0Mjg3NDE1LCJyb2xlcyI6WyJPUEVSQVRPUiJdfQ.AilpXisJ0xJpVRcJXinCamwYh3fny6NZ9cTpCjrFxlo"}
```

- **Filter Verification:** `JwtAuthenticationFilter.java` was updated to use `MACVerifier`. When sending an unsigned/forged token (which previously bypassed the filter), the system now intercepts and drops the request before parsing claims.
```http
POST /api/v1/complaints/1/triage?status=VERIFIED
Authorization: Bearer eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiYWRtaW4iLCAidGVuYW50X2lkIjogMSwgInJvbGVzIjogWyJPUEVSQVRPUiJdfQ.ZmFrZV9zaWduYXR1cmU

HTTP/1.1 401 Unauthorized
Content-Length: 0
```

**VERDICT:** REAL/SECURE. Authentication can no longer be bypassed via forged tokens.

---

## 2. Do roles actually exist and get enforced?
**CLAIM:** Yes. Roles are parsed from the signed token, converted to Spring Security `GrantedAuthority`, and actively enforced via method-level annotations.

**EVIDENCE:**
- **Annotations Enforced:** `PublicComplaintController.java` now uses `@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")` on the `triageComplaint` endpoint.
- **Wrong Role Rejected:** Logging in as `inspector` yields a valid token with the `INSPECTOR` role. When using this token to access the triage endpoint, it is rejected by the authorization layer:
```http
PATCH /api/v1/complaints/1/triage?status=VERIFIED
Authorization: Bearer <valid_inspector_token>

HTTP/1.1 403 Forbidden
```
- **Correct Role Accepted:** When using the `operator` token, the request penetrates the security filter. (It returns 404 only because Complaint ID 1 does not exist in the seeded DB, which proves authorization succeeded).
```http
PATCH /api/v1/complaints/1/triage?status=VERIFIED
Authorization: Bearer <valid_operator_token>

HTTP/1.1 404 Not Found
```

**VERDICT:** REAL/ENFORCED. Role-based access control is actively protecting endpoints.

---

## 3. Is user registration real?
**CLAIM:** No. Registration does not exist, by design for this phase. Test users are instead seeded via Flyway.

**EVIDENCE:**
- **No Registration Endpoint:** There are no endpoints for user signup.
- **Seeding:** The migration `V3__Auth_And_Seed_Test_Users.sql` seeds `operator`, `inspector`, and `admin` with valid `BCrypt` hashes of the password `"password"`.

**VERDICT:** OUT OF SCOPE. User creation is handled out-of-band/via migrations.

---

## 4. What does "dashboard shows live data" actually mean?
**CLAIM:** The dashboard uses a genuine real-time WebSocket (STOMP) connection that reacts automatically to backend anomalies without polling or static seeding.

**EVIDENCE:**
- **Frontend Code (`voltix-dashboard/src/hooks/useWebSocket.ts`):** 
```typescript
const WS_URL = 'ws://localhost:8080/ws/alerts/websocket';
// ...
subscriptionRef.current = stompClient.subscribe(
  '/topic/alerts',
  (frame: IMessage) => {
    const alert: SystemAlert = JSON.parse(frame.body);
    setLastMessage(alert);
    addAlert(alert);
  }
);
```
- **Backend Dispatch Logs:**
```text
c.v.w.alerts.AlertDispatchService : System Alert created: ID=383044, Type=NTL_ANOMALY, Severity=MEDIUM, PriorityScore=1.0000
c.v.w.alerts.AlertDispatchService : [WS DISPATCH] Broadcasting alert ID=383044 to dashboard operators
```

**VERDICT:** REAL. The live connection is fully implemented via WebSocket broadcasts.

---

## 5. Current Known Limitations (Open Items)
While the core vulnerabilities are patched, the following architectural limitations remain open as accepted technical debt for this phase:
1. **ONNX Concurrency Untested:** High-load multi-threaded inference against the session pool is not yet validated by integration tests.
2. **Fsync Tradeoff Accepted Not Solved:** Persistence durability via fsync tradeoffs remains a risk.
3. **Rate Limiter Unbounded Map:** The public complaint rate limiter relies on an unbounded in-memory map, which poses a long-term OutOfMemory risk under sustained load.
