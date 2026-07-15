# VoltiX — Project Roadmap & Risk Analysis

## Purpose of this document
This is a build-order plan, not a feature list. Every phase exists because it either unblocks the next phase or closes a risk identified below. Do not skip a phase to get to the "interesting" ML parts faster — the risks in Section 1 are exactly the kind that surface at 80% completion and force a rewrite.

---

## 1. Risks You Should Not Avoid

| # | Risk | Why it matters | Where it hits you if ignored |
|---|------|-----------------|-------------------------------|
| 1 | No durability for in-flight telemetry (accept → in-memory queue → process) | Data loss on crash/restart, unacceptable for a fault/theft detection system | Silent gaps in `metrics_history`; undetectable data loss |
| 2 | `LinkedBlockingQueue` ties ingestion to a single JVM | No horizontal scaling path without a rewrite | Discovered only when you try to scale past one instance |
| 3 | `CallerRunsPolicy` contradicts the <20ms SLA | Blocks the request thread exactly when load is highest | Latency spikes/API timeouts under the load you built this to handle |
| 4 | "Multi-tenancy" claimed but no `tenant_id` anywhere in schema | Role-based access ≠ tenant isolation | Cross-tenant data leakage once you add a second utility/org |
| 5 | Isolation Forest described as "modeling" P=V×I | It's unsupervised pattern detection, not physics | Weak answer if anyone technical asks how the model actually works |
| 6 | No validation plan for unsupervised anomaly detection | You can't currently prove the model detects anything real | Ship a model with unknown precision/recall |
| 7 | Anonymous complaint endpoint auto-escalates to priority work order | Zero abuse mitigation on a public, unauthenticated route | Spam floods the field-dispatch queue |
| 8 | Voltage validation bound (0–300V) too loose | Real anomalies (sag/swell) pass through undetected | False sense of validation coverage |
| 9 | No partition-creation strategy for `metrics_history` | Partitioned tables need forward-created partitions | Inserts start failing once you run out of partitions |
| 10 | `risk_multiplier` column defined, never used | Dead schema signals incomplete design | Reviewers/interviewers will ask "so what does this do?" |
| 11 | No batch-insert strategy | Row-by-row writes at 1,000/s will bottleneck Postgres regardless of partitioning | DB becomes the bottleneck the architecture was designed to avoid |
| 12 | No observability beyond structured logs | Can't see queue saturation, ONNX latency drift, DB write lag in real time | Problems discovered by users/reviewers before you |
| 13 | ONNX Runtime session thread-safety assumed, not verified against the specific Java binding version | If the binding isn't safe for concurrent `run()` calls, you get race conditions under load | Intermittent, hard-to-reproduce inference errors |
| 14 | No CI/CD or automated test suite defined | Manual verification doesn't scale as modules multiply | Regressions creep in silently across 4 packages |

---

## 2. Build Phases

Each phase lists exit criteria — don't move on until they're actually true, not "mostly working."

### Phase 0 — Foundations (2–3 days)
- Repo structure, package skeleton (`com.voltix.telemetry`, `.analytics`, `.workflow`, `.security`)
- Docker Compose: Postgres + app container
- Flyway/Liquibase migration baseline (schema as code, not manual SQL)
- CI pipeline skeleton (build + test on push)
- **Exit:** `docker-compose up` gives you a running empty system with migrations applied.

### Phase 1 — Core Ingestion Pipeline (1–2 weeks)
- REST ingress endpoint, input validation (voltage/current/clock-skew bounds — tighten voltage bound per Risk #8)
- Fix backpressure policy (Risk #3): `AbortPolicy` + `429` response, or explicit shed-and-log
- Decide durability tradeoff (Risk #1) explicitly — either synchronous staging insert before queueing, or a documented, deliberate "best-effort" tradeoff
- Structured logging: `[TransactionID] [MeterID] [ZoneID]`
- Load test with Gatling/k6 to actually prove <20ms ingress under 1,000 req/s — don't assume it
- **Exit:** Load test report showing p99 latency under target, with backpressure behavior verified (not just implemented).

### Phase 2 — Persistence Layer (1 week)
- Batch inserts from worker pool (Risk #11) instead of per-row writes
- Partition automation for `metrics_history` (Risk #9) — pg_partman or a scheduled job that creates N partitions ahead
- Decide what `risk_multiplier` actually does (Risk #10) and wire it in, or remove it
- **Exit:** Sustained write test at target throughput with no partition failures and no write-lag buildup.

### Phase 3 — Track 1: Anomaly Guard (2 weeks)
- Offline training (Python), correct the model's description (Risk #5) in all docs
- Define validation methodology (Risk #6): synthetic fault injection on a held-out set, measure detection rate before trusting it in the pipeline
- Export to ONNX, verify Java runtime binding's concurrency guarantees (Risk #13) — test explicitly with concurrent load, don't assume
- Fallback rule engine (hard-zero-drop) as designed
- **Exit:** Documented precision/recall (or detection rate) against injected synthetic anomalies, plus a concurrency stress test on the ONNX session.

### Phase 4 — Workflow & Alerts (1 week)
- Alert lifecycle (ASSIGNED → IN_PROGRESS → RESOLVED), WebSocket push to dashboard
- **Exit:** End-to-end trace from anomaly detection to dashboard alert, latency measured against the 150ms end-to-end target.

### Phase 5 — Public Complaints (3–4 days)
- Rate limiting (e.g. bucket4j) on the anonymous endpoint (Risk #7)
- Enforce `PENDING_VERIFICATION` as a real gate before a work order is auto-created, not just a default column value
- Dedup on address + time window
- **Exit:** Abuse test (burst submissions) confirms rate limiting and triage gate actually block escalation.

### Phase 6 — Track 2: Macro Load Monitor (2 weeks)
- Data join (London SmartMeter + weather), batch training schedule
- Model evaluation: MAE/RMSE against a held-out time period, documented
- Background daemon integration, scheduled evaluation
- **Exit:** Forecast accuracy numbers documented, not just "it runs."

### Phase 7 — Security & Multi-Tenancy (1–2 weeks)
- JWT + roles as designed
- Decide tenant isolation model (Risk #4): add `tenant_id` and enforce at query layer (or Postgres RLS) if genuine multi-tenancy is a real requirement; otherwise, rename the claim to "role-based access control" in docs
- Secrets management (not hardcoded config)
- **Exit:** A second tenant's data is provably inaccessible to the first tenant's operator role, if multi-tenancy is kept as a real requirement.

### Phase 8 — Observability (1 week)
- Metrics (Micrometer/Prometheus): queue depth, ONNX inference latency, DB write lag
- Alerting on queue saturation before it causes rejections
- **Exit:** A dashboard that would tell you *why* the system is slow, not just that it is.

### Phase 9 — Full-System Load & Chaos Testing (1–2 weeks)
- Sustained load test at 1,000 req/s for a real duration (not a 30-second burst)
- Chaos scenarios: ONNX runtime throwing errors, DB connection loss, queue saturation — confirm fallback behavior actually triggers
- **Exit:** A written incident report format is dry-run tested against at least one injected failure.

### Phase 10 — Deployment (1 week)
- Containerized deployment, explicitly documented scaling ceiling (Risk #2) — state plainly that horizontal scaling requires replacing the in-memory queue, don't leave it implied
- **Exit:** Deployment doc that's honest about what this architecture can and can't do at scale.

---

## 3. Sequencing Notes
- Don't start Phase 3 (ML) before Phase 1–2 are load-tested. A fast model behind a slow/lossy pipeline is wasted work.
- Phase 5 (complaints) is small but skipped often because it's "less interesting" — it's also the most exposed unauthenticated surface in the whole system. Don't leave it for last.
- Phase 7 (multi-tenancy) is expensive to retrofit. Decide the tenant model before Phase 2 schema is finalized if at all possible — adding `tenant_id` after data exists is a migration, not a feature.
