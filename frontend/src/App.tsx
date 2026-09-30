import { useSession } from '@/hooks/useSession'
import { SignInForm } from '@/components/SignInForm'
import { ChartWorkspace } from '@/components/ChartWorkspace'
import { PracticeView } from '@/components/practice/PracticeView'
import { DrillView } from '@/components/drills/DrillView'
import { useState } from 'react'
import type { ChartAdapter } from '@/lib/chart/types'
import './App.css'

export default function App({ adapterFactory }: { adapterFactory?: () => ChartAdapter } = {}) {
  const { state, login, register, logout } = useSession()
  const [tab, setTab] = useState<'chart' | 'practice' | 'drills'>('chart')

  return (
    <main className="shell">
      <header className="shell__header">
        <div>
          <h1>Tradeez</h1>
          <p className="shell__tagline">Futures day-trading practice</p>
        </div>
        {state.status === 'signedIn' && (
          <div className="shell__account">
            <span>{state.user.displayName}</span>
            <button type="button" className="link" onClick={logout}>
              Sign out
            </button>
          </div>
        )}
      </header>

      {state.status === 'loading' && <p role="status">Loading…</p>}

      {state.status === 'signedOut' && <SignInForm onLogin={login} onRegister={register} />}

      {state.status === 'signedIn' && (
        <>
          <nav className="tabs" role="tablist" aria-label="Sections">
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'chart'}
              className={tab === 'chart' ? 'tab tab--active' : 'tab'}
              onClick={() => setTab('chart')}
            >
              Chart
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'practice'}
              className={tab === 'practice' ? 'tab tab--active' : 'tab'}
              onClick={() => setTab('practice')}
            >
              Practice
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'drills'}
              className={tab === 'drills' ? 'tab tab--active' : 'tab'}
              onClick={() => setTab('drills')}
            >
              Drills
            </button>
          </nav>
          {tab === 'chart' && <ChartWorkspace adapterFactory={adapterFactory} />}
          {tab === 'practice' && <PracticeView adapterFactory={adapterFactory} />}
          {tab === 'drills' && <DrillView adapterFactory={adapterFactory} />}
        </>
      )}

      <footer className="shell__footer">
        <p>
          Educational software. Not financial advice. Simulated results do not guarantee live
          results. Futures are leveraged instruments and most retail day traders lose money.
        </p>
      </footer>
    </main>
  )
}
