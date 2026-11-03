import { useCallback, useEffect, useState } from 'react'
import {
  changePassword as apiChangePassword,
  deleteAccount as apiDeleteAccount,
  login as apiLogin,
  logout as apiLogout,
  logoutEverywhere as apiLogoutEverywhere,
  isPendingSignUp,
  register as apiRegister,
  restoreSession,
  type User,
} from '@/lib/api/auth'
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

  /** T56: 'check-inbox' when the address must be confirmed first — nobody is signed in yet. */
  const register = useCallback(
    async (email: string, password: string, displayName: string): Promise<'signed-in' | 'check-inbox'> => {
      const result = await apiRegister({ email, password, displayName })
      if (isPendingSignUp(result)) return 'check-inbox'
      setState({ status: 'signedIn', user: result })
      return 'signed-in'
    },
    [],
  )

  /** T56: an emailed link confirmed the address or set a new password, and the server signed this browser in. */
  const signedIn = useCallback((user: User) => setState({ status: 'signedIn', user }), [])

  /**
   * BUG-160: signed out here only once the server has done it. The session's cookies are HttpOnly: only the server can
   * clear them, so a sign-out that failed and looked done left the next reload signed in — on a shared computer, open
   * to whoever came next. A failure is thrown for the menu to say so and offer another try.
   */
  const logout = useCallback(async () => {
    await apiLogout()
    setState({ status: 'signedOut' })
  }, [])

  /** Every device, this one included (DEC-064's account menu). As above: said done only when it is. */
  const logoutEverywhere = useCallback(async () => {
    await apiLogoutEverywhere()
    setState({ status: 'signedOut' })
  }, [])

  /** The server renews this session's cookies and signs out every other one. */
  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    const user = await apiChangePassword({ currentPassword, newPassword })
    setState({ status: 'signedIn', user })
  }, [])

  /** BUG-120: the account is gone, so this session is too. Only on success: a wrong password keeps them here. */
  const deleteAccount = useCallback(async (password: string) => {
    await apiDeleteAccount(password)
    setState({ status: 'signedOut' })
  }, [])

  return { state, login, register, signedIn, logout, logoutEverywhere, changePassword, deleteAccount }
}
