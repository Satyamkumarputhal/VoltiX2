// ============================================================
// VoltiX TypeScript contracts — mirrors backend DTOs exactly
// ============================================================

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AlertStatus   = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type ComplaintStatus =
  | 'PENDING_VERIFICATION'
  | 'VERIFIED'
  | 'ESCALATED'
  | 'REJECTED';

// ── SystemAlert ──────────────────────────────────────────────
export interface SystemAlert {
  alertId:       number;
  tenantId:      number;
  meterId:       string;
  zoneId:        number;
  alertType:     string;
  severity:      AlertSeverity;
  anomalyScore:  number;
  priorityScore: number;
  status:        AlertStatus;
  detectedAt:    string; // ISO-8601
}

// ── PublicComplaint ──────────────────────────────────────────
export interface PublicComplaint {
  complaintId:     number;
  tenantId:        number;
  zoneId:          number;
  incidentAddress: string;
  description:     string;
  status:          ComplaintStatus;
  submittedAt:     string;
  triagedAt?:      string;
}

// ── TelemetryPacket ──────────────────────────────────────────
export interface TelemetryPacket {
  meterId:       string;
  tenantId:      number;
  zoneId:        number;
  transactionId: string;
  voltage:       number;
  current:       number;
  kwConsumed:    number;
  recordedAt:    string;
}

// ── ZoneAggregate ────────────────────────────────────────────
export interface ZoneAggregate {
  zoneId:      number;
  tenantId:    number;
  avgVoltage:  number;
  avgCurrent:  number;
  totalKw:     number;
  hour:        string;
}

// ── ZoneForecast ─────────────────────────────────────────────
// Mirrors the backend GET /api/v1/forecasts response exactly.
// The backend intentionally omits tenantId (the endpoint is already
// tenant-scoped by the caller's JWT). Timestamp semantics:
//   aggregatedHour      = source hour whose actual telemetry was aggregated
//   forecastTargetHour  = the future hour the forecast predicts (authoritative
//                         "forecast for" time — never derived as +2h here)
//   forecastKw2h        = predicted load (kW) for forecastTargetHour
//   forecastGeneratedAt = when the forecast was generated/persisted
export interface ZoneForecast {
  zoneId:              number;
  aggregatedHour:      string; // ISO-8601
  forecastTargetHour:  string; // ISO-8601
  forecastKw2h:        number;
  forecastGeneratedAt: string; // ISO-8601
}

// ── API response wrappers ────────────────────────────────────
export interface ApiError {
  status:  number;
  message: string;
  detail?: string;
}

export interface ComplaintSubmitResponse {
  status:      string;
  complaintId?: number;
  message:     string;
}

// ── Chart data point ─────────────────────────────────────────
export interface MetricDataPoint {
  time:      string;
  voltage:   number;
  current:   number;
  kw:        number;
}
