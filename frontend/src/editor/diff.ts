import type { AttributeValue, ModelObject, Ref } from '../api/types'
import { allObjects } from './modelOps'

export interface AttributeChange {
  name: string
  before: AttributeValue | undefined
  after: AttributeValue | undefined
}

export interface ObjectChange {
  object: ModelObject
  attributes: AttributeChange[]
  /** Names of the references whose targets differ. */
  references: string[]
}

export interface ModelDiff {
  /** Objects that exist only in `after`. */
  added: ModelObject[]
  /** Objects that exist only in `before`. */
  removed: ModelObject[]
  changed: ObjectChange[]
}

const refKey = (ref: Ref) => `${ref.model ?? ''}#${ref.id}`

function sameTargets(a: Ref[] = [], b: Ref[] = []): boolean {
  return a.length === b.length && a.every((ref, i) => refKey(ref) === refKey(b[i]))
}

/**
 * What differs between two contents of the same model. Objects are matched by id: one whose id
 * changed counts as removed and added. Where an object sits in the containment tree is not compared.
 */
export function diffRoots(before: ModelObject[], after: ModelObject[]): ModelDiff {
  const old = new Map(allObjects(before).map((o) => [o.id, o]))
  const current = allObjects(after)
  const seen = new Set(current.map((o) => o.id))
  const diff: ModelDiff = { added: [], removed: [...old.values()].filter((o) => !seen.has(o.id)), changed: [] }
  for (const object of current) {
    const previous = old.get(object.id)
    if (!previous) {
      diff.added.push(object)
      continue
    }
    const attributes = [...new Set([...Object.keys(previous.attributes), ...Object.keys(object.attributes)])]
      .filter((name) => previous.attributes[name] !== object.attributes[name])
      .map((name) => ({ name, before: previous.attributes[name], after: object.attributes[name] }))
    const references = [...new Set([...Object.keys(previous.references), ...Object.keys(object.references)])].filter(
      (name) => !sameTargets(previous.references[name], object.references[name]),
    )
    if (attributes.length || references.length || previous.eClass !== object.eClass) {
      diff.changed.push({ object, attributes, references })
    }
  }
  return diff
}

export function isEmptyDiff(diff: ModelDiff): boolean {
  return !diff.added.length && !diff.removed.length && !diff.changed.length
}
