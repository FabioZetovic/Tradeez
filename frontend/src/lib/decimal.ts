/**
 * A number as a learner types it (BUG-089), or null when it cannot be read with certainty.
 *
 * Croatian traders — the author and the jury among them — write 1,5 for one and a half. Read with the comma stripped,
 * "1,5" was 15 and "250,50" was 25,050. So a comma before one or two last digits, with no dot, is the decimal point;
 * commas or dots between groups of three are thousands, either way round ("1,234.50", "1.234,50"); anything else is not
 * guessed at.
 *
 * @param strip characters that may stand around the number and mean nothing to it — a currency or a percent sign
 */
export function parseDecimal(text: string, strip = ''): number | null {
  let s = text.replace(/\s/g, '')
  for (const c of strip) s = s.split(c).join('')
  if (s === '') return null
  let normal: string
  if (/^-?\d+(\.\d+)?$/.test(s) || /^-?\.\d+$/.test(s)) normal = s
  else if (/^-?\d+,\d{1,2}$/.test(s)) normal = s.replace(',', '.')
  else if (/^-?[1-9]\d{0,2}(,\d{3})+(\.\d+)?$/.test(s)) normal = s.replace(/,/g, '')
  else if (/^-?[1-9]\d{0,2}(\.\d{3})+(,\d+)?$/.test(s)) normal = s.replace(/\./g, '').replace(',', '.')
  else return null
  const n = Number(normal)
  return Number.isFinite(n) ? n : null
}

/** BUG-089: said beside a price box whose text is not a price, instead of putting the old one back in silence. */
export const NOT_A_PRICE = 'Not a price: write it like 20011.25 or 20011,25.'
