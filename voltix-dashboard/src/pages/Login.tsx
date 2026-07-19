import React, { useState, useCallback } from 'react';
import { useNavigate, useLocation, Navigate } from 'react-router-dom';
import { Zap, Loader2, AlertCircle, LogIn } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';

interface LocationState {
  from?: { pathname?: string };
}

export default function Login() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const redirectTo = (location.state as LocationState)?.from?.pathname ?? '/';

  // Already logged in? Skip the login screen.
  if (isAuthenticated) {
    return <Navigate to={redirectTo} replace />;
  }

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setError(null);

      if (!username.trim() || !password) {
        setError('Enter both username and password.');
        return;
      }

      setSubmitting(true);
      try {
        await login(username.trim(), password);
        navigate(redirectTo, { replace: true });
      } catch (err: unknown) {
        const status = (err as { response?: { status?: number } })?.response?.status;
        const serverMsg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
        if (status === 401) {
          setError(serverMsg ?? 'Invalid username or password.');
        } else if (status === undefined) {
          setError('Cannot reach the server. Is the backend running on :8080?');
        } else {
          setError(serverMsg ?? `Login failed (HTTP ${status}). Please try again.`);
        }
      } finally {
        setSubmitting(false);
      }
    },
    [username, password, login, navigate, redirectTo],
  );

  return (
    <div className="min-h-screen bg-grid-base flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* Brand */}
        <div className="flex items-center justify-center gap-2 mb-6">
          <Zap className="w-6 h-6 text-accent-amber" />
          <span className="font-semibold text-lg text-white tracking-wide">VoltiX</span>
          <span className="text-grid-muted text-sm">/ Operator Sign-in</span>
        </div>

        <form
          onSubmit={handleSubmit}
          className="rounded-lg border border-grid-border bg-grid-surface p-6 flex flex-col gap-4"
        >
          {/* Visible error banner */}
          {error && (
            <div
              role="alert"
              className="flex items-start gap-2 p-3 rounded bg-accent-red/10 border border-accent-red/30 text-red-400 text-xs animate-fade-in"
            >
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label htmlFor="username" className="block text-xs text-grid-muted mb-1.5">Username</label>
            <input
              id="username"
              name="username"
              type="text"
              autoComplete="username"
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="operator"
              className="w-full bg-grid-raised border border-grid-border rounded px-3 py-2 text-sm text-white
                         placeholder:text-grid-muted focus:outline-none focus:ring-1 focus:ring-accent-blue/50
                         focus:border-accent-blue transition-colors"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-xs text-grid-muted mb-1.5">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full bg-grid-raised border border-grid-border rounded px-3 py-2 text-sm text-white
                         placeholder:text-grid-muted focus:outline-none focus:ring-1 focus:ring-accent-blue/50
                         focus:border-accent-blue transition-colors"
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="flex items-center justify-center gap-2 w-full py-2.5 rounded
                       bg-accent-blue hover:bg-blue-500 text-white text-sm font-medium
                       transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting
              ? <><Loader2 className="w-4 h-4 animate-spin" /> Signing in…</>
              : <><LogIn className="w-4 h-4" /> Sign in</>}
          </button>
        </form>
      </div>
    </div>
  );
}
