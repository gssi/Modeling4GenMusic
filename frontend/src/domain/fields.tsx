import { type ReactNode, useEffect, useId, useState } from 'react'
import type { AttributeValue } from '../api/types'
import { AbcPreview } from './AbcPreview'
import { MIDI_CHOICES, midiName } from './notes'
import { parseRhythm, rhythmToAbc } from './rhythm'

/** Form controls shared by the forms of the domain views. */

export function Field({ label, children, hint }: { label: string; children: (id: string) => ReactNode; hint?: string }) {
  const id = useId()
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children(id)}
      {hint && <p className="hint">{hint}</p>}
    </div>
  )
}

export function TextInput(props: { id?: string; value: AttributeValue | undefined; onChange: (value: string | undefined) => void; label?: string }) {
  return (
    <input
      id={props.id}
      type="text"
      aria-label={props.label}
      value={props.value === undefined ? '' : String(props.value)}
      onChange={(e) => props.onChange(e.target.value === '' ? undefined : e.target.value)}
    />
  )
}

/** Number typed as text: commits when the text is a number, keeps showing what was typed otherwise. */
export function NumberInput(props: {
  id?: string
  value: AttributeValue | undefined
  integer?: boolean
  label?: string
  onChange: (value: number | undefined) => void
}) {
  const { value, integer, onChange } = props
  const [text, setText] = useState(value === undefined ? '' : String(value))
  const [invalid, setInvalid] = useState(false)
  useEffect(() => {
    setText((current) => (Number(current.replace(',', '.')) === value ? current : value === undefined ? '' : String(value)))
    setInvalid(false)
  }, [value])
  return (
    <input
      id={props.id}
      type="text"
      inputMode={integer ? 'numeric' : 'decimal'}
      className="is-number"
      aria-label={props.label}
      aria-invalid={invalid}
      value={text}
      onChange={(e) => {
        const raw = e.target.value
        setText(raw)
        if (raw.trim() === '') {
          setInvalid(false)
          onChange(undefined)
          return
        }
        const parsed = Number(raw.replace(',', '.'))
        const ok = Number.isFinite(parsed) && (!integer || Number.isInteger(parsed))
        setInvalid(!ok)
        if (ok) onChange(parsed)
      }}
    />
  )
}

export function Select(props: {
  id?: string
  value: AttributeValue | undefined
  options: readonly string[]
  labels?: Record<string, string>
  label?: string
  onChange: (value: string) => void
}) {
  return (
    <select id={props.id} aria-label={props.label} value={String(props.value ?? '')} onChange={(e) => props.onChange(e.target.value)}>
      {props.options.map((option) => (
        <option key={option} value={option}>
          {props.labels?.[option] ?? option}
        </option>
      ))}
    </select>
  )
}

export function NoteSelect(props: { id?: string; value: AttributeValue | undefined; label?: string; onChange: (midi: number) => void }) {
  const current = typeof props.value === 'number' ? props.value : undefined
  const choices = current !== undefined && !MIDI_CHOICES.includes(current) ? [current, ...MIDI_CHOICES] : MIDI_CHOICES
  return (
    <select id={props.id} aria-label={props.label} value={current ?? ''} onChange={(e) => props.onChange(Number(e.target.value))}>
      {current === undefined && <option value="">Scegli…</option>}
      {choices.map((midi) => (
        <option key={midi} value={midi}>
          {midiName(midi)} (MIDI {midi})
        </option>
      ))}
    </select>
  )
}

/** Rhythm expression with live validation and its notation next to it. */
export function RhythmInput(props: { value: AttributeValue | undefined; label: string; onChange: (value: string | undefined) => void }) {
  const id = useId()
  const text = props.value === undefined ? '' : String(props.value)
  const parsed = parseRhythm(text)
  return (
    <div className="rhythm-input">
      <div>
        <input
          type="text"
          aria-label={props.label}
          aria-invalid={!parsed.ok}
          aria-describedby={`${id}-status`}
          placeholder="es. 8. 16"
          value={text}
          onChange={(e) => props.onChange(e.target.value === '' ? undefined : e.target.value)}
        />
        <p id={`${id}-status`} className={parsed.ok ? 'visually-hidden' : 'field-error'}>
          {parsed.ok ? 'Pattern valido.' : `Pattern non valido: ${parsed.error}.`}
        </p>
      </div>
      {parsed.ok ? (
        <AbcPreview abc={rhythmToAbc(parsed.tokens)} label={`Notazione del pattern ${text}`} />
      ) : (
        <div className="abc-preview is-empty" aria-hidden="true" />
      )}
    </div>
  )
}

/** One choice among a few, shown as adjoining buttons. */
export function Segmented(props: { legend: string; value: AttributeValue | undefined; options: readonly string[]; labels?: Record<string, string>; onChange: (value: string) => void }) {
  const name = useId()
  return (
    <fieldset className="segmented">
      <legend className="visually-hidden">{props.legend}</legend>
      {props.options.map((option) => (
        <label key={option} className={props.value === option ? 'is-on' : undefined}>
          <input type="radio" name={name} value={option} checked={props.value === option} onChange={() => props.onChange(option)} />
          {props.labels?.[option] ?? option}
        </label>
      ))}
    </fieldset>
  )
}
