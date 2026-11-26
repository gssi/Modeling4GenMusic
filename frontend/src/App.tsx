import { useEffect } from 'react'
import { Dialog } from './app/Dialog'
import { ProblemsPanel } from './app/ProblemsPanel'
import { useStudio } from './app/store'
import { metamodelColor } from './app/theme'
import { WorkspacePanel } from './app/WorkspacePanel'
import { Canvas } from './editor/Canvas'
import { Palette } from './editor/Palette'
import { PropertiesPanel } from './editor/PropertiesPanel'

export default function App() {
  const ready = useStudio((s) => s.ready)
  const loadError = useStudio((s) => s.loadError)
  const tabs = useStudio((s) => s.tabs)
  const activeId = useStudio((s) => s.activeId)
  const open = useStudio((s) => s.open)
  const metamodels = useStudio((s) => s.metamodels)
  const summaries = useStudio((s) => s.summaries)
  const notice = useStudio((s) => s.notice)
  const pending = useStudio((s) => s.pendingPlacement)
  const { init, activate, closeTab, notify, requestAdd, placePending, save } = useStudio.getState()

  useEffect(() => {
    void init()
  }, [init])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
        event.preventDefault()
        const id = useStudio.getState().activeId
        if (id) void save(id)
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

  return (
    <div className="shell">
      <header className="shell-header">
        <h1>Music Learner Modeling Studio</h1>
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
        {active && activeMetamodel && (
          <Palette
            metamodel={activeMetamodel}
            color={metamodelColor(summaries, activeMetamodel.nsURI)}
            onAdd={(className) => requestAdd(active.doc.id, className)}
          />
        )}
      </div>

      <main className="shell-center">
        {tabs.length > 0 && (
          <div className="tabs" role="tablist" aria-label="Modelli aperti">
            {tabs.map((id) => (
              <div key={id} className={`tab${id === activeId ? ' is-active' : ''}`}>
                <button type="button" role="tab" aria-selected={id === activeId} onClick={() => activate(id)}>
                  {open[id]?.doc.name ?? id}
                  {open[id]?.dirty && <span title="Modifiche non salvate"> •<span className="visually-hidden"> (modifiche non salvate)</span></span>}
                </button>
                <button
                  type="button"
                  className="icon"
                  aria-label={`Chiudi ${open[id]?.doc.name ?? id}`}
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
        {active ? (
          <Canvas key={active.doc.id} modelId={active.doc.id} />
        ) : (
          <div className="welcome">
            <h2>Apri un modello o creane uno</h2>
            <p>
              A sinistra trovi i modelli del workspace, raggruppati per tipo. Con «+» ne crei uno nuovo, con «Importa XMI»
              ne carichi uno esistente.
            </p>
          </div>
        )}
      </main>

      <div className="shell-right">{active && <PropertiesPanel modelId={active.doc.id} />}</div>

      <div className="shell-bottom">
        <ProblemsPanel modelId={active?.doc.id ?? null} />
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
