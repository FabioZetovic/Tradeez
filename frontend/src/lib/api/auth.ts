import { apiFetch, ApiError } from './client'

/**
 * Identity calls.
 *
 * DEC-032 — note what this module does *not* export: there is no `getToken`, no `setToken`, and
 * nothing touches `localStorage`. Tokens live in HttpOnly cookies that this code cannot read, which
 * is the point. "Am I signed in?" is answered by asking the server (`getMe`), not by inspecting
 * client state.
 */

export interface User {
  id: string
  email: string
  displayName: string
  createdAt: string
}

export interface RegisterInput {
  email: string
  password: string
  displayName: string
}

export interface LoginInput {
  email: string
  password: string
}

/** T56: a sign-up waits for its address to be confirmed — nobody is signed in until the emailed link is opened. */
export interface PendingSignUp {
  verifyEmail: true
  email: string
  message: string
}

/** A user where confirmation is switched off (some backend tests), otherwise a sign-up waiting for its email. */
export const register = (input: RegisterInput) =>
  apiFetch<User | PendingSignUp>('/api/auth/register', { method: 'POST', body: JSON.stringify(input) })

export const isPendingSignUp = (result: User | PendingSignUp): result is PendingSignUp => 'verifyEmail' in result

const post = <T>(path: string, body: object) => apiFetch<T>(path, { method: 'POST', body: JSON.stringify(body) })

/** T56: the emailed link's token, which confirms the address and signs in. */
export const verifyEmail = (token: string) => post<User>('/api/auth/verify', { token })

/** T56: the link again — with the account's password. The answer never says whether it went. */
export const resendVerification = (email: string, password: string) =>
  post<void>('/api/auth/verify/resend', { email, password })

/** T56: a link to set a new password, if the address has an account. The answer never says. */
export const forgotPassword = (email: string) => post<void>('/api/auth/password/forgot', { email })

/** T56: a new password with the emailed link's token; every other session ends, this one starts. */
export const resetPassword = (token: string, newPassword: string) =>
  post<User>('/api/auth/password/reset', { token, newPassword })

/** The server's code for a failure it wants the page to act on (T56: EMAIL_NOT_VERIFIED). */
export function errorCode(error: unknown): string | undefined {
  return error instanceof ApiError && typeof error.body === 'object' && error.body !== null
    ? (error.body as { code?: string }).code
    : undefined
}

export const login = (input: LoginInput) =>
  apiFetch<User>('/api/auth/login', { method: 'POST', body: JSON.stringify(input) })

export const logout = () => apiFetch<void>('/api/auth/logout', { method: 'POST' })

/** Every session of the account, this one included (DEC-032's logout-all). */
export const logoutEverywhere = () => apiFetch<void>('/api/auth/logout-all', { method: 'POST' })

/**
 * A new password (DEC-064). The server signs out every other session and renews this one's cookies; a
 * wrong current password is a 403, a weak new one a 400 naming every problem.
 */
export const changePassword = (input: { currentPassword: string; newPassword: string }) =>
  apiFetch<User>('/api/auth/password', { method: 'POST', body: JSON.stringify(input) })

/** Everything the app holds about the signed-in account, as the server writes it (BUG-120). */
export const exportAccount = () => apiFetch<Record<string, unknown>>('/api/me/export')

/** The account and all of it, for good (BUG-120). A wrong password is a 403. */
export const deleteAccount = (password: string) =>
  apiFetch<void>('/api/me', { method: 'DELETE', body: JSON.stringify({ password }) })

/** The server's words for a failed request — its own message, unrephrased — or a fallback when it said none. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && typeof error.body === 'object' && error.body !== null) {
    return (error.body as { message?: string }).message ?? 'Something went wrong.'
  }
  return 'Could not reach the server.'
}

const getMe = () => apiFetch<User>('/api/me')

/**
 * Resolves the current session on page load.
 *
 * The access token expires long before the refresh token does, so a returning user will usually
 * fail `getMe` the first time; `apiFetch` then refreshes and asks again (BUG-040). A 401 that
 * survives that means genuinely signed out.
 */
export async function restoreSession(): Promise<User | null> {
  try {
    return await getMe()
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}
