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
  /** Attribute name of each row. */
  rowNames: string[]
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
    const entries = Object.entries(object.attributes).sort(([a], [b]) => rank(a) - rank(b))
    nodes.push({
      id: object.id,
      kind: 'object',
      title: `${object.id} : ${object.eClass}`,
      rows: entries.map(([name, value]) => `${name} = ${formatEnumOrValue(value, !!shown?.enums.has(name))}`),
      rowNames: entries.map(([name]) => name),
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
            rowNames: [],
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

export interface Lane {
  /** Position of the edge among the ones that join the same two nodes, from 0. */
  index: number
  count: number
}

/**
 * References that join the same two nodes would be drawn on top of each other: each one gets a
 * lane, so that the connectors and their labels can be spread apart. Containments are left alone,
 * an object being contained only once.
 */
export function parallelLanes(edges: GraphEdge[]): Record<string, Lane> {
  const groups = new Map<string, GraphEdge[]>()
  for (const edge of edges) {
    if (edge.kind === 'containment') continue
    const key = `${edge.source}\u0000${edge.target}`
    groups.set(key, [...(groups.get(key) ?? []), edge])
  }
  const lanes: Record<string, Lane> = {}
  for (const group of groups.values()) {
    group.forEach((edge, index) => {
      lanes[edge.id] = { index, count: group.length }
    })
  }
  return lanes
}
