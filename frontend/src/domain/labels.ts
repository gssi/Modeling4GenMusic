/**
 * Domain wording for the generic editor: how each metamodel is called in the workspace and
 * which roles its models can play. Everything here is optional: an unknown metamodel falls
 * back to its package name and has no roles.
 */

export interface RoleOption {
  value: string
  label: string
}

interface MetamodelWording {
  /** Group title in the workspace panel. */
  plural: string
  /** Label of the action that creates a model of this kind. */
  create: string
  roles?: RoleOption[]
}

const WORDING: Record<string, MetamodelWording> = {
  'http://music/profile': { plural: 'Profili degli allievi', create: 'Nuovo profilo' },
  'http://music/needs': { plural: 'Cataloghi dei bisogni', create: 'Nuovo catalogo dei bisogni' },
  'http://music/exercise': {
    plural: 'Esercizi',
    create: 'Nuovo esercizio',
    roles: [
      { value: 'intent', label: 'Intento didattico' },
      { value: 'derived', label: 'Spec derivata' },
    ],
  },
  'http://music/quality': { plural: 'Modelli di qualità', create: 'Nuovo modello di qualità' },
  'http://music/weaving': { plural: 'Tracce di derivazione', create: 'Nuova traccia di derivazione' },
}

const RULE_EXAMPLES: Record<string, string> = {
  'profile.evl': 'avvisa se il tempo comodo dell’allievo è sotto i 40 bpm',
  'needs.evl': 'avvisa se un tipo di bisogno non ha una descrizione',
  'intent.evl': 'avvisa se l’esercizio ha più di 16 battute',
  'quality.evl': 'avvisa se un modello di qualità non ha nessun binding',
  'weaving.evl': 'avvisa se un link non ha una motivazione scritta',
  'derive.etl': 'dopo le regole, se la specifica ha più di 12 battute riducile a 12 e registra il motivo nella traccia',
}

/** A request one could make to the rule assistant for a script, to show how to phrase it. */
export function ruleExample(script: string): string | undefined {
  return RULE_EXAMPLES[script]
}

export function wording(nsURI: string, fallbackName: string): MetamodelWording {
  return WORDING[nsURI] ?? { plural: fallbackName, create: `Nuovo modello ${fallbackName}` }
}

export function roleLabel(nsURI: string, role: string | undefined): string | undefined {
  if (!role) return undefined
  return WORDING[nsURI]?.roles?.find((r) => r.value === role)?.label ?? role
}

export function roleOptions(nsURI: string): RoleOption[] {
  return WORDING[nsURI]?.roles ?? []
}
