#!/usr/bin/env node
/**
 * Comprueba la demo publicada (o una copia local) contra el Worker: recorre todas las pantallas,
 * abre detalles y pestañas, y lista las consultas que el Worker no sabe responder.
 *   node scripts/check.mjs [url de la app]   (por defecto https://www.pedrogomez.dev/proyecto/pera-erp/app)
 */
import { chromium } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const APP = (process.argv[2] ?? 'https://www.pedrogomez.dev/proyecto/pera-erp/app').replace(/\/$/, '')
const mobile = process.argv.includes('--mobile')
const routes = [...readFileSync(resolve(here, '../../../frontend/src/routing/routes.tsx'), 'utf8').matchAll(/path: '(\/[^']*)'/g)].map((match) => match[1])

const browser = await chromium.launch({ channel: 'chrome' })
const viewport = mobile ? { width: 390, height: 844 } : { width: 1440, height: 900 }
const page = await (await browser.newContext({ viewport, isMobile: mobile, hasTouch: mobile, locale: 'es-ES' })).newPage()
const missing = new Map()
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
page.on('response', (response) => {
  const url = new URL(response.url())
  if (!url.pathname.includes('/api/')) return
  if (response.status() >= 400 && response.request().method() === 'GET') missing.set(`${response.status()} ${url.pathname}${url.search}`, page.url())
})
const settle = async (ms = 700) => { await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => undefined); await page.waitForTimeout(ms) }

let blankPages = 0
for (const path of routes) {
  await page.goto(`${APP}${path}`)
  await settle(1000)
  const text = (await page.locator('main, #root').first().innerText().catch(() => '')).trim()
  if (text.length < 40 || /no está disponible/.test(text)) { blankPages += 1; console.log(`  pantalla vacía o sin demo: ${path}`) }
  const rows = Math.min(3, await page.locator('tr.clickable-row').count())
  for (let index = 0; index < rows; index += 1) {
    await page.locator('tr.clickable-row').nth(index).click({ timeout: 3000 }).catch(() => undefined)
    await settle(500)
    await page.keyboard.press('Escape').catch(() => undefined)
  }
  const tabs = Math.min(10, await page.locator('.workspace-tabs button, [role="tab"]').count())
  for (let index = 0; index < tabs; index += 1) {
    await page.locator('.workspace-tabs button, [role="tab"]').nth(index).click({ timeout: 3000 }).catch(() => undefined)
    await settle(500)
  }
}
await browser.close()
console.log(JSON.stringify({ pantallas: routes.length, vacias: blankPages, consultasSinRespuesta: missing.size, errores: errors }, null, 1))
for (const [request, at] of missing) console.log(`  ${request}  (${at})`)
