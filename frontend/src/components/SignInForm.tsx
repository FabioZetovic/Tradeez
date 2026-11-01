import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Eye, EyeOff } from 'lucide-react'
import { useIsPhone } from '@/hooks/useIsPhone'
import { errorCode, errorMessage, resendVerification } from '@/lib/api/auth'
import { PasswordRules } from './PasswordRules'
import { Button } from './ui'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'

interface SignInFormProps {
  /** Which page this is: /sign-in or /sign-up (T36). */
  mode: 'login' | 'register'
  onLogin: (email: string, password: string) => Promise<void>
  /** T56: 'check-inbox' when the account waits for its address to be confirmed. */
  onRegister: (email: string, password: string, displayName: string) => Promise<'signed-in' | 'check-inbox' | void>
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
  // T56: the sign-up went through and a link is on its way; or sign-in found the address unconfirmed.
  const [checkInbox, setCheckInbox] = useState(false)
  const [unconfirmed, setUnconfirmed] = useState(false)
  const [resent, setResent] = useState(false)
  const rulesId = useId()

  const isRegister = mode === 'register'

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setUnconfirmed(false)
    setResent(false)
    setBusy(true)
    try {
      if (isRegister) {
        if ((await onRegister(email, password, displayName)) === 'check-inbox') setCheckInbox(true)
      } else {
        await onLogin(email, password)
      }
    } catch (e) {
      setUnconfirmed(errorCode(e) === 'EMAIL_NOT_VERIFIED')
      // Surface the server's wording rather than inventing our own: the backend deliberately
      // returns the same message for a wrong password and an unknown account so the API cannot be
      // used to discover which addresses are registered. Rephrasing here could undo that.
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  /** T56: the link again. The answer never says whether it went — nor does this. */
  async function sendAgain() {
    setResent(false)
    try {
      await resendVerification(email, password)
    } finally {
      setResent(true)
    }
  }

  if (checkInbox) {
    return (
      <section className={phone ? 'w-full' : 'w-full rounded-card border border-line bg-surface p-7 shadow-panel'}>
        <h2 className="font-display text-2xl font-bold tracking-tight text-ink">Check your inbox</h2>
        <p className="mt-3 text-sm text-subtle">
          We sent a link to <span className="font-medium text-ink">{email}</span>. Open it within 24 hours to confirm the
          address is yours — then you are signed in.
        </p>
        <p className="mt-3 text-sm text-subtle">Nothing there? Look in spam, or send it again.</p>
        <Button className="mt-5 w-full" onClick={() => void sendAgain()}>
          Send the link again
        </Button>
        {resent && (
          <p role="status" className="mt-3 text-sm text-subtle">
            If that address has an account waiting, a new link is on its way.
          </p>
        )}
      </section>
    )
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
        {unconfirmed && (
          <div className="flex flex-col gap-2">
            <Button onClick={() => void sendAgain()}>Send the link again</Button>
            {resent && (
              <p role="status" className="text-sm text-subtle">
                A new link is on its way to {email}.
              </p>
            )}
          </div>
        )}

        <Button type="submit" variant="primary" size="lg" disabled={busy} className="mt-1 w-full">
          {busy ? 'Working…' : isRegister ? 'Create account' : 'Sign in'}
        </Button>
      </form>

      {!isRegister && (
        <Link
          to="/forgot-password"
          className="mt-4 block text-center text-sm text-subtle underline-offset-4 hover:text-ink hover:underline focus-visible:outline-2 focus-visible:outline-accent pointer-coarse:py-3"
        >
          Forgot your password?
        </Link>
      )}
      <Link
        to={isRegister ? '/sign-in' : '/sign-up'}
        className="mt-5 block text-center text-sm text-accent underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-accent pointer-coarse:py-3"
      >
        {isRegister ? 'Sign in instead' : 'Create an account instead'}
      </Link>
    </section>
  )
}
