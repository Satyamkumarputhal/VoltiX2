import React, { useState, useEffect } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  Zap,
  AlertTriangle,
  TrendingUp,
  ChevronRight,
} from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';

interface NavItem {
  label: string;
  href: string;
  icon: React.ReactNode;
  roles?: string[];
}

interface NavSection {
  title: string;
  items: NavItem[];
}

const NAV_SECTIONS: NavSection[] = [
  {
    title: 'OPERATIONS',
    items: [
      { label: 'Dashboard', href: '/dashboard', icon: <Zap className="w-4 h-4" />, roles: ['OPERATOR', 'ADMIN', 'ROLE_OPERATOR', 'ROLE_ADMIN'] },
      { label: 'Alerts', href: '/dashboard/alerts', icon: <AlertTriangle className="w-4 h-4" />, roles: ['OPERATOR', 'ADMIN', 'ROLE_OPERATOR', 'ROLE_ADMIN'] },
    ],
  },
  {
    title: 'INTELLIGENCE',
    items: [
      { label: 'Forecasts', href: '/dashboard/forecasts', icon: <TrendingUp className="w-4 h-4" />, roles: ['OPERATOR', 'ADMIN', 'ROLE_OPERATOR', 'ROLE_ADMIN'] },
      { label: 'Complaints', href: '/dashboard/complaints', icon: <AlertTriangle className="w-4 h-4" />, roles: ['OPERATOR', 'ADMIN', 'ROLE_OPERATOR', 'ROLE_ADMIN'] },
    ],
  },
];

interface SidebarProps {
  collapsed?: boolean;
  onToggle?: () => void;
  isOpen?: boolean;
  onClose?: () => void;
}

function hasAccess(roles: string[] | undefined, userRoles: string[]): boolean {
  if (!roles || roles.length === 0) return true;
  return roles.some((role) => userRoles.includes(role) || userRoles.includes(`ROLE_${role}`));
}

export function Sidebar({ collapsed = false, onToggle: _onToggle, isOpen: _isOpen = false, onClose: _onClose }: SidebarProps) {
  const { user } = useAuth();
  const location = useLocation();
  const userRoles = user?.roles || [];

  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['OPERATIONS', 'INTELLIGENCE', 'SYSTEM']));

  useEffect(() => {
    const checkMobile = () => {};
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  const navLinkClass = (isActive: boolean) => `
    flex items-center gap-3 px-3 py-2.5 rounded-[8px]
    transition-colors duration-150
    ${isActive
      ? 'bg-accent-amber/10 text-accent-amber border-l-[3px] border-accent-amber'
      : 'text-grid-muted hover:text-grid-text hover:bg-grid-raised/50'}
    font-medium text-sm
    ${collapsed ? 'justify-center px-2' : ''}
  `;

  const renderNavItem = (item: NavItem) => {
    if (!hasAccess(item.roles, userRoles)) return null;
    const isActive = location.pathname === item.href || location.pathname.startsWith(item.href + '/');
    return (
      <NavLink key={item.href} to={item.href} className={navLinkClass(isActive)} title={collapsed ? item.label : undefined} aria-label={item.label}>
        <span className="w-5 h-5 flex-shrink-0 flex items-center justify-center" aria-hidden="true">{item.icon}</span>
        {!collapsed && <span className="truncate">{item.label}</span>}
      </NavLink>
    );
  };

  const toggleSection = (title: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(title)) next.delete(title);
      else next.add(title);
      return next;
    });
  };

  const renderSection = (section: NavSection) => {
    const visibleItems = section.items.filter((item) => hasAccess(item.roles, userRoles));
    if (visibleItems.length === 0) return null;
    const isExpanded = expandedSections.has(section.title);
    return (
      <div key={section.title} className="mb-4">
        {!collapsed && (
          <button onClick={() => toggleSection(section.title)} className="w-full flex items-center justify-between px-3 py-2 text-xs font-semibold uppercase tracking-wider text-grid-dim hover:text-grid-muted transition-colors" aria-expanded={isExpanded} aria-controls={`section-${section.title}`}>
            <span>{section.title}</span>
            <span className={`ml-auto transition-transform duration-150 ${isExpanded ? 'rotate-90' : ''}`}><ChevronRight className="w-3.5 h-3.5" /></span>
          </button>
        )}
        <div id={`section-${section.title}`} className={`${isExpanded ? '' : 'hidden'} animate-slide-in`} role="group" aria-label={section.title}>
          <nav className="flex flex-col gap-1" aria-label={section.title}>{visibleItems.map(renderNavItem)}</nav>
        </div>
      </div>
    );
  };

  return (
    <aside className={`w-64 h-full bg-grid-surface border-r border-grid-border flex flex-col transition-all duration-200 ${collapsed ? 'w-16' : ''}`} role="navigation" aria-label="Main navigation">
      <div className="flex flex-col h-full">
        <div className={`p-4 border-b border-grid-border flex items-center gap-3 ${collapsed ? 'justify-center' : ''}`}>
          <Zap className="w-6 h-6 text-accent-amber flex-shrink-0" />
          {!collapsed && <span className="font-semibold text-lg text-grid-text tracking-wide">VoltiX</span>}
        </div>
        <nav className="flex-1 overflow-y-auto p-2 space-y-4" aria-label="Main navigation">{NAV_SECTIONS.map(renderSection)}</nav>
        <div className="p-3 border-t border-grid-border"><div className="text-[10px] text-grid-dim text-center">VoltiX v0.1</div></div>
      </div>
    </aside>
  );
}