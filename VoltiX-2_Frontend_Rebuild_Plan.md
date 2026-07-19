# VoltiX-2 — Frontend Rebuild Plan

> **Purpose:** Take the dashboard from "good-looking skeleton demo with fake data" to "real, defensible operator tool" within review timeline constraints. Every phase has an exit criterion verified against real backend behavior — not visual inspection alone.
>
> **Context this plan responds to:** A frontend audit (see `report/frontend_audit.md` or equivalent) found the dashboard's core metrics panel is 100% fabricated (`Math.random()`), auth uses a hardcoded fake token that the real backend correctly rejects, and several smaller but real bugs exist (unread counter never decrements, dead Tailwind config, hardcoded WebSocket URL bypassing the dev proxy, dead code, no navigation).
>
> **Guiding principle for this rebuild: real data over polished data.** A plain UI showing genuine backend state is more defensible in a review than a beautiful UI showing fabricated numbers. If a reviewer asks "is this live," the answer must always be yes.

---

## Current State Summary (from audit)

| Area | Status |
|---|---|
| Stack (React 19, TS, Vite, Tailwind v4, Zustand, Recharts, STOMP, axios) | ✅ Solid, no changes needed |
| WebSocket infra (`useWebSocket.ts`, reconnect logic) | ✅ Good foundation, one bug (hardcoded URL) |
| Alert store (Zustand, rolling window, dedup) | ✅ Good foundation, one bug (unreadCount) |
| Grid metrics panel (voltage/current/kW charts) | 🔴 100% fake — `Math.random()`, no backend call |
| Auth | 🔴 Hardcoded invalid token; real login endpoint exists but unused; no login page |
| Complaint triage panel | ⚠️ Real, but permanently broken due to fake auth token |
| Public complaint form | ✅ Real and working |
| Navigation | 🔴 None — `/report` is undiscoverable |
| Route protection | 🔴 None — dashboard renders even with no valid session |

---

## Guiding Rules for This Rebuild

1. **No fabricated data, anywhere.** If a real backend endpoint doesn't exist for something, either build the minimal real endpoint or remove the panel — never simulate with random numbers.
2. **Verify each phase against real backend behavior before moving to the next.** Same standard as the backend work: raw request/response evidence, not "looks right in the browser."
3. **Scope each Qoder session narrowly** — one phase at a time, explicit file boundaries, so we don't repeat today's merge-conflict/stale-branch problems.
4. **Commit after each verified phase**, on its own branch, same discipline as backend fixes.

---

## Phase 1 — Authentication & Route Protection

**Why first:** Nothing else in the dashboard can be honestly demoed while auth is fake. This also directly answers your role-management question — the backend already correctly restricts what a login can produce (only 3 seeded users exist, no self-registration), so the frontend just needs to actually use real login instead of a hardcoded token.

### Tasks
1. Build a `/login` page: username/password form, calls `POST /api/v1/auth/login`, stores the real returned JWT.
2. Store the token appropriately for a real app (in-memory React context or a secure cookie — not `localStorage`, to avoid unnecessary XSS exposure of a bearer token).
3. Remove `DEV_TOKEN` and all references to it from `client.ts`.
4. Add route protection: if no valid token exists, any attempt to reach `/` redirects to `/login`.
5. Add a logout action (clears token, redirects to `/login`).
6. Show the logged-in username/role somewhere in the header (small, but proves the token's claims are actually being read, not just stored).

### Exit Criteria
- Visiting `/` with no session redirects to `/login`.
- Logging in as `operator` successfully loads the dashboard, and the Complaint Triage panel actually loads real data (no more "Failed to load complaints" error).
- Logging in with wrong credentials shows a real error, not a silent failure.
- Logging out and revisiting `/` redirects back to `/login`.

---

## Phase 2 — Demo Trigger & Removing Fabricated Data

**Why this matters most for your review:** This phase directly solves "how do I show alerts happening live in front of a reviewer" and removes the single biggest credibility risk in the current frontend (the fake charts).

### Part A — Remove the fake metrics panel
1. Delete `GridMetricsEngine`'s random-data generation entirely (`Math.random()`, `generatePointFromAlerts`).
2. Do not attempt to replace it with a real live telemetry polling endpoint under time pressure — that's a bigger backend addition than this timeline supports.
3. Replace the panel with real, cheap-to-fetch summary data already available or trivial to add: total alerts today, pending complaints count, active zones/meters count. Simple stat cards, not fake charts.

### Part B — Add a backend demo-trigger endpoint
1. New endpoint: `POST /api/v1/demo/simulate-telemetry` (operator/admin only, behind real auth).
2. On call, it synthetically generates a small batch of realistic telemetry readings (a mix of normal readings and at least one deliberately anomalous pattern — e.g., zero-current-under-load) and pushes them through the **real** ingestion pipeline (same path as `POST /api/v1/telemetry/submit`), not a shortcut that fakes an alert directly.
3. This is honest because it's driving genuine ONNX inference and the genuine alert dispatch path — it's a data *source* for demo purposes, not a fabricated *result*.

### Part C — Add the frontend trigger
1. A "Simulate Telemetry Event" button (operator/admin only) in the dashboard, calling the new endpoint.
2. On click, show a small loading/confirmation state, then let the existing WebSocket pipeline do its real job — a new alert should appear in the Live Alerts feed within a second or two, exactly as it would from real hardware.

### Exit Criteria
- No `Math.random()` or fabricated values remain anywhere in the codebase (verify with a grep).
- Clicking "Simulate Telemetry Event" while logged in as operator produces a real alert in the Live Alerts feed, provably through the real pipeline (check backend logs show the request went through `AnomalyDetectionEngine`, not a shortcut).
- This becomes the actual review demo moment — rehearse it.

---

## Phase 3 — Fix Known Bugs (cheap, do alongside Phases 1–2)

| Bug | Fix |
|---|---|
| `unreadCount` only increments, never decrements | `markAcknowledged` in `alertStore.ts` must decrement the count when an alert is acknowledged |
| Tailwind v3/v4 config split (dead `animate-pulse-critical`) | Consolidate all theme/animation definitions into `index.css` `@theme` (v4 way); delete the conflicting `tailwind.config.js` entries |
| WebSocket hardcodes `ws://localhost:8080/...` | Route through the Vite proxy config (`vite.config.ts`) instead, so it works in any environment, not just local dev |
| `App.css` — 184 lines of dead Vite starter template | Delete entirely; confirm nothing imports it first |
| No navigation between `/` and `/report` | Render the already-imported but unused `NavLink` in a simple header nav |
| Hardcoded zone list `[1,2,3,4,5]` in complaint form | Fetch real zones from an existing/available endpoint instead of hardcoding |
| Type mismatch: `complaintId` is `string` in one DTO, `number` in another | Pick one type (likely `number`, matching the backend), fix both DTOs |
| 500ms debounce lag before any validation feedback in complaint form | Reduce to something snappier (e.g. 150-200ms) or add immediate feedback for empty-field cases |

### Exit Criteria
- Acknowledging alerts actually reduces the unread badge count.
- Critical alerts visibly pulse (confirm the animation actually renders now).
- Both routes are reachable via visible navigation.
- No dead files remain (`App.css` gone).

---

## Phase 4 — Visual Polish (bounded — last, time-permitting only)

**Hard limit: 2-3 days maximum.** Only start this phase once Phases 1-3 are done and verified. A functionally honest but plain UI beats a polished UI with functional gaps.

### Tasks (in priority order, stop when time runs out)
1. Responsive layout check below `lg` breakpoint — confirm it doesn't just awkwardly stack.
2. Consistent spacing/alignment pass across panels.
3. Basic accessibility: `aria-label` on icon-only buttons (ACK, refresh), don't rely on color alone for severity (add a text/icon indicator alongside color).
4. If time remains: a simple overview → detail navigation structure (e.g., zone drill-down), but only if Phases 1-3 leave real slack in the schedule.

### Exit Criteria
- Dashboard is usable and doesn't visibly break on a laptop-sized screen.
- No functionality was sacrificed for visual changes.

---

## Working Process for Each Phase

1. Scoped Qoder prompt, narrow file boundaries, explicit exit criteria (same discipline as backend work).
2. After each phase, verify manually in the browser AND check real backend logs/responses — don't trust "looks right."
3. Commit on its own branch, merge only after verification.
4. Update the project's "known limitations" doc if anything gets deliberately deferred (e.g., full real-time telemetry polling, deeper zone drill-down).

---

## What This Plan Deliberately Does NOT Include (and why)

- **Full live telemetry polling/charting** — would require a new backend streaming/polling endpoint and meaningful frontend charting work under real telemetry volume; too large for remaining timeline. The demo-trigger button (Phase 2) solves the "show it working live" need without this.
- **Self-service registration UI** — matches the backend's deliberate scope decision; not needed since only 3 seeded accounts exist.
- **Historical telemetry / ML model output views** — real backend capability exists (ONNX scores, forecasts) but a dedicated UI for this is a nice-to-have beyond current scope; mention as future work if asked.
