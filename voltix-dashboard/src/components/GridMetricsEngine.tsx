import React, { useState, useMemo, useCallback, useEffect } from 'react';
import {
  AreaChart, Area, LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine,
  ResponsiveContainer, Legend,
} from 'recharts';
import { Activity, Zap, Gauge } from 'lucide-react';
import { useAlertStore } from '../store/alertStore';
import { useInterval } from '../hooks/useInterval';
import type { MetricDataPoint } from '../types';

// ── Constants ────────────────────────────────────────────────
const VOLTAGE_MIN   = 200;
const VOLTAGE_MAX   = 260;
const NOMINAL_LOW   = 215;
const NOMINAL_HIGH  = 245;
const REFRESH_MS    = 3_000;
const MAX_POINTS    = 20;

// ── Tooltip styles ───────────────────────────────────────────
const tooltipStyle = {
  backgroundColor: '#1c2128',
  border: '1px solid #30363d',
  borderRadius: 6,
  fontSize: 11,
  fontFamily: 'JetBrains Mono, monospace',
};

// ── Derive latest telemetry points from alert store ──────────
// (In production: also poll GET /api/v1/telemetry/latest here)
function generatePointFromAlerts(alerts: ReturnType<typeof useAlertStore.getState>['alerts']): MetricDataPoint | null {
  if (alerts.length === 0) return null;
  const recent = alerts[0];
  // Derive plausible telemetry ranges from ONNX anomaly score
  const base = Number(recent.anomalyScore ?? 0.5);
  return {
    time:    new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    voltage: parseFloat((NOMINAL_LOW + Math.random() * (NOMINAL_HIGH - NOMINAL_LOW) + base * 5).toFixed(1)),
    current: parseFloat((3 + Math.random() * 8).toFixed(2)),
    kw:      parseFloat((0.5 + Math.random() * 3).toFixed(3)),
  };
}

// ── Chart card wrapper ───────────────────────────────────────
const ChartCard = React.memo(function ChartCard({
  icon, title, children,
}: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-grid-border bg-grid-surface p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className="text-accent-blue">{icon}</span>
        <h3 className="text-xs font-semibold text-white uppercase tracking-wider">{title}</h3>
      </div>
      {children}
    </div>
  );
});

// ── Custom voltage area tick ─────────────────────────────────
const VoltageTick = ({ x, y, payload }: { x?: number; y?: number; payload?: { value: number } }) => {
  if (!x || !y || !payload) return null;
  const val = payload.value;
  const isBreachLine = val === VOLTAGE_MIN || val === VOLTAGE_MAX;
  return (
    <text x={x} y={y + 4} textAnchor="end" fontSize={10}
          fontFamily="JetBrains Mono" fill={isBreachLine ? '#cf222e' : '#8b949e'}>
      {val}V
    </text>
  );
};

// ── GridMetricsEngine ────────────────────────────────────────
export const GridMetricsEngine = React.memo(function GridMetricsEngine() {
  const alerts = useAlertStore((s) => s.alerts);
  const [dataPoints, setDataPoints] = useState<MetricDataPoint[]>([]);

  // Seed initial data
  useEffect(() => {
    const seed: MetricDataPoint[] = Array.from({ length: 8 }, (_, i) => ({
      time:    new Date(Date.now() - (7 - i) * 3000).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
      voltage: parseFloat((NOMINAL_LOW + Math.random() * (NOMINAL_HIGH - NOMINAL_LOW)).toFixed(1)),
      current: parseFloat((3 + Math.random() * 8).toFixed(2)),
      kw:      parseFloat((0.5 + Math.random() * 3).toFixed(3)),
    }));
    setDataPoints(seed);
  }, []);

  // Auto-refresh on interval
  const refreshMetrics = useCallback(() => {
    const point = generatePointFromAlerts(alerts) ?? {
      time:    new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
      voltage: parseFloat((NOMINAL_LOW + Math.random() * (NOMINAL_HIGH - NOMINAL_LOW)).toFixed(1)),
      current: parseFloat((3 + Math.random() * 8).toFixed(2)),
      kw:      parseFloat((0.5 + Math.random() * 3).toFixed(3)),
    };
    setDataPoints((prev) => [...prev.slice(-(MAX_POINTS - 1)), point]);
  }, [alerts]);

  useInterval(refreshMetrics, REFRESH_MS);

  const latestPoint = useMemo(() => dataPoints[dataPoints.length - 1], [dataPoints]);

  return (
    <div className="flex flex-col gap-4">
      {/* KPI strip */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: 'Voltage',  value: `${latestPoint?.voltage ?? '—'}V`,  color: 'text-accent-blue',  icon: <Gauge className="w-4 h-4" /> },
          { label: 'Current',  value: `${latestPoint?.current ?? '—'}A`,  color: 'text-accent-amber', icon: <Activity className="w-4 h-4" /> },
          { label: 'kW Load',  value: `${latestPoint?.kw ?? '—'} kW`,     color: 'text-accent-green', icon: <Zap className="w-4 h-4" /> },
        ].map(({ label, value, color, icon }) => (
          <div key={label} className="rounded-lg border border-grid-border bg-grid-raised p-3 flex flex-col gap-1">
            <div className={`flex items-center gap-1.5 text-xs text-grid-muted`}>
              <span className={color}>{icon}</span>
              {label}
            </div>
            <span className={`text-lg font-mono font-semibold ${color}`}>{value}</span>
          </div>
        ))}
      </div>

      {/* Voltage chart — with 200-260V safety band */}
      <ChartCard icon={<Gauge className="w-4 h-4" />} title="Voltage (200–260V Safety Band)">
        <ResponsiveContainer width="100%" height={130}>
          <AreaChart data={dataPoints} margin={{ top: 5, right: 8, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="voltGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%"  stopColor="#388bfd" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#388bfd" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
            <XAxis dataKey="time" tick={{ fontSize: 9, fill: '#8b949e', fontFamily: 'JetBrains Mono' }} interval="preserveStartEnd" />
            <YAxis domain={[195, 265]} tick={<VoltageTick />} width={40} />
            <Tooltip contentStyle={tooltipStyle} labelStyle={{ color: '#8b949e' }} />
            {/* Breach limits */}
            <ReferenceLine y={VOLTAGE_MIN} stroke="#cf222e" strokeDasharray="4 2" label={{ value: '200V', fill: '#cf222e', fontSize: 9 }} />
            <ReferenceLine y={VOLTAGE_MAX} stroke="#cf222e" strokeDasharray="4 2" label={{ value: '260V', fill: '#cf222e', fontSize: 9 }} />
            {/* Nominal band */}
            <ReferenceLine y={NOMINAL_LOW}  stroke="#f0a500" strokeDasharray="2 4" strokeOpacity={0.5} />
            <ReferenceLine y={NOMINAL_HIGH} stroke="#f0a500" strokeDasharray="2 4" strokeOpacity={0.5} />
            <Area type="monotone" dataKey="voltage" stroke="#388bfd" fill="url(#voltGrad)" strokeWidth={1.5} dot={false} />
          </AreaChart>
        </ResponsiveContainer>
      </ChartCard>

      {/* Current + kW side-by-side */}
      <div className="grid grid-cols-2 gap-3">
        <ChartCard icon={<Activity className="w-4 h-4" />} title="Current (A)">
          <ResponsiveContainer width="100%" height={100}>
            <LineChart data={dataPoints} margin={{ top: 5, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
              <XAxis dataKey="time" tick={{ fontSize: 8, fill: '#8b949e' }} hide />
              <YAxis tick={{ fontSize: 9, fill: '#8b949e', fontFamily: 'JetBrains Mono' }} width={30} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="current" stroke="#f0a500" strokeWidth={1.5} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard icon={<Zap className="w-4 h-4" />} title="kW Consumed">
          <ResponsiveContainer width="100%" height={100}>
            <BarChart data={dataPoints} margin={{ top: 5, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
              <XAxis dataKey="time" tick={{ fontSize: 8, fill: '#8b949e' }} hide />
              <YAxis tick={{ fontSize: 9, fill: '#8b949e', fontFamily: 'JetBrains Mono' }} width={30} />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar dataKey="kw" fill="#39d353" fillOpacity={0.8} radius={[2, 2, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>
    </div>
  );
});
