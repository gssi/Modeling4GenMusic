import { Field, NumberInput, Select, TextInput } from './fields'
import { bandIssues } from './quality'
import { useModelForm } from './useModelForm'

const INSTRUMENT_LABELS: Record<string, string> = { PIANO: 'Pianoforte', GUITAR: 'Chitarra', FLUTE: 'Flauto' }
const LEVEL_LABELS: Record<string, string> = { LOW: 'Basso', MEDIUM: 'Medio', HIGH: 'Alto' }

/** The quality model as a table: for each attribute, the metric and the range of each level. */
export function QualityTable({ modelId }: { modelId: string }) {
  const form = useModelForm(modelId)
  const model = form.root

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

  const bindings = model.contents.bindings ?? []
  const levels = form.literals('Band', 'level')

  return (
    <div className="form-view">
      <section>
        <h2>Modello di qualità</h2>
        <div className="form-grid">
          <Field label="Strumento">
            {(id) => (
              <Select id={id} value={model.attributes.instrument} options={form.literals('QualityModel', 'instrument')} labels={INSTRUMENT_LABELS} onChange={(v) => form.set(model.id, 'instrument', v)} />
            )}
          </Field>
        </div>
      </section>

      {bindings.map((binding) => {
        const bands = binding.contents.bands ?? []
        const issues = bandIssues(bands.map((b) => ({ level: LEVEL_LABELS[String(b.attributes.level)] ?? String(b.attributes.level), min: Number(b.attributes.min ?? 0), max: Number(b.attributes.max ?? 0) })))
        const title = String(binding.attributes.attribute ?? 'Attributo')
        return (
          <section key={binding.id}>
            <div className="form-row-head">
              <h2>{title}</h2>
              <button type="button" onClick={() => form.remove(binding.id)}>
                Elimina {title}
              </button>
            </div>
            <div className="form-grid">
              <Field label="Attributo di qualità">
                {(id) => <Select id={id} value={binding.attributes.attribute} options={form.literals('MetricBinding', 'attribute')} onChange={(v) => form.set(binding.id, 'attribute', v)} />}
              </Field>
              <Field label="Metrica">{(id) => <TextInput id={id} value={binding.attributes.metric} onChange={(v) => form.set(binding.id, 'metric', v)} />}</Field>
            </div>
            <table className="form-table">
              <thead>
                <tr>
                  <th scope="col">Livello</th>
                  <th scope="col">Da</th>
                  <th scope="col">A</th>
                </tr>
              </thead>
              <tbody>
                {levels.map((level) => {
                  const band = bands.find((b) => b.attributes.level === level)
                  const name = LEVEL_LABELS[level] ?? level
                  return (
                    <tr key={level}>
                      <th scope="row">{name}</th>
                      {band ? (
                        <>
                          <td>
                            <NumberInput label={`${title}, ${name}: da`} value={band.attributes.min} onChange={(v) => form.set(band.id, 'min', v ?? 0)} />
                          </td>
                          <td>
                            <NumberInput label={`${title}, ${name}: a`} value={band.attributes.max} onChange={(v) => form.set(band.id, 'max', v ?? 0)} />
                          </td>
                        </>
                      ) : (
                        <td colSpan={2}>
                          <button type="button" onClick={() => form.add('Band', { id: binding.id, feature: 'bands' }, { level, min: 0, max: 1 })}>
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
            'MetricBinding',
            { id: model.id, feature: 'bindings' },
            {},
            levels.map((level, i) => ({
              feature: 'bands',
              className: 'Band',
              attributes: { level, min: Math.round((i / levels.length) * 100) / 100, max: Math.round(((i + 1) / levels.length) * 100) / 100 },
            })),
          )
        }
      >
        Aggiungi attributo di qualità
      </button>
    </div>
  )
}
