/** Read-only picture of the process: inputs, derivation, derived spec, and the phases still to come. */
export function PipelineView({ done }: { done: boolean }) {
  const arrow = (
    <svg className="pipeline-arrow" viewBox="0 0 40 16" aria-hidden="true">
      <path d="M0 8 H34 M27 2 L35 8 L27 14" fill="none" stroke="currentColor" strokeWidth="1.75" />
    </svg>
  )
  return (
    <figure className="pipeline" aria-label="Processo dalla descrizione dell’allievo all’esercizio">
      <ol>
        <li className="pipeline-inputs">
          <span className="pipeline-box">Profilo dell’allievo</span>
          <span className="pipeline-box">Intento didattico</span>
          <span className="pipeline-box">Modello di qualità</span>
        </li>
        <li>{arrow}</li>
        <li>
          <span className="pipeline-box is-phase">
            <strong>Fase 1</strong> Derivazione
          </span>
        </li>
        <li>{arrow}</li>
        <li className="pipeline-inputs">
          <span className={`pipeline-box${done ? ' is-done' : ''}`}>Spec dell’esercizio</span>
          <span className={`pipeline-box${done ? ' is-done' : ''}`}>Traccia di derivazione</span>
        </li>
        <li>{arrow}</li>
        <li className="pipeline-later">
          {[2, 3, 4, 5].map((phase) => (
            <span key={phase} className="pipeline-box is-phase is-stub">
              <strong>Fase {phase}</strong>
            </span>
          ))}
          <span className="hint">Generazione e controllo dell’esercizio: non ancora disponibili.</span>
        </li>
      </ol>
    </figure>
  )
}
