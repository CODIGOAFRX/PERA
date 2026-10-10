#!/usr/bin/env node
/**
 * Datos de demostración del TPV, el almacén móvil y las etiquetas.
 *
 * Crea por la API (como lo haría un usuario) familias de tienda, unos 60 artículos con código de
 * barras, embalajes, existencias iniciales, ventas de hoy en las dos cajas demo y movimientos de
 * almacén registrados por los operarios. Es idempotente: lo que ya existe no se vuelve a crear.
 *
 * Uso (con el entorno local arrancado):
 *   node scripts/seed-demo-tpv-almacen.mjs
 * Variables opcionales: PERA_API_URL (por defecto http://localhost:8080) y PERA_DEMO_PASSWORD.
 * Solo para la empresa demo local: no lo ejecutes contra una instalación real.
 */

const API = process.env.PERA_API_URL ?? 'http://localhost:8080'
const PASSWORD = process.env.PERA_DEMO_PASSWORD ?? 'ChangeMe123!'
const NOTE = 'Inventario inicial (demo TPV y almacén)'

// ------------------------------------------------------------------ utilidades

async function login(username) {
  const response = await fetch(`${API}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: PASSWORD }) })
  const body = await response.json()
  if (!body.accessToken) throw new Error(`No se pudo iniciar sesión como ${username}: ${JSON.stringify(body)}`)
  return body.accessToken
}

function client(token) {
  return async (method, path, body) => {
    const response = await fetch(`${API}${path}`, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Accept-Language': 'es-ES' }, body: body === undefined ? undefined : JSON.stringify(body) })
    const text = await response.text()
    const data = text ? JSON.parse(text) : undefined
    if (!response.ok) throw new Error(`${method} ${path} → ${response.status}: ${data?.detail ?? text}`)
    return data
  }
}

function eanCheck(digits) {
  let sum = 0
  for (let index = 0; index < digits.length; index += 1) sum += Number(digits[index]) * ((digits.length - index) % 2 === 1 ? 3 : 1)
  return (10 - (sum % 10)) % 10
}
const internalEan = (sequence) => { const body = `20${String(sequence).padStart(10, '0')}`; return `${body}${eanCheck(body)}` }

// Aleatorio reproducible: la misma carga produce los mismos tickets.
let seed = 20261010
const random = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648 }
const pick = (list) => list[Math.floor(random() * list.length)]
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// ------------------------------------------------------------------ catálogo de tienda

const TYPES = [
  { code: 'TDA-ALIM', name: 'Alimentación', groups: [['TDA-CONS', 'Conservas'], ['TDA-DESA', 'Desayuno y dulces'], ['TDA-PAST', 'Pasta, arroz y aceite'], ['TDA-LACT', 'Lácteos y huevos'], ['TDA-PANA', 'Panadería'], ['TDA-FRUT', 'Fruta y verdura']] },
  { code: 'TDA-BEBI', name: 'Bebidas', groups: [['TDA-AGUA', 'Agua y refrescos'], ['TDA-VINO', 'Vinos y cervezas'], ['TDA-CAFE', 'Café e infusiones']] },
  { code: 'TDA-DROG', name: 'Droguería e higiene', groups: [['TDA-LIMP', 'Limpieza'], ['TDA-HIGI', 'Higiene personal']] },
  { code: 'TDA-HOGA', name: 'Hogar y ferretería', groups: [['TDA-FERR', 'Ferretería'], ['TDA-MENA', 'Menaje']] },
]

// [código, nombre, PVP con IVA, IVA %, grupo, unidad, existencias iniciales]
const PRODUCTS = [
  ['SARD-120', 'Sardinas en aceite de oliva 120 g', 1.45, 10, 'TDA-CONS', 'UNIT', 240],
  ['ATUN-P3', 'Atún claro en aceite pack 3', 3.25, 10, 'TDA-CONS', 'UNIT', 120],
  ['MEJI-115', 'Mejillones en escabeche 115 g', 2.10, 10, 'TDA-CONS', 'UNIT', 90],
  ['TOMA-400', 'Tomate triturado 400 g', 0.89, 10, 'TDA-CONS', 'UNIT', 150],
  ['GARB-400', 'Garbanzos cocidos 400 g', 0.95, 10, 'TDA-CONS', 'UNIT', 110],
  ['GALL-800', 'Galletas María 800 g', 1.89, 10, 'TDA-DESA', 'UNIT', 80],
  ['CACA-400', 'Cacao soluble 400 g', 3.40, 10, 'TDA-DESA', 'UNIT', 45],
  ['MERM-340', 'Mermelada de fresa 340 g', 1.99, 10, 'TDA-DESA', 'UNIT', 40],
  ['MAGD-12', 'Magdalenas 12 unidades', 2.25, 10, 'TDA-DESA', 'UNIT', 36],
  ['CERE-500', 'Copos de avena 500 g', 2.65, 10, 'TDA-DESA', 'UNIT', 30],
  ['ARRO-1K', 'Arroz redondo 1 kg', 1.55, 4, 'TDA-PAST', 'UNIT', 100],
  ['ESPA-500', 'Espaguetis 500 g', 0.99, 4, 'TDA-PAST', 'UNIT', 130],
  ['MACA-500', 'Macarrones 500 g', 0.99, 4, 'TDA-PAST', 'UNIT', 120],
  ['ACEI-1L', 'Aceite de oliva virgen extra 1 L', 8.95, 4, 'TDA-PAST', 'UNIT', 60],
  ['HARI-1K', 'Harina de trigo 1 kg', 0.85, 4, 'TDA-PAST', 'UNIT', 50],
  ['LECH-1L', 'Leche entera 1 L', 0.99, 4, 'TDA-LACT', 'UNIT', 180],
  ['YOGU-P4', 'Yogur natural pack 4', 1.35, 10, 'TDA-LACT', 'UNIT', 48],
  ['QUES-250', 'Queso semicurado 250 g', 3.95, 4, 'TDA-LACT', 'UNIT', 30],
  ['MANT-250', 'Mantequilla 250 g', 2.79, 10, 'TDA-LACT', 'UNIT', 24],
  ['HUEV-12', 'Huevos camperos docena', 3.10, 4, 'TDA-LACT', 'UNIT', 40],
  ['PAN-BARRA', 'Barra de pan', 0.75, 4, 'TDA-PANA', 'UNIT', 60],
  ['PAN-MOLDE', 'Pan de molde integral', 1.65, 4, 'TDA-PANA', 'UNIT', 25],
  ['CROI-UD', 'Cruasán de mantequilla', 0.90, 10, 'TDA-PANA', 'UNIT', 30],
  ['NAPO-UD', 'Napolitana de chocolate', 1.10, 10, 'TDA-PANA', 'UNIT', 30],
  ['PLAT-KG', 'Plátano de Canarias (kg)', 2.39, 4, 'TDA-FRUT', 'KILOGRAM', 45],
  ['TOMP-KG', 'Tomate pera (kg)', 2.10, 4, 'TDA-FRUT', 'KILOGRAM', 35],
  ['MANZ-KG', 'Manzana golden (kg)', 1.99, 4, 'TDA-FRUT', 'KILOGRAM', 40],
  ['AGUA-150', 'Agua mineral 1,5 L', 0.55, 10, 'TDA-AGUA', 'UNIT', 300],
  ['AGUA-P6', 'Agua mineral pack 6 × 1,5 L', 2.95, 10, 'TDA-AGUA', 'UNIT', 60],
  ['COLA-33', 'Refresco de cola lata 33 cl', 0.95, 21, 'TDA-AGUA', 'UNIT', 192],
  ['ZUMO-1L', 'Zumo de naranja 1 L', 1.85, 10, 'TDA-AGUA', 'UNIT', 48],
  ['ISOT-50', 'Bebida isotónica 50 cl', 1.30, 21, 'TDA-AGUA', 'UNIT', 72],
  ['CERV-33', 'Cerveza lata 33 cl', 0.79, 21, 'TDA-VINO', 'UNIT', 240],
  ['CERV-P6', 'Cerveza pack 6 latas', 4.35, 21, 'TDA-VINO', 'UNIT', 40],
  ['VINO-CRI', 'Vino tinto crianza 75 cl', 6.90, 21, 'TDA-VINO', 'UNIT', 36],
  ['VINO-VER', 'Vino blanco verdejo 75 cl', 5.75, 21, 'TDA-VINO', 'UNIT', 36],
  ['CAVA-BRU', 'Cava brut 75 cl', 7.50, 21, 'TDA-VINO', 'UNIT', 18],
  ['CAFE-250', 'Café molido natural 250 g', 3.45, 10, 'TDA-CAFE', 'UNIT', 60],
  ['CAFE-C10', 'Café en cápsulas 10 unidades', 2.99, 10, 'TDA-CAFE', 'UNIT', 50],
  ['TE-25', 'Té verde 25 bolsitas', 1.75, 10, 'TDA-CAFE', 'UNIT', 24],
  ['MANZ-20', 'Manzanilla 20 bolsitas', 1.20, 10, 'TDA-CAFE', 'UNIT', 24],
  ['DETE-2L', 'Detergente líquido 2 L', 6.95, 21, 'TDA-LIMP', 'UNIT', 30],
  ['LAVA-750', 'Lavavajillas a mano 750 ml', 1.65, 21, 'TDA-LIMP', 'UNIT', 40],
  ['LEJI-2L', 'Lejía 2 L', 1.10, 21, 'TDA-LIMP', 'UNIT', 40],
  ['PAPC-2R', 'Papel de cocina 2 rollos', 1.95, 21, 'TDA-LIMP', 'UNIT', 50],
  ['BOLS-30', 'Bolsas de basura 30 unidades', 1.40, 21, 'TDA-LIMP', 'UNIT', 60],
  ['PAPH-12', 'Papel higiénico 12 rollos', 4.75, 21, 'TDA-HIGI', 'UNIT', 40],
  ['GEL-750', 'Gel de ducha 750 ml', 2.45, 21, 'TDA-HIGI', 'UNIT', 30],
  ['CHAM-400', 'Champú 400 ml', 2.95, 21, 'TDA-HIGI', 'UNIT', 30],
  ['DENT-75', 'Pasta de dientes 75 ml', 1.85, 21, 'TDA-HIGI', 'UNIT', 40],
  ['DESO-200', 'Desodorante en spray 200 ml', 2.60, 21, 'TDA-HIGI', 'UNIT', 30],
  ['PILA-AA4', 'Pilas alcalinas AA pack 4', 3.95, 21, 'TDA-FERR', 'UNIT', 50],
  ['BOMB-E27', 'Bombilla LED E27 9 W', 2.99, 21, 'TDA-FERR', 'UNIT', 40],
  ['CINT-AME', 'Cinta americana 25 m', 3.50, 21, 'TDA-FERR', 'UNIT', 20],
  ['TORN-100', 'Tornillos surtidos 100 unidades', 4.20, 21, 'TDA-FERR', 'UNIT', 25],
  ['ALAR-3T', 'Alargador 3 tomas 1,5 m', 7.95, 21, 'TDA-FERR', 'UNIT', 15],
  ['VASO-P6', 'Vasos de cristal pack 6', 5.95, 21, 'TDA-MENA', 'UNIT', 12],
  ['PLAT-P6', 'Platos llanos pack 6', 8.50, 21, 'TDA-MENA', 'UNIT', 10],
  ['FIAM-1L', 'Fiambrera hermética 1 L', 3.25, 21, 'TDA-MENA', 'UNIT', 20],
  ['SERV-100', 'Servilletas de papel 100 unidades', 1.15, 21, 'TDA-MENA', 'UNIT', 50],
]

// [código de artículo, tipo de embalaje, código, unidades]
const PACKAGING = [
  ['SARD-120', 'CAJA', 'CAJA24', 24], ['SARD-120', 'PALE', 'PALE1200', 1200],
  ['ATUN-P3', 'CAJA', 'CAJA12', 12], ['TOMA-400', 'CAJA', 'CAJA24', 24],
  ['AGUA-150', 'PACK', 'PACK6', 6], ['AGUA-150', 'PALE', 'PALE504', 504],
  ['COLA-33', 'CAJA', 'CAJA24', 24], ['CERV-33', 'CAJA', 'CAJA24', 24],
  ['LECH-1L', 'CAJA', 'CAJA6', 6], ['ESPA-500', 'CAJA', 'CAJA20', 20],
  ['PAPH-12', 'PALE', 'PALE60', 60], ['VINO-CRI', 'CAJA', 'CAJA6', 6],
]

// ------------------------------------------------------------------ carga

async function findOrCreate(api, list, create, match) {
  const existing = list.find(match)
  return existing ?? create()
}

async function seedCatalogue(admin) {
  const natures = (await admin('GET', '/api/v1/product-natures?size=200')).content
  const nature = await findOrCreate(admin, natures, () => admin('POST', '/api/v1/product-natures', { code: 'TIENDA', name: 'Productos de tienda', active: true }), (item) => item.code === 'TIENDA')
  const supertypes = (await admin('GET', '/api/v1/product-supertypes?size=200')).content
  const supertype = await findOrCreate(admin, supertypes, () => admin('POST', '/api/v1/product-supertypes', { code: 'TIENDA-GEN', name: 'Tienda de conveniencia', natureId: nature.id, active: true }), (item) => item.code === 'TIENDA-GEN')
  const types = (await admin('GET', '/api/v1/product-types?size=200')).content
  const groups = (await admin('GET', '/api/v1/product-groups?size=200')).content
  const groupIds = {}
  for (const type of TYPES) {
    const savedType = await findOrCreate(admin, types, () => admin('POST', '/api/v1/product-types', { code: type.code, name: type.name, supertypeId: supertype.id, active: true }), (item) => item.code === type.code)
    for (const [code, name] of type.groups) {
      const group = await findOrCreate(admin, groups, () => admin('POST', '/api/v1/product-groups', { code, name, productTypeId: savedType.id, active: true }), (item) => item.code === code)
      groupIds[code] = { id: group.id, typeId: savedType.id }
    }
  }

  const existing = new Map()
  for (let page = 0; ; page += 1) {
    const response = await admin('GET', `/api/v1/products?size=200&page=${page}`)
    response.content.forEach((product) => existing.set(product.code, product))
    if (page + 1 >= response.page.totalPages) break
  }
  const products = {}
  let created = 0
  for (const [index, [code, name, retail, taxRate, group, unit]] of PRODUCTS.entries()) {
    if (existing.has(code)) { products[code] = existing.get(code); continue }
    // Precio base sin IVA con cuatro decimales: al sumarle el IVA da exactamente el PVP.
    const basePrice = Math.round((retail / (1 + taxRate / 100)) * 10000) / 10000
    products[code] = await admin('POST', '/api/v1/products', {
      code, name, description: null, productTypeId: groupIds[group].typeId, productGroupId: groupIds[group].id, taxCodeId: null,
      familyId: null, categoryId: null, unitOfMeasure: unit, basePrice, taxRate, active: true, barcode: internalEan(1001 + index),
    })
    created += 1
  }
  console.log(`Catálogo: ${created} artículos nuevos (${PRODUCTS.length} de tienda en total).`)

  const packagingTypes = (await admin('GET', '/api/v1/packaging-types?size=200')).content
  const typeIds = {}
  for (const [code, name, description] of [['CAJA', 'Caja de cartón', 'Caja de cartón para reposición'], ['PALE', 'Palé europeo', 'Palé de 120 × 80 cm'], ['PACK', 'Pack retractilado', 'Unidades agrupadas con film']]) {
    const type = await findOrCreate(admin, packagingTypes, () => admin('POST', '/api/v1/packaging-types', { code, name, description, returnable: code === 'PALE', active: true }), (item) => item.code === code)
    typeIds[code] = type.id
  }
  let packagings = 0
  for (const [productCode, typeCode, shortCode, units] of PACKAGING) {
    // El código de embalaje (SKU) es único en la empresa: lleva el del artículo.
    const code = `${shortCode}-${productCode}`
    const product = products[productCode]
    const current = (await admin('GET', `/api/v1/product-packaging?productId=${product.id}&size=50`)).content
    if (current.some((item) => item.code === code)) continue
    await admin('POST', '/api/v1/product-packaging', { productId: product.id, packagingTypeId: typeIds[typeCode], code, unitsPerPackage: units, levels: null, unitsPerLevel: null, length: null, width: null, height: null, grossWeight: null, defaultPackaging: !current.some((item) => item.defaultPackaging), active: true })
    packagings += 1
  }
  console.log(`Embalajes: ${packagings} nuevos.`)
  return products
}

async function seedStock(admin, products) {
  const warehouses = (await admin('GET', '/api/v1/warehouses?size=50')).content
  const central = warehouses.find((item) => item.defaultWarehouse && item.active) ?? warehouses[0]
  const secondary = warehouses.find((item) => item.id !== central.id && item.active)
  let entries = 0
  for (const [code, , , , , unit, quantity] of PRODUCTS) {
    const product = products[code]
    const levels = (await admin('GET', `/api/v1/stock-levels?productId=${product.id}&size=10`)).content
    if (levels.length > 0) continue
    await admin('POST', '/api/v1/stock-movements/adjustments', { warehouseId: central.id, productId: product.id, productCode: product.code, productName: product.name, unitOfMeasure: unit, type: 'ADJUSTMENT_IN', quantity, note: NOTE })
    entries += 1
    if (secondary && random() < 0.3) {
      await admin('POST', '/api/v1/stock-movements/adjustments', { warehouseId: secondary.id, productId: product.id, productCode: product.code, productName: product.name, unitOfMeasure: unit, type: 'ADJUSTMENT_IN', quantity: Math.max(6, Math.round(quantity / 4)), note: NOTE })
    }
  }
  console.log(`Existencias iniciales: ${entries} artículos inventariados en ${central.code}.`)
  return { central, secondary }
}

async function ensureRegisters(admin) {
  const existing = await admin('GET', '/api/v1/cash-registers')
  for (const [code, name] of [['DEMO-CAJA-1', 'Caja 1 (mostrador)'], ['DEMO-CAJA-2', 'Caja 2 (tienda)']]) {
    if (existing.some((item) => item.code === code)) continue
    await admin('POST', '/api/v1/cash-registers', { code, name, ownerName: null, active: true })
    console.log(`Caja ${code} creada.`)
  }
}

async function sellTickets(cashier, registerCode, products, count, openingAmount) {
  const registers = await cashier('GET', '/api/v1/pos/registers')
  const register = registers.find((item) => item.code === registerCode)
  if (!register) { console.log(`No existe la caja ${registerCode}; se omite.`); return null }
  let sessionId = register.openSession?.id
  if (!sessionId) sessionId = (await cashier('POST', `/api/v1/pos/registers/${register.id}/open`, { openingAmount })).sessionId
  const summary = await cashier('GET', `/api/v1/pos/sessions/${sessionId}/summary`)
  if (summary.figures.ticketCount >= Math.floor(count / 2)) { console.log(`${register.name}: ya tiene ${summary.figures.ticketCount} tickets; no se añaden más.`); return { sessionId, tickets: [] } }
  const codes = PRODUCTS.map(([code]) => code)
  const tickets = []
  for (let index = 0; index < count; index += 1) {
    const lines = []
    const size = 1 + Math.floor(random() * 5)
    for (let line = 0; line < size; line += 1) {
      const code = pick(codes)
      if (lines.some((item) => item.code === code)) continue
      const kilos = products[code].unitOfMeasure === 'KILOGRAM'
      lines.push({ code, productId: products[code].id, quantity: kilos ? Math.round((0.4 + random() * 1.6) * 1000) / 1000 : 1 + Math.floor(random() * 3), unitPrice: null, discountPercent: index === 3 && line === 0 ? 10 : null })
    }
    const roll = random()
    const method = roll < 0.55 ? 'CASH' : roll < 0.9 || lines.length < 3 ? 'CARD' : 'MIXED'
    // Sin efectivo entregado el servidor cobra el importe exacto; el mixto paga 5 € con tarjeta.
    tickets.push({ sessionId, lines: lines.map(({ code, ...rest }) => rest), discountPercent: index === 7 ? 5 : null, paymentMethod: method, cashTendered: null, cardAmount: method === 'MIXED' ? 5 : null, customerName: index === 5 ? 'Bar La Plaza S.L.' : null, customerTaxId: index === 5 ? 'B12345678' : null, note: null, clientReference: null })
  }
  const sold = []
  for (const body of tickets) {
    try { sold.push(await cashier('POST', '/api/v1/pos/tickets', body)) } catch (error) { console.log(`  Ticket omitido: ${error.message}`) }
    await sleep(40)
  }
  console.log(`${register.name}: ${sold.length} tickets cobrados.`)
  return { sessionId, tickets: sold }
}

async function main() {
  const admin = client(await login('admin'))
  const products = await seedCatalogue(admin)
  await seedStock(admin, products)

  // Abre la marca de lectura de tickets en el almacén antes de vender, para que estas ventas descuenten.
  await admin('GET', '/api/v1/sales-deliveries?size=1')
  await sleep(1500)

  // En una instalación recién clonada las cajas demo no existen: se crean aquí, como haría el administrador en Cartera.
  await ensureRegisters(admin)

  const cajero1 = client(await login('cajero1'))
  const caja1 = await sellTickets(cajero1, 'DEMO-CAJA-1', products, 14, 150)
  const cajero2 = client(await login('cajero2'))
  const caja2 = await sellTickets(cajero2, 'DEMO-CAJA-2', products, 9, 120)

  if (caja1?.tickets.length) {
    // Una devolución parcial y una anulación para que el seguimiento tenga de todo.
    const sale = caja1.tickets.find((ticket) => ticket.lines.some((line) => line.quantity >= 2))
    if (sale) {
      const line = sale.lines.find((item) => item.quantity >= 2)
      await cajero1('POST', `/api/v1/pos/tickets/${sale.id}/refund`, { sessionId: caja1.sessionId, lines: [{ lineId: line.id, quantity: 1 }], paymentMethod: sale.paymentMethod === 'CARD' ? 'CARD' : 'CASH', reason: 'El cliente se llevó una unidad de más', clientReference: null })
    }
    const last = caja1.tickets[caja1.tickets.length - 1]
    await cajero1('POST', `/api/v1/pos/tickets/${last.id}/void`, { reason: 'Cobrado por error: el cliente había pagado en la otra caja' })
    await cajero1('POST', `/api/v1/pos/sessions/${caja1.sessionId}/movements`, { type: 'WITHDRAWAL', amount: 50, concept: 'Retirada a caja fuerte' })
    console.log('Caja 1: una devolución, una anulación y una retirada de efectivo.')
  }
  if (caja2?.tickets.length) await cajero2('POST', `/api/v1/pos/sessions/${caja2.sessionId}/movements`, { type: 'INCOME', amount: 30, concept: 'Cambio en monedas del banco' })

  // Operarios de almacén desde el móvil.
  const scans = [
    ['almacen1', 'IN', 'SARD-120', 48, 'RECEIPT', 'Albarán del proveedor 2381 · 2 cajas'],
    ['almacen1', 'IN', 'AGUA-150', 120, 'RECEIPT', 'Albarán del proveedor 2381'],
    ['almacen1', 'OUT', 'HUEV-12', 2, 'DAMAGED', 'Se cayó la caja al descargar'],
    ['almacen1', 'OUT', 'YOGU-P4', 3, 'EXPIRED', 'Caducados el 9/10'],
    ['almacen1', 'IN', 'CERV-33', 72, 'RECEIPT', '3 cajas de 24'],
    ['almacen2', 'OUT', 'PAN-BARRA', 6, 'SPOILED', 'Pan del día anterior'],
    ['almacen2', 'IN', 'PILA-AA4', 10, 'CUSTOMER_RETURN', 'Devolución de cliente sin abrir'],
    ['almacen2', 'OUT', 'BOMB-E27', 1, 'INTERNAL_USE', 'Para la nave de expediciones'],
    ['almacen2', 'IN', 'LECH-1L', 36, 'COUNT', 'Recuento de la cámara'],
  ]
  const operators = {}
  let done = 0
  for (const [user, direction, code, quantity, reason, note] of scans) {
    operators[user] ??= client(await login(user))
    const mine = await operators[user]('GET', `/api/v1/stock-movements?mine=true&size=50&occurredFrom=${encodeURIComponent(new Date(new Date().setHours(0, 0, 0, 0)).toISOString())}`)
    if (mine.content.some((movement) => movement.productCode === code && movement.note === note)) continue
    const product = products[code]
    await operators[user]('POST', '/api/v1/stock-movements/scan', { warehouseId: null, productId: product.id, productCode: product.code, productName: product.name, unitOfMeasure: product.unitOfMeasure, direction, quantity, reason, note, clientReference: null })
    done += 1
  }
  console.log(`Almacén móvil: ${done} movimientos registrados por los operarios.`)
  console.log('Listo. Usuarios: cajero1, cajero2, almacen1 y almacen2 con la contraseña de desarrollo.')
}

main().catch((error) => { console.error(error.message); process.exit(1) })
