import { useEffect, useState } from 'react'
import { type PlayerText, ScorePlayer } from '../domain/ScorePlayer'
import { type CaseData, type CaseEntry, type Requirement, type Result, downloads, loadCase, loadCases, loadResult } from './data'

const PLAYER: PlayerText = {
  group: 'Listen to the exercise',
  play: 'Play',
  pause: 'Pause',
  resume: 'Resume',
  loading: 'Preparing the audio…',
  restart: 'From the start',
  speed: 'Speed',
  speeds: ['Half speed', 'Three quarters', 'Written tempo'],
  error: 'The audio could not be prepared: the instrument sounds are downloaded from the Internet.',
  unsupported: 'This browser cannot play the exercise.',
}

/** `?view=full` shows what the experts must not see: the automatic checks and how the exercise was obtained. */
const FULL = new URLSearchParams(window.location.search).get('view') === 'full'

const currentCase = () => window.location.hash.replace(/^#\/?/, '').toLowerCase()

export function ExperimentApp() {
  const [cases, setCases] = useState<CaseEntry[] | null>(null)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(currentCase)

  useEffect(() => {
    loadCases().then(setCases, (e: Error) => setError(e.message))
    const onHash = () => {
      setSelected(currentCase())
      window.scrollTo(0, 0)
    }
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  const entry = cases?.find((c) => c.folder === selected)
  return (
    <div className="experiment">
      <header className="site-head">
        <a href="#/" className="site-title">
          Exercises for special needs
        </a>
        <span>Expert evaluation{FULL && ' · researcher view'}</span>
      </header>
      <main>
        {error && <p className="field-error">The cases could not be loaded ({error}).</p>}
        {cases && (entry ? <CasePage key={entry.folder} entry={entry} cases={cases} /> : <Index cases={cases} />)}
      </main>
    </div>
  )
}

function Index({ cases }: { cases: CaseEntry[] }) {
  return (
    <>
      <h1>Do the generated exercises respect the modelled requirements?</h1>
      <div className="prose">
        <p>
          Each case below describes a piano learner, the needs recorded in the learner’s profile and what a teacher asked for. From these models a
          complete specification of the exercise was derived automatically, and a music language model was asked to write an exercise that follows it.
        </p>
        <p>
          For every case you will find the list of requirements that were derived, each with an identifier such as <strong>C2-R5</strong>, and the
          exercise that was generated: you can read the score and listen to it. Please judge, requirement by requirement, whether the exercise
          respects it, and report your answers in the questionnaire using the identifiers.
        </p>
        <p>
          The exercises are shown exactly as they were generated, one per case, without any selection or correction: some of them may not respect
          every requirement.
        </p>
      </div>
      <ol className="case-list">
        {cases.map((c) => (
          <li key={c.id}>
            {c.generated ? (
              <a href={`#/${c.folder}`}>
                <span className="case-id">{c.id}</span>
                <span>{c.title}</span>
              </a>
            ) : (
              <span className="hint">
                {c.id} · {c.title} (not generated yet)
              </span>
            )}
          </li>
        ))}
      </ol>
    </>
  )
}

function CasePage({ entry, cases }: { entry: CaseEntry; cases: CaseEntry[] }) {
  const [data, setData] = useState<CaseData | null>(null)
  const [result, setResult] = useState<Result | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    loadCase(entry).then(setData, (e: Error) => setError(e.message))
    if (FULL) loadResult(entry).then(setResult, () => setResult(null))
  }, [entry])

  if (error) return <p className="field-error">This case could not be loaded ({error}).</p>
  if (!data) return <p className="hint">Loading…</p>

  const { info, requirements, abc } = data
  const at = cases.findIndex((c) => c.folder === entry.folder)
  const previous = cases[at - 1]
  const next = cases[at + 1]

  return (
    <>
      <p className="crumb">
        <a href="#/">All cases</a>
      </p>
      <h1>
        <span className="case-id">{info.id}</span> {info.title}
      </h1>

      <section>
        <h2>What was modelled</h2>
        <dl className="described">
          <dt>The learner</dt>
          <dd>{info.learner}</dd>
          <dt>Recorded needs</dt>
          <dd>
            {info.needs.length === 0 ? (
              'None.'
            ) : (
              <ul className="needs">
                {info.needs.map((need) => (
                  <li key={need.type}>
                    <strong>{need.type}.</strong> {need.description}
                  </li>
                ))}
              </ul>
            )}
          </dd>
          <dt>The teacher’s request</dt>
          <dd>{info.intent}</dd>
          <dt>What the derivation does</dt>
          <dd>{info.expectation}</dd>
        </dl>
      </section>

      <section>
        <h2>Requirements the exercise should respect</h2>
        <p className="hint">Derived automatically from the models above. Use the identifiers in the questionnaire.</p>
        <RequirementTable requirements={requirements.requirements} />
      </section>

      <section>
        <h2>Generated exercise</h2>
        {requirements.staffScale > 1 && <p className="hint">The staff is shown {requirements.staffScale} times its normal size, as the profile requires.</p>}
        <ScorePlayer abc={abc} scale={requirements.staffScale} label={`Score of the exercise generated for case ${info.id}`} text={PLAYER} />
        <details className="abc">
          <summary>ABC notation of the exercise</summary>
          <pre className="abc-source">{abc}</pre>
        </details>
      </section>

      <section>
        <h2>Files of this case</h2>
        <ul className="downloads">
          {downloads(entry).map((file) => (
            <li key={file.href}>
              <a href={file.href} download>
                {file.label}
              </a>
            </li>
          ))}
        </ul>
      </section>

      {FULL && <ResearcherView requirements={data} result={result} />}

      <nav className="pager" aria-label="Other cases">
        {previous ? <a href={`#/${previous.folder}`}>← {previous.id}</a> : <span />}
        {next ? <a href={`#/${next.folder}`}>{next.id} →</a> : <span />}
      </nav>
    </>
  )
}

function RequirementTable({ requirements }: { requirements: Requirement[] }) {
  return (
    <div className="table-scroll">
      <table className="requirements">
        <thead>
          <tr>
            <th scope="col">ID</th>
            <th scope="col">Requirement</th>
            <th scope="col">Where it comes from</th>
          </tr>
        </thead>
        <tbody>
          {requirements.map((r) => (
            <tr key={r.id}>
              <th scope="row">{r.id}</th>
              <td>
                {r.text}
                {r.strength === 'preferred' && <span className="tag">preference, not mandatory</span>}
                {r.appliedBy === 'display' && <span className="tag">applies to how the score is shown</span>}
              </td>
              <td>{r.origin.label}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Checks({ checks }: { checks: Result['report']['checks'] }) {
  return (
    <ul className="check-list">
      {checks.map((check, index) => (
        <li key={index} className={check.passed ? 'is-ok' : check.blocking ? 'is-bad' : 'is-note'}>
          <span className="check-state">{check.passed ? 'passed' : check.blocking ? 'failed' : 'remark'}</span>
          <span>
            <code>{check.name}</code> · <span lang="it">{check.message}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}

function ResearcherView({ requirements, result }: { requirements: CaseData; result: Result | null }) {
  const all = [...requirements.requirements.requirements, ...requirements.requirements.qualityGoals]
  return (
    <div className="researcher">
      <h2>Researcher view</h2>
      <p className="hint">Not shown to the experts. Messages produced by the studio are in Italian.</p>
      {!result ? (
        <p className="hint">No result file for this case.</p>
      ) : (
        <>
          <section>
            <h3>Automatic checks on the exercise shown above</h3>
            <p className={`verdict ${result.valid ? 'is-ok' : 'is-bad'}`}>
              {result.valid ? 'All blocking checks passed' : 'At least one blocking check failed'} · attempt {result.chosenAttempt} of {result.attempts.length}
            </p>
            <Checks checks={result.report.checks} />
          </section>

          <section>
            <h3>Generation</h3>
            <dl className="facts">
              <dt>Generator</dt>
              <dd>{result.metadata.generator}</dd>
              <dt>Model</dt>
              <dd>{result.metadata.model ?? '—'}</dd>
              <dt>Seed of the chosen attempt</dt>
              <dd>{result.metadata.seed ?? '—'}</dd>
              <dt>Attempts</dt>
              <dd>{result.attempts.length}</dd>
              <dt>Time</dt>
              <dd>{result.metadata.seconds} s</dd>
            </dl>
            {result.metadata.prompt && (
              <details>
                <summary>Request sent to the music model</summary>
                <pre className="abc-source">{result.metadata.prompt}</pre>
              </details>
            )}
            {result.intentWarnings.length > 0 && (
              <>
                <h4>Warnings on the teacher’s request, before deriving</h4>
                <ul className="plain">
                  {result.intentWarnings.map((w) => (
                    <li key={w.rule}>
                      <code>{w.rule}</code> · <span lang="it">{w.message}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>

          {result.attempts.length > 1 && (
            <section>
              <h3>All attempts</h3>
              {result.attempts.map((attempt, index) => (
                <details key={index} className="attempt">
                  <summary>
                    Attempt {index + 1}: {attempt.report.valid ? 'valid' : 'not valid'}
                    {index + 1 === result.chosenAttempt && ' (shown above)'}
                  </summary>
                  <pre className="abc-source">{attempt.abc}</pre>
                  <Checks checks={attempt.report.checks} />
                </details>
              ))}
            </section>
          )}
        </>
      )}

      <section>
        <h3>Traceability of the requirements</h3>
        <div className="table-scroll">
          <table className="requirements">
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">Kind</th>
                <th scope="col">Told to the generator and checked</th>
                <th scope="col">Rules</th>
                <th scope="col">Rationale in the trace</th>
              </tr>
            </thead>
            <tbody>
              {all.map((r) => (
                <tr key={r.id}>
                  <th scope="row">{r.id}</th>
                  <td>
                    <code>{r.kind}</code>
                    {r.kind === 'qualityGoal' && <div>{r.text}</div>}
                  </td>
                  <td>{r.appliedBy === 'display' ? 'no: applied by the page' : r.enforced ? (r.strength === 'preferred' ? 'yes, as a preference' : 'yes') : 'no: not enforced yet'}</td>
                  <td>{r.origin.rules?.join(' → ')}</td>
                  <td lang="it">
                    {r.origin.rationale}
                    {r.origin.originalValue !== undefined && ` (${r.origin.originalValue} → ${r.origin.newValue})`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
