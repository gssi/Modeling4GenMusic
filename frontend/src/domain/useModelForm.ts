import type { AttributeValue, ModelObject } from '../api/types'
import { useStudio } from '../app/store'
import { findClass, instantiate } from '../editor/metamodel'
import { addChild, addRoot, removeObjects, setAttribute, setReference, uniqueId } from '../editor/modelOps'

/** Editing helpers for a form view: every change goes through the same store actions the diagram uses. */
export function useModelForm(modelId: string) {
  const model = useStudio((s) => s.open[modelId])
  const metamodels = useStudio((s) => s.metamodels)
  const mutate = useStudio((s) => s.mutate)
  const nsURI = model.doc.metamodel

  const descriptor = (className: string) => findClass(metamodels, { metamodel: nsURI, name: className })
  const literals = (className: string, attribute: string): string[] =>
    descriptor(className)?.attributes.find((a) => a.name === attribute)?.literals ?? []

  return {
    roots: model.doc.roots,
    root: model.doc.roots[0] as ModelObject | undefined,
    literals,
    /** Concrete classes that can be used where `baseClass` is expected. */
    subclasses: (baseClass: string): string[] =>
      (metamodels[nsURI]?.classes ?? [])
        .filter((c) => !c.abstract && c.supertypes.some((s) => s.metamodel === nsURI && s.name === baseClass))
        .map((c) => c.name),
    set: (id: string, attribute: string, value: AttributeValue | undefined) =>
      mutate(modelId, (roots) => setAttribute(roots, id, attribute, value), `${id}.${attribute}`),
    setRef: (id: string, feature: string, target: { model?: string; id: string } | null) =>
      mutate(modelId, (roots) => setReference(roots, id, feature, target ? [target] : [])),
    /** Adds an object of the class, with the given attributes and children, to a container or as a root. */
    add: (
      className: string,
      parent: { id: string; feature: string } | null,
      attributes: Record<string, AttributeValue> = {},
      children: { feature: string; className: string; attributes?: Record<string, AttributeValue> }[] = [],
    ) =>
      mutate(modelId, (roots) => {
        const classDescriptor = descriptor(className)
        if (!classDescriptor) return roots
        let object = instantiate(classDescriptor, uniqueId(roots, className))
        object = { ...object, attributes: { ...object.attributes, ...attributes } }
        let next = parent ? addChild(roots, parent.id, parent.feature, object) : addRoot(roots, object)
        for (const child of children) {
          const childDescriptor = descriptor(child.className)
          if (!childDescriptor) continue
          const childObject = instantiate(childDescriptor, uniqueId(next, child.className))
          next = addChild(next, object.id, child.feature, { ...childObject, attributes: { ...childObject.attributes, ...child.attributes } })
        }
        return next
      }),
    /** An id no element of the model has yet, for an object built by the form itself. */
    newId: (className: string) => uniqueId(model.doc.roots, className),
    /** Adds a ready-made object, with its children and references, to a container. */
    insert: (parent: { id: string; feature: string }, object: ModelObject) =>
      mutate(modelId, (roots) => addChild(roots, parent.id, parent.feature, object)),
    remove: (id: string) => mutate(modelId, (roots) => removeObjects(roots, [id])),
  }
}
