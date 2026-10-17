/**
 * password-1.0.0 (DEC-064) as far as a browser can check it while the password is typed. The server is
 * the judge: it applies these same rules, and also refuses common passwords and ones found in known
 * breaches, which no browser should be sent a list of.
 */

export interface PasswordRule {
  id: 'length' | 'bytes' | 'variety' | 'personal'
  text: string
  met: boolean
}

const MIN_CHARACTERS = 12
const MAX_BYTES = 72
const MIN_DISTINCT = 5
/** Shorter words are too common inside ordinary passwords to refuse. */
const MIN_CONTEXT_WORD = 3

export function passwordRules(password: string, email = '', displayName = ''): PasswordRule[] {
  // Code points, not UTF-16 units: an emoji is one character, as the server counts it.
  const characters = [...password]
  const lower = password.toLowerCase()
  const emailName = email.split('@')[0].toLowerCase()
  const name = displayName.trim().toLowerCase()
  const personal =
    (emailName.length >= MIN_CONTEXT_WORD && lower.includes(emailName)) ||
    (name.length >= MIN_CONTEXT_WORD && lower.includes(name)) ||
    lower.includes('tradeez')
  return [
    { id: 'length', text: `At least ${MIN_CHARACTERS} characters`, met: characters.length >= MIN_CHARACTERS },
    {
      id: 'bytes',
      text: `At most ${MAX_BYTES} bytes: 72 plain letters, fewer with accents or emoji`,
      met: new TextEncoder().encode(password).length <= MAX_BYTES,
    },
    {
      id: 'variety',
      text: `At least ${MIN_DISTINCT} different characters, and not a run like abcdef`,
      met: new Set(characters).size >= MIN_DISTINCT && !straightRun(characters),
    },
    { id: 'personal', text: 'Not your email, your name or the app’s name', met: password.length > 0 && !personal },
  ]
}

/** Three in four neighbouring characters one apart, up or down, as the server reads a run. */
function straightRun(characters: string[]): boolean {
  const codes = characters.map((c) => c.codePointAt(0) ?? 0)
  let steps = 0
  for (let i = 1; i < codes.length; i++) {
    if (Math.abs(codes[i] - codes[i - 1]) === 1) steps++
  }
  return codes.length > 1 && steps * 4 >= (codes.length - 1) * 3
}
