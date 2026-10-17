/**
 * Demo pública de PERA ERP en Cloudflare Workers.
 *
 * El backend real (ocho servicios Java y PostgreSQL) no cabe en un Worker. Para que la demo del
 * portfolio esté siempre disponible, rápida y sin coste, este Worker sirve una instantánea de la
 * demo local grabada con scripts/record.mjs: las mismas respuestas que daría PERA con los datos
 * inventados de la empresa VISITA. Se puede recorrer todo; las escrituras responden «no se guarda».
 *
 * Vercel reescribe pedrogomez.dev/proyecto/pera-erp/api/* hacia /api/* de este Worker.
 */
const SNAPSHOT = '/snapshot/'
const TOKEN_TTL_SECONDS = 8 * 60 * 60
// Parámetros que el navegador calcula con la fecha de hoy: si no coinciden con los grabados,
// se acepta igualmente la respuesta grabada más parecida.
const SOFT_PARAMS = new Set(['from', 'to', 'fromDate', 'toDate', 'date', 'dateFrom', 'dateTo', 'occurredFrom', 'occurredTo', 'issuedFrom', 'issuedTo', 'day', 'month', 'year', 'week', 'period', 'start', 'end', 'since', 'until', 'reference', 'referenceDate', 'asOf'])

let indexPromise = null

function loadIndex(env, origin) {
  indexPromise ??= env.ASSETS.fetch(new Request(`${origin}${SNAPSHOT}index.json`)).then((response) => {
    if (!response.ok) throw new Error('Sin instantánea')
    return response.json()
  }).catch((error) => { indexPromise = null; throw error })
  return indexPromise
}

function problem(status, title, detail, extra = {}) {
  return new Response(JSON.stringify({ type: 'about:blank', title, status, detail, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json', 'Cache-Control': 'no-store' },
  })
}

function base64url(value) {
  const bytes = new TextEncoder().encode(typeof value === 'string' ? value : JSON.stringify(value))
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

// Token del visitante con los mismos datos que emite PERA. La interfaz solo lo lee (nombre, roles,
// permisos y caducidad); aquí no protege nada porque no hay nada que escribir.
function visitorLogin(index) {
  const now = Math.floor(Date.now() / 1000)
  const claims = { ...index.login.claims, iat: now, exp: now + TOKEN_TTL_SECONDS }
  const token = `${base64url({ alg: 'HS256', typ: 'JWT' })}.${base64url(claims)}.${base64url('pera-demo-snapshot')}`
  return Response.json({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: TOKEN_TTL_SECONDS, companySelectionRequired: false, companies: index.login.companies }, { headers: { 'Cache-Control': 'no-store' } })
}

function pick(variants, query) {
  let best = null
  let bestScore = -Infinity
  for (const variant of variants) {
    let score = 0
    const keys = new Set([...Object.keys(variant.query), ...Object.keys(query)])
    for (const key of keys) {
      const recorded = variant.query[key]
      const asked = query[key]
      if (recorded === asked) score += 3
      else if (SOFT_PARAMS.has(key)) score -= 0.1
      else if (recorded === undefined || asked === undefined) score -= 1
      else score -= 4
    }
    if (score > bestScore) { best = variant; bestScore = score }
  }
  return best
}

async function serveApi(request, env, url, path) {
  let index
  try {
    index = await loadIndex(env, url.origin)
  } catch {
    return problem(503, 'Demo no disponible', 'La demo se está actualizando. Prueba de nuevo en un momento.')
  }
  if (request.method === 'POST' && path === '/api/v1/auth/demo') return visitorLogin(index)
  if (request.method === 'OPTIONS') return new Response(null, { status: 204 })
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return problem(403, 'Solo lectura', 'En la demo publicada puedes recorrerlo todo, pero los cambios no se guardan.', { code: 'DEMO_READ_ONLY' })
  }
  const variants = index.routes[`GET ${path}`]
  if (!variants?.length) return problem(404, 'No disponible en la demo', 'Esta consulta no forma parte de la demo publicada.')
  const variant = pick(variants, Object.fromEntries(url.searchParams))
  const asset = await env.ASSETS.fetch(new Request(`${url.origin}${SNAPSHOT}${variant.file}`))
  if (!asset.ok) return problem(404, 'No disponible en la demo', 'Esta consulta no forma parte de la demo publicada.')
  return new Response(asset.body, {
    status: 200,
    headers: { 'Content-Type': variant.type || 'application/json', 'Cache-Control': 'no-store', 'X-Pera-Demo': 'snapshot' },
  })
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    // La API llega como /api/... (reescritura de Vercel) o con el prefijo del portfolio.
    const path = url.pathname.replace(/^\/proyecto\/pera-erp(?=\/api\/)/, '')
    if (path.startsWith('/api/')) return serveApi(request, env, url, path)
    if (path.startsWith(SNAPSHOT)) return new Response('Not found', { status: 404 })
    if (path === '/' || path === '/health') return Response.json({ service: 'pera-demo', mode: 'snapshot' })
    return new Response('Not found', { status: 404 })
  },
}
