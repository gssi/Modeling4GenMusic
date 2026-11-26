export interface BandRange {
  level: string
  min: number
  max: number
}

const EPSILON = 1e-9

/**
 * Problems of the bands of one metric: reversed bounds, overlaps, and parts of [0, 1] left uncovered.
 * Bands that only touch (one ends where the next starts) are fine.
 */
export function bandIssues(bands: BandRange[]): string[] {
  const issues: string[] = []
  for (const band of bands) {
    if (!(band.min < band.max)) issues.push(`La banda ${band.level} ha il minimo non inferiore al massimo.`)
  }
  const sorted = bands.filter((b) => b.min < b.max).sort((a, b) => a.min - b.min)
  if (!sorted.length) return [...issues, 'Nessuna banda copre l’intervallo da 0 a 1.']
  if (sorted[0].min > EPSILON) issues.push(`Nessuna banda copre i valori da 0 a ${sorted[0].min}.`)
  for (let i = 1; i < sorted.length; i++) {
    const previous = sorted[i - 1]
    const current = sorted[i]
    if (current.min < previous.max - EPSILON) {
      issues.push(`Le bande ${previous.level} e ${current.level} si sovrappongono tra ${current.min} e ${Math.min(previous.max, current.max)}.`)
    } else if (current.min > previous.max + EPSILON) {
      issues.push(`Nessuna banda copre i valori da ${previous.max} a ${current.min}.`)
    }
  }
  const top = Math.max(...sorted.map((b) => b.max))
  if (top < 1 - EPSILON) issues.push(`Nessuna banda copre i valori da ${top} a 1.`)
  return issues
}
