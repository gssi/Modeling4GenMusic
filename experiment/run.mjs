// Runs the experiment cases: for each folder of cases/ it imports the profile and the intent into
// a studio backend, derives the exercise spec, asks the music model for an exercise and writes
// what came out next to the inputs. One run per case, the result is kept as it is.
//
//   node experiment/run.mjs [--api http://localhost:8091/api] [--generator ollama] [--force] [case ...]
//
// The backend must be already running on a workspace of its own (see README.md). A case that
// already has a result is skipped unless --force is given.
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const CASES = join(HERE, 'cases')
const FIXTURES = join(HERE, '..', 'fixtures', 'giulia')
const SHARED = ['default.needs.xmi', 'piano.quality.xmi']

const args = process.argv.slice(2)
const option = (name, fallback) => {
  const at = args.indexOf(name)
  return at < 0 ? fallback : args.splice(at, 2)[1]
}
const api = option('--api', 'http://localhost:8091/api')
const generator = option('--generator', 'ollama')
const force = args.includes('--force')
const wanted = args.filter((a) => !a.startsWith('--')).map((a) => a.toLowerCase())

// ---------------------------------------------------------------- the studio API

async function call(path, init) {
  const response = await fetch(api + path, init)
  if (!response.ok) throw new Error(`${init?.method ?? 'GET'} ${path}: ${response.status} ${await response.text()}`)
  return response
}

const json = (method, body) => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

async function importXmi(file, name, role) {
  const id = name.replace(/\.xmi$/, '')
  // a model left by an earlier run is replaced, with what was derived from it
  for (const stale of [`${id}.weaving`, `${id}.derived`, id]) {
    const found = await fetch(`${api}/models/${stale}`)
    if (found.ok) await call(`/models/${stale}`, { method: 'DELETE' })
  }
  const form = new FormData()
  form.append('file', new Blob([readFileSync(file)], { type: 'text/xml' }), name)
  if (role) form.append('role', role)
  return (await call('/models/import', { method: 'POST', body: form })).json()
}

const model = async (id) => (await call(`/models/${id}`)).json()
const xmi = async (id) => (await call(`/models/${id}/xmi`)).text()

// ---------------------------------------------------------------- wording, in English

const NOTE_NAMES = ['C', 'C♯', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B']
const note = (midi) => `${NOTE_NAMES[midi % 12]}${Math.floor(midi / 12) - 1}`

const INTERVALS = ['unison', 'minor second', 'major second', 'minor third', 'major third', 'perfect fourth', 'tritone', 'perfect fifth',
  'minor sixth', 'major sixth', 'minor seventh', 'major seventh', 'octave']
const interval = (semitones) => `${semitones} semitones${INTERVALS[semitones] ? ` (${INTERVALS[semitones]})` : ''}`

const DURATIONS = { 1: 'whole note', 2: 'half note', 4: 'quarter note', 8: 'eighth note', 16: 'sixteenth note' }
const rhythm = (expression) =>
  String(expression).trim().split(/\s+/).map((token) => (token.endsWith('.') ? `dotted ${DURATIONS[token.slice(0, -1)] ?? token}` : (DURATIONS[token] ?? token))).join(' + ')

const words = (camel) => camel.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (c) => c.toUpperCase()).replace(/ ./g, (c) => c.toLowerCase())
const times = (n) => (n === 1 ? 'once' : n === 2 ? 'twice' : `${n} times`)
const QUALITY = { RHYTHMIC_COMPLEXITY: 'Rhythmic complexity', MELODIC_COMPLEXITY: 'Melodic complexity', DENSITY: 'Density', LENGTH: 'Length' }

// ---------------------------------------------------------------- requirements from the derived spec

/**
 * Where an element or an attribute of the derived spec comes from, read from the weaving: who asked
 * for it, the rule that wrote it and, when a need of the learner is involved, the type of the need.
 */
function originOf(weaving, catalog, targetId, feature) {
  const links = (weaving.roots[0].contents.links ?? []).filter(
    (l) => l.references.target?.[0]?.id === targetId && (l.attributes.targetFeature ?? null) === (feature ?? null) && l.eClass !== 'Resolution',
  )
  if (!links.length) return { source: 'unknown', label: 'Not traced' }
  const sources = links.flatMap((l) => l.contents.sources ?? [])
  const origins = new Set(sources.map((s) => s.attributes.origin ?? 'TEACHER'))
  const last = links[links.length - 1]
  const trace = { rules: links.map((l) => l.attributes.rule), kind: last.eClass, rationale: last.attributes.rationale }
  if (last.eClass === 'ConflictFix') Object.assign(trace, { originalValue: last.attributes.originalValue, newValue: last.attributes.newValue })

  const effect = sources.find((s) => s.attributes.origin === 'NEED_CATALOG')?.references.element?.[0]?.id
  const type = effect && (catalog.roots[0].contents.types ?? []).find((t) => (t.contents.effects ?? []).some((e) => e.id === effect))
  if (type) {
    const needType = words(type.attributes.name)
    const overrides = origins.has('TEACHER') || links.some((l) => l.attributes.rule === 'CopyIntent')
    return { source: 'need', needType, label: overrides ? `The teacher's request, limited by the need “${needType}”` : `The learner's need “${needType}”`, ...trace }
  }
  if (origins.has('TEACHER') && origins.has('PROFILE')) {
    const label = last.eClass === 'ConflictFix' ? "The teacher's request, limited by the learner profile" : "The teacher's request, weighted by the learner profile"
    return { source: 'teacher+profile', label, ...trace }
  }
  if (origins.has('TEACHER')) return { source: 'teacher', label: 'The teacher', ...trace }
  if (origins.has('PROFILE')) return { source: 'profile', label: 'The learner profile', ...trace }
  return { source: 'default', label: 'A default of the derivation rules', ...trace }
}

function requirementsOf(caseId, spec, weaving, catalog) {
  const root = spec.roots[0]
  const a = root.attributes
  const list = []
  const add = (target, feature, kind, text, extra = {}) =>
    list.push({ id: `${caseId}-R${list.length + 1}`, kind, text, enforced: true, strength: 'required', origin: originOf(weaving, catalog, target, feature), ...extra })

  if (a.key) add(root.id, 'key', 'key', `The exercise is in the key of ${a.key} major.`)
  if (a.meter) add(root.id, 'meter', 'meter', `The exercise is in ${a.meter} time.`)
  if (a.bars) add(root.id, 'bars', 'bars', `The exercise is ${a.bars} bars long.`)
  if (a.maxBpm) add(root.id, 'maxBpm', 'tempo', `The tempo is not faster than ${a.maxBpm} beats per minute.`)

  for (const c of root.contents.constraints ?? []) {
    const soft = c.attributes.strength === 'SOFT'
    const strength = soft ? { strength: 'preferred' } : {}
    const pattern = c.contents.pattern?.[0]?.attributes ?? {}
    const isRhythm = (pattern.kind ?? 'RHYTHM') === 'RHYTHM'
    switch (c.eClass) {
      case 'ContainsPattern': {
        const count = c.attributes.minOccurrences ?? 1
        if (isRhythm) add(c.id, null, 'containsPattern', `The rhythm ${rhythm(pattern.expression)} occurs at least ${times(count)}.`, { pattern: pattern.expression, minOccurrences: count, ...strength })
        else add(c.id, null, 'containsPattern', `The ${String(pattern.kind).toLowerCase()} pattern “${pattern.expression}” occurs at least ${times(count)}.`, { pattern: pattern.expression, enforced: false, ...strength })
        break
      }
      case 'ExcludesPattern':
        if (isRhythm) add(c.id, null, 'excludesPattern', `The rhythm ${rhythm(pattern.expression)} never occurs.`, { pattern: pattern.expression, ...strength })
        else add(c.id, null, 'excludesPattern', `The ${String(pattern.kind).toLowerCase()} pattern “${pattern.expression}” never occurs.`, { pattern: pattern.expression, enforced: false, ...strength })
        break
      case 'RangeConstraint':
        add(c.id, null, 'range', `Every note lies between ${note(c.attributes.lowestMidi)} and ${note(c.attributes.highestMidi)}, both included.`,
          { lowestMidi: c.attributes.lowestMidi, highestMidi: c.attributes.highestMidi, ...strength })
        break
      case 'SpanConstraint': {
        const hand = (c.attributes.hand ?? 'LEFT').toLowerCase()
        add(c.id, null, 'handSpan', `No two notes played together by the ${hand} hand are further apart than ${interval(c.attributes.maxSimultaneousSemitones)}.`,
          { semitones: c.attributes.maxSimultaneousSemitones, ...strength })
        add(c.id, null, 'positionChanges', `No melodic leap between two consecutive notes is wider than ${interval(c.attributes.maxStretchInPosition)}, so the ${hand} hand never has to change position.`,
          { semitones: c.attributes.maxStretchInPosition, ...strength })
        break
      }
      case 'RenderConstraint':
        // how the score is shown: the page applies it, the music model is not told
        add(c.id, null, 'render', `The staff is displayed at least ${c.attributes.staffScale} times its normal size.`, { staffScale: c.attributes.staffScale, appliedBy: 'display', ...strength })
        break
      default:
        add(c.id, null, c.eClass, `${words(c.eClass)} (${JSON.stringify(c.attributes)}).`, { enforced: false })
    }
  }

  // Quality goals are in the spec but neither the generator nor the checks use them yet.
  const goals = (root.contents.qualityGoals ?? []).map((g, i) => ({
    id: `${caseId}-Q${i + 1}`,
    kind: 'qualityGoal',
    text: `${QUALITY[g.attributes.attribute ?? 'RHYTHMIC_COMPLEXITY']}: ${(g.attributes.level ?? 'LOW').toLowerCase()}.`,
    enforced: false,
    strength: 'not enforced',
    origin: originOf(weaving, catalog, g.id, null),
  }))
  return { requirements: list, qualityGoals: goals }
}

// ---------------------------------------------------------------- the run

async function run(folder, catalog) {
  const dir = join(CASES, folder)
  const info = JSON.parse(readFileSync(join(dir, 'case.json'), 'utf8'))
  if (existsSync(join(dir, 'result.json')) && !force) {
    console.log(`${info.id}: already generated, skipped (use --force to run it again)`)
    return
  }
  await importXmi(join(dir, `${folder}.profile.xmi`), `${folder}.profile.xmi`)
  await importXmi(join(dir, `${folder}.intent.xmi`), `${folder}.intent.xmi`, 'intent')
  const warnings = await (await call(`/models/${folder}.intent/validate`, { method: 'POST' })).json()

  const derivation = await (await call('/derivations', json('POST', { learnerModelId: `${folder}.profile`, teacherIntentModelId: `${folder}.intent`, qualityModelId: 'piano.quality' }))).json()
  const derivedId = `${folder}.intent.derived`
  const weavingId = `${folder}.intent.weaving`
  const spec = await model(derivedId)
  const weaving = await model(weavingId)

  console.log(`${info.id}: derived, asking ${generator} for the exercise…`)
  const generation = await (await call('/generations', json('POST', { specModelId: derivedId, qualityModelId: 'piano.quality', generator }))).json()

  writeFileSync(join(dir, `${derivedId}.xmi`), await xmi(derivedId))
  writeFileSync(join(dir, `${weavingId}.xmi`), await xmi(weavingId))
  writeFileSync(join(dir, 'exercise.abc'), generation.abc.endsWith('\n') ? generation.abc : `${generation.abc}\n`)
  const staffScale = Math.max(1, ...(spec.roots[0].contents.constraints ?? []).filter((c) => c.eClass === 'RenderConstraint').map((c) => c.attributes.staffScale ?? 1))
  const requirements = { case: info.id, staffScale, ...requirementsOf(info.id, spec, weaving, catalog) }
  writeFileSync(join(dir, 'requirements.json'), `${JSON.stringify(requirements, null, 2)}\n`)
  const chosen = generation.attempts.findIndex((attempt) => attempt.abc === generation.abc) + 1
  const result = {
    case: info.id,
    intentWarnings: warnings.map((w) => ({ rule: w.constraint, severity: w.severity, message: w.message })),
    derivationDiagnostics: derivation.diagnostics ?? [],
    metadata: generation.metadata,
    valid: generation.report.valid,
    report: generation.report,
    chosenAttempt: chosen,
    attempts: generation.attempts,
  }
  writeFileSync(join(dir, 'result.json'), `${JSON.stringify(result, null, 2)}\n`)
  console.log(`${info.id}: ${generation.report.valid ? 'valid' : 'NOT valid'} after ${generation.attempts.length} attempt(s), ${requirements.requirements.length} requirements`)
}

const folders = readdirSync(CASES, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && existsSync(join(CASES, entry.name, 'case.json')))
  .map((entry) => entry.name)
  .sort()

// The catalog and the quality model are the same for every case: the copies kept with the cases
// are the ones the run used.
mkdirSync(join(CASES, 'shared'), { recursive: true })
for (const file of SHARED) {
  copyFileSync(join(FIXTURES, file), join(CASES, 'shared', file))
  await importXmi(join(FIXTURES, file), file)
}
const catalog = await model('default.needs')

for (const folder of folders.filter((f) => !wanted.length || wanted.includes(f))) await run(folder, catalog)

const index = folders.map((folder) => {
  const info = JSON.parse(readFileSync(join(CASES, folder, 'case.json'), 'utf8'))
  return { id: info.id, folder, title: info.title, generated: existsSync(join(CASES, folder, 'result.json')) }
})
writeFileSync(join(CASES, 'cases.json'), `${JSON.stringify(index, null, 2)}\n`)
