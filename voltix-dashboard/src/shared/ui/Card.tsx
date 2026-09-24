import React from 'react';

export type CardVariant = 'default' | 'elevated' | 'outlined';
export type CardPadding = 'none' | 'sm' | 'md' | 'lg';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: CardVariant;
  padding?: CardPadding;
  hoverable?: boolean;
  children: React.ReactNode;
}

const VARIANT_CLASSES: Record<string, string> = {
  default: 'bg-grid-surface border border-grid-border',
  elevated: 'bg-grid-surface border border-grid-border shadow-lg',
  outlined: 'bg-transparent border-2 border-grid-border',
};

const PADDING_CLASSES: Record<CardPadding, string> = {
  none: '',
  sm: 'p-3',
  md: 'p-4',
  lg: 'p-6',
};

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'elevated' | 'outlined';
  padding?: 'none' | 'sm' | 'md' | 'lg';
  hoverable?: boolean;
  children: React.ReactNode;
}

export const Card = React.forwardRef<HTMLDivElement, CardProps>(
  (
    {
      variant = 'default',
      padding = 'md',
      hoverable = false,
      className = '',
      children,
      ...props
    },
    ref
  ) => {
    return (
      <div
        ref={ref}
        className={`
          rounded-[14px] border border-grid-border bg-grid-surface
          shadow-sm
          transition-all duration-150 ease-out
          ${VARIANT_CLASSES[variant]}
          ${PADDING_CLASSES[padding]}
          ${hoverable ? 'hover:bg-grid-raised hover:shadow-md transition-all duration-150 cursor-pointer' : ''}
          ${className}
        `}
        {...props}
      >
        {children}
      </div>
    );
  }
);

Card.displayName = 'Card';