import { describe, expect, it } from 'vitest'
import {
  closedTradeDrawing,
  defaultBracket,
  lockedBracket,
  directionOf,
  flipBracket,
  formatCents,
  formatR,
  MAX_BRACKET_TICKS,
  outcomeLabel,
  pinEntry,
  PLAN_DRAWING_ID,
  planFromDrawing,
  planNumbers,
  planProblem,
  TRADE_DRAWING_PREFIX,
} from './plan'
import { timeframeSeconds } from '../chart/dateNavigation'
import { candleAt } from '../chart/timeToX'

const REF = 80_000
const T = 1_710_338_400

describe('finished trades on the chart (DEC-046)', () => {
  const plan = {
    direction: 'LONG' as const,
    stopTicks: REF - 40,
    targetTicks: REF + 80,
    contracts: 1,
    referenceTicks: REF,
    lockedAt: T,
    plannedRewardRisk: 2,
  }

  /** The chart's five-minute candles, as a function: where the candle a time falls in starts. */
  const fiveMinutes = (t: number) => t - (t % 300)

  it('draws nothing for a trade still running: its exit is the future', () => {
    expect(closedTradeDrawing({ number: 1, plan, entry: { time: T, ticks: REF + 1 } }, fiveMinutes)).toBeNull()
  })

  it('spans entry bar to exit bar, at the real fill, with the planned stop and target', () => {
    const drawing = closedTradeDrawing(
      {
        number: 3,
        plan,
        result: {
          outcome: 'TARGET',
          entryTime: T + 7,
          entryTicks: REF + 1,
          exitTime: T + 1234,
          exitTicks: REF + 80,
          pnlTicks: 79,
          netPnlCents: 39_250,
          commissionCents: 250,
          maeTicks: 10,
          mfeTicks: 80,
          gapExit: false,
          rMultiple: 1.98,
          resolution: 'M1',
          engineVersion: 'fill-1.1.0',
          fillModelVersion: 'slip-1t/comm-250c',
        },
      },
      fiveMinutes,
    )
    expect(drawing?.id).toBe(`${TRADE_DRAWING_PREFIX}3`)
    // Snapped to the five-minute bars the chart draws: 09:00:07 → 09:00, 09:20:34 → 09:20.
    expect(drawing?.anchors.map((a) => a.time)).toEqual([T, T, T])
    expect(drawing?.endTime).toBe(T + 1200)
    expect(drawing?.anchors.map((a) => a.priceTicks)).toEqual([REF + 1, REF - 40, REF + 80])
  })

  it('T35: on a daily chart, snaps to the candles the chart has — opening at 18:00 New York, not on a multiple of a day', () => {
    // Two daily candles opening at 18:00 EDT, 22:00 UTC: Monday's (Sunday evening) and Tuesday's.
    const monday = Date.UTC(2025, 5, 8, 22) / 1000
    const daily = [monday, monday + 86_400].map((time) => ({ time, open: 1, high: 1, low: 1, close: 1, volume: 1 }))
    const drawing = closedTradeDrawing(
      {
        number: 1,
        plan,
        result: {
          outcome: 'STOP', entryTime: monday + 3_600 * 15, entryTicks: REF, exitTime: monday + 86_400 + 600,
          exitTicks: REF - 41, pnlTicks: -41, netPnlCents: -20_750, commissionCents: 250, maeTicks: 41, mfeTicks: 3,
          gapExit: false, rMultiple: -1.03, resolution: 'M1', engineVersion: 'fill-1.2.0', fillModelVersion: 'slip-1t/comm-250c',
        },
      },
      (t) => candleAt(t, daily),
    )
    expect(drawing?.anchors[0].time).toBe(monday)
    expect(drawing?.endTime).toBe(monday + 86_400)
    // A multiple of a day would have been UTC midnight, the wrong side of the open.
    expect(monday % 86_400).not.toBe(0)
  })

  it('reads timeframe codes', () => {
    expect(timeframeSeconds('1m')).toBe(60)
    expect(timeframeSeconds('15m')).toBe(900)
    expect(timeframeSeconds('1h')).toBe(3600)
  })
})

describe('practice plan helpers', () => {
  it('places a 2R bracket on the correct sides for each direction', () => {
    const long = defaultBracket('LONG', REF, T)
    expect(long.id).toBe(PLAN_DRAWING_ID)
    expect(long.kind).toBe('position')
    expect(long.anchors.map((a) => a.priceTicks)).toEqual([REF, REF - 40, REF + 80])

    const short = defaultBracket('SHORT', REF, T)
    expect(short.anchors.map((a) => a.priceTicks)).toEqual([REF, REF + 40, REF - 80])
  })

  it('reads direction from where the target is', () => {
    expect(directionOf(defaultBracket('LONG', REF, T))).toBe('LONG')
    expect(directionOf(defaultBracket('SHORT', REF, T))).toBe('SHORT')
  })

  it('flips a long into the mirror-image short, and back', () => {
    const long = defaultBracket('LONG', REF, T)
    const short = flipBracket(long)
    expect(short.anchors.map((a) => a.priceTicks)).toEqual([REF, REF + 40, REF - 80])
    expect(flipBracket(short).anchors).toEqual(long.anchors)
  })

  it('pins the entry to the market price whatever was dragged', () => {
    // The order is at market. The learner chooses the bracket, not the fill.
    const dragged = defaultBracket('LONG', REF, T)
    dragged.anchors[0] = { time: T - 900, priceTicks: REF + 25 }
    const pinned = pinEntry(dragged, REF + 3, T + 300)
    expect(pinned.anchors[0]).toEqual({ time: T + 300, priceTicks: REF + 3 })
    // Stop and target keep their prices; only their time follows the entry.
    expect(pinned.anchors[1]).toEqual({ time: T + 300, priceTicks: REF - 40 })
    expect(pinned.anchors[2]).toEqual({ time: T + 300, priceTicks: REF + 80 })
  })

  it('turns the drawing into the request the server expects', () => {
    expect(planFromDrawing(defaultBracket('SHORT', REF, T), 3)).toEqual({
      direction: 'SHORT',
      stopTicks: REF + 40,
      targetTicks: REF - 80,
      contracts: 3,
    })
  })

  describe('planProblem mirrors the server rules', () => {
    const ok = { direction: 'LONG' as const, stopTicks: REF - 40, targetTicks: REF + 80, contracts: 1 }

    it('accepts a sensible bracket', () => {
      expect(planProblem(ok, REF)).toBeNull()
    })

    it('rejects levels on the wrong side of the price', () => {
      expect(planProblem({ ...ok, stopTicks: REF + 1 }, REF)).toMatch(/stop below/)
      expect(planProblem({ ...ok, targetTicks: REF }, REF)).toMatch(/stop below/)
      expect(
        planProblem({ ...ok, direction: 'SHORT', stopTicks: REF - 40, targetTicks: REF - 80 }, REF),
      ).toMatch(/stop above/)
    })

    it('rejects impossible contract counts', () => {
      expect(planProblem({ ...ok, contracts: 0 }, REF)).toMatch(/Contracts/)
      expect(planProblem({ ...ok, contracts: 101 }, REF)).toMatch(/Contracts/)
      expect(planProblem({ ...ok, contracts: 1.5 }, REF)).toMatch(/Contracts/)
    })

    it('rejects brackets absurdly far from the price', () => {
      expect(planProblem({ ...ok, targetTicks: REF + MAX_BRACKET_TICKS + 1 }, REF)).toMatch(
        /within/,
      )
    })

    it('draws the lines exactly where the server does: at the price is refused, a tick away is not', () => {
      const short = { direction: 'SHORT' as const, stopTicks: REF + 40, targetTicks: REF - 80, contracts: 1 }
      expect(planProblem(short, REF)).toBeNull()
      // A stop at the price would be filled the moment the trade is entered.
      expect(planProblem({ ...ok, stopTicks: REF }, REF)).toMatch(/stop below/)
      expect(planProblem({ ...short, stopTicks: REF }, REF)).toMatch(/stop above/)
      expect(planProblem({ ...short, targetTicks: REF }, REF)).toMatch(/stop above/)
      expect(planProblem({ ...ok, stopTicks: REF - 1, targetTicks: REF + 1 }, REF)).toBeNull()
      expect(planProblem({ ...short, stopTicks: REF + 1, targetTicks: REF - 1 }, REF)).toBeNull()
      // The farthest allowed, and one beyond, either way.
      expect(planProblem({ ...ok, stopTicks: REF - MAX_BRACKET_TICKS }, REF)).toBeNull()
      expect(planProblem({ ...ok, stopTicks: REF - MAX_BRACKET_TICKS - 1 }, REF)).toMatch(/within/)
      expect(planProblem({ ...short, targetTicks: REF - MAX_BRACKET_TICKS - 1 }, REF)).toMatch(/within/)
      expect(planProblem({ ...ok, contracts: 100 }, REF)).toBeNull()
    })
  })

  it("computes a short's risk above and reward below", () => {
    expect(
      planNumbers({ direction: 'SHORT', stopTicks: REF + 10, targetTicks: REF - 25, contracts: 3 }, REF, 500),
    ).toEqual({ riskTicks: 10, rewardTicks: 25, ratio: 2.5, riskCents: 15_000, rewardCents: 37_500 })
  })

  it('has no reward : risk without a risk', () => {
    expect(planNumbers({ ...ok(), stopTicks: REF }, REF, 500).ratio).toBeNull()
  })

  it('BUG-058: never prints a fraction of a cent, whatever is typed in the contracts box', () => {
    const typed = planNumbers({ ...ok(), stopTicks: REF - 41, contracts: 2.7 }, REF, 500)
    expect(formatCents(typed.riskCents)).toBe('$553.50')
    expect(formatCents(26_650.000000000004)).toBe('$266.50')
    expect(formatCents(-0.4)).toBe('$0.00')
  })

  it('computes risk and reward in integer cents, scaled by contracts', () => {
    const numbers = planNumbers(
      { direction: 'LONG', stopTicks: REF - 40, targetTicks: REF + 80, contracts: 2 },
      REF,
      500,
    )
    expect(numbers).toEqual({
      riskTicks: 40,
      rewardTicks: 80,
      ratio: 2,
      riskCents: 40_000,
      rewardCents: 80_000,
    })
  })

  it('formats cents without floating-point money', () => {
    expect(formatCents(0)).toBe('$0.00')
    expect(formatCents(20_000)).toBe('$200.00')
    expect(formatCents(-250)).toBe('−$2.50')
    expect(formatCents(123_456_789)).toBe('$1,234,567.89')
  })

  it('formats R with a sign, using the same minus as money', () => {
    expect(formatR(2)).toBe('+2.00R')
    expect(formatR(-1.025)).toBe('−1.02R')
    expect(formatR(0)).toBe('0.00R')
  })

  it('names an ambiguous outcome as an assumption, not a stop (INVARIANT 6)', () => {
    expect(outcomeLabel('AMBIGUOUS')).toMatch(/Ambiguous/)
    expect(outcomeLabel('AMBIGUOUS')).not.toMatch(/Stopped/)
    expect(outcomeLabel('STOP')).toBe('Stopped out')
  })
})

function ok() {
  return { direction: 'LONG' as const, stopTicks: REF - 40, targetTicks: REF + 80, contracts: 1 }
}

describe('what each outcome is called', () => {
  it('names every outcome a trade can have, the learner\'s own close included', () => {
    expect(outcomeLabel('TARGET')).toBe('Target hit')
    expect(outcomeLabel('STOP')).toBe('Stopped out')
    expect(outcomeLabel('TIME_EXIT')).toBe('Closed at the session end')
    expect(outcomeLabel('CLOSED')).toBe('Closed by you')
  })
})

describe('partials (T28)', () => {
  const plan = {
    direction: 'LONG' as const,
    stopTicks: REF - 40,
    targetTicks: REF + 80,
    contracts: 2,
    referenceTicks: REF,
    lockedAt: T,
    plannedRewardRisk: 1.5,
    partialTicks: REF + 40,
    partialContracts: 1,
    breakevenAfterPartial: true,
  }
  const filled = { time: T + 120, ticks: REF + 40, contracts: 1, pnlTicks: 39, gap: false }

  it('goes into the request only when there is one', () => {
    const drawing = defaultBracket('LONG', REF, T)
    expect(planFromDrawing(drawing, 2, { ticks: REF + 40, contracts: 1, breakeven: true })).toEqual({
      direction: 'LONG',
      stopTicks: REF - 40,
      targetTicks: REF + 80,
      contracts: 2,
      partialTicks: REF + 40,
      partialContracts: 1,
      breakevenAfterPartial: true,
    })
    expect(planFromDrawing(drawing, 2, null)).not.toHaveProperty('partialTicks')
  })

  it('is drawn on the running trade; once filled with the move, the stop is drawn at the entry', () => {
    const before = lockedBracket(plan, T, REF + 1)
    expect(before.partialTicks).toBe(REF + 40)
    expect(before.anchors[1].priceTicks).toBe(REF - 40)
    expect(lockedBracket(plan, T, REF + 1, filled).anchors[1].priceTicks).toBe(REF + 1)
    expect(lockedBracket({ ...plan, breakevenAfterPartial: false }, T, REF + 1, filled).anchors[1].priceTicks).toBe(REF - 40)
  })

  it('stays on the finished trade, and is checked for its contracts and its place', () => {
    const result = {
      outcome: 'STOP' as const,
      entryTime: T,
      entryTicks: REF + 1,
      exitTime: T + 600,
      exitTicks: REF,
      pnlTicks: -1,
      netPnlCents: 0,
      commissionCents: 500,
      maeTicks: 1,
      mfeTicks: 40,
      gapExit: false,
      rMultiple: 0.48,
      resolution: 'M1',
      engineVersion: 'fill-1.4.0',
      fillModelVersion: 'slip-1t/comm-250c',
      partial: filled,
    }
    expect(closedTradeDrawing({ number: 1, plan, result }, (t) => t)?.partialTicks).toBe(REF + 40)
    const request = { direction: 'LONG' as const, stopTicks: REF - 40, targetTicks: REF + 80, contracts: 2 }
    expect(planProblem({ ...request, partialTicks: REF + 40, partialContracts: 1 }, REF)).toBeNull()
    expect(planProblem({ ...request, partialTicks: REF + 40 }, REF)).toMatch(/fewer than all/)
    expect(planProblem({ ...request, partialTicks: REF, partialContracts: 1 }, REF)).toMatch(/between/)
    expect(planProblem({ ...request, direction: 'SHORT', stopTicks: REF + 40, targetTicks: REF - 80, partialTicks: REF - 81, partialContracts: 1 }, REF)).toMatch(/between/)
    expect(planNumbers({ ...request, partialTicks: REF + 40, partialContracts: 1 }, REF, 500)).toEqual({
      riskTicks: 40,
      rewardTicks: 80,
      ratio: 1.5,
      riskCents: 40_000,
      rewardCents: 60_000,
    })
  })
})
