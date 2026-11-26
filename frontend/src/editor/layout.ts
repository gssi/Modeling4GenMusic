import ELK from 'elkjs/lib/elk.bundled.js'
import type { Position } from '../api/types'
import type { Graph, GraphNode } from './graph'

/** Node geometry shared by layout and rendering, so that what is laid out is what is drawn. */
export const NODE = {
  minWidth: 168,
  maxWidth: 340,
  charWidth: 7.6,
  paddingX: 28,
  headerHeight: 36,
  rowHeight: 22,
  compartmentPadding: 12,
  proxyHeight: 58,
  /** Horizontal room between a container and its contents: the role name is written there. */
  layerGap: 136,
  rowGap: 28,
} as const

export interface Size {
  width: number
  height: number
}

export function nodeSize(node: GraphNode): Size {
  const lines = node.kind === 'proxy' ? [node.title, node.externalModel ?? ''] : [node.title, ...node.rows]
  const longest = Math.max(...lines.map((l) => l.length))
  const width = Math.min(NODE.maxWidth, Math.max(NODE.minWidth, Math.ceil(longest * NODE.charWidth + NODE.paddingX)))
  if (node.kind === 'proxy') return { width, height: NODE.proxyHeight }
  const compartment = node.rows.length ? node.rows.length * NODE.rowHeight + NODE.compartmentPadding : 0
  return { width, height: NODE.headerHeight + compartment }
}

const elk = new ELK()

/**
 * Automatic layout: containers on the left, contents and reference targets to their right,
 * layer by layer. Returns the top-left position of every node.
 */
export async function autoLayout(graph: Graph): Promise<Record<string, Position>> {
  if (!graph.nodes.length) return {}
  const known = new Set(graph.nodes.map((n) => n.id))
  const result = await elk.layout({
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': 'RIGHT',
      'elk.edgeRouting': 'ORTHOGONAL',
      'elk.layered.spacing.nodeNodeBetweenLayers': String(NODE.layerGap),
      'elk.spacing.nodeNode': String(NODE.rowGap),
      'elk.spacing.componentComponent': '56',
      'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
      'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
    },
    children: graph.nodes.map((node) => ({ id: node.id, ...nodeSize(node) })),
    edges: graph.edges
      .filter((e) => known.has(e.source) && known.has(e.target))
      .map((e) => ({ id: e.id, sources: [e.source], targets: [e.target] })),
  })
  const positions: Record<string, Position> = {}
  for (const child of result.children ?? []) {
    positions[child.id] = { x: Math.round(child.x ?? 0), y: Math.round(child.y ?? 0) }
  }
  return positions
}

/** Position for a node added after the first layout: below the lowest node of its column, or of everything. */
export function placeNewNode(
  graph: Graph,
  positions: Record<string, Position>,
  nodeId: string,
  nearId?: string,
): Position {
  const boxes = graph.nodes
    .filter((n) => n.id !== nodeId && positions[n.id])
    .map((n) => ({ ...positions[n.id], ...nodeSize(n) }))
  if (!boxes.length) return { x: 40, y: 40 }
  const near = nearId ? positions[nearId] : undefined
  const nearNode = nearId ? graph.nodes.find((n) => n.id === nearId) : undefined
  const x = near && nearNode ? near.x + nodeSize(nearNode).width + NODE.layerGap : Math.min(...boxes.map((b) => b.x))
  const self = graph.nodes.find((n) => n.id === nodeId)
  const width = self ? nodeSize(self).width : NODE.minWidth
  const column = boxes.filter((b) => b.x < x + width && b.x + b.width > x)
  const y = column.length ? Math.max(...column.map((b) => b.y + b.height)) + NODE.rowGap : (near?.y ?? 40)
  return { x, y }
}

/**
 * Moves nodes down where a node that grew (for example after an attribute was set) now covers
 * the ones below it. Nodes keep their column and their vertical order.
 */
export function pushApart(graph: Graph, positions: Record<string, Position>): Record<string, Position> {
  const gap = 16
  const boxes = graph.nodes
    .filter((n) => positions[n.id])
    .map((n) => ({ id: n.id, ...positions[n.id], ...nodeSize(n) }))
    .sort((a, b) => a.y - b.y || a.x - b.x)
  let moved = false
  for (let i = 1; i < boxes.length; i++) {
    for (let j = 0; j < i; j++) {
      const above = boxes[j]
      const box = boxes[i]
      const sameColumn = box.x < above.x + above.width && above.x < box.x + box.width
      if (sameColumn && box.y < above.y + above.height + gap && above.y <= box.y) {
        box.y = above.y + above.height + gap
        moved = true
      }
    }
  }
  if (!moved) return positions
  const result = { ...positions }
  for (const box of boxes) result[box.id] = { x: box.x, y: box.y }
  return result
}
