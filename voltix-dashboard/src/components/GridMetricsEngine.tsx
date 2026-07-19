import React, { useState, useCallback } from 'react';
import { Activity, Zap, AlertTriangle, FileWarning, Radio, Loader2 } from 'lucide-react';
import { useAlertStore } from '../store/alertStore';
import { useAuth } from '../auth/AuthContext';
import client from '../api/client';

// ── Types ────────────────────────────────────────────────────
interface SimulationResult {
  status: string;
  message: string;
  count: number;
  readings: Array<{ transactionId: string; meterId: string; anomalous: boolean }>;
}

// ── Stat card ────────────────────────────────────────────────
const StatCard = React.memo(function StatCard({
  icon, label, value, color,
}: { icon: React.ReactNode; label: string; value: string | number; color: string }) {
  return (
    <div className="rounded-lg border border-grid-border bg-grid-raised p-4 flex flex-col gap-1.5">
      <div className="flex items-center gap-1.5 text-xs text-grid-muted">
        <span className={color}>{icon}</span>
        {label}
      </div>
      <span className={`text-xl font-mono font-semibold ${color}`}>{value}</span>
    </div>
  );
});

// ── GridMetricsEngine ────────────────────────────────────────
// Displays real summary data from the alert store and provides
// the demo simulation trigger for live reviews.
export const GridMetricsEngine = React.memo(function GridMetricsEngine() {
  const alerts = useAlertStore((s) => s.alerts);
  const { user } = useAuth();

  const [simulating, setSimulating] = useState(false);
  const [simResult, setSimResult] = useState<SimulationResult | null>(null);
  const [simError, setSimError] = useState<string | null>(null);

  // Derived real stats from the alert store (no fake data)
  const totalAlerts = alerts.length;
  const criticalAlerts = alerts.filter((a) => a.severity === 'CRITICAL').length;
  const highAlerts = alerts.filter((a) => a.severity === 'HIGH').length;
  const openAlerts = alerts.filter((a) => a.status === 'OPEN').length;

  // Unique zones and meters observed in alerts
  const activeZones = new Set(alerts.map((a) => a.zoneId)).size;
  const activeMeters = new Set(alerts.map((a) => a.meterId)).size;

  const canSimulate = user?.roles?.some((r) =>
    r === 'OPERATOR' || r === 'ADMIN' || r === 'ROLE_OPERATOR' || r === 'ROLE_ADMIN'
  );

  const handleSimulate = useCallback(async () => {
    setSimulating(true);
    setSimResult(null);
    setSimError(null);
    try {
      const res = await client.post<SimulationResult>('/demo/simulate-telemetry');
      setSimResult(res.data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Simulation failed';
      setSimError(message);
    } finally {
      setSimulating(false);
    }
  }, []);

  return (
    <div className="flex flex-col gap-4">
      {/* Real summary stats from alert store */}
      <div className="grid grid-cols-3 gap-3">
        <StatCard
          icon={<AlertTriangle className="w-4 h-4" />}
          label="Total Alerts"
          value={totalAlerts}
          color="text-accent-blue"
        />
        <StatCard
          icon={<Zap className="w-4 h-4" />}
          label="Critical"
          value={criticalAlerts}
          color="text-red-400"
        />
        <StatCard
          icon={<Activity className="w-4 h-4" />}
          label="High"
          value={highAlerts}
          color="text-accent-amber"
        />
      </div>

      <div className="grid grid-cols-3 gap-3">
        <StatCard
          icon={<FileWarning className="w-4 h-4" />}
          label="Open"
          value={openAlerts}
          color="text-accent-green"
        />
        <StatCard
          icon={<Radio className="w-4 h-4" />}
          label="Active Zones"
          value={activeZones}
          color="text-accent-blue"
        />
        <StatCard
          icon={<Activity className="w-4 h-4" />}
          label="Active Meters"
          value={activeMeters}
          color="text-accent-amber"
        />
      </div>

      {/* Demo simulation trigger — only visible to OPERATOR/ADMIN */}
      {canSimulate && (
        <div className="rounded-lg border border-grid-border bg-grid-surface p-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-white">Live Pipeline Demo</h3>
              <p className="text-xs text-grid-muted mt-0.5">
                Inject telemetry through the real ONNX anomaly-detection pipeline
              </p>
            </div>
            <button
              onClick={handleSimulate}
              disabled={simulating}
              className="flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium
                         bg-accent-blue/20 text-accent-blue border border-accent-blue/30
                         hover:bg-accent-blue/30 transition-colors
                         disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label="Simulate telemetry event through the real anomaly detection pipeline"
            >
              {simulating ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Processing…
                </>
              ) : (
                <>
                  <Zap className="w-4 h-4" />
                  Simulate Telemetry Event
                </>
              )}
            </button>
          </div>

          {/* Result feedback */}
          {simResult && (
            <div className="mt-3 p-2.5 rounded border border-green-500/30 bg-green-500/10 text-xs text-green-300">
              {simResult.message} — alerts will appear in the Live Feed via WebSocket shortly.
            </div>
          )}
          {simError && (
            <div className="mt-3 p-2.5 rounded border border-red-500/30 bg-red-500/10 text-xs text-red-300">
              Error: {simError}
            </div>
          )}
        </div>
      )}
    </div>
  );
});
