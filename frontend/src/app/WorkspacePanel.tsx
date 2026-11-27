import { useRef, useState } from 'react'
import type { MetamodelSummary, ModelHeader } from '../api/types'
import { roleLabel, wording } from '../domain/labels'
import { Dialog } from './Dialog'
import { type FolderNode, countModels, folderPath, folderPaths, folderTree, readClosedFolders, writeClosedFolders } from './folders'
import { useStudio } from './store'
import { metamodelColor } from './theme'

/** The folders in use, offered wherever a folder is typed. */
export const FOLDER_SUGGESTIONS = 'workspace-folders'

function CreateDialog({ summary, folder: initialFolder, onClose }: { summary: MetamodelSummary; folder: string; onClose: () => void }) {
  const words = wording(summary.nsURI, summary.name)
  const [name, setName] = useState('')
  const [folder, setFolder] = useState(initialFolder)
  const [role, setRole] = useState(words.roles?.[0]?.value ?? '')
  const submit = () => {
    if (!name.trim()) return
    void useStudio.getState().createModel(summary.nsURI, name, role || undefined, folderPath(folder) || undefined)
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
      <div className="field">
        <label htmlFor="new-model-folder">Cartella</label>
        <input id="new-model-folder" type="text" list={FOLDER_SUGGESTIONS} placeholder="Nessuna" value={folder} onChange={(e) => setFolder(e.target.value)} />
        <p className="hint">Facoltativa. Usa «/» per una cartella dentro un’altra, per esempio «Classe 3/Giulia».</p>
      </div>
    </Dialog>
  )
}

export function WorkspacePanel() {
  const summaries = useStudio((s) => s.summaries)
  const models = useStudio((s) => s.models)
  const activeId = useStudio((s) => s.activeId)
  const open = useStudio((s) => s.open)
  const { openModel, deleteModel, importModel } = useStudio.getState()
  const [creating, setCreating] = useState<{ summary: MetamodelSummary; folder: string } | null>(null)
  const [deleting, setDeleting] = useState<ModelHeader | null>(null)
  const [closed, setClosed] = useState(readClosedFolders)
  const fileInput = useRef<HTMLInputElement>(null)
  const importFolder = useRef('')
  const tree = folderTree(models)

  const toggle = (path: string) => {
    const next = closed.includes(path) ? closed.filter((p) => p !== path) : [...closed, path]
    setClosed(next)
    writeClosedFolders(next)
  }

  const chooseFiles = (folder: string) => {
    importFolder.current = folder
    fileInput.current?.click()
  }

  /** The models of one folder, by kind. At the top every kind is listed, so that each can be created. */
  const groups = (node: FolderNode) =>
    summaries.map((summary) => {
      const words = wording(summary.nsURI, summary.name)
      const group = node.models.filter((m) => m.metamodel === summary.nsURI)
      if (node.path && group.length === 0) return null
      const create = node.path ? `${words.create} in ${node.path}` : words.create
      return (
        <section key={summary.nsURI} className="workspace-group">
          <h3>
            <span className="palette-swatch" style={{ background: metamodelColor(summaries, summary.nsURI) }} aria-hidden="true" />
            {words.plural}
            <button type="button" className="icon" aria-label={create} title={create} onClick={() => setCreating({ summary, folder: node.path })}>
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
    })

  const folder = (node: FolderNode) => {
    const isOpen = !closed.includes(node.path)
    const count = countModels(node)
    return (
      <section key={node.path} className="workspace-folder" aria-label={`Cartella ${node.path}`}>
        <div className="workspace-folder-head">
          <button type="button" className="workspace-folder-toggle" aria-expanded={isOpen} onClick={() => toggle(node.path)}>
            <span className="workspace-folder-mark" aria-hidden="true">
              {isOpen ? '▾' : '▸'}
            </span>
            <span className="workspace-folder-name">{node.name}</span>
            <span className="workspace-folder-count">
              {count}
              <span className="visually-hidden"> {count === 1 ? 'modello' : 'modelli'}</span>
            </span>
          </button>
          <button type="button" className="icon" aria-label={`Importa XMI in ${node.path}`} title="Importa XMI in questa cartella" onClick={() => chooseFiles(node.path)}>
            ↥
          </button>
        </div>
        {isOpen && (
          <div className="workspace-folder-body">
            {groups(node)}
            {node.children.map(folder)}
          </div>
        )}
      </section>
    )
  }

  return (
    <nav className="workspace" aria-label="Modelli del workspace">
      <div className="workspace-head">
        <h2>Modelli</h2>
        <button type="button" onClick={() => chooseFiles('')}>
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
            for (const file of files) await importModel(file, undefined, importFolder.current || undefined)
          }}
        />
      </div>
      {groups(tree)}
      {tree.children.map(folder)}
      <datalist id={FOLDER_SUGGESTIONS}>
        {folderPaths(models).map((path) => (
          <option key={path} value={path} />
        ))}
      </datalist>
      {creating && <CreateDialog summary={creating.summary} folder={creating.folder} onClose={() => setCreating(null)} />}
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
