import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, setSessionExpiredHandler } from './client'

/**
 * BUG-040 — the access token expires after fifteen minutes. Every request must survive that by
 * refreshing once and trying again, and only a refused refresh may sign the user out.
 */

interface Sent {
  method: string
  url: string
  body: unknown
  csrf: string | null
}

type Reply = { status: number; body?: unknown } | 'network-error'

/** A fetch that answers each request from `reply` and records what was sent. */
function mockFetch(reply: (sent: Sent, index: number) => Reply | Promise<Reply>) {
  const sent: Sent[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit) => {
      const request: Sent = {
        method: init.method ?? 'GET',
        url,
        body: init.body,
        csrf: new Headers(init.headers).get('X-XSRF-TOKEN'),
      }
      sent.push(request)
      const answer = await reply(request, sent.length - 1)
      if (answer === 'network-error') throw new TypeError('Failed to fetch')
      return {
        ok: answer.status < 400,
        status: answer.status,
        statusText: String(answer.status),
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => answer.body ?? {},
        text: async () => JSON.stringify(answer.body ?? {}),
      }
    }),
  )
  return sent
}

const isRefresh = (s: Sent) => s.url.endsWith('/api/auth/refresh')

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('apiFetch after the access token expires (BUG-040)', () => {
  it('refreshes once and sends the request again', async () => {
    let refreshed = false
    const sent = mockFetch((s) => {
      if (isRefresh(s)) {
        refreshed = true
        return { status: 200 }
      }
      return refreshed ? { status: 200, body: { ok: true } } : { status: 401 }
    })

    await expect(apiFetch('/api/practice/sessions')).resolves.toEqual({ ok: true })
    expect(sent.map((s) => s.url)).toEqual([
      '/api/practice/sessions',
      '/api/auth/refresh',
      '/api/practice/sessions',
    ])
  })

  it('lets requests that fail together share one refresh', async () => {
    let refreshed = false
    let releaseRefresh: () => void = () => {}
    const sent = mockFetch((s) => {
      if (isRefresh(s)) {
        return new Promise((resolve) => {
          releaseRefresh = () => {
            refreshed = true
            resolve({ status: 200 })
          }
        })
      }
      return refreshed ? { status: 200, body: s.url } : { status: 401 }
    })

    const both = Promise.all([apiFetch('/api/a'), apiFetch('/api/b')])
    await vi.waitFor(() => expect(sent.filter(isRefresh)).toHaveLength(1))
    releaseRefresh()

    await expect(both).resolves.toEqual(['/api/a', '/api/b'])
    expect(sent.filter(isRefresh)).toHaveLength(1)
  })

  it('sends the body again, with the CSRF cookie as it is after the refresh', async () => {
    document.cookie = 'XSRF-TOKEN=before'
    let refreshed = false
    const sent = mockFetch((s) => {
      if (isRefresh(s)) {
        refreshed = true
        document.cookie = 'XSRF-TOKEN=after'
        return { status: 200 }
      }
      return refreshed ? { status: 200 } : { status: 401 }
    })

    await apiFetch('/api/practice/sessions/x/plan', { method: 'POST', body: '{"contracts":1}' })
    const [first, , retry] = sent
    expect(first.csrf).toBe('before')
    expect(retry.csrf).toBe('after')
    expect(retry.body).toBe('{"contracts":1}')
  })

  it('reports the session over when the refresh itself is refused', async () => {
    const expired = vi.fn()
    const unregister = setSessionExpiredHandler(expired)
    mockFetch(() => ({ status: 401 }))

    const error = await apiFetch('/api/me').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(expired).toHaveBeenCalledTimes(1)
    unregister()
  })

  it('does not sign anyone out over a network failure or a server error during the refresh', async () => {
    const expired = vi.fn()
    const unregister = setSessionExpiredHandler(expired)

    mockFetch((s) => (isRefresh(s) ? 'network-error' : { status: 401 }))
    await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 401 })
    mockFetch((s) => (isRefresh(s) ? { status: 503 } : { status: 401 }))
    await expect(apiFetch('/api/me')).rejects.toMatchObject({ status: 401 })

    expect(expired).not.toHaveBeenCalled()
    unregister()
  })

  it('refreshes for an endpoint that needs an access token, even under /api/auth', async () => {
    let refreshed = false
    const sent = mockFetch((s) => {
      if (isRefresh(s)) {
        refreshed = true
        return { status: 200 }
      }
      return refreshed ? { status: 204 } : { status: 401 }
    })
    await apiFetch('/api/auth/logout-all', { method: 'POST' })
    expect(sent.filter(isRefresh)).toHaveLength(1)
  })

  it('never refreshes for the auth endpoints: a 401 from sign-in is a wrong password', async () => {
    const sent = mockFetch(() => ({ status: 401, body: { message: 'Invalid email or password.' } }))
    await expect(apiFetch('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      status: 401,
    })
    expect(sent).toHaveLength(1)
  })
})
