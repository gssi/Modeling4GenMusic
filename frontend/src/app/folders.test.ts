import { describe, expect, it } from 'vitest'
import type { ModelHeader } from '../api/types'
import { countModels, folderPath, folderPaths, folderTree } from './folders'

const model = (id: string, folder?: string): ModelHeader => ({ id, name: id, metamodel: 'http://music/profile', version: 1, folder })

describe('workspace folders', () => {
  const models = [model('a'), model('b', 'Esperimento/C2'), model('c', 'Giulia'), model('d', 'Esperimento/C10'), model('e', 'Esperimento'), model('f', ' Esperimento / C2 ')]

  it('normalises a path', () => {
    expect(folderPath(' Classe 3 // Pianoforte/ ')).toBe('Classe 3/Pianoforte')
    expect(folderPath('  ')).toBe('')
    expect(folderPath(undefined)).toBe('')
  })

  it('arranges the models by folder, keeping the ones without a folder at the top', () => {
    const tree = folderTree(models)
    expect(tree.models.map((m) => m.id)).toEqual(['a'])
    expect(tree.children.map((c) => c.path)).toEqual(['Esperimento', 'Giulia'])
    const experiment = tree.children[0]
    expect(experiment.models.map((m) => m.id)).toEqual(['e'])
    // numbers sort as numbers: C2 before C10
    expect(experiment.children.map((c) => [c.name, c.path, c.models.map((m) => m.id)])).toEqual([
      ['C2', 'Esperimento/C2', ['b', 'f']],
      ['C10', 'Esperimento/C10', ['d']],
    ])
    expect(countModels(experiment)).toBe(4)
    expect(countModels(tree)).toBe(6)
  })

  it('lists the folders in use, parents included', () => {
    expect(folderPaths(models)).toEqual(['Esperimento', 'Esperimento/C2', 'Esperimento/C10', 'Giulia'])
    expect(folderPaths([model('a')])).toEqual([])
  })
})
