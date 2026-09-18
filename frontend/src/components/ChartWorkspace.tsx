import { useEffect, useState } from 'react'
import { PriceChart } from './PriceChart'
import { SimulatedDataBanner } from './SimulatedDataBanner'
import {
  demoWindow,
  getBars,
  listInstruments,
  TIMEFRAMES,
  type BarSeries,
  type Instrument,
} from '@/lib/api/marketData'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { timezoneLabel, type AxisTimezone } from '@/lib/chart/timeAxis'

interface ChartWorkspaceProps {
  /** Injected in tests so the chart can render without a canvas. */
  adapterFactory?: () => ChartAdapter
}

export function ChartWorkspace({ adapterFactory }: ChartWorkspaceProps) {
  const [instruments, setInstruments] = useState<Instrument[]>([])
  const [symbol, setSymbol] = useState<string>('DEMO-NQ')
  const [timeframe, setTimeframe] = useState<string>('5m')
  const [series, setSeries] = useState<BarSeries | null>(null)
  const [axisTimezone, setAxisTimezone] = useState<AxisTimezone>('exchange')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listInstruments()
      .then(setInstruments)
      .catch(() => setError('Could not load instruments.'))
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    const { from, to } = demoWindow(timeframe)
    getBars({ symbol, timeframe, from, to, seed: 42 })
      .then((result) => {
        if (!cancelled) setSeries(result)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load bars.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [symbol, timeframe])

  const instrument = instruments.find((i) => i.symbol === symbol)
  const lastBar = series?.bars.at(-1)

  return (
    <section className="workspace">
      {/* The banner reads the data's own dataClass rather than assuming. When real licensed data
          eventually arrives, this disappears on its own instead of needing to be remembered. */}
      <SimulatedDataBanner dataClass={series?.dataClass ?? 'SIMULATED'} />

      <div className="toolbar">
        <label>
          Instrument
          <select value={symbol} onChange={(e) => setSymbol(e.target.value)}>
            {instruments.map((i) => (
              <option key={i.symbol} value={i.symbol}>
                {i.symbol}
              </option>
            ))}
          </select>
        </label>

        <div className="timeframes" role="group" aria-label="Timeframe">
          {TIMEFRAMES.map((tf) => (
            <button
              key={tf}
              type="button"
              aria-pressed={tf === timeframe}
              className={tf === timeframe ? 'tf tf--active' : 'tf'}
              onClick={() => setTimeframe(tf)}
            >
              {tf}
            </button>
          ))}
        </div>

        {instrument && (
          <button
            type="button"
            className="tf"
            data-testid="timezone-toggle"
            title="Switch the time axis between exchange-local and UTC"
            onClick={() => setAxisTimezone(axisTimezone === 'utc' ? 'exchange' : 'utc')}
          >
            {timezoneLabel(instrument.exchangeTimezone, axisTimezone, new Date())}
          </button>
        )}

        {instrument && lastBar && (
          <span className="last-price" data-testid="last-price">
            {formatPrice(lastBar.close, instrument.tickSize)}
          </span>
        )}
      </div>

      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      {instrument && series && !error && (
        <PriceChart
          bars={series.bars}
          tickSize={instrument.tickSize}
          exchangeTimezone={instrument.exchangeTimezone}
          axisTimezone={axisTimezone}
          adapterFactory={adapterFactory}
        />
      )}

      {loading && <p role="status">Loading…</p>}

      {series && instrument && (
        <dl className="kv kv--compact">
          <dt>Bars</dt>
          <dd>{series.bars.length}</dd>
          <dt>Tick</dt>
          <dd>
            {instrument.tickSize} = ${(instrument.tickValueCents / 100).toFixed(2)}
          </dd>
          <dt>Seed</dt>
          <dd>{series.seed}</dd>
          <dt>Generator</dt>
          <dd>{series.generatorVersion}</dd>
        </dl>
      )}
    </section>
  )
}
