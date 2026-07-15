import React, { useMemo, useCallback } from 'react';
import { ShieldAlert, Radio, Zap } from 'lucide-react';
import { useAlertStore } from '../store/alertStore';
import type { SystemAlert, AlertSeverity } from '../types';

// ── Severity visual config ───────────────────────────────────
const SEVERITY_CONFIG: Record<AlertSeverity, { bg: string; text: string; badge: string; pulse: boolean }> = {
  LOW:      { bg: 'hover:bg-white/5',                    text: 'text-grid-muted',    badge: 'bg-grid-border text-grid-muted',         pulse: false },
  MEDIUM:   { bg: 'hover:bg-amber-500/10',               text: 'text-amber-400',     badge: 'bg-amber-500/20 text-amber-400',          pulse: false },
  HIGH:     { bg: 'hover:bg-orange-500/10',              text: 'text-orange-400',    badge: 'bg-orange-500/20 text-orange-400',         pulse: false },
  CRITICAL: { bg: 'hover:bg-red-600/10 bg-red-900/5',    text: 'text-red-400',       badge: 'bg-red-600/20 text-red-400',              pulse: true  },
};

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch { return iso; }
}

// ── Alert row — memoised to prevent re-renders during bursts ──
const AlertRow = React.memo(function AlertRow({
  alert,
  onAcknowledge,
}: {
  alert:          SystemAlert;
  onAcknowledge:  (id: number) => void;
}) {
  const cfg = SEVERITY_CONFIG[alert.severity];

  return (
    <div
      className={`
        flex items-start gap-3 px-4 py-2.5 border-b border-grid-border/40
        transition-colors animate-slide-in ${cfg.bg}
        ${cfg.pulse ? 'animate-pulse-critical' : ''}
      `}
    >
      {/* Severity indicator strip */}
      <div className={`w-0.5 self-stretch rounded-full ${
        alert.severity === 'CRITICAL' ? 'bg-accent-red' :
        alert.severity === 'HIGH'     ? 'bg-accent-orange' :
        alert.severity === 'MEDIUM'   ? 'bg-accent-amber' : 'bg-grid-border'
      }`} />

      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <span className={`badge ${cfg.badge}`}>
              {alert.severity}
            </span>
            <span className="text-xs font-mono text-white truncate">
              {alert.meterId}
            </span>
          </div>
          <span className="text-[10px] font-mono text-grid-muted whitespace-nowrap">
            {formatTime(alert.detectedAt)}
          </span>
        </div>

        <div className="mt-0.5 flex items-center gap-3 text-xs text-grid-muted">
          <span className="flex items-center gap-1">
            <Zap className="w-3 h-3" />
            Zone {alert.zoneId}
          </span>
          <span>{alert.alertType.replace('_', ' ')}</span>
          <span className="font-mono">
            score: <span className={cfg.text}>{Number(alert.priorityScore).toFixed(2)}</span>
          </span>
        </div>
      </div>

      {alert.status === 'OPEN' && (
        <button
          onClick={() => onAcknowledge(alert.alertId)}
          className="shrink-0 text-[10px] px-2 py-1 rounded border border-grid-border
                     text-grid-muted hover:text-accent-blue hover:border-accent-blue transition-colors font-mono"
        >
          ACK
        </button>
      )}

      {alert.status === 'ACKNOWLEDGED' && (
        <span className="shrink-0 text-[10px] font-mono text-accent-blue">ACK'd</span>
      )}
    </div>
  );
});

// ── AlertsFeed ───────────────────────────────────────────────
export const AlertsFeed = React.memo(function AlertsFeed() {
  const alerts       = useAlertStore((s) => s.alerts);
  const markAcknowledged = useAlertStore((s) => s.markAcknowledged);
  const clearAlerts  = useAlertStore((s) => s.clearAlerts);
  const unreadCount  = useAlertStore((s) => s.unreadCount);

  const handleAck = useCallback((id: number) => markAcknowledged(id), [markAcknowledged]);

  // Memoised to prevent re-sorting the full list unless alerts change
  const sortedAlerts = useMemo(
    () => [...alerts].sort((a, b) => {
      const order: Record<AlertSeverity, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
      return order[a.severity] - order[b.severity];
    }),
    [alerts],
  );

  return (
    <div className="flex flex-col h-full rounded-lg border border-grid-border bg-grid-surface overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-grid-border">
        <div className="flex items-center gap-2">
          <ShieldAlert className="w-4 h-4 text-accent-red" />
          <h2 className="text-sm font-semibold text-white">Live Alerts</h2>
          {unreadCount > 0 && (
            <span className="px-1.5 py-0.5 text-[10px] rounded-full bg-accent-red/20 text-accent-red font-mono">
              {unreadCount}
            </span>
          )}
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-grid-muted">{alerts.length} active</span>
          {alerts.length > 0 && (
            <button
              onClick={clearAlerts}
              className="text-[10px] text-grid-muted hover:text-white transition-colors"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* Feed */}
      <div className="flex-1 overflow-y-auto">
        {sortedAlerts.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-2 text-grid-muted">
            <Radio className="w-6 h-6 opacity-40" />
            <p className="text-xs">Awaiting live alerts…</p>
          </div>
        ) : (
          sortedAlerts.map((alert) => (
            <AlertRow key={alert.alertId} alert={alert} onAcknowledge={handleAck} />
          ))
        )}
      </div>
    </div>
  );
});
