import { useNavigate, Link } from 'react-router-dom';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAlertStore } from '../store/alertStore';
import { useAuth } from '../auth/AuthContext';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { ComplaintTriagePanel } from '../components/ComplaintTriagePanel';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { Zap, LogOut, FileWarning, ShieldAlert, Loader2, ClipboardCheck } from 'lucide-react';
import { useState, useCallback } from 'react';
import client from '../api/client';
import type { SystemAlert, AlertSeverity } from '../types';

// ── Severity config ──────────────────────────────────────────
const SEV_STRIP: Record<AlertSeverity, string> = {
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
    <div className="flex items-center border-b border-grid-border bg-grid-surface h-9 shrink-0 px-1">
      <RibbonCell label="TOTAL" value={total} color="text-grid-text" />
      <RibbonCell label="CRIT" value={critical} color="text-severity-critical" pulse={critical > 0} />
      <RibbonCell label="HIGH" value={high} color="text-severity-high" />
      <RibbonCell label="MED" value={medium} color="text-severity-medium" />
      <RibbonCell label="OPEN" value={open} color="text-accent-cyan" />
      <RibbonCell label="MTRS" value={new Set(alerts.map((a) => a.meterId)).size} color="text-grid-muted" />
    </div>
  );
}

function RibbonCell({ label, value, color, pulse }: { label: string; value: number; color: string; pulse?: boolean }) {
  return (
    <div className="flex items-center gap-1.5 px-2.5 py-1 mx-0.5 rounded-[3px] bg-grid-raised border border-grid-border-subtle">
      <span className="text-grid-dim text-[9px] tracking-wider font-mono">{label}</span>
      <span className={`font-bold text-[13px] font-mono ${color} ${pulse ? 'animate-pulse-soft' : ''}`}>{value}</span>
    </div>
  );
}

// ── Alert Row ────────────────────────────────────────────────
function AlertRow({ alert, onAck, odd }: { alert: SystemAlert; onAck: (id: number) => void; odd: boolean }) {
  return (
    <div className={`flex items-center h-8 border-b border-grid-border-subtle transition-colors text-xs font-mono ${odd ? 'bg-grid-raised/40' : 'bg-transparent'} hover:bg-grid-elevated/60 ${alert.severity === 'CRITICAL' ? 'animate-pulse-critical' : ''}`}>
      {/* Severity strip */}
      <div className={`w-[3px] h-full ${SEV_STRIP[alert.severity]} shrink-0`} />

      {/* Severity label */}
      <span className={`w-[68px] px-2 text-[10px] font-semibold tracking-wider ${SEV_TEXT[alert.severity]}`}>
        {alert.severity}
      </span>

      {/* Meter ID */}
      <span className="w-[60px] text-grid-text">{alert.meterId}</span>

      {/* Zone */}
      <span className="w-[44px] text-grid-dim">Z{alert.zoneId}</span>

      {/* Score */}
      <span className={`w-[52px] ${SEV_TEXT[alert.severity]}`}>
        {Number(alert.priorityScore).toFixed(2)}
      </span>

      {/* Type */}
      <span className="flex-1 text-grid-dim truncate">{alert.alertType.replace(/_/g, ' ')}</span>

      {/* Timestamp */}
      <span className="w-[68px] text-right text-grid-dim pr-1">{formatTime(alert.detectedAt)}</span>

      {/* ACK button */}
      <div className="w-[44px] flex justify-center">
        {alert.status === 'OPEN' ? (
          <button
            onClick={() => onAck(alert.alertId)}
            className="text-[9px] px-1.5 py-0.5 rounded-[2px] border border-grid-border text-grid-muted hover:text-accent-cyan hover:border-accent-cyan transition-colors"
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

// ── Demo Trigger Button ──────────────────────────────────────
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
        className="flex items-center gap-1.5 px-3 py-1.5 text-[10px] font-mono tracking-wide rounded-[3px] bg-accent-blue/15 text-accent-blue border border-accent-blue/30 hover:bg-accent-blue/25 transition-colors disabled:opacity-50"
      >
        {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <Zap className="w-3 h-3" />}
        SIMULATE
      </button>
      {result && <span className="text-[10px] font-mono text-accent-green">{result}</span>}
    </div>
  );
}

// ── Section Panel wrapper ────────────────────────────────────
function Panel({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`bg-grid-surface border border-grid-border rounded-[5px] overflow-hidden ${className}`}>
      {children}
    </div>
  );
}

function PanelHeader({ icon, label, right }: { icon: React.ReactNode; label: string; right?: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between h-8 px-3 bg-grid-raised border-b border-grid-border">
      <div className="flex items-center gap-2">
        {icon}
        <span className="text-[10px] font-mono text-grid-muted tracking-widest">{label}</span>
      </div>
      {right}
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

      {/* ── Main Content ── */}
      <div className="flex-1 flex gap-2 p-2 min-h-0">

        {/* LEFT: Alert Feed Panel */}
        <Panel className="flex-1 flex flex-col min-w-0">
          <PanelHeader
            icon={<ShieldAlert className="w-3 h-3 text-grid-dim" />}
            label="ALERT FEED"
            right={
              <div className="flex items-center gap-3">
                {unreadCount > 0 && (
                  <span className="text-[10px] font-mono text-accent-red">{unreadCount} new</span>
                )}
                {alerts.length > 0 && (
                  <button onClick={clearAlerts} className="text-[9px] font-mono text-grid-dim hover:text-grid-muted transition-colors">
                    CLEAR
                  </button>
                )}
              </div>
            }
          />

          {/* Column header */}
          <div className="flex items-center h-6 border-b border-grid-border-subtle text-[9px] font-mono text-grid-dim tracking-wider bg-grid-raised/50 px-0">
            <div className="w-[3px] shrink-0" />
            <span className="w-[68px] px-2">SEV</span>
            <span className="w-[60px]">METER</span>
            <span className="w-[44px]">ZONE</span>
            <span className="w-[52px]">SCORE</span>
            <span className="flex-1">TYPE</span>
            <span className="w-[68px] text-right pr-1">TIME</span>
            <span className="w-[44px] text-center">ACT</span>
          </div>

          {/* Alert rows — scrollable, capped */}
          <div className="flex-1 overflow-y-auto min-h-0">
            {sortedAlerts.length === 0 ? (
              <div className="h-20 flex items-center justify-center text-grid-dim text-xs font-mono border-b border-grid-border-subtle">
                Awaiting live alerts…
              </div>
            ) : (
              sortedAlerts.map((alert, i) => (
                <AlertRow key={alert.alertId} alert={alert} onAck={markAcknowledged} odd={i % 2 === 1} />
              ))
            )}
          </div>
        </Panel>

        {/* RIGHT: Complaint Queue + Demo Trigger */}
        <div className="w-[320px] shrink-0 flex flex-col gap-2 min-h-0">

          {/* Complaint Queue Panel */}
          <Panel className="flex-1 flex flex-col min-h-0">
            <PanelHeader
              icon={<ClipboardCheck className="w-3 h-3 text-grid-dim" />}
              label="COMPLAINT QUEUE"
            />
            <div className="flex-1 overflow-y-auto min-h-0">
              <ErrorBoundary panelName="Complaint Triage">
                <ComplaintTriagePanel />
              </ErrorBoundary>
            </div>
          </Panel>

          {/* Demo Trigger Panel */}
          <Panel className="shrink-0">
            <PanelHeader
              icon={<Zap className="w-3 h-3 text-grid-dim" />}
              label="PIPELINE DEMO"
            />
            <div className="px-3 py-3">
              <p className="text-[10px] font-mono text-grid-dim mb-2">
                Inject telemetry through the real ONNX anomaly pipeline
              </p>
              <DemoTriggerButton />
            </div>
          </Panel>
        </div>
      </div>
    </div>
  );
}
