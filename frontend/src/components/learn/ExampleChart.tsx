import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { PriceChart } from '../PriceChart'
import { AdapterOut, CompanionChart } from '../CompanionChart'
import { SetupOverlay } from '../SetupOverlay'
import type { Example } from '@/lib/api/lessons'
import type { Setup } from '@/lib/api/setups'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { timeframeSeconds } from '@/lib/chart/dateNavigation'
import { timeToX } from '@/lib/chart/timeToX'
import { useOverlayCanvas } from '@/hooks/useOverlayCanvas'
import { formatSessionDate, formatSessionTime } from '@/lib/chart/timeAxis'

interface ExampleChartProps {
  example: Example
  tickSize: number
  exchangeTimezone: string
  adapterFactory?: () => ChartAdapter
}

/**
 * One real example on a lesson card: the candles around it, the hit drawn exactly as the chart's
 * Setups layer draws it, and the rule's own reasons for it.
 */
export function ExampleChart({ example, tickSize, exchangeTimezone, adapterFactory }: ExampleChartProps) {
  const layer = useMemo(() => ({ rules: [example.rule], setups: [example.setup], rolls: [] }), [example])
  const shown = useMemo(() => new Set([example.detector]), [example.detector])
  const { anchor, end } = example.setup
  const time = (t: number) => formatSessionTime(t, exchangeTimezone)
  // DEC-075: this chart, for the other market's chart under it to keep to.
  const [leader, setLeader] = useState<ChartAdapter | null>(null)

  return (
    <figure className="flex min-w-0 flex-col gap-2" data-testid="lesson-example">
      <PriceChart
        bars={example.bars}
        tickSize={tickSize}
        exchangeTimezone={exchangeTimezone}
        axisTimezone="exchange"
        adapterFactory={adapterFactory}
        heightClass="h-64"
        overlay={(adapter) => (
          <>
            {example.companion && <AdapterOut adapter={adapter} onAdapter={setLeader} />}
            <HitBand adapter={adapter} bars={example.bars} setup={example.setup} timeframe={example.timeframe} />
            <SetupOverlay
              adapter={adapter}
              layer={layer}
              bars={example.bars}
              timeframe={example.timeframe}
              shown={shown}
            />
          </>
        )}
      />
      {example.companion && (
        <CompanionChart
          companion={example.companion}
          leader={leader}
          exchangeTimezone={exchangeTimezone}
          axisTimezone="exchange"
          adapterFactory={adapterFactory}
          heightClass="h-40"
        />
      )}
      <figcaption className="text-sm">
        <strong>{example.label}</strong>{' '}
        <span className="text-subtle">
          · {formatSessionDate(anchor, exchangeTimezone)}, {time(anchor)} · {example.timeframe}
          {end !== anchor && ` · known when the ${time(end)} candle closed`}
        </span>
        {example.explanation.map((line) => (
          <p key={line} className="mt-1 text-xs text-subtle">
            {line}
          </p>
        ))}
      </figcaption>
    </figure>
  )
}

/**
 * A faint band behind the hit's candles, from its start (or the chart's left edge) to the candle it is
 * known by: a small gap on a forty-candle chart is otherwise easy to miss.
 */
function HitBand({
  adapter,
  bars,
  setup,
  timeframe,
}: {
  adapter: ChartAdapter | null
  bars: DomainBar[]
  setup: Setup
  timeframe: string
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const projection = adapter?.getProjection()
    const ctx = canvas?.getContext('2d')
    if (!canvas || !projection || !ctx || bars.length === 0) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    const step = timeframeSeconds(timeframe)
    const left = timeToX(Math.max(setup.start, bars[0].time), bars, step, projection)
    const right = timeToX(setup.end + step, bars, step, projection)
    if (left === null || right === null) return
    ctx.fillStyle = 'rgba(88, 166, 255, 0.1)'
    ctx.fillRect(left, 0, Math.max(right - left, 2), canvas.height)
  }, [adapter, bars, setup, timeframe])

  useOverlayCanvas(canvasRef, adapter, draw)
  useEffect(draw, [draw])

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      data-testid="example-band"
      className="pointer-events-none absolute inset-0 z-[1]"
    />
  )
}
