import React from 'react';

export type InputType = 'text' | 'number' | 'email' | 'password' | 'textarea' | 'select';

export interface InputProps {
  label?: string;
  error?: string;
  helper?: string;
  required?: boolean;
  disabled?: boolean;
  type?: InputType;
  options?: { value: string; label: string }[];
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  className?: string;
  id?: string;
  value?: string | number;
  onChange?: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => void;
  onBlur?: (e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => void;
  placeholder?: string;
}

const BASE_CLASSES = `
  w-full bg-grid-raised/50 border rounded-[10px]
  px-4 py-3 text-[15px] text-grid-text placeholder:text-grid-dim
  focus:outline-none focus:ring-2 focus:border-accent-amber/50
  focus:ring-accent-amber/20 transition-all
  disabled:opacity-50 disabled:cursor-not-allowed
  transition-all duration-150
`;

export const Input = React.forwardRef<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement, InputProps>(
  (
    {
      label,
      error,
      helper,
      required,
      disabled,
      type = 'text',
      options,
      leftIcon,
      rightIcon,
      className = '',
      id,
      value,
      onChange,
      onBlur,
      placeholder,
      ...props
    },
    ref
  ) => {
    if (type === 'textarea') {
      return (
        <div className="w-full">
          {label && (
            <label htmlFor={id || `input-${Math.random().toString(36).slice(2, 9)}`} className="block text-[13px] text-grid-muted mb-2 font-medium">
              {label} {required && <span className="text-accent-red ml-1">*</span>}
            </label>
          )}
          <textarea
            ref={ref as React.Ref<HTMLTextAreaElement>}
            id={id || `input-${Math.random().toString(36).slice(2, 9)}`}
            disabled={disabled}
            required={required}
            className={`${BASE_CLASSES} ${error ? 'border-accent-red focus:ring-accent-red/20 focus:border-accent-red/50' : 'border-grid-border focus:ring-accent-amber/20 focus:border-accent-amber/50'} resize-none`}
            {...props}
          />
          {error && <p className="text-[12px] text-accent-red mt-1.5 ml-1" role="alert">{error}</p>}
          {helper && !error && <p className="text-[12px] text-grid-dim mt-1.5 ml-1">{helper}</p>}
        </div>
      );
    }

    if (type === 'select') {
      return (
        <div className="w-full">
          {label && (
            <label htmlFor={id || `input-${Math.random().toString(36).slice(2, 9)}`} className="block text-[13px] text-grid-muted mb-2 font-medium">
              {label} {required && <span className="text-accent-red ml-1">*</span>}
            </label>
          )}
          <select
            ref={ref as React.Ref<HTMLSelectElement>}
            id={id || `input-${Math.random().toString(36).slice(2, 9)}`}
            disabled={disabled}
            required={required}
            className={`${BASE_CLASSES} ${error ? 'border-accent-red focus:ring-accent-red/20 focus:border-accent-red/50' : 'border-grid-border focus:ring-accent-amber/20 focus:border-accent-amber/50'} appearance-none`}
            {...props}
          >
            {options?.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          {error && <p className="text-[12px] text-accent-red mt-1.5 ml-1" role="alert">{error}</p>}
          {helper && !error && <p className="text-[12px] text-grid-dim mt-1.5 ml-1">{helper}</p>}
        </div>
      );
    }

    return (
      <div className="w-full">
        {label && (
          <label htmlFor={id || `input-${Math.random().toString(36).slice(2, 9)}`} className="block text-[13px] text-grid-muted mb-2 font-medium">
            {label} {required && <span className="text-accent-red ml-1">*</span>}
          </label>
        )}
        <div className="relative">
          {leftIcon && (
            <div className="absolute inset-y-0 left-0 flex items-center pl-3 text-grid-dim pointer-events-none">
              {leftIcon}
            </div>
          )}
          <input
            ref={ref as React.Ref<HTMLInputElement>}
            id={id || `input-${Math.random().toString(36).slice(2, 9)}`}
            type={type}
            disabled={disabled}
            required={required}
            className={`${BASE_CLASSES} ${error ? 'border-accent-red focus:ring-accent-red/20 focus:border-accent-red/50' : 'border-grid-border focus:ring-accent-amber/20 focus:border-accent-amber/50'} ${leftIcon ? 'pl-10' : ''} ${rightIcon ? 'pr-10' : ''}`}
            {...props}
          />
          {rightIcon && (
            <div className="absolute inset-y-0 right-0 flex items-center pr-3 text-grid-dim pointer-events-none">
              {rightIcon}
            </div>
          )}
        </div>
        {error && <p className="text-[12px] text-accent-red mt-1.5 ml-1" role="alert">{error}</p>}
        {helper && !error && <p className="text-[12px] text-grid-dim mt-1.5 ml-1">{helper}</p>}
      </div>
    );
  }
);

Input.displayName = 'Input';