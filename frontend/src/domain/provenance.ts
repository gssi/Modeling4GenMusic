import type { ModelDocument, ModelHeader, ModelObject } from '../api/types'
import { allObjects } from '../editor/modelOps'

/**
 * Reading of a derivation weaving model: for each element of the derived spec, the links that
 * explain where it comes from.
 */

export const WEAVING = 'http://music/weaving'

/** Blue for what the teacher asked, green for what was derived from the profile or by a rule. */
export type Tint = 'teacher' | 'derived'

export interface LinkSource {
  origin: string
  feature?: string
  model?: string
  id: string
}

export interface LinkInfo {
  id: string
  kind: string
  rule: string
  rationale: string
  targetId: string
  targetFeature?: string
  critique?: string
  originalValue?: string
  newValue?: string
  sources: LinkSource[]
}

export interface Provenance {
  weavingId: string
  derivedId: string
  inputs: { learner?: string; teacherIntent?: string; qualityModel?: string; needCatalog?: string }
  links: LinkInfo[]
  byElement: Record<string, LinkInfo[]>
}

const text = (object: ModelObject, name: string): string | undefined => {
  const value = object.attributes[name]
  return value === undefined || value === '' ? undefined : String(value)
}

/** Returns null when the model is not the weaving of the given derived spec. */
export function buildProvenance(weaving: ModelDocument, derivedId: string): Provenance | null {
  const root = weaving.roots.find((r) => r.eClass === 'DerivationWeaving')
  if (!root || root.references.derivedSpec?.[0]?.model !== derivedId) return null
  const links: LinkInfo[] = []
  for (const link of root.contents.links ?? []) {
    const target = link.references.target?.[0]
    if (!target) continue
    links.push({
      id: link.id,
      kind: link.eClass,
      rule: text(link, 'rule') ?? '',
      rationale: text(link, 'rationale') ?? '',
      targetId: target.id,
      targetFeature: text(link, 'targetFeature'),
      critique: text(link, 'critique'),
      originalValue: text(link, 'originalValue'),
      newValue: text(link, 'newValue'),
      sources: (link.contents.sources ?? []).flatMap((source) => {
        const element = source.references.element?.[0]
        return element
          ? [{ origin: text(source, 'origin') ?? '', feature: text(source, 'feature'), model: element.model, id: element.id }]
          : []
      }),
    })
  }
  const byElement: Record<string, LinkInfo[]> = {}
  for (const link of links) (byElement[link.targetId] ??= []).push(link)
  return {
    weavingId: weaving.id,
    derivedId,
    inputs: {
      learner: root.references.learner?.[0]?.model,
      teacherIntent: root.references.teacherIntent?.[0]?.model,
      qualityModel: root.references.qualityModel?.[0]?.model,
      needCatalog: root.references.needCatalog?.[0]?.model,
    },
    links,
    byElement,
  }
}

const TINT_BY_KIND: Record<string, Tint | undefined> = {
  Refinement: 'teacher',
  Derivation: 'derived',
  ConflictFix: 'derived',
}

export interface ElementTints {
  /** Colour of the whole element, from the links that concern it as a whole. */
  node?: Tint
  /** Colour of single attributes, from the links that name a target feature. */
  attributes: Record<string, Tint>
}

export function elementTints(provenance: Provenance, elementId: string): ElementTints {
  const result: ElementTints = { attributes: {} }
  for (const link of provenance.byElement[elementId] ?? []) {
    const tint = TINT_BY_KIND[link.kind]
    if (!tint) continue
    if (link.targetFeature) result.attributes[link.targetFeature] = tint
    else result.node = tint
  }
  return result
}

/** Colours and legend of a derived spec, in the form the diagram expects. */
export function provenanceDecoration(provenance: Provenance) {
  return {
    tints: (elementId: string) => elementTints(provenance, elementId),
    legend: [
      { tint: 'teacher', label: 'Chiesto dalla docente' },
      { tint: 'derived', label: 'Derivato da profilo, bisogni o regole' },
    ],
  }
}

/** Elements of the derived spec that no link explains. */
export function untraced(provenance: Provenance, roots: ModelObject[]): string[] {
  return allObjects(roots)
    .map((o) => o.id)
    .filter((id) => !provenance.byElement[id]?.length)
}

/** Looks for the weaving model of a derived spec among the workspace models. */
export async function findProvenance(
  derivedId: string,
  models: ModelHeader[],
  getModel: (id: string) => Promise<ModelDocument>,
): Promise<Provenance | null> {
  for (const header of models.filter((m) => m.metamodel === WEAVING)) {
    const provenance = buildProvenance(await getModel(header.id), derivedId)
    if (provenance) return provenance
  }
  return null
}

export const LINK_LABELS: Record<string, string> = {
  Refinement: 'Ripreso dalla docente',
  Derivation: 'Derivato',
  ConflictFix: 'Conflitto corretto',
  Resolution: 'Soglia del modello di qualità',
}

export const ORIGIN_LABELS: Record<string, string> = {
  TEACHER: 'Intento della docente',
  PROFILE: 'Profilo dell’allievo',
  NEED_CATALOG: 'Catalogo dei bisogni',
  QUALITY_MODEL: 'Modello di qualità',
  RULE_DEFAULT: 'Regola predefinita',
}
