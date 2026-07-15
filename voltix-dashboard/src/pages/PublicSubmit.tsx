import React from 'react';
import { Zap, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import { ComplaintSubmissionForm } from '../components/ComplaintSubmissionForm';

export default function PublicSubmit() {
  return (
    <div className="min-h-screen bg-grid-base flex flex-col">
      {/* Header */}
      <header className="h-12 border-b border-grid-border bg-grid-surface flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-2">
          <Zap className="w-5 h-5 text-accent-amber" />
          <span className="font-semibold text-sm text-white">VoltiX</span>
          <span className="text-grid-muted text-xs">/ Report Incident</span>
        </div>
        <Link
          to="/"
          className="flex items-center gap-1.5 text-xs text-grid-muted hover:text-white transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          Back to Dashboard
        </Link>
      </header>

      {/* Content */}
      <main className="flex-1 flex items-start justify-center p-6 pt-12">
        <div className="w-full max-w-lg">
          <div className="mb-6 text-center">
            <h1 className="text-xl font-semibold text-white">Report a Grid Incident</h1>
            <p className="text-xs text-grid-muted mt-2">
              Submit an anonymous report. Your IP is hashed and never stored in plain text.
              Reports are reviewed by grid operators within 24 hours.
            </p>
          </div>
          <ComplaintSubmissionForm />
        </div>
      </main>
    </div>
  );
}
