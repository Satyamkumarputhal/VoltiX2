import React from 'react';
import { Wifi, WifiOff, Loader2 } from 'lucide-react';

type WsStatus = 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR';

interface ConnectionStatusProps {
  status:            WsStatus;
  reconnectAttempts: number;
}

const STATUS_CONFIG: Record<WsStatus, { label: string; color: string; icon: React.ReactNode }> = {
  CONNECTED:    { label: 'Live',         color: 'text-accent-green',  icon: <Wifi className="w-3.5 h-3.5" /> },
  CONNECTING:   { label: 'Connecting…',  color: 'text-accent-amber ws-connecting',  icon: <Loader2 className="w-3.5 h-3.5 animate-spin" /> },
  DISCONNECTED: { label: 'Reconnecting', color: 'text-accent-orange', icon: <WifiOff className="w-3.5 h-3.5" /> },
  ERROR:        { label: 'Error',        color: 'text-accent-red',    icon: <WifiOff className="w-3.5 h-3.5" /> },
};

export const ConnectionStatus = React.memo(function ConnectionStatus({
  status,
  reconnectAttempts,
}: ConnectionStatusProps) {
  const cfg = STATUS_CONFIG[status];

  return (
    <div className={`flex items-center gap-1.5 text-xs font-mono ${cfg.color}`}>
      {/* Status dot */}
      <span className={`w-1.5 h-1.5 rounded-full ${
        status === 'CONNECTED' ? 'bg-accent-green' :
        status === 'CONNECTING' ? 'bg-accent-amber ws-connecting' :
        'bg-accent-red'
      }`} />
      {cfg.icon}
      <span>{cfg.label}</span>
      {reconnectAttempts > 0 && status !== 'CONNECTED' && (
        <span className="text-grid-muted">(#{reconnectAttempts})</span>
      )}
    </div>
  );
});
