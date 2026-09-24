import React from 'react';
import { SEVERITY_CONFIG, type AlertSeverity } from './status';

export interface StatCardProps {
  label: string;
  value: string | number;
  icon?: React.ReactNode;
  color?: string;
  trend?: {
    value: string;
    up?: boolean;
  };
  severity?: AlertSeverity;
  loading?: boolean;
}

export const StatCard = React.memo(function StatCard({
  label,
  value,
  icon,
  color = 'text-grid-text',
  trend,
  severity,
  loading = false,
}: StatCardProps) {
  // If severity is provided, use its color configuration
  let resolvedColor = color;
  if (severity) {
    const cfg = SEVERITY_CONFIG[severity];
    resolvedColor = cfg.text.replace('text-', '');
  }

  return (
    <div className="card px-5 py-4 flex flex-col gap-1.5">
      <div className="flex items-center gap-1.5 text-xs text-grid-muted">
        {icon && <span className={resolvedColor}>{icon}</span>}
        <span className="stat-label">{label}</span>
      </div>
      <div className={`stat-value ${resolvedColor} leading-none`}>
        {loading ? (
          <span className="animate-pulse bg-grid-border/50 h-8 w-24 rounded" aria-hidden="true" />
        ) : (
          String(value)
        )}
      </div>
      {trend && (
        <div className={`flex items-center gap-1 text-xs font-mono ${trend.up ? 'text-accent-green' : 'text-accent-red'}`}>
          {trend.up ? '▲' : '▼'} {trend.value}
        </div>
      )}
    </div>
  );
});

StatCard.displayName = 'StatCard';