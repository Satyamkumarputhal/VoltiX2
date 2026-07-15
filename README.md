# VoltiX — Real-Time Smart Grid Ingestion & ML Guard

VoltiX is a modular, high-throughput smart grid data ingestion and analysis system. It is designed to tackle **Non-Technical Losses (NTL)**—such as electricity theft, wire tapping, meter bypass, and hardware failures—in real time. The system ingests high-velocity telemetry, applies multi-track machine learning models for anomaly detection and regional load forecasting, manages anonymous public complaints, and broadcasts alerts to an operator dashboard via WebSockets.

---

## 🏗️ System Architecture

```
                                    +-----------------------------------+
                                    |     React Operator Dashboard      |
                                    |  (Vite, TS, WebSockets, Tailwind) |
                                    +-----------------+-----------------+
                                                      ^
                                                      | WebSockets / REST
                                                      v
                                    +-----------------+-----------------+
                                    |       VoltiX Spring Boot Core      |
                                    +-------+-------------------+-------+
                                            |                   |
                     Ingested Telemetry     v                   v  Triage / Queries
                 +--------------------------+          +--------+------------------+
                 |                                     |                           |
                 v                                     v                           v
     +-----------+-----------+                     +---+---------------------------+---+
     |   Telemetry Ingress   |                     |          Security Module          |
     |   (REST Controller)   |                     |     (JWT, Tenant Isolation,       |
     +-----------+-----------+                     |        Role-Based Access)         |
                 |                                 +---+---------------------------+---+
                 | 1. Synchronous Insert               |
                 v                                     |
     +-----------+-----------+                         |
     |   telemetry_staging   |                         |
     |    (PostgreSQL)       |                         |
     +-----------+-----------+                         |
                 |                                     |
                 | 2. Queue Buffer                     |
                 v                                     |
     +-----------+-----------+                         |
     | LinkedBlockingQueue   |                         |
     +-----------+-----------+                         |
                 |                                     |
                 | 3. Worker Thread Pool               |
                 v                                     v
     +-----------+-----------+                 +-------+-------------------------------+
     |   Telemetry Workers   |                 |               PostgreSQL DB           |
     | (ONNX Inference Pool) | --------------->| - metrics_history (Range Partitioned) |
     +-----------------------+  Batch Insert   | - system_alerts, public_complaints   |
                                               +---------------------------------------+
```

### 1. Ingestion Pipeline
* **High-Throughput Endpoint:** `POST /api/v1/telemetry/submit` returns `HTTP 202 Accepted` in `<20ms` by offloading processing to an in-memory queue.
* **Concurrency Configuration:** Backpressure is enforced via a customized `ThreadPoolTaskExecutor` (Core pool size: 4, Max pool size: 8, Queue capacity: 5,000) using a reject policy (`AbortPolicy`) that returns `429 Too Many Requests` rather than blocking threads.
* **Staging Durability:** Raw telemetry is written to `telemetry_staging` synchronously. If the database is down, the request fails with `503 Service Unavailable`, preventing data loss.

### 2. Multi-Track Machine Learning Framework
* **Track 1: Anomaly Guard (Device-Level Classification):** Unsupervised Isolation Forest model trained on the *UCI Household Power Consumption Dataset* (features: `Global_active_power`, `Voltage`, `Global_intensity`). It identifies statistical anomalies in device behavior (e.g., active power with zero current). Executed inline inside the Java pipeline using an application-scoped `OnnxSessionPool` (average inference: `<20ms`).
* **Track 2: Macro Load Monitor (Regional Demand Forecasting):** Random Forest Regressor trained on the *London SmartMeter Dataset* paired with DarkSky hourly weather logs (features: `Hour`, `DayOfWeek`, `Temperature`). It forecasts aggregated zone consumption 2 hours in advance to alert operators to potential grid overload. Run asynchronously via a Spring scheduled task.
* **Rule-Based Fallback:** If the ONNX engine encounters runtime failures, a fallback rules engine engages immediately to perform standard boundary validation.

### 3. Public Incident Reporting Gateway
* **Anonymous Submissions:** Citizen-facing endpoint (`POST /api/v1/complaints/anonymous`) allows reporting localized power issues.
* **Abuse Mitigations:** Rate-limited using a sliding token-bucket algorithm (`Bucket4j`) and deduplicated based on SHA-256 address hashes in a 60-minute window.
* **Triage Gate:** Submissions land in `PENDING_VERIFICATION` and require manual operator verification (`PATCH /api/v1/complaints/{id}/triage`) before escalating into work orders, preventing spam-flooding of maintenance dispatch teams.

### 4. Enterprise Security & Multi-Tenancy
* **Tenant Scoping:** Implements strict data isolation. JPA queries are intercepted by an AOP aspect (`TenantSecurityAspect`) that dynamically injects active tenant scopes, throwing `AccessDeniedException` on cross-tenant leakage.
* **Role-Based Access Control (RBAC):** Grid endpoints are restricted using standard JWT scopes (`Grid Operators`, `Field Inspectors`, `System Administrators`).

---

## 📂 Project Directory Structure

```
VoltiX-2/
├── src/                                  # Spring Boot Core Application
│   ├── main/
│   │   ├── java/com/voltix/
│   │   │   ├── telemetry/                # Telemetry ingress & worker threads
│   │   │   ├── analytics/                # ONNX session pool & forecasting/anomaly engines
│   │   │   ├── workflow/                 # System alerts & complaint lifecycle
│   │   │   ├── security/                 # JWT validation & Tenant security aspects
│   │   │   ├── persistence/              # Batch writers & partition managers
│   │   │   └── platform/                 # App bootstrapper, WebSockets, exception handling
│   │   └── resources/
│   │       ├── db/migration/             # Flyway migration scripts (V1, V2)
│   │       ├── models/                   # Exported ONNX ML models (.onnx)
│   │       └── application.yml           # Core config parameters
│   └── test/                             # Unit & Integration test suite
├── voltix-dashboard/                     # React & TS Vite Dashboard Application
│   ├── src/
│   │   ├── components/                   # Live Alert feed, Metrics Engine, Triage Panel
│   │   ├── pages/                        # Operator Dashboard, Public Submit page
│   │   └── hooks/                        # WebSocket hook subscriptions
│   └── package.json
├── dataset/                              # Datasets used for ML training (UCI, London)
├── train_anomaly_guard.py                # Python training script for Track 1 Isolation Forest
├── train_macro_monitor.py                # Python training script for Track 2 Demand Forecaster
├── validate_model_performance.py         # Python ML security audit validator
├── ingress_load_test.js                  # k6 load testing script
├── docker-compose.yml                    # Postgres infrastructure container definition
├── pom.xml                               # Maven project dependencies
└── VoltiX_SRS_Architecture_v2.md         # SRS & Architecture specification document
```

---

## 🛠️ Getting Started & Setup

### Prerequisites
* **Java 21** & **Maven 3.8+**
* **Node.js v18+** & **npm**
* **Python 3.10+** (with `pip`)
* **Docker** & **Docker Compose**

### 1. Launch the Database
Start a local PostgreSQL instance mapped to port `5433`:
```bash
docker compose up -d
```
*Schema migrations are managed via **Flyway** and will apply automatically when the Spring Boot application boots.*

### 2. Prepare & Verify the ML Models
If the `.onnx` models are missing from [src/main/resources/models](file:///d:/gand%20ghisai/projects/VoltiX-2/src/main/resources/models), or to retrain them, follow these steps:

1. **Install Python dependencies:**
   ```bash
   pip install numpy pandas scikit-learn skl2onnx onnxruntime
   ```
2. **Train & Export Track 1 (Anomaly Guard):**
   ```bash
   python train_anomaly_guard.py
   ```
3. **Train & Export Track 2 (Macro Load Monitor):**
   ```bash
   python train_macro_monitor.py
   ```
4. **Audit and Validate model accuracy bounds:**
   ```bash
   python validate_model_performance.py
   ```
   *The models must achieve >=95% precision/recall on synthetic validation anomalies before being approved for deployment.*

### 3. Run the Spring Boot Core
Start the backend server on port `8080`:
```bash
mvn spring-boot:run
```

### 4. Run the Operator Dashboard
Navigate to the frontend folder, install dependencies, and launch the Vite development server:
```bash
cd voltix-dashboard
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173) in your browser.

---

## 🧪 Testing & Validation

### Backend Test Suite
Executes unit tests, JPA integrations, websocket broadcast verification, and rate limiter assertions:
```bash
mvn test
```

### Ingestion Load Testing
To run a simulated load test hitting the ingress queue at a rate of 1,000 requests/second using **k6**:
1. Install k6 (see [k6 installation guide](https://k6.io/docs/get-started/installation/)).
2. Make sure the backend is running.
3. Execute the load test:
   ```bash
   k6 run ingress_load_test.js
   ```
   *The load test verifies the SLA target of p99 latency <20ms and checks the server's backpressure rejection behavior.*

---

## ⚙️ Key Configuration (from [application.yml](file:///d:/gand%20ghisai/projects/VoltiX-2/src/main/resources/application.yml))

* **Connection Pool:** Hikari pool connection timeout is restricted to `250ms` (`spring.datasource.hikari.connection-timeout=250`) to avoid connection exhaustion and prevent blocks on high-concurrency ingestion threads.
* **Worker Execution Limits:** Bounded thread executor queues up to `5000` items.
* **Telemetry Bounds:** Voltage is validated to realistic grid bands (`200.0V` to `260.0V`).
* **Complaint Rate Limits:** Standard anonymous endpoints are restricted to `20` submissions per minute per IP.
