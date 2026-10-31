import { useCallback, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ArrowLeft, CalendarDays, ChevronDown, ChevronRight, CircleHelp, FileLock2, KeyRound, LogOut, MonitorOff, X } from 'lucide-react'
import { errorMessage, type User } from '@/lib/api/auth'
import { passwordRules } from '@/lib/password'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'
import { PasswordRules } from '../PasswordRules'
import { Button, Glass, Sheet } from '../ui'
import { DISCLAIMER_SHORT } from '@/lib/disclaimer'
import { useIsPhone } from '@/hooks/useIsPhone'

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
  const phone = useIsPhone()
  const close = useCallback(() => {
    setOpen(false)
    setView('menu')
  }, [])

  if (phone) {
    // A phone (T53): the avatar opens a sheet from the foot of the screen, with the calendar and the guide in it.
    const go = (to: string) => {
      close()
      void navigate(to)
    }
    return (
      <>
        <button
          type="button"
          aria-haspopup="dialog"
          aria-label={user.displayName}
          aria-expanded={open}
          onClick={() => setOpen(true)}
          className="grid size-11 cursor-pointer place-items-center rounded-xl focus-visible:outline-2 focus-visible:outline-accent"
        >
          <Avatar name={user.displayName} />
        </button>
        <Sheet open={open} onClose={close} label="Account">
          <div className="flex items-center gap-3 px-1 pt-1 pb-4">
            <Avatar name={user.displayName} large />
            <div className="min-w-0 flex-1">
              <p className="text-[17px] font-semibold text-ink">{user.displayName}</p>
              <p className="truncate text-sm text-subtle">{user.email}</p>
            </div>
            <button
              type="button"
              aria-label="Close"
              onClick={close}
              className="grid size-11 cursor-pointer place-items-center rounded-full text-ink focus-visible:outline-2 focus-visible:outline-accent"
            >
              <span className="grid size-9 place-items-center rounded-full bg-raised">
                <X aria-hidden className="size-4" />
              </span>
            </button>
          </div>
          {view === 'menu' ? (
            <>
              <SheetGroup>
                <SheetRow icon={<CalendarDays />} onClick={() => go('/news')}>
                  Economic calendar
                </SheetRow>
                <SheetRow icon={<CircleHelp />} onClick={() => go('/help')}>
                  How Tradeez works
                </SheetRow>
                <SheetRow icon={<FileLock2 />} onClick={() => go('/account')}>
                  Your data
                </SheetRow>
                <SheetRow icon={<KeyRound />} onClick={() => setView('password')}>
                  Change password
                </SheetRow>
              </SheetGroup>
              <SheetGroup className="mt-3">
                <SheetRow icon={<LogOut />} onClick={() => void onLogout()} plain>
                  Sign out
                </SheetRow>
                <SheetRow icon={<MonitorOff />} onClick={() => void onLogoutEverywhere()} plain danger>
                  Sign out of every device
                </SheetRow>
              </SheetGroup>
              <p className="mt-4 text-center text-xs leading-relaxed text-subtle">{DISCLAIMER_SHORT}</p>
            </>
          ) : (
            <PasswordForm user={user} onChangePassword={onChangePassword} onBack={() => setView('menu')} />
          )}
        </Sheet>
      </>
    )
  }

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
        <Avatar name={user.displayName} />
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
              <MenuRow
                icon={<FileLock2 />}
                onClick={() => {
                  close()
                  void navigate('/account')
                }}
              >
                Your data
              </MenuRow>
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

/** The initials on the accent's tint (T36); larger at the head of the phone's sheet. */
function Avatar({ name, large = false }: { name: string; large?: boolean }) {
  return (
    <span
      aria-hidden
      className={cx(
        'grid shrink-0 place-items-center rounded-full bg-accent/16 font-bold text-accent',
        large ? 'size-12 text-base' : 'size-7 text-xs max-md:size-[34px]',
      )}
    >
      {initials(name)}
    </span>
  )
}

/** A group of rows on the phone's account sheet (T53). */
function SheetGroup({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cx('divide-y divide-line overflow-hidden rounded-card border border-line bg-raised/60', className)}>{children}</div>
}

/** One row of the account sheet: 56px to touch; a destination gets a chevron, an action (sign out) none. */
function SheetRow({
  icon,
  plain = false,
  danger = false,
  onClick,
  children,
}: {
  icon: ReactNode
  plain?: boolean
  danger?: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        'flex min-h-14 w-full cursor-pointer items-center gap-3 px-4 text-left text-[15px] font-medium',
        'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent [&>svg]:size-5 [&>svg]:shrink-0',
        danger ? 'text-down' : plain ? 'text-ink' : 'text-ink [&>svg:first-child]:text-accent',
      )}
    >
      {icon}
      <span className="flex-1">{children}</span>
      {!plain && <ChevronRight aria-hidden className="text-subtle" />}
    </button>
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
