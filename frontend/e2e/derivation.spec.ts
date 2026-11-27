import { readFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'

const TEACHER = 'rgb(29, 79, 184)'
const DERIVED = 'rgb(30, 107, 58)'

test('profile from the learner form, derivation, provenance colours and generated exercise', async ({ page, request }) => {
  // the catalog of need types is a model of the workspace: here it comes from the fixtures
  const catalog = await request.post('/api/models/import', {
    multipart: { file: { name: 'default.needs.xmi', mimeType: 'text/xml', buffer: readFileSync('../fixtures/giulia/default.needs.xmi') } },
  })
  expect(catalog.ok()).toBeTruthy()
  await page.goto('/')

  // --- the learner profile, written in the form view
  await page.getByRole('button', { name: 'Nuovo profilo' }).click()
  await page.getByLabel('Nome').fill('Profilo e2e')
  await page.getByRole('button', { name: 'Crea', exact: true }).click()
  // the generic editor offers the new classes without any code written for them
  await expect(page.getByRole('region', { name: 'Elementi che puoi aggiungere' }).getByRole('button', { name: 'Slot' })).toBeVisible()
  await page.getByRole('button', { name: 'Scheda' }).click()
  await page.getByRole('button', { name: 'Crea il profilo' }).click()

  await page.getByLabel('Nome').fill('giulia')
  await page.getByLabel('Strumento').selectOption('PIANO')
  await page.getByLabel('Tempo comodo (bpm)').fill('72')
  await page.getByLabel('Nota più grave').selectOption('60')
  await page.getByLabel('Nota più acuta').selectOption('79')

  await page.getByRole('button', { name: 'Valuta ritmo' }).click()
  await page.getByLabel('Livello di Ritmo').fill('2')
  await page.getByLabel('Confidenza su Ritmo').fill('0.7')

  // needs: the form offers the types of the chosen catalog and a field for each of their parameters
  await page.getByLabel('Catalogo dei bisogni', { exact: true }).selectOption({ label: 'default.needs' })
  await page.getByLabel('Tipo di bisogno da aggiungere').selectOption({ label: 'Ipovisione' })
  await page.getByRole('button', { name: 'Aggiungi bisogno' }).click()
  await expect(page.getByLabel('Ipovisione: ingrandimento minimo del pentagramma')).toHaveValue('1.5')

  // a physical limit: the right hand opens a major sixth; the model stores semitones, the form shows names
  await page.getByLabel('Tipo di bisogno da aggiungere').selectOption({ label: 'Limite di apertura della mano' })
  await page.getByRole('button', { name: 'Aggiungi bisogno' }).click()
  await expect(page.getByLabel('Limite di apertura della mano: mano')).toHaveValue('RIGHT')
  await page.getByLabel('Limite di apertura della mano: apertura massima').selectOption({ label: 'sesta maggiore' })
  await expect(page.getByLabel('Limite di apertura della mano: apertura massima')).toHaveValue('9')
  await page.getByLabel('Limite di apertura della mano: misura in cm').fill('16')

  await page.getByRole('button', { name: 'Aggiungi pattern' }).click()
  await page.getByLabel('Pattern 1', { exact: true }).fill('8. 16')
  await page.getByLabel('Stato del pattern 1').selectOption('LEARNING')
  await page.getByRole('button', { name: 'Aggiungi pattern' }).click()
  await page.getByLabel('Pattern 2', { exact: true }).fill('16 8.')
  await page.getByLabel('Stato del pattern 2').selectOption('NOT_INTRODUCED')
  await expect(page.getByRole('img', { name: 'Notazione del pattern 8. 16' }).locator('svg')).toBeVisible()

  // an invalid pattern is reported while typing and has no notation
  await page.getByLabel('Pattern 2', { exact: true }).fill('16 3')
  await expect(page.getByText("token non valido '3'")).toBeVisible()
  await page.getByLabel('Pattern 2', { exact: true }).fill('16 8.')

  await page.screenshot({ path: 'test-results/learner-form.png' })
  await page.getByRole('button', { name: 'Salva', exact: true }).click()
  await expect(page.getByText('«Profilo e2e» salvato.')).toBeVisible()
  await expect(page.getByText('Nessun problema nel modello.')).toBeVisible()

  // --- the other two inputs come from the API: the quality model fixture and a teaching intent
  const imported = await request.post('/api/models/import', {
    multipart: { file: { name: 'piano.quality.xmi', mimeType: 'text/xml', buffer: readFileSync('../fixtures/giulia/piano.quality.xmi') } },
  })
  expect(imported.ok()).toBeTruthy()
  const created = await request.post('/api/models', { data: { metamodel: 'http://music/exercise', name: 'Intento e2e', role: 'intent' } })
  const intent = await created.json()
  const saved = await request.put(`/api/models/${intent.id}`, {
    data: {
      version: intent.version,
      roots: [
        {
          id: 'intent',
          eClass: 'ExerciseSpec',
          attributes: { key: 'G', meter: '4/4', bars: 8, requestedBpm: 90 },
          contents: {
            constraints: [
              {
                id: 'c1',
                eClass: 'ContainsPattern',
                contents: { pattern: [{ id: 'p1', eClass: 'Pattern', attributes: { kind: 'RHYTHM', expression: '8. 16' } }] },
              },
            ],
            qualityGoals: [{ id: 'g1', eClass: 'QualityGoal', attributes: { level: 'MEDIUM' }, references: { dimension: [{ model: 'piano.quality', id: 'RhythmicComplexity' }] } }],
          },
          references: { learner: [{ model: 'profilo-e2e', id: 'learner1' }], qualityModel: [{ model: 'piano.quality', id: 'piano_quality' }] },
        },
      ],
    },
  })
  expect(saved.ok()).toBeTruthy()
  await page.reload()

  // --- derivation
  await page.getByRole('button', { name: 'Deriva un esercizio' }).click()
  await page.getByLabel('Intento didattico', { exact: true }).selectOption({ label: 'Intento e2e' })
  await expect(page.getByLabel('Profilo dell’allievo', { exact: true })).toHaveValue('profilo-e2e')
  // the quality model is not chosen here: it is the one the goals of the intent refer to
  await expect(page.getByLabel('Modello di qualità dell’intento')).toHaveText('piano.quality')

  await expect(page.getByText(/Il tempo richiesto \(90 bpm\) supera il tempo comodo/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Deriva', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Limita il tempo a 72 bpm' }).click()
  await expect(page.getByText('Nessun problema: l’intento è pronto per la derivazione.')).toBeVisible()
  await page.getByRole('button', { name: 'Deriva', exact: true }).click()

  // --- the derived spec opens with provenance colours
  await expect(page.getByRole('tab', { name: /Spec derivata/ })).toBeVisible()
  const node = (text: string) => page.locator('.model-node', { hasText: text })
  await expect(node(': ContainsPattern')).toHaveClass(/is-tint-teacher/)
  await expect(node(': ContainsPattern')).toHaveCSS('border-top-color', TEACHER)
  await expect(node(': ContainsPattern')).toContainText('minOccurrences = 3')
  for (const derived of [': ExcludesPattern', ': RangeConstraint', ': RenderConstraint', ': SpanConstraint']) {
    await expect(node(derived)).toHaveClass(/is-tint-derived/)
    await expect(node(derived)).toHaveCSS('border-top-color', DERIVED)
  }
  await expect(node(': SpanConstraint')).toContainText('maxSimultaneousSemitones = 9')
  await expect(node(': SpanConstraint')).toContainText('hand = RIGHT')
  await expect(page.getByRole('region', { name: 'Elementi che puoi aggiungere' }).getByRole('button', { name: 'SpanConstraint' })).toBeVisible()
  const spec = node(': ExerciseSpec')
  await expect(spec.locator('li', { hasText: 'key = "G"' })).toHaveClass(/is-tint-teacher/)
  await expect(spec.locator('li', { hasText: 'maxBpm = 72' })).toHaveClass(/is-tint-derived/)
  await expect(spec.locator('li', { hasText: 'instrument = PIANO' })).toHaveClass(/is-tint-derived/)
  await expect(page.getByLabel('Legenda dei colori')).toContainText('Chiesto dalla docente')

  // the span constraint is editable in the generated properties panel and traced to the hand limit
  await node(': SpanConstraint').click()
  await expect(page.getByRole('textbox', { name: 'maxStretchInPosition' })).toHaveValue('9')
  await expect(page.getByRole('combobox', { name: /^hand/ })).toHaveValue('RIGHT')
  await expect(page.getByText('Limite di apertura della mano destra: massimo 9 semitoni (sesta maggiore)')).toBeVisible()
  await expect(page.getByText('Regola ApplyNeeds')).toBeVisible()

  // the link names both the value of the profile and the effect of the catalog that used it
  await expect(page.getByRole('button', { name: /need2_maxStretchSemitones\.value/ }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: /HandSpanLimit_span in default\.needs/ })).toBeVisible()
  await expect(page.getByText('Catalogo dei bisogni', { exact: true })).toBeVisible()

  // selecting a derived element explains where it comes from
  await node(': RangeConstraint').click()
  await expect(page.getByText('Regola RangeFromLearner')).toBeVisible()

  await page.screenshot({ path: 'test-results/derived-spec.png' })

  // the same diagram in a smaller window: it stays readable and is panned instead of shrunk
  await page.setViewportSize({ width: 1024, height: 768 })
  await page.getByRole('button', { name: 'Adatta' }).click()
  await page.screenshot({ path: 'test-results/derived-spec-1024.png' })
  await expect(page.getByRole('button', { name: 'Deriva un esercizio' })).toBeInViewport()
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('button', { name: 'Adatta' }).click()

  // a source of the link opens beside the derived spec, with the source element selected
  await page.getByRole('button', { name: /learner1\.lowestMidi/ }).click()
  await expect(page.getByRole('button', { name: 'Chiudi il confronto' })).toBeVisible()
  await expect(page.locator('.pane').nth(1).locator('.model-node.is-selected')).toContainText('learner1 : Learner')
  await page.screenshot({ path: 'test-results/split-view.png' })
  await page.getByRole('button', { name: 'Chiudi il confronto' }).click()

  // the derivation log lists one line per link
  await page.getByRole('button', { name: 'Log di derivazione' }).click()
  await expect(page.locator('.derivation-log li', { hasText: 'TempoLimit' })).toContainText('Conflitto corretto')
  await page.getByRole('button', { name: 'Problemi' }).click()

  // exports: the model as JSON and the diagram as an image
  await page.getByText('Esporta', { exact: true }).click()
  const [json] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Modello JSON' }).click()])
  expect(json.suggestedFilename()).toBe('intento-e2e.derived.json')
  const [png] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Immagine PNG' }).click()])
  expect(png.suggestedFilename()).toBe('intento-e2e.derived.png')
  const [svg] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Immagine SVG' }).click()])
  expect(svg.suggestedFilename()).toBe('intento-e2e.derived.svg')
  const [xmi] = await Promise.all([page.waitForEvent('download'), page.getByRole('link', { name: 'Modello XMI' }).click()])
  expect(xmi.suggestedFilename()).toBe('intento-e2e.derived.xmi')
  await page.getByText('Esporta', { exact: true }).click()

  // --- the stub generation gives a valid exercise
  await page.getByRole('button', { name: 'Esercizio', exact: true }).click()
  await page.getByRole('button', { name: 'Genera esercizio' }).click()
  await expect(page.getByText(/^Valido/)).toBeVisible()
  // the exercise can be listened to
  await expect(page.getByRole('group', { name: 'Ascolto dell’esercizio' }).getByRole('button', { name: 'Ascolta' })).toBeEnabled()
  await expect(page.getByLabel('Velocità')).toHaveValue('100')
  await expect(page.locator('.check-list')).toContainText('Non ci sono note simultanee')
  // each quality goal is measured with the metric of its dimension, as a remark
  await expect(page.locator('.check-list')).toContainText('Complessità ritmica MEDIUM: shortNoteShare vale')
  await expect(page.locator('.check-list')).toContainText('Complessità melodica LOW: meanLeap vale')
  await expect(page.locator('.check-list')).toContainText('Nessun cambio di posizione della mano destra')
  // the sample tune has short bars: a remark, not a failure
  await expect(page.locator('.check-list li.is-note').first()).toContainText('Le battute 2, 4, 6, 7 non durano 4/4')
  await expect(page.getByRole('img', { name: 'Partitura dell’esercizio generato' }).locator('svg')).toBeVisible()
  await page.screenshot({ path: 'test-results/generated-exercise.png' })

  // --- the other form views
  await page.getByRole('button', { name: /^Intento e2e/ }).click()
  await page.getByRole('button', { name: 'Scheda', exact: true }).click()
  await expect(page.getByLabel('Tipo di modello')).toHaveValue('intent')
  await expect(page.getByLabel('Tonalità')).toHaveValue('G')
  await expect(page.getByLabel('Allievo')).toHaveValue('profilo-e2e#learner1')
  await page.getByLabel('Tipo di vincolo da aggiungere').selectOption('ContainsPattern')
  await page.getByRole('button', { name: 'Aggiungi vincolo' }).click()
  await page.getByLabel('Tipo di pattern').last().selectOption('INTERVAL')
  await page.getByLabel('Espressione del pattern').last().fill('+12 x')
  await expect(page.getByText("passo non valido 'x'")).toBeVisible()
  await page.getByLabel('Espressione del pattern').last().fill('+12')
  await expect(page.getByText(/Salto più ampio: ottava/)).toBeVisible()
  await page.getByRole('button', { name: 'Salva', exact: true }).click()
  await expect(page.getByText(/salto di 12 semitoni \(ottava\), oltre l'apertura della mano destra/)).toBeVisible()
  await page.screenshot({ path: 'test-results/leap-warning.png' })
  // from the problem to the rule that raised it, and back to the model
  await page.getByRole('button', { name: 'Vedi la regola' }).first().click()
  await expect(page.getByRole('tab', { name: 'Regole' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.locator('.rule.is-focused')).toHaveCount(1)
  await page.getByRole('tab', { name: 'Intento e2e', exact: true }).click()
  await page.getByLabel('Tipo di vincolo da aggiungere').selectOption('RangeConstraint')
  await page.getByRole('button', { name: 'Aggiungi vincolo' }).click()
  await expect(page.getByRole('heading', { name: 'Estensione delle note' })).toBeVisible()
  await page.getByRole('button', { name: 'Aggiungi obiettivo di qualità' }).click()
  await page.getByRole('group', { name: 'Livello di Complessità melodica' }).getByText('Alta').click()
  // the page itself never scrolls: only the panels do
  expect(await page.evaluate(() => document.scrollingElement?.scrollTop)).toBe(0)
  await page.screenshot({ path: 'test-results/intent-builder.png' })
  await page.getByRole('button', { name: 'Annulla', exact: true }).click()
  await page.getByRole('button', { name: 'Annulla', exact: true }).click()
  await page.getByRole('button', { name: 'Annulla', exact: true }).click()
  // the three edits made after saving are undone; the saved octave leap stays
  await expect(page.getByRole('heading', { name: 'Estensione delle note' })).toHaveCount(0)
  await expect(page.getByLabel('Espressione del pattern').last()).toHaveValue('+12')

  await page.getByRole('button', { name: /^piano\.quality/ }).click()
  await page.getByRole('button', { name: 'Scheda', exact: true }).click()
  await expect(page.locator('.form-warnings')).toHaveCount(0)
  // the dimensions are data of the model: what they are called, how they are measured, what a level means
  await expect(page.getByLabel('Metrica di Complessità ritmica')).toHaveValue('shortNoteShare')
  await expect(page.getByText('Quota di note più brevi di una semiminima')).toBeVisible()
  await expect(page.getByLabel('Complessità ritmica, Medio: frase per il generatore')).toHaveValue('Mix quarter notes with eighth notes.')
  await page.getByLabel('Complessità ritmica, Medio: da').fill('0.2')
  await expect(page.locator('.form-warnings')).toContainText('si sovrappongono')
  await page.getByRole('button', { name: 'Aggiungi dimensione di qualità' }).click()
  await expect(page.getByRole('heading', { name: 'Dimension1' })).toBeVisible()
  await expect(page.getByLabel('Metrica di Dimension1')).toHaveValue('shortNoteShare')
  await page.screenshot({ path: 'test-results/quality-table.png' })
})
