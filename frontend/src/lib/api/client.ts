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

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (!SAFE_METHODS.has(method)) {
    const csrf = readCookie(CSRF_COOKIE)
    if (csrf) headers.set(CSRF_HEADER, decodeURIComponent(csrf))
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    method,
    headers,
    credentials: 'include',
  })

  const payload = response.headers.get('content-type')?.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, payload)
  }

  return payload as T
}
