import { useAuth } from '../../auth/AuthContext';
import { ConnectionStatus } from '../../components/ConnectionStatus';

interface HeaderProps {
  onMenuClick: () => void;
  title?: string;
}

function Header({ onMenuClick, title }: HeaderProps) {
  const { user, logout } = useAuth();

  return (
    <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-4 shrink-0">
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuClick}
          className="p-2 rounded-[6px] text-grid-muted hover:text-grid-text hover:bg-grid-raised/50 transition-colors lg:hidden"
          aria-label="Toggle menu"
          aria-expanded="false"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        {title && (
          <h1 className="font-semibold text-[15px] text-grid-text truncate max-w-[300px]">
            {title}
          </h1>
        )}
      </div>

      <div className="flex items-center gap-4">
        <ConnectionStatus
          status="CONNECTED"
          reconnectAttempts={0}
        />

        <div className="flex items-center gap-4">
          <div className="relative" style={{ zIndex: 50 }}>
            <button
              className="flex items-center gap-2 p-1.5 rounded-[6px] text-grid-muted hover:text-grid-text hover:bg-grid-raised/50 transition-colors"
              aria-label="Notifications"
              aria-expanded="false"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.343C7.67 5.065 6 6.435 6 8v17.768c0 .615.497 1.116 1.108 1.096l4.996-.002h.004zm-11.5 0h11.5" />
              </svg>
              <span className="absolute -top-1 -right-1 w-4 h-4 bg-accent-red rounded-full text-[9px] font-mono font-semibold text-white flex items-center justify-center">
                0
              </span>
            </button>
          </div>

          <div className="flex items-center gap-3">
            <div className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-[6px] bg-grid-raised/50">
              <div className="w-7 h-7 rounded-full bg-accent-amber/20 flex items-center justify-center">
                <svg className="w-4 h-4 text-accent-amber" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                </svg>
              </div>
              <span className="text-sm font-medium text-grid-text">
                {user?.username}
              </span>
              <span className="px-1.5 py-0.5 text-[10px] rounded-full bg-accent-amber/20 text-accent-amber font-mono uppercase tracking-wide">
                {user?.roles?.[0]?.replace('ROLE_', '')}
              </span>
            </div>

            <button
              onClick={() => {
                logout();
                window.location.href = '/login';
              }}
              className="p-2 rounded-[6px] text-grid-muted hover:text-accent-red hover:bg-grid-raised/50 transition-colors"
              aria-label="Log out"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a2 2 0 012-2h2a2 2 0 012 2v2h10a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2 2v2a2 2 0 01-2 2h-2a2 2 0 01-2 2v2c0 .55-.45 1-1 1h-2a2 2 0 01-2-2v-2a2 2 0 01-2-2H7a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012-2z" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}

export { Header };