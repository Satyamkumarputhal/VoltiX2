import { Link } from 'react-router-dom';
import { Zap, LogIn, FileWarning, Activity, Shield, Database } from 'lucide-react';

export default function Home() {
  return (
    <div className="min-h-screen bg-grid-base flex flex-col">
      
      {/* Hero Section */}
      <div className="flex-1 flex items-center justify-center p-6">
        <div className="w-full max-w-4xl">
          
          {/* Main hero card — matches dashboard card aesthetic */}
          <div className="card p-12 mb-8">
            <div className="flex flex-col items-center gap-8 text-center">
              
              {/* Branding */}
              <div className="flex items-center gap-3">
                <Zap className="w-10 h-10 text-accent-amber" />
                <span className="font-semibold text-4xl text-white tracking-wide">VoltiX</span>
              </div>

              {/* Tagline */}
              <p className="text-xl text-grid-muted max-w-2xl leading-relaxed">
                Real-time smart grid monitoring and incident reporting
              </p>

              {/* Action buttons */}
              <div className="flex flex-col sm:flex-row gap-4 w-full max-w-xl mt-4">
                
                {/* Primary: Operator Login */}
                <Link
                  to="/login"
                  className="flex-1 flex items-center justify-center gap-3 px-8 py-5 rounded-lg
                             bg-accent-amber hover:bg-amber-400 text-grid-base font-semibold text-base
                             transition-colors shadow-md"
                >
                  <LogIn className="w-5 h-5" />
                  <div className="flex flex-col items-start gap-0.5">
                    <span>Operator Login</span>
                    <span className="text-xs font-normal opacity-80">For grid operators</span>
                  </div>
                </Link>

                {/* Secondary: Report Incident */}
                <Link
                  to="/report"
                  className="flex-1 flex items-center justify-center gap-3 px-8 py-5 rounded-lg
                             border-2 border-grid-border hover:border-accent-amber/50 bg-grid-surface
                             text-grid-text hover:text-accent-amber font-semibold text-base
                             transition-colors"
                >
                  <FileWarning className="w-5 h-5" />
                  <div className="flex flex-col items-start gap-0.5">
                    <span>Report an Incident</span>
                    <span className="text-xs font-normal text-grid-dim">For the public</span>
                  </div>
                </Link>
              </div>

              {/* Footer note */}
              <p className="text-sm text-grid-dim mt-2">
                No account required for incident reporting
              </p>
            </div>
          </div>

          {/* Feature cards row — same visual pattern as dashboard stat cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            
            <div className="card px-6 py-5 flex flex-col items-center gap-3 text-center">
              <Activity className="w-8 h-8 text-accent-amber" />
              <div>
                <div className="stat-label mb-1">Real-time Detection</div>
                <p className="text-xs text-grid-muted">ONNX anomaly inference with live WebSocket alerts</p>
              </div>
            </div>

            <div className="card px-6 py-5 flex flex-col items-center gap-3 text-center">
              <Shield className="w-8 h-8 text-accent-cyan" />
              <div>
                <div className="stat-label mb-1">Tenant Isolation</div>
                <p className="text-xs text-grid-muted">Multi-tenant architecture with row-level security</p>
              </div>
            </div>

            <div className="card px-6 py-5 flex flex-col items-center gap-3 text-center">
              <Database className="w-8 h-8 text-accent-blue" />
              <div>
                <div className="stat-label mb-1">Time-Series Storage</div>
                <p className="text-xs text-grid-muted">Partitioned metrics history with batch writes</p>
              </div>
            </div>

          </div>
        </div>
      </div>

      {/* Footer — matches dashboard footer style */}
      <footer className="shrink-0 h-10 border-t border-grid-border bg-grid-surface flex items-center justify-center px-5 text-xs text-grid-dim">
        <span>VoltiX Grid Monitor v0.1 — Real-time smart grid analytics platform</span>
      </footer>
    </div>
  );
}
