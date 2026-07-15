# VoltiX Defect Tracker

This file tracks defects, security vulnerabilities, performance regressions, and accepted limitations.

| Defect ID | Claimed Behavior / Phase | Actual Finding | Severity | Status |
|-----------|---------------------------|----------------|----------|--------|
| VT-DEF-01 | Phase 1: Hikari Connection Timeout | Database connection timeout is not configured in properties, defaulting to 30s. | HIGH | FIXED |
| VT-DEF-02 | Phase 1: Performance Load Tests | No performance tests or load scripts exist in the codebase. | HIGH | RUNNING |
| VT-DEF-03 | Phase 1: Ingestion Durability | No background worker reprocesses or reconciles staging records left as `processed = false` after a crash. | LOW | ACCEPTED-LIMITATION |
