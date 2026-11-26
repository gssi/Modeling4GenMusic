import type {
  AttributeValue,
  ClassDescriptor,
  MetamodelDescriptor,
  ModelObject,
  ReferenceDescriptor,
  TypeRef,
} from '../api/types'

/** All loaded metamodels, by nsURI. Classes of one metamodel may extend or reference another one. */
export type Metamodels = Record<string, MetamodelDescriptor>

const ANY_OBJECT: TypeRef = { metamodel: 'http://www.eclipse.org/emf/2002/Ecore', name: 'EObject' }

export function findClass(metamodels: Metamodels, type: TypeRef): ClassDescriptor | undefined {
  return metamodels[type.metamodel]?.classes.find((c) => c.name === type.name)
}

/** Whether an instance of `actual` can be used where `expected` is required. */
export function conformsTo(metamodels: Metamodels, actual: TypeRef, expected: TypeRef): boolean {
  if (expected.metamodel === ANY_OBJECT.metamodel && expected.name === ANY_OBJECT.name) return true
  if (actual.metamodel === expected.metamodel && actual.name === expected.name) return true
  const descriptor = findClass(metamodels, actual)
  return !!descriptor?.supertypes.some((s) => s.metamodel === expected.metamodel && s.name === expected.name)
}

export function concreteClasses(metamodel: MetamodelDescriptor): ClassDescriptor[] {
  return metamodel.classes.filter((c) => !c.abstract)
}

export function containmentReferences(descriptor: ClassDescriptor): ReferenceDescriptor[] {
  return descriptor.references.filter((r) => r.containment)
}

export function plainReferences(descriptor: ClassDescriptor): ReferenceDescriptor[] {
  return descriptor.references.filter((r) => !r.containment)
}

/** A class can sit at the top of a model when no containment reference of its metamodel accepts it. */
export function canBeRoot(metamodels: Metamodels, metamodel: MetamodelDescriptor, className: string): boolean {
  const type: TypeRef = { metamodel: metamodel.nsURI, name: className }
  return !metamodel.classes.some((c) =>
    containmentReferences(c).some((r) => conformsTo(metamodels, type, r.target)),
  )
}

export function hasRoom(reference: ReferenceDescriptor, currentCount: number): boolean {
  return reference.upper < 0 || currentCount < reference.upper
}

/** A new object with the default value of every single-valued attribute that has one. */
export function instantiate(descriptor: ClassDescriptor, id: string): ModelObject {
  const attributes: Record<string, AttributeValue> = {}
  for (const attribute of descriptor.attributes) {
    if (attribute.upper !== 1) continue
    if (attribute.defaultValue !== undefined && attribute.defaultValue !== null) {
      attributes[attribute.name] = attribute.defaultValue
    } else if (attribute.type === 'enum' && attribute.literals?.length) {
      attributes[attribute.name] = attribute.literals[0]
    }
  }
  return { id, eClass: descriptor.name, attributes, contents: {}, references: {} }
}
