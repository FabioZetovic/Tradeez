import { useCallback, useEffect, useState } from 'react'
import { login as apiLogin, logout as apiLogout, register as apiRegister, restoreSession, type User }
  from '@/lib/api/auth'
import { setSessionExpiredHandler } from '@/lib/api/client'

type State =
  | { status: 'loading' }
  | { status: 'signedIn'; user: User }
  /** `expired`: signed out by the server while using the app, not by choice (BUG-040). */
  | { status: 'signedOut'; expired?: boolean }

/**
 * Who is signed in.
 *
 * DEC-032 — the answer comes from the server, never from client state. The tokens live in HttpOnly
 * cookies that this code cannot read, so there is no local flag to consult and no way for the UI to
 * believe it is signed in when it is not.
 */
export function useSession() {
  const [state, setState] = useState<State>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    restoreSession()
      .then((user) => {
        if (cancelled) return
        setState(user ? { status: 'signedIn', user } : { status: 'signedOut' })
      })
      .catch(() => {
        if (!cancelled) setState({ status: 'signedOut' })
      })
    return () => {
      cancelled = true
    }
  }, [])

  // The session can end while the app is open — a refresh token revoked elsewhere, or thirty days
  // gone. Show the sign-in form rather than let every request fail. Only from signed in: a visitor
  // who was never signed in is not told their session ended.
  useEffect(
    () =>
      setSessionExpiredHandler(() =>
        setState((current) =>
          current.status === 'signedIn' ? { status: 'signedOut', expired: true } : current,
        ),
      ),
    [],
  )

  const login = useCallback(async (email: string, password: string) => {
    const user = await apiLogin({ email, password })
    setState({ status: 'signedIn', user })
  }, [])

  const register = useCallback(
    async (email: string, password: string, displayName: string) => {
      const user = await apiRegister({ email, password, displayName })
      setState({ status: 'signedIn', user })
    },
    [],
  )

  const logout = useCallback(async () => {
    try {
      await apiLogout()
    } finally {
      // Sign out locally even if the request failed. Leaving the UI in a signed-in state after the
      // user asked to leave is worse than a stale server-side session, which expires anyway.
      setState({ status: 'signedOut' })
    }
  }, [])

  return { state, login, register, logout }
}
