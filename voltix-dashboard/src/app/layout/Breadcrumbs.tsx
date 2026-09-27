import { ChevronRight } from 'lucide-react';
import { useLocation } from 'react-router-dom';

interface BreadcrumbItem {
  label: string;
  href?: string;
}

const ROUTE_LABELS: Record<string, string> = {
  dashboard: 'Dashboard',
  alerts: 'Alerts',
  complaints: 'Complaints',
  forecasts: 'Forecasts',
  'live-grid': 'Live Grid',
  meters: 'Meters',
  incidents: 'Incidents',
  'field-jobs': 'Field Jobs',
  inspectors: 'Inspectors',
  analytics: 'Analytics',
  notifications: 'Notifications',
  'system-health': 'System Health',
  'audit-logs': 'Audit Logs',
  settings: 'Settings',
};

function formatSegment(segment: string): string {
  return ROUTE_LABELS[segment] || segment
    .split('-')
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join(' ');
}

export function Breadcrumbs() {
  const location = useLocation();
  const pathname = location.pathname;

  if (pathname === '/') {
    return null;
  }

  const segments = pathname.split('/').filter(Boolean);

  const items: BreadcrumbItem[] = [
    { label: 'Dashboard', href: '/dashboard' },
  ];

  segments.forEach((segment, index) => {
    if (segment === 'dashboard') return;
    const href = '/' + segments.slice(0, index + 1).join('/');
    const label = formatSegment(segment);
    items.push({ label, href });
  });

  return (
    <nav
      className="flex items-center gap-1.5 px-4 py-2 text-xs text-grid-muted bg-grid-surface border-b border-grid-border"
      aria-label="Breadcrumb"
    >
      <ol className="flex items-center gap-1.5 flex-wrap" role="list">
        {items.map((item, index) => (
          <li key={item.href || item.label} className="flex items-center gap-1.5">
            {index > 0 && (
              <ChevronRight className="w-3 h-3 text-grid-dim flex-shrink-0" aria-hidden="true" />
            )}
            {item.href && index < items.length - 1 ? (
              <a
                href={item.href}
                className="text-grid-muted hover:text-grid-text transition-colors font-medium truncate max-w-[160px]"
              >
                {item.label}
              </a>
            ) : (
              <span className="text-grid-text font-medium truncate max-w-[160px]" aria-current="page">
                {item.label}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}