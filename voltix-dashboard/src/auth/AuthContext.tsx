import React, { createContext, useContext, useState, useCallback, useEffect, useMemo } from 'react';
import client, { setAuthToken, setUnauthorizedHandler } from '../api/client';
import { decodeJwt, isTokenValid, type JwtClaims } from './jwt';

export interface AuthUser {
  username: string;
  roles:    string[];
  tenantId?: number;
}

interface AuthContextValue {
  token:           string | null;
  user:            AuthUser | null;
  isAuthenticated: boolean;
  login:  (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function claimsToUser(claims: JwtClaims | null): AuthUser | null {
  if (!claims) return null;
  return { username: claims.sub, roles: claims.roles, tenantId: claims.tenant_id };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // Token lives only in memory (React state + the axios module holder).
  const [token, setToken] = useState<string | null>(null);

  const user = useMemo(() => claimsToUser(token ? decodeJwt(token) : null), [token]);
  const isAuthenticated = isTokenValid(token);

  const logout = useCallback(() => {
    setToken(null);
    setAuthToken(null);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    // The login call itself needs no auth header.
    const res = await client.post<{ token: string }>('/auth/login', { username, password });
    const newToken = res.data?.token;
    if (!newToken || !isTokenValid(newToken)) {
      throw new Error('Login succeeded but no valid token was returned.');
    }
    setAuthToken(newToken); // make it available to the axios interceptor immediately
    setToken(newToken);
  }, []);

  // If any authenticated request comes back 401, drop the session.
  useEffect(() => {
    setUnauthorizedHandler(() => setToken(null));
    return () => setUnauthorizedHandler(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, isAuthenticated, login, logout }),
    [token, user, isAuthenticated, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
