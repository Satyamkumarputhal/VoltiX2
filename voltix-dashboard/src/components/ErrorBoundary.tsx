import React, { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children:    ReactNode;
  panelName?:  string;
}

interface State {
  hasError: boolean;
  error:    Error | null;
}

/**
 * High-level error boundary protecting each dashboard slot.
 * When a child throws during rendering, it catches the error and
 * renders a minimal "Panel Unavailable" placeholder — preventing
 * adjacent slots from crashing.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error(`[VoltiX ErrorBoundary] Panel "${this.props.panelName}" crashed:`, error, info);
  }

  handleReset = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (!this.state.hasError) return this.props.children;

    return (
      <div className="flex flex-col items-center justify-center h-full min-h-[200px] gap-3 rounded-lg border border-accent-orange/30 bg-grid-surface p-6 text-center">
        <AlertTriangle className="w-8 h-8 text-accent-orange" />
        <div>
          <p className="text-sm font-semibold text-accent-orange">
            {this.props.panelName ?? 'Panel'} Unavailable
          </p>
          <p className="text-xs text-grid-muted mt-1">
            {this.state.error?.message ?? 'An unexpected rendering error occurred.'}
          </p>
        </div>
        <button
          onClick={this.handleReset}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded border border-grid-border
                     text-grid-muted hover:text-white hover:border-accent-blue transition-colors"
        >
          <RefreshCw className="w-3 h-3" />
          Retry
        </button>
      </div>
    );
  }
}
