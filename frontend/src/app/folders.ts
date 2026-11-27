import type { ModelHeader } from '../api/types'

/**
 * Folders of the workspace. A folder is a label of the models (`Classe 3/Giulia`), not a place on
 * the disk: it exists as long as a model is in it.
 */
export interface FolderNode {
  /** Last level of the path, shown in the panel. */
  name: string
  /** Whole path, '' for the top of the workspace. */
  path: string
  models: ModelHeader[]
  children: FolderNode[]
}

/** The path as the server keeps it: levels trimmed, no empty ones; '' for no folder. */
export function folderPath(text: string | undefined): string {
  return (text ?? '')
    .split('/')
    .map((level) => level.trim())
    .filter(Boolean)
    .join('/')
}

/** The models arranged by folder; folders in alphabetical order, models in the order given. */
export function folderTree(models: ModelHeader[]): FolderNode {
  const root: FolderNode = { name: '', path: '', models: [], children: [] }
  for (const model of models) {
    let node = root
    for (const level of folderPath(model.folder).split('/').filter(Boolean)) {
      let child = node.children.find((c) => c.name === level)
      if (!child) {
        child = { name: level, path: node.path ? `${node.path}/${level}` : level, models: [], children: [] }
        node.children.push(child)
        node.children.sort((a, b) => a.name.localeCompare(b.name, 'it', { numeric: true }))
      }
      node = child
    }
    node.models.push(model)
  }
  return root
}

/** Every folder in use, parents included, sorted: the suggestions when a model is moved. */
export function folderPaths(models: ModelHeader[]): string[] {
  const paths = new Set<string>()
  for (const model of models) {
    const levels = folderPath(model.folder).split('/').filter(Boolean)
    levels.forEach((_, i) => paths.add(levels.slice(0, i + 1).join('/')))
  }
  return [...paths].sort((a, b) => a.localeCompare(b, 'it', { numeric: true }))
}

export function countModels(node: FolderNode): number {
  return node.models.length + node.children.reduce((sum, child) => sum + countModels(child), 0)
}

const CLOSED_KEY = 'studio.folders.closed'

export function readClosedFolders(): string[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(CLOSED_KEY) ?? '[]')
    return Array.isArray(stored) ? stored.filter((p): p is string => typeof p === 'string') : []
  } catch {
    return []
  }
}

export function writeClosedFolders(paths: string[]): void {
  try {
    localStorage.setItem(CLOSED_KEY, JSON.stringify(paths))
  } catch {
    // the panel still works, it only forgets which folders were closed
  }
}
