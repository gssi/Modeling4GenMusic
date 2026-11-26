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
  version: number
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
