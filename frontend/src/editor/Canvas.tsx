import {
  Background,
  BackgroundVariant,
  type Connection,
  type FinalConnectionState,
  type NodeChangeset,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
} from '@xyflow/react'
import { toPng, toSvg } from 'html-to-image'
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import type { ReferenceDescriptor } from '../api/types'
import { Dialog } from '../app/Dialog'
import { classDisplay, useStudio } from '../app/store'
import { metamodelColor } from '../app/theme'
import { canStartConnection, connectableReferences } from './connections'
import { parallelLanes, projectGraph } from './graph'
import { nodeSize } from './layout'
import { EdgeMarkers, ModelEdge, type ModelFlowEdge } from './ModelEdge'
import { CanvasContext } from './canvasContext'
import { ModelNode, type ModelFlowNode } from './ModelNode'
import { addReference, descendants, locate, removeReference } from './modelOps'
import { PALETTE_MIME } from './Palette'

const nodeTypes = { model: ModelNode }
const edgeTypes = { model: ModelEdge }
// Fitting never shrinks below a readable size: a larger diagram is panned instead.
const FIT = { padding: '32px', minZoom: 0.7, maxZoom: 1, duration: 0 } as const

interface PendingLink {
  sourceId: string
  targetId: string
  references: ReferenceDescriptor[]
}

/** Optional colouring of elements, supplied by whoever knows what the colours mean. */
export interface Decoration {
  /** CSS suffix for the whole node (`is-tint-…`) and for single attribute rows. */
  tints: (elementId: string) => { node?: string; attributes: Record<string, string> }
  legend: { tint: string; label: string }[]
}

interface CanvasProps {
  modelId: string
  decoration?: Decoration
  /** Application commands shown after the save and validate buttons. */
  toolbarExtra?: ReactNode
}

function CanvasInner({ modelId, decoration, toolbarExtra }: CanvasProps) {
  const model = useStudio((s) => s.open[modelId])
  const metamodels = useStudio((s) => s.metamodels)
  const summaries = useStudio((s) => s.summaries)
  const { select, mutate, setPositions, requestAdd, removeSelected, undo, redo, copy, paste, save, relayout, validate, openModel } =
    useStudio.getState()
  const flow = useReactFlow<ModelFlowNode, ModelFlowEdge>()
  const wrapper = useRef<HTMLDivElement>(null)
  const autoFit = useRef(true)

  const [nodes, setNodes] = useState<ModelFlowNode[]>([])
  const [hoveredEdge, setHoveredEdge] = useState<string | null>(null)
  const [selectedEdge, setSelectedEdge] = useState<string | null>(null)
  const [pendingLink, setPendingLink] = useState<PendingLink | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)

  const { doc, positions, selection, diagnostics, dirty } = model
  const nsURI = doc.metamodel
  const color = metamodelColor(summaries, nsURI)
  const graph = useMemo(() => projectGraph(doc.roots, classDisplay(metamodels, nsURI)), [doc.roots, metamodels, nsURI])

  // The store is the source of truth; the local node list only adds what React Flow tracks while dragging.
  useEffect(() => {
    const counts = new Map<string, { errors: number; warnings: number }>()
    // Problems describe the saved model: while there are unsaved edits the badges would be stale.
    for (const d of dirty ? [] : diagnostics) {
      if (!d.elementId) continue
      const entry = counts.get(d.elementId) ?? { errors: 0, warnings: 0 }
      if (d.severity === 'ERROR') entry.errors++
      else if (d.severity === 'WARNING') entry.warnings++
      counts.set(d.elementId, entry)
    }
    setNodes(
      graph.nodes.map((node) => {
        const located = node.kind === 'object' ? locate(doc.roots, node.id) : undefined
        const tints = decoration && node.kind === 'object' ? decoration.tints(node.id) : undefined
        return {
          id: node.id,
          type: 'model' as const,
          position: positions[node.id] ?? { x: 0, y: 0 },
          ...nodeSize(node),
          selected: selection.includes(node.id),
          ariaLabel: node.kind === 'proxy' ? `${node.title}, nel modello ${node.externalModel}` : [node.title, ...node.rows].join(', '),
          data: {
            graphNode: node,
            color,
            errors: counts.get(node.id)?.errors ?? 0,
            warnings: counts.get(node.id)?.warnings ?? 0,
            canLink: !!located && canStartConnection(metamodels, nsURI, doc.roots, located.object),
            tint: tints?.node,
            rowTints: tints ? node.rowNames.map((name) => tints.attributes[name]) : undefined,
          },
        }
      }),
    )
  }, [graph, positions, selection, diagnostics, dirty, doc.roots, metamodels, nsURI, color, decoration])

  const edges = useMemo<ModelFlowEdge[]>(() => {
    const lanes = parallelLanes(graph.edges)
    return graph.edges.map((edge) => {
                const target = graph.nodes.find((n) => n.id === edge.target)
        return {
          id: edge.id,
          type: 'model' as const,
          source: edge.source,
          target: edge.target,
          sourceHandle: edge.kind === 'containment' ? 'out-contain' : 'out-ref',
          targetHandle: edge.kind === 'containment' ? 'in-contain' : 'in-ref',
          selectable: edge.kind !== 'containment',
          selected: selectedEdge === edge.id,
          focusable: false,
          zIndex: hoveredEdge === edge.id ? 2 : 0,
          data: {
            kind: edge.kind,
            label: edge.label,
            ownerId: edge.source,
            ref:
              edge.kind === 'reference'
                ? { id: edge.target }
                : edge.kind === 'external'
                  ? { model: target?.externalModel, id: target?.externalId ?? '' }
                  : undefined,
            lane: lanes[edge.id]?.index ?? 0,
            lanes: lanes[edge.id]?.count ?? 1,
            emphasis: hoveredEdge === null ? ('normal' as const) : hoveredEdge === edge.id ? ('hot' as const) : ('dim' as const),
            onHover: setHoveredEdge,
          },
        }
      })
  }, [graph, hoveredEdge, selectedEdge])

  const onNodesChange = useCallback(
    (changes: NodeChangeset<ModelFlowNode>) => {
      setNodes((current) => {
        const next = changes.applyTo(current)
        if (changes.getByType('select').length > 0) {
          const ids = next.filter((n) => n.selected).map((n) => n.id)
          queueMicrotask(() => select(modelId, ids))
        }
        return next
      })
    },
    [modelId, select],
  )

  const canConnect = useCallback(
    (sourceId: string, targetId: string) => {
      const source = locate(doc.roots, sourceId)
      const target = locate(doc.roots, targetId)
      return !!source && !!target && connectableReferences(metamodels, nsURI, source.object, target.object).length > 0
    },
    [doc.roots, metamodels, nsURI],
  )

  const link = useCallback(
    (sourceId: string, targetId: string) => {
      const source = locate(doc.roots, sourceId)
      const target = locate(doc.roots, targetId)
      if (!source || !target) return
      const references = connectableReferences(metamodels, nsURI, source.object, target.object)
      if (references.length === 1) {
        mutate(modelId, (roots) => addReference(roots, source.object.id, references[0].name, { id: target.object.id }))
      } else if (references.length > 1) {
        setPendingLink({ sourceId: source.object.id, targetId: target.object.id, references })
      }
    },
    [doc.roots, metamodels, nsURI, modelId, mutate],
  )

  const onConnect = useCallback((connection: Connection) => link(connection.source, connection.target), [link])

  // A link can be dropped anywhere on an object, not only on its attachment point.
  const onConnectEnd = useCallback(
    (event: MouseEvent | TouchEvent, state: FinalConnectionState) => {
      if (state.isValid || !state.fromNode) return
      const point = 'changedTouches' in event ? event.changedTouches[0] : event
      const dropped = document.elementFromPoint(point.clientX, point.clientY)?.closest<HTMLElement>('.react-flow__node')
      const targetId = dropped?.dataset.id
      if (targetId && targetId !== state.fromNode.id && wrapper.current?.contains(dropped)) link(state.fromNode.id, targetId)
    },
    [link],
  )

  const fit = useCallback(() => {
    autoFit.current = true
    void flow.fitView(FIT)
  }, [flow])

  // Keep the diagram fitted to its container until the person pans or zooms by hand.
  useEffect(() => {
    const element = wrapper.current
    if (!element) return
    const observer = new ResizeObserver(() => {
      if (autoFit.current) void flow.fitView(FIT)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [flow])

  const nodeCount = graph.nodes.length
  useEffect(() => {
    if (!autoFit.current) return
    const timer = setTimeout(() => void flow.fitView(FIT), 60)
    return () => clearTimeout(timer)
  }, [nodeCount, flow])

  // Bring an element selected from outside the diagram (for example from the problems list) into view.
  const focusId = selection.length === 1 ? selection[0] : null
  useEffect(() => {
    if (!focusId) return
    const node = flow.getNode(focusId)
    if (!node) return
    const { x, y, zoom } = flow.getViewport()
    const box = wrapper.current?.getBoundingClientRect()
    if (!box) return
    const left = node.position.x * zoom + x
    const top = node.position.y * zoom + y
    const visible = left >= 0 && top >= 0 && left + (node.width ?? 0) * zoom <= box.width && top + (node.height ?? 0) * zoom <= box.height
    if (!visible) void flow.fitView({ ...FIT, nodes: [{ id: focusId }], maxZoom: zoom, duration: 200 })
  }, [focusId, flow])

  const requestDelete = useCallback(() => {
    const state = useStudio.getState().open[modelId]
    if (!state) return
    if (selectedEdge) {
      const edge = edges.find((e) => e.id === selectedEdge)
      if (edge?.data?.ref) {
        const { ownerId, label, ref } = edge.data
        mutate(modelId, (roots) => removeReference(roots, ownerId, label, ref))
      }
      setSelectedEdge(null)
      return
    }
    const objects = state.selection.map((id) => locate(state.doc.roots, id)).filter((l) => !!l)
    if (!objects.length) return
    const children = objects.reduce((n, l) => n + descendants(l.object).length, 0)
    if (children > 0) {
      const names = objects.map((l) => l.object.id).join(', ')
      setConfirmDelete(
        `Eliminando ${names} elimini anche ${children === 1 ? 'l’elemento che contiene' : `i ${children} elementi che contiene`}.`,
      )
    } else {
      removeSelected(modelId)
    }
  }, [modelId, selectedEdge, edges, mutate, removeSelected])

  const onKeyDown = (event: React.KeyboardEvent) => {
    const target = event.target as HTMLElement
    if (target.closest('input, select, textarea, dialog')) return
    const mod = event.metaKey || event.ctrlKey
    if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault()
      requestDelete()
    } else if (mod && event.key.toLowerCase() === 'z') {
      event.preventDefault()
      if (event.shiftKey) redo(modelId)
      else undo(modelId)
    } else if (mod && event.key.toLowerCase() === 'y') {
      event.preventDefault()
      redo(modelId)
    } else if (mod && event.key.toLowerCase() === 'c') {
      copy(modelId)
    } else if (mod && event.key.toLowerCase() === 'v') {
      paste(modelId)
    }
  }

  const onDrop = (event: React.DragEvent) => {
    const className = event.dataTransfer.getData(PALETTE_MIME)
    if (!className) return
    event.preventDefault()
    const position = flow.screenToFlowPosition({ x: event.clientX, y: event.clientY })
    const under = document.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('.react-flow__node')
    requestAdd(modelId, className, position, under?.dataset.id)
  }

  const download = (href: string, fileName: string) => {
    const link = document.createElement('a')
    link.href = href
    link.download = fileName
    link.click()
  }

  const exportImage = async (format: 'png' | 'svg') => {
    const element = wrapper.current
    if (!element) return
    const options = {
      backgroundColor: '#ffffff',
      pixelRatio: 2,
      filter: (node: HTMLElement) => !node.classList?.contains('react-flow__attribution') && !node.classList?.contains('canvas-empty'),
    }
    try {
      download(await (format === 'png' ? toPng(element, options) : toSvg(element, options)), `${modelId}.${format}`)
    } catch {
      useStudio.getState().notify({ kind: 'error', text: 'Non sono riuscito a creare l’immagine del diagramma.' })
    }
  }

  const exportJson = () => {
    const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' })
    const href = URL.createObjectURL(blob)
    download(href, `${modelId}.json`)
    setTimeout(() => URL.revokeObjectURL(href), 1000)
  }

  const actions = useMemo(
    () => ({ canConnect, openExternal: (external: string, id: string) => void openModel(external, id) }),
    [canConnect, openModel],
  )

  return (
    <div className="canvas" onKeyDown={onKeyDown}>
      <div className="canvas-toolbar" role="toolbar" aria-label="Azioni sul diagramma">
        <button type="button" className="primary" onClick={() => void save(modelId)} disabled={!model.dirty}>
          Salva
        </button>
        <button type="button" onClick={() => void validate(modelId)} disabled={model.dirty} title={model.dirty ? 'Salva per poter validare' : undefined}>
          Valida
        </button>
        {toolbarExtra}
        <span className="toolbar-gap" />
        <button type="button" onClick={() => undo(modelId)} disabled={!model.past.length}>
          Annulla
        </button>
        <button type="button" onClick={() => redo(modelId)} disabled={!model.future.length}>
          Ripeti
        </button>
        <button type="button" onClick={requestDelete} disabled={!selection.length && !selectedEdge}>
          Elimina
        </button>
        <span className="toolbar-gap" />
        <button
          type="button"
          onClick={() => {
            void relayout(modelId).then(fit)
          }}
        >
          Riordina
        </button>
        <details className="menu">
          <summary>Esporta</summary>
          <div className="menu-items">
            <a href={api.xmiUrl(modelId)} download={`${modelId}.xmi`} title={model.dirty ? 'Scarica l’ultima versione salvata' : undefined}>
              Modello XMI
            </a>
            <button type="button" onClick={exportJson}>
              Modello JSON
            </button>
            <button type="button" onClick={() => void exportImage('png')}>
              Immagine PNG
            </button>
            <button type="button" onClick={() => void exportImage('svg')}>
              Immagine SVG
            </button>
          </div>
        </details>
        <span className="toolbar-spacer" />
        <button type="button" aria-label="Riduci" onClick={() => { autoFit.current = false; void flow.zoomOut() }}>
          −
        </button>
        <button type="button" aria-label="Ingrandisci" onClick={() => { autoFit.current = false; void flow.zoomIn() }}>
          +
        </button>
        <button type="button" onClick={fit}>
          Adatta
        </button>
      </div>
      <div
        className="canvas-surface"
        ref={wrapper}
        onDragOver={(e) => {
          if (e.dataTransfer.types.includes(PALETTE_MIME)) {
            e.preventDefault()
            e.dataTransfer.dropEffect = 'copy'
          }
        }}
        onDrop={onDrop}
      >
        <EdgeMarkers />
        <CanvasContext.Provider value={actions}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            onNodesChange={onNodesChange}
            onNodeDragStop={(_, __, dragged) =>
              setPositions(modelId, Object.fromEntries(dragged.map((n) => [n.id, n.position])))
            }
            onConnect={onConnect}
            onConnectEnd={onConnectEnd}
            isValidConnection={(c) => canConnect(c.source, c.target)}
            onEdgeClick={(_, edge) => setSelectedEdge(edge.selectable === false ? null : edge.id)}
            onEdgeMouseEnter={(_, edge) => setHoveredEdge(edge.id)}
            onEdgeMouseLeave={() => setHoveredEdge(null)}
            onPaneClick={() => setSelectedEdge(null)}
            onNodeClick={() => setSelectedEdge(null)}
            onMoveEnd={(event) => {
              if (event) autoFit.current = false
            }}
            connectionRadius={48}
            fitView
            fitViewOptions={FIT}
            minZoom={0.3}
            maxZoom={2}
            zoomOnScroll={false}
            zoomOnPinch={false}
            zoomOnDoubleClick={false}
            preventScrolling={false}
            deleteKeyCode={null}
            nodesFocusable
            edgesFocusable={false}
          >
            <Background variant={BackgroundVariant.Dots} gap={22} size={1.2} color="#d3d8e0" />
          </ReactFlow>
        </CanvasContext.Provider>
        {decoration && (
          <ul className="canvas-legend" aria-label="Legenda dei colori">
            {decoration.legend.map((entry) => (
              <li key={entry.tint}>
                <span className={`legend-swatch is-tint-${entry.tint}`} aria-hidden="true" /> {entry.label}
              </li>
            ))}
          </ul>
        )}
        {graph.nodes.length === 0 && (
          <p className="canvas-empty">Il modello è vuoto. Scegli un elemento dall’elenco a sinistra per iniziare.</p>
        )}
      </div>

      {pendingLink && (
        <Dialog title="Quale collegamento vuoi creare?" onClose={() => setPendingLink(null)}>
          <p>
            Da {pendingLink.sourceId} a {pendingLink.targetId} sono possibili più riferimenti.
          </p>
          <ul className="choice-list">
            {pendingLink.references.map((reference) => (
              <li key={reference.name}>
                <button
                  type="button"
                  onClick={() => {
                    mutate(modelId, (roots) =>
                      addReference(roots, pendingLink.sourceId, reference.name, { id: pendingLink.targetId }),
                    )
                    setPendingLink(null)
                  }}
                >
                  {reference.name}
                </button>
              </li>
            ))}
          </ul>
        </Dialog>
      )}

      {confirmDelete && (
        <Dialog
          title="Eliminare anche il contenuto?"
          onClose={() => setConfirmDelete(null)}
          actions={
            <>
              <button type="button" onClick={() => setConfirmDelete(null)}>
                Annulla
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => {
                  removeSelected(modelId)
                  setConfirmDelete(null)
                }}
              >
                Elimina
              </button>
            </>
          }
        >
          <p>{confirmDelete}</p>
        </Dialog>
      )}
    </div>
  )
}

export function Canvas(props: CanvasProps) {
  return (
    <ReactFlowProvider>
      <CanvasInner {...props} />
    </ReactFlowProvider>
  )
}
