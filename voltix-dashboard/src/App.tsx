import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { AuthProvider } from './auth/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AppShell } from './app/layout/AppShell';

const Home        = lazy(() => import('./pages/Home'));
const Dashboard   = lazy(() => import('./pages/Dashboard'));
const PublicSubmit = lazy(() => import('./pages/PublicSubmit'));
const Login       = lazy(() => import('./pages/Login'));
const DesignPreview = lazy(() => import('./pages/DesignPreview'));

const Alerts      = lazy(() => import('./pages/Alerts'));
const Forecasts   = lazy(() => import('./pages/Forecasts'));
const Complaints  = lazy(() => import('./pages/Complaints'));

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
      <AuthProvider>
        <Suspense fallback={<LoadingFallback />}>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/report" element={<PublicSubmit />} />
            <Route path="/design" element={<DesignPreview />} />

            {/* Protected application shell */}
            <Route
              path="/dashboard/*"
              element={
                <ProtectedRoute>
                  <AppShell />
                </ProtectedRoute>
              }
            >
              <Route index element={<Dashboard />} />
              <Route path="alerts" element={<Alerts />} />
              <Route path="forecasts" element={<Forecasts />} />
              <Route path="complaints" element={<Complaints />} />
            </Route>
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}