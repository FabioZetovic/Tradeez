import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { errorMessage, forgotPassword, resetPassword, verifyEmail, type User } from '@/lib/api/auth'
import { useIsPhone } from '@/hooks/useIsPhone'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'
import { PasswordRules } from './PasswordRules'
import { Button } from './ui'

/**
 * The token an emailed link carries after its "#" (T56) — a part of the address the browser never sends to a server —
 * read once and wiped from the address bar, so it is not left in the history.
 */
function useLinkToken(): string | null {
  const [token] = useState(() => {
    const value = window.location.hash.replace(/^#/, '')
    if (value) window.history.replaceState(window.history.state, '', window.location.pathname)
    return value || null
  })
  return token
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  const phone = useIsPhone()
  return (
    <section className={phone ? 'w-full' : 'w-full rounded-card border border-line bg-surface p-7 shadow-panel'}>
      <h2 className="font-display text-2xl font-bold tracking-tight text-ink">{title}</h2>
      {children}
    </section>
  )
}

const linkClass = 'text-accent underline-offset-4 hover:underline'

/** /verify-email#token: confirms the address and signs in, then the app opens on Home. */
export function VerifyEmail({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const token = useLinkToken()
  const [error, setError] = useState<string | null>(token ? null : 'This link is incomplete. Open it from the email again.')
  // A link works once: React's development double effect must not spend it twice.
  const sent = useRef(false)

  useEffect(() => {
    if (!token || sent.current) return
    sent.current = true
    verifyEmail(token)
      .then(onSignedIn)
      .catch((e: unknown) => setError(errorMessage(e)))
  }, [token, onSignedIn])

  return (
    <Card title={error ? 'That link did not work' : 'Confirming your email…'}>
      {error ? (
        <>
          <p role="alert" className={cx(errorClass, 'mt-3')}>
            {error}
          </p>
          <p className="mt-4 text-sm text-subtle">
            <Link to="/sign-in" className={linkClass}>
              Sign in
            </Link>{' '}
            to have a new one sent, or{' '}
            <Link to="/sign-up" className={linkClass}>
              sign up again
            </Link>
            .
          </p>
        </>
      ) : (
        <p role="status" className="mt-3 text-sm text-subtle">
          One moment.
        </p>
      )}
    </Card>
  )
}

/** /forgot-password: an address in, a link out — if the address has an account. The page never says which. */
export function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await forgotPassword(email)
      setSent(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <Card title="Check your inbox">
        <p role="status" className="mt-3 text-sm text-subtle">
          If an account has the address <span className="font-medium text-ink">{email}</span>, we sent it a link to set a
          new password. It works for an hour.
        </p>
        <Link to="/sign-in" className={cx(linkClass, 'mt-5 block text-center text-sm')}>
          Back to sign in
        </Link>
      </Card>
    )
  }
  return (
    <Card title="Forgot your password?">
      <p className="mt-1 mb-6 text-sm text-subtle">We will email you a link to set a new one.</p>
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4">
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
        {error && (
          <p role="alert" className={errorClass}>
            {error}
          </p>
        )}
        <Button type="submit" variant="primary" size="lg" disabled={busy} className="mt-1 w-full">
          {busy ? 'Sending…' : 'Send the link'}
        </Button>
      </form>
      <Link to="/sign-in" className={cx(linkClass, 'mt-5 block text-center text-sm')}>
        Back to sign in
      </Link>
    </Card>
  )
}

/** /reset-password#token: a new password; every other session ends and this one starts. */
export function ResetPassword({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const token = useLinkToken()
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const rulesId = useId()

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!token) return
    setBusy(true)
    setError(null)
    try {
      onSignedIn(await resetPassword(token, password))
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <Card title="That link is incomplete">
        <p className="mt-3 text-sm text-subtle">
          Open it from the email again, or{' '}
          <Link to="/forgot-password" className={linkClass}>
            ask for a new one
          </Link>
          .
        </p>
      </Card>
    )
  }
  return (
    <Card title="Set a new password">
      <p className="mt-1 mb-6 text-sm text-subtle">Every other device you are signed in on will be signed out.</p>
      <form onSubmit={(e) => void submit(e)} className="flex flex-col gap-4">
        <label className={labelClass}>
          New password
          <input
            className={fieldClass}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={12}
            autoComplete="new-password"
            aria-describedby={rulesId}
          />
        </label>
        <PasswordRules id={rulesId} password={password} email="" />
        {error && (
          <p role="alert" className={errorClass}>
            {error}{' '}
            <Link to="/forgot-password" className={linkClass}>
              Ask for a new link
            </Link>
          </p>
        )}
        <Button type="submit" variant="primary" size="lg" disabled={busy} className="mt-1 w-full">
          {busy ? 'Saving…' : 'Set the password'}
        </Button>
      </form>
    </Card>
  )
}
