import { useEffect, useState } from 'react'
import type { ModelObject } from '../api/types'
import { useStudio } from '../app/store'
import { Field, NoteSelect, NumberInput, RhythmInput, Segmented, Select, TextInput } from './fields'
import { QUALITY, dimensionOf, freeDimensions, newGoal, readQualityModel } from './qualityModel'
import { INTERVAL_HINT, intervalName, maxLeap, parseIntervals } from './interval'
import { RHYTHM_HINT } from './rhythm'
import { useModelForm } from './useModelForm'

const PROFILE = 'http://music/profile'
const CONSTRAINT_LABELS: Record<string, string> = {
  ContainsPattern: 'Deve contenere un pattern',
  ExcludesPattern: 'Non deve contenere un pattern',
  RangeConstraint: 'Estensione delle note',
  RenderConstraint: 'Dimensione del pentagramma',
  SpanConstraint: 'Apertura della mano',
}
const LEVEL_LABELS: Record<string, string> = { LOW: 'Bassa', MEDIUM: 'Media', HIGH: 'Alta' }
const STRENGTH_LABELS: Record<string, string> = { HARD: 'Obbligatorio', SOFT: 'Preferibile' }
const HAND_LABELS: Record<string, string> = { LEFT: 'Sinistra', RIGHT: 'Destra' }
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
  const intervals = parseIntervals(String(pattern.attributes.expression ?? ''))
  return (
    <>
      <Field label="Tipo di pattern">
        {(id) => <Select id={id} value={pattern.attributes.kind} options={form.literals('Pattern', 'kind')} labels={KIND_LABELS} onChange={(v) => form.set(pattern.id, 'kind', v)} />}
      </Field>
      <div className="field is-wide">
        <span className="field-label">Pattern</span>
        {rhythmic ? (
          <RhythmInput label="Espressione del pattern" value={pattern.attributes.expression} onChange={(v) => form.set(pattern.id, 'expression', v)} />
        ) : pattern.attributes.kind === 'INTERVAL' ? (
          <>
            <TextInput label="Espressione del pattern" value={pattern.attributes.expression} onChange={(v) => form.set(pattern.id, 'expression', v)} />
            <p className={intervals.ok ? 'hint' : 'field-error'}>
              {intervals.ok
                ? `${INTERVAL_HINT} Salto più ampio: ${intervalName(maxLeap(intervals.steps))}.`
                : `Pattern non valido: ${intervals.error}.`}
            </p>
          </>
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
  const qualityModels = models.filter((m) => m.metamodel === QUALITY)
  const qualityRef = spec.references.qualityModel?.[0]
  const qualityDoc = qualityRef?.model ? externals[qualityRef.model] : undefined
  const quality = qualityDoc ? readQualityModel(qualityDoc) : null
  const free = freeDimensions(goals, quality)
  const chooseQuality = (model: string) => {
    const root = externals[model]?.roots.find((r) => r.eClass === 'QualityModel')
    form.setRef(spec.id, 'qualityModel', root ? { model, id: root.id } : null)
  }

  const addConstraint = (className: string) => {
    const parent = { id: spec.id, feature: 'constraints' }
    if (className === 'ContainsPattern' || className === 'ExcludesPattern') {
      form.add(className, parent, {}, [{ feature: 'pattern', className: 'Pattern', attributes: { kind: 'RHYTHM' } }])
    } else if (className === 'RangeConstraint') {
      form.add(className, parent, { lowestMidi: 60, highestMidi: 72 })
    } else if (className === 'SpanConstraint') {
      form.add(className, parent, { hand: 'RIGHT', maxSimultaneousSemitones: 12, maxStretchInPosition: 12 })
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
              {constraint.eClass === 'SpanConstraint' && (
                <>
                  <Field label="Mano">
                    {(id) => <Select id={id} value={constraint.attributes.hand} options={form.literals('SpanConstraint', 'hand')} labels={HAND_LABELS} onChange={(v) => form.set(constraint.id, 'hand', v)} />}
                  </Field>
                  <Field label="Intervallo massimo tra note insieme (semitoni)">
                    {(id) => <NumberInput id={id} integer value={constraint.attributes.maxSimultaneousSemitones} onChange={(v) => form.set(constraint.id, 'maxSimultaneousSemitones', v ?? 0)} />}
                  </Field>
                  <Field label="Salto massimo senza spostare la mano (semitoni)">
                    {(id) => <NumberInput id={id} integer value={constraint.attributes.maxStretchInPosition} onChange={(v) => form.set(constraint.id, 'maxStretchInPosition', v ?? 0)} />}
                  </Field>
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
        <p className="hint">Quanto deve essere impegnativo l’esercizio. Le dimensioni disponibili sono quelle del modello di qualità scelto.</p>
        <Field label="Modello di qualità" hint={qualityModels.length ? undefined : 'Nessun modello di qualità nel workspace: creane o importane uno per fissare obiettivi.'}>
          {(id) => (
            <select id={id} value={qualityRef?.model ?? ''} onChange={(e) => chooseQuality(e.target.value)}>
              <option value="">Nessuno</option>
              {qualityRef?.model && !qualityModels.some((m) => m.id === qualityRef.model) && <option value={qualityRef.model}>{qualityRef.model}</option>}
              {qualityModels.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          )}
        </Field>
        {quality && goals.length === 0 && <p className="hint">Nessun obiettivo. Per le dimensioni che non indichi vale ciò che dice il modello di qualità.</p>}
        {goals.map((goal) => {
          const dimension = dimensionOf(goal, quality)
          const title = dimension?.label ?? goal.references.dimension?.[0]?.id ?? goal.id
          const choices = dimension ? [dimension, ...free] : free
          return (
            <div key={goal.id} className="form-goal">
              <select
                aria-label={`Dimensione dell’obiettivo ${goal.id}`}
                value={dimension?.id ?? ''}
                onChange={(e) => quality && form.setRef(goal.id, 'dimension', { model: quality.model, id: e.target.value })}
              >
                {!dimension && <option value="">{title} (non nel modello di qualità)</option>}
                {choices.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.label}
                  </option>
                ))}
              </select>
              <Segmented legend={`Livello di ${title}`} value={goal.attributes.level ?? 'LOW'} options={form.literals('QualityGoal', 'level')} labels={LEVEL_LABELS} onChange={(v) => form.set(goal.id, 'level', v)} />
              <button type="button" className="icon" aria-label={`Elimina l’obiettivo ${title}`} onClick={() => form.remove(goal.id)}>
                ×
              </button>
              {dimension?.description && <p className="hint form-goal-note">{dimension.description}</p>}
            </div>
          )
        })}
        <button
          type="button"
          disabled={!quality || free.length === 0}
          onClick={() => quality && free[0] && form.insert({ id: spec.id, feature: 'qualityGoals' }, newGoal(form.newId('QualityGoal'), quality, free[0], 'MEDIUM'))}
        >
          Aggiungi obiettivo di qualità
        </button>
      </section>
    </div>
  )
}
