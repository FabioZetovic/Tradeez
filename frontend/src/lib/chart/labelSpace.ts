/**
 * Room for text on a chart canvas (BUG-048).
 *
 * Lines that end together at nearly the same price — the 09:30 open fifteen ticks under the PDH, or
 * the OTE levels of a short leg — would print their labels on top of each other, and two labels
 * written over one another read as neither. Labels are placed most important first; one that would
 * overlap tries a line higher, then gives way. Its line is still drawn: only the words are dropped.
 */

interface Box {
  x: number
  y: number
  width: number
  height: number
}

const LINE_HEIGHT = 12

export class LabelSpace {
  private readonly taken: Box[] = []

  /**
   * Finds room for text whose baseline would be at `y`, starting `x` from the left, `width` wide.
   * Returns the baseline to draw at — `y`, or a line or two above it — or null if there is none.
   */
  place(x: number, y: number, width: number, tries = 2): number | null {
    for (let i = 0; i <= tries; i++) {
      const baseline = y - i * LINE_HEIGHT
      const box = { x, y: baseline - LINE_HEIGHT + 2, width, height: LINE_HEIGHT }
      if (!this.taken.some((t) => overlaps(t, box))) {
        this.taken.push(box)
        return baseline
      }
    }
    return null
  }
}

function overlaps(a: Box, b: Box): boolean {
  return a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
}
