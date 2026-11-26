import type { ComponentType } from 'react'
import { GenerationPanel } from './GenerationPanel'
import { IntentBuilder } from './IntentBuilder'
import { LearnerForm } from './LearnerForm'
import { QualityTable } from './QualityTable'

export interface DomainView {
  /** Name of the view in the Diagram / … switch. */
  label: string
  component: ComponentType<{ modelId: string }>
}

/** The form view of a model, when its metamodel (and role) has one; the diagram is always available. */
export function domainView(metamodel: string, role: string | undefined): DomainView | null {
  switch (metamodel) {
    case 'http://music/profile':
      return { label: 'Scheda', component: LearnerForm }
    case 'http://music/exercise':
      return role === 'derived' ? { label: 'Esercizio', component: GenerationPanel } : { label: 'Scheda', component: IntentBuilder }
    case 'http://music/quality':
      return { label: 'Scheda', component: QualityTable }
    default:
      return null
  }
}
