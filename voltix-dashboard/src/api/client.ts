import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios';

// ── In-memory bearer token ───────────────────────────────────
// Deliberately NOT persisted to localStorage/sessionStorage to avoid
// exposing the bearer token to XSS. The AuthProvider owns the token
// lifecycle and pushes it here via setAuthToken(); on a full page
// reload the token is gone and the user must log in again.
let authToken: string | null = null;

// Optional callback invoked when the server rejects a token (401),
// so the AuthProvider can clear session state and trigger a redirect.
let onUnauthorized: (() => void) | null = null;

export function setAuthToken(token: string | null): void {
  authToken = token;
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

// ── Axios instance ───────────────────────────────────────────
const client: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
});

// Request interceptor — inject the real Bearer token on every call
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (authToken) {
      config.headers.Authorization = `Bearer ${authToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// Response interceptor — clear session on 401 and notify the app
client.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error) => {
    if (error.response?.status === 401) {
      authToken = null;
      onUnauthorized?.();
    }
    return Promise.reject(error);
  },
);

export default client;
