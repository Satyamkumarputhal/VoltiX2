// ============================================================
// Minimal JWT claim decoder (no verification — the backend signs
// and verifies; the client only reads claims for display + expiry).
// ============================================================

export interface JwtClaims {
  sub:        string;   // username
  roles:      string[]; // e.g. ["OPERATOR"]
  tenant_id?: number;
  exp?:       number;   // seconds since epoch
}

/**
 * Decode the payload segment of a JWT without verifying its signature.
 * Returns null if the token is malformed.
 */
export function decodeJwt(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    // base64url → base64
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    const raw = JSON.parse(json) as Record<string, unknown>;
    return {
      sub:       String(raw.sub ?? ''),
      roles:     Array.isArray(raw.roles) ? (raw.roles as string[]) : [],
      tenant_id: typeof raw.tenant_id === 'number' ? raw.tenant_id : undefined,
      exp:       typeof raw.exp === 'number' ? raw.exp : undefined,
    };
  } catch {
    return null;
  }
}

/**
 * A token is considered valid for client-side session purposes if it
 * decodes cleanly and its `exp` claim (if present) is in the future.
 */
export function isTokenValid(token: string | null): boolean {
  if (!token) return false;
  const claims = decodeJwt(token);
  if (!claims) return false;
  if (claims.exp && claims.exp * 1000 <= Date.now()) return false;
  return true;
}
