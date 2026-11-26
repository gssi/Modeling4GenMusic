import type { MetamodelSummary } from '../api/types'

/** One hue per metamodel, assigned by position so that no metamodel is named here. */
const METAMODEL_COLORS = ['#1f6f78', '#8a3f7c', '#93650a', '#4b5670', '#3d6b2f', '#a04a2a']

export function metamodelColor(summaries: MetamodelSummary[], nsURI: string): string {
  const index = Math.max(0, summaries.findIndex((s) => s.nsURI === nsURI))
  return METAMODEL_COLORS[index % METAMODEL_COLORS.length]
}
