import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { Diagnostic } from '../api/types'
import { useStudio } from '../app/store'
import { PipelineView } from './PipelineView'

const PROFILE = 'http://music/profile'
const EXERCISE = 'http://music/exercise'
const QUALITY = 'http://music/quality'
const SEVERITY: Record<string, string> = { ERROR: 'Errore', WARNING: 'Avviso', INFO: 'Nota' }

/** Choose the intent and the learner, review the checks on the intent, derive the exercise spec. */
export function DerivationView() {
  const models = useStudio((s) => s.models)
  const derivation = useStudio((s) => s.derivation)
  const { setDerivationInput, derive, reloadModel, openModel } = useStudio.getState()
  const [diagnostics, setDiagnostics] = useState<Diagnostic[] | null>(null)
  const [dismissed, setDismissed] = useState<string[]>([])
  const [checkError, setCheckError] = useState<string | null>(null)

  const learners = models.filter((m) => m.metamodel === PROFILE)
  const intents = models.filter((m) => m.metamodel === EXERCISE && m.role !== 'derived')
  const qualities = models.filter((m) => m.metamodel === QUALITY)
  const { learnerId, intentId, qualityId, running, result } = derivation

  const check = useCallback(async (id: string) => {
    setCheckError(null)
    try {
      setDiagnostics(await api.validate(id))
    } catch (e) {
      setDiagnostics(null)
      setCheckError(e instanceof Error ? e.message : 'Errore inatteso')
    }
  }, [])

  useEffect(() => {
    setDiagnostics(null)
    setDismissed([])
    if (intentId) void check(intentId)
  }, [intentId, check])

  // Choosing an intent proposes the learner it was written for and shows its quality model.
  useEffect(() => {
    if (!intentId) return
    let cancelled = false
    api
      .getModel(intentId)
      .then((doc) => {
        if (cancelled) return
        const learner = doc.roots[0]?.references.learner?.[0]?.model
        // The quality model is not chosen here: it is the one the goals of the intent refer to.
        setDerivationInput({ qualityId: doc.roots[0]?.references.qualityModel?.[0]?.model ?? '', ...(learner && learners.some((l) => l.id === learner) ? { learnerId: learner } : {}) })
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
    // Only the chosen intent matters: the list of profiles is read when the answer arrives.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [intentId])

  const applyFix = async (diagnostic: Diagnostic, index: number) => {
    try {
      const fixed = await api.applyFix(intentId, diagnostic.id, index)
      setDiagnostics(fixed.diagnostics)
      await reloadModel(intentId)
    } catch (e) {
      setCheckError(e instanceof Error ? e.message : 'Errore inatteso')
    }
  }

  const errors = diagnostics?.filter((d) => d.severity === 'ERROR') ?? []
  const pending = diagnostics?.filter((d) => d.fixes.length > 0 && !dismissed.includes(d.id)) ?? []
  const ready = !!learnerId && !!intentId && diagnostics !== null && errors.length === 0 && pending.length === 0

  const select = (label: string, id: string, value: string, options: typeof models, onChange: (v: string) => void, empty: string) => (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{options.length ? 'Scegli…' : empty}</option>
        {options.map((m) => (
          <option key={m.id} value={m.id}>
            {m.name}
          </option>
        ))}
      </select>
    </div>
  )

  return (
    <div className="form-view derivation">
      <section>
        <h2>Da quali modelli derivare</h2>
        <div className="form-grid">
          {select('Intento didattico', 'derive-intent', intentId, intents, (v) => setDerivationInput({ intentId: v }), 'Nessun intento nel workspace')}
          {select('Profilo dell’allievo', 'derive-learner', learnerId, learners, (v) => setDerivationInput({ learnerId: v }), 'Nessun profilo nel workspace')}
          <div className="field">
            <span className="field-label">Modello di qualità</span>
            <p aria-label="Modello di qualità dell’intento">
              {!intentId ? 'Quello indicato dall’intento.' : qualityId ? (qualities.find((m) => m.id === qualityId)?.name ?? qualityId) : 'Nessuno: l’intento non ha obiettivi di qualità.'}
            </p>
          </div>
        </div>
      </section>

      {intentId && (
        <section aria-live="polite">
          <h2>Controlli sull’intento</h2>
          {checkError && <p className="field-error">Controllo non riuscito: {checkError}</p>}
          {!checkError && diagnostics === null && <p className="hint">Controllo in corso…</p>}
          {diagnostics?.length === 0 && <p className="hint">Nessun problema: l’intento è pronto per la derivazione.</p>}
          {diagnostics && diagnostics.length > 0 && (
            <ul className="problem-list">
              {diagnostics.map((d) => (
                <li key={d.id} className={`problem is-${d.severity.toLowerCase()}`}>
                  <span className="problem-severity">{SEVERITY[d.severity]}</span>
                  <span className="problem-text">
                    {d.message}
                    <span className="problem-source">{d.constraint}</span>
                  </span>
                  {!dismissed.includes(d.id) &&
                    d.fixes.map((fix, index) => (
                      <button key={fix.title} type="button" className="problem-fix" onClick={() => void applyFix(d, index)}>
                        {fix.title}
                      </button>
                    ))}
                  {d.fixes.length > 0 &&
                    (dismissed.includes(d.id) ? (
                      <span className="hint">Lasciato com’è.</span>
                    ) : (
                      <button type="button" onClick={() => setDismissed((ids) => [...ids, d.id])}>
                        Lascia com’è
                      </button>
                    ))}
                </li>
              ))}
            </ul>
          )}
          {errors.length > 0 && <p className="field-error">Correggi gli errori dell’intento prima di derivare.</p>}
          {errors.length === 0 && pending.length > 0 && <p className="hint">Per ogni correzione proposta, applicala oppure lasciala com’è.</p>}
        </section>
      )}

      <section>
        <button type="button" className="primary" disabled={!ready || running} onClick={() => void derive()}>
          {running ? 'Derivazione in corso…' : 'Deriva'}
        </button>
        {result && (
          <p className="derivation-done" role="status">
            Spec derivata.{' '}
            <button type="button" className="link" onClick={() => void openModel(result.derivedSpecModelId)}>
              Apri la spec
            </button>{' '}
            oppure{' '}
            <button type="button" className="link" onClick={() => void openModel(result.weavingModelId)}>
              la traccia di derivazione
            </button>
            .
          </p>
        )}
      </section>

      <section>
        <h2>Il processo</h2>
        <PipelineView done={!!result} />
      </section>
    </div>
  )
}
