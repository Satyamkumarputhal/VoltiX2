/**
 * Design System Preview — NOT a production page.
 * Used to review the visual direction of the new token system before
 * applying it to the real dashboard and report form.
 * Remove this file before final merge if not needed.
 */
export default function DesignPreview() {
  return (
    <div className="min-h-screen bg-grid-base p-8 font-sans">
      <h1 className="text-xl font-semibold text-grid-text mb-1">VoltiX Design System Preview</h1>
      <p className="text-sm text-grid-muted mb-8">Industrial monitoring tokens — control-room aesthetic</p>

      {/* ── Severity Badges ── */}
      <section className="mb-8">
        <h2 className="stat-label mb-3">Severity Badges</h2>
        <div className="flex items-center gap-3">
          <span className="badge badge-low">LOW</span>
          <span className="badge badge-medium">MEDIUM</span>
          <span className="badge badge-high">HIGH</span>
          <span className="badge badge-critical">CRITICAL</span>
        </div>
      </section>

      {/* ── Stat Cards (KPI) ── */}
      <section className="mb-8">
        <h2 className="stat-label mb-3">Stat Cards — Operator KPIs</h2>
        <div className="grid grid-cols-4 gap-4 max-w-3xl">
          {[
            { label: 'Active Alerts', value: '12', color: 'text-accent-amber' },
            { label: 'Critical', value: '3', color: 'text-accent-red' },
            { label: 'Zones Online', value: '2', color: 'text-accent-green' },
            { label: 'Avg Score', value: '0.64', color: 'text-accent-cyan' },
          ].map(({ label, value, color }) => (
            <div key={label} className="card flex flex-col gap-2">
              <span className="stat-label">{label}</span>
              <span className={`stat-value ${color}`}>{value}</span>
            </div>
          ))}
        </div>
      </section>

      {/* ── Buttons ── */}
      <section className="mb-8">
        <h2 className="stat-label mb-3">Buttons</h2>
        <div className="flex items-center gap-3">
          <button className="btn btn-primary">Simulate Event</button>
          <button className="btn btn-danger">Escalate</button>
          <button className="btn btn-ghost">Acknowledge</button>
          <button className="btn btn-primary" disabled>Disabled</button>
        </div>
      </section>

      {/* ── Alert Row Sample ── */}
      <section className="mb-8">
        <h2 className="stat-label mb-3">Alert Row — Data-Dense Readout</h2>
        <div className="card max-w-2xl">
          <div className="flex items-center justify-between py-2 border-b border-grid-border-subtle">
            <div className="flex items-center gap-3">
              <div className="w-0.5 h-8 rounded-full bg-accent-amber" />
              <span className="badge badge-medium">MEDIUM</span>
              <span className="data-mono">SM-2</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="data-mono text-grid-muted">Zone 1</span>
              <span className="data-mono text-grid-muted">score: <span className="text-accent-amber">1.92</span></span>
              <span className="data-mono text-grid-dim">21:53:28</span>
            </div>
          </div>
          <div className="flex items-center justify-between py-2">
            <div className="flex items-center gap-3">
              <div className="w-0.5 h-8 rounded-full bg-accent-red" />
              <span className="badge badge-critical">CRITICAL</span>
              <span className="data-mono">SM-4</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="data-mono text-grid-muted">Zone 1</span>
              <span className="data-mono text-grid-muted">score: <span className="text-accent-red">4.12</span></span>
              <span className="data-mono text-grid-dim">21:52:05</span>
            </div>
          </div>
        </div>
      </section>

      {/* ── Typography Scale ── */}
      <section className="mb-8">
        <h2 className="stat-label mb-3">Type Scale</h2>
        <div className="card max-w-xl flex flex-col gap-2">
          <p className="text-2xl font-semibold">2xl — Page Title (24px)</p>
          <p className="text-xl">xl — Section Header (20px)</p>
          <p className="text-lg">lg — Subsection (16px)</p>
          <p className="text-base">base — Body (14px, dense default)</p>
          <p className="text-sm text-grid-muted">sm — Secondary (13px)</p>
          <p className="text-xs text-grid-dim font-mono">xs mono — Micro-labels, badges (11px)</p>
        </div>
      </section>

      {/* ── Color Swatches ── */}
      <section className="mb-8">
        <h2 className="stat-label mb-3">Surface Depth + Accents</h2>
        <div className="flex gap-2 mb-4">
          {[
            ['Base', 'bg-grid-base'],
            ['Surface', 'bg-grid-surface'],
            ['Raised', 'bg-grid-raised'],
            ['Elevated', 'bg-grid-elevated'],
          ].map(([name, bg]) => (
            <div key={name} className={`${bg} border border-grid-border w-20 h-16 rounded-[5px] flex items-end p-2`}>
              <span className="text-[9px] text-grid-muted font-mono">{name}</span>
            </div>
          ))}
        </div>
        <div className="flex gap-2">
          {[
            ['Green', 'bg-accent-green'],
            ['Blue', 'bg-accent-blue'],
            ['Amber', 'bg-accent-amber'],
            ['Orange', 'bg-accent-orange'],
            ['Red', 'bg-accent-red'],
            ['Cyan', 'bg-accent-cyan'],
          ].map(([name, bg]) => (
            <div key={name} className={`${bg} w-14 h-10 rounded-[3px] flex items-end justify-center pb-1`}>
              <span className="text-[8px] text-black/70 font-mono font-bold">{name}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
