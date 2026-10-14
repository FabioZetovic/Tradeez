import { useState } from 'react'
import { useSession } from '@/hooks/useSession'
import { SignInForm } from '@/components/SignInForm'
import { ChartWorkspace } from '@/components/ChartWorkspace'
import { PracticeView } from '@/components/practice/PracticeView'
import { DrillView } from '@/components/drills/DrillView'
import { Button, Notice } from '@/components/ui'
import { cx } from '@/lib/styles'
import type { ChartAdapter } from '@/lib/chart/types'

type Section = 'chart' | 'practice' | 'drills'

const SECTIONS: Array<{ id: Section; label: string }> = [
  { id: 'chart', label: 'Chart' },
  { id: 'practice', label: 'Practice' },
  { id: 'drills', label: 'Drills' },
]

/** DEC-015: visible on every screen. */
const DISCLAIMER =
  'Educational software. Not financial advice. Simulated results do not guarantee live results. ' +
  'Futures are leveraged instruments and most retail day traders lose money.'

export default function App({ adapterFactory }: { adapterFactory?: () => ChartAdapter } = {}) {
  const { state, login, register, logout } = useSession()
  const [section, setSection] = useState<Section>('chart')

  if (state.status !== 'signedIn') {
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-8 px-4 py-12">
        <Brand large />
        {state.status === 'loading' ? (
          <p role="status" className="text-sm text-subtle">
            Loading…
          </p>
        ) : (
          <>
            {state.expired && (
              <Notice role="status" className="max-w-md" data-testid="session-expired">
                You were signed out. Sign in again to carry on — your practice sessions and drawings
                are saved.
              </Notice>
            )}
            <SignInForm onLogin={login} onRegister={register} />
          </>
        )}
        <p className="max-w-md text-center text-xs text-subtle">{DISCLAIMER}</p>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh flex-col">
      <header className="sticky top-0 z-20 border-b border-line bg-canvas/90 backdrop-blur">
        <div className="mx-auto flex h-14 w-full max-w-screen-2xl items-center gap-8 px-6">
          <Brand />
          <nav role="tablist" aria-label="Sections" className="flex h-full items-stretch gap-1">
            {SECTIONS.map(({ id, label }) => (
              <button
                key={id}
                type="button"
                role="tab"
                aria-selected={section === id}
                onClick={() => setSection(id)}
                className={cx(
                  'relative cursor-pointer px-3 text-sm font-medium transition-colors',
                  section === id
                    ? 'text-ink after:absolute after:inset-x-2 after:-bottom-px after:h-0.5 after:rounded-full after:bg-brand'
                    : 'text-subtle hover:text-ink',
                )}
              >
                {label}
              </button>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2 text-sm text-subtle">
            <span>{state.user.displayName}</span>
            <Button variant="ghost" size="sm" onClick={logout}>
              Sign out
            </Button>
          </div>
        </div>
      </header>

      {/* BUG-032: w-full and min-w-0 give the page a definite width, so the chart — which holds the
          pixel width it last measured — can never push it wider than the window. */}
      <main className="mx-auto w-full min-w-0 max-w-screen-2xl flex-1 px-6 py-5">
        {section === 'chart' && <ChartWorkspace adapterFactory={adapterFactory} />}
        {section === 'practice' && <PracticeView adapterFactory={adapterFactory} />}
        {section === 'drills' && <DrillView adapterFactory={adapterFactory} />}
      </main>

      <footer className="border-t border-line">
        <p className="mx-auto max-w-screen-2xl px-6 py-4 text-xs text-subtle">{DISCLAIMER}</p>
      </footer>
    </div>
  )
}

function Brand({ large = false }: { large?: boolean }) {
  return (
    <div className={cx('flex items-center', large ? 'flex-col gap-3 text-center' : 'gap-2.5')}>
      <img src="/favicon.svg" alt="" className={large ? 'size-12' : 'size-7'} />
      <div>
        <h1 className={cx('font-semibold tracking-tight text-ink', large ? 'text-3xl' : 'text-base leading-5')}>
          Tradeez
        </h1>
        <p className={cx('text-subtle', large ? 'mt-1 text-sm' : 'text-xs leading-4')}>ICT practice on NQ futures</p>
      </div>
    </div>
  )
}
