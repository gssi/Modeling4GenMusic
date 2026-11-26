import { Field, NoteSelect, NumberInput, RhythmInput, Select, TextInput } from './fields'
import { REACH_CHOICES, intervalName } from './interval'
import { RHYTHM_HINT } from './rhythm'
import { useModelForm } from './useModelForm'

const AREA_LABELS: Record<string, string> = { RHYTHM: 'Ritmo', PITCH_READING: 'Lettura delle note', TECHNIQUE: 'Tecnica' }
const INSTRUMENT_LABELS: Record<string, string> = { PIANO: 'Pianoforte', GUITAR: 'Chitarra', FLUTE: 'Flauto' }
const HAND_LABELS: Record<string, string> = { LEFT: 'Sinistra', RIGHT: 'Destra' }
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
  const reaches = (learner.contents.needs ?? []).filter((n) => n.eClass === 'HandSpanLimit')
  const freeHand = form.literals('HandSpanLimit', 'hand').find((hand) => !reaches.some((r) => r.attributes.hand === hand))
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
        <h2>Limiti fisici</h2>
        <p className="hint">
          Quanto si apre comodamente la mano sulla tastiera. È un limite stabile, non un obiettivo: l’esercizio non lo supera mai.
        </p>
        {reaches.length > 0 && (
          <table className="form-table">
            <thead>
              <tr>
                <th scope="col">Mano</th>
                <th scope="col">Apertura massima</th>
                <th scope="col">Misura in cm (facoltativa)</th>
                <th scope="col">
                  <span className="visually-hidden">Azioni</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {reaches.map((reach) => {
                const hand = HAND_LABELS[String(reach.attributes.hand)] ?? String(reach.attributes.hand)
                const semitones = Number(reach.attributes.maxStretchSemitones ?? 0)
                const choices = REACH_CHOICES.includes(semitones) ? REACH_CHOICES : [semitones, ...REACH_CHOICES]
                return (
                  <tr key={reach.id}>
                    <td>
                      <Select
                        label={`Mano del limite ${reach.id}`}
                        value={reach.attributes.hand}
                        options={form.literals('HandSpanLimit', 'hand')}
                        labels={HAND_LABELS}
                        onChange={(v) => form.set(reach.id, 'hand', v)}
                      />
                    </td>
                    <td>
                      <select
                        aria-label={`Apertura massima della mano ${hand.toLowerCase()}`}
                        value={semitones}
                        onChange={(e) => form.set(reach.id, 'maxStretchSemitones', Number(e.target.value))}
                      >
                        {choices.map((n) => (
                          <option key={n} value={n}>
                            {intervalName(n)}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <NumberInput
                        label={`Apertura in centimetri della mano ${hand.toLowerCase()}`}
                        value={Number(reach.attributes.handSpanCm ?? 0) > 0 ? reach.attributes.handSpanCm : undefined}
                        onChange={(v) => form.set(reach.id, 'handSpanCm', v ?? 0)}
                      />
                    </td>
                    <td>
                      <button type="button" className="icon" aria-label={`Togli il limite della mano ${hand.toLowerCase()}`} onClick={() => form.remove(reach.id)}>
                        ×
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
        <button
          type="button"
          disabled={!freeHand}
          onClick={() => freeHand && form.add('HandSpanLimit', { id: learner.id, feature: 'needs' }, { hand: freeHand === 'LEFT' && !reaches.length ? 'RIGHT' : freeHand, maxStretchSemitones: 12 })}
        >
          Aggiungi un limite di apertura
        </button>
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
