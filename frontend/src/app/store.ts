import { create } from 'zustand'
import { ApiError, api } from '../api/client'
import type {
  DerivationResult,
  Diagnostic,
  GenerationResult,
  GeneratorInfo,
  MetamodelSummary,
  ModelDocument,
  ModelHeader,
  ModelObject,
  Position,
  RuleSet,
  TransformationScript,
} from '../api/types'
import { type Provenance, findProvenance } from '../domain/provenance'
import { type ContainerSlot, containerSlots } from '../editor/connections'
import { type ClassDisplay, projectGraph } from '../editor/graph'
import { autoLayout, placeNewNode, pushApart } from '../editor/layout'
import { type Metamodels, canBeRoot, findClass, instantiate } from '../editor/metamodel'
import { addChild, addRoot, cloneWithNewIds, locate, removeObjects, uniqueId } from '../editor/modelOps'

/** Id of the tab that hosts the derivation view; it is not a model. */
export const DERIVE_TAB = '@derive'
/** Id of the tab that shows the validation rules. */
export const RULES_TAB = '@rules'

const HISTORY_LIMIT = 100
const COALESCE_MS = 1500

export interface OpenModel {
  doc: ModelDocument
  dirty: boolean
  past: ModelObject[][]
  future: ModelObject[][]
  selection: string[]
  positions: Record<string, Position>
  diagnostics: Diagnostic[]
  /** False until the model has been validated at least once in its current saved state. */
  validated: boolean
  lastEdit?: { key: string; at: number }
}

export type ViewMode = 'diagram' | 'form'

export interface DerivationState {
  learnerId: string
  intentId: string
  qualityId: string
  running: boolean
  result: DerivationResult | null
}

export interface Notice {
  kind: 'info' | 'error'
  text: string
}

/** A new object waiting for the user to choose where it is contained. */
export interface PendingPlacement {
  modelId: string
  className: string
  slots: ContainerSlot[]
  position?: Position
}

interface StudioState {
  ready: boolean
  loadError: string | null
  summaries: MetamodelSummary[]
  metamodels: Metamodels
  models: ModelHeader[]
  open: Record<string, OpenModel>
  tabs: string[]
  activeId: string | null
  notice: Notice | null
  pendingPlacement: PendingPlacement | null
  clipboard: ModelObject | null
  /** Other models read to offer cross-model reference targets. */
  externals: Record<string, ModelDocument>
  viewMode: Record<string, ViewMode>
  /** Where the elements of a derived spec come from, by model id. */
  provenance: Record<string, Provenance>
  /** Model shown beside the active one, to look at the sources of a derived element. */
  split: string | null
  derivation: DerivationState
  generations: Record<string, GenerationResult>
  generating: string | null
  generators: GeneratorInfo[]
  /** Generator chosen in the exercise view; null until the list is read, then the default one. */
  generatorId: string | null
  /** Validation scripts as the server parses them; null until read. */
  rules: RuleSet[] | null
  /** The derivation script as the server parses it; null until read. */
  transformation: TransformationScript | null
  /** Name of the rule the rules tab scrolls to. */
  ruleFocus: string | null

  setViewMode: (modelId: string, mode: ViewMode) => void
  setRole: (modelId: string, role: string) => void
  openDerivation: () => void
  openRules: (ruleName?: string) => void
  loadRules: () => Promise<void>
  /** Keeps a new text of a validation script; resolves to false when the server refused it. */
  saveRules: (script: string, source: string) => Promise<boolean>
  /** Goes back to the script shipped with the application. */
  resetRules: (script: string) => Promise<void>
  saveTransformation: (source: string) => Promise<boolean>
  resetTransformation: () => Promise<void>
  setDerivationInput: (input: Partial<Pick<DerivationState, 'learnerId' | 'intentId' | 'qualityId'>>) => void
  derive: () => Promise<void>
  reloadModel: (id: string) => Promise<void>
  showSource: (modelId: string, elementId: string) => Promise<void>
  closeSplit: () => void
  generate: (modelId: string) => Promise<void>
  loadGenerators: () => Promise<void>
  chooseGenerator: (id: string) => void

  init: () => Promise<void>
  refreshModels: () => Promise<void>
  notify: (notice: Notice | null) => void
  createModel: (metamodel: string, name: string, role?: string, folder?: string) => Promise<void>
  importModel: (file: File, role?: string, folder?: string) => Promise<void>
  /** Moves a model to a folder of the workspace ('' for none); its content is not touched. */
  moveModel: (id: string, folder: string) => Promise<void>
  deleteModel: (id: string) => Promise<void>
  openModel: (id: string, selectId?: string) => Promise<void>
  closeTab: (id: string) => void
  activate: (id: string) => void
  select: (modelId: string, ids: string[]) => void
  mutate: (modelId: string, change: (roots: ModelObject[]) => ModelObject[], coalesceKey?: string) => void
  renamePosition: (modelId: string, oldId: string, newId: string) => void
  undo: (modelId: string) => void
  redo: (modelId: string) => void
  setPositions: (modelId: string, positions: Record<string, Position>) => void
  relayout: (modelId: string) => Promise<void>
  requestAdd: (modelId: string, className: string, position?: Position, hintId?: string) => void
  placePending: (slot: ContainerSlot | null) => void
  removeSelected: (modelId: string) => void
  copy: (modelId: string) => void
  paste: (modelId: string) => void
  save: (modelId: string) => Promise<void>
  /** Brings back a saved version as a new one; resolves to false when it could not be done. */
  restoreVersion: (modelId: string, version: number) => Promise<boolean>
  validate: (modelId: string) => Promise<void>
  applyFix: (modelId: string, diagnosticId: string, fixIndex: number) => Promise<void>
  loadExternals: (exceptId: string) => Promise<void>
}

/** Attribute order and enum attributes of each class, as the diagram shows them. */
export function classDisplay(metamodels: Metamodels, nsURI: string): Record<string, ClassDisplay> {
  const result: Record<string, ClassDisplay> = {}
  for (const c of metamodels[nsURI]?.classes ?? []) {
    result[c.name] = {
      order: c.attributes.map((a) => a.name),
      enums: new Set(c.attributes.filter((a) => a.type === 'enum').map((a) => a.name)),
    }
  }
  return result
}

const layoutTimers = new Map<string, ReturnType<typeof setTimeout>>()

function scheduleLayoutSave(modelId: string, read: () => Record<string, Position> | undefined) {
  clearTimeout(layoutTimers.get(modelId))
  layoutTimers.set(
    modelId,
    setTimeout(() => {
      const nodes = read()
      if (nodes) api.saveLayout(modelId, { nodes }).catch(() => undefined)
    }, 600),
  )
}

const message = (e: unknown) => (e instanceof Error ? e.message : 'Errore inatteso')

export const useStudio = create<StudioState>((set, get) => {
  const patch = (modelId: string, change: (model: OpenModel) => Partial<OpenModel>) =>
    set((state) => {
      const model = state.open[modelId]
      return model ? { open: { ...state.open, [modelId]: { ...model, ...change(model) } } } : {}
    })

  /** Gives a position to the nodes that have none and drops the positions of removed nodes. */
  const settlePositions = (modelId: string, nearId?: string, wanted?: Record<string, Position>) => {
    const state = get()
    const model = state.open[modelId]
    if (!model) return
    const graph = projectGraph(model.doc.roots)
    const positions: Record<string, Position> = {}
    for (const node of graph.nodes) {
      if (model.positions[node.id]) positions[node.id] = model.positions[node.id]
    }
    for (const node of graph.nodes) {
      if (positions[node.id]) continue
      const incoming = graph.edges.find((e) => e.target === node.id)
      positions[node.id] =
        wanted?.[node.id] ?? placeNewNode(graph, positions, node.id, incoming?.source ?? nearId)
    }
    patch(modelId, () => ({ positions: pushApart(graph, positions) }))
    scheduleLayoutSave(modelId, () => get().open[modelId]?.positions)
  }

  const applyRoots = (
    modelId: string,
    roots: ModelObject[],
    extra: Partial<OpenModel> = {},
    coalesceKey?: string,
  ) => {
    patch(modelId, (model) => {
      if (roots === model.doc.roots) return {}
      const now = Date.now()
      const coalesce =
        !!coalesceKey && model.lastEdit?.key === coalesceKey && now - model.lastEdit.at < COALESCE_MS
      return {
        doc: { ...model.doc, roots },
        dirty: true,
        validated: false,
        past: coalesce ? model.past : [...model.past, model.doc.roots].slice(-HISTORY_LIMIT),
        future: [],
        lastEdit: coalesceKey ? { key: coalesceKey, at: now } : undefined,
        ...extra,
      }
    })
  }

  const insert = (modelId: string, className: string, slot: ContainerSlot | null, position?: Position) => {
    const state = get()
    const model = state.open[modelId]
    const descriptor = model && findClass(state.metamodels, { metamodel: model.doc.metamodel, name: className })
    if (!model || !descriptor) return
    const object = instantiate(descriptor, uniqueId(model.doc.roots, className))
    const roots = slot
      ? addChild(model.doc.roots, slot.parent.id, slot.reference.name, object)
      : addRoot(model.doc.roots, object)
    applyRoots(modelId, roots, { selection: [object.id] })
    settlePositions(modelId, slot?.parent.id, position ? { [object.id]: position } : undefined)
  }

  /** Takes in a changed script and validates the open models again, since the rules they answer to changed. */
  const adoptRules = (updated: RuleSet, text: string) => {
    set((state) => ({
      rules: state.rules?.map((r) => (r.script === updated.script ? updated : r)) ?? null,
      notice: { kind: 'info', text },
    }))
    for (const id of Object.keys(get().open)) void get().validate(id)
  }

  /** Reads a model from the server into a tab, replacing an open copy, without changing the active tab. */
  const load = async (id: string, selectId?: string) => {
    try {
      const [doc, layout] = await Promise.all([api.getModel(id), api.getLayout(id)])
      const saved = layout.nodes ?? {}
      const graph = projectGraph(doc.roots)
      const positions = graph.nodes.some((n) => saved[n.id]) ? saved : await autoLayout(graph)
      set((state) => ({
        open: {
          ...state.open,
          [id]: {
            doc,
            dirty: false,
            past: [],
            future: [],
            selection: selectId ? [selectId] : [],
            positions,
            diagnostics: [],
            validated: false,
          },
        },
        tabs: state.tabs.includes(id) ? state.tabs : [...state.tabs, id],
      }))
      settlePositions(id)
      void get().validate(id)
      if (doc.role === 'derived') {
        const provenance = await findProvenance(id, get().models, api.getModel)
        if (provenance) set((state) => ({ provenance: { ...state.provenance, [id]: provenance } }))
      }
    } catch (e) {
      set({ notice: { kind: 'error', text: message(e) } })
    }
  }

  return {
    ready: false,
    loadError: null,
    summaries: [],
    metamodels: {},
    models: [],
    open: {},
    tabs: [],
    activeId: null,
    notice: null,
    pendingPlacement: null,
    clipboard: null,
    externals: {},
    viewMode: {},
    provenance: {},
    split: null,
    derivation: { learnerId: '', intentId: '', qualityId: '', running: false, result: null },
    generations: {},
    generating: null,
    rules: null,
    transformation: null,
    ruleFocus: null,
    generators: [],
    generatorId: null,

    setViewMode: (modelId, mode) => set((state) => ({ viewMode: { ...state.viewMode, [modelId]: mode } })),

    setRole: (modelId, role) =>
      patch(modelId, (model) => (model.doc.role === role ? {} : { doc: { ...model.doc, role }, dirty: true, validated: false })),

    openRules: (ruleName) =>
      set((state) => ({
        tabs: state.tabs.includes(RULES_TAB) ? state.tabs : [...state.tabs, RULES_TAB],
        activeId: RULES_TAB,
        split: null,
        ruleFocus: ruleName ?? null,
      })),

    loadRules: async () => {
      try {
        const [rules, transformation] = await Promise.all([api.listRules(), api.getTransformation()])
        set({ rules, transformation })
      } catch (e) {
        set({ notice: { kind: 'error', text: `Lettura delle regole non riuscita: ${message(e)}` } })
      }
    },

    saveRules: async (script, source) => {
      try {
        adoptRules(await api.saveRules(script, source), `Regole di ${script} salvate.`)
        return true
      } catch (e) {
        set({ notice: { kind: 'error', text: `Regole non salvate: ${message(e)}` } })
        return false
      }
    },

    saveTransformation: async (source) => {
      try {
        const transformation = await api.saveTransformation(source)
        set({ transformation, notice: { kind: 'info', text: `Regole di ${transformation.script} salvate.` } })
        return true
      } catch (e) {
        set({ notice: { kind: 'error', text: `Regole non salvate: ${message(e)}` } })
        return false
      }
    },

    resetTransformation: async () => {
      try {
        const transformation = await api.resetTransformation()
        set({ transformation, notice: { kind: 'info', text: `Regole di ${transformation.script} riportate a quelle originali.` } })
      } catch (e) {
        set({ notice: { kind: 'error', text: `Ripristino non riuscito: ${message(e)}` } })
      }
    },

    resetRules: async (script) => {
      try {
        adoptRules(await api.resetRules(script), `Regole di ${script} riportate a quelle originali.`)
      } catch (e) {
        set({ notice: { kind: 'error', text: `Ripristino non riuscito: ${message(e)}` } })
      }
    },

    openDerivation: () =>
      set((state) => ({
        tabs: state.tabs.includes(DERIVE_TAB) ? state.tabs : [...state.tabs, DERIVE_TAB],
        activeId: DERIVE_TAB,
        split: null,
      })),

    setDerivationInput: (input) => set((state) => ({ derivation: { ...state.derivation, ...input, result: null } })),

    derive: async () => {
      const { learnerId, intentId, qualityId } = get().derivation
      if (!learnerId || !intentId || !qualityId) return
      const unsaved = [learnerId, intentId, qualityId].find((id) => get().open[id]?.dirty)
      if (unsaved) {
        set({ notice: { kind: 'error', text: `Salva «${get().open[unsaved].doc.name}» prima di derivare: la derivazione legge i modelli salvati.` } })
        return
      }
      set((state) => ({ derivation: { ...state.derivation, running: true, result: null } }))
      try {
        const result = await api.derive(learnerId, intentId, qualityId)
        // The derived models were rewritten on disk: drop the copies that may be open.
        set((state) => {
          const open = { ...state.open }
          const provenance = { ...state.provenance }
          const generations = { ...state.generations }
          for (const id of [result.derivedSpecModelId, result.weavingModelId]) {
            delete open[id]
            delete provenance[id]
            delete generations[id]
          }
          return { open, provenance, generations, externals: {}, derivation: { ...state.derivation, running: false, result } }
        })
        await get().refreshModels()
        await get().openModel(result.derivedSpecModelId)
      } catch (e) {
        set((state) => ({
          derivation: { ...state.derivation, running: false },
          notice: { kind: 'error', text: `Derivazione non riuscita: ${message(e)}` },
        }))
      }
    },

    reloadModel: async (id) => {
      if (!get().open[id]) return
      await load(id, get().open[id].selection[0])
    },

    showSource: async (modelId, elementId) => {
      if (!get().open[modelId]) await load(modelId, elementId)
      if (!get().open[modelId]) return
      get().select(modelId, [elementId])
      set((state) => ({ split: modelId === state.activeId ? state.split : modelId }))
    },

    closeSplit: () => set({ split: null }),

    loadGenerators: async () => {
      try {
        const generators = await api.listGenerators()
        set((state) => ({
          generators,
          generatorId:
            state.generatorId && generators.some((g) => g.id === state.generatorId)
              ? state.generatorId
              : (generators.find((g) => g.isDefault)?.id ?? generators[0]?.id ?? null),
        }))
      } catch {
        // the view says that no generator could be read
      }
    },

    chooseGenerator: (id) => set({ generatorId: id }),

    generate: async (modelId) => {
      const model = get().open[modelId]
      if (!model) return
      if (model.dirty) {
        set({ notice: { kind: 'error', text: 'Salva la spec prima di generare l’esercizio.' } })
        return
      }
      set({ generating: modelId })
      try {
        const result = await api.generate(modelId, get().provenance[modelId]?.inputs.qualityModel, get().generatorId ?? undefined)
        set((state) => ({ generations: { ...state.generations, [modelId]: result }, generating: null }))
      } catch (e) {
        set({ generating: null, notice: { kind: 'error', text: `Generazione non riuscita: ${message(e)}` } })
        void get().loadGenerators()
      }
    },

    init: async () => {
      try {
        const summaries = await api.listMetamodels()
        const descriptors = await Promise.all(summaries.map((s) => api.getMetamodel(s.prefix)))
        const metamodels: Metamodels = {}
        for (const d of descriptors) metamodels[d.nsURI] = d
        const models = await api.listModels()
        set({ summaries, metamodels, models, ready: true, loadError: null })
        void get().loadRules()
      } catch (e) {
        set({ loadError: message(e) })
      }
    },

    refreshModels: async () => {
      try {
        set({ models: await api.listModels() })
      } catch (e) {
        set({ notice: { kind: 'error', text: message(e) } })
      }
    },

    notify: (notice) => set({ notice }),

    createModel: async (metamodel, name, role, folder) => {
      try {
        const doc = await api.createModel(metamodel, name, role, folder)
        await get().refreshModels()
        await get().openModel(doc.id)
      } catch (e) {
        set({ notice: { kind: 'error', text: message(e) } })
      }
    },

    importModel: async (file, role, folder) => {
      try {
        const doc = await api.importModel(file, role, folder)
        set((s) => ({ externals: {}, notice: { kind: 'info', text: `Importato «${doc.name}».` }, open: s.open }))
        await get().refreshModels()
        await get().openModel(doc.id)
      } catch (e) {
        set({ notice: { kind: 'error', text: `Importazione non riuscita: ${message(e)}` } })
      }
    },

    moveModel: async (id, folder) => {
      try {
        const moved = await api.moveModel(id, folder)
        set((state) => {
          const opened = state.open[id]
          return {
            models: state.models.map((m) => (m.id === id ? { ...m, folder: moved.folder } : m)),
            open: opened ? { ...state.open, [id]: { ...opened, doc: { ...opened.doc, folder: moved.folder } } } : state.open,
          }
        })
      } catch (e) {
        set({ notice: { kind: 'error', text: `Spostamento non riuscito: ${message(e)}` } })
      }
    },

    deleteModel: async (id) => {
      try {
        await api.deleteModel(id)
        get().closeTab(id)
        set({ externals: {} })
        await get().refreshModels()
      } catch (e) {
        set({ notice: { kind: 'error', text: message(e) } })
      }
    },

    openModel: async (id, selectId) => {
      if (get().open[id]) {
        if (selectId) get().select(id, [selectId])
      } else {
        await load(id, selectId)
      }
      if (get().open[id]) set((state) => ({ activeId: id, split: state.split === id ? null : state.split }))
    },

    closeTab: (id) =>
      set((state) => {
        const tabs = state.tabs.filter((t) => t !== id)
        const open = { ...state.open }
        delete open[id]
        const activeId = state.activeId === id ? (tabs[tabs.length - 1] ?? null) : state.activeId
        const split = state.split === id || state.split === activeId ? null : state.split
        return { tabs, open, activeId, split }
      }),

    activate: (id) => set((state) => ({ activeId: id, split: state.split === id ? null : state.split })),

    select: (modelId, ids) =>
      patch(modelId, (model) =>
        model.selection.length === ids.length && model.selection.every((id, i) => id === ids[i])
          ? {}
          : { selection: ids },
      ),

    mutate: (modelId, change, coalesceKey) => {
      const model = get().open[modelId]
      if (!model) return
      applyRoots(modelId, change(model.doc.roots), {}, coalesceKey)
      settlePositions(modelId)
    },

    renamePosition: (modelId, oldId, newId) =>
      patch(modelId, (model) => {
        const positions = { ...model.positions }
        if (positions[oldId]) {
          positions[newId] = positions[oldId]
          delete positions[oldId]
        }
        return { positions, selection: model.selection.map((id) => (id === oldId ? newId : id)) }
      }),

    undo: (modelId) => {
      patch(modelId, (model) => {
        const previous = model.past[model.past.length - 1]
        if (!previous) return {}
        return {
          doc: { ...model.doc, roots: previous },
          past: model.past.slice(0, -1),
          future: [model.doc.roots, ...model.future],
          dirty: true,
          validated: false,
          lastEdit: undefined,
        }
      })
      settlePositions(modelId)
    },

    redo: (modelId) => {
      patch(modelId, (model) => {
        const next = model.future[0]
        if (!next) return {}
        return {
          doc: { ...model.doc, roots: next },
          past: [...model.past, model.doc.roots],
          future: model.future.slice(1),
          dirty: true,
          validated: false,
          lastEdit: undefined,
        }
      })
      settlePositions(modelId)
    },

    setPositions: (modelId, positions) => {
      patch(modelId, (model) => ({ positions: { ...model.positions, ...positions } }))
      scheduleLayoutSave(modelId, () => get().open[modelId]?.positions)
    },

    relayout: async (modelId) => {
      const model = get().open[modelId]
      if (!model) return
      const positions = await autoLayout(projectGraph(model.doc.roots))
      patch(modelId, () => ({ positions }))
      scheduleLayoutSave(modelId, () => get().open[modelId]?.positions)
    },

    requestAdd: (modelId, className, position, hintId) => {
      const state = get()
      const model = state.open[modelId]
      const metamodel = model && state.metamodels[model.doc.metamodel]
      if (!model || !metamodel) return
      const slots = containerSlots(state.metamodels, metamodel.nsURI, model.doc.roots, className)
      const preferred = hintId ?? (model.selection.length === 1 ? model.selection[0] : undefined)
      const inPreferred = slots.filter((s) => s.parent.id === preferred)
      if (inPreferred.length === 1) return insert(modelId, className, inPreferred[0], position)
      if (canBeRoot(state.metamodels, metamodel, className)) return insert(modelId, className, null, position)
      if (slots.length === 1) return insert(modelId, className, slots[0], position)
      if (slots.length === 0) {
        set({
          notice: {
            kind: 'error',
            text: `Nessun elemento del modello può contenere un oggetto ${className}. Crea prima il suo contenitore.`,
          },
        })
        return
      }
      set({ pendingPlacement: { modelId, className, slots, position } })
    },

    placePending: (slot) => {
      const pending = get().pendingPlacement
      set({ pendingPlacement: null })
      if (pending && slot) insert(pending.modelId, pending.className, slot, pending.position)
    },

    removeSelected: (modelId) => {
      const model = get().open[modelId]
      if (!model || !model.selection.length) return
      applyRoots(modelId, removeObjects(model.doc.roots, model.selection), { selection: [] })
      settlePositions(modelId)
    },

    copy: (modelId) => {
      const model = get().open[modelId]
      const located = model && model.selection.length === 1 ? locate(model.doc.roots, model.selection[0]) : undefined
      if (located) {
        set({ clipboard: located.object, notice: { kind: 'info', text: `Copiato ${located.object.id}.` } })
      }
    },

    paste: (modelId) => {
      const state = get()
      const model = state.open[modelId]
      const source = state.clipboard
      const metamodel = model && state.metamodels[model.doc.metamodel]
      if (!model || !source || !metamodel) return
      if (!findClass(state.metamodels, { metamodel: metamodel.nsURI, name: source.eClass })) {
        set({ notice: { kind: 'error', text: `Un oggetto ${source.eClass} non può stare in questo modello.` } })
        return
      }
      const copy = cloneWithNewIds(model.doc.roots, source)
      // Paste next to the original when it is still there, otherwise wherever the class fits.
      const original = locate(model.doc.roots, source.id)
      const slots = containerSlots(state.metamodels, metamodel.nsURI, model.doc.roots, source.eClass)
      const slot =
        slots.find((s) => s.parent.id === original?.parent?.id && s.reference.name === original?.feature) ??
        slots.find((s) => model.selection.includes(s.parent.id)) ??
        (canBeRoot(state.metamodels, metamodel, source.eClass) ? null : slots[0])
      if (slot === undefined) {
        set({ notice: { kind: 'error', text: `Non c'è posto per un altro oggetto ${source.eClass}.` } })
        return
      }
      const roots = slot
        ? addChild(model.doc.roots, slot.parent.id, slot.reference.name, copy)
        : addRoot(model.doc.roots, copy)
      applyRoots(modelId, roots, { selection: [copy.id] })
      settlePositions(modelId, slot?.parent.id)
    },

    save: async (modelId) => {
      const model = get().open[modelId]
      if (!model) return
      try {
        const saved = await api.saveModel(model.doc)
        patch(modelId, (current) => ({
          // Keep edits made while the request was in flight.
          doc: current.doc.roots === model.doc.roots ? saved : { ...current.doc, version: saved.version },
          dirty: current.doc.roots !== model.doc.roots,
        }))
        set((state) => ({
          externals: {},
          models: state.models.map((m) => (m.id === saved.id ? { ...m, version: saved.version, role: saved.role } : m)),
          notice: { kind: 'info', text: `«${saved.name}» salvato.` },
        }))
        await get().validate(modelId)
      } catch (e) {
        const conflict = e instanceof ApiError && e.status === 409
        set({
          notice: {
            kind: 'error',
            text: conflict
              ? 'Il modello è stato modificato altrove dopo l’apertura. Chiudi la scheda e riaprilo per vedere la versione aggiornata.'
              : `Salvataggio non riuscito: ${message(e)}`,
          },
        })
      }
    },

    restoreVersion: async (modelId, version) => {
      const model = get().open[modelId]
      if (!model) return false
      if (model.dirty) {
        set({ notice: { kind: 'error', text: 'Salva o annulla le modifiche prima di ripristinare una versione.' } })
        return false
      }
      try {
        const restored = await api.restoreVersion(modelId, version)
        await load(modelId)
        set((state) => ({
          externals: {},
          models: state.models.map((m) => (m.id === restored.id ? { ...m, version: restored.version } : m)),
          notice: { kind: 'info', text: `«${restored.name}» riportato al contenuto della versione ${version}.` },
        }))
        return true
      } catch (e) {
        set({ notice: { kind: 'error', text: `Ripristino non riuscito: ${message(e)}` } })
        return false
      }
    },

    validate: async (modelId) => {
      const model = get().open[modelId]
      if (!model || model.dirty) return
      try {
        const diagnostics = await api.validate(modelId)
        patch(modelId, (current) => (current.dirty ? {} : { diagnostics, validated: true }))
      } catch (e) {
        set({ notice: { kind: 'error', text: `Validazione non riuscita: ${message(e)}` } })
      }
    },

    applyFix: async (modelId, diagnosticId, fixIndex) => {
      const model = get().open[modelId]
      if (!model) return
      if (model.dirty) {
        set({ notice: { kind: 'error', text: 'Salva il modello prima di applicare una correzione.' } })
        return
      }
      try {
        const result = await api.applyFix(modelId, diagnosticId, fixIndex)
        patch(modelId, () => ({
          doc: result.model,
          diagnostics: result.diagnostics,
          validated: true,
          dirty: false,
          past: [],
          future: [],
        }))
        set({ externals: {}, notice: { kind: 'info', text: 'Correzione applicata e salvata.' } })
        settlePositions(modelId)
      } catch (e) {
        set({ notice: { kind: 'error', text: `Correzione non riuscita: ${message(e)}` } })
      }
    },

    loadExternals: async (exceptId) => {
      const state = get()
      const missing = state.models.filter((m) => m.id !== exceptId && !state.externals[m.id])
      if (!missing.length) return
      try {
        const docs = await Promise.all(missing.map((m) => api.getModel(m.id)))
        set((s) => ({ externals: { ...s.externals, ...Object.fromEntries(docs.map((d) => [d.id, d])) } }))
      } catch {
        // candidates stay empty; saving still reports dangling references
      }
    },
  }
})
