import type { AttributeValue, ModelObject, Ref } from '../api/types'

/**
 * Pure operations on the object tree of a model. Every function returns new roots and leaves
 * its input untouched, so that a list of roots can be kept as an undo snapshot.
 */

export interface Located {
  object: ModelObject
  parent: ModelObject | null
  feature: string | null
}

export function walk(roots: ModelObject[], visit: (located: Located) => void): void {
  const visitObject = (object: ModelObject, parent: ModelObject | null, feature: string | null) => {
    visit({ object, parent, feature })
    for (const [name, children] of Object.entries(object.contents)) {
      for (const child of children) visitObject(child, object, name)
    }
  }
  for (const root of roots) visitObject(root, null, null)
}

export function allObjects(roots: ModelObject[]): ModelObject[] {
  const result: ModelObject[] = []
  walk(roots, ({ object }) => result.push(object))
  return result
}

export function locate(roots: ModelObject[], id: string): Located | undefined {
  let found: Located | undefined
  walk(roots, (located) => {
    if (located.object.id === id) found = located
  })
  return found
}

export function descendants(object: ModelObject): ModelObject[] {
  return allObjects([object]).slice(1)
}

/** Rebuilds the tree applying `change` to every object; returning null drops the object and its subtree. */
function mapTree(roots: ModelObject[], change: (object: ModelObject) => ModelObject | null): ModelObject[] {
  const mapObject = (object: ModelObject): ModelObject | null => {
    const changed = change(object)
    if (!changed) return null
    const contents: Record<string, ModelObject[]> = {}
    for (const [name, children] of Object.entries(changed.contents)) {
      const kept = children.map(mapObject).filter((c): c is ModelObject => c !== null)
      if (kept.length) contents[name] = kept
    }
    return { ...changed, contents }
  }
  return roots.map(mapObject).filter((r): r is ModelObject => r !== null)
}

function update(roots: ModelObject[], id: string, change: (object: ModelObject) => ModelObject): ModelObject[] {
  return mapTree(roots, (object) => (object.id === id ? change(object) : object))
}

export function uniqueId(roots: ModelObject[], className: string): string {
  const taken = new Set(allObjects(roots).map((o) => o.id))
  const base = className.charAt(0).toLowerCase() + className.slice(1)
  for (let i = 1; ; i++) {
    const candidate = `${base}${i}`
    if (!taken.has(candidate)) return candidate
  }
}

export function addRoot(roots: ModelObject[], object: ModelObject): ModelObject[] {
  return [...roots, object]
}

export function addChild(roots: ModelObject[], parentId: string, feature: string, child: ModelObject): ModelObject[] {
  return update(roots, parentId, (parent) => ({
    ...parent,
    contents: { ...parent.contents, [feature]: [...(parent.contents[feature] ?? []), child] },
  }))
}

/** Removes the objects and their subtrees, together with every reference that pointed to them. */
export function removeObjects(roots: ModelObject[], ids: string[]): ModelObject[] {
  const doomed = new Set<string>()
  for (const id of ids) {
    const located = locate(roots, id)
    if (located) for (const object of allObjects([located.object])) doomed.add(object.id)
  }
  return mapTree(roots, (object) => {
    if (doomed.has(object.id)) return null
    const references: Record<string, Ref[]> = {}
    for (const [name, refs] of Object.entries(object.references)) {
      const kept = refs.filter((r) => r.model || !doomed.has(r.id))
      if (kept.length) references[name] = kept
    }
    return { ...object, references }
  })
}

export function setAttribute(
  roots: ModelObject[],
  id: string,
  name: string,
  value: AttributeValue | undefined,
): ModelObject[] {
  return update(roots, id, (object) => {
    const attributes = { ...object.attributes }
    if (value === undefined) delete attributes[name]
    else attributes[name] = value
    return { ...object, attributes }
  })
}

const sameRef = (a: Ref, b: Ref) => a.id === b.id && (a.model ?? null) === (b.model ?? null)

export function addReference(roots: ModelObject[], id: string, feature: string, ref: Ref): ModelObject[] {
  return update(roots, id, (object) => {
    const current = object.references[feature] ?? []
    if (current.some((r) => sameRef(r, ref))) return object
    return { ...object, references: { ...object.references, [feature]: [...current, ref] } }
  })
}

/** Replaces the targets of a reference; an empty list clears it. */
export function setReference(roots: ModelObject[], id: string, feature: string, refs: Ref[]): ModelObject[] {
  return update(roots, id, (object) => {
    const references = { ...object.references }
    if (refs.length) references[feature] = refs
    else delete references[feature]
    return { ...object, references }
  })
}

export function removeReference(roots: ModelObject[], id: string, feature: string, ref: Ref): ModelObject[] {
  const located = locate(roots, id)
  if (!located) return roots
  return setReference(
    roots,
    id,
    feature,
    (located.object.references[feature] ?? []).filter((r) => !sameRef(r, ref)),
  )
}

/** Changes the id of an object and of every reference to it. Returns null when the id is taken or empty. */
export function renameId(roots: ModelObject[], oldId: string, newId: string): ModelObject[] | null {
  const id = newId.trim()
  if (!id || /\s/.test(id)) return null
  if (id === oldId) return roots
  if (locate(roots, id)) return null
  return mapTree(roots, (object) => {
    const references: Record<string, Ref[]> = {}
    for (const [name, refs] of Object.entries(object.references)) {
      references[name] = refs.map((r) => (!r.model && r.id === oldId ? { ...r, id } : r))
    }
    return { ...object, id: object.id === oldId ? id : object.id, references }
  })
}

/**
 * Deep copy of a subtree with fresh ids. References inside the copy follow the copies;
 * references to objects outside the subtree are kept as they are.
 */
export function cloneWithNewIds(roots: ModelObject[], source: ModelObject): ModelObject {
  const taken = new Set(allObjects(roots).map((o) => o.id))
  const renamed = new Map<string, string>()
  for (const object of allObjects([source])) {
    const base = object.eClass.charAt(0).toLowerCase() + object.eClass.slice(1)
    let i = 1
    while (taken.has(`${base}${i}`)) i++
    taken.add(`${base}${i}`)
    renamed.set(object.id, `${base}${i}`)
  }
  const copy = (object: ModelObject): ModelObject => ({
    id: renamed.get(object.id)!,
    eClass: object.eClass,
    attributes: { ...object.attributes },
    contents: Object.fromEntries(Object.entries(object.contents).map(([k, v]) => [k, v.map(copy)])),
    references: Object.fromEntries(
      Object.entries(object.references).map(([k, v]) => [
        k,
        v.map((r) => (!r.model && renamed.has(r.id) ? { ...r, id: renamed.get(r.id)! } : { ...r })),
      ]),
    ),
  })
  return copy(source)
}
