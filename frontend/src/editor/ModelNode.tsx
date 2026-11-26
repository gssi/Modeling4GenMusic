import { Handle, type Node, type NodeProps, Position, useConnection } from '@xyflow/react'
import { type CSSProperties, useContext } from 'react'
import { CanvasContext } from './canvasContext'
import type { GraphNode } from './graph'

export interface ModelNodeData extends Record<string, unknown> {
  graphNode: GraphNode
  color: string
  errors: number
  warnings: number
  /** Whether a reference can still be drawn from this object. */
  canLink: boolean
}

export type ModelFlowNode = Node<ModelNodeData, 'model'>

/** Vertical position of the reference attachment points: the middle of the header. */
const REF_PORT = 18

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

/** An object in UML notation: underlined `id : Class` header and a compartment of `name = value` rows. */
export function ModelNode({ id, data, selected }: NodeProps<ModelFlowNode>) {
  const { graphNode, color, errors, warnings, canLink } = data
  const connection = useConnection()
  const actions = useContext(CanvasContext)
  const isProxy = graphNode.kind === 'proxy'

  let state = ''
  if (connection.inProgress && connection.fromNode.id !== id) {
    state = !isProxy && actions.canConnect(connection.fromNode.id, id) ? ' is-valid-target' : ' is-dimmed'
  }

  if (isProxy) {
    return (
      <div
        className={`model-node is-proxy${selected ? ' is-selected' : ''}${state}`}
        onDoubleClick={() => actions.openExternal(graphNode.externalModel!, graphNode.externalId!)}
        title="Doppio clic per aprire il modello"
      >
        <Handle id="in-ref" type="target" position={Position.Left} isConnectable={false} />
        <div className="model-node-title">{graphNode.title}</div>
        <div className="model-node-origin">
          {graphNode.unresolved ? 'non trovato in ' : 'in '}
          {graphNode.externalModel}
        </div>
      </div>
    )
  }

  return (
    <div
      className={`model-node${selected ? ' is-selected' : ''}${state}`}
      style={{ '--mm': color } as CSSProperties}
    >
      {/* Containments and references use separate attachment points, so their connectors never share a stretch. */}
      <Handle id="in-contain" type="target" position={Position.Left} isConnectable={false} className="is-hidden-handle" />
      <Handle id="in-ref" type="target" position={Position.Left} style={{ top: REF_PORT }} className="is-hidden-handle" />
      <div className="model-node-header">
        <span className="model-node-title">{graphNode.title}</span>
      </div>
      {graphNode.rows.length > 0 && (
        <ul className="model-node-rows">
          {graphNode.rows.map((row) => (
            <li key={row}>{row}</li>
          ))}
        </ul>
      )}
      {(errors > 0 || warnings > 0) && (
        <span
          className={`model-node-badge ${errors > 0 ? 'is-error' : 'is-warning'}`}
          aria-label={[
            errors > 0 ? plural(errors, 'errore', 'errori') : '',
            warnings > 0 ? plural(warnings, 'avviso', 'avvisi') : '',
          ]
            .filter(Boolean)
            .join(', ')}
        >
          {errors > 0 ? errors : warnings}
        </span>
      )}
      <Handle id="out-contain" type="source" position={Position.Right} isConnectable={false} className="is-hidden-handle" />
      <Handle
        id="out-ref"
        type="source"
        position={Position.Right}
        style={{ top: REF_PORT }}
        isConnectable={canLink}
        className={canLink ? 'is-link-handle' : 'is-hidden-handle'}
        title={canLink ? 'Trascina verso un altro oggetto per collegarlo' : undefined}
      />
    </div>
  )
}
