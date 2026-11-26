import { useEffect } from 'react'
import type { GenerationReport } from '../api/types'
import { useStudio } from '../app/store'
import { allObjects } from '../editor/modelOps'
import { AbcPreview } from './AbcPreview'

function Checks({ report }: { report: GenerationReport }) {
  return (
    <ul className="check-list">
      {report.checks.map((check) => (
        <li key={check.name + check.message} className={check.passed ? 'is-ok' : check.blocking ? 'is-bad' : 'is-note'}>
          <span className="check-state">{check.passed ? 'Superato' : check.blocking ? 'Non superato' : 'Da rivedere'}</span>
          <span>{check.message || check.name}</span>
        </li>
      ))}
    </ul>
  )
}

/** For a derived spec: asks a generator for an exercise, shows it in notation and reports how it meets the spec. */
export function GenerationPanel({ modelId }: { modelId: string }) {
  const model = useStudio((s) => s.open[modelId])
  const result = useStudio((s) => s.generations[modelId])
  const generating = useStudio((s) => s.generating === modelId)
  const generators = useStudio((s) => s.generators)
  const generatorId = useStudio((s) => s.generatorId)
  const { generate, loadGenerators, chooseGenerator } = useStudio.getState()

  useEffect(() => {
    void loadGenerators()
  }, [loadGenerators])

  const render = allObjects(model.doc.roots).find((o) => o.eClass === 'RenderConstraint')
  const scale = Number(render?.attributes.staffScale ?? 1) || 1
  const chosen = generators.find((g) => g.id === generatorId)
  const attempts = result?.attempts ?? []
  const discarded = attempts.filter((a) => a.abc !== result?.abc)
  const meta = result?.metadata ?? {}

  return (
    <div className="form-view">
      <section>
        <h2>Esercizio</h2>
        <fieldset className="generator-choice">
          <legend>Chi scrive l’esercizio</legend>
          {generators.length === 0 && <p className="hint">Non riesco a leggere l’elenco dei generatori dal server.</p>}
          {generators.map((g) => (
            <label key={g.id} className={g.available ? undefined : 'is-unavailable'}>
              <input
                type="radio"
                name="generator"
                value={g.id}
                checked={g.id === generatorId}
                disabled={!g.available}
                onChange={() => chooseGenerator(g.id)}
              />
              <span>
                <strong>{g.label}</strong>
                {g.id === 'mock' && <span className="hint"> Sempre lo stesso brano di prova, qualunque sia la spec.</span>}
                {g.model && <span className="hint"> Modello musicale locale: {g.model}.</span>}
                {!g.available && g.reason && <span className="generator-reason"> Non disponibile. {g.reason}.</span>}
              </span>
            </label>
          ))}
        </fieldset>
        <div className="form-add">
          <button type="button" className="primary" onClick={() => void generate(modelId)} disabled={generating || !chosen?.available}>
            {generating ? 'Generazione in corso…' : result ? 'Genera di nuovo' : 'Genera esercizio'}
          </button>
          <button type="button" onClick={() => void loadGenerators()} disabled={generating}>
            Ricontrolla i generatori
          </button>
        </div>
        {generating && chosen?.model && (
          <p className="hint" role="status">
            Il modello scrive e l’esercizio viene controllato; se non rispetta la spec si riprova. Può volerci qualche decina di secondi.
          </p>
        )}
      </section>

      {result && (
        <>
          <section>
            <h2>Partitura</h2>
            <p className="hint">
              {meta.generator === 'mock' ? 'Brano di prova.' : `Scritto da ${String(meta.model ?? meta.generator)}.`}
              {typeof meta.attempts === 'number' && meta.attempts > 1 && ` Scelto tra ${meta.attempts} tentativi.`}
              {typeof meta.seconds === 'number' && meta.generator !== 'mock' && ` Tempo: ${meta.seconds} secondi.`}
              {scale !== 1 && ` Pentagramma ingrandito ${scale} volte, come richiesto dal vincolo di resa.`}
            </p>
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
                ? attempts.length > 1
                  ? `Non valido: nessuno dei ${attempts.length} tentativi supera tutti i controlli obbligatori. Questo è il migliore.`
                  : 'Non valido: almeno un controllo obbligatorio non è superato.'
                : result.report.checks.every((c) => c.passed)
                  ? 'Valido: l’esercizio rispetta tutti i controlli.'
                  : 'Valido: i controlli obbligatori sono superati, ma c’è qualcosa da rivedere.'}
            </p>
            <Checks report={result.report} />
          </section>

          {discarded.length > 0 && (
            <section>
              <h2>Tentativi scartati</h2>
              {discarded.map((attempt, index) => (
                <details key={index} className="attempt">
                  <summary>
                    Tentativo scartato {index + 1}: {attempt.report.checks.filter((c) => c.blocking && !c.passed).length} controlli obbligatori non superati
                  </summary>
                  <Checks report={attempt.report} />
                  <pre className="abc-source">{attempt.abc}</pre>
                </details>
              ))}
            </section>
          )}
        </>
      )}
    </div>
  )
}
