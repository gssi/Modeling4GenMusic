import type {
  AssistantStatus,
  DerivationResult,
  Diagnostic,
  GenerationResult,
  GeneratorInfo,
  MetamodelDescriptor,
  MetamodelSummary,
  ModelDocument,
  ModelHeader,
  ModelLayout,
  ModelVersion,
  RuleProposal,
  RuleSet,
  TransformationScript,
} from './types'

/** Error returned by the backend, with its HTTP status. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const WORKSPACE = 'default'

function url(path: string, params: Record<string, string | undefined> = {}): string {
  const query = new URLSearchParams({ workspaceId: WORKSPACE })
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) query.set(key, value)
  }
  return `/api${path}?${query}`
}

async function request<T>(input: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(input, init)
  } catch {
    throw new ApiError(0, 'Il server non risponde. Controlla che il backend sia avviato.')
  }
  if (!response.ok) {
    let message = `Richiesta non riuscita (${response.status})`
    try {
      const body = await response.json()
      if (body && typeof body.message === 'string') message = body.message
    } catch {
      // keep the generic message
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

const model = (id: string) => `/models/${encodeURIComponent(id)}`

export const api = {
  listMetamodels: () => request<MetamodelSummary[]>(url('/metamodels')),
  getMetamodel: (prefix: string) => request<MetamodelDescriptor>(url(`/metamodels/${prefix}`)),

  listModels: () => request<ModelHeader[]>(url('/models')),
  createModel: (metamodel: string, name: string, role?: string, folder?: string) =>
    request<ModelDocument>(url('/models'), json('POST', { metamodel, name, role, folder })),
  moveModel: (id: string, folder: string) => request<ModelHeader>(url(`${model(id)}/folder`), json('PUT', { folder })),
  getModel: (id: string) => request<ModelDocument>(url(model(id))),
  saveModel: (doc: ModelDocument) => request<ModelDocument>(url(model(doc.id)), json('PUT', doc)),
  deleteModel: (id: string) => request<void>(url(model(id)), { method: 'DELETE' }),
  importModel: (file: File, role?: string, folder?: string) => {
    const form = new FormData()
    form.append('file', file)
    return request<ModelDocument>(url('/models/import', { role, folder }), { method: 'POST', body: form })
  },

  getLayout: (id: string) => request<ModelLayout>(url(`${model(id)}/layout`)),
  saveLayout: (id: string, layout: ModelLayout) =>
    request<ModelLayout>(url(`${model(id)}/layout`), json('PUT', layout)),

  listVersions: (id: string) => request<ModelVersion[]>(url(`${model(id)}/versions`)),
  getVersion: (id: string, version: number) => request<ModelDocument>(url(`${model(id)}/versions/${version}`)),
  restoreVersion: (id: string, version: number) =>
    request<ModelDocument>(url(`${model(id)}/versions/${version}/restore`), { method: 'POST' }),

  xmiUrl: (id: string) => url(`${model(id)}/xmi`),

  derive: (learnerModelId: string, teacherIntentModelId: string, qualityModelId: string) =>
    request<DerivationResult>(url('/derivations'), json('POST', { learnerModelId, teacherIntentModelId, qualityModelId })),
  listGenerators: () => request<GeneratorInfo[]>(url('/generations/generators')),
  generate: (specModelId: string, qualityModelId?: string, generator?: string) =>
    request<GenerationResult>(url('/generations'), json('POST', { specModelId, qualityModelId, generator })),

  listRules: () => request<RuleSet[]>(url('/rules')),
  checkRules: (script: string, source: string) =>
    request<RuleSet>(url(`/rules/${encodeURIComponent(script)}/check`), json('POST', { source })),
  saveRules: (script: string, source: string) =>
    request<RuleSet>(url(`/rules/${encodeURIComponent(script)}`), json('PUT', { source })),
  resetRules: (script: string) => request<RuleSet>(url(`/rules/${encodeURIComponent(script)}`), { method: 'DELETE' }),
  getTransformation: () => request<TransformationScript>(url('/derivations/script')),
  checkTransformation: (source: string) =>
    request<TransformationScript>(url('/derivations/script/check'), json('POST', { source })),
  saveTransformation: (source: string) => request<TransformationScript>(url('/derivations/script'), json('PUT', { source })),
  resetTransformation: () => request<TransformationScript>(url('/derivations/script'), { method: 'DELETE' }),
  getAssistant: () => request<AssistantStatus>(url('/assistant')),
  proposeRule: (script: string, source: string, description: string) =>
    request<RuleProposal>(url('/assistant/rules'), json('POST', { script, source, description })),
  validate: (id: string) => request<Diagnostic[]>(url(`${model(id)}/validate`), { method: 'POST' }),
  applyFix: (id: string, diagnosticId: string, fixIndex: number) =>
    request<{ model: ModelDocument; diagnostics: Diagnostic[] }>(
      url(`${model(id)}/fixes`),
      json('POST', { diagnosticId, fixIndex }),
    ),
}
