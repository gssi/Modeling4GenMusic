import { useEffect, useMemo, useState } from 'react'
import { Dialog } from './app/Dialog'
import { ProblemsPanel } from './app/ProblemsPanel'
import { VersionsButton } from './app/VersionHistory'
import { RulesView } from './app/RulesView'
import { DERIVE_TAB, RULES_TAB, useStudio } from './app/store'
import { metamodelColor } from './app/theme'
import { WorkspacePanel } from './app/WorkspacePanel'
import { roleOptions } from './domain/labels'
import { DerivationView } from './domain/DerivationView'
import { provenanceDecoration } from './domain/provenance'
import { DerivationLog, ProvenanceSection } from './domain/ProvenanceSection'
import { domainView } from './domain/views'
import { Canvas } from './editor/Canvas'
import { Palette } from './editor/Palette'
import { PropertiesPanel } from './editor/PropertiesPanel'

/** One model in the centre: the switch between diagram and form, and the chosen view. */
function ModelPane({ modelId, beside }: { modelId: string; beside?: boolean }) {
  const model = useStudio((s) => s.open[modelId])
  const mode = useStudio((s) => s.viewMode[modelId] ?? 'diagram')
  const provenance = useStudio((s) => s.provenance[modelId])
  const { setViewMode, setRole, save, undo, redo, closeSplit } = useStudio.getState()
  const decoration = useMemo(() => (provenance ? provenanceDecoration(provenance) : undefined), [provenance])
  const view = domainView(model.doc.metamodel, model.doc.role)
  const roles = roleOptions(model.doc.metamodel)
  const Form = view?.component

  return (
    <section className="pane" aria-label={model.doc.name}>
      {(view || beside || roles.length > 0) && (
        <div className="pane-head">
          {beside && <h2>{model.doc.name}</h2>}
          {view && (
            <div className="segmented" role="group" aria-label="Vista del modello">
              <button type="button" className={mode === 'diagram' ? 'is-on' : undefined} aria-pressed={mode === 'diagram'} onClick={() => setViewMode(modelId, 'diagram')}>
                Diagramma
              </button>
              <button type="button" className={mode === 'form' ? 'is-on' : undefined} aria-pressed={mode === 'form'} onClick={() => setViewMode(modelId, 'form')}>
                {view.label}
              </button>
            </div>
          )}
          {roles.length > 0 && (
            <label className="pane-role">
              Tipo
              <select aria-label="Tipo di modello" value={model.doc.role ?? ''} onChange={(e) => setRole(modelId, e.target.value)}>
                {!model.doc.role && <option value="">Non indicato</option>}
                {roles.map((role) => (
                  <option key={role.value} value={role.value}>
                    {role.label}
                  </option>
                ))}
              </select>
            </label>
          )}
          {beside && (
            <button type="button" onClick={closeSplit}>
              Chiudi il confronto
            </button>
          )}
        </div>
      )}
      {Form && mode === 'form' ? (
        <div className="canvas">
          <div className="canvas-toolbar" role="toolbar" aria-label="Azioni sul modello">
            <button type="button" className="primary" onClick={() => void save(modelId)} disabled={!model.dirty}>
              Salva
            </button>
            <VersionsButton modelId={modelId} />
            <span className="toolbar-gap" />
            <button type="button" onClick={() => undo(modelId)} disabled={!model.past.length}>
              Annulla
            </button>
            <button type="button" onClick={() => redo(modelId)} disabled={!model.future.length}>
              Ripeti
            </button>
          </div>
          <div className="form-scroll">
            <Form modelId={modelId} />
          </div>
        </div>
      ) : (
        <Canvas key={modelId} modelId={modelId} decoration={decoration} toolbarExtra={<VersionsButton modelId={modelId} />} />
      )}
    </section>
  )
}

function BottomPanel({ modelId }: { modelId: string | null }) {
  const provenance = useStudio((s) => (modelId ? s.provenance[modelId] : undefined))
  const [tab, setTab] = useState<'problems' | 'log'>('problems')
  const showLog = tab === 'log' && !!provenance && !!modelId
  return (
    <>
      {provenance && (
        <div className="bottom-tabs" role="group" aria-label="Pannello inferiore">
          <button type="button" className={!showLog ? 'is-on' : undefined} aria-pressed={!showLog} onClick={() => setTab('problems')}>
            Problemi
          </button>
          <button type="button" className={showLog ? 'is-on' : undefined} aria-pressed={showLog} onClick={() => setTab('log')}>
            Log di derivazione
          </button>
        </div>
      )}
      {showLog ? (
        <section className="problems" aria-label="Log di derivazione">
          <DerivationLog provenance={provenance} onSelect={(id) => useStudio.getState().select(modelId, [id])} />
        </section>
      ) : (
        <ProblemsPanel modelId={modelId} />
      )}
    </>
  )
}

export default function App() {
  const ready = useStudio((s) => s.ready)
  const loadError = useStudio((s) => s.loadError)
  const tabs = useStudio((s) => s.tabs)
  const activeId = useStudio((s) => s.activeId)
  const open = useStudio((s) => s.open)
  const split = useStudio((s) => s.split)
  const viewMode = useStudio((s) => s.viewMode)
  const provenance = useStudio((s) => s.provenance)
  const metamodels = useStudio((s) => s.metamodels)
  const summaries = useStudio((s) => s.summaries)
  const notice = useStudio((s) => s.notice)
  const pending = useStudio((s) => s.pendingPlacement)
  const { init, activate, closeTab, notify, requestAdd, placePending, save, openDerivation, openRules } = useStudio.getState()

  useEffect(() => {
    void init()
  }, [init])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
        event.preventDefault()
        const id = useStudio.getState().activeId
        if (id && useStudio.getState().open[id]) void save(id)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [save])

  useEffect(() => {
    if (notice?.kind !== 'info') return
    const timer = setTimeout(() => notify(null), 4000)
    return () => clearTimeout(timer)
  }, [notice, notify])

  // Warn before leaving with unsaved changes.
  useEffect(() => {
    const dirty = Object.values(open).some((m) => m.dirty)
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [open])

  if (loadError) {
    return (
      <main className="startup">
        <h1>Music Learner Modeling Studio</h1>
        <p>Non riesco a leggere i metamodelli dal server: {loadError}</p>
        <button type="button" onClick={() => void init()}>
          Riprova
        </button>
      </main>
    )
  }
  if (!ready) {
    return (
      <main className="startup">
        <h1>Music Learner Modeling Studio</h1>
        <p>Caricamento dei metamodelli…</p>
      </main>
    )
  }

  const active = activeId ? open[activeId] : undefined
  const activeMetamodel = active ? metamodels[active.doc.metamodel] : undefined
  const diagram = !!active && (viewMode[active.doc.id] ?? 'diagram') === 'diagram'
  const beside = split && split !== activeId && open[split] ? split : null
  const activeProvenance = active ? provenance[active.doc.id] : undefined
  const tabName = (id: string) => (id === DERIVE_TAB ? 'Derivazione' : id === RULES_TAB ? 'Regole' : (open[id]?.doc.name ?? id))

  return (
    <div className="shell">
      <header className="shell-header">
        <h1>Music Learner Modeling Studio</h1>
        <button type="button" className="header-action" onClick={openDerivation}>
          Deriva un esercizio
        </button>
        <button type="button" className="header-action" onClick={() => openRules()}>
          Regole
        </button>
        <div className={`notice${notice ? ` is-${notice.kind}` : ''}`} role="status" aria-live="polite">
          {notice && (
            <>
              <span>{notice.text}</span>
              <button type="button" className="icon" aria-label="Chiudi il messaggio" onClick={() => notify(null)}>
                ×
              </button>
            </>
          )}
        </div>
      </header>

      <div className="shell-left">
        <WorkspacePanel />
        {active && activeMetamodel && diagram && (
          <Palette
            metamodel={activeMetamodel}
            color={metamodelColor(summaries, activeMetamodel.nsURI)}
            onAdd={(className) => requestAdd(active.doc.id, className)}
          />
        )}
      </div>

      <main className="shell-center">
        {tabs.length > 0 && (
          <div className="tabs" role="tablist" aria-label="Schede aperte">
            {tabs.map((id) => (
              <div key={id} className={`tab${id === activeId ? ' is-active' : ''}`}>
                <button type="button" role="tab" aria-selected={id === activeId} onClick={() => activate(id)}>
                  {tabName(id)}
                  {open[id]?.dirty && (
                    <span title="Modifiche non salvate">
                      {' '}
                      •<span className="visually-hidden"> (modifiche non salvate)</span>
                    </span>
                  )}
                </button>
                <button
                  type="button"
                  className="icon"
                  aria-label={`Chiudi ${tabName(id)}`}
                  onClick={() => {
                    if (open[id]?.dirty) {
                      notify({ kind: 'error', text: 'Salva le modifiche prima di chiudere la scheda, oppure annullale.' })
                    } else {
                      closeTab(id)
                    }
                  }}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
        {activeId === DERIVE_TAB ? (
          <div className="form-scroll">
            <DerivationView />
          </div>
        ) : activeId === RULES_TAB ? (
          <div className="form-scroll">
            <RulesView />
          </div>
        ) : active ? (
          <div className={`panes${beside ? ' is-split' : ''}`}>
            <ModelPane key={active.doc.id} modelId={active.doc.id} />
            {beside && <ModelPane key={beside} modelId={beside} beside />}
          </div>
        ) : (
          <div className="welcome">
            <h2>Apri un modello o creane uno</h2>
            <p>
              A sinistra trovi i modelli del workspace, raggruppati per tipo. Con «+» ne crei uno nuovo, con «Importa XMI»
              ne carichi uno esistente. Quando hai profilo, intento e modello di qualità, «Deriva un esercizio» produce la
              specifica completa.
            </p>
          </div>
        )}
      </main>

      <div className="shell-right">
        {active && diagram && (
          <PropertiesPanel
            modelId={active.doc.id}
            extra={activeProvenance ? (elementId) => <ProvenanceSection links={activeProvenance.byElement[elementId] ?? []} /> : undefined}
          />
        )}
        {active && !diagram && (
          <aside className="properties" aria-label="Proprietà">
            <h2>Proprietà</h2>
            <p className="hint">Nella scheda modifichi i valori direttamente. Passa a «Diagramma» per vedere ogni elemento con le sue proprietà.</p>
          </aside>
        )}
      </div>

      <div className="shell-bottom">
        <BottomPanel modelId={active?.doc.id ?? null} />
      </div>

      {pending && (
        <Dialog title={`Dove va il nuovo ${pending.className}?`} onClose={() => placePending(null)}>
          <p>Più elementi del modello possono contenerlo. Scegli il contenitore.</p>
          <ul className="choice-list">
            {pending.slots.map((slot) => (
              <li key={`${slot.parent.id}.${slot.reference.name}`}>
                <button type="button" onClick={() => placePending(slot)}>
                  {slot.parent.id} : {slot.parent.eClass}
                  <span className="choice-detail"> come {slot.reference.name}</span>
                </button>
              </li>
            ))}
          </ul>
        </Dialog>
      )}
    </div>
  )
}
