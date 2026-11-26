import { useEffect, useId, useMemo, useState } from 'react'
import type {
  AttributeDescriptor,
  AttributeValue,
  ModelObject,
  Ref,
  ReferenceDescriptor,
} from '../api/types'
import { useStudio } from '../app/store'
import { conformsTo, findClass, hasRoom, plainReferences } from './metamodel'
import { allObjects, locate, removeReference, renameId, setAttribute, setReference } from './modelOps'

interface Candidate {
  ref: Ref
  label: string
  group: string
}

const refKey = (ref: Ref) => `${ref.model ?? ''}#${ref.id}`

/** Text field that commits while typing when the text is a valid value, and resets when the model changes. */
function ValueField(props: {
  id: string
  attribute: AttributeDescriptor
  value: AttributeValue | undefined
  onChange: (value: AttributeValue | undefined) => void
}) {
  const { id, attribute, value, onChange } = props
  const [text, setText] = useState(value === undefined ? '' : String(value))
  const [invalid, setInvalid] = useState(false)
  useEffect(() => {
    setText(value === undefined ? '' : String(value))
    setInvalid(false)
  }, [value])

  const numeric = attribute.type === 'int' || attribute.type === 'double'
  const commit = (raw: string) => {
    setText(raw)
    if (!numeric) {
      onChange(raw === '' ? undefined : raw)
      return
    }
    if (raw.trim() === '') {
      setInvalid(false)
      onChange(undefined)
      return
    }
    const parsed = Number(raw.replace(',', '.'))
    const ok = Number.isFinite(parsed) && (attribute.type === 'double' || Number.isInteger(parsed))
    setInvalid(!ok)
    if (ok) onChange(parsed)
  }

  return (
    <>
      <input
        id={id}
        type="text"
        inputMode={attribute.type === 'int' ? 'numeric' : attribute.type === 'double' ? 'decimal' : undefined}
        value={text}
        aria-required={attribute.lower >= 1}
        aria-invalid={invalid}
        aria-describedby={invalid ? `${id}-error` : undefined}
        onChange={(e) => commit(e.target.value)}
      />
      {invalid && (
        <p className="field-error" id={`${id}-error`}>
          {attribute.type === 'int' ? 'Serve un numero intero.' : 'Serve un numero.'}
        </p>
      )}
    </>
  )
}

function IdField({ modelId, object }: { modelId: string; object: ModelObject }) {
  const fieldId = useId()
  const [text, setText] = useState(object.id)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    setText(object.id)
    setError(null)
  }, [object.id])

  const commit = () => {
    if (text === object.id) return
    const { open, mutate, renamePosition } = useStudio.getState()
    const renamed = renameId(open[modelId].doc.roots, object.id, text)
    if (!renamed) {
      setError('Identificativo vuoto, con spazi o già usato nel modello.')
      return
    }
    renamePosition(modelId, object.id, text.trim())
    mutate(modelId, () => renamed)
  }

  return (
    <div className="field">
      <label htmlFor={fieldId}>Identificativo</label>
      <input
        id={fieldId}
        type="text"
        value={text}
        aria-invalid={!!error}
        aria-describedby={error ? `${fieldId}-error` : undefined}
        onChange={(e) => {
          setText(e.target.value)
          setError(null)
        }}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
      />
      {error && (
        <p className="field-error" id={`${fieldId}-error`}>
          {error}
        </p>
      )}
    </div>
  )
}

function AttributeField(props: { modelId: string; object: ModelObject; attribute: AttributeDescriptor }) {
  const { modelId, object, attribute } = props
  const fieldId = useId()
  const value = object.attributes[attribute.name]
  const change = (next: AttributeValue | undefined) =>
    useStudio
      .getState()
      .mutate(modelId, (roots) => setAttribute(roots, object.id, attribute.name, next), `${object.id}.${attribute.name}`)

  const label = (
    <label htmlFor={fieldId}>
      {attribute.name}
      {attribute.lower >= 1 && <span className="required"> (obbligatorio)</span>}
    </label>
  )

  if (attribute.type === 'boolean') {
    return (
      <div className="field is-inline">
        <input id={fieldId} type="checkbox" checked={value === true} onChange={(e) => change(e.target.checked)} />
        {label}
      </div>
    )
  }
  return (
    <div className="field">
      {label}
      {attribute.type === 'enum' ? (
        <select id={fieldId} value={String(value ?? '')} onChange={(e) => change(e.target.value)}>
          {attribute.literals?.map((literal) => (
            <option key={literal} value={literal}>
              {literal}
            </option>
          ))}
        </select>
      ) : (
        <ValueField id={fieldId} attribute={attribute} value={value} onChange={change} />
      )}
    </div>
  )
}

function ReferenceField(props: {
  modelId: string
  object: ModelObject
  reference: ReferenceDescriptor
  candidates: Candidate[]
}) {
  const { modelId, object, reference, candidates } = props
  const fieldId = useId()
  const current = object.references[reference.name] ?? []
  const single = reference.upper === 1
  const { mutate, openModel, select } = useStudio.getState()
  const taken = new Set(current.map(refKey))
  const available = candidates.filter((c) => !taken.has(refKey(c.ref)))
  const groups = [...new Set(available.map((c) => c.group))]
  const full = !single && !hasRoom(reference, current.length)

  const choose = (key: string) => {
    const candidate = candidates.find((c) => refKey(c.ref) === key)
    if (!candidate) return
    mutate(modelId, (roots) => setReference(roots, object.id, reference.name, single ? [candidate.ref] : [...current, candidate.ref]))
  }

  return (
    <div className="field">
      <label htmlFor={fieldId}>
        {reference.name}
        {reference.lower >= 1 && <span className="required"> (obbligatorio)</span>}
      </label>
      {current.length > 0 && (
        <ul className="ref-list">
          {current.map((ref) => (
            <li key={refKey(ref)} className={ref.unresolved ? 'is-unresolved' : undefined}>
              <button
                type="button"
                className="link"
                onClick={() => (ref.model ? void openModel(ref.model, ref.id) : select(modelId, [ref.id]))}
                title={ref.model ? 'Apri il modello che lo contiene' : 'Seleziona nel diagramma'}
              >
                {ref.id}
                {ref.model && <span className="ref-model"> in {ref.model}</span>}
              </button>
              {ref.unresolved && <span className="ref-warning">non trovato</span>}
              <button
                type="button"
                className="icon"
                aria-label={`Rimuovi il collegamento a ${ref.id}`}
                onClick={() => mutate(modelId, (roots) => removeReference(roots, object.id, reference.name, ref))}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {!full && (
        <select id={fieldId} value="" onChange={(e) => choose(e.target.value)} disabled={!available.length}>
          <option value="">
            {available.length ? (current.length ? (single ? 'Sostituisci con…' : 'Aggiungi…') : 'Scegli…') : 'Nessun elemento adatto'}
          </option>
          {groups.map((group) => (
            <optgroup key={group} label={group}>
              {available
                .filter((c) => c.group === group)
                .map((c) => (
                  <option key={refKey(c.ref)} value={refKey(c.ref)}>
                    {c.label}
                  </option>
                ))}
            </optgroup>
          ))}
        </select>
      )}
    </div>
  )
}

export function PropertiesPanel({ modelId }: { modelId: string }) {
  const model = useStudio((s) => s.open[modelId])
  const metamodels = useStudio((s) => s.metamodels)
  const externals = useStudio((s) => s.externals)
  const models = useStudio((s) => s.models)
  const loadExternals = useStudio((s) => s.loadExternals)

  const selectedId = model.selection.length === 1 ? model.selection[0] : null
  const located = selectedId ? locate(model.doc.roots, selectedId) : undefined
  const object = located?.object
  const nsURI = model.doc.metamodel
  const descriptor = object ? findClass(metamodels, { metamodel: nsURI, name: object.eClass }) : undefined
  const references = useMemo(() => (descriptor ? plainReferences(descriptor) : []), [descriptor])

  useEffect(() => {
    if (references.length) void loadExternals(modelId)
  }, [references, modelId, loadExternals, models])

  const candidatesOf = (reference: ReferenceDescriptor): Candidate[] => {
    const result: Candidate[] = []
    for (const other of allObjects(model.doc.roots)) {
      if (other.id !== object?.id && conformsTo(metamodels, { metamodel: nsURI, name: other.eClass }, reference.target)) {
        result.push({ ref: { id: other.id }, label: `${other.id} : ${other.eClass}`, group: 'Questo modello' })
      }
    }
    for (const header of models) {
      const external = externals[header.id]
      if (!external || external.id === modelId) continue
      for (const other of allObjects(external.roots)) {
        if (conformsTo(metamodels, { metamodel: external.metamodel, name: other.eClass }, reference.target)) {
          result.push({
            ref: { model: external.id, id: other.id },
            label: `${other.id} : ${other.eClass}`,
            group: external.name,
          })
        }
      }
    }
    return result
  }

  if (model.selection.length > 1) {
    return (
      <aside className="properties" aria-label="Proprietà">
        <h2>Proprietà</h2>
        <p className="hint">{model.selection.length} elementi selezionati. Selezionane uno solo per modificarlo.</p>
      </aside>
    )
  }

  if (selectedId?.startsWith('proxy:')) {
    const [external, externalId] = selectedId.slice('proxy:'.length).split('#')
    return (
      <aside className="properties" aria-label="Proprietà">
        <h2>Elemento di un altro modello</h2>
        <p>
          {externalId} si trova nel modello {external}.
        </p>
        <button type="button" onClick={() => void useStudio.getState().openModel(external, externalId)}>
          Apri {external}
        </button>
      </aside>
    )
  }

  if (!object || !descriptor) {
    return (
      <aside className="properties" aria-label="Proprietà">
        <h2>Proprietà</h2>
        <p className="hint">Seleziona un elemento del diagramma per vederne e modificarne i valori.</p>
      </aside>
    )
  }

  return (
    <aside className="properties" aria-label="Proprietà">
      <h2>{object.eClass}</h2>
      {located?.parent && (
        <p className="hint">
          Contenuto in{' '}
          <button type="button" className="link" onClick={() => useStudio.getState().select(modelId, [located.parent!.id])}>
            {located.parent.id}
          </button>{' '}
          ({located.feature})
        </p>
      )}
      <IdField modelId={modelId} object={object} />
      {descriptor.attributes
        .filter((a) => a.upper === 1)
        .map((attribute) => (
          <AttributeField key={attribute.name} modelId={modelId} object={object} attribute={attribute} />
        ))}
      {references.length > 0 && <h3>Collegamenti</h3>}
      {references.map((reference) => (
        <ReferenceField
          key={reference.name}
          modelId={modelId}
          object={object}
          reference={reference}
          candidates={candidatesOf(reference)}
        />
      ))}
    </aside>
  )
}
