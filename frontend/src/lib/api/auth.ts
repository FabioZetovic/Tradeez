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

export const register = (input: RegisterInput) =>
  apiFetch<User>('/api/auth/register', { method: 'POST', body: JSON.stringify(input) })

export const login = (input: LoginInput) =>
  apiFetch<User>('/api/auth/login', { method: 'POST', body: JSON.stringify(input) })

export const logout = () => apiFetch<void>('/api/auth/logout', { method: 'POST' })

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
