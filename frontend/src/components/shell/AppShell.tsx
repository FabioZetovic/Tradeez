import { useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { Menu, TrendingUp, X } from 'lucide-react'
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
  // Phones (T46): the sections fold into a menu under the header.
  const [menu, setMenu] = useState(false)

  return (
    <div className="flex min-h-svh flex-col">
      <header className="sticky top-0 z-20 border-b border-line bg-canvas/80 backdrop-blur">
        {/* The full width on every screen, so it never jumps sideways between the reading pages and the chart pages
            (BUG-102). */}
        <div className="flex h-16 w-full items-center gap-3 px-4 md:gap-8 md:px-6">
          <Link to="/" className="rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent pointer-coarse:py-1.5">
            <Logo />
          </Link>
          <nav aria-label="Sections" className="hidden gap-1 md:flex">
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
            <button
              type="button"
              aria-expanded={menu}
              aria-controls="section-menu"
              onClick={() => setMenu((m) => !m)}
              className="flex cursor-pointer items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-ink hover:bg-sunken focus-visible:outline-2 focus-visible:outline-accent md:hidden pointer-coarse:min-h-11"
            >
              {menu ? <X aria-hidden className="size-4" /> : <Menu aria-hidden className="size-4" />}
              Menu
            </button>
            <AccountMenu
              user={user}
              onChangePassword={onChangePassword}
              onLogout={onLogout}
              onLogoutEverywhere={onLogoutEverywhere}
            />
          </div>
        </div>
        <AnimatePresence initial={false}>
          {menu && (
            <motion.nav
              id="section-menu"
              aria-label="Menu"
              className="overflow-hidden border-t border-line md:hidden"
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
            >
              <div className="flex flex-col gap-1 px-4 py-3">
                {NAV.map(({ to, label }) => (
                  <NavLink
                    key={to}
                    to={to}
                    onClick={() => setMenu(false)}
                    className={({ isActive }) =>
                      cx(
                        'rounded-lg px-3 py-2.5 text-base font-medium',
                        isActive ? 'bg-sunken text-ink' : 'text-subtle hover:text-ink',
                      )
                    }
                  >
                    {label}
                  </NavLink>
                ))}
              </div>
            </motion.nav>
          )}
        </AnimatePresence>
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
