/**
 * Interval pattern notation: melodic steps in semitones separated by spaces. Every step carries its
 * sign (`+3` up a minor third, `-2` down a major second); `0` repeats the note.
 * The backend validator accepts exactly the same grammar.
 */

export type IntervalParse = { ok: true; steps: number[] } | { ok: false; error: string }

/** Widest step that can be written: two octaves. */
export const MAX_STEP = 24

export function parseIntervals(expression: string | null | undefined): IntervalParse {
  if (!expression || !expression.trim()) return { ok: false, error: 'il pattern è vuoto' }
  const steps: number[] = []
  for (const text of expression.trim().split(/\s+/)) {
    if (!/^(0|[+-][1-9][0-9]?)$/.test(text)) {
      return { ok: false, error: `passo non valido '${text}' (ammessi: 0, oppure un numero di semitoni con il segno, come +3 o -2)` }
    }
    const step = Number(text)
    if (Math.abs(step) > MAX_STEP) {
      return { ok: false, error: `passo troppo ampio '${text}' (al massimo ${MAX_STEP} semitoni)` }
    }
    steps.push(step)
  }
  return { ok: true, steps }
}

export function isValidIntervals(expression: string | null | undefined): boolean {
  return parseIntervals(expression).ok
}

/** Widest step, whatever its direction. */
export function maxLeap(steps: number[]): number {
  return steps.reduce((widest, step) => Math.max(widest, Math.abs(step)), 0)
}

export function normalizeIntervals(steps: number[]): string {
  return steps.map((s) => (s > 0 ? `+${s}` : String(s))).join(' ')
}

const NAMES = [
  'unisono',
  'seconda minore',
  'seconda maggiore',
  'terza minore',
  'terza maggiore',
  'quarta giusta',
  'tritono',
  'quinta giusta',
  'sesta minore',
  'sesta maggiore',
  'settima minore',
  'settima maggiore',
  'ottava',
  'nona minore',
  'nona maggiore',
  'decima minore',
  'decima maggiore',
]

/** Italian name of an interval of that many semitones, for example "sesta maggiore" for 9. */
export function intervalName(semitones: number): string {
  const size = Math.abs(semitones)
  return size < NAMES.length ? NAMES[size] : `${size} semitoni`
}

/** The hand openings offered in the learner form, from a third to a tenth, in semitones. */
export const REACH_CHOICES: number[] = [3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]

/** How to write an interval pattern, for the people who type one. */
export const INTERVAL_HINT = 'Scrivi i passi in semitoni con il segno, separati da spazi: «+3 -2 +12» sale di una terza minore, scende di un tono, sale di un’ottava.'
