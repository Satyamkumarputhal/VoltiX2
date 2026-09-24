import { useWebSocket } from '../hooks/useWebSocket';
import { useAlertStore } from '../store/alertStore';
import { useAuth } from '../auth/AuthContext';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { ComplaintTriagePanel } from '../components/ComplaintTriagePanel';
import { ForecastPanel } from '../components/ForecastPanel';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { Zap, AlertTriangle, TrendingUp, Loader2, Shield, Wifi, WifiOff, AlertCircle, ChevronRight } from 'lucide-react';
import { useState, useCallback, useMemo, useEffect } from 'react';
import client from '../api/client';
import type { SystemAlert, AlertSeverity } from '../types';
import { StatCard, Badge, Card } from '../shared/ui';
import { getSeverityConfig } from '../shared/ui/status';

// ── Alert Row (compact) ────────────────────────────────────────────────
function AlertRow({ alert, onAck, odd }: { alert: SystemAlert; onAck: (id: number) => void; odd: boolean }) {
  const sev = getSeverityConfig(alert.severity);
  return (
    <div className={`flex items-center h-9 border-l-[3px] ${sev.borderLeft} ${odd ? 'bg-grid-raised/30' : ''} hover:bg-grid-raised/60 transition-colors ${alert.severity === 'CRITICAL' ? 'animate-pulse-critical' : ''}`}>
      <Badge variant={alert.severity} size="sm" className="w-[72px] pl-2 text-[10px] font-mono font-semibold shrink-0">
        {alert.severity}
      </Badge>
      <span className="w-[70px] text-[12px] font-mono text-grid-text shrink-0">{alert.meterId}</span>
      <span className="w-[40px] text-[12px] font-mono text-grid-dim shrink-0">Z{alert.zoneId}</span>
      <span className={`w-[60px] text-[12px] font-mono font-semibold ${sev.scoreText} shrink-0`}>
        {Number(alert.priorityScore).toFixed(3)}
      </span>
      <span className="flex-1 text-[12px] text-grid-muted truncate pr-2">{alert.alertType.replace(/_/g, ' ')}</span>
      <span className="w-[65px] text-right text-[11px] font-mono text-grid-dim shrink-0 pr-2">{formatTime(alert.detectedAt)}</span>
      <div className="w-[48px] flex justify-center shrink-0">
        {alert.status === 'OPEN' ? (
          <button
            onClick={() => onAck(alert.alertId)}
            className="text-[9px] font-mono px-1.5 py-0.5 rounded-[3px] border border-grid-border text-grid-muted hover:text-accent-amber hover:border-accent-amber/50 transition-colors"
          >
            ACK
          </button>
        ) : (
          <span className="text-[9px] font-mono text-accent-green">ACK'd</span>
        )}
      </div>
    </div>
  );
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch { return '—'; }
}

// ── Critical Alert Summary Row ────────────────────────────────────────
function CriticalAlertSummaryRow({ alert }: { alert: SystemAlert }) {
  const sev = getSeverityConfig(alert.severity);
  return (
    <div className={`flex items-center gap-2 h-8 px-3 ${sev.bg} rounded-[8px] ${alert.severity === 'CRITICAL' ? 'animate-pulse-critical' : ''}`}>
      <Badge variant={alert.severity} size="sm" dot className="shrink-0">{alert.severity}</Badge>
      <span className="w-[70px] text-[12px] font-mono text-grid-text shrink-0">{alert.meterId}</span>
      <span className="w-[40px] text-[12px] font-mono text-grid-dim shrink-0">Z{alert.zoneId}</span>
      <span className={`w-[60px] text-[12px] font-mono font-semibold ${sev.scoreText} shrink-0`}>
        {Number(alert.priorityScore).toFixed(3)}
      </span>
      <span className="flex-1 text-[12px] text-grid-muted truncate">{alert.alertType.replace(/_/g, ' ')}</span>
    </div>
  );
}

// ── Demo Trigger ─────────────────────────────────────────────
function DemoTriggerButton() {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const { user } = useAuth();
  const canSimulate = user?.roles?.some((r) =>
    r === 'OPERATOR' || r === 'ADMIN' || r === 'ROLE_OPERATOR' || r === 'ROLE_ADMIN'
  );

  const handleClick = useCallback(async () => {
    setLoading(true);
    setResult(null);
    try {
      const res = await client.post<{ count: number }>('/demo/simulate-telemetry');
      setResult(`${res.data.count} readings submitted`);
      setTimeout(() => setResult(null), 4000);
    } catch {
      setResult('Failed');
      setTimeout(() => setResult(null), 3000);
    } finally {
      setLoading(false);
    }
  }, []);

  if (!canSimulate) return null;

  return (
    <div className="flex items-center gap-3">
      <button
        onClick={handleClick}
        disabled={loading}
        className="btn btn-primary"
      >
        {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Zap className="w-3.5 h-3.5" />}
        Simulate Event
      </button>
      {result && <span className="text-[12px] font-mono text-accent-green">{result}</span>}
    </div>
  );
}

// ── Pipeline Status ──────────────────────────────────────────
function PipelineStatus({ wsStatus, alertCount, forecastAvailable }: {
  wsStatus: string;
  alertCount: number;
  forecastAvailable: boolean;
}) {
  const isConnected = wsStatus === 'CONNECTED';
  return (
    <Card variant="outlined" padding="md" className="flex items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <div className={`w-8 h-8 rounded-full flex items-center justify-center ${isConnected ? 'bg-accent-green/20' : 'bg-accent-red/20'}`}>
          {isConnected ? (
            <Wifi className="w-4 h-4 text-accent-green" />
          ) : (
            <WifiOff className="w-4 h-4 text-accent-red" />
          )}
        </div>
        <div>
          <p className="text-xs font-semibold text-grid-text">Telemetry Pipeline</p>
          <p className="text-[10px] text-grid-dim">
            {isConnected ? 'WebSocket live — receiving alerts' : `Disconnected`}
          </p>
        </div>
      </div>
      <div className="flex items-center gap-4 text-right hidden sm:flex">
        <div>
          <p className="text-[10px] text-grid-dim">Alert Feed</p>
          <p className="text-[13px] font-mono font-semibold text-grid-text">{alertCount}</p>
        </div>
        <div>
          <p className="text-[10px] text-grid-dim">Forecast</p>
          <p className={`text-[13px] font-mono font-semibold ${forecastAvailable ? 'text-accent-green' : 'text-grid-muted'}`}>
            {forecastAvailable ? 'Available' : '—'}
          </p>
        </div>
      </div>
    </Card>
  );
}

// ── Empty State ──────────────────────────────────────────────
function EmptyState({ icon: Icon, title, description }: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[80px] gap-2 text-center px-6">
      <Icon className="w-6 h-6 text-grid-dim opacity-40" />
      <p className="text-[11px] font-mono uppercase tracking-wide text-grid-muted">{title}</p>
      <p className="text-xs text-grid-dim">{description}</p>
    </div>
  );
}

// ── Dashboard ────────────────────────────────────────────────
export default function Dashboard() {
  const { status: wsStatus, reconnectAttempts: wsReconnectAttempts } = useWebSocket();
  const alerts = useAlertStore((s) => s.alerts);
  const markAcknowledged = useAlertStore((s) => s.markAcknowledged);
  const unreadCount = useAlertStore((s) => s.unreadCount);
  const clearAlerts = useAlertStore((s) => s.clearAlerts);
  const fetchAlerts = useAlertStore((s) => s.fetchAlerts);

  const openAlerts = useMemo(() => alerts.filter(a => a.status === 'OPEN'), [alerts]);

  const sortedAlerts = useMemo(() => [...openAlerts].sort((a, b) => {
    const order: Record<AlertSeverity, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
    const sevDiff = order[a.severity] - order[b.severity];
    if (sevDiff !== 0) return sevDiff;
    return new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime();
  }), [openAlerts]);

  const criticalAlerts = useMemo(() => sortedAlerts.filter(a => a.severity === 'CRITICAL'), [sortedAlerts]);
  const highAlerts = useMemo(() => sortedAlerts.filter(a => a.severity === 'HIGH'), [sortedAlerts]);
  const requiresAttention = useMemo(() => [...criticalAlerts, ...highAlerts].slice(0, 5), [criticalAlerts, highAlerts]);

  const total = openAlerts.length;
  const critical = criticalAlerts.length;
  const high = highAlerts.length;
  const medium = openAlerts.filter((a) => a.severity === 'MEDIUM').length;
  const open = total;
  const meters = new Set(openAlerts.map((a) => a.meterId)).size;

  // Hydrate alert store with persisted alerts on mount
  useEffect(() => {
    fetchAlerts();
  }, [fetchAlerts]);

  return (
    <div className="h-screen bg-grid-base flex flex-col overflow-hidden">
      {/* ── Content ── */}
      <div className="flex-1 flex flex-col gap-3 p-4 overflow-auto min-h-0">

        {/* ── A. GRID OVERVIEW ── */}
        <section aria-labelledby="grid-overview" className="shrink-0">
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-grid-dim">Grid Overview</h2>
            <ConnectionStatus status={wsStatus as 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED' | 'ERROR'} reconnectAttempts={wsReconnectAttempts} />
          </div>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            <StatCard label="ACTIVE ALERTS" value={total} severity={total > 0 ? (critical > 0 ? 'CRITICAL' : high > 0 ? 'HIGH' : medium > 0 ? 'MEDIUM' : 'LOW') : 'LOW'} />
            <StatCard label="CRITICAL" value={critical} severity="CRITICAL" />
            <StatCard label="HIGH" value={high} severity="HIGH" />
            <StatCard label="MEDIUM" value={medium} severity="MEDIUM" />
            <StatCard label="OPEN" value={open} color="text-accent-cyan" />
            <StatCard label="ACTIVE METERS" value={meters} color="text-grid-text" />
          </div>
        </section>

        {/* ── B. MAIN OPERATIONS AREA (2-column on desktop) ── */}
        <section aria-labelledby="operations-area" className="flex-1 min-h-0">
          <div className="grid grid-cols-1 lg:grid-cols-[70%_30%] gap-3 h-full min-h-0">
            {/* LEFT COLUMN: Active Alerts (top) + Forecast (bottom) */}
            <div className="flex flex-col gap-3 h-full min-h-0">
              {/* Active Alerts */}
              <section aria-labelledby="active-alerts" className="flex-1 min-h-0 flex flex-col">
                <div className="flex items-center justify-between mb-1 shrink-0">
                  <h3 id="active-alerts" className="text-xs font-semibold uppercase tracking-wider text-grid-dim flex items-center gap-2">
                    <Shield className="w-3.5 h-3.5 text-accent-amber" />
                    Active Alerts
                    {unreadCount > 0 && (
                      <span className="text-[10px] font-mono text-accent-red px-1.5 py-0.5 rounded bg-accent-red/10">{unreadCount} new</span>
                    )}
                  </h3>
                  {alerts.length > 0 && (
                    <button onClick={clearAlerts} className="text-[10px] text-grid-dim hover:text-grid-muted transition-colors">
                      Clear all
                    </button>
                  )}
                </div>

                <Card variant="default" padding="none" className="flex flex-col flex-1 min-h-0 overflow-hidden">
                  {/* Table header */}
                  <div className="flex items-center h-8 border-b border-grid-border-subtle text-[10px] text-grid-dim tracking-wider uppercase px-3 bg-grid-raised/30 shrink-0">
                    <span className="w-[3px] shrink-0" />
                    <span className="w-[72px] pl-2">SEV</span>
                    <span className="w-[70px]">METER</span>
                    <span className="w-[40px]">ZN</span>
                    <span className="w-[60px]">SCORE</span>
                    <span className="flex-1">TYPE</span>
                    <span className="w-[65px] text-right pr-2">TIME</span>
                    <span className="w-[48px] text-center">ACT</span>
                  </div>

                  {/* Rows */}
                  <div className="flex-1 overflow-y-auto min-h-0">
                    {sortedAlerts.length === 0 ? (
                      <EmptyState
                        icon={AlertCircle}
                        title="No Active Alerts"
                        description="Grid conditions are currently within monitored thresholds."
                      />
                    ) : (
                      sortedAlerts.map((alert, i) => (
                        <AlertRow key={alert.alertId} alert={alert} onAck={markAcknowledged} odd={i % 2 === 1} />
                      ))
                    )}
                  </div>
                </Card>
              </section>

              {/* Forecast Intelligence */}
              <section aria-labelledby="forecast-intelligence" className="shrink-0 min-h-[240px] max-h-[360px] flex flex-col">
                <h3 id="forecast-intelligence" className="text-xs font-semibold uppercase tracking-wider text-grid-dim mb-1 flex items-center gap-2 shrink-0">
                  <TrendingUp className="w-3.5 h-3.5 text-accent-amber" />
                  2H Load Forecast
                </h3>
                <ErrorBoundary panelName="Load Forecast">
                  <ForecastPanel />
                </ErrorBoundary>
              </section>
            </div>

            {/* RIGHT COLUMN: Requires Attention + Complaint Triage + Pipeline + Demo */}
            <div className="h-full min-h-0 flex flex-col gap-2 min-w-0">
              {/* Requires Attention — bounded height, internal scroll */}
              {(critical > 0 || high > 0) && (
                <section aria-labelledby="requires-attention" className="shrink-0">
                  <h3 id="requires-attention" className="text-xs font-semibold uppercase tracking-wider text-grid-dim mb-1 flex items-center gap-2">
                    <AlertTriangle className="w-3.5 h-3.5 text-accent-red" />
                    Requires Attention
                  </h3>
                  <div className="max-h-[280px] overflow-y-auto grid grid-cols-1 gap-1.5 pr-1">
                    {requiresAttention.map((alert) => (
                      <CriticalAlertSummaryRow key={alert.alertId} alert={alert} />
                    ))}
                  </div>
                </section>
              )}

              {/* Complaint Triage */}
              <section aria-labelledby="complaint-triage" className="shrink-0">
                <h3 id="complaint-triage" className="text-xs font-semibold uppercase tracking-wider text-grid-dim mb-1 flex items-center gap-2">
                  <AlertTriangle className="w-3.5 h-3.5 text-accent-amber" />
                  Complaint Triage
                </h3>
                <ErrorBoundary panelName="Complaint Triage">
                  <ComplaintTriagePanel />
                </ErrorBoundary>
              </section>

              {/* Pipeline Status */}
              <section aria-labelledby="pipeline-status" className="shrink-0">
                <h3 id="pipeline-status" className="text-xs font-semibold uppercase tracking-wider text-grid-dim mb-1 flex items-center gap-2">
                  <Zap className="w-3.5 h-3.5 text-accent-cyan" />
                  Pipeline Status
                </h3>
                <PipelineStatus
                  wsStatus={wsStatus}
                  alertCount={total}
                  forecastAvailable={true}
                />
              </section>

              {/* Demo / Test Pipeline */}
              <section aria-labelledby="demo-pipeline" className="shrink-0">
                <h3 id="demo-pipeline" className="text-xs font-semibold uppercase tracking-wider text-grid-dim mb-1 flex items-center gap-2">
                  <ChevronRight className="w-3.5 h-3.5 text-grid-dim" />
                  Demo / Test Pipeline
                </h3>
                <Card variant="outlined" padding="md" className="bg-grid-raised/30 border-accent-amber/30">
                  <p className="text-[11px] text-grid-dim mb-2">
                    Inject telemetry through the real ONNX anomaly pipeline
                  </p>
                  <DemoTriggerButton />
                </Card>
              </section>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}