import type { MetamodelDescriptor, ModelObject } from '../api/types'
import type { Metamodels } from './metamodel'

/** Shared test data: a cut of the learner profile and exercise metamodels, and Giulia's models. */

const PROFILE = 'http://music/profile'
const EXERCISE = 'http://music/exercise'

const attr = (name: string, type: 'string' | 'int' | 'double' | 'enum' = 'string', lower = 0) => ({
  name,
  type,
  lower,
  upper: 1,
  ...(type === 'enum' ? { literals: ['A', 'B'] } : {}),
})

const profile: MetamodelDescriptor = {
  nsURI: PROFILE,
  prefix: 'pr',
  name: 'profile',
  enums: [],
  classes: [
    {
      name: 'Learner',
      abstract: false,
      supertypes: [],
      attributes: [attr('name', 'string', 1), attr('instrument', 'enum'), attr('comfortableBpm', 'int')],
      references: [
        { name: 'skills', target: { metamodel: PROFILE, name: 'Skill' }, containment: true, lower: 0, upper: -1 },
        { name: 'needs', target: { metamodel: PROFILE, name: 'Need' }, containment: true, lower: 0, upper: -1 },
        { name: 'patterns', target: { metamodel: PROFILE, name: 'PatternMastery' }, containment: true, lower: 0, upper: -1 },
      ],
    },
    { name: 'Skill', abstract: false, supertypes: [], attributes: [attr('area', 'enum'), attr('level', 'int')], references: [] },
    {
      name: 'Need',
      abstract: false,
      supertypes: [],
      attributes: [],
      references: [{ name: 'slots', target: { metamodel: PROFILE, name: 'Slot' }, containment: true, lower: 0, upper: -1 }],
    },
    { name: 'Slot', abstract: false, supertypes: [], attributes: [attr('value')], references: [] },
    { name: 'PatternMastery', abstract: false, supertypes: [], attributes: [attr('expression'), attr('status', 'enum')], references: [] },
  ],
}

const exercise: MetamodelDescriptor = {
  nsURI: EXERCISE,
  prefix: 'ex',
  name: 'exercise',
  enums: [],
  classes: [
    {
      name: 'ExerciseSpec',
      abstract: false,
      supertypes: [],
      attributes: [attr('key'), attr('bars', 'int')],
      references: [
        { name: 'learner', target: { metamodel: PROFILE, name: 'Learner' }, containment: false, lower: 0, upper: 1 },
        { name: 'constraints', target: { metamodel: EXERCISE, name: 'Constraint' }, containment: true, lower: 0, upper: -1 },
        { name: 'related', target: { metamodel: EXERCISE, name: 'Constraint' }, containment: false, lower: 0, upper: 1 },
      ],
    },
    { name: 'Constraint', abstract: true, supertypes: [], attributes: [attr('strength', 'enum')], references: [] },
    {
      name: 'ContainsPattern',
      abstract: false,
      supertypes: [{ metamodel: EXERCISE, name: 'Constraint' }],
      attributes: [attr('strength', 'enum'), attr('minOccurrences', 'int')],
      references: [{ name: 'pattern', target: { metamodel: EXERCISE, name: 'Pattern' }, containment: true, lower: 1, upper: 1 }],
    },
    {
      name: 'ExcludesPattern',
      abstract: false,
      supertypes: [{ metamodel: EXERCISE, name: 'Constraint' }],
      attributes: [attr('strength', 'enum')],
      references: [{ name: 'pattern', target: { metamodel: EXERCISE, name: 'Pattern' }, containment: true, lower: 1, upper: 1 }],
    },
    { name: 'Pattern', abstract: false, supertypes: [], attributes: [attr('expression')], references: [] },
  ],
}

export const metamodels: Metamodels = { [PROFILE]: profile, [EXERCISE]: exercise }
export { PROFILE, EXERCISE, profile, exercise }

const leaf = (id: string, eClass: string, attributes: ModelObject['attributes'] = {}): ModelObject => ({
  id,
  eClass,
  attributes,
  contents: {},
  references: {},
})

export const giuliaProfile: ModelObject[] = [
  {
    id: 'giulia',
    eClass: 'Learner',
    attributes: { name: 'giulia', instrument: 'PIANO', comfortableBpm: 72, lowestMidi: 60, highestMidi: 79 },
    contents: {
      skills: [
        leaf('skill_rhythm', 'Skill', { area: 'RHYTHM', level: 2, confidence: 0.7 }),
        leaf('skill_pitch', 'Skill', { area: 'PITCH_READING', level: 3, confidence: 0.8 }),
      ],
      needs: [
        {
          id: 'need_lowvision',
          eClass: 'Need',
          attributes: {},
          contents: { slots: [leaf('need_lowvision_minStaffScale', 'Slot', { value: '1.5' })] },
          references: {},
        },
      ],
      patterns: [
        leaf('pm_quarter', 'PatternMastery', { expression: '4', status: 'MASTERED' }),
        leaf('pm_eighths', 'PatternMastery', { expression: '8 8', status: 'MASTERED' }),
        leaf('pm_dotted', 'PatternMastery', { expression: '8. 16', status: 'LEARNING' }),
        leaf('pm_reverse_dotted', 'PatternMastery', { expression: '16 8.', status: 'NOT_INTRODUCED' }),
      ],
    },
    references: {},
  },
]

export const giuliaIntent: ModelObject[] = [
  {
    id: 'intent',
    eClass: 'ExerciseSpec',
    attributes: { key: 'G', meter: '4/4', bars: 8, requestedBpm: 90 },
    contents: {
      constraints: [
        {
          id: 'c1',
          eClass: 'ContainsPattern',
          attributes: { strength: 'HARD', minOccurrences: 1 },
          contents: { pattern: [leaf('p1', 'Pattern', { expression: '8. 16' })] },
          references: {},
        },
        leaf('c2', 'ExcludesPattern', { strength: 'HARD' }),
      ],
    },
    references: { learner: [{ model: 'giulia.profile', id: 'giulia' }], related: [{ id: 'c1' }] },
  },
]
