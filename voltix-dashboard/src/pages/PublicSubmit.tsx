import { Zap, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import { ComplaintSubmissionForm } from '../components/ComplaintSubmissionForm';

export default function PublicSubmit() {
  return (
    <div className="min-h-screen bg-grid-base flex flex-col">
      {/* Minimal header — brand identity only, no operator chrome */}
      <header className="h-14 border-b border-grid-border/50 flex items-center justify-between px-6 shrink-0">
        <div className="flex items-center gap-2.5">
          <Zap className="w-5 h-5 text-accent-amber" />
          <span className="font-semibold text-[16px] text-grid-text">VoltiX</span>
        </div>
        {/* Subtle — not prominent, mostly for operators who navigated here */}
        <Link
          to="/"
          className="flex items-center gap-1.5 text-[12px] text-grid-dim hover:text-grid-muted transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          Dashboard
        </Link>
      </header>

      {/* Content — generous vertical spacing, centered narrow column */}
      <main className="flex-1 flex items-start justify-center px-5 py-12 sm:py-16">
        <div className="w-full max-w-[520px]">
          {/* Page heading — larger, calmer */}
          <div className="mb-8 text-center">
            <h1 className="text-[24px] sm:text-[28px] font-semibold text-grid-text leading-tight">
              Report a Grid Incident
            </h1>
            <p className="text-[14px] sm:text-[15px] text-grid-muted mt-3 leading-relaxed max-w-[420px] mx-auto">
              Submit an anonymous report about a power issue in your area.
              Your IP is hashed and never stored in plain text.
            </p>
          </div>

          {/* Form */}
          <ComplaintSubmissionForm />

          {/* Reassurance footer */}
          <p className="text-center text-[12px] text-grid-dim mt-8 leading-relaxed">
            Reports are reviewed by grid operators within 24 hours.
            No account or login required.
          </p>
        </div>
      </main>
    </div>
  );
}
