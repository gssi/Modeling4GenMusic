import type { AttributeValue, ModelObject } from '../api/types'
import { walk } from './modelOps'

/** Diagram elements derived from a model. This is a pure projection: the model stays the only state. */

export interface GraphNode {
  id: string
  kind: 'object' | 'proxy'
  /** Header text, in UML object notation: `id : Class`. */
  title: string
  /** Attribute compartment rows, `name = value`. */
  rows: string[]
  /** For a proxy: the external model it stands for. */
  externalModel?: string
  externalId?: string
  unresolved?: boolean
}

export interface GraphEdge {
  id: string
  kind: 'containment' | 'reference' | 'external'
  source: string
  target: string
  label: string
}

export interface Graph {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export const proxyNodeId = (model: string, id: string) => `proxy:${model}#${id}`

export function formatValue(value: AttributeValue): string {
  return typeof value === 'string' ? `"${value}"` : String(value)
}

export function formatEnumOrValue(value: AttributeValue, isEnum: boolean): string {
  return isEnum ? String(value) : formatValue(value)
}

/** How the attributes of a class are shown: their order, and which ones are enum literals (no quotes). */
export interface ClassDisplay {
  order: string[]
  enums: Set<string>
}

export function projectGraph(roots: ModelObject[], display: Record<string, ClassDisplay> = {}): Graph {
  const nodes: GraphNode[] = []
  const edges: GraphEdge[] = []
  const proxies = new Map<string, GraphNode>()

  walk(roots, ({ object, parent, feature }) => {
    const shown = display[object.eClass]
    const rank = (name: string) => {
      const index = shown?.order.indexOf(name) ?? -1
      return index < 0 ? Number.MAX_SAFE_INTEGER : index
    }
    nodes.push({
      id: object.id,
      kind: 'object',
      title: `${object.id} : ${object.eClass}`,
      rows: Object.entries(object.attributes)
        .sort(([a], [b]) => rank(a) - rank(b))
        .map(([name, value]) => `${name} = ${formatEnumOrValue(value, !!shown?.enums.has(name))}`),
    })
    if (parent && feature) {
      edges.push({
        id: `c:${parent.id}>${object.id}`,
        kind: 'containment',
        source: parent.id,
        target: object.id,
        label: feature,
      })
    }
    for (const [name, refs] of Object.entries(object.references)) {
      for (const ref of refs) {
        if (!ref.model) {
          edges.push({
            id: `r:${object.id}.${name}>${ref.id}`,
            kind: 'reference',
            source: object.id,
            target: ref.id,
            label: name,
          })
          continue
        }
        const proxyId = proxyNodeId(ref.model, ref.id)
        if (!proxies.has(proxyId)) {
          proxies.set(proxyId, {
            id: proxyId,
            kind: 'proxy',
            title: ref.id,
            rows: [],
            externalModel: ref.model,
            externalId: ref.id,
            unresolved: ref.unresolved,
          })
        }
        edges.push({
          id: `x:${object.id}.${name}>${proxyId}`,
          kind: 'external',
          source: object.id,
          target: proxyId,
          label: name,
        })
      }
    }
  })
  return { nodes: [...nodes, ...proxies.values()], edges }
}
