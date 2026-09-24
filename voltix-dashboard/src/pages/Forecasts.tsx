import React from 'react';
import { useAuth } from '../auth/AuthContext';
import client from '../api/client';
import type { ZoneForecast } from '../types';

function formatHourUtc(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'UTC',
    });
  } catch (_err) {
    return '—';
  }
}

function timeAgo(iso: string): string {
  try {
    const diff = Date.now() - new Date(iso).getTime();
    const mins = Math.floor(diff / 60_000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
  } catch (_err) {
    return '—';
  }
}

function formatKw(kw: number): string {
  return Number(kw).toFixed(2);
}

function Forecasts() {
  const { user } = useAuth();
  const canView = user?.roles?.some((r) =>
    r === 'OPERATOR' || r === 'ADMIN' || r === 'ROLE_OPERATOR' || r === 'ROLE_ADMIN'
  );

  const [forecasts, setForecasts] = React.useState<ZoneForecast[]>([]);
  const [_loading, setLoading] = React.useState(true);
  const [initialLoad, setInitialLoad] = React.useState(true);
  const [_error, setError] = React.useState<string | null>(null);

  const fetchForecasts = React.useCallback(async () => {
    if (initialLoad) setLoading(true);
    setError(null);
    try {
      const res = await client.get<ZoneForecast[]>('/forecasts');
      const sorted = [...res.data].sort((a, b) => a.zoneId - b.zoneId);
      setForecasts(sorted);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Forecast service unavailable.');
    } finally {
      setLoading(false);
      setInitialLoad(false);
    }
  }, [initialLoad]);

  React.useEffect(() => {
    if (!canView) {
      setLoading(false);
      setInitialLoad(false);
      return;
    }
    fetchForecasts();
    const interval = setInterval(fetchForecasts, 60_000);
    return () => clearInterval(interval);
  }, [canView, fetchForecasts]);

  if (!canView) return null;

  return (
    <div className="h-screen bg-grid-base flex flex-col overflow-hidden">
      <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-5 shrink-0">
        <div className="flex items-center gap-3">
          <svg className="w-4 h-4 text-accent-amber" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
          </svg>
          <span className="font-semibold text-[15px] text-grid-text">2H Load Forecast</span>
          <span className="text-[10px] font-mono text-accent-amber/80 border border-accent-amber/30 rounded px-1 py-0.5">PREDICTED</span>
        </div>
        <button
          onClick={async () => {
            try {
              await client.post('/forecasts/run');
            } catch {}
          }}
          disabled={false}
          aria-label="Refresh forecasts"
          className="text-grid-dim hover:text-grid-text transition-colors disabled:opacity-40"
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 00-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </header>

      <div className="flex-1 p-4 overflow-auto">
        <div className="flex flex-col gap-4">
          {forecasts.length > 0 && (
            <div className="flex flex-col gap-3">
              {forecasts.map((f, i) => (
                <div
                  key={f.zoneId}
                  className={`px-4 py-3 border-l-[3px] border-l-accent-amber/70 animate-fade-in ${i % 2 === 1 ? 'bg-grid-raised/20' : ''}`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-[12px] font-mono font-semibold text-grid-text tracking-wide">
                      ZONE {String(f.zoneId).padStart(2, '0')}
                    </span>
                    <span className="text-[10px] font-mono text-grid-dim">
                      gen {timeAgo(f.forecastGeneratedAt)}
                    </span>
                  </div>
                  <div className="mt-1 flex items-baseline gap-1.5">
                    <span className="text-[22px] font-mono font-bold text-accent-amber leading-none">
                      {formatKw(f.forecastKw2h)}
                    </span>
                    <span className="text-[12px] text-grid-muted">kW</span>
                    <span className="text-[9px] font-mono text-grid-dim uppercase ml-1">predicted</span>
                  </div>
                  <div className="mt-1.5 flex items-center gap-4 text-[11px] font-mono">
                    <span className="text-grid-muted">
                      TARGET{' '}
                      <span className="text-grid-text">{formatHourUtc(f.forecastTargetHour)}</span>
                      <span className="text-grid-dim"> UTC</span>
                    </span>
                    <span className="text-grid-muted">
                      GEN{' '}
                      <span className="text-grid-text">{formatHourUtc(f.forecastGeneratedAt)}</span>
                      <span className="text-grid-dim"> UTC</span>
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default Forecasts;