import { describe, expect, it } from 'vitest'
import { parseDecimal } from './decimal'

describe('parseDecimal (BUG-089)', () => {
  it('reads a dot as the decimal point, as before', () => {
    expect(parseDecimal('1.5')).toBe(1.5)
    expect(parseDecimal('20011.25')).toBe(20011.25)
    expect(parseDecimal('.5')).toBe(0.5)
    expect(parseDecimal('250')).toBe(250)
    expect(parseDecimal('-3.75')).toBe(-3.75)
  })

  it('reads a comma before one or two last digits as the decimal point', () => {
    expect(parseDecimal('1,5')).toBe(1.5)
    expect(parseDecimal('250,50')).toBe(250.5)
    expect(parseDecimal('4,50')).toBe(4.5)
    expect(parseDecimal('20011,25')).toBe(20011.25)
    expect(parseDecimal('0,5')).toBe(0.5)
  })

  it('reads groups of three as thousands, either way round', () => {
    expect(parseDecimal('1,234')).toBe(1234)
    expect(parseDecimal('20,011')).toBe(20011)
    expect(parseDecimal('1,234.50')).toBe(1234.5)
    expect(parseDecimal('1.234,50')).toBe(1234.5)
    expect(parseDecimal('1.234.567')).toBe(1234567)
  })

  it('takes away what may stand around the number, and spaces', () => {
    expect(parseDecimal(' $ 250,50 ', '$')).toBe(250.5)
    expect(parseDecimal('2,5 %', '%')).toBe(2.5)
    expect(parseDecimal('1 234,50')).toBe(1234.5)
  })

  it('does not guess at what it cannot read with certainty', () => {
    for (const text of ['', ' ', 'abc', '1,5,5', '0,125', '1,2345', '12,34,567', '1.5.5', '1,5.5', '1e3', '$5', '5%']) {
      expect(parseDecimal(text), text).toBeNull()
    }
  })
})
