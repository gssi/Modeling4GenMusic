import { expect, test } from '@playwright/test'

test('diagram editing: typed connection by dragging, copy and paste, guarded delete, undo', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Nuova traccia di derivazione' }).click()
  await page.getByLabel('Nome').fill('Traccia e2e')
  await page.getByRole('button', { name: 'Crea', exact: true }).click()

  const palette = page.getByRole('region', { name: 'Elementi che puoi aggiungere' })
  const node = (id: string) => page.locator(`.react-flow__node[data-id="${id}"]`)

  // the palette puts each new object in the only place that can contain it
  await palette.getByRole('button', { name: 'DerivationWeaving' }).click()
  await palette.getByRole('button', { name: 'Derivation', exact: true }).click()
  await palette.getByRole('button', { name: 'SourceEnd' }).click()
  await expect(node('derivationWeaving1')).toBeVisible()
  await expect(node('derivation1')).toContainText('derivation1 : Derivation')
  await expect(node('sourceEnd1')).toBeVisible()
  await expect(page.locator('.model-edge-label', { hasText: 'links' })).toHaveCount(1)
  await expect(page.locator('.model-edge-label', { hasText: 'sources' })).toHaveCount(1)

  // dragging from the link handle of the derivation to the root creates its `target` reference
  // the diagram refits after each addition: let it settle before reading positions
  await page.waitForTimeout(500)
  const handle = node('derivation1').locator('.react-flow__handle.is-link-handle')
  const from = (await handle.boundingBox())!
  const to = (await node('derivationWeaving1').boundingBox())!
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
  await page.mouse.down()
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 12 })
  // while dragging, objects that cannot be linked are dimmed and valid ones are marked
  await expect(node('derivationWeaving1').locator('.model-node')).toHaveClass(/is-valid-target/)
  await page.mouse.up()
  await expect(page.locator('.model-edge-label', { hasText: 'target' })).toHaveCount(1)
  await node('derivation1').click()
  await expect(page.getByRole('button', { name: 'Rimuovi il collegamento a derivationWeaving1' })).toBeVisible()

  // copy and paste duplicate the object with its content, under fresh ids
  await page.keyboard.press('ControlOrMeta+c')
  await page.keyboard.press('ControlOrMeta+v')
  await expect(node('derivation2')).toBeVisible()
  await expect(node('sourceEnd2')).toBeVisible()

  // deleting an object that contains others asks first
  await node('derivation2').click()
  await page.keyboard.press('Delete')
  const dialog = page.getByRole('dialog', { name: 'Eliminare anche il contenuto?' })
  await expect(dialog).toContainText('derivation2')
  await dialog.getByRole('button', { name: 'Annulla' }).click()
  await expect(node('derivation2')).toBeVisible()
  await page.keyboard.press('Delete')
  await dialog.getByRole('button', { name: 'Elimina' }).click()
  await expect(node('derivation2')).toHaveCount(0)
  await expect(node('sourceEnd2')).toHaveCount(0)

  // undo brings it back, redo removes it again
  await page.getByRole('button', { name: 'Annulla', exact: true }).click()
  await expect(node('sourceEnd2')).toBeVisible()
  await page.getByRole('button', { name: 'Ripeti' }).click()
  await expect(node('derivation2')).toHaveCount(0)

  // a leaf is deleted without asking
  await node('sourceEnd1').click()
  await page.keyboard.press('Delete')
  await expect(node('sourceEnd1')).toHaveCount(0)
  await expect(dialog).toHaveCount(0)

  await page.getByRole('button', { name: 'Salva', exact: true }).click()
  await expect(page.getByText('«Traccia e2e» salvato.')).toBeVisible()
  await page.screenshot({ path: 'test-results/editor-interactions.png' })

  // every save leaves a version: the first one, empty, can be brought back as a new version
  await page.getByRole('button', { name: 'Versioni' }).click()
  const versions = page.getByRole('dialog', { name: 'Versioni di «Traccia e2e»' })
  await expect(versions.getByRole('button', { name: /^Versione 2 \(attuale\)/ })).toBeVisible()
  await expect(versions.getByRole('button', { name: /^Versione 1/ })).toHaveAttribute('aria-pressed', 'true')
  await expect(versions.getByText('Non ha')).toBeVisible()
  await expect(versions).toContainText('DerivationWeaving derivationWeaving1')
  await page.screenshot({ path: 'test-results/versions.png' })
  await versions.getByRole('button', { name: 'Ripristina la versione 1' }).click()
  await expect(page.getByText('«Traccia e2e» riportato al contenuto della versione 1.')).toBeVisible()
  await expect(node('derivationWeaving1')).toHaveCount(0)
  await page.getByRole('button', { name: 'Versioni' }).click()
  await expect(versions.getByRole('button', { name: /^Versione 3 \(attuale\)/ })).toContainText('ripristino della versione 1')
  await versions.getByRole('button', { name: 'Chiudi' }).click()
})

test('validation rules can be read and searched', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Regole', exact: true }).click()
  const tempo = page.getByRole('article', { name: 'TempoAboveComfort' })
  await expect(tempo).toContainText('Avviso')
  await expect(tempo).toContainText('self.requestedBpm <= self.learner.comfortableBpm')
  await expect(tempo).toContainText('Correzione rapida')
  await expect(page.getByRole('article', { name: 'HandSpanBounds' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Regole di weaving.evl' })).toBeVisible()

  await page.getByLabel('Cerca tra le regole').fill('LeapBeyond')
  await expect(page.getByRole('article')).toHaveCount(1)
  await expect(page.getByRole('article', { name: 'LeapBeyondReach' })).toBeVisible()
  await page.screenshot({ path: 'test-results/rules.png' })
})

test('a validation script is edited only when it parses and runs, and can go back to the original', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Regole', exact: true }).click()
  const tempo = page.getByRole('article', { name: 'TempoAboveComfort' })
  await expect(tempo).toContainText('Avviso')

  await page.getByRole('button', { name: 'Modifica intent.evl' }).click()
  const text = page.getByLabel('Testo di intent.evl')
  const original = await text.inputValue()
  const save = page.getByRole('button', { name: 'Salva le regole' })
  await expect(save).toBeDisabled()

  // a syntax error is reported with its line and blocks saving
  await text.fill(original.replace('critique TempoAboveComfort {', 'critique TempoAboveComfort'))
  await expect(page.getByText('C’è un errore di sintassi')).toBeVisible()
  await expect(page.getByRole('button', { name: /^riga 7, colonna/ })).toBeVisible()
  await expect(save).toBeDisabled()

  // right syntax but a property that does not exist: the trial run on the models finds it
  await text.fill(original.replace('guard: self.hasLearner()', 'guard: self.noSuchThing > 0'))
  await expect(page.getByText(/eseguite sui modelli del workspace le regole si fermano/)).toBeVisible()
  await expect(save).toBeDisabled()

  // a sound change is saved and shows in the rules
  await text.fill(original.replace('critique TempoAboveComfort', 'constraint TempoAboveComfort'))
  await expect(page.getByText(/Sintassi corretta e prova sui modelli del workspace riuscita/)).toBeVisible()
  await page.screenshot({ path: 'test-results/rules-editor.png' })
  await save.click()
  await expect(page.getByText('Regole di intent.evl salvate.')).toBeVisible()
  await expect(tempo).toContainText('Errore')
  await expect(page.getByRole('region', { name: 'Regole di intent.evl' })).toContainText('modificato')

  await page.getByRole('button', { name: 'Ripristina le regole originali di intent.evl' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Ripristina' }).click()
  await expect(page.getByText('Regole di intent.evl riportate a quelle originali.')).toBeVisible()
  await expect(tempo).toContainText('Avviso')
})

test('the derivation script is shown with its rules and edited under the same checks', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Regole', exact: true }).click()
  const section = page.getByRole('region', { name: 'Regole di derive.etl' })
  await expect(section.getByRole('article', { name: 'CopyIntent' })).toContainText('T!ExerciseSpec')
  await expect(section.getByRole('article', { name: 'SpanFromHandLimit' })).toBeVisible()
  await expect(section.getByRole('article', { name: 'TempoLimit' })).toContainText('dopo le regole')

  await page.getByRole('button', { name: 'Modifica derive.etl' }).click()
  const text = page.getByLabel('Testo di derive.etl')
  const original = await text.inputValue()
  const save = page.getByRole('button', { name: 'Salva le regole' })

  await text.fill(original.replace('rule CopyIntent', 'rul CopyIntent'))
  await expect(page.getByText(/error[ei] di sintassi/)).toBeVisible()
  await expect(save).toBeDisabled()

  await text.fill(original.replace('  s.key = t.key;', '  s.key = t.noSuchThing;'))
  await expect(page.getByText(/eseguite sui modelli del workspace le regole si fermano/)).toBeVisible()
  await expect(page.getByText(/Property 'noSuchThing' not found/).first()).toBeVisible()
  await expect(save).toBeDisabled()

  await text.fill(original.replace('LEARNING_MIN_OCCURRENCES() : Integer { return 3; }', 'LEARNING_MIN_OCCURRENCES() : Integer { return 4; }'))
  await expect(page.getByText(/Sintassi corretta e prova sui modelli del workspace riuscita/)).toBeVisible()
  await save.click()
  await expect(page.getByText('Regole di derive.etl salvate.')).toBeVisible()
  await expect(section).toContainText('modificato')
  await page.screenshot({ path: 'test-results/derivation-rules.png' })

  await page.getByRole('button', { name: 'Ripristina le regole originali di derive.etl' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Ripristina' }).click()
  await expect(page.getByText('Regole di derive.etl riportate a quelle originali.')).toBeVisible()
  await expect(section).not.toContainText('modificato')
})

test('without a language model the rule assistant says what is missing and the editor still works', async ({ page }) => {
  // the availability is whatever the machine running the test has: the answer is fixed here
  await page.route('**/api/assistant?*', (route) =>
    route.fulfill({ json: { available: false, model: 'un-modello', reason: 'Ollama non risponde: avvialo con «ollama serve».' } }),
  )
  await page.goto('/')
  await page.getByRole('button', { name: 'Regole', exact: true }).click()
  await page.getByRole('button', { name: 'Modifica profile.evl' }).click()
  const assistant = page.getByRole('region', { name: 'Assistente per le regole' })
  await expect(assistant).toContainText('L’assistente non è disponibile. Ollama non risponde')
  await expect(assistant.getByRole('button', { name: 'Proponi la regola' })).toHaveCount(0)
  await expect(page.getByLabel('Testo di profile.evl')).toBeEditable()
})

test('a described rule is proposed with what it would report, then inserted to be read and saved by hand', async ({ page }) => {
  const rule = 'context L!Learner {\n\n  critique SlowLearner {\n    check: self.comfortableBpm >= 40\n    message: "Tempo comodo molto basso"\n  }\n}'
  await page.route('**/api/assistant?*', (route) => route.fulfill({ json: { available: true, model: 'un-modello', reason: null } }))
  await page.route('**/api/assistant/rules?*', async (route) => {
    const { source, description } = route.request().postDataJSON()
    const base = source.trimEnd()
    await route.fulfill({
      json: {
        rule,
        source: `${base}\n\n// ${description}\n${rule}\n`,
        line: base.split('\n').length + 2,
        problems: [],
        failures: [],
        attempts: 1,
        seconds: 1.2,
        model: 'un-modello',
        effects: { models: 1, reports: [] },
      },
    })
  })
  await page.goto('/')
  await page.getByRole('button', { name: 'Regole', exact: true }).click()
  await page.getByRole('button', { name: 'Modifica profile.evl' }).click()
  const assistant = page.getByRole('region', { name: 'Assistente per le regole' })
  await assistant.getByLabel('Descrizione della regola').fill('Avvisa se il tempo comodo è sotto i 40 bpm')
  await assistant.getByRole('button', { name: 'Proponi la regola' }).click()
  await expect(assistant).toContainText('critique SlowLearner')
  await expect(assistant).toContainText('La proposta ha sintassi corretta e gira sui modelli del workspace.')
  await expect(assistant).toContainText('Provata su un modello del workspace, non segnala nulla.')
  await page.screenshot({ path: 'test-results/rule-assistant.png' })

  // nothing changes until the proposal is put in the text; from there the usual checks and saving apply
  const text = page.getByLabel('Testo di profile.evl')
  await expect(text).not.toHaveValue(/SlowLearner/)
  await assistant.getByRole('button', { name: 'Inserisci nel testo' }).click()
  await expect(text).toHaveValue(/\/\/ Avvisa se il tempo comodo è sotto i 40 bpm\ncontext L!Learner \{\n\n {2}critique SlowLearner/)
  await expect(page.getByText(/Sintassi corretta e prova sui modelli del workspace riuscita: 6 regole/)).toBeVisible()
  await page.getByRole('button', { name: 'Salva le regole' }).click()
  await expect(page.getByRole('article', { name: 'SlowLearner' })).toContainText('Avviso')

  await page.getByRole('button', { name: 'Ripristina le regole originali di profile.evl' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Ripristina' }).click()
  await expect(page.getByRole('article', { name: 'SlowLearner' })).toHaveCount(0)
})
