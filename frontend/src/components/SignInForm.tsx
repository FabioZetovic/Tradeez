import { useId, useState, type FormEvent } from 'react'
import { errorMessage } from '@/lib/api/auth'
import { PasswordRules } from './PasswordRules'
import { Button } from './ui'
import { errorClass, fieldClass, labelClass } from '@/lib/styles'

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
  const rulesId = useId()

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
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="w-full max-w-sm rounded-xl border border-line bg-surface p-6 shadow-2xl shadow-black/40">
      <h2 className="mb-5 text-lg font-semibold text-ink">{isRegister ? 'Create an account' : 'Sign in'}</h2>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {isRegister && (
          <label className={labelClass}>
            Display name
            <input
              className={fieldClass}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              maxLength={60}
            />
          </label>
        )}

        <label className={labelClass}>
          Email
          <input
            className={fieldClass}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="username"
          />
        </label>

        <div className="flex flex-col gap-1.5">
          <label className={labelClass}>
            Password
            <input
              className={fieldClass}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={isRegister ? 12 : undefined}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              aria-describedby={isRegister ? rulesId : undefined}
            />
          </label>
          {isRegister && <PasswordRules id={rulesId} password={password} email={email} displayName={displayName} />}
        </div>

        {error && (
          <p role="alert" className={errorClass}>
            {error}
          </p>
        )}

        <Button type="submit" variant="primary" size="lg" disabled={busy} className="mt-1 w-full">
          {busy ? 'Working…' : isRegister ? 'Create account' : 'Sign in'}
        </Button>
      </form>

      <Button
        variant="link"
        className="mt-4 w-full"
        onClick={() => {
          setMode(isRegister ? 'login' : 'register')
          setError(null)
        }}
      >
        {isRegister ? 'I already have an account' : 'Create an account instead'}
      </Button>
    </section>
  )
}
