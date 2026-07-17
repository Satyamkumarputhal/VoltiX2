# VoltiX-2 — Plan of Action & Project Handover (Corrected, Ground-Truth Version)

> **Purpose:** Self-contained handover reflecting the ACTUAL verified state of the project — not claims, not stale reports. Every status below has been personally confirmed via `mvn test` output, direct code review, or real request/response evidence, not agent self-reporting alone.
>
> **Last verified:** 2026-07-17
> **Current build status:** ✅ PASSING — 32/32 tests, confirmed directly by the developer running `mvn test` after the auth-branch merge into `main`.
>
> **Project purpose note:** This is a university showcase project, not a production deployment. Priorities below are ordered for review-readiness within a 2–4 week window, not production hardening. Where a production-grade fix would take too long, the honest, documented tradeoff is the correct choice — and is called out explicitly below.

---

## 1. What VoltiX-2 Is

A real-time smart grid data ingestion and analysis system targeting Non-Technical Losses (NTL) — theft, wire tapping, meter bypass, hardware faults. It:

1. Ingests smart meter telemetry over REST (target: 1,000 req/s, p99 < 20ms ingress — **see §5 item 1 for real status, this target is NOT currently met with durability intact**).
2. Runs two ML tracks: inline per-meter anomaly detection (ONNX Isolation Forest) and scheduled regional load forecasting (ONNX regressor).
3. Creates alerts on detected anomalies and pushes them live to a React operator dashboard over WebSockets (STOMP) — **confirmed genuinely real, not static/seeded, via direct audit.**
4. Accepts anonymous public complaints (rate-limited, deduplicated, gated behind operator triage).
5. Enforces JWT auth with roles (OPERATOR, INSPECTOR, ADMIN) and tenant scoping — **real signature verification confirmed present; this was previously a critical security hole (forged tokens were accepted) and has been fixed and verified.**

**Architecture:** Decoupled Modular Monolith. Single-instance by design — horizontal scaling would require replacing the in-memory queue with Kafka. This is a known, accepted limitation, not a gap to close for this project's scope.

---

## 2. Verified Current Status (by component)

### 2.1 Ingestion Pipeline — ⚠️ Partially resolved, one real open decision remains
- Tomcat/Hikari/OSIV tuning applied and verified.
- **Real finding, still open:** with full crash-durability intact (`fsync=on`, `synchronous_commit=on`), sustained 1,000 req/s load produced **p99 = 1.34s and 82.76% failure rate** — far outside target, due to disk I/O limits on synchronous per-row commits.
- Disabling `fsync`/`synchronous_commit` restores the target (p99 = 15.22ms, 0% failure) but **removes crash-safety** — unacceptable for a theft-detection system's core guarantee, so this was correctly reverted.
- **A batched/group-commit middle-ground approach was proposed but never actually tested.** This remains the single most valuable open engineering item if time allows (see §4).
- **Given project scope:** the honest, defensible position is to document the real achievable throughput with durability intact, or test the group-commit approach if time permits — NOT to silently re-enable `fsync=off`.

### 2.2 ML Track 1 — Anomaly Guard — ✅ Done, with documented tradeoff
- Original validation had a data-leakage bug (fake tiled/constant fault vectors gave a meaningless 100% precision/recall). Fixed — both the training script's internal check and the dedicated `validate_model_performance.py` now use realistic noisy synthetic distributions.
- **Contamination deliberately tuned and compared across 3 values, with reasoning:**

  | `contamination` | Precision | Recall | Accuracy |
  |---|---|---|---|
  | 0.02 (original) | 0.94 | 0.54 | 0.63 |
  | **0.05 (chosen)** | **0.81** | **0.97** | **0.81** |
  | 0.08 | 0.80 | 1.00 | 0.815 |

  **0.05 was chosen deliberately**: recall gains beyond this point are negligible (0.97→1.00 for no real benefit), while 0.05 already catches nearly all real anomalies. Chosen because a missed theft event is costlier in this domain than an extra false alarm/inspection.
- `AnomalyDetectionEngineTest` regression from this change is **already fixed**: the borderline case (230V/10A/2.3kW) is preserved as its own documented test (`whenTelemetryIsBorderlineHighLoad_thenReturnAnomalous`), and the "normal" baseline test now uses a genuinely central value (240V/6A/1.5kW). Both pass.
- **Known, honest limitation:** synthetic fault distributions are our best approximation of real theft/tampering; no labeled real-world incident data exists to validate against. State this plainly if asked in review — it's a structural limitation of unsupervised anomaly detection, not an oversight.

### 2.3 ML Track 2 — Macro Load Monitor — ✅ Fixed, real improvement (doc was stale on this)
- **Original model was worse than predicting the average** (RMSE 18.43 vs. target std dev 14.90) — root cause: no lag/recent-consumption features, and an unguarded 2-hour shift vulnerable to silent timestamp-gap mispairing.
- **Fixed:** added `lag_1h`/`lag_2h` features (recent actual consumption per zone) and a time-gap guard on the target shift.
- **Verified new result: MAE = 4.44, RMSE = 5.84** (target mean 40.70, std dev 14.90) — RMSE now well under the naive baseline, a genuinely defensible model.
- `ZoneLoadForecaster.java` updated to fetch real lag values via DB lookup (`ORDER BY aggregated_hour DESC LIMIT 1`, tolerant of data gaps) and falls back to heuristic if insufficient history exists — verified via passing `LoadForecastSchedulerIntegrationTest`.

### 2.4 Tenant Isolation — ✅ Done and verified
- Root cause was real: repository methods fetched all-tenant data, then `TenantSecurityAspect` threw `AccessDeniedException` on noticing a foreign tenant's row — meaning legitimate same-tenant requests failed whenever another tenant had ANY data in that table (fails closed, but broken, not a leak).
- Fixed at the query level: `PublicComplaintRepository`, `SystemAlertRepository`, `TelemetryStagingRepository` all now scope by `tenant_id` directly in the query.
- Anonymous complaint endpoint's tenant resolution (via `zone_id` → `grid_zones` lookup) implemented and tested, including the invalid-zone-id 400 case.
- **Verified with real behavioral proof**, not just code review: real seeded tenant A gets only tenant A's data (200 + correct rows); tenant A cannot access tenant B's data (403). Confirmed via actual `mvn test` output, run personally.

### 2.5 Authentication & Roles — ✅ Done, deliberately scoped
- **Critical finding, now fixed:** `JwtAuthenticationFilter` originally parsed tokens WITHOUT verifying signatures — any forged token was accepted. This is now fixed via `MACVerifier`; forged/unsigned tokens are correctly rejected with 401 (verified via real request/response).
- Real login endpoint added: `POST /api/v1/auth/login`, issues genuinely signed JWTs.
- Seeded test users via Flyway `V3__Auth_And_Seed_Test_Users.sql`: `operator`, `inspector`, `admin` (all password `password`, tenant_id=1). Note: a fourth user (`operator2`, tenant_id=2) is seeded inline within `PublicComplaintControllerTest` for the cross-tenant test case, not in the migration — this is correct since tenant 2 doesn't exist at migration time.
- Role enforcement (`@PreAuthorize`) verified with a **real positive case** (valid operator token → real 200 with real data), not just absence-of-403.
- `TenantContext.getCurrentTenant()` confirmed populated only AFTER signature verification succeeds — verified via direct code review of `JwtAuthenticationFilter.java`.
- **Full self-service registration is explicitly out of scope** — deliberate decision given project timeline, not an oversight. State this plainly if asked.

### 2.6 Dashboard / Frontend — ⚠️ Backend real, frontend needs work
- WebSocket (STOMP) live connection confirmed genuinely real via direct audit — not static/seeded data. This is a real asset for the demo.
- **Visual polish still needed** — developer's own assessment: functional but not visually appealing. Bounded time (2-3 days max) should go here; do not let this expand to consume review prep time.

---

## 3. Test Suite — Current Verified State

**32 tests, 32 passing, 0 failures, 0 errors.** Confirmed via direct `mvn test` run, most recently right after resolving a merge conflict in `ZoneLoadForecaster.java` (duplicate lag-feature logic from two independent fixes; resolved to a single clean implementation, nearest-prior-hour lookup, tolerant of data gaps).

```
src/test/java/com/voltix/
  analytics/anomaly/AnomalyDetectionEngineTest.java          ← 5 tests, all pass (incl. new borderline case)
  analytics/forecasting/LoadForecastSchedulerIntegrationTest.java
  load/ChaosAndLoadIntegrationTest.java
  persistence/HikariConfigVerificationTest.java
  persistence/PersistenceIntegrationTest.java
  security/MultiTenancySecurityIntegrationTest.java
  telemetry/controller/TelemetryIngressControllerTest.java
  telemetry/validation/TelemetryValidatorTest.java
  workflow/alerts/AlertDispatchIntegrationTest.java
  workflow/complaints/PublicComplaintControllerTest.java     ← 8 tests, incl. real-login-token tenant tests
```

---

## 4. Genuinely Open Items (honest, not overclaimed)

1. **Ingestion durability vs. throughput tradeoff — UNRESOLVED.** Real numbers exist for both extremes (see §2.1). Group-commit/batched-write middle ground proposed, never tested. **Decide and document** — either accept a lower realistic throughput number with full durability, or spend time testing the batching approach. Do not silently re-enable `fsync=off`.
2. **Rate limiter unbounded map** — `PublicComplaintController`'s per-IP Bucket4j cache has no eviction; grows unbounded under sustained distinct-IP traffic. Real but low-urgency; a bounded cache (e.g., Caffeine with `expireAfterAccess`) would close this cheaply if time allows.
3. **ONNX session-pool concurrency — untested.** `OnnxSessionPool` (commons-pool2-backed) has no test proving thread-safety under real concurrent load. Known gap, acceptable to state as such in review.
4. **Write-path tenant scoping sweep incomplete.** `TelemetryStagingRepository.markFailed` was fixed, but a full sweep of every non-`find*` repository method (custom `@Query`, `countBy*`, `deleteBy*`) for missing tenant scoping was never exhaustively completed and evidenced with a real grep + verification. Worth a final pass if time allows.
5. **Secrets hardcoded.** `jwt-secret` in `application.yml` is a placeholder dev value, not externalized via environment variables. Fine for a showcase project; mention as a known production gap if asked.
6. **Observability, CI, deployment docs** — not built. Given project scope (review, not production), these are reasonable to explicitly defer and mention as "known future work" rather than spend remaining time on.

---

## 5. Plan of Action — Ordered for Review Readiness (2–4 week window)

### Already done (do not redo)
- ✅ Ingestion Tomcat/Hikari tuning
- ✅ Tenant isolation, query-level fix, verified
- ✅ ML Track 1 tuned and validated (contamination=0.05, documented tradeoff)
- ✅ ML Track 2 fixed (lag features), verified real improvement
- ✅ Auth: signature verification, login, role enforcement, verified
- ✅ Merge into `main` complete, 32/32 tests passing

### Next, in order
1. **Decide the fsync/durability tradeoff (§4 item 1).** Either test the batched-write approach, or document the real achievable throughput honestly with durability intact. This is your strongest "I found a real engineering tradeoff and made a documented decision" story for review — don't skip documenting it even if you don't have time to fully solve it.
2. **Frontend visual pass — bounded to 2-3 days.** Clean layout, readable alerts, no breakage. Do not chase feature completeness here.
3. **Write the "known limitations" one-pager** covering §4 items 2-6 — a reviewer respects "I know what's not done and why" far more than an unblemished claim.
4. **Rehearse explaining, out loud, without notes:** how Isolation Forest works, why `contamination=0.05` was chosen over 0.02/0.08, why the tenant-isolation bug happened and how it was fixed, why the fsync tradeoff exists and what was decided, why the original JWT filter was a real security hole.

---

## 6. How to Run Everything

```bash
docker compose up -d                       # Postgres on 5433 (+ app container)
./mvnw spring-boot:run                     # or mvnw.cmd on Windows — app on :8080, Flyway migrates V1–V3
mvn test                                   # full suite — expect 32/32 passing
cd voltix-dashboard && npm install && npm run dev    # operator dashboard
k6 run ingress_load_test.js                # sustained load test (app must be running)
python train_anomaly_guard.py              # retrain Track 1 (contamination=0.05 already set)
python train_macro_monitor.py              # retrain Track 2 (lag features already implemented)
python validate_model_performance.py       # Track 1 real validation — expect ~0.81 precision / ~0.97 recall
```

Login for manual testing: `POST /api/v1/auth/login` with `operator`/`password` (also `inspector`, `admin`).

**Conventions to preserve:** schema changes only via new Flyway migrations; models retrained offline in Python, never in Java; any model retrain requires re-running `validate_model_performance.py` AND `mvn test` before commit; verify claims with raw command output before marking anything "done" — this project's history has repeatedly shown self-reported "all tests passing" claims need independent confirmation.
