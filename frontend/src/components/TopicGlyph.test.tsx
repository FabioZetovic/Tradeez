import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { TopicGlyph } from './TopicGlyph'

describe('TopicGlyph (T55)', () => {
  it("draws a known topic's own picture, hidden from screen readers", () => {
    const { container } = render(<TopicGlyph topic="Gaps & imbalances" className="text-t-gaps" />)
    const svg = container.querySelector('svg')!
    expect(svg).toHaveAttribute('aria-hidden')
    expect(svg).toHaveClass('text-t-gaps')
    // Three candles and the gap between them, not the plain circle.
    expect(svg.querySelectorAll('rect')).toHaveLength(4)
    expect(svg.querySelector('circle')).toBeNull()
  })

  it('draws a plain circle for a topic it does not know', () => {
    const { container } = render(<TopicGlyph topic="More drills" />)
    expect(container.querySelector('circle')).not.toBeNull()
  })
})
