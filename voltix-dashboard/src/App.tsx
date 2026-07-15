import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import { Loader2 } from 'lucide-react';

const Dashboard   = lazy(() => import('./pages/Dashboard'));
const PublicSubmit = lazy(() => import('./pages/PublicSubmit'));

function LoadingFallback() {
  return (
    <div className="min-h-screen bg-grid-base flex items-center justify-center gap-2 text-grid-muted">
      <Loader2 className="w-5 h-5 animate-spin" />
      <span className="text-sm">Loading VoltiX…</span>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          <Route path="/"       element={<Dashboard />} />
          <Route path="/report" element={<PublicSubmit />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
