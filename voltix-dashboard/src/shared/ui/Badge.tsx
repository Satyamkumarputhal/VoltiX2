import React from 'react';
import { SEVERITY_CONFIG, getSemanticStatusConfig, type AlertSeverity, type StatusVariant } from './status';

export type BadgeVariant = AlertSeverity | StatusVariant;
export type BadgeSize = 'sm' | 'md';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  size?: BadgeSize;
  children: React.ReactNode;
  dot?: boolean;
}

const SIZE_CLASSES: Record<string, string> = {
  sm: 'px-2 py-0.5 text-[10px]',
  md: 'px-2.5 py-1 text-[11px]',
};

export const Badge = React.forwardRef<HTMLSpanElement, BadgeProps>(
  (
    {
      variant = 'LOW',
      size = 'md',
      children,
      dot = false,
      className = '',
      ...props
    },
    ref
  ) => {
    // Determine which config to use
    const isAlertSeverity = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(variant as string);
    const isSemantic = ['info', 'success', 'warning', 'danger'].includes(variant as string);

    let config: {
      badge: string;
      text: string;
      bg: string;
    };

    if (isAlertSeverity) {
      const cfg = { ...SEVERITY_CONFIG[variant as AlertSeverity] };
      config = { badge: cfg.badge, text: cfg.text, bg: cfg.bg };
    } else if (isSemantic) {
      config = getSemanticStatusConfig(variant as Exclude<StatusVariant, 'low' | 'medium' | 'high' | 'critical'>);
    } else {
      // Default to LOW
      config = SEVERITY_CONFIG.LOW;
    }

    const badgeClass = `${config.badge} ${config.text}`;

    return (
      <span
        ref={ref}
        className={`
          inline-flex items-center gap-1.5
          font-mono font-semibold uppercase tracking-wide
          rounded-[4px]
          ${SIZE_CLASSES[size] || SIZE_CLASSES.md}
          ${badgeClass}
          ${className}
        `}
        {...props}
      >
        {dot && (
          <span
            className={`w-1.5 h-1.5 rounded-full shrink-0 ${
              variant === 'CRITICAL' || variant === 'danger' ? 'bg-accent-red' :
              variant === 'HIGH' || variant === 'warning' ? 'bg-accent-orange' :
              variant === 'MEDIUM' || variant === 'medium' ? 'bg-accent-amber' :
              variant === 'LOW' || variant === 'low' || variant === 'info' ? 'bg-grid-border' :
              variant === 'success' ? 'bg-accent-green' :
              'bg-grid-border'
            }`}
          />
        )}
        {children}
      </span>
    );
  }
);

Badge.displayName = 'Badge';