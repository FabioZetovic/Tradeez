import { useCallback, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ArrowLeft, ChevronDown, CircleHelp, KeyRound, LogOut, MonitorOff } from 'lucide-react'
import { errorMessage, type User } from '@/lib/api/auth'
import { passwordRules } from '@/lib/password'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'
import { PasswordRules } from '../PasswordRules'
import { Button, Glass } from '../ui'

interface AccountMenuProps {
  user: User
  onChangePassword: (currentPassword: string, newPassword: string) => Promise<void>
  onLogout: () => Promise<void>
  onLogoutEverywhere: () => Promise<void>
}

/** "Fabio Zetović" → "FZ", "marta" → "MA". */
function initials(name: string): string {
  const words = name.trim().split(/\s+/)
  return (words.length > 1 ? words[0][0] + words[words.length - 1][0] : name.trim().slice(0, 2)).toUpperCase()
}

/**
 * The signed-in account (DEC-064, T36): who is signed in, change the password, the guide, sign out here or on
 * every device. Opens under the name in the header; Escape or a click elsewhere closes it.
 */
export function AccountMenu({ user, onChangePassword, onLogout, onLogoutEverywhere }: AccountMenuProps) {
  const [open, setOpen] = useState(false)
  const [view, setView] = useState<'menu' | 'password'>('menu')
  const root = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const close = useCallback(() => {
    setOpen(false)
    setView('menu')
  }, [])

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-haspopup="dialog"
        aria-label={user.displayName}
        aria-expanded={open}
        onClick={() => (open ? close() : setOpen(true))}
        className="flex cursor-pointer items-center gap-2 rounded-lg py-1.5 pr-2 pl-1.5 text-sm text-ink transition-colors hover:bg-sunken focus-visible:outline-2 focus-visible:outline-accent pointer-coarse:min-h-11"
      >
        <span aria-hidden className="grid size-7 place-items-center rounded-full bg-accent/16 text-xs font-bold text-accent">
          {initials(user.displayName)}
        </span>
        {/* Beside the full nav a long name wrapped onto two lines on a tablet (BUG-116): shown from a laptop up, cut short. */}
        <span className="hidden max-w-40 truncate whitespace-nowrap lg:inline">{user.displayName}</span>
        <ChevronDown aria-hidden className={cx('size-4 text-subtle transition-transform', open && 'rotate-180')} />
      </button>
      <Glass open={open} onClose={close} anchor={root} role="dialog" aria-label="Account" className="w-72 p-1.5">
        <div className="px-3 pt-2 pb-2.5">
          <p className="text-sm font-medium text-ink">{user.displayName}</p>
          <p className="text-xs text-subtle">
            Signed in as <span className="text-ink">{user.email}</span>
          </p>
        </div>
        <div className="border-t border-line pt-1.5">
          {view === 'menu' ? (
            <>
              <MenuRow icon={<KeyRound />} onClick={() => setView('password')}>
                Change password
              </MenuRow>
              <MenuRow
                icon={<CircleHelp />}
                onClick={() => {
                  close()
                  void navigate('/help')
                }}
              >
                How it works
              </MenuRow>
              <MenuRow icon={<LogOut />} onClick={() => void onLogout()}>
                Sign out
              </MenuRow>
              <MenuRow icon={<MonitorOff />} danger onClick={() => void onLogoutEverywhere()}>
                Sign out of every device
              </MenuRow>
            </>
          ) : (
            <PasswordForm user={user} onChangePassword={onChangePassword} onBack={() => setView('menu')} />
          )}
        </div>
      </Glass>
    </div>
  )
}

function MenuRow({
  icon,
  danger = false,
  onClick,
  children,
}: {
  icon: ReactNode
  danger?: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        'flex w-full cursor-pointer items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-sunken',
        'focus-visible:outline-2 focus-visible:outline-accent [&_svg]:size-4 [&_svg]:shrink-0',
        danger ? 'text-down' : 'text-ink [&_svg]:text-subtle',
      )}
    >
      {icon}
      {children}
    </button>
  )
}

function PasswordForm({
  user,
  onChangePassword,
  onBack,
}: Pick<AccountMenuProps, 'user' | 'onChangePassword'> & { onBack: () => void }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)
  const rulesId = useId()

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
    <form onSubmit={submit} className="flex flex-col gap-3 p-2" aria-label="Change password">
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon" aria-label="Back" onClick={onBack}>
          <ArrowLeft aria-hidden className="size-4" />
        </Button>
        <h2 className="text-sm font-semibold text-ink">Change password</h2>
      </div>
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
  )
}
