import { useEffect, useState } from 'react'
import type { ModelObject } from '../api/types'
import { useStudio } from '../app/store'
import { Field, NoteSelect, NumberInput, RhythmInput, Segmented, Select, TextInput } from './fields'
import { RHYTHM_HINT } from './rhythm'
import { useModelForm } from './useModelForm'

const PROFILE = 'http://music/profile'
const CONSTRAINT_LABELS: Record<string, string> = {
  ContainsPattern: 'Deve contenere un pattern',
  ExcludesPattern: 'Non deve contenere un pattern',
  RangeConstraint: 'Estensione delle note',
  RenderConstraint: 'Dimensione del pentagramma',
}
const QUALITY_LABELS: Record<string, string> = {
  RHYTHMIC_COMPLEXITY: 'Complessità ritmica',
  MELODIC_COMPLEXITY: 'Complessità melodica',
  DENSITY: 'Densità',
  LENGTH: 'Lunghezza',
}
const LEVEL_LABELS: Record<string, string> = { LOW: 'Bassa', MEDIUM: 'Media', HIGH: 'Alta' }
const STRENGTH_LABELS: Record<string, string> = { HARD: 'Obbligatorio', SOFT: 'Preferibile' }
const KIND_LABELS: Record<string, string> = { RHYTHM: 'Ritmico', INTERVAL: 'Intervallare', HARMONY: 'Armonico' }

function PatternFields({ form, owner }: { form: ReturnType<typeof useModelForm>; owner: ModelObject }) {
  const pattern = owner.contents.pattern?.[0]
  if (!pattern) {
    return (
      <button type="button" onClick={() => form.add('Pattern', { id: owner.id, feature: 'pattern' }, { kind: 'RHYTHM' })}>
        Indica il pattern
      </button>
    )
  }
  const rhythmic = pattern.attributes.kind === 'RHYTHM'
  return (
    <>
      <Field label="Tipo di pattern">
        {(id) => <Select id={id} value={pattern.attributes.kind} options={form.literals('Pattern', 'kind')} labels={KIND_LABELS} onChange={(v) => form.set(pattern.id, 'kind', v)} />}
      </Field>
      <div className="field is-wide">
        <span className="field-label">Pattern</span>
        {rhythmic ? (
          <RhythmInput label="Espressione del pattern" value={pattern.attributes.expression} onChange={(v) => form.set(pattern.id, 'expression', v)} />
        ) : (
          <TextInput label="Espressione del pattern" value={pattern.attributes.expression} onChange={(v) => form.set(pattern.id, 'expression', v)} />
        )}
      </div>
    </>
  )
}

/** The teaching intent as a form: what the exercise is about, its constraints and its quality goals. */
export function IntentBuilder({ modelId }: { modelId: string }) {
  const form = useModelForm(modelId)
  const models = useStudio((s) => s.models)
  const externals = useStudio((s) => s.externals)
  const loadExternals = useStudio((s) => s.loadExternals)
  const [newConstraint, setNewConstraint] = useState('')
  const spec = form.root

  useEffect(() => {
    void loadExternals(modelId)
  }, [loadExternals, modelId, models])

  if (!spec || spec.eClass !== 'ExerciseSpec') {
    return (
      <div className="form-view">
        <p>Questo esercizio è ancora vuoto.</p>
        <button type="button" className="primary" onClick={() => form.add('ExerciseSpec', null, { meter: '4/4', bars: 8 })}>
          Inizia l’intento
        </button>
      </div>
    )
  }

  const a = spec.attributes
  const learners = models
    .filter((m) => m.metamodel === PROFILE)
    .flatMap((m) => (externals[m.id]?.roots ?? []).filter((r) => r.eClass === 'Learner').map((r) => ({ key: `${m.id}#${r.id}`, model: m.id, id: r.id, label: `${r.attributes.name ?? r.id} (${m.name})` })))
  const learnerRef = spec.references.learner?.[0]
  const learnerKey = learnerRef ? `${learnerRef.model}#${learnerRef.id}` : ''
  const constraints = spec.contents.constraints ?? []
  const goals = spec.contents.qualityGoals ?? []
  const constraintTypes = form.subclasses('Constraint')
  const usedAttributes = new Set(goals.map((g) => g.attributes.attribute))
  const freeAttribute = form.literals('QualityGoal', 'attribute').find((x) => !usedAttributes.has(x))

  const addConstraint = (className: string) => {
    const parent = { id: spec.id, feature: 'constraints' }
    if (className === 'ContainsPattern' || className === 'ExcludesPattern') {
      form.add(className, parent, {}, [{ feature: 'pattern', className: 'Pattern', attributes: { kind: 'RHYTHM' } }])
    } else if (className === 'RangeConstraint') {
      form.add(className, parent, { lowestMidi: 60, highestMidi: 72 })
    } else if (className === 'RenderConstraint') {
      form.add(className, parent, { staffScale: 1 })
    } else {
      form.add(className, parent)
    }
  }

  return (
    <div className="form-view">
      <section>
        <h2>Esercizio</h2>
        <div className="form-grid">
          <Field label="Allievo" hint={learners.length ? undefined : 'Crea prima un profilo: l’intento è pensato per un allievo.'}>
            {(id) => (
              <select
                id={id}
                value={learnerKey}
                onChange={(e) => {
                  const chosen = learners.find((l) => l.key === e.target.value)
                  form.setRef(spec.id, 'learner', chosen ? { model: chosen.model, id: chosen.id } : null)
                }}
              >
                <option value="">Nessuno</option>
                {learnerRef && !learners.some((l) => l.key === learnerKey) && <option value={learnerKey}>{learnerRef.id}</option>}
                {learners.map((l) => (
                  <option key={l.key} value={l.key}>
                    {l.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Tonalità" hint="Come in ABC: G, Dm, Bb…">
            {(id) => <TextInput id={id} value={a.key} onChange={(v) => form.set(spec.id, 'key', v)} />}
          </Field>
          <Field label="Metro">{(id) => <TextInput id={id} value={a.meter} onChange={(v) => form.set(spec.id, 'meter', v)} />}</Field>
          <Field label="Battute">{(id) => <NumberInput id={id} integer value={a.bars} onChange={(v) => form.set(spec.id, 'bars', v ?? 0)} />}</Field>
          <Field label="Tempo richiesto (bpm)">
            {(id) => <NumberInput id={id} integer value={a.requestedBpm} onChange={(v) => form.set(spec.id, 'requestedBpm', v ?? 0)} />}
          </Field>
        </div>
      </section>

      <section>
        <h2>Vincoli</h2>
        <p className="hint">{RHYTHM_HINT}</p>
        {constraints.length === 0 && <p className="hint">Nessun vincolo. Aggiungine uno per dire cosa l’esercizio deve o non deve contenere.</p>}
        {constraints.map((constraint) => (
          <div key={constraint.id} className="form-card">
            <div className="form-row-head">
              <h3>{CONSTRAINT_LABELS[constraint.eClass] ?? constraint.eClass}</h3>
              <button type="button" className="icon" aria-label={`Elimina il vincolo ${constraint.id}`} onClick={() => form.remove(constraint.id)}>
                ×
              </button>
            </div>
            <div className="form-grid">
              {(constraint.eClass === 'ContainsPattern' || constraint.eClass === 'ExcludesPattern') && <PatternFields form={form} owner={constraint} />}
              {constraint.eClass === 'ContainsPattern' && (
                <Field label="Almeno quante volte">
                  {(id) => <NumberInput id={id} integer value={constraint.attributes.minOccurrences} onChange={(v) => form.set(constraint.id, 'minOccurrences', v ?? 1)} />}
                </Field>
              )}
              {constraint.eClass === 'RangeConstraint' && (
                <>
                  <Field label="Nota più grave">{(id) => <NoteSelect id={id} value={constraint.attributes.lowestMidi} onChange={(v) => form.set(constraint.id, 'lowestMidi', v)} />}</Field>
                  <Field label="Nota più acuta">{(id) => <NoteSelect id={id} value={constraint.attributes.highestMidi} onChange={(v) => form.set(constraint.id, 'highestMidi', v)} />}</Field>
                </>
              )}
              {constraint.eClass === 'RenderConstraint' && (
                <Field label="Ingrandimento del pentagramma">
                  {(id) => <NumberInput id={id} value={constraint.attributes.staffScale} onChange={(v) => form.set(constraint.id, 'staffScale', v ?? 1)} />}
                </Field>
              )}
              <div className="field">
                <span className="field-label">Forza</span>
                <Segmented
                  legend={`Forza del vincolo ${constraint.id}`}
                  value={constraint.attributes.strength}
                  options={form.literals(constraint.eClass, 'strength')}
                  labels={STRENGTH_LABELS}
                  onChange={(v) => form.set(constraint.id, 'strength', v)}
                />
              </div>
            </div>
          </div>
        ))}
        <div className="form-add">
          <select aria-label="Tipo di vincolo da aggiungere" value={newConstraint} onChange={(e) => setNewConstraint(e.target.value)}>
            <option value="">Scegli il tipo di vincolo…</option>
            {constraintTypes.map((type) => (
              <option key={type} value={type}>
                {CONSTRAINT_LABELS[type] ?? type}
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={!newConstraint}
            onClick={() => {
              addConstraint(newConstraint)
              setNewConstraint('')
            }}
          >
            Aggiungi vincolo
          </button>
        </div>
      </section>

      <section>
        <h2>Obiettivi di qualità</h2>
        {goals.length === 0 && <p className="hint">Nessun obiettivo. Quelli che non indichi vengono tenuti bassi dalla derivazione.</p>}
        {goals.map((goal) => (
          <div key={goal.id} className="form-goal">
            <Select
              label={`Attributo dell’obiettivo ${goal.id}`}
              value={goal.attributes.attribute}
              options={form.literals('QualityGoal', 'attribute')}
              labels={QUALITY_LABELS}
              onChange={(v) => form.set(goal.id, 'attribute', v)}
            />
            <Segmented
              legend={`Livello di ${QUALITY_LABELS[String(goal.attributes.attribute)] ?? goal.attributes.attribute}`}
              value={goal.attributes.level}
              options={form.literals('QualityGoal', 'level')}
              labels={LEVEL_LABELS}
              onChange={(v) => form.set(goal.id, 'level', v)}
            />
            <button type="button" className="icon" aria-label={`Elimina l’obiettivo ${goal.id}`} onClick={() => form.remove(goal.id)}>
              ×
            </button>
          </div>
        ))}
        <button
          type="button"
          disabled={!freeAttribute}
          onClick={() => freeAttribute && form.add('QualityGoal', { id: spec.id, feature: 'qualityGoals' }, { attribute: freeAttribute, level: 'MEDIUM' })}
        >
          Aggiungi obiettivo di qualità
        </button>
      </section>
    </div>
  )
}
