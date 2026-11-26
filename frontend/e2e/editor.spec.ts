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
})
