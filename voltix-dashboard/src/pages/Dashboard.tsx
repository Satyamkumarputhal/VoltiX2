import { useNavigate, Link } from 'react-router-dom';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAlertStore } from '../store/alertStore';
import { useAuth } from '../auth/AuthContext';
import { AlertsFeed } from '../components/AlertsFeed';
import { GridMetricsEngine } from '../components/GridMetricsEngine';
import { ComplaintTriagePanel } from '../components/ComplaintTriagePanel';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { Zap, LayoutDashboard, LogOut, UserCircle, FileWarning } from 'lucide-react';

export default function Dashboard() {
  const { status, reconnectAttempts } = useWebSocket();
  const unreadCount = useAlertStore((s) => s.unreadCount);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-grid-base flex flex-col">

      {/* ── Top Navigation Bar ── */}
      <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-2">
          <Zap className="w-5 h-5 text-accent-amber" />
          <span className="font-semibold text-sm text-white tracking-wide">VoltiX</span>
          <span className="text-grid-muted text-xs">/ Grid Command Center</span>
          <nav className="ml-4 flex items-center gap-3">
            <Link to="/" className="text-xs text-white font-medium flex items-center gap-1">
              <LayoutDashboard className="w-3.5 h-3.5" />
              Dashboard
            </Link>
            <Link to="/report" className="text-xs text-grid-muted hover:text-white transition-colors flex items-center gap-1">
              <FileWarning className="w-3.5 h-3.5" />
              Report Incident
            </Link>
          </nav>
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

          {/* Logged-in identity — read from real JWT claims */}
          {user && (
            <span className="flex items-center gap-1.5 text-xs text-grid-muted" title={`Tenant ${user.tenantId ?? '—'}`}>
              <UserCircle className="w-4 h-4 text-accent-blue" />
              <span className="font-mono text-white">{user.username}</span>
              {user.roles.length > 0 && (
                <span className="px-1.5 py-0.5 rounded bg-accent-blue/15 text-accent-blue text-[10px] font-mono uppercase tracking-wide">
                  {user.roles.join(', ')}
                </span>
              )}
            </span>
          )}

          {/* Logout */}
          <button
            onClick={handleLogout}
            aria-label="Log out"
            className="flex items-center gap-1 text-xs text-grid-muted hover:text-accent-red transition-colors"
          >
            <LogOut className="w-3.5 h-3.5" />
            Logout
          </button>
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
