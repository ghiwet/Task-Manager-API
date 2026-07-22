// Keycloak puts realm roles in `realm_access.roles` on the access token, so decode it to tell whether
// the signed-in user is an admin (the id-token profile doesn't carry them).
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
