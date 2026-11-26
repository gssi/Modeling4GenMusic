import { useRef, useState } from 'react'
import type { MetamodelSummary, ModelHeader } from '../api/types'
import { roleLabel, wording } from '../domain/labels'
import { Dialog } from './Dialog'
import { useStudio } from './store'
import { metamodelColor } from './theme'

function CreateDialog({ summary, onClose }: { summary: MetamodelSummary; onClose: () => void }) {
  const words = wording(summary.nsURI, summary.name)
  const [name, setName] = useState('')
  const [role, setRole] = useState(words.roles?.[0]?.value ?? '')
  const submit = () => {
    if (!name.trim()) return
    void useStudio.getState().createModel(summary.nsURI, name, role || undefined)
    onClose()
  }
  return (
    <Dialog
      title={words.create}
      onClose={onClose}
      actions={
        <>
          <button type="button" onClick={onClose}>
            Annulla
          </button>
          <button type="button" className="primary" onClick={submit} disabled={!name.trim()}>
            Crea
          </button>
        </>
      }
    >
      <div className="field">
        <label htmlFor="new-model-name">Nome</label>
        <input
          id="new-model-name"
          type="text"
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') submit()
          }}
        />
      </div>
      {words.roles && (
        <div className="field">
          <label htmlFor="new-model-role">Tipo</label>
          <select id="new-model-role" value={role} onChange={(e) => setRole(e.target.value)}>
            {words.roles.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </div>
      )}
    </Dialog>
  )
}

export function WorkspacePanel() {
  const summaries = useStudio((s) => s.summaries)
  const models = useStudio((s) => s.models)
  const activeId = useStudio((s) => s.activeId)
  const open = useStudio((s) => s.open)
  const { openModel, deleteModel, importModel } = useStudio.getState()
  const [creating, setCreating] = useState<MetamodelSummary | null>(null)
  const [deleting, setDeleting] = useState<ModelHeader | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  return (
    <nav className="workspace" aria-label="Modelli del workspace">
      <div className="workspace-head">
        <h2>Modelli</h2>
        <button type="button" onClick={() => fileInput.current?.click()}>
          Importa XMI
        </button>
        <input
          ref={fileInput}
          type="file"
          accept=".xmi,application/xml,text/xml"
          multiple
          hidden
          onChange={async (e) => {
            const files = [...(e.target.files ?? [])]
            e.target.value = ''
            for (const file of files) await importModel(file)
          }}
        />
      </div>
      {summaries.map((summary) => {
        const words = wording(summary.nsURI, summary.name)
        const group = models.filter((m) => m.metamodel === summary.nsURI)
        return (
          <section key={summary.nsURI} className="workspace-group">
            <h3>
              <span className="palette-swatch" style={{ background: metamodelColor(summaries, summary.nsURI) }} aria-hidden="true" />
              {words.plural}
              <button
                type="button"
                className="icon"
                aria-label={words.create}
                title={words.create}
                onClick={() => setCreating(summary)}
              >
                +
              </button>
            </h3>
            {group.length === 0 ? (
              <p className="hint">Ancora nessuno.</p>
            ) : (
              <ul>
                {group.map((m) => (
                  <li key={m.id} className={m.id === activeId ? 'is-active' : undefined}>
                    <button type="button" className="workspace-model" onClick={() => void openModel(m.id)} aria-current={m.id === activeId}>
                      <span className="workspace-model-name">
                        {m.name}
                        {open[m.id]?.dirty && <span title="Modifiche non salvate"> •<span className="visually-hidden"> (modifiche non salvate)</span></span>}
                      </span>
                      {m.role && <span className="workspace-model-role">{roleLabel(m.metamodel, m.role)}</span>}
                    </button>
                    <button type="button" className="icon" aria-label={`Elimina ${m.name}`} title="Elimina" onClick={() => setDeleting(m)}>
                      ×
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )
      })}
      {creating && <CreateDialog summary={creating} onClose={() => setCreating(null)} />}
      {deleting && (
        <Dialog
          title={`Eliminare «${deleting.name}»?`}
          onClose={() => setDeleting(null)}
          actions={
            <>
              <button type="button" onClick={() => setDeleting(null)}>
                Annulla
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => {
                  void deleteModel(deleting.id)
                  setDeleting(null)
                }}
              >
                Elimina
              </button>
            </>
          }
        >
          <p>Il modello viene rimosso dal workspace insieme alla disposizione del suo diagramma. I modelli che lo citano mostreranno un riferimento non trovato.</p>
        </Dialog>
      )}
    </nav>
  )
}
