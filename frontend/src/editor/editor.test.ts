import { describe, expect, it } from 'vitest'
import { canStartConnection, connectableReferences, containerSlots } from './connections'
import { projectGraph, proxyNodeId } from './graph'
import { autoLayout, nodeSize, placeNewNode, pushApart } from './layout'
import { canBeRoot, conformsTo, instantiate } from './metamodel'
import {
  addChild,
  addReference,
  allObjects,
  cloneWithNewIds,
  locate,
  removeObjects,
  renameId,
  setAttribute,
  uniqueId,
} from './modelOps'
import { EXERCISE, PROFILE, exercise, giuliaIntent, giuliaProfile, metamodels, profile } from './testModel'

const object = (roots: typeof giuliaIntent, id: string) => locate(roots, id)!.object

describe('metamodel rules', () => {
  it('accepts subclasses and any class where EObject is expected', () => {
    const lowVision = { metamodel: PROFILE, name: 'LowVision' }
    expect(conformsTo(metamodels, lowVision, { metamodel: PROFILE, name: 'Need' })).toBe(true)
    expect(conformsTo(metamodels, lowVision, { metamodel: PROFILE, name: 'Skill' })).toBe(false)
    expect(conformsTo(metamodels, lowVision, { metamodel: 'http://www.eclipse.org/emf/2002/Ecore', name: 'EObject' })).toBe(true)
  })

  it('lets only uncontained classes be roots', () => {
    expect(canBeRoot(metamodels, profile, 'Learner')).toBe(true)
    expect(canBeRoot(metamodels, profile, 'LowVision')).toBe(false)
    expect(canBeRoot(metamodels, exercise, 'Pattern')).toBe(false)
  })

  it('creates objects with enum defaults', () => {
    const skill = instantiate(profile.classes.find((c) => c.name === 'Skill')!, 'skill1')
    expect(skill.attributes).toEqual({ area: 'A' })
  })
})

describe('model operations', () => {
  it('never mutate their input', () => {
    const before = JSON.stringify(giuliaProfile)
    setAttribute(giuliaProfile, 'giulia', 'comfortableBpm', 80)
    removeObjects(giuliaProfile, ['skill_pitch'])
    renameId(giuliaProfile, 'giulia', 'g')
    expect(JSON.stringify(giuliaProfile)).toBe(before)
  })

  it('generates readable unique ids', () => {
    expect(uniqueId(giuliaProfile, 'Skill')).toBe('skill1')
    const withOne = addChild(giuliaProfile, 'giulia', 'skills', { id: 'skill1', eClass: 'Skill', attributes: {}, contents: {}, references: {} })
    expect(uniqueId(withOne, 'Skill')).toBe('skill2')
  })

  it('removes a subtree together with the references to it', () => {
    const roots = removeObjects(giuliaIntent, ['c1'])
    expect(allObjects(roots).map((o) => o.id)).toEqual(['intent', 'c2'])
    expect(object(roots, 'intent').references).toEqual({ learner: [{ model: 'giulia.profile', id: 'giulia' }] })
  })

  it('renames an id everywhere and refuses a taken one', () => {
    const roots = renameId(giuliaIntent, 'c1', 'dotted')!
    expect(object(roots, 'intent').references.related).toEqual([{ id: 'dotted' }])
    expect(renameId(giuliaIntent, 'c1', 'c2')).toBeNull()
    expect(renameId(giuliaIntent, 'c1', 'two words')).toBeNull()
  })

  it('copies a subtree with fresh ids', () => {
    const copy = cloneWithNewIds(giuliaIntent, object(giuliaIntent, 'c1'))
    expect(copy.id).toBe('containsPattern1')
    expect(copy.contents.pattern[0].id).toBe('pattern1')
    expect(copy.contents.pattern[0].attributes).toEqual({ expression: '8. 16' })
  })
})

describe('typed connections', () => {
  const spec = object(giuliaIntent, 'intent')

  it('offers only references whose type fits and that have room', () => {
    const empty = { ...spec, references: {} }
    expect(connectableReferences(metamodels, EXERCISE, empty, object(giuliaIntent, 'c2')).map((r) => r.name)).toEqual(['related'])
    expect(connectableReferences(metamodels, EXERCISE, empty, object(giuliaIntent, 'p1'))).toEqual([])
    // `related` holds one element and is already taken by c1
    expect(connectableReferences(metamodels, EXERCISE, spec, object(giuliaIntent, 'c2'))).toEqual([])
  })

  it('knows when no connection can start from an object', () => {
    expect(canStartConnection(metamodels, EXERCISE, giuliaIntent, spec)).toBe(false)
    const freed = addReference(removeObjects(giuliaIntent, ['c1']), 'intent', 'learner', { model: 'x', id: 'y' })
    expect(canStartConnection(metamodels, EXERCISE, freed, object(freed, 'intent'))).toBe(true)
    expect(canStartConnection(metamodels, EXERCISE, giuliaIntent, object(giuliaIntent, 'c1'))).toBe(false)
  })

  it('finds the containers with room for a new object', () => {
    expect(containerSlots(metamodels, PROFILE, giuliaProfile, 'LowVision').map((s) => `${s.parent.id}.${s.reference.name}`)).toEqual(['giulia.needs'])
    // c1 already has its single pattern, c2 has none
    expect(containerSlots(metamodels, EXERCISE, giuliaIntent, 'Pattern').map((s) => s.parent.id)).toEqual(['c2'])
    expect(containerSlots(metamodels, PROFILE, [], 'Skill')).toEqual([])
  })
})

describe('diagram projection', () => {
  it('draws objects, containments, references and proxies of other models', () => {
    const graph = projectGraph(giuliaIntent, {
      ContainsPattern: { order: ['minOccurrences', 'strength'], enums: new Set(['strength']) },
    })
    expect(graph.nodes.map((n) => n.id)).toEqual(['intent', 'c1', 'p1', 'c2', proxyNodeId('giulia.profile', 'giulia')])
    expect(graph.nodes[0].title).toBe('intent : ExerciseSpec')
    expect(graph.nodes[0].rows).toContain('key = "G"')
    expect(graph.nodes[1].rows).toEqual(['minOccurrences = 1', 'strength = HARD'])
    expect(graph.edges.map((e) => `${e.kind}:${e.source}-${e.label}->${e.target}`)).toEqual([
      'external:intent-learner->proxy:giulia.profile#giulia',
      'reference:intent-related->c1',
      'containment:intent-constraints->c1',
      'containment:c1-pattern->p1',
      'containment:intent-constraints->c2',
    ])
  })
})

describe('automatic layout', () => {
  const overlaps = (a: Box, b: Box) => a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
  interface Box {
    x: number
    y: number
    width: number
    height: number
  }

  for (const [name, roots] of [
    ['profile', giuliaProfile],
    ['intent', giuliaIntent],
  ] as const) {
    it(`places every node of Giulia's ${name} without overlaps, contents to the right of their container`, async () => {
      const graph = projectGraph(roots)
      const positions = await autoLayout(graph)
      const boxes = graph.nodes.map((n) => ({ id: n.id, ...positions[n.id], ...nodeSize(n) }))
      expect(boxes.every((b) => Number.isFinite(b.x) && Number.isFinite(b.y))).toBe(true)
      for (let i = 0; i < boxes.length; i++) {
        for (let j = i + 1; j < boxes.length; j++) {
          expect(overlaps(boxes[i], boxes[j]), `${boxes[i].id} overlaps ${boxes[j].id}`).toBe(false)
        }
      }
      for (const edge of graph.edges.filter((e) => e.kind === 'containment')) {
        const parent = boxes.find((b) => b.id === edge.source)!
        const child = boxes.find((b) => b.id === edge.target)!
        expect(child.x).toBeGreaterThanOrEqual(parent.x + parent.width)
      }
    })
  }

  it('places a node added later clear of the existing ones', async () => {
    const graph = projectGraph(giuliaProfile)
    const positions = await autoLayout(graph)
    const grown = projectGraph(addChild(giuliaProfile, 'giulia', 'skills', { id: 'skill1', eClass: 'Skill', attributes: {}, contents: {}, references: {} }))
    const added = grown.nodes.find((n) => n.id === 'skill1')!
    const box = { ...placeNewNode(grown, positions, 'skill1', 'giulia'), ...nodeSize(added) }
    for (const node of graph.nodes) {
      expect(overlaps(box, { ...positions[node.id], ...nodeSize(node) }), `overlaps ${node.id}`).toBe(false)
    }
  })

  it('pushes nodes down when one above them grows', async () => {
    const positions = await autoLayout(projectGraph(giuliaProfile))
    const grown = projectGraph(
      giuliaProfile.map((l) => ({
        ...l,
        contents: { ...l.contents, skills: l.contents.skills.map((s) => ({ ...s, attributes: { ...s.attributes, a: 1, b: 2, c: 3 } })) },
      })),
    )
    const settled = pushApart(grown, positions)
    const boxes = grown.nodes.map((n) => ({ id: n.id, ...settled[n.id], ...nodeSize(n) }))
    for (let i = 0; i < boxes.length; i++) {
      for (let j = i + 1; j < boxes.length; j++) {
        expect(overlaps(boxes[i], boxes[j]), `${boxes[i].id} overlaps ${boxes[j].id}`).toBe(false)
      }
    }
    expect(settled.giulia).toEqual(positions.giulia)
    expect(pushApart(projectGraph(giuliaProfile), positions)).toBe(positions)
  })

  it('sizes nodes from their text', () => {
    const [learner] = projectGraph(giuliaProfile).nodes
    expect(nodeSize(learner).height).toBe(36 + 5 * 22 + 12)
    expect(nodeSize(learner).width).toBeGreaterThanOrEqual(168)
  })
})
