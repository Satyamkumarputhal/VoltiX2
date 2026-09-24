import { useEffect, useState, useMemo } from 'react';
import { useAlertStore } from '../store/alertStore';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { Badge } from '../shared/ui';
import { getSeverityConfig } from '../shared/ui/status';
import type { SystemAlert } from '../types';

type StatusFilter = 'ALL' | 'OPEN' | 'ACKNOWLEDGED';

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch { return '—'; }
}

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

function Alerts() {
  const { alerts, loading, error, fetchAlerts, markAcknowledged } = useAlertStore();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');

  useEffect(() => {
    fetchAlerts();
  }, [fetchAlerts]);

  const filteredAlerts = useMemo(() => {
    if (statusFilter === 'ALL') return alerts;
    return alerts.filter(a => a.status === statusFilter);
  }, [alerts, statusFilter]);

  return (
    <div className="h-screen bg-grid-base flex flex-col overflow-hidden">
      <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-5 shrink-0">
        <div className="flex items-center gap-3">
          <svg className="w-4 h-4 text-accent-red" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
          <span className="font-semibold text-[15px] text-grid-text">Alert History</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-[12px] text-grid-muted">Filter:</span>
          <div className="flex items-center gap-1 bg-grid-raised rounded p-1">
            {(['ALL', 'OPEN', 'ACKNOWLEDGED'] as StatusFilter[]).map((f) => (
              <button
                key={f}
                onClick={() => setStatusFilter(f)}
                className={`px-2 py-1 text-[11px] font-mono rounded transition-colors ${
                  statusFilter === f
                    ? 'bg-accent-amber text-grid-base'
                    : 'text-grid-muted hover:text-grid-text'
                }`}
              >
                {f}
              </button>
            ))}
          </div>
          <span className="text-[12px] text-grid-muted">WS:</span>
          <ConnectionStatus status="CONNECTED" reconnectAttempts={0} />
        </div>
      </header>

      <div className="flex-1 p-4 overflow-auto">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <div className="card p-5">
            <div className="flex items-center gap-2 text-sm text-grid-muted mb-1">
              <svg className="w-4 h-4 text-accent-red" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
              <span className="text-grid-muted">TOTAL</span>
            </div>
            <div className="text-2xl font-mono font-bold text-grid-text">{filteredAlerts.length}</div>
          </div>
          <div className="card p-5">
            <div className="flex items-center gap-2 text-sm text-grid-muted mb-1">
              <svg className="w-4 h-4 text-accent-red" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
              <span className="text-grid-muted">CRITICAL</span>
            </div>
            <div className="text-2xl font-mono font-bold text-accent-red">{filteredAlerts.filter((a) => a.severity === 'CRITICAL').length}</div>
          </div>
          <div className="card p-5">
            <div className="flex items-center gap-2 text-sm text-grid-muted mb-1">
              <svg className="w-4 h-4 text-accent-orange" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
              <span className="text-grid-muted">HIGH</span>
            </div>
            <div className="text-2xl font-mono font-bold text-accent-orange">{filteredAlerts.filter((a) => a.severity === 'HIGH').length}</div>
          </div>
          <div className="card p-5">
            <div className="flex items-center gap-2 text-sm text-grid-muted mb-1">
              <svg className="w-4 h-4 text-accent-amber" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
              <span className="text-grid-muted">MEDIUM</span>
            </div>
            <div className="text-2xl font-mono font-bold text-accent-amber">{filteredAlerts.filter((a) => a.severity === 'MEDIUM').length}</div>
          </div>
        </div>

        <div className="card flex flex-col h-full">
          <div className="flex items-center justify-between px-4 py-3 border-b border-grid-border">
            <div className="flex items-center gap-2">
              <svg className="w-4 h-4 text-accent-red" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
              <span className="text-[14px] font-semibold text-grid-text">Alert History</span>
              <Badge variant="low" size="sm" className="text-[10px]">
                {alerts.filter(a => a.status === 'OPEN').length} open / {alerts.filter(a => a.status === 'ACKNOWLEDGED').length} acknowledged
              </Badge>
            </div>
          </div>
          <div className="flex-1 overflow-y-auto">
            {loading && (
              <div className="h-24 flex items-center justify-center text-grid-dim text-[13px]">
                Loading alerts…
              </div>
            )}
            {error && (
              <div className="flex flex-col items-center justify-center h-24 gap-2 text-center text-accent-orange">
                <p className="text-xs">{error}</p>
              </div>
            )}
            {!loading && !error && filteredAlerts.length === 0 && (
              <div className="h-24 flex items-center justify-center text-grid-dim text-[13px]">
                {statusFilter === 'ALL' ? 'No alerts in history' : `No ${statusFilter.toLowerCase()} alerts`}
              </div>
            )}
            {!loading && !error && filteredAlerts.length > 0 && (
              <div className="flex-1 overflow-y-auto">
                <div className="space-y-1">
                  {filteredAlerts.map((alert, i) => (
                    <AlertRow key={alert.alertId} alert={alert} onAck={markAcknowledged} odd={i % 2 === 1} />
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default Alerts;