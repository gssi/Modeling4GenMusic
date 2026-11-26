// Makes a PNG copy, at twice the size, of the SVG diagrams given as arguments.
// It uses the headless browser already installed for the frontend end-to-end tests:
//
//   cd frontend && node ../metamodels/svg_to_png.mjs ../metamodels/img/profile.svg ../metamodels/img/exercise.svg
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

const { chromium } = createRequire(resolve(process.cwd(), 'package.json'))('@playwright/test')

const browser = await chromium.launch()
for (const file of process.argv.slice(2)) {
  const svg = readFileSync(file, 'utf8')
  const [, width, height] = svg.match(/<svg[^>]*width="([\d.]+)" height="([\d.]+)"/)
  const page = await browser.newPage({ viewport: { width: Math.ceil(width), height: Math.ceil(height) }, deviceScaleFactor: 2 })
  await page.setContent(`<body style="margin:0">${svg}</body>`)
  const target = file.replace(/\.svg$/, '.png')
  await page.screenshot({ path: target })
  await page.close()
  console.log(target)
}
await browser.close()
