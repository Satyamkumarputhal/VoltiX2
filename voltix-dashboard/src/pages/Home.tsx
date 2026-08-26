import { Link } from 'react-router-dom';
import { Zap, LogIn, FileWarning } from 'lucide-react';

export default function Home() {
  return (
    <div className="min-h-screen bg-grid-base flex items-center justify-center p-6">
      <div className="w-full max-w-2xl flex flex-col items-center gap-8 text-center">
        
        {/* Branding */}
        <div className="flex items-center gap-3">
          <Zap className="w-8 h-8 text-accent-amber" />
          <span className="font-semibold text-3xl text-white tracking-wide">VoltiX</span>
        </div>

        {/* Tagline */}
        <p className="text-lg text-grid-muted max-w-lg">
          Real-time smart grid monitoring and incident reporting
        </p>

        {/* Action buttons */}
        <div className="flex flex-col sm:flex-row gap-4 w-full max-w-md">
          
          {/* Primary: Operator Login */}
          <Link
            to="/login"
            className="flex-1 flex items-center justify-center gap-2 px-6 py-4 rounded-lg
                       bg-accent-amber hover:bg-amber-400 text-grid-base font-medium
                       transition-colors group"
          >
            <LogIn className="w-5 h-5" />
            <div className="flex flex-col items-start">
              <span className="text-base">Operator Login</span>
              <span className="text-xs opacity-80">For grid operators</span>
            </div>
          </Link>

          {/* Secondary: Report Incident */}
          <Link
            to="/report"
            className="flex-1 flex items-center justify-center gap-2 px-6 py-4 rounded-lg
                       border-2 border-grid-border hover:border-accent-amber/50 bg-grid-surface
                       text-grid-text hover:text-accent-amber font-medium transition-colors group"
          >
            <FileWarning className="w-5 h-5" />
            <div className="flex flex-col items-start">
              <span className="text-base">Report an Incident</span>
              <span className="text-xs text-grid-dim">For the public</span>
            </div>
          </Link>
        </div>

        {/* Footer note */}
        <p className="text-xs text-grid-dim mt-4">
          No account required for incident reporting
        </p>
      </div>
    </div>
  );
}
