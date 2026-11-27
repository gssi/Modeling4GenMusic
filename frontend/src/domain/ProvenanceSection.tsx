import { useStudio } from '../app/store'
import { LINK_LABELS, type LinkInfo, ORIGIN_LABELS, type Provenance } from './provenance'

/** Why a derived element is there: the links of the weaving model that target it. */
export function ProvenanceSection({ links }: { links: LinkInfo[] }) {
  const showSource = useStudio((s) => s.showSource)
  return (
    <>
      <h3>Provenienza</h3>
      {links.length === 0 && <p className="hint">Nessun collegamento spiega questo elemento.</p>}
      {links.map((link) => (
        <div key={link.id} className={`provenance-link is-${link.kind.toLowerCase()}`}>
          <p className="provenance-kind">
            {LINK_LABELS[link.kind] ?? link.kind}
            {link.targetFeature && <span className="provenance-feature"> · {link.targetFeature}</span>}
          </p>
          <p>{link.rationale}</p>
          {link.kind === 'ConflictFix' && (
            <p>
              Valore {link.originalValue} portato a {link.newValue}
              {link.critique && ` (${link.critique})`}.
            </p>
          )}
          <p className="hint">Regola {link.rule}</p>
          <ul className="ref-list">
            {link.sources.map((source) => (
              <li key={`${source.model}#${source.id}#${source.feature ?? ''}`}>
                <button
                  type="button"
                  className="link"
                  onClick={() => source.model && void showSource(source.model, source.id)}
                  title="Mostra la sorgente accanto"
                >
                  {source.id}
                  {source.feature && `.${source.feature}`}
                  <span className="ref-model"> in {source.model}</span>
                </button>
                <span className="hint">{ORIGIN_LABELS[source.origin] ?? source.origin}</span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </>
  )
}


/** The links of a derivation, one per line, as a log. */
export function DerivationLog({ provenance, onSelect }: { provenance: Provenance; onSelect: (elementId: string) => void }) {
  return (
    <ol className="derivation-log">
      {provenance.links.map((link) => (
        <li key={link.id}>
          <span className={`log-kind is-${link.kind.toLowerCase()}`}>{LINK_LABELS[link.kind] ?? link.kind}</span>
          <button type="button" className="link" onClick={() => onSelect(link.targetId)}>
            {link.targetId}
            {link.targetFeature && `.${link.targetFeature}`}
          </button>
          <span className="log-rule">{link.rule}</span>
          <span>{link.rationale}</span>
        </li>
      ))}
    </ol>
  )
}
