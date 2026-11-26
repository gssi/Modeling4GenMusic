import { describe, expect, it } from 'vitest'
import { midiName } from './notes'
import { WEAVING, buildProvenance, elementTints, untraced } from './provenance'
import { bandIssues } from './quality'
import { isValidRhythm, normalizeRhythm, parseRhythm, rhythmToAbc, rhythmToAbcNotes, rhythmUnits } from './rhythm'

const tokens = (expression: string) => {
  const parsed = parseRhythm(expression)
  if (!parsed.ok) throw new Error(parsed.error)
  return parsed.tokens
}

describe('rhythm patterns', () => {
  // Same cases as the backend validator: the two must accept exactly the same expressions.
  it.each(['4', '8 8', '8. 16', '16 8.', '1', '2.', '16 16 16 16', '  8.   16  '])('accepts %j', (expression) => {
    expect(isValidRhythm(expression)).toBe(true)
  })

  it.each(['', '   ', '3', '8 x 16', '8. 32', '04', '8..', '.8', '8,16', '-4'])('rejects %j', (expression) => {
    expect(isValidRhythm(expression)).toBe(false)
  })

  it('rejects a missing expression', () => {
    expect(isValidRhythm(null)).toBe(false)
    expect(isValidRhythm(undefined)).toBe(false)
  })

  it('names the offending token', () => {
    expect(parseRhythm('8 x 16')).toEqual({ ok: false, error: expect.stringContaining("'x'") })
    expect(parseRhythm('8. 32')).toEqual({ ok: false, error: expect.stringContaining("'32'") })
    expect(parseRhythm(' ')).toEqual({ ok: false, error: expect.stringContaining('vuoto') })
  })

  it('parses tokens and lengths', () => {
    expect(tokens('8. 16')).toEqual([
      { duration: 8, dotted: true },
      { duration: 16, dotted: false },
    ])
    expect(rhythmUnits(tokens('8. 16'))).toBe(rhythmUnits(tokens('4')))
    expect(rhythmUnits(tokens('1'))).toBe(4 * rhythmUnits(tokens('4')))
  })

  it('normalizes spacing and leaves invalid input trimmed', () => {
    expect(normalizeRhythm(' 8.  16 ')).toBe('8. 16')
    expect(normalizeRhythm(' foo ')).toBe('foo')
    expect(normalizeRhythm(null)).toBe('')
  })

  it('converts to ABC with a sixteenth as unit length', () => {
    expect(rhythmToAbcNotes(tokens('8. 16'))).toBe('B3B')
    expect(rhythmToAbcNotes(tokens('16 8.'))).toBe('BB3')
    expect(rhythmToAbcNotes(tokens('4 8 8'))).toBe('B4B2B2')
    expect(rhythmToAbcNotes(tokens('16. 1 2.'))).toBe('B3/2B16B12')
    expect(rhythmToAbc(tokens('4'))).toContain('L:1/16')
  })
})

describe('quality bands', () => {
  const band = (level: string, min: number, max: number) => ({ level, min, max })

  it('accepts bands that cover 0 to 1 and only touch', () => {
    expect(bandIssues([band('LOW', 0, 0.3), band('MEDIUM', 0.3, 0.6), band('HIGH', 0.6, 1)])).toEqual([])
  })

  it('reports overlaps, gaps and reversed bounds', () => {
    expect(bandIssues([band('LOW', 0, 0.4), band('MEDIUM', 0.3, 0.6), band('HIGH', 0.6, 1)])[0]).toContain('si sovrappongono')
    expect(bandIssues([band('LOW', 0, 0.2), band('MEDIUM', 0.3, 0.6), band('HIGH', 0.6, 1)])[0]).toContain('da 0.2 a 0.3')
    expect(bandIssues([band('LOW', 0.1, 0.5), band('HIGH', 0.5, 0.9)])).toHaveLength(2)
    expect(bandIssues([band('LOW', 0.5, 0.2)])).toHaveLength(2)
    expect(bandIssues([])).toHaveLength(1)
  })
})

describe('note names', () => {
  it('names MIDI numbers', () => {
    expect(midiName(60)).toBe('Do4')
    expect(midiName(79)).toBe('Sol5')
    expect(midiName(36)).toBe('Do2')
  })
})

describe('provenance', () => {
  const link = (id: string, eClass: string, targetId: string, attributes: Record<string, string>, origin: string, sourceModel: string, sourceId: string) => ({
    id,
    eClass,
    attributes,
    contents: { sources: [{ id: `${id}_s`, eClass: 'SourceEnd', attributes: { origin }, contents: {}, references: { element: [{ model: sourceModel, id: sourceId }] } }] },
    references: { target: [{ model: 'x.derived', id: targetId }] },
  })
  const weaving = {
    id: 'x.weaving',
    name: 'w',
    metamodel: WEAVING,
    version: 1,
    roots: [
      {
        id: 'w',
        eClass: 'DerivationWeaving',
        attributes: {},
        references: { derivedSpec: [{ model: 'x.derived', id: 'spec' }], learner: [{ model: 'p', id: 'giulia' }], teacherIntent: [{ model: 'x', id: 'intent' }] },
        contents: {
          links: [
            link('l1', 'Refinement', 'spec', { rule: 'CopyIntent', targetFeature: 'key' }, 'TEACHER', 'x', 'intent'),
            link('l2', 'ConflictFix', 'spec', { rule: 'TempoLimit', targetFeature: 'maxBpm', originalValue: '90', newValue: '72' }, 'PROFILE', 'p', 'giulia'),
            link('l3', 'Refinement', 'c1', { rule: 'StrengthenLearningPattern' }, 'TEACHER', 'x', 'c0'),
            link('l4', 'Derivation', 'c2', { rule: 'RangeFromLearner' }, 'PROFILE', 'p', 'giulia'),
            link('l5', 'Resolution', 'g1', { rule: 'ResolveThresholds' }, 'QUALITY_MODEL', 'q', 'r_med'),
          ],
        },
      },
    ],
  }

  it('reads links by target element', () => {
    const provenance = buildProvenance(weaving, 'x.derived')!
    expect(provenance.inputs).toEqual({ learner: 'p', teacherIntent: 'x', qualityModel: undefined })
    expect(provenance.byElement.spec.map((l) => l.rule)).toEqual(['CopyIntent', 'TempoLimit'])
    expect(provenance.byElement.spec[1]).toMatchObject({ originalValue: '90', newValue: '72', sources: [{ origin: 'PROFILE', model: 'p', id: 'giulia' }] })
    expect(buildProvenance(weaving, 'other')).toBeNull()
  })

  it('colours teacher elements blue and derived ones green', () => {
    const provenance = buildProvenance(weaving, 'x.derived')!
    expect(elementTints(provenance, 'spec')).toEqual({ attributes: { key: 'teacher', maxBpm: 'derived' } })
    expect(elementTints(provenance, 'c1').node).toBe('teacher')
    expect(elementTints(provenance, 'c2').node).toBe('derived')
    expect(elementTints(provenance, 'g1')).toEqual({ attributes: {} })
  })

  it('lists the elements without a link', () => {
    const provenance = buildProvenance(weaving, 'x.derived')!
    const leaf = (id: string) => ({ id, eClass: 'X', attributes: {}, contents: {}, references: {} })
    expect(untraced(provenance, [leaf('spec'), leaf('c1'), leaf('orphan')])).toEqual(['orphan'])
  })
})
