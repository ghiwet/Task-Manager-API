const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

// The auth layer supplies the token via setTokenProvider; the client stays decoupled from it.
let tokenProvider: () => string | undefined = () => undefined;

export function setTokenProvider(fn: () => string | undefined): void {
  tokenProvider = fn;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  // Only set Content-Type when there's a body; on a bodyless GET/DELETE it would force an extra preflight.
  if (init.body != null) {
    headers.set('Content-Type', 'application/json');
  }
  const token = tokenProvider();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new ApiError(res.status, body || res.statusText);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}
