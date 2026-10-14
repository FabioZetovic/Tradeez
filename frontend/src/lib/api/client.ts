/**
 * Minimal typed API client.
 *
 * DEC-007 — request/response *shapes* come from the generated OpenAPI types
 * (`npm run api:types`, output `schema.d.ts`, which is gitignored and regenerated). Nothing in
 * this file should hand-declare a server payload. This module owns only transport concerns.
 *
 * DEC-032 — authentication rides on HttpOnly cookies, so every request sets
 * `credentials: 'include'` and echoes the CSRF cookie back on mutating verbs. There is
 * deliberately no token-in-localStorage path here for anyone to reach for.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

export class ApiError extends Error {
  // Declared as fields rather than constructor parameter properties: the tsconfig enables
  // `erasableSyntaxOnly`, which rejects any TS syntax that emits runtime code.
  readonly status: number
  readonly statusText: string
  readonly body: unknown

  constructor(status: number, statusText: string, body: unknown) {
    super(`${status} ${statusText}`)
    this.name = 'ApiError'
    this.status = status
    this.statusText = statusText
    this.body = body
  }
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.split('=')
    .slice(1)
    .join('=')
}

/**
 * BUG-040 — the access token lives fifteen minutes, the refresh token thirty days. A request refused
 * with 401 refreshes the session once and is sent again, so the app keeps working past its first
 * quarter of an hour. Requests that fail while a refresh is under way wait for that one rather than
 * starting their own: two refreshes racing is how a user got signed out before (BUG-034).
 *
 * Sign-in, registration, refresh and logout are never retried: none of them uses an access token, so
 * their 401 is an answer — a wrong password, or a session that is over.
 */
type RefreshOutcome = 'refreshed' | 'expired' | 'failed'

let refreshing: Promise<RefreshOutcome> | null = null
let onSessionExpired: (() => void) | null = null

/**
 * Who to tell when the session is over — the refresh token itself was refused — so the app can ask
 * the user to sign in again instead of failing every request. Returns the unregister function.
 */
export function setSessionExpiredHandler(handler: () => void): () => void {
  onSessionExpired = handler
  return () => {
    if (onSessionExpired === handler) onSessionExpired = null
  }
}

/** Endpoints whose 401 is an answer, not an expired access token: they never use one. */
const NO_REFRESH = new Set(['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout'])

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await send(path, init)
  if (response.status === 401 && !NO_REFRESH.has(path)) {
    const outcome = await refreshSession()
    if (outcome === 'refreshed') return parse<T>(await send(path, init))
    // A network failure is not a verdict on the session: only a refused refresh signs the user out.
    if (outcome === 'expired') onSessionExpired?.()
  }
  return parse<T>(response)
}

function refreshSession(): Promise<RefreshOutcome> {
  refreshing ??= send('/api/auth/refresh', { method: 'POST' })
    // Only a refusal ends the session. A server error is a bad moment, not a verdict.
    .then((response): RefreshOutcome =>
      response.ok ? 'refreshed' : response.status === 401 || response.status === 403 ? 'expired' : 'failed',
    )
    .catch((): RefreshOutcome => 'failed')
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

/** One request. The CSRF header is read fresh each time: a retry may follow a rotated cookie. */
function send(path: string, init: RequestInit): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (!SAFE_METHODS.has(method)) {
    const csrf = readCookie(CSRF_COOKIE)
    if (csrf) headers.set(CSRF_HEADER, decodeURIComponent(csrf))
  }

  return fetch(`${BASE_URL}${path}`, {
    ...init,
    method,
    headers,
    credentials: 'include',
  })
}

async function parse<T>(response: Response): Promise<T> {
  const payload = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, payload)
  }

  return payload as T
}
