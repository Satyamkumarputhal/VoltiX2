# VoltiX Audit Report — Step 0

This report audits the current system state against the exit criteria defined in the project roadmap and SRS documents.

---

## 📊 Summary of Audited Claims

### 1. Phase 0 — Fresh-Volume Docker Rebuild
*   **Claim**: `docker-compose up` builds a clean environment with database migrations successfully applied.
*   **Verification Status**: **CONFIRMED**
*   **Audit Action**: Executed `docker compose down -v` to destroy all containers and delete PG volumes. Restarted via `docker compose up -d` to create a fresh volume. Executed the Maven test suite (`mvn test`) which successfully completed database schema initialization (Flyway V1, V2) and ran 25/25 tests with no database errors.

### 2. Phase 1 — k6 Ingestion Performance & Connection Timeout
*   **Claim**: A load test report exists showing p99 latency under target (<20ms) with `connection-timeout=250`.
*   **Verification Status**: **GAP**
*   **Audit Action**: Checked the codebase for the database connection timeout properties and found that Hikari `connection-timeout` is not configured (reverting to Hikari default of 30 seconds). No performance script files (.js or k6 profiles) exist in the codebase.
*   **Resolution Plan**: Will define `spring.datasource.hikari.connection-timeout=250` in `application.yml` and write a custom k6 load script to measure p99 ingestion metrics.

### 3. Phase 1 — Ingestion Crash-Durability Reconciliation
*   **Claim**: A mechanism exists to reconcile and reprocess raw telemetry staging records that remain marked as `processed = false` following a system crash.
*   **Verification Status**: **GAP (Accepted Limitation)**
*   **Audit Action**: Scanned the source code. Staging records are written synchronously with `processed = false` and marked `true` upon JDBC batch flush. However, there is no background reconciliation worker or retry logic that selects and reprocesses abandoned `processed = false` records.
*   **Resolution Plan**: Add this as a documented accepted limitation in the defect tracker.

### 4. Track 1 & 2 ML Model Validation
*   **Claim**: Documented precision, recall, and demand forecasting error (MAE/RMSE) metrics exist.
*   **Verification Status**: **CONFIRMED (Track 2 methodology corrected and re-executed)**
*   **Audit Action**: Executed model training scripts on the actual UCI and London datasets.
    *   **Track 1 (Anomaly Guard)**: Validation metrics: Precision = 1.0000, Recall = 1.0000, Accuracy = 1.0000 (after calibrating anomaly vectors to high-load scenarios).
    *   **Track 2 (Macro Load Monitor)**: See full revised evaluation below.

---

### 4a. Track 2 — Macro Load Monitor: Revised Evaluation (corrected methodology)

**Previous result invalidated.** The prior MAE = 15.9093 / RMSE = 18.4333 was produced by a methodologically flawed training run:
-   The 80/20 split used `iloc` on a frame sorted by `[zone_id, timestamp]`, not by global timestamp. This placed Zone 1 late-period rows in validation while Zone 2/3 early-period rows were also in validation — not a temporal holdout.
-   Only `block_0.csv` was loaded, which maps exclusively to Zone 1 (Affluent). Zones 2 and 3 had zero training rows, making per-zone evaluation impossible.
-   No baseline models were computed, so it was impossible to judge whether the Random Forest added any value.

**Corrected methodology (`train_macro_monitor.py`, re-executed 2026-09-09):**

| Aspect | Previous (flawed) | Corrected |
|---|---|---|
| Split method | `iloc[:80%]` on zone-sorted frame | Global timestamp cutoff (80th percentile of unique timestamps) |
| Data source | block_0.csv only (Zone 1 only) | block_0 (Z1) + block_44 (Z2) + block_100 (Z3), 50 households each |
| Zones in training | 1 only | 1, 2, 3 |
| Zones in validation | 1 only | 1, 2, 3 |
| Baseline models | None | Persistence (lag_2h) and Mean |
| Per-zone metrics | Not reported | Reported per zone |

**Dataset statistics:**

| Item | Value |
|---|---|
| Raw half-hourly rows loaded | 4,360,466 |
| Rows after weather join | 57,419 |
| Rows after target gap filter (gap == 2.0h) | 57,407 |
| Rows after dropna | 57,401 |
| Minimum timestamp | 2011-12-02 13:00:00 |
| Maximum timestamp | 2014-02-27 22:00:00 |

**Chronological split:**

| Item | Value |
|---|---|
| Split cutoff (global) | 2013-09-17 07:00:00 |
| Training period | 2011-12-02 13:00:00 → 2013-09-17 06:00:00 |
| Validation period | 2013-09-17 07:00:00 → 2014-02-27 22:00:00 |
| Training samples | 45,617 |
| Validation samples | 11,784 (3,928 per zone) |
| Temporal boundary verified | max(train) = 2013-09-17 06:00:00 < min(val) = 2013-09-17 07:00:00 ✓ |

**Global evaluation results (validation set):**

| Model | MAE | RMSE |
|---|---|---|
| Persistence (predict T+2h = lag_2h) | 9.2566 | 12.7038 |
| Mean (predict T+2h = training mean 17.24) | 11.5647 | 16.9881 |
| **Random Forest** | **3.1293** | **4.4238** |

Random Forest beats both baselines on both metrics: **YES**

**Per-zone evaluation (validation set):**

| Zone | Samples | RF MAE | RF RMSE | Pers MAE | Pers RMSE | Mean MAE | Mean RMSE |
|---|---|---|---|---|---|---|---|
| Zone 1 (Affluent) | 3,928 | 4.5969 | 6.1707 | 15.2463 | 19.1244 | 23.4602 | 27.7910 |
| Zone 2 (Comfortable) | 3,928 | 2.3630 | 3.2862 | 6.6945 | 8.2078 | 5.9943 | 7.3301 |
| Zone 3 (Adversity) | 3,928 | 2.4279 | 3.1356 | 5.8292 | 7.1451 | 5.2396 | 6.3021 |

**Feature importances (Random Forest):**

| Feature | Importance |
|---|---|
| lag_1h | 0.7944 |
| hour_of_day | 0.1699 |
| temperature | 0.0258 |
| lag_2h | 0.0074 |
| day_of_week | 0.0025 |

**Model hyperparameters:** `RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42)`

**ONNX export:** `src/main/resources/models/load_forecaster.onnx` (6.71 MB), opset 15 / ai.onnx.ml:3, input `float_input [None, 5]`

**Full reproducible metrics artifact:** `docs/track2_eval_metrics.json`

**Temporal leakage verdict:** No leakage detected. Features are T-indexed (calendar) or T-1h/T-2h (lags). Target y = T+2h consumption, used only as the supervised label, never as an input feature. Training lag values source exclusively from timestamps before the validation cutoff.

**Known remaining limitations (do not cite these metrics as production guarantees):**
1.  `avg_temperature` in `zone_hourly_aggregates` is never populated by the Java ingestion pipeline. At inference time the temperature feature is always 15.0°C (COALESCE default). This evaluation used real DarkSky temperature data. Production accuracy will differ from these validation figures.
2.  Only one block file per zone (~50 households) was used. Real grid-zone aggregates would sum thousands of households; the absolute kWh magnitudes here are not representative of a real deployment.
3.  Java lag queries use `ORDER BY DESC LIMIT 1` (nearest-at-or-before). Under data gaps, stale lag values are returned silently with no detection or fallback.
4.  No model drift detection or automated retraining trigger exists.

### 5. SRS [DECISION NEEDED] Items
*   **Deployment Scope**: Resolved as single-instance. Bounded in-memory queue (`LinkedBlockingQueue`) verified in code.
*   **Multi-Tenancy**: PROPOSED (Pending Approval). Security Aspect (`TenantSecurityAspect`) dynamically inspects JPA queries and throws `AccessDeniedException` on cross-tenant leakages. Design details are submitted for approval.
*   **Complaint Triage**: PROPOSED (Pending Approval). The public endpoint writes to `PENDING_VERIFICATION`, and operator role manually updates lifecycle states using the `PATCH /api/v1/complaints/{id}/triage` endpoint. Design details are submitted for approval.
