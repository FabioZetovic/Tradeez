import { useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { motion } from 'motion/react'
import { TrendingUp } from 'lucide-react'
import { DISCLAIMER, DISCLAIMER_SHORT } from '@/lib/disclaimer'
import { cx } from '@/lib/styles'
import { AccountMenu } from './AccountMenu'
import type { User } from '@/lib/api/auth'

/** Home, the sections in the order they build on one another, and the economic calendar (T40). */
const NAV = [
  { to: '/', label: 'Home' },
  { to: '/learn', label: 'Learn' },
  { to: '/drills', label: 'Drills' },
  { to: '/practice', label: 'Practice' },
  { to: '/chart', label: 'Chart' },
  { to: '/news', label: 'News' },
] as const

/** Pages built around a chart take the whole width; reading pages keep a 1280 px column (docs/REDESIGN.md T36). */
const WIDE = ['/chart', '/practice', '/drills']

/** The Chart tab fills the window to the edges, with Lovable's narrow gutter (T38). */
const FULL = '/chart'

interface AppShellProps {
  user: User
  onChangePassword: (currentPassword: string, newPassword: string) => Promise<void>
  onLogout: () => Promise<void>
  onLogoutEverywhere: () => Promise<void>
  children: ReactNode
}

/** The frame around every signed-in screen (T36): the header with the sections and the account, the footer. */
export function AppShell({ user, onChangePassword, onLogout, onLogoutEverywhere, children }: AppShellProps) {
  const { pathname } = useLocation()
  const section = '/' + (pathname.split('/')[1] ?? '')
  const wide = WIDE.includes(section)

  return (
    <div className="flex min-h-svh flex-col">
      <header className="sticky top-0 z-20 border-b border-line bg-canvas/80 backdrop-blur">
        {/* As wide as the page under it, so the logo lines up with the content's left edge. */}
        <div className={cx('mx-auto flex h-16 w-full items-center gap-8 px-6', wide ? 'max-w-none' : 'max-w-[1280px]')}>
          <Link to="/" className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent">
            <Logo />
          </Link>
          <nav aria-label="Sections" className="flex gap-1">
            {NAV.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cx(
                    'rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
                    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
                    isActive ? 'bg-sunken text-ink' : 'text-subtle hover:text-ink',
                  )
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto">
            <AccountMenu
              user={user}
              onChangePassword={onChangePassword}
              onLogout={onLogout}
              onLogoutEverywhere={onLogoutEverywhere}
            />
          </div>
        </div>
      </header>

      {/* BUG-032: w-full and min-w-0 give the page a definite width, so the chart — which holds the
          pixel width it last measured — can never push it wider than the window. */}
      <main
        className={cx(
          'mx-auto w-full min-w-0 flex-1',
          section === FULL ? 'p-3' : 'px-6 py-8',
          wide ? 'max-w-none' : 'max-w-[1280px]',
        )}
      >
        <motion.div
          key={section}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
        >
          {children}
        </motion.div>
      </main>

      <Footer />
    </div>
  )
}

function Footer() {
  const [more, setMore] = useState(false)
  return (
    <footer className="border-t border-line px-6 py-3 text-center text-xs text-subtle">
      {DISCLAIMER_SHORT}{' '}
      <button
        type="button"
        aria-expanded={more}
        onClick={() => setMore((m) => !m)}
        className="cursor-pointer underline underline-offset-2 hover:text-ink"
      >
        {more ? 'Less' : 'Read more'}
      </button>
      {more && <p className="mx-auto mt-2 max-w-3xl">{DISCLAIMER}</p>}
    </footer>
  )
}

/** The mark and the name: a rising line on the accent. */
export function Logo({ large = false }: { large?: boolean }) {
  return (
    <span className={cx('flex items-center', large ? 'gap-3' : 'gap-2.5')}>
      <span className={cx('grid place-items-center rounded-lg bg-accent text-accent-ink', large ? 'size-10' : 'size-8')}>
        <TrendingUp aria-hidden strokeWidth={2.5} className={large ? 'size-6' : 'size-5'} />
      </span>
      <span className={cx('font-display font-bold tracking-tight text-ink', large ? 'text-2xl' : 'text-lg')}>Tradeez</span>
    </span>
  )
}
