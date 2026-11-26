import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import type { AssistantStatus, RuleProposal, RuleSet, RuleSetRule, Severity, TransformationScript } from '../api/types'
import { Dialog } from './Dialog'
import { roleLabel, ruleExample, wording } from '../domain/labels'
import { useStudio } from './store'

const SEVERITY: Record<Severity, string> = { ERROR: 'Errore', WARNING: 'Avviso', INFO: 'Nota' }
const BLOCKS: Record<Severity, string> = {
  ERROR: 'Un errore indica un modello non valido: va corretto.',
  WARNING: 'Un avviso segnala qualcosa da valutare: non blocca.',
  INFO: 'Una nota è solo informativa.',
}

/** `T!ExerciseSpec` reads as the class name; the part before `!` is the name of the model in the script. */
const className = (type: string) => type.slice(type.indexOf('!') + 1)

const matches = (rule: RuleSetRule, query: string) =>
  !query || [rule.name, rule.comment, rule.source].some((text) => text?.toLowerCase().includes(query))

function Code({ label, text }: { label: string; text?: string }) {
  if (!text) return null
  return (
    <>
      <dt>{label}</dt>
      <dd>
        <pre className="rule-code">
          <code>{text}</code>
        </pre>
      </dd>
    </>
  )
}

function RuleCard({ rule, focused }: { rule: RuleSetRule; focused: boolean }) {
  const ref = useRef<HTMLElement>(null)
  useEffect(() => {
    if (focused) ref.current?.scrollIntoView({ block: 'center' })
  }, [focused])
  return (
    <article ref={ref} className={`rule${focused ? ' is-focused' : ''}`} aria-label={rule.name}>
      <h5>
        <span className={`rule-severity is-${rule.severity.toLowerCase()}`} title={BLOCKS[rule.severity]}>
          {SEVERITY[rule.severity]}
        </span>
        {rule.name}
        <span className="rule-line">riga {rule.line}</span>
      </h5>
      {rule.comment && <p className="rule-comment">{rule.comment}</p>}
      <dl>
        <Code label="Si controlla quando" text={rule.guard} />
        <Code label="Deve valere" text={rule.check} />
        <Code label="Messaggio" text={rule.message} />
        {rule.fixes.map((fix, i) => (
          <Code key={i} label="Correzione rapida" text={[fix.guard && `guard: ${fix.guard}`, `title: ${fix.title ?? ''}`, fix.body].filter(Boolean).join('\n')} />
        ))}
      </dl>
    </article>
  )
}

/** What the server says about a new text: the same for validation and transformation scripts. */
interface CheckResult {
  problems: RuleSet['problems']
  failures: RuleSet['failures']
  /** How many rules the text holds, when it parses. */
  count: number
}

/** A script that can be edited: where its text is checked and kept. */
interface Editable {
  script: string
  source: string
  check: (source: string) => Promise<CheckResult>
  save: (source: string) => Promise<boolean>
}

type CheckState = { status: 'unchanged' } | { status: 'checking' } | { status: 'failed'; text: string } | { status: 'done'; result: CheckResult }

/**
 * Describes a rule in plain words and gets it written in the language of the script. The proposal is
 * shown with what the checks say about it; putting it in the text is a separate, deliberate step.
 */
function RuleAssistant({ script, source, onInsert }: { script: string; source: string; onInsert: (added: string) => void }) {
  const [status, setStatus] = useState<AssistantStatus | null>(null)
  const [description, setDescription] = useState('')
  const [asking, setAsking] = useState(false)
  const [proposal, setProposal] = useState<RuleProposal | null>(null)
  const [error, setError] = useState<string | null>(null)
  const example = ruleExample(script)
  const evl = script.endsWith('.evl')

  useEffect(() => {
    let cancelled = false
    api
      .getAssistant()
      .then((s) => !cancelled && setStatus(s))
      .catch(() => !cancelled && setStatus({ available: false, model: '', reason: 'Il server non risponde.' }))
    return () => {
      cancelled = true
    }
  }, [])

  const ask = async () => {
    setAsking(true)
    setError(null)
    setProposal(null)
    try {
      setProposal(await api.proposeRule(script, source, description))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setAsking(false)
    }
  }

  const sound = !!proposal && proposal.problems.length === 0 && proposal.failures.length === 0
  const effects = proposal?.effects
  return (
    <section className="rule-assistant" aria-label="Assistente per le regole">
      <h4>Descrivi la regola</h4>
      {status && !status.available ? (
        <p className="hint">
          L’assistente non è disponibile. {status.reason} Puoi comunque scrivere la regola a mano qui sotto.
        </p>
      ) : (
        <>
          <p className="hint">
            Scrivi cosa deve fare la regola: un modello linguistico locale{status?.model ? ` (${status.model})` : ''} la traduce in{' '}
            {evl ? 'EVL' : 'ETL'}. È una proposta: leggila prima di inserirla.
          </p>
          <label htmlFor={`rules-ask-${script}`} className="visually-hidden">
            Descrizione della regola
          </label>
          <textarea
            id={`rules-ask-${script}`}
            rows={2}
            value={description}
            placeholder={example ? `Per esempio: ${example}` : undefined}
            onChange={(e) => setDescription(e.target.value)}
          />
          <div className="rule-editor-actions">
            <button type="button" onClick={() => void ask()} disabled={!status?.available || asking || !description.trim()}>
              Proponi la regola
            </button>
          </div>
        </>
      )}
      <div aria-live="polite">
        {asking && <p className="hint">Sto scrivendo e controllando la regola: può servire fino a un minuto…</p>}
        {error && <p className="rule-problems">{error}</p>}
        {proposal && (
          <div className="rule-proposal">
            <pre className="rule-code">
              <code>{proposal.rule}</code>
            </pre>
            {sound ? (
              <p className="rule-sound">La proposta ha sintassi corretta e gira sui modelli del workspace.</p>
            ) : (
              <div className="rule-problems">
                <p>
                  Dopo {proposal.attempts === 1 ? 'un tentativo' : `${proposal.attempts} tentativi`} la proposta ha ancora errori. Puoi
                  inserirla e correggerla a mano, oppure descriverla in un altro modo.
                </p>
                <ul>
                  {proposal.problems.map((p, i) => (
                    <li key={`p${i}`}>
                      Sintassi, riga {p.line}: {p.reason}
                    </li>
                  ))}
                  {proposal.failures.map((f) => (
                    <li key={f.modelId}>
                      «{f.modelName}»: {f.message}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {sound && effects && effects.models === 0 && (
              <p className="hint">Nel workspace non ci sono modelli su cui provarla: controlla con cura che faccia ciò che hai chiesto.</p>
            )}
            {sound && effects && effects.models > 0 && effects.reports.length === 0 && (
              <p>
                Provata su {effects.models === 1 ? 'un modello' : `${effects.models} modelli`} del workspace, non segnala nulla. Se ti
                aspettavi una segnalazione, la regola non fa ciò che hai chiesto.
              </p>
            )}
            {sound && effects && effects.reports.length > 0 && (
              <>
                <p>
                  Provata su {effects.models === 1 ? 'un modello' : `${effects.models} modelli`} del workspace, segnalerebbe questo. Se
                  non te lo aspettavi, la regola non fa ciò che hai chiesto.
                </p>
                <ul>
                  {effects.reports.slice(0, 6).map((r, i) => (
                    <li key={i}>
                      «{r.modelName}»{r.elementId ? `, ${r.elementId}` : ''}: {r.message}
                    </li>
                  ))}
                  {effects.reports.length > 6 && <li>e altre {effects.reports.length - 6} segnalazioni</li>}
                </ul>
              </>
            )}
            {sound && !evl && (
              <p className="hint">Una derivazione di prova è riuscita; per vedere cosa cambia nella specifica va rifatto «Deriva un esercizio» dopo il salvataggio.</p>
            )}
            <div className="rule-editor-actions">
              <button
                type="button"
                className={sound ? 'primary' : undefined}
                onClick={() => {
                  onInsert(proposal.source.split('\n').slice(proposal.line - 1).join('\n'))
                  setProposal(null)
                  setDescription('')
                }}
              >
                Inserisci nel testo
              </button>
              <button type="button" onClick={() => setProposal(null)}>
                Scarta
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

/**
 * The text of a script, checked by the server while it is written: first the syntax, then a trial run
 * on the workspace models. It can be saved only when both pass.
 */
function RuleEditor({ set, onClose }: { set: Editable; onClose: () => void }) {
  const { check: runCheck, save: runSave } = set
  const [source, setSource] = useState(set.source)
  const [check, setCheck] = useState<CheckState>({ status: 'unchanged' })
  const [saving, setSaving] = useState(false)
  const area = useRef<HTMLTextAreaElement>(null)
  const changed = source !== set.source

  useEffect(() => {
    if (!changed) {
      setCheck({ status: 'unchanged' })
      return
    }
    setCheck({ status: 'checking' })
    let cancelled = false
    const timer = setTimeout(() => {
      runCheck(source)
        .then((result) => !cancelled && setCheck({ status: 'done', result }))
        .catch((e: unknown) => !cancelled && setCheck({ status: 'failed', text: e instanceof Error ? e.message : String(e) }))
    }, 450)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [runCheck, source, changed])

  const goToLine = (line: number) => {
    const lines = source.split('\n')
    const start = lines.slice(0, Math.max(0, line - 1)).reduce((n, l) => n + l.length + 1, 0)
    const end = start + (lines[line - 1]?.length ?? 0)
    area.current?.focus()
    area.current?.setSelectionRange(start, end)
  }

  /** Adds a proposed rule at the end of the text and selects it, so that it is seen and read. */
  const insert = (added: string) => {
    const base = source.trimEnd()
    const next = `${base}\n\n${added.trimEnd()}\n`
    setSource(next)
    requestAnimationFrame(() => {
      area.current?.focus()
      area.current?.setSelectionRange(base.length + 2, next.length - 1)
      if (area.current) area.current.scrollTop = area.current.scrollHeight
    })
  }

  const result = check.status === 'done' ? check.result : null
  const sound = !!result && result.problems.length === 0 && result.failures.length === 0
  const save = async () => {
    setSaving(true)
    const saved = await runSave(source)
    setSaving(false)
    if (saved) onClose()
  }

  return (
    <div className="rule-editor">
      <RuleAssistant script={set.script} source={source} onInsert={insert} />
      <label htmlFor={`rules-source-${set.script}`}>Testo di {set.script}</label>
      <textarea
        id={`rules-source-${set.script}`}
        ref={area}
        className="rule-source"
        value={source}
        spellCheck={false}
        autoCapitalize="off"
        autoCorrect="off"
        rows={Math.min(32, Math.max(12, source.split('\n').length + 1))}
        aria-describedby={`rules-check-${set.script}`}
        onChange={(e) => setSource(e.target.value)}
      />
      <div id={`rules-check-${set.script}`} className="rule-check" aria-live="polite">
        {check.status === 'unchanged' && <p className="hint">Nessuna modifica. Il testo viene controllato mentre scrivi.</p>}
        {check.status === 'checking' && <p className="hint">Controllo in corso…</p>}
        {check.status === 'failed' && <p className="rule-problems">Controllo non riuscito: {check.text}</p>}
        {result && result.problems.length > 0 && (
          <div className="rule-problems">
            <p>
              {result.problems.length === 1 ? 'C’è un errore di sintassi' : `Ci sono ${result.problems.length} errori di sintassi`}: correggi per
              poter salvare.
            </p>
            <ul>
              {result.problems.map((p, i) => (
                <li key={i}>
                  <button type="button" className="link" onClick={() => goToLine(p.line)}>
                    riga {p.line}, colonna {p.column}
                  </button>
                  : {p.reason}
                </li>
              ))}
            </ul>
          </div>
        )}
        {result && result.problems.length === 0 && result.failures.length > 0 && (
          <div className="rule-problems">
            <p>La sintassi è corretta, ma eseguite sui modelli del workspace le regole si fermano con un errore:</p>
            <ul>
              {result.failures.map((f) => (
                <li key={f.modelId}>
                  «{f.modelName}»: {f.message}
                </li>
              ))}
            </ul>
          </div>
        )}
        {sound && (
          <p className="rule-sound">
            Sintassi corretta e prova sui modelli del workspace riuscita: {result.count} regole.
          </p>
        )}
      </div>
      <div className="rule-editor-actions">
        <button type="button" className="primary" onClick={() => void save()} disabled={!changed || !sound || saving}>
          Salva le regole
        </button>
        <button type="button" onClick={onClose}>
          Annulla
        </button>
      </div>
    </div>
  )
}

function RuleSetSection({ set, query, focus }: { set: RuleSet; query: string; focus: string | null }) {
  const summaries = useStudio((s) => s.summaries)
  const name = summaries.find((s) => s.nsURI === set.metamodel)?.name ?? set.metamodel
  const metamodelName = (nsURI: string) => summaries.find((s) => s.nsURI === nsURI)?.name ?? nsURI
  // A script may open the same type more than once: show its rules together.
  const groups = useMemo(() => {
    const byType = new Map<string, RuleSetRule[]>()
    for (const context of set.contexts) {
      byType.set(context.type, [...(byType.get(context.type) ?? []), ...context.rules.filter((r) => matches(r, query))])
    }
    return [...byType].filter(([, rules]) => rules.length > 0)
  }, [set, query])
  const [editing, setEditing] = useState(false)
  const [resetting, setResetting] = useState(false)
  const editable = useMemo<Editable>(
    () => ({
      script: set.script,
      source: set.source,
      check: async (source) => {
        const result = await api.checkRules(set.script, source)
        return { problems: result.problems, failures: result.failures, count: result.contexts.reduce((n, c) => n + c.rules.length, 0) }
      },
      save: (source) => useStudio.getState().saveRules(set.script, source),
    }),
    [set.script, set.source],
  )
  const total = set.contexts.reduce((n, c) => n + c.rules.length, 0)
  const shown = groups.reduce((n, [, rules]) => n + rules.length, 0)
  if (query && shown === 0 && !editing) return null

  return (
    <section className="rule-set" aria-label={`Regole di ${set.script}`}>
      <h3>
        {wording(set.metamodel, name).plural}
        <span className="rule-set-file">
          {set.script}
          {set.modified && ', modificato'}
        </span>
        {!editing && (
          <span className="rule-set-actions">
            <button type="button" onClick={() => setEditing(true)} aria-label={`Modifica ${set.script}`}>
              Modifica
            </button>
            {set.modified && (
              <button type="button" onClick={() => setResetting(true)} aria-label={`Ripristina le regole originali di ${set.script}`}>
                Ripristina le originali
              </button>
            )}
          </span>
        )}
      </h3>
      <p className="hint">
        {total} {total === 1 ? 'regola' : 'regole'} sui modelli «{name}»
        {set.exceptRoles.length > 0 && `, tranne quelli di tipo «${set.exceptRoles.map((r) => roleLabel(set.metamodel, r)).join('», «')}»`}. Nello
        script il modello si chiama {set.model}
        {set.related.map((r) => `; ${r.alias} è il modello «${metamodelName(r.metamodel)}» raggiunto da «${r.reference}»`).join('')}.
        {set.tools.length > 0 && ` Funzioni di supporto disponibili: ${set.tools.join(', ')}.`}
      </p>
      {set.problems.length > 0 && (
        <div className="rule-problems" role="alert">
          <p>Lo script ha errori di sintassi e non può essere eseguito:</p>
          <ul>
            {set.problems.map((p, i) => (
              <li key={i}>
                riga {p.line}, colonna {p.column}: {p.reason}
              </li>
            ))}
          </ul>
        </div>
      )}
      {editing && <RuleEditor set={editable} onClose={() => setEditing(false)} />}
      {resetting && (
        <Dialog
          title={`Ripristinare le regole originali di ${set.script}?`}
          onClose={() => setResetting(false)}
          actions={
            <>
              <button type="button" onClick={() => setResetting(false)}>
                Annulla
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => {
                  void useStudio.getState().resetRules(set.script)
                  setResetting(false)
                }}
              >
                Ripristina
              </button>
            </>
          }
        >
          <p>Le modifiche fatte a questo script vanno perse e tornano le regole fornite con l’applicazione.</p>
        </Dialog>
      )}
      {!editing && groups.map(([type, rules]) => (
        <div key={type} className="rule-context">
          <h4>Per ogni {className(type)}</h4>
          {rules.map((rule) => (
            <RuleCard key={rule.name} rule={rule} focused={rule.name === focus} />
          ))}
        </div>
      ))}
      {!query && !editing && set.operations.length > 0 && (
        <details className="rule-more">
          <summary>Operazioni usate dalle regole ({set.operations.length})</summary>
          {set.operations.map((op) => (
            <div key={`${op.context ?? ''}.${op.name}`}>
              {op.comment && <p className="rule-comment">{op.comment}</p>}
              <pre className="rule-code">
                <code>{op.source}</code>
              </pre>
            </div>
          ))}
        </details>
      )}
      {!query && !editing && (
        <details className="rule-more">
          <summary>Sorgente completo di {set.script}</summary>
          <pre className="rule-code">
            <code>{set.source}</code>
          </pre>
        </details>
      )}
    </section>
  )
}

const textMatches = (query: string, ...texts: (string | undefined)[]) => !query || texts.some((t) => t?.toLowerCase().includes(query))

/** The derivation script: rules that create elements, the blocks around them, and its editing. */
function TransformationSection({ script, query }: { script: TransformationScript; query: string }) {
  const summaries = useStudio((s) => s.summaries)
  const [editing, setEditing] = useState(false)
  const [resetting, setResetting] = useState(false)
  const editable = useMemo<Editable>(
    () => ({
      script: script.script,
      source: script.source,
      check: async (source) => {
        const result = await api.checkTransformation(source)
        return { problems: result.problems, failures: result.failures, count: result.rules.length + result.blocks.filter((b) => b.name).length }
      },
      save: (source) => useStudio.getState().saveTransformation(source),
    }),
    [script.script, script.source],
  )
  const models = (list: TransformationScript['inputs']) =>
    list.map((m) => `${m.alias} («${summaries.find((s) => s.nsURI === m.metamodel)?.name ?? m.metamodel}»)`).join(', ')
  const rules = script.rules.filter((r) => textMatches(query, r.name, r.comment, r.source))
  const blocks = script.blocks.filter((b) => textMatches(query, b.name, b.comment, b.source))
  if (query && !rules.length && !blocks.length && !editing) return null

  return (
    <section className="rule-set" aria-label={`Regole di ${script.script}`}>
      <h3>
        Derivazione dell’esercizio
        <span className="rule-set-file">
          {script.script}
          {script.modified && ', modificato'}
        </span>
        {!editing && (
          <span className="rule-set-actions">
            <button type="button" onClick={() => setEditing(true)} aria-label={`Modifica ${script.script}`}>
              Modifica
            </button>
            {script.modified && (
              <button type="button" onClick={() => setResetting(true)} aria-label={`Ripristina le regole originali di ${script.script}`}>
                Ripristina le originali
              </button>
            )}
          </span>
        )}
      </h3>
      <p className="hint">
        {script.rules.length} regole che creano elementi e {script.blocks.length} blocchi eseguiti prima e dopo. Lo script legge{' '}
        {models(script.inputs)} e scrive {models(script.outputs)}.
        {script.tools.length > 0 && ` Funzioni di supporto disponibili: ${script.tools.join(', ')}.`}
      </p>
      {script.problems.length > 0 && (
        <div className="rule-problems" role="alert">
          <p>Lo script ha errori di sintassi e non può essere eseguito:</p>
          <ul>
            {script.problems.map((p, i) => (
              <li key={i}>
                riga {p.line}, colonna {p.column}: {p.reason}
              </li>
            ))}
          </ul>
        </div>
      )}
      {editing && <RuleEditor set={editable} onClose={() => setEditing(false)} />}
      {resetting && (
        <Dialog
          title={`Ripristinare le regole originali di ${script.script}?`}
          onClose={() => setResetting(false)}
          actions={
            <>
              <button type="button" onClick={() => setResetting(false)}>
                Annulla
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => {
                  void useStudio.getState().resetTransformation()
                  setResetting(false)
                }}
              >
                Ripristina
              </button>
            </>
          }
        >
          <p>Le modifiche fatte a questo script vanno perse e tornano le regole fornite con l’applicazione.</p>
        </Dialog>
      )}
      {!editing && rules.length > 0 && (
        <div className="rule-context">
          <h4>Regole che creano elementi</h4>
          {rules.map((rule) => (
            <article key={rule.name} className="rule" aria-label={rule.name}>
              <h5>
                {rule.name}
                <span className="rule-line">riga {rule.line}</span>
              </h5>
              {rule.comment && <p className="rule-comment">{rule.comment}</p>}
              <dl>
                <dt>Da</dt>
                <dd>{rule.from}</dd>
                <dt>Crea</dt>
                <dd>{rule.to.join(', ')}</dd>
                <Code label="Si applica quando" text={rule.guard} />
              </dl>
              <details className="rule-more">
                <summary>Testo della regola</summary>
                <pre className="rule-code">
                  <code>{rule.source}</code>
                </pre>
              </details>
            </article>
          ))}
        </div>
      )}
      {!editing && blocks.length > 0 && (
        <div className="rule-context">
          <h4>Blocchi eseguiti prima e dopo le regole</h4>
          {blocks.map((block) => (
            <article key={`${block.kind}.${block.name ?? block.line}`} className="rule" aria-label={block.name ?? block.kind}>
              <h5>
                {block.name ?? (block.kind === 'pre' ? 'Preparazione' : 'Chiusura')}
                <span className="rule-line">
                  {block.kind === 'pre' ? 'prima delle regole' : 'dopo le regole'}, riga {block.line}
                </span>
              </h5>
              {block.comment && <p className="rule-comment">{block.comment}</p>}
              <details className="rule-more">
                <summary>Testo del blocco</summary>
                <pre className="rule-code">
                  <code>{block.source}</code>
                </pre>
              </details>
            </article>
          ))}
        </div>
      )}
      {!query && !editing && script.operations.length > 0 && (
        <details className="rule-more">
          <summary>Operazioni usate dalle regole ({script.operations.length})</summary>
          {script.operations.map((op) => (
            <div key={`${op.context ?? ''}.${op.name}.${op.line}`}>
              {op.comment && <p className="rule-comment">{op.comment}</p>}
              <pre className="rule-code">
                <code>{op.source}</code>
              </pre>
            </div>
          ))}
        </details>
      )}
      {!query && !editing && (
        <details className="rule-more">
          <summary>Sorgente completo di {script.script}</summary>
          <pre className="rule-code">
            <code>{script.source}</code>
          </pre>
        </details>
      )}
    </section>
  )
}

/** The validation and derivation rules, as the engine reads them, and the editing of their scripts. */
export function RulesView() {
  const rules = useStudio((s) => s.rules)
  const transformation = useStudio((s) => s.transformation)
  const focus = useStudio((s) => s.ruleFocus)
  const [query, setQuery] = useState('')
  const needle = query.trim().toLowerCase()

  useEffect(() => {
    if (!rules) void useStudio.getState().loadRules()
  }, [rules])

  if (!rules) return <p className="hint form-view">Lettura delle regole…</p>
  const visible =
    rules.some((set) => !needle || set.contexts.some((c) => c.rules.some((r) => matches(r, needle)))) ||
    !!transformation?.rules.some((r) => textMatches(needle, r.name, r.comment, r.source)) ||
    !!transformation?.blocks.some((b) => textMatches(needle, b.name, b.comment, b.source))
  return (
    <div className="form-view rules">
      <h2>Regole</h2>
      <p>
        Le regole di validazione (EVL) sono i controlli fatti a ogni salvataggio; quelle di derivazione (ETL) costruiscono la
        specifica dell’esercizio. Sono mostrate come le legge il motore che le esegue. Con «Modifica» cambi il testo di uno
        script: si salva solo se la sintassi è corretta e se le regole girano sui modelli del workspace.
      </p>
      <div className="field rules-filter">
        <label htmlFor="rules-filter">Cerca tra le regole</label>
        <input id="rules-filter" type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Nome o testo, per esempio «bpm»" />
      </div>
      {rules.map((set) => (
        <RuleSetSection key={set.script} set={set} query={needle} focus={needle ? null : focus} />
      ))}
      {transformation && <TransformationSection script={transformation} query={needle} />}
      {!visible && <p className="hint">Nessuna regola contiene «{query.trim()}».</p>}
    </div>
  )
}
