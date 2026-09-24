import React, { useState, useEffect, useCallback } from 'react';
import { TrendingUp, AlertCircle, RefreshCw } from 'lucide-react';
import client from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Skeleton } from '../shared/ui';
import type { ZoneForecast } from '../types';

// ── Timestamp formatting ─────────────────────────────────────
// The backend sends absolute instants (…Z). We render them in UTC so the
// operational "TARGET" / "GENERATED" hours are unambiguous and match the
// grid's canonical timezone. We NEVER derive forecastTargetHour on the
// client — it is displayed exactly as persisted by the backend.
function formatHourUtc(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'UTC',
    });
  } catch {
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
  } catch {
    return '—';
  }
}

// Predicted kW is a NUMERIC(14,4); show 2 dp for operational readability.
function formatKw(kw: number): string {
  return Number(kw).toFixed(2);
}

// Latest forecastGeneratedAt across all zones, for the summary strip.
function latestGeneratedAt(rows: ZoneForecast[]): string | null {
  if (rows.length === 0) return null;
  return rows.reduce((latest, r) =>
    new Date(r.forecastGeneratedAt).getTime() > new Date(latest).getTime()
      ? r.forecastGeneratedAt
      : latest,
  rows[0].forecastGeneratedAt);
}

export const ForecastPanel = React.memo(function ForecastPanel() {
  const { user } = useAuth();
  // The backend restricts GET /api/v1/forecasts to OPERATOR / ADMIN. Mirror
  // that here so we never render a permanently-broken panel for other roles
  // (e.g. INSPECTOR). Handles both "OPERATOR" and "ROLE_OPERATOR" shapes.
  const canView = user?.roles?.some((r) =>
    r === 'OPERATOR' || r === 'ADMIN' || r === 'ROLE_OPERATOR' || r === 'ROLE_ADMIN'
  );

  const [forecasts, setForecasts] = useState<ZoneForecast[]>([]);
  const [loading, setLoading] = useState(true);
  const [initialLoad, setInitialLoad] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchForecasts = useCallback(async () => {
    if (initialLoad) setLoading(true);
    setError(null);
    try {
      const res = await client.get<ZoneForecast[]>('/forecasts');
      // Deterministic ordering by zone for a stable data wall.
      const sorted = [...res.data].sort((a, b) => a.zoneId - b.zoneId);
      setForecasts(sorted);
    } catch (err: unknown) {
      // 401 is handled centrally by the axios interceptor (session cleared).
      // Surface a concise operational message for everything else.
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Forecast service unavailable.');
    } finally {
      setLoading(false);
      setInitialLoad(false);
    }
  }, [initialLoad]);

  useEffect(() => {
    if (!canView) {
      setLoading(false);
      setInitialLoad(false);
      return;
    }
    fetchForecasts();
    // Forecasts are regenerated hourly by the backend scheduler and have no
    // WebSocket channel (only alerts use STOMP). A gentle 60s poll is enough
    // to reflect the hourly refresh without aggressive traffic.
    const interval = setInterval(fetchForecasts, 60_000);
    return () => clearInterval(interval);
  }, [canView, fetchForecasts]);

  // Role-gated: do not render a broken panel for users who cannot call the API.
  if (!canView) return null;

  const zoneCount = forecasts.length;
  const generatedAt = latestGeneratedAt(forecasts);

  return (
    <div className="card flex flex-col overflow-hidden">
      {/* ── Header ── */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-grid-border shrink-0">
        <div className="flex items-center gap-2">
          <TrendingUp className="w-4 h-4 text-accent-amber" />
          <span className="text-[14px] font-semibold text-grid-text tracking-wide">2H LOAD FORECAST</span>
          <span className="text-[10px] font-mono text-accent-amber/80 border border-accent-amber/30 rounded px-1 py-0.5">
            PREDICTED
          </span>
        </div>
        <button
          onClick={fetchForecasts}
          disabled={loading}
          aria-label="Refresh forecasts"
          className="text-grid-dim hover:text-grid-text transition-colors disabled:opacity-40"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* ── Summary strip (real data only) ── */}
      {!loading && !error && zoneCount > 0 && (
        <div className="flex items-center justify-between px-4 py-2 border-b border-grid-border-subtle bg-grid-raised/20 shrink-0 text-[11px]">
          <span className="text-grid-muted">
            <span className="font-mono text-grid-text">{zoneCount}</span>
            {' '}zone{zoneCount === 1 ? '' : 's'}
          </span>
          <span className="text-grid-muted">
            HORIZON <span className="font-mono text-accent-amber">+2H</span>
          </span>
          <span className="text-grid-muted">
            GEN <span className="font-mono text-grid-text">{generatedAt ? formatHourUtc(generatedAt) : '—'}</span>
            <span className="text-grid-dim"> UTC</span>
          </span>
        </div>
      )}

      {/* ── Body ── */}
      <div className="overflow-y-auto">
        {/* Loading — skeleton, no fake values */}
        {loading && <Skeleton variant="card" count={3} />}

        {/* Error */}
        {error && !loading && (
          <div className="flex flex-col items-center gap-2 p-6 text-center">
            <AlertCircle className="w-6 h-6 text-accent-orange" />
            <p className="text-[11px] font-mono uppercase tracking-wide text-accent-orange">Forecast Error</p>
            <p className="text-xs text-grid-muted">{error}</p>
            <button onClick={fetchForecasts} className="text-xs text-accent-blue hover:underline mt-1">
              Retry
            </button>
          </div>
        )}

        {/* Empty — not an error */}
        {!loading && !error && zoneCount === 0 && (
          <div className="flex flex-col items-center justify-center h-full min-h-[80px] gap-2 text-center px-6">
            <TrendingUp className="w-6 h-6 text-grid-dim opacity-40" />
            <p className="text-[11px] font-mono uppercase tracking-wide text-grid-muted">No Forecast Data</p>
            <p className="text-xs text-grid-dim">No 2-hour forecasts are currently available.</p>
          </div>
        )}

        {/* Success — per-zone data wall */}
        {!loading && !error && zoneCount > 0 && (
          <div className="flex flex-col">
            {forecasts.map((f, i) => (
              <div
                key={f.zoneId}
                className={`px-4 py-3 border-l-[3px] border-l-accent-amber/70 animate-fade-in
                            ${i % 2 === 1 ? 'bg-grid-raised/20' : ''}`}
              >
                {/* Zone label */}
                <div className="flex items-center justify-between">
                  <span className="text-[12px] font-mono font-semibold text-grid-text tracking-wide">
                    ZONE {String(f.zoneId).padStart(2, '0')}
                  </span>
                  <span className="text-[10px] font-mono text-grid-dim">
                    gen {timeAgo(f.forecastGeneratedAt)}
                  </span>
                </div>

                {/* Predicted load — the headline number */}
                <div className="mt-1 flex items-baseline gap-1.5">
                  <span className="text-[22px] font-mono font-bold text-accent-amber leading-none">
                    {formatKw(f.forecastKw2h)}
                  </span>
                  <span className="text-[12px] text-grid-muted">kW</span>
                  <span className="text-[9px] font-mono text-grid-dim uppercase ml-1">predicted</span>
                </div>

                {/* Target + generated (from API, displayed directly) */}
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
  );
});
