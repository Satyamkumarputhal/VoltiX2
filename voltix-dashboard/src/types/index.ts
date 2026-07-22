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
