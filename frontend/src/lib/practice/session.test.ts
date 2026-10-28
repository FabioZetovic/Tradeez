import { describe, expect, it } from 'vitest'
import { takeaways } from './session'
import type { ScoreLine, TradeView } from '@/lib/api/practice'

const trade = (process: ScoreLine[]) => ({ scorecard: { version: 'scorecard-1.1.0', outcome: [], process } }) as unknown as TradeView

describe('what to take from a session (T53)', () => {
  it("is the scorecards' process lines gathered: rules counted kept and broken, the rest as the last trade put them", () => {
    const lines = takeaways([
      trade([
        { id: 'risk', label: 'Risk limit', text: 'Within $200.', verdict: 'KEPT' },
        { id: 'time', label: 'Time', text: 'Placed outside the killzones.' },
      ]),
      trade([
        { id: 'risk', label: 'Risk limit', text: 'Over $200.', verdict: 'BROKEN' },
        { id: 'time', label: 'Time', text: 'Placed in New York AM.' },
      ]),
    ])
    expect(lines).toEqual(['Risk limit: kept on 1 of 2 trades.', 'Time: Placed in New York AM.'])
  })

  it('says nothing for trades without a scorecard', () => {
    expect(takeaways([{} as TradeView])).toEqual([])
  })
})
