import { useState, type FormEvent } from 'react'
import { ApiError } from '@/lib/api/client'

interface SignInFormProps {
  onLogin: (email: string, password: string) => Promise<void>
  onRegister: (email: string, password: string, displayName: string) => Promise<void>
}

/**
 * Sign in or create an account.
 *
 * Nothing here stores a token — there is no token to store. The server sets HttpOnly cookies the
 * page cannot read (DEC-032), so this component's only job is to collect credentials and let the
 * session hook ask the server who is signed in.
 */
export function SignInForm({ onLogin, onRegister }: SignInFormProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const isRegister = mode === 'register'

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (isRegister) {
        await onRegister(email, password, displayName)
      } else {
        await onLogin(email, password)
      }
    } catch (e) {
      // Surface the server's wording rather than inventing our own: the backend deliberately
      // returns the same message for a wrong password and an unknown account so the API cannot be
      // used to discover which addresses are registered. Rephrasing here could undo that.
      setError(
        e instanceof ApiError && typeof e.body === 'object' && e.body !== null
          ? ((e.body as { message?: string }).message ?? 'Something went wrong.')
          : 'Could not reach the server.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel panel--narrow">
      <h2>{isRegister ? 'Create an account' : 'Sign in'}</h2>

      <form onSubmit={handleSubmit} className="form">
        {isRegister && (
          <label>
            Display name
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              maxLength={60}
            />
          </label>
        )}

        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="username"
          />
        </label>

        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={isRegister ? 12 : undefined}
            autoComplete={isRegister ? 'new-password' : 'current-password'}
          />
          {isRegister && <small>At least 12 characters. Length beats complexity.</small>}
        </label>

        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}

        <button type="submit" disabled={busy}>
          {busy ? 'Working…' : isRegister ? 'Create account' : 'Sign in'}
        </button>
      </form>

      <button
        type="button"
        className="link"
        onClick={() => {
          setMode(isRegister ? 'login' : 'register')
          setError(null)
        }}
      >
        {isRegister ? 'I already have an account' : 'Create an account instead'}
      </button>
    </section>
  )
}
