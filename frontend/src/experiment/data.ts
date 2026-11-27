/** What the site reads: the files the experiment runner leaves in experiment/cases/. */

export interface CaseEntry {
  id: string
  folder: string
  title: string
  generated: boolean
}

export interface CaseInfo {
  id: string
  title: string
  learner: string
  needs: { type: string; description: string }[]
  intent: string
  expectation: string
}

export interface Origin {
  source: string
  label: string
  needType?: string
  rules?: string[]
  kind?: string
  rationale?: string
  originalValue?: string
  newValue?: string
}

export interface Requirement {
  id: string
  kind: string
  text: string
  enforced: boolean
  strength: string
  appliedBy?: string
  origin: Origin
}

export interface Requirements {
  case: string
  staffScale: number
  requirements: Requirement[]
}

export interface Check {
  name: string
  passed: boolean
  blocking: boolean
  message: string
}

export interface Report {
  valid: boolean
  checks: Check[]
}

export interface Result {
  case: string
  intentWarnings: { rule: string; severity: string; message: string }[]
  metadata: Record<string, string | number | undefined>
  valid: boolean
  report: Report
  chosenAttempt: number
  attempts: { abc: string; report: Report }[]
}

export interface CaseData {
  entry: CaseEntry
  info: CaseInfo
  requirements: Requirements
  abc: string
}

async function read(path: string): Promise<Response> {
  const response = await fetch(path)
  if (!response.ok) throw new Error(`${path}: ${response.status}`)
  return response
}

export const loadCases = async (): Promise<CaseEntry[]> => (await read('cases.json')).json()

export async function loadCase(entry: CaseEntry): Promise<CaseData> {
  const [info, requirements, abc] = await Promise.all([
    read(`${entry.folder}/case.json`).then((r) => r.json()),
    read(`${entry.folder}/requirements.json`).then((r) => r.json()),
    read(`${entry.folder}/exercise.abc`).then((r) => r.text()),
  ])
  return { entry, info, requirements, abc }
}

/** The checks, the discarded attempts and the request sent to the music model: researchers only. */
export const loadResult = async (entry: CaseEntry): Promise<Result> => (await read(`${entry.folder}/result.json`)).json()

/** The models of a case that can be downloaded, in the order of the pipeline. */
export function downloads(entry: CaseEntry): { label: string; href: string }[] {
  const f = entry.folder
  return [
    { label: 'Exercise (ABC notation)', href: `${f}/exercise.abc` },
    { label: 'Learner profile (XMI)', href: `${f}/${f}.profile.xmi` },
    { label: 'Teaching intent (XMI)', href: `${f}/${f}.intent.xmi` },
    { label: 'Derived exercise spec (XMI)', href: `${f}/${f}.intent.derived.xmi` },
    { label: 'Derivation trace (XMI)', href: `${f}/${f}.intent.weaving.xmi` },
    { label: 'Catalog of need types (XMI)', href: 'shared/default.needs.xmi' },
    { label: 'Requirements (JSON)', href: `${f}/requirements.json` },
  ]
}
