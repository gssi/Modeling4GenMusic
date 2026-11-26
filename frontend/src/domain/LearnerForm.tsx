import { Field, NoteSelect, NumberInput, RhythmInput, Select, TextInput } from './fields'
import { RHYTHM_HINT } from './rhythm'
import { useModelForm } from './useModelForm'

const AREA_LABELS: Record<string, string> = { RHYTHM: 'Ritmo', PITCH_READING: 'Lettura delle note', TECHNIQUE: 'Tecnica' }
const INSTRUMENT_LABELS: Record<string, string> = { PIANO: 'Pianoforte', GUITAR: 'Chitarra', FLUTE: 'Flauto' }
const STATUS_LABELS: Record<string, string> = { MASTERED: 'Acquisito', LEARNING: 'In apprendimento', NOT_INTRODUCED: 'Non ancora introdotto' }

/** The learner profile as a form: instrument, tempo, range, skills, needs and rhythmic patterns. */
export function LearnerForm({ modelId }: { modelId: string }) {
  const form = useModelForm(modelId)
  const learner = form.root

  if (!learner || learner.eClass !== 'Learner') {
    return (
      <div className="form-view">
        <p>Questo modello non contiene ancora un allievo.</p>
        <button type="button" className="primary" onClick={() => form.add('Learner', null)}>
          Crea il profilo
        </button>
      </div>
    )
  }

  const skills = learner.contents.skills ?? []
  const lowVision = (learner.contents.needs ?? []).find((n) => n.eClass === 'LowVision')
  const patterns = learner.contents.patterns ?? []
  const a = learner.attributes

  return (
    <div className="form-view">
      <section>
        <h2>Allievo</h2>
        <div className="form-grid">
          <Field label="Nome">{(id) => <TextInput id={id} value={a.name} onChange={(v) => form.set(learner.id, 'name', v)} />}</Field>
          <Field label="Strumento">
            {(id) => (
              <Select id={id} value={a.instrument} options={form.literals('Learner', 'instrument')} labels={INSTRUMENT_LABELS} onChange={(v) => form.set(learner.id, 'instrument', v)} />
            )}
          </Field>
          <Field label="Tempo comodo (bpm)">
            {(id) => <NumberInput id={id} integer value={a.comfortableBpm} onChange={(v) => form.set(learner.id, 'comfortableBpm', v ?? 0)} />}
          </Field>
          <Field label="Nota più grave">{(id) => <NoteSelect id={id} value={a.lowestMidi} onChange={(v) => form.set(learner.id, 'lowestMidi', v)} />}</Field>
          <Field label="Nota più acuta">{(id) => <NoteSelect id={id} value={a.highestMidi} onChange={(v) => form.set(learner.id, 'highestMidi', v)} />}</Field>
        </div>
      </section>

      <section>
        <h2>Abilità</h2>
        <p className="hint">Livello da 0 a 5 e quanto sei sicuro della stima, da 0 a 1.</p>
        <table className="form-table">
          <thead>
            <tr>
              <th scope="col">Area</th>
              <th scope="col">Livello</th>
              <th scope="col">Confidenza</th>
              <th scope="col">
                <span className="visually-hidden">Azioni</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {form.literals('Skill', 'area').map((area) => {
              const skill = skills.find((s) => s.attributes.area === area)
              const name = AREA_LABELS[area] ?? area
              if (!skill) {
                return (
                  <tr key={area}>
                    <th scope="row">{name}</th>
                    <td colSpan={2} className="hint">
                      Non valutata.
                    </td>
                    <td>
                      <button type="button" onClick={() => form.add('Skill', { id: learner.id, feature: 'skills' }, { area, level: 0, confidence: 0.5 })}>
                        Valuta {name.toLowerCase()}
                      </button>
                    </td>
                  </tr>
                )
              }
              const level = Number(skill.attributes.level ?? 0)
              const confidence = Number(skill.attributes.confidence ?? 0)
              return (
                <tr key={area}>
                  <th scope="row">{name}</th>
                  <td>
                    <span className="slider">
                      <input type="range" min={0} max={5} step={1} value={level} aria-label={`Livello di ${name}`} onChange={(e) => form.set(skill.id, 'level', Number(e.target.value))} />
                      <output>{level}</output>
                    </span>
                  </td>
                  <td>
                    <span className="slider">
                      <input
                        type="range"
                        min={0}
                        max={1}
                        step={0.05}
                        value={confidence}
                        aria-label={`Confidenza su ${name}`}
                        onChange={(e) => form.set(skill.id, 'confidence', Math.round(Number(e.target.value) * 100) / 100)}
                      />
                      <output>{confidence}</output>
                    </span>
                  </td>
                  <td>
                    <button type="button" className="icon" aria-label={`Togli la valutazione di ${name}`} onClick={() => form.remove(skill.id)}>
                      ×
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </section>

      <section>
        <h2>Bisogni</h2>
        <div className="field is-inline">
          <input
            id="need-low-vision"
            type="checkbox"
            checked={!!lowVision}
            onChange={(e) => (e.target.checked ? form.add('LowVision', { id: learner.id, feature: 'needs' }, { minStaffScale: 1.5 }) : lowVision && form.remove(lowVision.id))}
          />
          <label htmlFor="need-low-vision">Ipovisione: serve un pentagramma ingrandito</label>
        </div>
        {lowVision && (
          <Field label="Ingrandimento minimo del pentagramma" hint="1 è la dimensione normale, 1.5 è una volta e mezza.">
            {(id) => <NumberInput id={id} value={lowVision.attributes.minStaffScale} onChange={(v) => form.set(lowVision.id, 'minStaffScale', v ?? 1)} />}
          </Field>
        )}
      </section>

      <section>
        <h2>Pattern ritmici</h2>
        <p className="hint">{RHYTHM_HINT}</p>
        {patterns.length === 0 ? (
          <p className="hint">Nessun pattern. Aggiungi quelli che l’allievo conosce, sta imparando o non ha ancora incontrato.</p>
        ) : (
          <table className="form-table">
            <thead>
              <tr>
                <th scope="col">Pattern e notazione</th>
                <th scope="col">Stato</th>
                <th scope="col">
                  <span className="visually-hidden">Azioni</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {patterns.map((pattern, index) => (
                <tr key={pattern.id}>
                  <td>
                    <RhythmInput label={`Pattern ${index + 1}`} value={pattern.attributes.expression} onChange={(v) => form.set(pattern.id, 'expression', v)} />
                  </td>
                  <td>
                    <Select
                      label={`Stato del pattern ${index + 1}`}
                      value={pattern.attributes.status}
                      options={form.literals('PatternMastery', 'status')}
                      labels={STATUS_LABELS}
                      onChange={(v) => form.set(pattern.id, 'status', v)}
                    />
                  </td>
                  <td>
                    <button type="button" className="icon" aria-label={`Elimina il pattern ${index + 1}`} onClick={() => form.remove(pattern.id)}>
                      ×
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <button type="button" onClick={() => form.add('PatternMastery', { id: learner.id, feature: 'patterns' })}>
          Aggiungi pattern
        </button>
      </section>
    </div>
  )
}
