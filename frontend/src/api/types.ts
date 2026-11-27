/** Shapes of the backend JSON contract. */

export interface TypeRef {
  metamodel: string
  name: string
}

export type AttributeType = 'string' | 'int' | 'double' | 'boolean' | 'enum'

export interface AttributeDescriptor {
  name: string
  type: AttributeType
  enumType?: TypeRef
  literals?: string[]
  lower: number
  upper: number
  defaultValue?: string | number | boolean
}

export interface ReferenceDescriptor {
  name: string
  target: TypeRef
  containment: boolean
  lower: number
  upper: number
}

export interface ClassDescriptor {
  name: string
  abstract: boolean
  supertypes: TypeRef[]
  attributes: AttributeDescriptor[]
  references: ReferenceDescriptor[]
}

export interface MetamodelDescriptor {
  nsURI: string
  prefix: string
  name: string
  classes: ClassDescriptor[]
  enums: { name: string; literals: string[] }[]
}

export interface MetamodelSummary {
  nsURI: string
  prefix: string
  name: string
}

export type AttributeValue = string | number | boolean

/** Reference target: `model` is absent when the target lives in the same model. */
export interface Ref {
  model?: string
  id: string
  unresolved?: boolean
}

export interface ModelObject {
  id: string
  eClass: string
  attributes: Record<string, AttributeValue>
  contents: Record<string, ModelObject[]>
  references: Record<string, Ref[]>
}

export interface ModelHeader {
  id: string
  name: string
  metamodel: string
  role?: string
  /** Where the model is shown in the workspace, levels separated by `/`. */
  folder?: string
  version: number
}

export interface RuleSetRule {
  name: string
  severity: Severity
  comment?: string
  guard?: string
  check?: string
  message?: string
  fixes: { title?: string; guard?: string; body?: string }[]
  source: string
  line: number
}

/** A validation script as the engine parses it. */
export interface RuleSet {
  script: string
  metamodel: string
  exceptRoles: string[]
  /** Name of the validated model inside the script. */
  model: string
  related: { alias: string; metamodel: string; reference: string }[]
  tools: string[]
  source: string
  problems: { line: number; column: number; reason: string }[]
  contexts: { type: string; rules: RuleSetRule[] }[]
  operations: { name: string; context?: string; comment?: string; source: string; line: number }[]
  /** True when the script was edited and differs from the one shipped with the application. */
  modified: boolean
  /** Models on which a trial run stopped with an error; filled only by a check. */
  failures: { modelId: string; modelName: string; message: string }[]
}

/** The transformation script as the engine parses it. */
export interface TransformationScript {
  script: string
  inputs: { alias: string; metamodel: string }[]
  outputs: { alias: string; metamodel: string }[]
  tools: string[]
  source: string
  problems: RuleSet['problems']
  rules: { name: string; from: string; to: string[]; comment?: string; guard?: string; source: string; line: number }[]
  /** `pre` and `post` blocks, in the order they run. */
  blocks: { kind: 'pre' | 'post'; name?: string; comment?: string; source: string; line: number }[]
  operations: RuleSet['operations']
  modified: boolean
  failures: RuleSet['failures']
}

/** Whether the rule assistant can answer, and with which language model. */
export interface AssistantStatus {
  available: boolean
  model: string
  /** What to do when it is not available. */
  reason?: string | null
}

/** A rule written by the assistant from a description, with the outcome of the checks that guard saving. */
export interface RuleProposal {
  rule: string
  /** The script the request carried, with the rule added at the end. */
  source: string
  /** Line of `source` where the added part starts. */
  line: number
  problems: RuleSet['problems']
  failures: RuleSet['failures']
  attempts: number
  seconds: number
  model: string
  /** For a validation rule that passes the checks: what it reports on the workspace models. */
  effects?: { models: number; reports: { modelName: string; rule: string; elementId?: string; message: string }[] } | null
}

/** One saved state in the history of a model. */
export interface ModelVersion {
  version: number
  name: string
  role?: string
  savedAt: string
  origin?: 'CREATED' | 'IMPORTED' | 'SAVED' | 'FIXED' | 'DERIVED' | 'RESTORED'
  restoredFrom?: number
}

export interface ModelDocument extends ModelHeader {
  roots: ModelObject[]
}

export interface Position {
  x: number
  y: number
}

export interface ModelLayout {
  nodes?: Record<string, Position>
}

export type Severity = 'ERROR' | 'WARNING' | 'INFO'

export interface Diagnostic {
  id: string
  modelId: string
  elementId: string | null
  severity: Severity
  constraint: string
  message: string
  fixes: { title: string }[]
}

export interface DerivationResult {
  derivedSpecModelId: string
  weavingModelId: string
  diagnostics: Diagnostic[]
}

export interface GenerationCheck {
  name: string
  passed: boolean
  /** False for remarks and preferences, which do not make the exercise invalid. */
  blocking: boolean
  message: string
}

export interface GenerationReport {
  valid: boolean
  checks: GenerationCheck[]
}

export interface GenerationResult {
  abc: string
  metadata?: Record<string, unknown>
  report: GenerationReport
  /** Every exercise that was written, in order; the chosen one is among them. */
  attempts?: { abc: string; report: GenerationReport }[]
}

export interface GeneratorInfo {
  id: string
  label: string
  model: string | null
  available: boolean
  /** What to do when the generator is not available. */
  reason: string | null
  isDefault: boolean
}
