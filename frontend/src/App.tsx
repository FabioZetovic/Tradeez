import { useSession } from '@/hooks/useSession'
import { SignInForm } from '@/components/SignInForm'
import { ChartWorkspace } from '@/components/ChartWorkspace'
import type { ChartAdapter } from '@/lib/chart/types'
import './App.css'

export default function App({ adapterFactory }: { adapterFactory?: () => ChartAdapter } = {}) {
  const { state, login, register, logout } = useSession()

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

      {state.status === 'signedIn' && <ChartWorkspace adapterFactory={adapterFactory} />}

      <footer className="shell__footer">
        <p>
          Educational software. Not financial advice. Simulated results do not guarantee live
          results. Futures are leveraged instruments and most retail day traders lose money.
        </p>
      </footer>
    </main>
  )
}
