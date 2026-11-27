import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { MetricInfo } from '../api/types'
import { Field, NumberInput, Select, TextInput } from './fields'
import { bandIssues } from './quality'
import { newDimensionName } from './qualityModel'
import { useModelForm } from './useModelForm'

const INSTRUMENT_LABELS: Record<string, string> = { PIANO: 'Pianoforte', GUITAR: 'Chitarra', FLUTE: 'Flauto' }
const LEVEL_LABELS: Record<string, string> = { LOW: 'Basso', MEDIUM: 'Medio', HIGH: 'Alto' }
const AREA_LABELS: Record<string, string> = { RHYTHM: 'Ritmo', PITCH_READING: 'Lettura delle note', TECHNIQUE: 'Tecnica' }

/**
 * The quality model as a form: its dimensions, each with the metric that measures it and what
 * every level means. Dimensions are added here; the metrics are the ones the server can compute.
 */
export function QualityTable({ modelId }: { modelId: string }) {
  const form = useModelForm(modelId)
  const model = form.root
  const [metrics, setMetrics] = useState<MetricInfo[]>([])

  useEffect(() => {
    api.listMetrics().then(setMetrics, () => setMetrics([]))
  }, [])

  if (!model || model.eClass !== 'QualityModel') {
    return (
      <div className="form-view">
        <p>Questo modello di qualità è vuoto.</p>
        <button type="button" className="primary" onClick={() => form.add('QualityModel', null)}>
          Crea il modello di qualità
        </button>
      </div>
    )
  }

  const dimensions = model.contents.dimensions ?? []
  const levels = form.literals('Band', 'level')
  const areas = form.literals('QualityDimension', 'skillArea')

  return (
    <div className="form-view">
      <section>
        <h2>Modello di qualità</h2>
        <p className="hint">
          Le dimensioni lungo cui un esercizio può essere chiesto semplice o impegnativo. Ogni intento che usa questo modello può fissare un livello per
          ciascuna.
        </p>
        <div className="form-grid">
          <Field label="Strumento">
            {(id) => (
              <Select id={id} value={model.attributes.instrument} options={form.literals('QualityModel', 'instrument')} labels={INSTRUMENT_LABELS} onChange={(v) => form.set(model.id, 'instrument', v)} />
            )}
          </Field>
        </div>
      </section>

      {dimensions.length === 0 && <p className="hint">Nessuna dimensione. Aggiungine una per poter fissare obiettivi di qualità negli intenti.</p>}
      {dimensions.map((dimension) => {
        const a = dimension.attributes
        const bands = dimension.contents.bands ?? []
        const issues = bandIssues(bands.map((b) => ({ level: LEVEL_LABELS[String(b.attributes.level)] ?? String(b.attributes.level), min: Number(b.attributes.min ?? 0), max: Number(b.attributes.max ?? 0) })))
        const title = String(a.label ?? a.name ?? dimension.id)
        const metric = String(a.metric ?? '')
        const known = metrics.find((m) => m.name === metric)
        const names = metrics.some((m) => m.name === metric) || !metric ? metrics.map((m) => m.name) : [metric, ...metrics.map((m) => m.name)]
        return (
          <section key={dimension.id}>
            <div className="form-row-head">
              <h2>{title}</h2>
              <button type="button" onClick={() => form.remove(dimension.id)}>
                Elimina {title}
              </button>
            </div>
            <div className="form-grid">
              <Field label="Nome" hint="Senza spazi: è il nome con cui la dimensione compare nei modelli.">
                {(id) => <TextInput id={id} label={`Nome di ${title}`} value={a.name} onChange={(v) => form.set(dimension.id, 'name', v)} />}
              </Field>
              <Field label="Etichetta">{(id) => <TextInput id={id} label={`Etichetta di ${title}`} value={a.label} onChange={(v) => form.set(dimension.id, 'label', v)} />}</Field>
              <Field label="Metrica" hint={known?.description ?? (metric ? 'Questa metrica non è tra quelle che lo studio sa calcolare.' : undefined)}>
                {(id) => (
                  <select id={id} aria-label={`Metrica di ${title}`} value={metric} onChange={(e) => form.set(dimension.id, 'metric', e.target.value)}>
                    {!metric && <option value="">Scegli…</option>}
                    {names.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                )}
              </Field>
              <div className="field is-wide">
                <label htmlFor={`${dimension.id}-description`}>Descrizione</label>
                <input
                  id={`${dimension.id}-description`}
                  type="text"
                  value={a.description === undefined ? '' : String(a.description)}
                  onChange={(e) => form.set(dimension.id, 'description', e.target.value === '' ? undefined : e.target.value)}
                />
              </div>
            </div>
            <div className="form-check">
              <input id={`${dimension.id}-implicit`} type="checkbox" checked={a.implicit === true} onChange={(e) => form.set(dimension.id, 'implicit', e.target.checked)} />
              <label htmlFor={`${dimension.id}-implicit`}>Vale anche quando l’intento non la cita, al livello</label>
              <Select label={`Livello predefinito di ${title}`} value={a.defaultLevel ?? levels[0]} options={levels} labels={LEVEL_LABELS} onChange={(v) => form.set(dimension.id, 'defaultLevel', v)} />
            </div>
            <div className="form-check">
              <input id={`${dimension.id}-skill`} type="checkbox" checked={a.skillLimited === true} onChange={(e) => form.set(dimension.id, 'skillLimited', e.target.checked)} />
              <label htmlFor={`${dimension.id}-skill`}>Avvisa se è chiesta alta a un allievo debole in</label>
              <Select label={`Abilità collegata a ${title}`} value={a.skillArea ?? areas[0]} options={areas} labels={AREA_LABELS} onChange={(v) => form.set(dimension.id, 'skillArea', v)} />
            </div>
            <table className="form-table">
              <thead>
                <tr>
                  <th scope="col">Livello</th>
                  <th scope="col">Da</th>
                  <th scope="col">A</th>
                  <th scope="col">Frase per il generatore (in inglese)</th>
                </tr>
              </thead>
              <tbody>
                {levels.map((level) => {
                  const band = bands.find((b) => (b.attributes.level ?? levels[0]) === level)
                  const name = LEVEL_LABELS[level] ?? level
                  return (
                    <tr key={level}>
                      <th scope="row">{name}</th>
                      {band ? (
                        <>
                          <td>
                            <NumberInput label={`${title}, ${name}: da`} value={band.attributes.min ?? 0} onChange={(v) => form.set(band.id, 'min', v ?? 0)} />
                          </td>
                          <td>
                            <NumberInput label={`${title}, ${name}: a`} value={band.attributes.max ?? 0} onChange={(v) => form.set(band.id, 'max', v ?? 0)} />
                          </td>
                          <td className="is-wide">
                            <TextInput label={`${title}, ${name}: frase per il generatore`} value={band.attributes.hint} onChange={(v) => form.set(band.id, 'hint', v)} />
                          </td>
                        </>
                      ) : (
                        <td colSpan={3}>
                          <button type="button" onClick={() => form.add('Band', { id: dimension.id, feature: 'bands' }, { level, min: 0, max: 1 })}>
                            Aggiungi la banda {name.toLowerCase()}
                          </button>
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {issues.length > 0 && (
              <ul className="form-warnings" role="status">
                {issues.map((issue) => (
                  <li key={issue}>{issue}</li>
                ))}
              </ul>
            )}
          </section>
        )
      })}

      <button
        type="button"
        onClick={() =>
          form.add(
            'QualityDimension',
            { id: model.id, feature: 'dimensions' },
            { name: newDimensionName(dimensions.map((d) => String(d.attributes.name ?? ''))), metric: metrics[0]?.name ?? '' },
            levels.map((level, i) => ({
              feature: 'bands',
              className: 'Band',
              attributes: { level, min: Math.round((i / levels.length) * 100) / 100, max: Math.round(((i + 1) / levels.length) * 100) / 100 },
            })),
          )
        }
      >
        Aggiungi dimensione di qualità
      </button>
    </div>
  )
}
