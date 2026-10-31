import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Eye, EyeOff } from 'lucide-react'
import { useIsPhone } from '@/hooks/useIsPhone'
import { errorMessage } from '@/lib/api/auth'
import { PasswordRules } from './PasswordRules'
import { Button } from './ui'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'

interface SignInFormProps {
  /** Which page this is: /sign-in or /sign-up (T36). */
  mode: 'login' | 'register'
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
export function SignInForm({ mode, onLogin, onRegister }: SignInFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [shown, setShown] = useState(false)
  const phone = useIsPhone()
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
    // On a phone (T53) the form stands on the page, not in a card.
    <section className={phone ? 'w-full' : 'w-full rounded-card border border-line bg-surface p-7 shadow-panel'}>
      <h2 className={cx('font-display text-2xl font-bold tracking-tight text-ink', !isRegister && 'mb-6')}>
        {isRegister ? 'Create an account' : 'Sign in'}
      </h2>
      {isRegister && <p className="mt-1 mb-6 text-sm text-subtle">Practise ICT on two years of real NQ and ES.</p>}

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {isRegister && (
          <label className={labelClass}>
            Display name
            <input
              className={fieldClass}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              maxLength={40}
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
          <div className="relative">
          <label className={labelClass}>
            Password
            <input
              className={cx(fieldClass, 'pr-12')}
              type={shown ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={isRegister ? 12 : undefined}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              aria-describedby={isRegister ? rulesId : undefined}
            />
          </label>
          {/* T53: to check what was typed — on a phone's keyboard most of all. */}
          <button
            type="button"
            aria-label={shown ? 'Hide password' : 'Show password'}
            aria-pressed={shown}
            onClick={() => setShown((s) => !s)}
            className="absolute right-0.5 bottom-0 grid size-10 cursor-pointer place-items-center rounded-lg text-subtle hover:text-ink focus-visible:outline-2 focus-visible:outline-accent pointer-coarse:size-11"
          >
            {shown ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
          </button>
          </div>
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

      <Link
        to={isRegister ? '/sign-in' : '/sign-up'}
        className="mt-5 block text-center text-sm text-accent underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-accent pointer-coarse:py-3"
      >
        {isRegister ? 'Sign in instead' : 'Create an account instead'}
      </Link>
    </section>
  )
}
