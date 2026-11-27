import type { ModelDocument, ModelObject } from '../api/types'

/**
 * Reading of a catalog of need types (metamodel `needs`) and of the needs of a learner that are
 * typed by it. The catalog is a model like the others: the form of the learner shows whatever
 * types it holds, so a type added there needs no change here.
 */

export const NEEDS = 'http://music/needs'

export type ParameterKind = 'INTEGER' | 'DECIMAL' | 'HAND' | 'INTERVAL' | 'NOTE' | 'RHYTHM'

export interface NeedParameter {
  id: string
  name: string
  label: string
  kind: ParameterKind
  required: boolean
  min?: number
  max?: number
  defaultValue?: string
}

export interface NeedType {
  id: string
  name: string
  label: string
  description?: string
  parameters: NeedParameter[]
}

export interface NeedCatalog {
  model: string
  id: string
  name: string
  types: NeedType[]
}

const text = (object: ModelObject, name: string): string | undefined => {
  const value = object.attributes[name]
  return value === undefined || value === '' ? undefined : String(value)
}

const number = (object: ModelObject, name: string): number | undefined => {
  const value = object.attributes[name]
  return typeof value === 'number' ? value : undefined
}

/** The catalog a model holds, or null when the model is not a catalog. */
export function readCatalog(doc: ModelDocument): NeedCatalog | null {
  const root = doc.roots.find((r) => r.eClass === 'NeedCatalog')
  if (!root) return null
  return {
    model: doc.id,
    id: root.id,
    name: text(root, 'name') ?? doc.name,
    types: (root.contents.types ?? []).map((type) => ({
      id: type.id,
      name: text(type, 'name') ?? type.id,
      label: text(type, 'label') ?? text(type, 'name') ?? type.id,
      description: text(type, 'description'),
      parameters: (type.contents.parameters ?? []).map((parameter) => ({
        id: parameter.id,
        name: text(parameter, 'name') ?? parameter.id,
        label: text(parameter, 'label') ?? text(parameter, 'name') ?? parameter.id,
        // an unset enum attribute has the first literal as its value
        kind: (text(parameter, 'kind') ?? 'INTEGER') as ParameterKind,
        required: parameter.attributes.required !== false,
        min: number(parameter, 'min'),
        max: number(parameter, 'max'),
        defaultValue: text(parameter, 'defaultValue'),
      })),
    })),
  }
}

/** The type of a need in a catalog, undefined when the need points elsewhere. */
export function typeOf(need: ModelObject, catalog: NeedCatalog | null): NeedType | undefined {
  const ref = need.references.type?.[0]
  return ref && catalog && ref.model === catalog.model ? catalog.types.find((t) => t.id === ref.id) : undefined
}

export function slotOf(need: ModelObject, parameter: NeedParameter): ModelObject | undefined {
  return (need.contents.slots ?? []).find((slot) => slot.references.parameter?.[0]?.id === parameter.id)
}

/** What the need says for a parameter: its slot, or else the default of the type. */
export function parameterValue(need: ModelObject, parameter: NeedParameter): string | undefined {
  const slot = slotOf(need, parameter)
  return (slot && text(slot, 'value')?.trim()) || parameter.defaultValue
}

export function newSlot(needId: string, catalog: NeedCatalog, parameter: NeedParameter, value: string | undefined): ModelObject {
  return {
    id: `${needId}_${parameter.name}`,
    eClass: 'Slot',
    attributes: value === undefined ? {} : { value },
    contents: {},
    references: { parameter: [{ model: catalog.model, id: parameter.id }] },
  }
}

/** A need of a type, with a slot for every parameter that has a default: the values can then be traced to the profile. */
export function newNeed(id: string, catalog: NeedCatalog, type: NeedType): ModelObject {
  return {
    id,
    eClass: 'Need',
    attributes: {},
    contents: { slots: type.parameters.filter((p) => p.defaultValue !== undefined).map((p) => newSlot(id, catalog, p, p.defaultValue)) },
    references: { type: [{ model: catalog.model, id: type.id }] },
  }
}

/** The bounds of a parameter in words, undefined when it has none. */
export function boundsHint(parameter: NeedParameter): string | undefined {
  const { min, max } = parameter
  if (min !== undefined && max !== undefined) return `Da ${min} a ${max}.`
  if (min !== undefined) return `Almeno ${min}.`
  if (max !== undefined) return `Al massimo ${max}.`
  return undefined
}
