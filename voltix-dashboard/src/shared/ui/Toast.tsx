import React, { useState, useCallback, createContext, useContext, type ReactNode } from 'react';
import { X, CheckCircle, AlertCircle, AlertTriangle, Info } from 'lucide-react';

export type ToastType = 'info' | 'success' | 'warning' | 'error';

export interface Toast {
  id: string;
  type: ToastType;
  message: string;
  title?: string;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}

interface ToastContextValue {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, 'id'>) => string;
  removeToast: (id: string) => void;
  clearToasts: () => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const TOAST_ICONS: Record<ToastType, React.ReactNode> = {
  info: <Info className="w-5 h-5 shrink-0" />,
  success: <CheckCircle className="w-5 h-5 shrink-0" />,
  warning: <AlertTriangle className="w-5 h-5 shrink-0" />,
  error: <AlertCircle className="w-5 h-5 shrink-0" />,
};

const TYPE_CONFIG = {
  info: { bg: 'bg-blue-500/10', border: 'border-blue-500/30', iconColor: 'text-blue-400', textColor: 'text-blue-300' },
  success: { bg: 'bg-green-500/10', border: 'border-green-500/30', iconColor: 'text-green-400', textColor: 'text-green-300' },
  warning: { bg: 'bg-amber-500/10', border: 'border-amber-500/30', iconColor: 'text-amber-400', textColor: 'text-amber-300' },
  error: { bg: 'bg-red-500/10', border: 'border-red-500/30', iconColor: 'text-red-400', textColor: 'text-red-300' },
};

interface ToastItemProps {
  toast: Toast;
  onClose: (id: string) => void;
}

function ToastItem({ toast, onClose }: ToastItemProps) {
  const config = TYPE_CONFIG[toast.type];

  return (
    <div
      className={`
        flex items-start gap-3 p-4 rounded-[10px] border animate-slide-in
        ${config.bg} ${config.border}
        shadow-lg
      `}
      role="alert"
    >
      <span className={`${config.iconColor} shrink-0 mt-0.5`}>
        {TOAST_ICONS[toast.type]}
      </span>
      <div className="flex-1 min-w-0">
        {toast.title && (
          <p className="font-semibold text-white mb-1">{toast.title}</p>
        )}
        <p className={`${config.textColor} text-sm leading-relaxed`}>
          {toast.message}
        </p>
        {toast.action && (
          <button
            onClick={() => {
              toast.action?.onClick();
              onClose(toast.id);
            }}
            className="mt-2 text-xs font-medium text-accent-amber hover:underline"
          >
            {toast.action.label}
          </button>
        )}
      </div>
      <button
        onClick={() => onClose(toast.id)}
        className="shrink-0 text-grid-muted hover:text-white transition-colors p-1"
        aria-label="Dismiss"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}

export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((toast: Omit<Toast, 'id'>) => {
    const id = Math.random().toString(36).slice(2, 9);
    const newToast = { ...toast, id };
    setToasts((prev) => [...prev, newToast]);

    // Auto-remove after duration
    const duration = toast.duration ?? 5000;
    if (duration > 0) {
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, duration);
    }
    return id;
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const clearToasts = useCallback(() => {
    setToasts([]);
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, addToast, removeToast, clearToasts }}>
      {children}
      <div
        className="fixed bottom-5 right-5 z-[400] flex flex-col gap-2 w-[360px] max-w-[calc(100vw-2rem)] pointer-events-none"
        role="region"
        aria-label="Notifications"
      >
        {toasts.map((toast) => (
          <div key={toast.id} className="pointer-events-auto">
            <ToastItem toast={toast} onClose={removeToast} />
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}