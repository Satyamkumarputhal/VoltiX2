/**
 * Single source of truth for all status/severity visual configurations.
 * Used by Badge, AlertRow, ConnectionStatus, and any other component
 * that needs to display status/severity visually.
 */

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ConnectionStatus = 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED' | 'ERROR';
export type StatusVariant = 'info' | 'success' | 'warning' | 'danger' | 'low' | 'medium' | 'high' | 'critical';

export interface SeverityVisualConfig {
  /** Tailwind background class for hover/background states */
  bg: string;
  /** Tailwind text color class */
  text: string;
  /** Tailwind badge background + text classes */
  badge: string;
  /** Whether to apply pulse animation (for critical) */
  pulse: boolean;
  /** Left border color class (for alert rows) */
  borderLeft: string;
  /** Tailwind text color for priority score */
  scoreText: string;
}

export interface ConnectionStatusConfig {
  label: string;
  color: string;
  icon: React.ReactNode;
  dotColor: string;
}

/**
 * Alert severity visual configuration — single source of truth
 * Used by: AlertRow (Dashboard), AlertsFeed, Badge component
 */
export const SEVERITY_CONFIG: Record<AlertSeverity, SeverityVisualConfig> = {
  LOW: {
    bg: 'hover:bg-white/5',
    text: 'text-grid-muted',
    badge: 'bg-grid-border text-grid-muted',
    pulse: false,
    borderLeft: 'border-l-grid-dim',
    scoreText: 'text-grid-muted',
  },
  MEDIUM: {
    bg: 'hover:bg-amber-500/10',
    text: 'text-amber-400',
    badge: 'bg-amber-500/20 text-amber-400',
    pulse: false,
    borderLeft: 'border-l-severity-medium',
    scoreText: 'text-severity-medium',
  },
  HIGH: {
    bg: 'hover:bg-orange-500/10',
    text: 'text-orange-400',
    badge: 'bg-orange-500/20 text-orange-400',
    pulse: false,
    borderLeft: 'border-l-severity-high',
    scoreText: 'text-severity-high',
  },
  CRITICAL: {
    bg: 'hover:bg-red-600/10 bg-red-900/5',
    text: 'text-red-400',
    badge: 'bg-red-600/20 text-red-400',
    pulse: true,
    borderLeft: 'border-l-severity-critical',
    scoreText: 'text-severity-critical',
  },
};

/**
 * Connection status visual configuration — single source of truth
 * Used by: ConnectionStatus component
 */
export const CONNECTION_STATUS_CONFIG: Record<ConnectionStatus, ConnectionStatusConfig> = {
  CONNECTED: {
    label: 'Live',
    color: 'text-accent-green',
    icon: null, // Will be rendered inline
    dotColor: 'bg-accent-green',
  },
  CONNECTING: {
    label: 'Connecting…',
    color: 'text-accent-amber',
    icon: null,
    dotColor: 'bg-accent-amber',
  },
  DISCONNECTED: {
    label: 'Reconnecting',
    color: 'text-accent-orange',
    icon: null,
    dotColor: 'bg-accent-red',
  },
  ERROR: {
    label: 'Error',
    color: 'text-accent-red',
    icon: null,
    dotColor: 'bg-accent-red',
  },
};

/**
 * Semantic status variants (for Badge, Toast, etc.)
 * Maps semantic variants to severity-like visual config
 */
export const SEMANTIC_STATUS_CONFIG: Record<Exclude<StatusVariant, 'low' | 'medium' | 'high' | 'critical'>, SeverityVisualConfig> = {
  info: {
    bg: 'hover:bg-blue-500/10',
    text: 'text-blue-400',
    badge: 'bg-blue-500/20 text-blue-400',
    pulse: false,
    borderLeft: 'border-l-blue-500',
    scoreText: 'text-blue-400',
  },
  success: {
    bg: 'hover:bg-green-500/10',
    text: 'text-green-400',
    badge: 'bg-green-500/20 text-green-400',
    pulse: false,
    borderLeft: 'border-l-green-500',
    scoreText: 'text-green-400',
  },
  warning: {
    bg: 'hover:bg-amber-500/10',
    text: 'text-amber-400',
    badge: 'bg-amber-500/20 text-amber-400',
    pulse: false,
    borderLeft: 'border-l-amber-500',
    scoreText: 'text-amber-400',
  },
  danger: {
    bg: 'hover:bg-red-500/10',
    text: 'text-red-400',
    badge: 'bg-red-500/20 text-red-400',
    pulse: false,
    borderLeft: 'border-l-red-500',
    scoreText: 'text-red-400',
  },
};

/**
 * Helper to get severity config by AlertSeverity
 */
export function getSeverityConfig(severity: AlertSeverity): SeverityVisualConfig {
  return SEVERITY_CONFIG[severity];
}

/**
 * Helper to get connection status config
 */
export function getConnectionStatusConfig(status: ConnectionStatus): ConnectionStatusConfig {
  return CONNECTION_STATUS_CONFIG[status];
}

/**
 * Helper to get semantic status config
 */
export function getSemanticStatusConfig(variant: Exclude<StatusVariant, 'low' | 'medium' | 'high' | 'critical'>): SeverityVisualConfig {
  return SEMANTIC_STATUS_CONFIG[variant];
}

/**
 * Get all severity values for iteration
 */
export const ALL_SEVERITIES: AlertSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const ALL_CONNECTION_STATUSES: ConnectionStatus[] = ['CONNECTED', 'CONNECTING', 'DISCONNECTED', 'ERROR'];