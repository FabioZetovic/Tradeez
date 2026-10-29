import { useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowLeft, BookOpen, CalendarDays, ChartCandlestick, CirclePlay, House, Target, TrendingUp } from 'lucide-react'
import { useIsPhone } from '@/hooks/useIsPhone'
import { DISCLAIMER, DISCLAIMER_SHORT } from '@/lib/disclaimer'
import { cx } from '@/lib/styles'
import { AccountMenu } from './AccountMenu'
import type { User } from '@/lib/api/auth'
import { enter } from '@/lib/motion'

/** Home, the sections in the order they build on one another, and the economic calendar (T40). */
const NAV = [
  { to: '/', label: 'Home' },
  { to: '/learn', label: 'Learn' },
  { to: '/drills', label: 'Drills' },
  { to: '/practice', label: 'Practice' },
  { to: '/chart', label: 'Chart' },
  { to: '/news', label: 'News' },
] as const

/**
 * Pages built around a chart take the whole width; reading pages keep a 1280 px column (docs/REDESIGN.md T36). Practice's
 * home is a reading page since T41; its session screen takes the width again with its own address (T42). So with Drills
 * (T43): the cards read in the column, a drill's chart takes the width.
 */
const wides = (pathname: string) => pathname === '/chart' || /^\/drills\/[^/]+/.test(pathname)

/** The Chart tab and a practice session fill the window to the edges, with Lovable's narrow gutter (T38, T42). */
const fills = (pathname: string) => pathname === '/chart' || /^\/practice\/[^/]+/.test(pathname)

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
  const full = fills(pathname)
  const wide = full || wides(pathname)
  const phone = useIsPhone()
  if (phone) {
    return (
      <PhoneShell user={user} onChangePassword={onChangePassword} onLogout={onLogout} onLogoutEverywhere={onLogoutEverywhere}>
        {children}
      </PhoneShell>
    )
  }

  return (
    <div className="flex min-h-svh flex-col">
      <header className="sticky top-0 z-20 border-b border-line bg-canvas/80 backdrop-blur">
        {/* The full width on every screen, so it never jumps sideways between the reading pages and the chart pages
            (BUG-102). */}
        <div className="flex h-16 w-full items-center gap-3 px-4 md:gap-8 md:px-6">
          <Link to="/" className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent pointer-coarse:py-1.5">
            <Logo />
          </Link>
          <nav aria-label="Sections" className="flex gap-1">
            {NAV.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cx(
                    'relative rounded-lg px-3 py-1.5 text-sm font-medium transition-colors pointer-coarse:py-3',
                    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
                    isActive ? 'text-ink' : 'text-subtle hover:text-ink',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {/* The highlight slides from the section left to the one opened. */}
                    {isActive && (
                      <motion.span
                        layoutId="section-highlight"
                        aria-hidden
                        className="absolute inset-0 rounded-lg bg-sunken"
                        transition={{ type: 'spring', bounce: 0.15, duration: 0.35 }}
                      />
                    )}
                    <span className="relative">{label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1">
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
          full ? 'p-3' : 'px-6 py-8',
          wide ? 'max-w-none' : 'max-w-[1280px]',
        )}
      >
        <motion.div
          key={section}
          {...enter(0, { rise: 8 })}
        >
          {children}
        </motion.div>
      </main>

      <Footer />
    </div>
  )
}

/** The phone's sections, for the tab bar (T53): News moved behind the calendar on Home. */
const TABS = [
  { to: '/', label: 'Home', icon: House },
  { to: '/learn', label: 'Learn', icon: BookOpen },
  { to: '/drills', label: 'Drills', icon: Target },
  { to: '/practice', label: 'Practice', icon: CirclePlay },
  { to: '/chart', label: 'Chart', icon: ChartCandlestick },
] as const

/** A drill, a practice session or a lesson fills the phone: no tab bar, its own bar to leave by. */
const immersive = (pathname: string) => /^\/(drills|practice|learn)\/./.test(pathname)

/** Pages reached from Home or the account sheet, with a bar back. */
const SUBPAGES: Record<string, string> = { '/news': 'Economic calendar', '/help': 'How it works' }

/** The sections' own titles in the phone's top bar. */
const TITLES: Record<string, string> = { '/learn': 'Learn', '/drills': 'Drills', '/practice': 'Practice' }

/**
 * The frame on a phone (T53, Claude Design): a top bar per page, the sections in a tab bar at the thumb, the account in
 * a sheet. A drill, a session or a lesson takes the whole screen and brings its own bar; the Chart tab its own too.
 */
function PhoneShell({ user, onChangePassword, onLogout, onLogoutEverywhere, children }: AppShellProps) {
  const { pathname } = useLocation()
  const section = '/' + (pathname.split('/')[1] ?? '')
  const full = immersive(pathname)
  const sub = SUBPAGES[pathname]
  const account = (
    <AccountMenu user={user} onChangePassword={onChangePassword} onLogout={onLogout} onLogoutEverywhere={onLogoutEverywhere} />
  )

  return (
    <div className={cx('flex min-h-svh flex-col', !full && 'pb-[calc(4.25rem+env(safe-area-inset-bottom))]')}>
      {!full && pathname !== '/chart' && (
        <header className="sticky top-0 z-20 flex h-14 items-center gap-2 bg-canvas/90 pr-2 pl-4 backdrop-blur">
          {sub ? (
            <>
              <Link
                to="/"
                aria-label="Back to Home"
                className="-ml-3 grid size-11 place-items-center rounded-xl text-ink focus-visible:outline-2 focus-visible:outline-accent"
              >
                <ArrowLeft aria-hidden className="size-5" />
              </Link>
              <p className="min-w-0 flex-1 truncate text-[17px] font-semibold text-ink">{sub}</p>
            </>
          ) : pathname === '/' ? (
            <>
              <Link to="/" className="flex-1 rounded-lg focus-visible:outline-2 focus-visible:outline-accent">
                <Logo />
              </Link>
              <Link
                to="/news"
                aria-label="Economic calendar"
                className="grid size-11 place-items-center rounded-xl text-ink focus-visible:outline-2 focus-visible:outline-accent"
              >
                <CalendarDays aria-hidden className="size-[22px]" />
              </Link>
            </>
          ) : (
            <p aria-hidden className="min-w-0 flex-1 truncate font-display text-[28px] font-bold tracking-tight text-ink">
              {TITLES[section] ?? ''}
            </p>
          )}
          {account}
        </header>
      )}

      <main className={cx('w-full min-w-0 flex-1', full || pathname === '/chart' ? 'p-0' : 'px-4 pt-2 pb-6')}>
        <motion.div key={section} {...enter(0, { rise: 8 })}>
          {children}
        </motion.div>
      </main>

      <Footer />
      {!full && <TabBar />}
    </div>
  )
}

/** The five sections at the thumb (T53): the current one in the accent. */
function TabBar() {
  return (
    <nav
      aria-label="Sections"
      className="fixed inset-x-0 bottom-0 z-30 flex border-t border-line bg-canvas/95 px-1.5 pt-1.5 pb-[calc(0.375rem+env(safe-area-inset-bottom))] backdrop-blur"
    >
      {TABS.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          className={({ isActive }) =>
            cx(
              'flex min-h-14 flex-1 flex-col items-center justify-center gap-1 rounded-xl text-[11px] font-medium',
              'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent',
              isActive ? 'text-ink [&_svg]:text-accent' : 'text-subtle',
            )
          }
        >
          <Icon aria-hidden className="size-[22px]" strokeWidth={1.8} />
          {label}
        </NavLink>
      ))}
    </nav>
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
        className="tap cursor-pointer underline underline-offset-2 hover:text-ink"
      >
        {more ? 'Less' : 'Read more'}
      </button>
      <AnimatePresence initial={false}>
        {more && (
          <motion.p
            className="mx-auto max-w-3xl overflow-hidden"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
          >
            <span className="block pt-2">{DISCLAIMER}</span>
          </motion.p>
        )}
      </AnimatePresence>
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
