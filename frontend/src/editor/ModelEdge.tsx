import { BaseEdge, type Edge, EdgeLabelRenderer, type EdgeProps, getSmoothStepPath } from '@xyflow/react'
import type { Ref } from '../api/types'
import type { GraphEdge } from './graph'

export interface ModelEdgeData extends Record<string, unknown> {
  kind: GraphEdge['kind']
  label: string
  /** Owner object, feature and target of a (non-containment) reference, to be able to remove it. */
  ownerId: string
  ref?: Ref
  emphasis: 'normal' | 'hot' | 'dim'
  /** Lane among the references that join the same two nodes. */
  lane: number
  lanes: number
  onHover: (id: string | null) => void
}

const LANE_GAP = 20

export type ModelFlowEdge = Edge<ModelEdgeData, 'model'>

/**
 * Orthogonal connector with rounded corners. The role name sits on the last stretch before the
 * target, where connectors that leave the same object have already fanned out.
 */
export function ModelEdge(props: EdgeProps<ModelFlowEdge>) {
  const { id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data, selected } = props
  // Parallel references are spread around the attachment point, one label height apart.
  const lanes = data?.lanes ?? 1
  const lane = data?.lane ?? 0
  const shift = (lane - (lanes - 1) / 2) * LANE_GAP
  const [path] = getSmoothStepPath({
    sourceX,
    sourceY: sourceY + shift,
    targetX,
    targetY: targetY + shift,
    sourcePosition,
    targetPosition,
    borderRadius: 10,
    // References turn closer to their source than containments, so the two kinds keep separate lanes.
    stepPosition: data?.kind === 'containment' ? 0.5 : 0.3 + lane * 0.1,
  })
  if (!data) return null
  const suffix = data.emphasis === 'hot' || selected ? '-hot' : ''
  const classes = `model-edge is-${data.kind} is-${data.emphasis}${selected ? ' is-selected' : ''}`
  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        className={classes}
        markerStart={data.kind === 'containment' ? `url(#marker-diamond${suffix})` : undefined}
        markerEnd={data.kind === 'containment' ? undefined : `url(#marker-arrow${suffix})`}
        interactionWidth={18}
      />
      <EdgeLabelRenderer>
        <div
          className={`model-edge-label is-${data.kind} is-${data.emphasis}${selected ? ' is-selected' : ''}`}
          style={{
            // A lone connector carries its label above the line; parallel ones carry it on the line.
            transform: `translate(-100%, -50%) translate(${targetX - (lanes > 1 ? 20 : 14)}px, ${targetY + shift - (lanes > 1 ? 0 : 13)}px)`,
          }}
          onMouseEnter={() => data.onHover(id)}
          onMouseLeave={() => data.onHover(null)}
        >
          {data.label}
        </div>
      </EdgeLabelRenderer>
    </>
  )
}

const EDGE_COLOR = '#4d566a'
const HOT_COLOR = '#3b2fd6'

/** Marker shapes follow EMF notation: filled diamond on the container, open arrow on a reference target. */
export function EdgeMarkers() {
  const variants = [
    { suffix: '', color: EDGE_COLOR },
    { suffix: '-hot', color: HOT_COLOR },
  ]
  return (
    <svg className="edge-markers" aria-hidden="true">
      <defs>
        {variants.map(({ suffix, color }) => (
          <marker
            key={`diamond${suffix}`}
            id={`marker-diamond${suffix}`}
            viewBox="0 0 18 10"
            markerWidth="18"
            markerHeight="10"
            refX="1"
            refY="5"
            orient="auto"
            markerUnits="userSpaceOnUse"
          >
            <path d="M1 5 L9 1 L17 5 L9 9 Z" fill={color} stroke={color} />
          </marker>
        ))}
        {variants.map(({ suffix, color }) => (
          <marker
            key={`arrow${suffix}`}
            id={`marker-arrow${suffix}`}
            viewBox="0 0 12 12"
            markerWidth="12"
            markerHeight="12"
            refX="10.5"
            refY="6"
            orient="auto-start-reverse"
            markerUnits="userSpaceOnUse"
          >
            <path d="M2 1.5 L10.5 6 L2 10.5" fill="none" stroke={color} strokeWidth="1.75" />
          </marker>
        ))}
      </defs>
    </svg>
  )
}
