import { useNavigate, Link } from 'react-router-dom';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAlertStore } from '../store/alertStore';
import { useAuth } from '../auth/AuthContext';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { Zap, LogOut, FileWarning, ShieldAlert } from 'lucide-react';
import type { SystemAlert, AlertSeverity } from '../types';

// ── Severity config ──────────────────────────────────────────
const SEV_COLOR: Record<AlertSeverity, string> = {
  LOW:      'bg-grid-dim',
  MEDIUM:   'bg-severity-medium',
  HIGH:     'bg-severity-high',
  CRITICAL: 'bg-severity-critical',
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

// ── Status Ribbon ────────────────────────────────────────────
function StatusRibbon() {
  const alerts = useAlertStore((s) => s.alerts);
  const total = alerts.length;
  const critical = alerts.filter((a) => a.severity === 'CRITICAL').length;
  const high = alerts.filter((a) => a.severity === 'HIGH').length;
  const medium = alerts.filter((a) => a.severity === 'MEDIUM').length;
  const open = alerts.filter((a) => a.status === 'OPEN').length;

  return (
    <div className="flex items-center gap-0 border-b border-grid-border-subtle bg-grid-surface text-xs font-mono h-9 shrink-0 overflow-x-auto">
      <RibbonCell label="TOTAL" value={total} color="text-grid-text" />
      <RibbonDivider />
      <RibbonCell label="CRITICAL" value={critical} color="text-severity-critical" pulse={critical > 0} />
      <RibbonDivider />
      <RibbonCell label="HIGH" value={high} color="text-severity-high" />
      <RibbonDivider />
      <RibbonCell label="MEDIUM" value={medium} color="text-severity-medium" />
      <RibbonDivider />
      <RibbonCell label="OPEN" value={open} color="text-accent-cyan" />
      <RibbonDivider />
      <RibbonCell label="METERS" value={new Set(alerts.map((a) => a.meterId)).size} color="text-grid-muted" />
    </div>
  );
}

function RibbonCell({ label, value, color, pulse }: { label: string; value: number; color: string; pulse?: boolean }) {
  return (
    <div className="flex items-center gap-2 px-4 h-full">
      <span className="text-grid-dim text-[10px] tracking-widest">{label}</span>
      <span className={`font-bold text-sm ${color} ${pulse ? 'animate-pulse-soft' : ''}`}>{value}</span>
    </div>
  );
}

function RibbonDivider() {
  return <div className="w-px h-4 bg-grid-border-subtle shrink-0" />;
}

// ── Alert Row ────────────────────────────────────────────────
function AlertRow({ alert, onAck }: { alert: SystemAlert; onAck: (id: number) => void }) {
  return (
    <div className={`flex items-center h-8 border-b border-grid-border-subtle hover:bg-grid-raised/50 transition-colors text-xs font-mono ${alert.severity === 'CRITICAL' ? 'animate-pulse-critical' : ''}`}>
      {/* Severity strip */}
      <div className={`w-[3px] h-full ${SEV_COLOR[alert.severity]} shrink-0`} />

      {/* Severity label */}
      <span className={`w-[72px] px-2 text-[10px] font-semibold tracking-wider ${SEV_TEXT[alert.severity]}`}>
        {alert.severity}
      </span>

      {/* Meter ID */}
      <span className="w-[64px] text-grid-text">{alert.meterId}</span>

      {/* Zone */}
      <span className="w-[56px] text-grid-dim">Z{alert.zoneId}</span>

      {/* Score */}
      <span className={`w-[56px] ${SEV_TEXT[alert.severity]}`}>
        {Number(alert.priorityScore).toFixed(2)}
      </span>

      {/* Type */}
      <span className="flex-1 text-grid-dim truncate">{alert.alertType.replace(/_/g, ' ')}</span>

      {/* Timestamp */}
      <span className="w-[72px] text-right text-grid-dim">{formatTime(alert.detectedAt)}</span>

      {/* ACK button */}
      <div className="w-[48px] flex justify-center">
        {alert.status === 'OPEN' ? (
          <button
            onClick={() => onAck(alert.alertId)}
            className="text-[9px] px-1.5 py-0.5 rounded border border-grid-border text-grid-muted hover:text-accent-cyan hover:border-accent-cyan transition-colors"
          >
            ACK
          </button>
        ) : (
          <span className="text-[9px] text-accent-cyan">ACK'd</span>
        )}
      </div>
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

  // Sort: CRITICAL first, then by detected time
  const sortedAlerts = [...alerts].sort((a, b) => {
    const order: Record<AlertSeverity, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
    const sevDiff = order[a.severity] - order[b.severity];
    if (sevDiff !== 0) return sevDiff;
    return new Date(b.detectedAt).getTime() - new Date(a.detectedAt).getTime();
  });

  return (
    <div className="h-screen bg-grid-base flex flex-col overflow-hidden">

      {/* ── Header Bar ── */}
      <header className="h-10 border-b border-grid-border bg-grid-surface flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-3">
          <Zap className="w-4 h-4 text-accent-amber" />
          <span className="font-semibold text-sm text-grid-text tracking-wide">VOLTIX</span>
          <span className="text-grid-dim text-[10px] font-mono tracking-widest">GRID COMMAND</span>
          <Link to="/report" className="ml-4 text-[10px] text-grid-muted hover:text-grid-text transition-colors flex items-center gap-1 font-mono tracking-wide">
            <FileWarning className="w-3 h-3" />
            REPORT
          </Link>
        </div>

        <div className="flex items-center gap-4">
          <ConnectionStatus status={status} reconnectAttempts={reconnectAttempts} />
          {user && (
            <span className="text-[10px] font-mono text-grid-muted">
              <span className="text-grid-text">{user.username}</span>
              <span className="ml-1 text-accent-cyan">{user.roles[0]}</span>
            </span>
          )}
          <button
            onClick={handleLogout}
            aria-label="Log out"
            className="text-grid-dim hover:text-accent-red transition-colors"
          >
            <LogOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </header>

      {/* ── Status Ribbon ── */}
      <StatusRibbon />

      {/* ── Main Content: Alert Feed + Right Panel ── */}
      <div className="flex-1 flex min-h-0">

        {/* ALERT FEED — left, dominant */}
        <div className="flex-1 flex flex-col min-w-0 border-r border-grid-border-subtle">
          {/* Section label */}
          <div className="flex items-center justify-between h-7 px-3 border-b border-grid-border-subtle bg-grid-base">
            <div className="flex items-center gap-2">
              <ShieldAlert className="w-3 h-3 text-grid-dim" />
              <span className="text-[10px] font-mono text-grid-dim tracking-widest">ALERT FEED</span>
              {unreadCount > 0 && (
                <span className="text-[10px] font-mono text-accent-red">{unreadCount} new</span>
              )}
            </div>
            {alerts.length > 0 && (
              <button onClick={clearAlerts} className="text-[9px] font-mono text-grid-dim hover:text-grid-muted transition-colors">
                CLEAR
              </button>
            )}
          </div>

          {/* Alert rows */}
          <div className="flex-1 overflow-y-auto">
            {sortedAlerts.length === 0 ? (
              <div className="flex items-center justify-center h-full text-grid-dim text-xs font-mono">
                Awaiting live alerts…
              </div>
            ) : (
              sortedAlerts.map((alert) => (
                <AlertRow key={alert.alertId} alert={alert} onAck={markAcknowledged} />
              ))
            )}
          </div>
        </div>

        {/* RIGHT PANEL — Complaints + Demo Trigger */}
        <div className="w-[340px] shrink-0 flex flex-col min-h-0 bg-grid-base">
          {/* Placeholder sections — will be filled with real components next */}
          <div className="flex-1 border-b border-grid-border-subtle flex flex-col">
            <div className="h-7 px-3 flex items-center border-b border-grid-border-subtle">
              <span className="text-[10px] font-mono text-grid-dim tracking-widest">COMPLAINT QUEUE</span>
            </div>
            <div className="flex-1 overflow-y-auto p-3">
              <span className="text-xs text-grid-dim font-mono">Complaint triage panel renders here</span>
            </div>
          </div>
          <div className="h-[100px] px-3 flex flex-col justify-center border-t border-grid-border-subtle">
            <span className="text-[10px] font-mono text-grid-dim tracking-widest mb-2">PIPELINE DEMO</span>
            <span className="text-xs text-grid-dim font-mono">Demo trigger renders here</span>
          </div>
        </div>
      </div>
    </div>
  );
}
