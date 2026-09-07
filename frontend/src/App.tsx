import { useEffect, useState } from 'react'
import { getHealth, type HealthResponse } from '@/lib/api/client'
import { SimulatedDataBanner } from '@/components/SimulatedDataBanner'
import './App.css'

type Status =
  | { kind: 'loading' }
  | { kind: 'ok'; health: HealthResponse }
  | { kind: 'error'; message: string }

export default function App() {
  const [status, setStatus] = useState<Status>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    getHealth()
      .then((health) => {
        if (!cancelled) setStatus({ kind: 'ok', health })
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setStatus({
            kind: 'error',
            message: error instanceof Error ? error.message : 'Unknown error',
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="shell">
      <header className="shell__header">
        <h1>Tradeez</h1>
        <p className="shell__tagline">Futures day-trading practice platform</p>
      </header>

      <SimulatedDataBanner />

      <section className="panel">
        <h2>Phase 0 — connectivity</h2>
        {status.kind === 'loading' && <p role="status">Checking backend…</p>}

        {status.kind === 'ok' && (
          <dl className="kv">
            <dt>Backend</dt>
            <dd data-testid="backend-status">{status.health.status}</dd>
            <dt>Database</dt>
            <dd data-testid="database-status">{status.health.database}</dd>
            <dt>Server time (UTC)</dt>
            <dd>{status.health.timestamp}</dd>
          </dl>
        )}

        {status.kind === 'error' && (
          <p role="alert" className="error">
            Backend unreachable: {status.message}
            <br />
            <small>Start it with <code>./mvnw spring-boot:run</code> in <code>backend/</code>.</small>
          </p>
        )}
      </section>

      <footer className="shell__footer">
        <p>
          Educational software. Not financial advice. Simulated results do not guarantee live
          results. Futures are leveraged instruments and most retail day traders lose money.
        </p>
      </footer>
    </main>
  )
}
