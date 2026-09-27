import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { Breadcrumbs } from './Breadcrumbs';

function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const isMobile = typeof window !== 'undefined' && window.innerWidth < 1024;

  const handleSidebarToggle = () => {
    setSidebarCollapsed(!sidebarCollapsed);
  };

  const closeSidebar = () => {
    setSidebarOpen(false);
  };

  return (
    <div className="h-screen bg-grid-base flex overflow-hidden">
      {/* Mobile sidebar overlay */}
      {isMobile && sidebarOpen && (
        <div
          className="fixed inset-0 z-[40] bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar */}
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={handleSidebarToggle}
        isOpen={sidebarOpen}
        onClose={closeSidebar}
      />

      {/* Main content area */}
      <div className="flex-1 flex flex-col overflow-hidden min-w-0">
        {/* Header */}
        <Header
          onMenuClick={() => setSidebarOpen(true)}
          title={getPageTitle(window.location.pathname)}
        />

        {/* Breadcrumbs */}
        <Breadcrumbs />

        {/* Main content */}
        <main className="flex-1 overflow-auto" id="main-content" role="main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function getPageTitle(pathname: string): string {
  const segments = pathname.split('/').filter(Boolean);

  if (segments.length === 0 || segments[0] === '') return 'Dashboard';

  const labels: Record<string, string> = {
    dashboard: 'Dashboard',
    'live-grid': 'Live Grid',
    meters: 'Meters',
    alerts: 'Alerts',
    forecasts: 'Forecasts',
    complaints: 'Complaints',
    incidents: 'Incidents',
    'field-jobs': 'Field Jobs',
    inspectors: 'Inspectors',
    analytics: 'Analytics',
    notifications: 'Notifications',
    'system-health': 'System Health',
    'audit-logs': 'Audit Logs',
    settings: 'Settings',
  };

  if (segments[0] === 'dashboard') {
    if (segments.length === 1) return 'Dashboard';
    return labels[segments[1]] || formatSegment(segments[1]);
  }

  if (segments[0] === 'report') return 'Report Incident';
  if (segments[0] === 'login') return 'Sign In';

  return formatSegment(segments[0]);
}

function formatSegment(segment: string): string {
  return segment
    .split('-')
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join(' ');
}

export { AppShell };