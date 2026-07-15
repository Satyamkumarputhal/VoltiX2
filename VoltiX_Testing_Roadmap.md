# VoltiX — Full Testing Roadmap

## 0. Audit Pass (do this before writing new tests)
Recheck every phase's original exit criteria against what was *actually verified with real output*, not narrated. Specifically re-confirm:
- Phase 0: fresh-volume Docker rebuild actually run (not deferred).
- Phase 1: real k6 p99 numbers exist for the corrected `connection-timeout`, staging insert included in the measured path.
- Phase 1: crash-durability check re-run — does anything actually reprocess `processed=false` rows, or is that still an open gap (it was, as a known limitation)?
- Track 1/2 models: do documented precision/detection-rate and MAE/RMSE numbers exist, or was "it runs" mistaken for "it's validated"?
- Any `[DECISION NEEDED]` items in the SRS — confirm they're actually resolved in code, not just in the doc.

Any gap found here goes into the defect list before testing "new" ground — don't build a polished test suite on top of an unverified foundation.

---

## 1. Unit Testing (per module)
- `telemetry`: DTO validation rules, staging insert success/failure paths, queue offer/reject logic.
- `analytics`: tensor normalization correctness, ONNX session invocation, fallback rule triggering when runtime throws.
- `workflow`: alert lifecycle transitions (illegal transitions rejected, e.g. RESOLVED → ASSIGNED).
- `security`: JWT issuance/validation, role enforcement, BCrypt hashing behavior.
- Target: meaningful line/branch coverage on business logic, not just getters/setters. Coverage % is a side effect, not the goal — missing edge-case branches matter more than the number.

## 2. Integration Testing
- Flyway migrations against real Postgres (already started in Phase 1 — extend to all migrations).
- Full HTTP request → staging insert → queue → worker → DB write, using Testcontainers.
- ONNX model load + inference inside the actual Spring context (not mocked).
- WebSocket alert broadcast: subscribe, trigger an alert, confirm delivery.
- Complaint endpoint → triage gate → work order promotion, full path.

## 3. ML Model Validation Testing (previously undefined — required now)
- **Track 1 (Anomaly Guard):** inject synthetic fault patterns (zero-current-under-load, voltage spikes outside 200–260V, sudden negative deltas) into a held-out set. Report actual detection rate / false-positive rate. No pass/fail threshold was previously defined — set one now (e.g. detect ≥90% of injected faults, false-positive rate below an agreed bound) and measure against it honestly.
- **Track 2 (Load Monitor):** MAE/RMSE against a held-out time window of the London dataset. Report the actual numbers — a forecasting model with no stated error margin isn't validated, regardless of whether it runs.
- Re-run both any time a model is retrained.

## 4. Performance & Load Testing
- Ingestion: sustained 1,000 req/s for a real duration (10+ minutes, not 2), p99 < 20ms including staging insert.
- Inference: p99 < 20ms per row under concurrent load (this also re-validates ONNX session thread-safety under real concurrency, not just an isolated test).
- End-to-end (ingestion → alert creation): p99 < 150ms under sustained load, not idle conditions.
- Report real numbers, including failures. If something misses target, that's a finding, not something to quietly adjust the test to hide.

## 5. Resilience / Chaos Testing
- Kill the app mid-load-test: confirm staged rows survive (already partially done) AND confirm what currently happens to them (known gap: no reconciliation — verify this is still true and documented, not silently assumed fixed).
- Kill the DB connection mid-request: confirm clean 503s, no hangs, no connection-pool deadlock (this is where the `connection-timeout` fix actually gets proven).
- Force ONNX runtime to throw: confirm fallback rule-based check actually engages and an alert/log still fires.
- Saturate the queue deliberately: confirm `AbortPolicy` returns clean rejections under sustained overload, not just a single burst.

## 6. Security Testing
- JWT: expired/tampered/missing token handling on protected routes.
- RBAC: confirm Field Inspector can't hit Operator-only endpoints and vice versa.
- Anonymous complaint endpoint: confirm rate limiting and dedup actually block a burst-submission attack (this was a flagged risk — verify the mitigation was actually built, not just planned).
- Multi-tenancy: if §7.1 was resolved as "real tenant isolation," prove one tenant cannot query another's data. If it was resolved as "RBAC only," confirm documentation says that plainly and no tenant-isolation claim survives anywhere in the docs.

## 7. System / End-to-End Testing
- Full scenario: simulated meter sends anomalous reading → alert created → dashboard receives WebSocket push → inspector assigns case → status transitions to RESOLVED.
- Full scenario: citizen submits complaint → triage gate holds it → promotion to work order → dashboard alert.
- Run both scenarios against a freshly built system from a clean `docker compose up`, not a long-lived dev environment that's accumulated hidden state.

## 8. UAT — Requirements Traceability
- Walk every requirement/constraint stated in the SRS (§4 performance targets, §5 schema behaviors, §6 complaint pipeline) and confirm each has at least one test proving it, not just code that implements it. Build a simple traceability table: Requirement → Test → Pass/Fail/Real number.

## 9. Regression Suite & CI Consolidation
- All of the above that can run automatically (unit, integration, migration checks) go into CI, running on every push — not just Phase 1's scope.
- Load/chaos tests don't belong in every CI run (too slow/disruptive) — but should have a scheduled or manually-triggered CI job so they don't silently rot.

## 10. Defect Tracking & Reporting
- Any gap found in the Audit Pass (§0) or during new testing goes into a single defect list with: what was claimed, what was actually found, severity, and whether it's fixed or accepted as a documented limitation.
- Don't let "documented limitation" become a silent way to avoid fixing something that's actually fixable in the time you have — use it only for genuine, deliberate scope boundaries (like the durability reconciliation gap).

## 11. Sign-off Criteria
The project is "tested," not just "complete," when:
- Every SRS requirement has a traced, passing test with a real recorded number where a number was required (latency, detection rate, error rate).
- Every chaos scenario has been actually run once, with output, not assumed.
- The defect list has no open item marked "unknown status."
