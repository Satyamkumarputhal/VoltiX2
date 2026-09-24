import React from 'react';

export type SkeletonVariant = 'text' | 'card' | 'stat' | 'table' | 'alert' | 'circular' | 'rectangular';

export interface SkeletonProps {
  variant?: SkeletonVariant;
  width?: string | number;
  height?: string | number;
  className?: string;
  count?: number;
}

const VARIANT_DEFAULTS: Record<string, { width: string; height: string }> = {
  text: { width: '100%', height: '1rem' },
  card: { width: '100%', height: '12rem' },
  stat: { width: '6rem', height: '3rem' },
  table: { width: '100%', height: '2.5rem' },
  alert: { width: '100%', height: '2.5rem' },
  circular: { width: '3rem', height: '3rem' },
  rectangular: { width: '8rem', height: '1rem' },
};

export const Skeleton = React.memo(function Skeleton({
  variant = 'text',
  width,
  height,
  className = '',
  count = 1,
}: SkeletonProps) {
  const defaults = VARIANT_DEFAULTS[variant] || VARIANT_DEFAULTS.text;
  const w = width || defaults.width;
  const h = height || defaults.height;

  const items = Array.from({ length: Math.max(1, count) }, (_, i) => (
    <div
      key={i}
      className={`
        animate-pulse bg-grid-border/50 rounded
        ${variant === 'circular' ? 'rounded-full' : 'rounded-[6px]'}
        ${className}
      `}
      style={{ width: w, height: h }}
      aria-hidden="true"
    />
  ));

  return <div className="flex flex-col gap-2">{items}</div>;
});

Skeleton.displayName = 'Skeleton';