import { useNavigate, Link } from 'react-router-dom';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAlertStore } from '../store/alertStore';
import { useAuth } from '../auth/AuthContext';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { ComplaintTriagePanel } from '../components/ComplaintTriagePanel';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { Zap, LogOut, FileWarning, ShieldAlert, Loader2 } from 'lucide-react';
import { useState, useCallback } from 'react';
import client from '../api/client';
import type { SystemAlert, AlertSeverity } from '../types';

// ── Severity config ──────────────────────────────────────────
const SEV_STRIP: Record<AlertSeverity, string> = {
  LOW:      'border-l-grid-dim',
  MEDIUM:   'border-l-severity-medium',
  HIGH:     'border-l-severity-high',
  CRITICAL: 'border-l-severity-critical',
};
const SEV_TEXT: Record<AlertSeverity, string> = {
  LOW:      'text-grid-muted',
  MEDIUM:   'text-severity-medium',
  HIGH:     'text-severity-high',
  CRITICAL: 'text-severity-critical',
};

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch { return '—'; }
}

// ── Stat Card ────────────────────────────────────────────────
function StatCard({ label, value, color }: { label: string; value: string | number; color: string }) {
  return (
    <div className="card px-5 py-4 flex flex-col gap-1.5">
      <span className="stat-label">{label}</span>
      <span className={`stat-value ${color}`}>{value}</span>
    </div>
  );
}

// ── Alert Row ────────────────────────────────────────────────
function AlertRow({ alert, onAck, odd }: { alert: SystemAlert; onAck: (id: number) => void; odd: boolean }) {
  return (
    <div className={`flex items-center h-10 border-l-[3px] ${SEV_STRIP[alert.severity]} ${odd ? 'bg-grid-raised/30' : ''} hover:bg-grid-raised/60 transition-colors ${alert.severity === 'CRITICAL' ? 'animate-pulse-critical' : ''}`}>
      {/* Severity label */}
      <span className={`w-[90px] pl-3 text-[12px] font-mono font-semibold ${SEV_TEXT[alert.severity]}`}>
        {alert.severity}
      </span>

      {/* Meter ID */}
      <span className="w-[80px] text-[13px] font-mono text-grid-text">{alert.meterId}</span>

      {/* Zone */}
      <span className="w-[50px] text-[13px] font-mono text-grid-dim">Z{alert.zoneId}</span>

      {/* Score */}
      <span className={`w-[70px] text-[13px] font-mono font-semibold ${SEV_TEXT[alert.severity]}`}>
        {Number(alert.priorityScore).toFixed(2)}
      </span>

      {/* Type */}
      <span className="flex-1 text-[13px] text-grid-muted">{alert.alertType.replace(/_/g, ' ')}</span>

      {/* Timestamp */}
      <span className="w-[80px] text-right text-[12px] font-mono text-grid-dim pr-3">{formatTime(alert.detectedAt)}</span>

      {/* ACK button */}
      <div className="w-[56px] flex justify-center pr-2">
        {alert.status === 'OPEN' ? (
          <button
            onClick={() => onAck(alert.alertId)}
            className="text-[10px] font-mono px-2 py-0.5 rounded-[4px] border border-grid-border text-grid-muted hover:text-accent-amber hover:border-accent-amber/50 transition-colors"
          >
            ACK
          </button>
        ) : (
          <span className="text-[10px] font-mono text-accent-green">ACK'd</span>
        )}
      </div>
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

// ── Dashboard ────────────────────────────────────────────────
export default function Dashboard() {
  const { status, reconnectAttempts } = useWebSocket();
  const alerts = useAlertStore((s) => s.alerts);
  const markAcknowledged = useAlertStore((s) => s.markAcknowledged);
  const unreadCount = useAlertStore((s) => s.unreadCount);
  const clearAlerts = useAlertStore((s) => s.clearAlerts);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const sortedAlerts = [...alerts].sort((a, b) => {
    const order: Record<AlertSeverity, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
    const sevDiff = order[a.severity] - order[b.severity];
    if (sevDiff !== 0) return sevDiff;
    return new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime();
  });

  const total = alerts.length;
  const critical = alerts.filter((a) => a.severity === 'CRITICAL').length;
  const medium = alerts.filter((a) => a.severity === 'MEDIUM').length;
  const meters = new Set(alerts.map((a) => a.meterId)).size;

  return (
    <div className="h-screen bg-grid-base flex flex-col overflow-hidden">

      {/* ── Header ── */}
      <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-5 shrink-0">
        <div className="flex items-center gap-3">
          <Zap className="w-4 h-4 text-accent-amber" />
          <span className="font-semibold text-[15px] text-grid-text">VoltiX</span>
          <span className="text-grid-muted text-[13px]">/ Grid Command</span>
          <Link to="/report" className="ml-5 text-[12px] text-grid-dim hover:text-grid-text transition-colors flex items-center gap-1.5">
            <FileWarning className="w-3.5 h-3.5" />
            Report
          </Link>
        </div>

        <div className="flex items-center gap-5">
          <ConnectionStatus status={status} reconnectAttempts={reconnectAttempts} />
          {user && (
            <span className="text-[12px] text-grid-muted">
              <span className="text-grid-text font-medium">{user.username}</span>
              <span className="ml-1.5 text-accent-amber uppercase text-[10px] font-semibold tracking-wide">{user.roles[0]}</span>
            </span>
          )}
          <button onClick={handleLogout} aria-label="Log out" className="text-grid-dim hover:text-accent-red transition-colors">
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* ── Content ── */}
      <div className="flex-1 flex flex-col gap-4 p-4 overflow-hidden">

        {/* ── Stat Cards Row ── */}
        <div className="grid grid-cols-4 gap-3 shrink-0">
          <StatCard label="TOTAL" value={total} color="text-grid-text" />
          <StatCard label="CRITICAL" value={critical} color="text-severity-critical" />
          <StatCard label="MEDIUM" value={medium} color="text-severity-medium" />
          <StatCard label="METERS" value={meters} color="text-grid-text" />
        </div>

        {/* ── Main Panels ── */}
        <div className="flex-1 flex gap-3 min-h-0">

          {/* Alert Feed */}
          <div className="card flex-1 flex flex-col min-w-0 overflow-hidden">
            {/* Panel header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-grid-border">
              <div className="flex items-center gap-2">
                <ShieldAlert className="w-4 h-4 text-accent-amber" />
                <span className="text-[14px] font-semibold text-grid-text">Live alerts</span>
                {unreadCount > 0 && (
                  <span className="text-[11px] font-mono text-accent-red ml-1">{unreadCount} new</span>
                )}
              </div>
              {alerts.length > 0 && (
                <button onClick={clearAlerts} className="text-[11px] text-grid-dim hover:text-grid-muted transition-colors">
                  Clear
                </button>
              )}
            </div>

            {/* Table header */}
            <div className="flex items-center h-8 border-b border-grid-border-subtle text-[11px] text-grid-dim tracking-wide uppercase px-0 bg-grid-raised/30">
              <span className="w-[3px] shrink-0" />
              <span className="w-[90px] pl-3">SEV</span>
              <span className="w-[80px]">METER</span>
              <span className="w-[50px]">ZN</span>
              <span className="w-[70px]">SCORE</span>
              <span className="flex-1">TYPE</span>
              <span className="w-[80px] text-right pr-3">TIME</span>
              <span className="w-[56px] text-center pr-2">ACT</span>
            </div>

            {/* Rows */}
            <div className="flex-1 overflow-y-auto">
              {sortedAlerts.length === 0 ? (
                <div className="h-20 flex items-center justify-center text-grid-dim text-[13px]">
                  Awaiting live alerts…
                </div>
              ) : (
                sortedAlerts.map((alert, i) => (
                  <AlertRow key={alert.alertId} alert={alert} onAck={markAcknowledged} odd={i % 2 === 1} />
                ))
              )}
            </div>
          </div>

          {/* Right Column */}
          <div className="w-[380px] shrink-0 flex flex-col gap-3 min-h-0">

            {/* Complaint Queue */}
            <div className="card flex-1 flex flex-col min-h-0 overflow-hidden">
              <div className="px-4 py-3 border-b border-grid-border">
                <span className="text-[14px] font-semibold text-grid-text">Complaint Queue</span>
              </div>
              <div className="flex-1 overflow-y-auto">
                <ErrorBoundary panelName="Complaint Triage">
                  <ComplaintTriagePanel />
                </ErrorBoundary>
              </div>
            </div>

            {/* Demo Trigger */}
            <div className="card px-4 py-4 shrink-0">
              <span className="stat-label block mb-2">Pipeline Demo</span>
              <p className="text-[12px] text-grid-dim mb-3">
                Inject telemetry through the real ONNX anomaly pipeline
              </p>
              <DemoTriggerButton />
            </div>
          </div>
        </div>
      </div>

      {/* ── Footer ── */}
      <footer className="shrink-0 h-8 border-t border-grid-border bg-grid-surface flex items-center justify-between px-5 text-[10px] text-grid-dim">
        <span>VoltiX Grid Monitor v0.1 — Zone 1 risk ×3.0 — ONNX Isolation Forest</span>
        <div className="flex items-center gap-3">
          <span>WS: <span className={status === 'CONNECTED' ? 'text-accent-green' : 'text-accent-red'}>{status}</span></span>
          <span>{alerts.length} in feed</span>
        </div>
      </footer>
    </div>
  );
}
