# VoltiX — SRS & Architecture Specification (v2)

> Revision note: this version corrects claims that weren't backed by the design (multi-tenancy, "modeling" physics), closes a durability gap in ingestion, fixes a backpressure/SLA contradiction, and adds abuse mitigation to the public complaint endpoint. Where a decision is still open, it's marked **[DECISION NEEDED]** rather than silently resolved.

## 1. Project Overview & Problem Statement
**Domain:** Real-time smart grid data ingestion, non-technical loss (NTL) detection, public citizen complaint management, and automated field dispatch workflows.

**The Problem:** Electrical networks lose significant revenue to NTL (theft, wire tapping, meter bypass, hardware faults). Traditional detection relies on retroactive billing audits. This system ingests high-velocity telemetry, analyzes it for anomalies inline, and routes confirmed/likely incidents to field response — without ingestion becoming a bottleneck and without silently dropping data under load.

**Deployment scope [DECISION NEEDED]:** This architecture is designed and documented as a **single-instance** system. Horizontal scaling of the ingestion tier requires replacing the in-memory queue with an externalized durable queue (e.g. Kafka). This is stated explicitly so it isn't discovered mid-project.

**Core Architecture:** Decoupled Modular Monolith using bounded internal memory queues (`LinkedBlockingQueue`) and a custom `ThreadPoolTaskExecutor` worker pool, isolating ingestion ingress from business/AI logic — with an explicit, documented durability tradeoff (see §4.1).

---

## 2. Multi-Track Machine Learning Framework

```
                          Incoming Telemetry Packet
                                      |
                             Conveyor Belt Queue
                                      |
              +-----------------------+-----------------------+
              |                                               |
              v                                               v
[ Track 1: Anomaly Guard ]                     [ Track 2: Macro Load Monitor ]
 Target: Individual Devices                     Target: Regional Grid Zones
 Data: Raw Physical Sensors (UCI)                Data: Aggregate Hourly Consumption (London)
 Model: Isolation Forest                         Model: Random Forest / XGBoost Regressor
 Mode: Low-Latency Embedded ONNX                 Mode: Scheduled Background Batch Task
```

### Track 1: Anomaly Guard (Individual Physics-Adjacent Classification)
**Objective:** Flag physical anomalies (wire taps, bypass hooks, hardware failures) at the item level.

**Dataset:** UCI Household Power Consumption Dataset.

**Feature contract** (zero-indexed float tensor):
- Index 0: `Global_active_power` (kW)
- Index 1: `Voltage` (V)
- Index 2: `Global_intensity` (A)

**Algorithmic logic (corrected framing):** Unsupervised Isolation Forest. It does **not** encode the physical law P = V×I directly — it learns the statistical joint distribution of the three features from training data and isolates points that fall outside normal density (e.g., active load with near-zero current). Document it this way; don't claim it "models" the physics.

**Validation methodology [required, previously undefined]:**
- Inject synthetic fault patterns into a held-out set: zero-current-under-load, sudden negative deltas, voltage spikes outside 220–240V nominal.
- Measure detection rate against these injected cases before trusting the model in the live pipeline.
- Re-run this validation any time the model is retrained.

**Deployment:** Trained offline in Python, exported to `anomaly_forest.onnx`, placed in `src/main/resources/models/`. Executed inline via Java ONNX Runtime under 20ms.

**Concurrency requirement [previously assumed, now explicit]:** Verify the specific ONNX Runtime Java binding version's documented thread-safety for concurrent `run()` calls on a shared session. Run an explicit concurrent-load test before relying on a single application-scoped session across threads. If unsafe, use a session pool instead of a singleton.

### Track 2: Macro Load Monitor (Regional Demand Forecasting)
**Objective:** Predict community-wide peak consumption ~2 hours ahead.

**Dataset:** London SmartMeter Dataset + weather logs.

**Algorithmic logic:** Random Forest / XGBoost Regressor on (Hour, Day, Temperature) → predicted zone load (kW).

**Evaluation requirement [previously undefined]:** Report MAE/RMSE against a held-out time window before treating forecasts as reliable for blackout prediction. A forecasting model without a stated error margin is not usable for the stated purpose (blackout prevention).

**Deployment:** Compiled to `load_forecaster.onnx`, evaluated asynchronously via scheduled background task.

---

## 3. Core Functional Modules

- **`com.voltix.telemetry`** — Ingress REST endpoints + internal Grid Device Simulator. **Important documentation correction:** the simulator replays static dataset rows as synthetic HTTP traffic for development/demo purposes. It must be documented as a *synthetic replay simulator*, not described as live telemetry, anywhere in user-facing docs — this avoids misleading anyone evaluating the system's actual data source.
- **`com.voltix.analytics`** — Normalizes telemetry into tensors, hosts the ONNX runtime session(s), executes fallback rule-based checks (hard-zero-drop) on runtime failure.
- **`com.voltix.workflow`** — Alert/case lifecycle (`ASSIGNED`, `IN_PROGRESS`, `RESOLVED`), work order and dashboard updates.
- **`com.voltix.security`** — JWT auth, BCrypt (cost factor 12), role-based access: Grid Operators, Field Inspectors, System Administrators.
  - **[DECISION NEEDED]** If genuine multi-tenant data isolation (separate utilities/orgs unable to see each other's data) is a real requirement, this module must also enforce tenant scoping at the query layer — see §5 schema note. If it's *not* a real requirement, remove "multi-tenancy" from all documentation and describe this as role-based access control only. Don't leave both claims standing at once.

---

## 4. System Target Performance Constraints & Fixes

- API ingress response: <20ms via `HTTP 202` queue offload.
- Inline AI inference: <20ms per row.
- End-to-end (ingestion → alert creation): <150ms.

### 4.1 Ingestion Concurrency Config (corrected)
- Core pool size: 4, Max pool size: 8, Queue capacity: 5,000.
- **Backpressure handler (corrected):** `AbortPolicy` (or a custom rejection handler) returning `429/503` to the caller when the queue is full, rather than `CallerRunsPolicy`. `CallerRunsPolicy` forces the accepting HTTP thread to execute the rejected task synchronously — directly conflicting with the <20ms SLA at the exact moment load is highest.
- **Durability decision (Phase 1 — RESOLVED):** Raw telemetry is synchronously inserted into `telemetry_staging` before being offered to the worker queue. If the staging insert fails (DB unavailable), the endpoint returns `503` — the packet is never silently dropped into the queue without a durable record. If the app crashes after a successful staging insert but before the worker processes the row, the row remains in `telemetry_staging` with `processed = false` and is recoverable. See `V2__telemetry_staging.sql`. **Known limitation:** staging insert prevents silent data loss on the HTTP accept path, but there is no reconciliation/retry mechanism yet for rows left at `processed = false` due to a crash or queue rejection. Deferred to a later phase.

### 4.2 Telemetry Validation Bounds (corrected)
- Voltage: tightened to a realistic operating band around nominal (e.g. 200–260V) rather than 0–300V, so sag/swell anomalies are actually catchable. Confirm exact bound against your target region's nominal voltage.
- Current: I ≥ 0A.
- Clock skew: (Current Time − 5m) ≤ t ≤ (Current Time + 1m).

### 4.3 Tracing
All workers log `[TransactionID] [MeterID] [ZoneID]` via structured SLF4J.

### 4.4 Observability [new — previously absent]
- Expose metrics (Micrometer/Prometheus): queue depth, rejection rate, ONNX inference latency, DB write lag.
- Alert on queue depth trending toward saturation *before* rejections start, not after.

---

## 5. Database Topology & Schema (corrected)

Uses PostgreSQL with native range partitioning for `metrics_history`, plus a read-optimized aggregate table for load monitor inputs.

**Partition maintenance [previously unspecified]:** partitions must be created ahead of time via a scheduled job or `pg_partman` — a partitioned table with no future partition silently rejects inserts. This must be automated, not manual.

**Batch writes [previously unspecified]:** the worker pool must batch-insert telemetry rows (JDBC batch or `COPY`), not issue one INSERT per packet — at target throughput, per-row writes will bottleneck Postgres regardless of partitioning.

```sql
CREATE TABLE grid_zones (
    zone_id BIGSERIAL PRIMARY KEY,
    zone_name VARCHAR(100) NOT NULL UNIQUE,
    risk_multiplier NUMERIC(3,2) DEFAULT 1.00
    -- risk_multiplier must be consumed somewhere (e.g. alert priority scoring)
    -- or removed. Currently unused in the described pipeline.
);

CREATE TABLE smart_meters (
    meter_id VARCHAR(50) PRIMARY KEY,
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

-- If multi-tenancy is a real requirement (see §3), add:
-- tenant_id BIGINT NOT NULL REFERENCES tenants(tenant_id)
-- to grid_zones (and cascade the scoping through zone_id everywhere it's referenced).

CREATE TABLE metrics_history (
    record_id BIGSERIAL,
    meter_id VARCHAR(50) NOT NULL REFERENCES smart_meters(meter_id),
    voltage NUMERIC(5,2) NOT NULL,
    current NUMERIC(5,2) NOT NULL,
    kw_consumed NUMERIC(7,4) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (record_id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE TABLE zone_hourly_aggregates (
    zone_id BIGINT REFERENCES grid_zones(zone_id),
    aggregated_hour TIMESTAMP WITH TIME ZONE NOT NULL,
    total_kw_consumed NUMERIC(12,4) NOT NULL,
    avg_temperature NUMERIC(4,1),
    PRIMARY KEY (zone_id, aggregated_hour)
);

CREATE TABLE system_alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    meter_id VARCHAR(50) NOT NULL REFERENCES smart_meters(meter_id),
    anomaly_score NUMERIC(4,3) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'OPEN',
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Public Citizen Gateway Table
CREATE TABLE public_complaints (
    complaint_id BIGSERIAL PRIMARY KEY,
    zone_id BIGINT NOT NULL REFERENCES grid_zones(zone_id),
    incident_address TEXT NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    submitter_ip_hash VARCHAR(64), -- added for rate-limit/dedup enforcement
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metrics_meter_time ON metrics_history(meter_id, recorded_at DESC);
CREATE INDEX idx_alerts_unresolved ON system_alerts(status) WHERE status = 'OPEN';
CREATE INDEX idx_complaints_pending ON public_complaints(status) WHERE status = 'PENDING_VERIFICATION';
```

---

## 6. Public Complaints Feature (corrected)

**Route:** `POST /api/v1/complaints/anonymous`, unauthenticated (`permitAll()`).

**Corrected pipeline (previously: instant auto-escalation to priority work order with no gate):**
1. Submission is rate-limited per IP (e.g. bucket4j) and deduplicated on address + time window.
2. Submission lands in `PENDING_VERIFICATION` and is **held there** — it does not auto-generate a work order at insert time.
3. A separate (automated or human-reviewed, [DECISION NEEDED] which) triage step promotes a complaint to an active work order and dashboard WebSocket alert.
4. This closes the previous abuse vector where any anonymous POST instantly created a high-priority field dispatch ticket.

---

## 7. Open Decisions Log
Track these explicitly rather than letting them default silently:
1. Is multi-tenancy a real product requirement, or is role-based access sufficient? (§3, §5)
2. ~~Ingestion durability~~ **RESOLVED (Phase 1):** Synchronous insert into `telemetry_staging` before queue offer — see §4.1 and `V2__telemetry_staging.sql`. Known limitation: no reconciliation/retry for `processed = false` rows post-crash yet; deferred to a later phase.
3. Complaint triage: automated rules or human review before work-order promotion? (§6)
4. Is single-instance deployment acceptable for the project's intended scope, or is horizontal scaling a real future requirement? (§1)
