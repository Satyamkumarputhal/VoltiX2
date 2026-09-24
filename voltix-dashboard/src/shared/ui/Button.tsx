import React from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  fullWidth?: boolean;
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-accent-amber/15 text-accent-amber border-accent-amber/30 hover:bg-accent-amber/25 active:bg-accent-amber/35',
  secondary: 'bg-grid-raised text-grid-text border-grid-border hover:bg-grid-elevated active:bg-grid-border',
  danger: 'bg-red-600/15 text-accent-red border-red-600/30 hover:bg-red-600/25 active:bg-red-600/35',
  ghost: 'bg-transparent text-grid-muted hover:bg-white/5 active:bg-white/10 border-transparent',
  outline: 'bg-transparent text-grid-text border-grid-border hover:bg-white/5 active:bg-white/10',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs gap-1.5',
  md: 'px-4 py-2 text-sm gap-2',
  lg: 'px-6 py-3 text-base gap-2.5',
};

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'primary',
      size = 'md',
      loading = false,
      leftIcon,
      rightIcon,
      fullWidth = false,
      disabled,
      className = '',
      children,
      ...props
    },
    ref
  ) => {
    const isDisabled = disabled || loading;

    return (
      <button
        ref={ref}
        disabled={isDisabled}
        className={`
          inline-flex items-center justify-center font-medium rounded-[6px]
          transition-all duration-150 ease-out
          focus:outline-none focus:ring-2 focus:ring-accent-amber/40 focus:ring-offset-2 focus:ring-offset-grid-base
          disabled:opacity-50 disabled:cursor-not-allowed
          ${SIZE_CLASSES[size]}
          ${VARIANT_CLASSES[variant]}
          ${fullWidth ? 'w-full' : ''}
          ${className}
        `}
        {...props}
      >
        {loading ? (
          <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        ) : (
          leftIcon
        )}
        {children}
        {!loading && rightIcon}
      </button>
    );
  }
);

Button.displayName = 'Button';