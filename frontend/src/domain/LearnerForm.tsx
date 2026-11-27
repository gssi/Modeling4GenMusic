import { useEffect, useState } from 'react'
import type { ModelObject } from '../api/types'
import { useStudio } from '../app/store'
import { Field, NoteSelect, NumberInput, RhythmInput, Select, TextInput } from './fields'
import { REACH_CHOICES, intervalName } from './interval'
import { RHYTHM_HINT } from './rhythm'
import { NEEDS, type NeedCatalog, type NeedParameter, type NeedType, boundsHint, newNeed, newSlot, readCatalog, slotOf, typeOf } from './needs'
import { useModelForm } from './useModelForm'

type Form = ReturnType<typeof useModelForm>

const AREA_LABELS: Record<string, string> = { RHYTHM: 'Ritmo', PITCH_READING: 'Lettura delle note', TECHNIQUE: 'Tecnica' }
const INSTRUMENT_LABELS: Record<string, string> = { PIANO: 'Pianoforte', GUITAR: 'Chitarra', FLUTE: 'Flauto' }
const HAND_LABELS: Record<string, string> = { LEFT: 'Sinistra', RIGHT: 'Destra' }
const STATUS_LABELS: Record<string, string> = { MASTERED: 'Acquisito', LEARNING: 'In apprendimento', NOT_INTRODUCED: 'Non ancora introdotto' }

/** One parameter of a need, edited with the control its kind calls for. The model keeps the value as text. */
function ParameterField({ form, need, catalog, type, parameter }: { form: Form; need: ModelObject; catalog: NeedCatalog; type: NeedType; parameter: NeedParameter }) {
  const slot = slotOf(need, parameter)
  const stored = slot ? slot.attributes.value : parameter.defaultValue
  const value = stored === undefined || stored === '' ? undefined : String(stored)
  const numeric = value !== undefined && Number.isFinite(Number(value)) ? Number(value) : undefined
  const name = `${type.label}: ${parameter.label.toLowerCase()}`
  const write = (next: string | undefined) =>
    slot ? form.set(slot.id, 'value', next) : next !== undefined && form.insert({ id: need.id, feature: 'slots' }, newSlot(need.id, catalog, parameter, next))
  const fallback = slot && value === undefined && parameter.defaultValue !== undefined ? `Vuoto: vale ${parameter.defaultValue}.` : undefined
  const hint = [boundsHint(parameter), parameter.required ? undefined : 'Facoltativo.', fallback].filter(Boolean).join(' ') || undefined

  if (parameter.kind === 'RHYTHM') {
    return (
      <div className="field">
        <span className="field-label">{parameter.label}</span>
        <RhythmInput label={name} value={value} onChange={write} />
      </div>
    )
  }
  return (
    <Field label={parameter.label} hint={parameter.kind === 'INTEGER' || parameter.kind === 'DECIMAL' ? hint : undefined}>
      {(id) => {
        switch (parameter.kind) {
          case 'HAND':
            return <Select id={id} label={name} value={value} options={['LEFT', 'RIGHT']} labels={HAND_LABELS} onChange={write} />
          case 'NOTE':
            return <NoteSelect id={id} label={name} value={numeric} onChange={(midi) => write(String(midi))} />
          case 'INTERVAL': {
            const choices = REACH_CHOICES.filter((n) => (parameter.min === undefined || n >= parameter.min) && (parameter.max === undefined || n <= parameter.max))
            return (
              <select id={id} aria-label={name} value={numeric ?? ''} onChange={(e) => write(e.target.value)}>
                {numeric === undefined && <option value="">Scegli…</option>}
                {(numeric === undefined || choices.includes(numeric) ? choices : [numeric, ...choices]).map((n) => (
                  <option key={n} value={n}>
                    {intervalName(n)}
                  </option>
                ))}
              </select>
            )
          }
          default:
            return <NumberInput id={id} label={name} integer={parameter.kind === 'INTEGER'} value={numeric} onChange={(v) => write(v === undefined ? undefined : String(v))} />
        }
      }}
    </Field>
  )
}

/**
 * The needs of the learner. Nothing here names a kind of need: the types that can be added, their
 * wording and their parameters are read from the catalog the profile points to.
 */
function NeedsSection({ form, learner, modelId }: { form: Form; learner: ModelObject; modelId: string }) {
  const models = useStudio((s) => s.models)
  const externals = useStudio((s) => s.externals)
  const loadExternals = useStudio((s) => s.loadExternals)
  const [newType, setNewType] = useState('')

  useEffect(() => {
    void loadExternals(modelId)
  }, [loadExternals, modelId, models])

  const catalogs = models.filter((m) => m.metamodel === NEEDS)
  const catalogRef = learner.references.catalog?.[0]
  const catalogDoc = catalogRef?.model ? externals[catalogRef.model] : undefined
  const catalog = catalogDoc ? readCatalog(catalogDoc) : null
  const needs = learner.contents.needs ?? []
  const chosen = catalog?.types.find((t) => t.id === newType)

  const chooseCatalog = (model: string) => {
    const root = externals[model]?.roots.find((r) => r.eClass === 'NeedCatalog')
    form.setRef(learner.id, 'catalog', root ? { model, id: root.id } : null)
  }

  return (
    <section>
      <h2>Bisogni</h2>
      <p className="hint">Limiti stabili dell’allievo, non obiettivi: l’esercizio li rispetta sempre. I tipi disponibili vengono dal catalogo scelto.</p>
      <Field label="Catalogo dei bisogni" hint={catalogs.length ? undefined : 'Nessun catalogo nel workspace: importane o creane uno per descrivere i bisogni.'}>
        {(id) => (
          <select id={id} value={catalogRef?.model ?? ''} onChange={(e) => chooseCatalog(e.target.value)}>
            <option value="">Nessuno</option>
            {catalogRef?.model && !catalogs.some((c) => c.id === catalogRef.model) && <option value={catalogRef.model}>{catalogRef.model}</option>}
            {catalogs.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        )}
      </Field>
      {catalog && needs.length === 0 && <p className="hint">Nessun bisogno indicato.</p>}
      {needs.map((need) => {
        const type = typeOf(need, catalog)
        const title = type?.label ?? need.references.type?.[0]?.id ?? need.id
        return (
          <div key={need.id} className="form-card">
            <div className="form-row-head">
              <h3>{title}</h3>
              <button type="button" className="icon" aria-label={`Togli il bisogno ${title}`} onClick={() => form.remove(need.id)}>
                ×
              </button>
            </div>
            {type?.description && <p className="hint">{type.description}</p>}
            {type && catalog ? (
              type.parameters.length > 0 && (
                <div className="form-grid">
                  {type.parameters.map((parameter) => (
                    <ParameterField key={parameter.id} form={form} need={need} catalog={catalog} type={type} parameter={parameter} />
                  ))}
                </div>
              )
            ) : (
              <p className="field-error">Il tipo di questo bisogno non è nel catalogo scelto: la derivazione lo ignora.</p>
            )}
          </div>
        )
      })}
      {catalog && (
        <div className="form-add">
          <select aria-label="Tipo di bisogno da aggiungere" value={newType} onChange={(e) => setNewType(e.target.value)}>
            <option value="">Scegli il tipo di bisogno…</option>
            {catalog.types.map((type) => (
              <option key={type.id} value={type.id}>
                {type.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={!chosen}
            onClick={() => {
              if (!chosen) return
              form.insert({ id: learner.id, feature: 'needs' }, newNeed(form.newId('Need'), catalog, chosen))
              setNewType('')
            }}
          >
            Aggiungi bisogno
          </button>
        </div>
      )}
    </section>
  )
}

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

      <NeedsSection form={form} learner={learner} modelId={modelId} />

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
