import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AttributeValue, ModelDocument, ModelObject, ModelVersion } from '../api/types'
import { diffRoots, isEmptyDiff } from '../editor/diff'
import { Dialog } from './Dialog'
import { useStudio } from './store'

function originLabel(version: ModelVersion): string {
  switch (version.origin) {
    case 'CREATED':
      return 'Creazione'
    case 'IMPORTED':
      return 'Importazione'
    case 'SAVED':
      return 'Salvataggio'
    case 'FIXED':
      return 'Correzione rapida'
    case 'DERIVED':
      return 'Derivazione'
    case 'RESTORED':
      return `Ripristino della versione ${version.restoredFrom}`
    default:
      return 'Modifica fuori dall’editor'
  }
}

const when = (savedAt: string) => new Date(savedAt).toLocaleString('it-IT', { dateStyle: 'medium', timeStyle: 'short' })

const shown = (value: AttributeValue | undefined) => (value === undefined || value === '' ? 'vuoto' : String(value))

const objectName = (object: ModelObject) => `${object.eClass} ${object.id}`

function ObjectList({ title, objects }: { title: string; objects: ModelObject[] }) {
  if (!objects.length) return null
  return (
    <>
      <h4>{title}</h4>
      <ul>
        {objects.map((o) => (
          <li key={o.id}>{objectName(o)}</li>
        ))}
      </ul>
    </>
  )
}

/** What the chosen version holds, told as differences from what is open in the editor. */
function Comparison({ current, past }: { current: ModelObject[]; past: ModelDocument }) {
  const diff = diffRoots(current, past.roots)
  if (isEmptyDiff(diff)) return <p className="hint">Ha lo stesso contenuto del modello aperto.</p>
  return (
    <div className="version-diff">
      <ObjectList title="Ha in più" objects={diff.added} />
      <ObjectList title="Non ha" objects={diff.removed} />
      {diff.changed.length > 0 && (
        <>
          <h4>Ha valori diversi</h4>
          <ul>
            {diff.changed.map((change) => (
              <li key={change.object.id}>
                {objectName(change.object)}
                <ul>
                  {change.attributes.map((a) => (
                    <li key={a.name}>
                      {a.name}: {shown(a.after)} <span className="hint">(ora {shown(a.before)})</span>
                    </li>
                  ))}
                  {change.references.map((name) => (
                    <li key={name}>{name}: collegamenti diversi</li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function VersionDialog({ modelId, onClose }: { modelId: string; onClose: () => void }) {
  const model = useStudio((s) => s.open[modelId])
  const [versions, setVersions] = useState<ModelVersion[] | null>(null)
  const [chosen, setChosen] = useState<number | null>(null)
  const [past, setPast] = useState<ModelDocument | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [restoring, setRestoring] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      .listVersions(modelId)
      .then((list) => {
        if (cancelled) return
        setVersions(list)
        setChosen(list[1]?.version ?? null)
      })
      .catch((e: unknown) => !cancelled && setError(e instanceof Error ? e.message : String(e)))
    return () => {
      cancelled = true
    }
  }, [modelId])

  useEffect(() => {
    if (chosen === null) return
    let cancelled = false
    api
      .getVersion(modelId, chosen)
      .then((doc) => !cancelled && setPast(doc))
      .catch((e: unknown) => !cancelled && setError(e instanceof Error ? e.message : String(e)))
    return () => {
      cancelled = true
    }
  }, [modelId, chosen])

  const currentVersion = versions?.[0]?.version
  const shownPast = past && past.version === chosen ? past : null
  const restore = async () => {
    if (chosen === null) return
    setRestoring(true)
    const done = await useStudio.getState().restoreVersion(modelId, chosen)
    setRestoring(false)
    if (done) onClose()
  }

  return (
    <Dialog
      title={`Versioni di «${model.doc.name}»`}
      wide
      onClose={onClose}
      actions={
        <>
          <button type="button" onClick={onClose}>
            Chiudi
          </button>
          <button
            type="button"
            className="primary"
            onClick={() => void restore()}
            disabled={chosen === null || chosen === currentVersion || model.dirty || restoring}
          >
            {chosen === null || chosen === currentVersion ? 'Ripristina' : `Ripristina la versione ${chosen}`}
          </button>
        </>
      }
    >
      {error && <p role="alert">{error}</p>}
      {!versions && !error && <p className="hint">Lettura delle versioni…</p>}
      {versions && (
        <div className="versions">
          <ul className="version-list" aria-label="Versioni salvate">
            {versions.map((v) => (
              <li key={v.version}>
                <button type="button" className={v.version === chosen ? 'is-on' : undefined} aria-pressed={v.version === chosen} onClick={() => setChosen(v.version)}>
                  <span className="version-number">
                    Versione {v.version}
                    {v.version === currentVersion && ' (attuale)'}
                  </span>
                  <span className="version-detail">
                    {when(v.savedAt)}, {originLabel(v).toLowerCase()}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <section className="version-compare" aria-live="polite" aria-label="Confronto con il modello aperto">
            {chosen === null ? (
              <p className="hint">Questo modello ha una sola versione. Ogni salvataggio ne aggiunge una.</p>
            ) : shownPast ? (
              <>
                <h3>La versione {chosen} rispetto al modello aperto</h3>
                <Comparison current={model.doc.roots} past={shownPast} />
              </>
            ) : (
              <p className="hint">Lettura della versione {chosen}…</p>
            )}
          </section>
        </div>
      )}
      {model.dirty && <p className="hint">Il modello ha modifiche non salvate: salvale o annullale prima di ripristinare una versione.</p>}
      {!model.dirty && chosen !== null && chosen !== currentVersion && (
        <p className="hint">Il ripristino aggiunge una nuova versione con questo contenuto: quelle esistenti restano.</p>
      )}
    </Dialog>
  )
}

/** Toolbar button that opens the saved versions of a model. */
export function VersionsButton({ modelId }: { modelId: string }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Versioni
      </button>
      {open && <VersionDialog modelId={modelId} onClose={() => setOpen(false)} />}
    </>
  )
}
