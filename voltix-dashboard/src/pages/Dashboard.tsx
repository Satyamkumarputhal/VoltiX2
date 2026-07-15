import React from 'react';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAlertStore } from '../store/alertStore';
import { AlertsFeed } from '../components/AlertsFeed';
import { GridMetricsEngine } from '../components/GridMetricsEngine';
import { ComplaintTriagePanel } from '../components/ComplaintTriagePanel';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { Zap, LayoutDashboard } from 'lucide-react';

export default function Dashboard() {
  const { status, reconnectAttempts } = useWebSocket();
  const unreadCount = useAlertStore((s) => s.unreadCount);

  return (
    <div className="min-h-screen bg-grid-base flex flex-col">

      {/* ── Top Navigation Bar ── */}
      <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-2">
          <Zap className="w-5 h-5 text-accent-amber" />
          <span className="font-semibold text-sm text-white tracking-wide">VoltiX</span>
          <span className="text-grid-muted text-xs">/ Grid Command Center</span>
        </div>

        <div className="flex items-center gap-4">
          {/* Unread alert badge */}
          {unreadCount > 0 && (
            <span className="flex items-center gap-1 text-xs font-mono text-accent-red">
              <span className="w-1.5 h-1.5 rounded-full bg-accent-red animate-pulse" />
              {unreadCount} alert{unreadCount !== 1 ? 's' : ''}
            </span>
          )}
          <ConnectionStatus status={status} reconnectAttempts={reconnectAttempts} />
        </div>
      </header>

      {/* ── Main Dashboard Grid ── */}
      <main className="flex-1 grid grid-cols-12 gap-4 p-4 overflow-hidden">

        {/* Left column — Metrics + Alerts (8/12) */}
        <div className="col-span-12 lg:col-span-8 flex flex-col gap-4 min-h-0">

          {/* Section label */}
          <div className="flex items-center gap-2">
            <LayoutDashboard className="w-3.5 h-3.5 text-grid-muted" />
            <h1 className="text-xs font-semibold text-grid-muted uppercase tracking-wider">
              Anomaly & Security Center
            </h1>
          </div>

          {/* Grid metrics charts */}
          <ErrorBoundary panelName="Grid Metrics Engine">
            <GridMetricsEngine />
          </ErrorBoundary>

          {/* Live alerts feed */}
          <div className="flex-1 min-h-0">
            <ErrorBoundary panelName="Live Alerts Feed">
              <AlertsFeed />
            </ErrorBoundary>
          </div>
        </div>

        {/* Right column — Triage (4/12) */}
        <div className="col-span-12 lg:col-span-4 min-h-0">
          <ErrorBoundary panelName="Complaint Triage">
            <ComplaintTriagePanel />
          </ErrorBoundary>
        </div>
      </main>
    </div>
  );
}
