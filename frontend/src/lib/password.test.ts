import { describe, expect, it } from 'vitest'
import { passwordRules } from './password'

const met = (password: string, email = 'marta.kovac@example.com', name = 'Marta') =>
  Object.fromEntries(passwordRules(password, email, name).map((r) => [r.id, r.met]))

describe('password-1.0.0 as the browser checks it (DEC-064)', () => {
  it('passes a long ordinary passphrase on every rule', () => {
    expect(met('blue lanterns over the harbour')).toEqual({ length: true, bytes: true, variety: true, personal: true })
  })

  it('counts characters as the server does: an emoji is one, and 72 bytes is the ceiling', () => {
    expect(met('short phrase').length).toBe(true)
    expect(met('short phras').length).toBe(false)
    expect(met('🌊🌙🚀🌿🌵🐟🍋🎸💡🧲🌋').length).toBe(false) // eleven characters, twenty-two UTF-16 units
    expect(met('a'.repeat(72)).bytes).toBe(true)
    expect(met('é'.repeat(37)).bytes).toBe(false) // 74 bytes
  })

  it('wants five different characters and no straight run', () => {
    expect(met('abababababababab').variety).toBe(false)
    expect(met('abcdefghijklmnop').variety).toBe(false)
    expect(met('zyxwvutsrqponmlk').variety).toBe(false)
    expect(met('lanterns 1234 harbour').variety).toBe(true)
  })

  it('refuses the email name, the display name and the app name, but not a two-letter name', () => {
    expect(met('hello marta.kovac there').personal).toBe(false)
    expect(met('lanterns for MARTA forever').personal).toBe(false)
    expect(met('I practise on Tradeez daily').personal).toBe(false)
    expect(met('blue lanterns over the harbour', 'al@example.com', 'Al').personal).toBe(true)
    // Nothing typed yet is not met: the list starts unticked.
    expect(met('').personal).toBe(false)
  })
})
