// Realm roles live in realm_access.roles on the access token (not the id-token profile), so decode it.
function decodePayload(token: string): Record<string, unknown> | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function hasAdminRole(accessToken: string | undefined): boolean {
  if (!accessToken) return false;
  const realmAccess = decodePayload(accessToken)?.['realm_access'] as { roles?: string[] } | undefined;
  return realmAccess?.roles?.includes('ADMIN') ?? false;
}
