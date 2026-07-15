import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios';

// ── Dev JWT (same as k6 load test token) ────────────────────
// Replace with real auth flow once /auth/login endpoint is implemented
const DEV_TOKEN = 'eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiZGV2aWNlLXNpbXVsYXRvciIsICJ0ZW5hbnRfaWQiOiAxLCAicm9sZXMiOiBbIm9wZXJhdG9yIl0sICJleHAiOiAxODgyNzI4MDAwfQ.c2lnbmF0dXJl';

function getToken(): string | null {
  return localStorage.getItem('voltix_token') ?? DEV_TOKEN;
}

function clearToken(): void {
  localStorage.removeItem('voltix_token');
}

// ── Axios instance ───────────────────────────────────────────
const client: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
});

// Request interceptor — inject Bearer token on every call
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// Response interceptor — handle 401 gracefully
client.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearToken();
      // In production: redirect to /login
      console.warn('[VoltiX] Unauthorized — token cleared');
    }
    return Promise.reject(error);
  },
);

export default client;
