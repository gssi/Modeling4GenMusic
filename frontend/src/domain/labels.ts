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

export function wording(nsURI: string, fallbackName: string): MetamodelWording {
  return WORDING[nsURI] ?? { plural: fallbackName, create: `Nuovo modello ${fallbackName}` }
}

export function roleLabel(nsURI: string, role: string | undefined): string | undefined {
  if (!role) return undefined
  return WORDING[nsURI]?.roles?.find((r) => r.value === role)?.label ?? role
}
