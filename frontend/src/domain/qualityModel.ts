import type { ModelDocument, ModelObject } from '../api/types'

/**
 * Reading of a quality model: the dimensions along which an exercise can be asked to be simple or
 * demanding. They are data of the model, so the forms show whatever dimensions it holds.
 */

export const QUALITY = 'http://music/quality'

export interface QualityDimension {
  id: string
  name: string
  label: string
  description?: string
  metric?: string
}

export interface QualityModel {
  model: string
  id: string
  dimensions: QualityDimension[]
}

const text = (object: ModelObject, name: string): string | undefined => {
  const value = object.attributes[name]
  return value === undefined || value === '' ? undefined : String(value)
}

/** The quality model a document holds, or null when the document is not one. */
export function readQualityModel(doc: ModelDocument): QualityModel | null {
  const root = doc.roots.find((r) => r.eClass === 'QualityModel')
  if (!root) return null
  return {
    model: doc.id,
    id: root.id,
    dimensions: (root.contents.dimensions ?? []).map((dimension) => ({
      id: dimension.id,
      name: text(dimension, 'name') ?? dimension.id,
      label: text(dimension, 'label') ?? text(dimension, 'name') ?? dimension.id,
      description: text(dimension, 'description'),
      metric: text(dimension, 'metric'),
    })),
  }
}

/** The dimension a goal is about, undefined when the goal points outside the quality model. */
export function dimensionOf(goal: ModelObject, quality: QualityModel | null): QualityDimension | undefined {
  const ref = goal.references.dimension?.[0]
  return ref && quality && ref.model === quality.model ? quality.dimensions.find((d) => d.id === ref.id) : undefined
}

/** The dimensions no goal is about yet: one goal for each dimension at most. */
export function freeDimensions(goals: ModelObject[], quality: QualityModel | null): QualityDimension[] {
  if (!quality) return []
  const used = new Set(goals.map((goal) => dimensionOf(goal, quality)?.id))
  return quality.dimensions.filter((d) => !used.has(d.id))
}

export function newGoal(id: string, quality: QualityModel, dimension: QualityDimension, level: string): ModelObject {
  return { id, eClass: 'QualityGoal', attributes: { level }, contents: {}, references: { dimension: [{ model: quality.model, id: dimension.id }] } }
}

/** A name for a new dimension that no other dimension of the model has. */
export function newDimensionName(taken: string[]): string {
  for (let i = 1; ; i++) {
    const candidate = `Dimension${i}`
    if (!taken.includes(candidate)) return candidate
  }
}
