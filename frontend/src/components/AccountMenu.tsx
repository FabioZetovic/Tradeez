import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { errorMessage, type User } from '@/lib/api/auth'
import { passwordRules } from '@/lib/password'
import { errorClass, fieldClass, labelClass } from '@/lib/styles'
import { PasswordRules } from './PasswordRules'
import { Button } from './ui'

interface AccountMenuProps {
  user: User
  onChangePassword: (currentPassword: string, newPassword: string) => Promise<void>
  onLogoutEverywhere: () => Promise<void>
}

/**
 * The signed-in account (DEC-064): change the password, or sign out of every device. Opens under the
 * display name in the header; Escape or a click elsewhere closes it.
 */
export function AccountMenu({ user, onChangePassword, onLogoutEverywhere }: AccountMenuProps) {
  const [open, setOpen] = useState(false)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const rulesId = useId()

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    const onClick = (event: MouseEvent) => {
      if (root.current && !root.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onClick)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onClick)
    }
  }, [open])

  const ready = current.length > 0 && passwordRules(next, user.email, user.displayName).every((rule) => rule.met)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setDone(false)
    setBusy(true)
    try {
      await onChangePassword(current, next)
      setCurrent('')
      setNext('')
      setDone(true)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div ref={root} className="relative">
      <Button
        variant="ghost"
        size="sm"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        {user.displayName}
      </Button>
      {open && (
        <div
          role="dialog"
          aria-label="Account"
          className="absolute top-full right-0 z-30 mt-2 flex w-80 flex-col gap-4 rounded-xl border border-line bg-surface p-4 shadow-2xl shadow-black/40"
        >
          <p className="text-xs text-subtle">
            Signed in as <span className="text-ink">{user.email}</span>
          </p>
          <form onSubmit={submit} className="flex flex-col gap-3" aria-label="Change password">
            <h2 className="text-sm font-semibold text-ink">Change password</h2>
            <label className={labelClass}>
              Current password
              <input
                className={fieldClass}
                type="password"
                value={current}
                onChange={(e) => setCurrent(e.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
            <div className="flex flex-col gap-1.5">
              <label className={labelClass}>
                New password
                <input
                  className={fieldClass}
                  type="password"
                  value={next}
                  onChange={(e) => setNext(e.target.value)}
                  autoComplete="new-password"
                  aria-describedby={rulesId}
                  required
                />
              </label>
              <PasswordRules id={rulesId} password={next} email={user.email} displayName={user.displayName} />
            </div>
            {error && (
              <p role="alert" className={errorClass}>
                {error}
              </p>
            )}
            {done && (
              <p role="status" className="text-sm text-up">
                Password changed. Every other device was signed out.
              </p>
            )}
            <Button type="submit" variant="primary" disabled={busy || !ready}>
              {busy ? 'Working…' : 'Change password'}
            </Button>
          </form>
          <div className="border-t border-line pt-3">
            <Button variant="ghost" size="sm" className="w-full" onClick={() => void onLogoutEverywhere()}>
              Sign out of every device
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
