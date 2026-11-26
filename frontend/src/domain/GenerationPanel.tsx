import { useStudio } from '../app/store'
import { allObjects } from '../editor/modelOps'
import { AbcPreview } from './AbcPreview'

/** For a derived spec: asks for an exercise, shows it in notation and reports how it meets the spec. */
export function GenerationPanel({ modelId }: { modelId: string }) {
  const model = useStudio((s) => s.open[modelId])
  const result = useStudio((s) => s.generations[modelId])
  const generating = useStudio((s) => s.generating === modelId)
  const generate = useStudio((s) => s.generate)

  const render = allObjects(model.doc.roots).find((o) => o.eClass === 'RenderConstraint')
  const scale = Number(render?.attributes.staffScale ?? 1) || 1

  return (
    <div className="form-view">
      <section>
        <h2>Esercizio</h2>
        <p className="hint">
          La generazione musicale non è ancora collegata: per ora ottieni un esercizio di esempio, controllato rispetto a questa spec.
        </p>
        <button type="button" className="primary" onClick={() => void generate(modelId)} disabled={generating}>
          {generating ? 'Generazione in corso…' : result ? 'Genera di nuovo' : 'Genera esercizio'}
        </button>
      </section>

      {result && (
        <>
          <section>
            <h2>Partitura</h2>
            {scale !== 1 && <p className="hint">Pentagramma ingrandito {scale} volte, come richiesto dal vincolo di resa.</p>}
            <div className="score">
              <AbcPreview abc={result.abc} scale={scale} label="Partitura dell’esercizio generato" />
            </div>
            <details>
              <summary>Testo ABC</summary>
              <pre className="abc-source">{result.abc}</pre>
            </details>
          </section>

          <section>
            <h2>Controllo rispetto alla spec</h2>
            <p className={`verdict ${result.report.valid ? 'is-ok' : 'is-bad'}`} role="status">
              {!result.report.valid
                ? 'Non valido: almeno un controllo obbligatorio non è superato.'
                : result.report.checks.every((c) => c.passed)
                  ? 'Valido: l’esercizio rispetta tutti i controlli.'
                  : 'Valido: i controlli obbligatori sono superati, ma c’è qualcosa da rivedere.'}
            </p>
            <ul className="check-list">
              {result.report.checks.map((check) => (
                <li key={check.name + check.message} className={check.passed ? 'is-ok' : check.blocking ? 'is-bad' : 'is-note'}>
                  <span className="check-state">{check.passed ? 'Superato' : check.blocking ? 'Non superato' : 'Da rivedere'}</span>
                  <span>{check.message || check.name}</span>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  )
}
