import type { ModelObject, ReferenceDescriptor, TypeRef } from '../api/types'
import {
  type Metamodels,
  conformsTo,
  containmentReferences,
  findClass,
  hasRoom,
  plainReferences,
} from './metamodel'
import { allObjects } from './modelOps'

const typeOf = (metamodel: string, object: ModelObject): TypeRef => ({ metamodel, name: object.eClass })

/**
 * Non-containment references of `source` that may point to `target`: the target class fits the
 * reference type, the reference is not full, and the link does not exist yet.
 */
export function connectableReferences(
  metamodels: Metamodels,
  metamodel: string,
  source: ModelObject,
  target: ModelObject,
): ReferenceDescriptor[] {
  const descriptor = findClass(metamodels, typeOf(metamodel, source))
  if (!descriptor) return []
  return plainReferences(descriptor).filter((reference) => {
    const current = source.references[reference.name] ?? []
    return (
      conformsTo(metamodels, typeOf(metamodel, target), reference.target) &&
      hasRoom(reference, current.length) &&
      !current.some((r) => !r.model && r.id === target.id)
    )
  })
}

/** Whether `source` has any reference that could still be drawn to some object of the model. */
export function canStartConnection(
  metamodels: Metamodels,
  metamodel: string,
  roots: ModelObject[],
  source: ModelObject,
): boolean {
  return allObjects(roots).some((target) => connectableReferences(metamodels, metamodel, source, target).length > 0)
}

export interface ContainerSlot {
  parent: ModelObject
  reference: ReferenceDescriptor
}

/** Places of the model where a new object of the class could be contained. */
export function containerSlots(
  metamodels: Metamodels,
  metamodel: string,
  roots: ModelObject[],
  className: string,
): ContainerSlot[] {
  const type: TypeRef = { metamodel, name: className }
  const slots: ContainerSlot[] = []
  for (const parent of allObjects(roots)) {
    const descriptor = findClass(metamodels, typeOf(metamodel, parent))
    if (!descriptor) continue
    for (const reference of containmentReferences(descriptor)) {
      if (
        conformsTo(metamodels, type, reference.target) &&
        hasRoom(reference, (parent.contents[reference.name] ?? []).length)
      ) {
        slots.push({ parent, reference })
      }
    }
  }
  return slots
}
