/**
 * Rhythmic pattern notation: duration tokens separated by spaces. A token is one of 1, 2, 4, 8, 16
 * (whole note to sixteenth), optionally followed by a dot: `8. 16` is a dotted eighth and a sixteenth.
 * The backend validator accepts exactly the same grammar.
 */

export type Duration = 1 | 2 | 4 | 8 | 16

export interface RhythmToken {
  duration: Duration
  dotted: boolean
}

export type RhythmParse = { ok: true; tokens: RhythmToken[] } | { ok: false; error: string }

const DURATIONS = new Set(['1', '2', '4', '8', '16'])

export function parseRhythm(expression: string | null | undefined): RhythmParse {
  if (!expression || !expression.trim()) return { ok: false, error: 'il pattern è vuoto' }
  const tokens: RhythmToken[] = []
  for (const text of expression.trim().split(/\s+/)) {
    const dotted = text.endsWith('.')
    const digits = dotted ? text.slice(0, -1) : text
    if (!DURATIONS.has(digits)) {
      return { ok: false, error: `token non valido '${text}' (ammessi: 1, 2, 4, 8, 16, con punto opzionale)` }
    }
    tokens.push({ duration: Number(digits) as Duration, dotted })
  }
  return { ok: true, tokens }
}

export function isValidRhythm(expression: string | null | undefined): boolean {
  return parseRhythm(expression).ok
}

/** Canonical spelling of a valid pattern; an invalid one is only trimmed. */
export function normalizeRhythm(expression: string | null | undefined): string {
  if (!expression) return ''
  const parsed = parseRhythm(expression)
  return parsed.ok ? parsed.tokens.map((t) => `${t.duration}${t.dotted ? '.' : ''}`).join(' ') : expression.trim()
}

/** Length of a token in sixty-fourths of a whole note, so that dotted values stay integers. */
export function tokenUnits(token: RhythmToken): number {
  const base = 64 / token.duration
  return token.dotted ? base * 1.5 : base
}

export function rhythmUnits(tokens: RhythmToken[]): number {
  return tokens.reduce((sum, t) => sum + tokenUnits(t), 0)
}

/** One ABC note per token, on a single pitch, with the unit length set to a sixteenth. */
export function rhythmToAbcNotes(tokens: RhythmToken[], pitch = 'B'): string {
  return tokens
    .map((token) => {
      const sixteenths = 16 / token.duration
      if (!token.dotted) return `${pitch}${sixteenths === 1 ? '' : sixteenths}`
      const doubled = sixteenths * 3
      return doubled % 2 === 0 ? `${pitch}${doubled / 2}` : `${pitch}${doubled}/2`
    })
    .join('')
}

/** A complete ABC tune that shows the pattern alone, without meter or bar lines. */
export function rhythmToAbc(tokens: RhythmToken[]): string {
  return `X:1\nM:none\nL:1/16\nK:C clef=treble\n${rhythmToAbcNotes(tokens)}`
}

/** How to write a pattern, for the people who type one. */
export const RHYTHM_HINT = 'Scrivi le durate separate da spazi: 1, 2, 4, 8, 16. Il punto allunga della metà, per esempio «8. 16».'
