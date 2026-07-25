import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { AuthProvider } from './auth/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';

const Dashboard   = lazy(() => import('./pages/Dashboard'));
const PublicSubmit = lazy(() => import('./pages/PublicSubmit'));
const Login       = lazy(() => import('./pages/Login'));

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
            <Route path="/login"  element={<Login />} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <Dashboard />
                </ProtectedRoute>
              }
            />
            <Route
              path="/report"
              element={<PublicSubmit />}
            />
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}
