#!/usr/bin/env node
/**
 * Graba la demo local de PERA para servirla desde Cloudflare.
 *
 * Recorre la aplicación en modo demo (la del portfolio, servida en local con el proxy de la API)
 * con Chrome, pantalla por pantalla, pestañas y detalles incluidos, y guarda cada respuesta GET de
 * la API. Después pide directamente a la pasarela el detalle de cada registro que aparece en los
 * listados, para que en la demo publicada se pueda abrir cualquier factura, cliente o recibo.
 *
 * Resultado: public/snapshot/index.json (qué respuesta corresponde a cada petición) y un fichero
 * por respuesta. El Worker (src/index.js) los sirve como Static Assets.
 *
 * Requisitos: PERA arrancado con la demo cargada (scripts/start-local.ps1 -Demo y
 * scripts/seed-demo-portfolio.mjs) y la vista local del portfolio (node scripts/serve-local.mjs).
 *   npm run record
 * Variables: PERA_DEMO_APP (http://127.0.0.1:8010/proyecto/pera-erp/app) y PERA_API_URL
 * (pasarela, http://localhost:8080).
 */
import { chromium } from '@playwright/test'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const APP = (process.env.PERA_DEMO_APP ?? 'http://127.0.0.1:8010/proyecto/pera-erp/app').replace(/\/$/, '')
const GATEWAY = (process.env.PERA_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const API_MARK = '/proyecto/pera-erp/api/'
const OUT = resolve(here, '../public/snapshot')
const ROUTES_FILE = resolve(here, '../../../frontend/src/routing/routes.tsx')
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi

const records = new Map()
let loginBody = null

function keyOf(method, path, search) {
  const params = [...new URLSearchParams(search)].sort(([a], [b]) => a.localeCompare(b))
  return `${method} ${path}${params.length ? `?${new URLSearchParams(params)}` : ''}`
}

function remember(method, path, search, status, type, body) {
  const key = keyOf(method, path, search)
  if (records.has(key) && records.get(key).status === 200) return
  records.set(key, { method, path, search, status, type, body })
}

// ------------------------------------------------------------------ 1. recorrido con el navegador

const routes = [...readFileSync(ROUTES_FILE, 'utf8').matchAll(/path: '(\/[^']*)'/g)].map((match) => match[1])
const browser = await chromium.launch({ channel: 'chrome' })

async function crawl(viewport, isMobile, paths) {
  const context = await browser.newContext({ viewport, isMobile, hasTouch: isMobile, locale: 'es-ES' })
  const page = await context.newPage()
  page.on('response', async (response) => {
    const url = new URL(response.url())
    if (!url.pathname.includes(API_MARK)) return
    const method = response.request().method()
    const path = '/api/' + url.pathname.split(API_MARK)[1]
    try {
      const body = await response.body()
      if (method === 'POST' && path === '/api/v1/auth/demo') { loginBody ??= body.toString('utf8'); return }
      if (method !== 'GET') return
      remember(method, path, url.search, response.status(), response.headers()['content-type'] ?? 'application/json', body)
    } catch { /* respuesta sin cuerpo */ }
  })
  const settle = async (ms = 900) => { await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => undefined); await page.waitForTimeout(ms) }
  const clickAll = async (selector, limit = 12) => {
    const count = Math.min(limit, await page.locator(selector).count())
    for (let index = 0; index < count; index += 1) {
      const target = page.locator(selector).nth(index)
      if (!(await target.isVisible().catch(() => false))) continue
      await target.click({ timeout: 3000 }).catch(() => undefined)
      await settle(500)
    }
  }
  const openRows = async (limit = 3) => {
    const count = Math.min(limit, await page.locator('tr.clickable-row').count())
    for (let index = 0; index < count; index += 1) {
      await page.locator('tr.clickable-row').nth(index).click({ timeout: 3000 }).catch(() => undefined)
      await settle(700)
      await page.keyboard.press('Escape').catch(() => undefined)
      await settle(300)
    }
  }

  for (const path of paths) {
    await page.goto(`${APP}${path === '/' ? '/' : path}`)
    await settle(1200)
    await openRows()
    const tabs = await page.locator('.workspace-tabs button, [role="tab"]').count()
    for (let index = 0; index < Math.min(tabs, 10); index += 1) {
      await page.locator('.workspace-tabs button, [role="tab"]').nth(index).click({ timeout: 3000 }).catch(() => undefined)
      await settle(700)
      await openRows(2)
    }
    if (path === '/tpv') {
      for (const name of ['Caja 1 (mostrador)', 'Caja 2 (tienda)']) {
        await page.goto(`${APP}/tpv`); await settle(900)
        await page.getByText(name).first().click({ timeout: 3000 }).catch(() => undefined)
        await settle(1200)
        await clickAll('.pos-category, .pos-categories button, .pos-filters button', 14)
      }
    }
    if (path === '/almacen-movil') {
      for (const label of ['Consultar existencias', 'Lo que he hecho hoy', 'Imprimir etiquetas']) {
        await page.goto(`${APP}/almacen-movil`); await settle(900)
        await page.getByText(label).first().click({ timeout: 3000 }).catch(() => undefined)
        await settle(1000)
      }
    }
    console.log(`· ${viewport.width}px ${path} (${records.size} respuestas)`)
  }
  await context.close()
}

rmSync(OUT, { recursive: true, force: true })
mkdirSync(OUT, { recursive: true })
await crawl({ width: 1440, height: 900 }, false, routes)
await crawl({ width: 390, height: 844 }, true, ['/', '/tpv', '/almacen-movil', '/ventas', '/clientes'])
await browser.close()
if (!loginBody) throw new Error('No se vio la entrada de la demo (POST /api/v1/auth/demo).')
const token = JSON.parse(loginBody).accessToken

// ------------------------------------------------------------------ 2. detalles de cada registro

// Patrones vistos con un identificador dentro (p. ej. /api/v1/documents/:id o ?documentId=:id):
// se piden para todos los identificadores que aparecen en las respuestas grabadas.
const ids = new Set()
for (const record of records.values()) {
  if (!String(record.type).includes('json')) continue
  for (const match of record.body.toString('utf8').matchAll(UUID)) ids.add(match[0].toLowerCase())
}
const patterns = new Map()
for (const record of records.values()) {
  const full = record.path + record.search
  if (!UUID.test(full)) continue
  UUID.lastIndex = 0
  const template = full.replace(UUID, ':id')
  UUID.lastIndex = 0
  if ((template.match(/:id/g) ?? []).length !== 1) continue
  patterns.set(template, record)
}
console.log(`Detalles: ${patterns.size} patrones × ${ids.size} identificadores posibles`)

async function fetchDirect(path, search) {
  const response = await fetch(`${GATEWAY}${path}${search}`, { headers: { Authorization: `Bearer ${token}`, 'Accept-Language': 'es-ES' } })
  if (response.status !== 200) return
  remember('GET', path, search, 200, response.headers.get('content-type') ?? 'application/json', Buffer.from(await response.arrayBuffer()))
}

let extra = 0
for (const template of patterns.keys()) {
  const queue = [...ids]
  // Peticiones en paralelo, de ocho en ocho.
  while (queue.length) {
    const batch = queue.splice(0, 8)
    await Promise.all(batch.map(async (id) => {
      const full = template.replace(':id', id)
      const [path, query = ''] = full.split('?')
      const key = keyOf('GET', path, query ? `?${query}` : '')
      if (records.has(key)) return
      const before = records.size
      await fetchDirect(path, query ? `?${query}` : '')
      if (records.size > before) extra += 1
    }))
  }
}
console.log(`Detalles añadidos: ${extra}`)

// ------------------------------------------------------------------ 3. ficheros para el Worker

const index = {}
let bytes = 0
for (const record of records.values()) {
  if (record.status !== 200) continue
  const extension = String(record.type).includes('json') ? 'json' : String(record.type).includes('pdf') ? 'pdf' : String(record.type).includes('xml') ? 'xml' : String(record.type).includes('image') ? 'img' : 'bin'
  const file = `${createHash('sha1').update(record.body).digest('hex').slice(0, 20)}.${extension}`
  writeFileSync(resolve(OUT, file), record.body)
  bytes += record.body.length
  const route = `${record.method} ${record.path}`
  ;(index[route] ??= []).push({ query: Object.fromEntries(new URLSearchParams(record.search)), file, type: record.type })
}
const login = JSON.parse(loginBody)
writeFileSync(resolve(OUT, 'index.json'), JSON.stringify({
  recordedAt: new Date().toISOString(),
  login: { companies: login.companies, claims: JSON.parse(Buffer.from(login.accessToken.split('.')[1], 'base64url').toString('utf8')) },
  routes: index,
}))
console.log(`Listo: ${Object.values(index).flat().length} respuestas en ${Object.keys(index).length} rutas, ${(bytes / 1048576).toFixed(1)} MB.`)
