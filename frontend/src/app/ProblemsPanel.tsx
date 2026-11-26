import type { Diagnostic, Severity } from '../api/types'
import { useStudio } from './store'

const ORDER: Record<Severity, number> = { ERROR: 0, WARNING: 1, INFO: 2 }
const LABEL: Record<Severity, string> = { ERROR: 'Errore', WARNING: 'Avviso', INFO: 'Nota' }

export function ProblemsPanel({ modelId }: { modelId: string | null }) {
  const model = useStudio((s) => (modelId ? s.open[modelId] : undefined))
  const known = useStudio((s) => s.rules)
  const { select, applyFix, openRules } = useStudio.getState()
  const isRule = (name: string) => !!known?.some((set) => set.contexts.some((c) => c.rules.some((r) => r.name === name)))

  let body
  if (!modelId || !model) {
    body = <p className="hint">Apri un modello per vederne i problemi.</p>
  } else if (model.dirty) {
    body = <p className="hint">Ci sono modifiche non salvate. I problemi si aggiornano al salvataggio.</p>
  } else if (!model.validated) {
    body = <p className="hint">Validazione in corso…</p>
  } else if (!model.diagnostics.length) {
    body = <p className="hint">Nessun problema nel modello.</p>
  } else {
    const sorted = [...model.diagnostics].sort((a, b) => ORDER[a.severity] - ORDER[b.severity])
    body = (
      <ul className="problem-list">
        {sorted.map((d: Diagnostic) => (
          <li key={d.id} className={`problem is-${d.severity.toLowerCase()}`}>
            <span className="problem-severity">{LABEL[d.severity]}</span>
            <button
              type="button"
              className="problem-message"
              disabled={!d.elementId}
              onClick={() => d.elementId && select(modelId, [d.elementId])}
              title={d.elementId ? `Mostra ${d.elementId} nel diagramma` : undefined}
            >
              {d.message}
              <span className="problem-source">
                {d.elementId ? `${d.elementId}, ` : ''}
                {d.constraint}
              </span>
            </button>
            {isRule(d.constraint) && (
              <button type="button" className="problem-rule" onClick={() => openRules(d.constraint)} title={`Mostra la regola ${d.constraint}`}>
                Vedi la regola
              </button>
            )}
            {d.fixes.map((fix, index) => (
              <button key={fix.title} type="button" className="problem-fix" onClick={() => void applyFix(modelId, d.id, index)}>
                {fix.title}
              </button>
            ))}
          </li>
        ))}
      </ul>
    )
  }

  const errors = model?.diagnostics.filter((d) => d.severity === 'ERROR').length ?? 0
  const warnings = model?.diagnostics.filter((d) => d.severity === 'WARNING').length ?? 0
  return (
    <section className="problems" aria-label="Problemi">
      <h2>
        Problemi
        {model && !model.dirty && model.validated && (errors > 0 || warnings > 0) && (
          <span className="problems-count">
            {errors > 0 && `${errors} ${errors === 1 ? 'errore' : 'errori'}`}
            {errors > 0 && warnings > 0 && ', '}
            {warnings > 0 && `${warnings} ${warnings === 1 ? 'avviso' : 'avvisi'}`}
          </span>
        )}
      </h2>
      <div aria-live="polite">{body}</div>
    </section>
  )
}
