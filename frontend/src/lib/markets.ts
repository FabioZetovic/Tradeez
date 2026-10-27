/** The real markets a learner can choose (T47): NQ first, ES beside it. */
export const MARKETS = ['NQ', 'ES'] as const

export type Market = (typeof MARKETS)[number]

const DRILL_MARKET_KEY = 'tradeez.drills.market'

/** The market drills are asked on, remembered in this browser; NQ when nothing is (or storage is blocked). */
export function drillMarket(): Market {
  try {
    const stored = localStorage.getItem(DRILL_MARKET_KEY)
    return stored === 'ES' ? 'ES' : 'NQ'
  } catch {
    return 'NQ'
  }
}

export function setDrillMarket(market: Market): void {
  try {
    localStorage.setItem(DRILL_MARKET_KEY, market)
  } catch {
    // A remembered market is a convenience: without storage, drills are on NQ.
  }
}
