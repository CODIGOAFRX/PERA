#!/usr/bin/env node
/**
 * Datos de la demo pública del portfolio (empresa VISITA, «Distribuciones Nevada (demo)»).
 *
 * Todo es inventado: una distribuidora de alimentación, bebidas y droguería de Granada que vende a
 * bares, hoteles y oficinas y tiene además mostrador (TPV) y almacén. Carga por la API, como lo haría
 * el visitante, cinco meses de actividad hasta hoy: clientes con sus contactos, proveedores, catálogo
 * con códigos de barras, existencias y ubicaciones, compras, presupuestos, albaranes, facturas con
 * Veri*Factu (en pruebas y sin certificado: nunca se remite nada), recibos cobrados y pendientes,
 * comisiones, reclamaciones, agenda, ventas de hoy en el TPV y movimientos del almacén móvil.
 *
 * Requisitos: el entorno arrancado con la demo activada (scripts/start-local.ps1 -Demo).
 *   node scripts/seed-demo-portfolio.mjs
 * Se puede relanzar si se corta: cada bloque comprueba antes lo que ya existe y no lo repite.
 *
 * Variables opcionales: PERA_API_URL (pasarela, http://localhost:8080) y, para los dos ajustes que la
 * pasarela no deja tocar al visitante (datos de la empresa y Veri*Factu), PERA_IDENTITY_URL
 * (http://localhost:8081) y PERA_SALES_URL (http://localhost:8083), a los que se llama directamente.
 */

const GATEWAY = process.env.PERA_API_URL ?? 'http://localhost:8080'
const IDENTITY = process.env.PERA_IDENTITY_URL ?? 'http://localhost:8081'
const SALES = process.env.PERA_SALES_URL ?? 'http://localhost:8083'
const DEMO_TAX_ID = process.env.PERA_DEMO_COMPANY_TAX_ID ?? 'B18745638'
const DEMO_LEGAL_NAME = 'Distribuciones Nevada S.L. (demo)'

// ------------------------------------------------------------------ utilidades

let token
function client(base) {
  return async (method, path, body) => {
    const response = await fetch(`${base}${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Accept-Language': 'es-ES' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    const text = await response.text()
    let data
    try { data = text ? JSON.parse(text) : undefined } catch { data = text }
    if (!response.ok) throw new Error(`${method} ${path} → ${response.status}: ${data?.detail ?? data?.title ?? text}`)
    return data
  }
}
const api = client(GATEWAY)
const identity = client(IDENTITY)
const sales = client(SALES)
const content = (data) => Array.isArray(data) ? data : data?.content ?? []

// Busca por código en un listado y solo crea si no está: así el script se puede relanzar.
async function ensure(listPath, code, create) {
  const found = content(await api('GET', listPath)).find((item) => item.code === code)
  return found ?? create()
}

// Aleatorio reproducible: la misma carga da los mismos datos.
let seed = 20261017
const random = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648 }
const pick = (list) => list[Math.floor(random() * list.length)]
const between = (min, max) => min + Math.floor(random() * (max - min + 1))
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const today = new Date()
today.setHours(12, 0, 0, 0)
const iso = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const addDays = (date, days) => { const next = new Date(date); next.setDate(next.getDate() + days); return next }
const isWeekend = (date) => date.getDay() === 0 || date.getDay() === 6
const TODAY = iso(today)

// CIF con dígito de control correcto (letra B, sociedad limitada).
function cif(seven) {
  const digits = String(seven).padStart(7, '0').split('').map(Number)
  let even = 0, odd = 0
  digits.forEach((n, i) => { if (i % 2 === 1) even += n; else { const x = n * 2; odd += Math.floor(x / 10) + (x % 10) } })
  return `B${digits.join('')}${(10 - ((even + odd) % 10)) % 10}`
}

function eanCheck(digits) {
  let sum = 0
  for (let index = 0; index < digits.length; index += 1) sum += Number(digits[index]) * ((digits.length - index) % 2 === 1 ? 3 : 1)
  return (10 - (sum % 10)) % 10
}
const internalEan = (sequence) => { const body = `20${String(sequence).padStart(10, '0')}`; return `${body}${eanCheck(body)}` }

const step = (text) => console.log(`· ${text}`)

// ------------------------------------------------------------------ datos inventados

const SALESPEOPLE = [
  ['NEV-MARTA', 'Marta Ruiz Olmedo', 'marta.ruiz@nevada-demo.example', '+34 600 112 401', 3],
  ['NEV-JAVI', 'Javier Molina Castro', 'javier.molina@nevada-demo.example', '+34 600 112 402', 2.5],
  ['NEV-ELENA', 'Elena Suárez Prieto', 'elena.suarez@nevada-demo.example', '+34 600 112 403', 2],
]

// [código, razón social, nombre comercial, dirección, ciudad, CP, provincia, teléfono, comercial, número CIF, contacto, cargo]
const CUSTOMERS = [
  ['C-001', 'Restaurante El Mirador del Darro S.L.', 'El Mirador del Darro', 'Carrera del Darro 41', 'Granada', '18010', 'Granada', '+34 958 220 101', 0, 3810231, 'Lucía Fernández', 'Jefa de cocina'],
  ['C-002', 'Hotel Jardines de la Vega S.L.', 'Jardines de la Vega', 'Camino de Ronda 155', 'Granada', '18003', 'Granada', '+34 958 250 202', 0, 3902214, 'Andrés Navarro', 'Compras'],
  ['C-003', 'Cafetería La Alacena S.L.', 'La Alacena', 'Calle Reyes Católicos 18', 'Granada', '18009', 'Granada', '+34 958 221 303', 1, 4120983, 'Rocío Medina', 'Encargada'],
  ['C-004', 'Bar Los Tres Arcos S.L.', 'Los Tres Arcos', 'Plaza Nueva 6', 'Granada', '18009', 'Granada', '+34 958 222 404', 1, 4241526, 'Paco Jiménez', 'Propietario'],
  ['C-005', 'Gimnasio Forma Sur S.L.', 'Forma Sur', 'Avenida de Dílar 92', 'Granada', '18007', 'Granada', '+34 958 133 505', 2, 4389017, 'Sergio Lara', 'Gerente'],
  ['C-006', 'Oficinas Centro Genil S.L.', 'Centro Genil', 'Paseo del Salón 3, 4.º', 'Granada', '18005', 'Granada', '+34 958 264 606', 2, 4475140, 'Inés Romero', 'Administración'],
  ['C-007', 'Colegio Sierra Clara S.L.', 'Colegio Sierra Clara', 'Calle Arabial 120', 'Granada', '18004', 'Granada', '+34 958 278 707', 2, 4552036, 'Teresa Vílchez', 'Secretaría'],
  ['C-008', 'Catering Azahar Eventos S.L.', 'Azahar Eventos', 'Polígono Asegra, nave 22', 'Peligros', '18210', 'Granada', '+34 958 405 808', 0, 4647328, 'Daniel Ortega', 'Producción'],
  ['C-009', 'Panadería Hornos de Santa Ana S.L.', 'Hornos de Santa Ana', 'Calle Elvira 77', 'Granada', '18010', 'Granada', '+34 958 225 909', 1, 4718345, 'Carmen Ávila', 'Propietaria'],
  ['C-010', 'Residencia Mayores Las Acacias S.L.', 'Las Acacias', 'Calle Neptuno 14', 'Armilla', '18100', 'Granada', '+34 958 571 010', 0, 4806712, 'Manuel Pérez', 'Dirección'],
  ['C-011', 'Hostal Puerta de Elvira S.L.', 'Puerta de Elvira', 'Calle Elvira 2', 'Granada', '18010', 'Granada', '+34 958 226 111', 1, 4912045, 'Nuria Castillo', 'Recepción'],
  ['C-012', 'Taberna Costa Tropical S.L.', 'Costa Tropical', 'Paseo del Altillo 9', 'Almuñécar', '18690', 'Granada', '+34 958 631 212', 1, 5013378, 'Óscar Rivas', 'Encargado'],
  ['C-013', 'Clínica Veterinaria Albaicín S.L.', 'Veterinaria Albaicín', 'Calle Pagés 31', 'Granada', '18010', 'Granada', '+34 958 284 313', 2, 5104611, 'Alba Moreno', 'Administración'],
  ['C-014', 'Coworking Atelier Sur S.L.', 'Atelier Sur', 'Calle Gran Vía de Colón 40', 'Granada', '18010', 'Granada', '+34 958 209 414', 2, 5207944, 'Hugo Delgado', 'Comunidad'],
  ['C-015', 'Chiringuito Playa Velilla S.L.', 'Playa Velilla', 'Paseo de Velilla s/n', 'Almuñécar', '18690', 'Granada', '+34 958 632 515', 0, 5301277, 'Rafa Gálvez', 'Propietario'],
  ['C-016', 'Escuela de Hostelería Alhambra S.L.', 'Escuela Alhambra', 'Calle San Antón 66', 'Granada', '18005', 'Granada', '+34 958 259 616', 0, 5409510, 'Patricia León', 'Coordinación'],
]

// [código, razón social, nombre, teléfono, número CIF]
const SUPPLIERS = [
  ['P-001', 'Conservas del Cantábrico Norte S.L. (demo)', 'Conservas Norte', '+34 942 300 101', 6110231],
  ['P-002', 'Bebidas y Aguas Sierra S.L. (demo)', 'Aguas Sierra', '+34 958 300 202', 6202214],
  ['P-003', 'Lácteos Valle Verde S.L. (demo)', 'Valle Verde', '+34 985 300 303', 6320983],
  ['P-004', 'Droguería Industrial Levante S.L. (demo)', 'Droguería Levante', '+34 963 300 404', 6441526],
  ['P-005', 'Ferretería Mayorista Sur S.L. (demo)', 'Mayorista Sur', '+34 954 300 505', 6589017],
]

const TYPES = [
  { code: 'ALIM', name: 'Alimentación', groups: [['CONS', 'Conservas'], ['DESA', 'Desayuno y dulces'], ['PAST', 'Pasta, arroz y aceite'], ['LACT', 'Lácteos y huevos'], ['PANA', 'Panadería'], ['FRUT', 'Fruta y verdura']] },
  { code: 'BEBI', name: 'Bebidas', groups: [['AGUA', 'Agua y refrescos'], ['VINO', 'Vinos y cervezas'], ['CAFE', 'Café e infusiones']] },
  { code: 'DROG', name: 'Droguería e higiene', groups: [['LIMP', 'Limpieza'], ['HIGI', 'Higiene personal']] },
  { code: 'HOGA', name: 'Hogar y ferretería', groups: [['FERR', 'Ferretería'], ['MENA', 'Menaje']] },
  { code: 'SERV', name: 'Servicios', groups: [['LOGI', 'Logística y reparto']] },
]

// [código, nombre, PVP con IVA, IVA %, grupo, unidad, existencias iniciales, proveedor]
const PRODUCTS = [
  ['SARD-120', 'Sardinas en aceite de oliva 120 g', 1.45, 10, 'CONS', 'UNIT', 900, 'P-001'],
  ['ATUN-P3', 'Atún claro en aceite pack 3', 3.25, 10, 'CONS', 'UNIT', 700, 'P-001'],
  ['MEJI-115', 'Mejillones en escabeche 115 g', 2.10, 10, 'CONS', 'UNIT', 500, 'P-001'],
  ['TOMA-400', 'Tomate triturado 400 g', 0.89, 10, 'CONS', 'UNIT', 900, 'P-001'],
  ['GARB-400', 'Garbanzos cocidos 400 g', 0.95, 10, 'CONS', 'UNIT', 600, 'P-001'],
  ['GALL-800', 'Galletas María 800 g', 1.89, 10, 'DESA', 'UNIT', 400, 'P-001'],
  ['CACA-400', 'Cacao soluble 400 g', 3.40, 10, 'DESA', 'UNIT', 250, 'P-001'],
  ['MERM-340', 'Mermelada de fresa 340 g', 1.99, 10, 'DESA', 'UNIT', 300, 'P-001'],
  ['MAGD-12', 'Magdalenas 12 unidades', 2.25, 10, 'DESA', 'UNIT', 260, 'P-001'],
  ['CERE-500', 'Copos de avena 500 g', 2.65, 10, 'DESA', 'UNIT', 200, 'P-001'],
  ['ARRO-1K', 'Arroz redondo 1 kg', 1.55, 4, 'PAST', 'UNIT', 700, 'P-001'],
  ['ESPA-500', 'Espaguetis 500 g', 0.99, 4, 'PAST', 'UNIT', 800, 'P-001'],
  ['MACA-500', 'Macarrones 500 g', 0.99, 4, 'PAST', 'UNIT', 700, 'P-001'],
  ['ACEI-1L', 'Aceite de oliva virgen extra 1 L', 8.95, 4, 'PAST', 'UNIT', 600, 'P-001'],
  ['HARI-1K', 'Harina de trigo 1 kg', 0.85, 4, 'PAST', 'UNIT', 500, 'P-001'],
  ['LECH-1L', 'Leche entera 1 L', 0.99, 4, 'LACT', 'UNIT', 1500, 'P-003'],
  ['YOGU-P4', 'Yogur natural pack 4', 1.35, 10, 'LACT', 'UNIT', 300, 'P-003'],
  ['QUES-250', 'Queso semicurado 250 g', 3.95, 4, 'LACT', 'UNIT', 260, 'P-003'],
  ['MANT-250', 'Mantequilla 250 g', 2.79, 10, 'LACT', 'UNIT', 240, 'P-003'],
  ['HUEV-12', 'Huevos camperos docena', 3.10, 4, 'LACT', 'UNIT', 360, 'P-003'],
  ['PAN-BARRA', 'Barra de pan', 0.75, 4, 'PANA', 'UNIT', 400, 'P-003'],
  ['PAN-MOLDE', 'Pan de molde integral', 1.65, 4, 'PANA', 'UNIT', 200, 'P-003'],
  ['CROI-UD', 'Cruasán de mantequilla', 0.90, 10, 'PANA', 'UNIT', 300, 'P-003'],
  ['NAPO-UD', 'Napolitana de chocolate', 1.10, 10, 'PANA', 'UNIT', 300, 'P-003'],
  ['PLAT-KG', 'Plátano de Canarias (kg)', 2.39, 4, 'FRUT', 'KILOGRAM', 300, 'P-003'],
  ['TOMP-KG', 'Tomate pera (kg)', 2.10, 4, 'FRUT', 'KILOGRAM', 300, 'P-003'],
  ['MANZ-KG', 'Manzana golden (kg)', 1.99, 4, 'FRUT', 'KILOGRAM', 300, 'P-003'],
  ['AGUA-150', 'Agua mineral 1,5 L', 0.55, 10, 'AGUA', 'UNIT', 2400, 'P-002'],
  ['AGUA-P6', 'Agua mineral pack 6 × 1,5 L', 2.95, 10, 'AGUA', 'UNIT', 500, 'P-002'],
  ['COLA-33', 'Refresco de cola lata 33 cl', 0.95, 21, 'AGUA', 'UNIT', 1900, 'P-002'],
  ['ZUMO-1L', 'Zumo de naranja 1 L', 1.85, 10, 'AGUA', 'UNIT', 500, 'P-002'],
  ['ISOT-50', 'Bebida isotónica 50 cl', 1.30, 21, 'AGUA', 'UNIT', 700, 'P-002'],
  ['CERV-33', 'Cerveza lata 33 cl', 0.79, 21, 'VINO', 'UNIT', 2400, 'P-002'],
  ['CERV-P6', 'Cerveza pack 6 latas', 4.35, 21, 'VINO', 'UNIT', 400, 'P-002'],
  ['VINO-CRI', 'Vino tinto crianza 75 cl', 6.90, 21, 'VINO', 'UNIT', 360, 'P-002'],
  ['VINO-VER', 'Vino blanco verdejo 75 cl', 5.75, 21, 'VINO', 'UNIT', 360, 'P-002'],
  ['CAVA-BRU', 'Cava brut 75 cl', 7.50, 21, 'VINO', 'UNIT', 180, 'P-002'],
  ['CAFE-250', 'Café molido natural 250 g', 3.45, 10, 'CAFE', 'UNIT', 500, 'P-002'],
  ['CAFE-C10', 'Café en cápsulas 10 unidades', 2.99, 10, 'CAFE', 'UNIT', 400, 'P-002'],
  ['TE-25', 'Té verde 25 bolsitas', 1.75, 10, 'CAFE', 'UNIT', 200, 'P-002'],
  ['MANZ-20', 'Manzanilla 20 bolsitas', 1.20, 10, 'CAFE', 'UNIT', 200, 'P-002'],
  ['DETE-2L', 'Detergente líquido 2 L', 6.95, 21, 'LIMP', 'UNIT', 300, 'P-004'],
  ['LAVA-750', 'Lavavajillas a mano 750 ml', 1.65, 21, 'LIMP', 'UNIT', 400, 'P-004'],
  ['LEJI-2L', 'Lejía 2 L', 1.10, 21, 'LIMP', 'UNIT', 400, 'P-004'],
  ['PAPC-2R', 'Papel de cocina 2 rollos', 1.95, 21, 'LIMP', 'UNIT', 500, 'P-004'],
  ['BOLS-30', 'Bolsas de basura 30 unidades', 1.40, 21, 'LIMP', 'UNIT', 600, 'P-004'],
  ['PAPH-12', 'Papel higiénico 12 rollos', 4.75, 21, 'HIGI', 'UNIT', 400, 'P-004'],
  ['GEL-750', 'Gel de ducha 750 ml', 2.45, 21, 'HIGI', 'UNIT', 300, 'P-004'],
  ['CHAM-400', 'Champú 400 ml', 2.95, 21, 'HIGI', 'UNIT', 300, 'P-004'],
  ['DENT-75', 'Pasta de dientes 75 ml', 1.85, 21, 'HIGI', 'UNIT', 300, 'P-004'],
  ['DESO-200', 'Desodorante en spray 200 ml', 2.60, 21, 'HIGI', 'UNIT', 300, 'P-004'],
  ['PILA-AA4', 'Pilas alcalinas AA pack 4', 3.95, 21, 'FERR', 'UNIT', 300, 'P-005'],
  ['BOMB-E27', 'Bombilla LED E27 9 W', 2.99, 21, 'FERR', 'UNIT', 300, 'P-005'],
  ['CINT-AME', 'Cinta americana 25 m', 3.50, 21, 'FERR', 'UNIT', 150, 'P-005'],
  ['TORN-100', 'Tornillos surtidos 100 unidades', 4.20, 21, 'FERR', 'UNIT', 150, 'P-005'],
  ['ALAR-3T', 'Alargador 3 tomas 1,5 m', 7.95, 21, 'FERR', 'UNIT', 120, 'P-005'],
  ['VASO-P6', 'Vasos de cristal pack 6', 5.95, 21, 'MENA', 'UNIT', 150, 'P-005'],
  ['PLAT-P6', 'Platos llanos pack 6', 8.50, 21, 'MENA', 'UNIT', 120, 'P-005'],
  ['FIAM-1L', 'Fiambrera hermética 1 L', 3.25, 21, 'MENA', 'UNIT', 200, 'P-005'],
  ['SERV-100', 'Servilletas de papel 100 unidades', 1.15, 21, 'MENA', 'UNIT', 600, 'P-005'],
  ['SRV-PORTE', 'Portes y entrega en local', 18.15, 21, 'LOGI', 'UNIT', 0, null],
  ['SRV-REPO', 'Reposición y colocación en almacén del cliente (hora)', 30.25, 21, 'LOGI', 'HOUR', 0, null],
]

// Qué compra cada tipo de cliente: así las facturas tienen sentido.
const BASKETS = {
  horeca: ['AGUA-150', 'COLA-33', 'CERV-33', 'VINO-CRI', 'VINO-VER', 'ACEI-1L', 'TOMA-400', 'ARRO-1K', 'ESPA-500', 'CAFE-250', 'LECH-1L', 'HUEV-12', 'PAPC-2R', 'LAVA-750', 'SERV-100', 'ZUMO-1L', 'QUES-250', 'MANT-250'],
  hotel: ['AGUA-150', 'ZUMO-1L', 'CAFE-C10', 'LECH-1L', 'MERM-340', 'MAGD-12', 'CROI-UD', 'GEL-750', 'CHAM-400', 'PAPH-12', 'DETE-2L', 'LEJI-2L', 'CAVA-BRU', 'YOGU-P4', 'TE-25'],
  oficina: ['AGUA-P6', 'CAFE-C10', 'CAFE-250', 'TE-25', 'MANZ-20', 'GALL-800', 'PAPH-12', 'PAPC-2R', 'BOLS-30', 'PILA-AA4', 'BOMB-E27', 'VASO-P6', 'ISOT-50'],
  panaderia: ['HARI-1K', 'MANT-250', 'HUEV-12', 'LECH-1L', 'CACA-400', 'MERM-340', 'PAPC-2R', 'BOLS-30', 'LEJI-2L'],
}
const CUSTOMER_BASKET = { 'C-001': 'horeca', 'C-002': 'hotel', 'C-003': 'horeca', 'C-004': 'horeca', 'C-005': 'oficina', 'C-006': 'oficina', 'C-007': 'oficina', 'C-008': 'horeca', 'C-009': 'panaderia', 'C-010': 'hotel', 'C-011': 'hotel', 'C-012': 'horeca', 'C-013': 'oficina', 'C-014': 'oficina', 'C-015': 'horeca', 'C-016': 'horeca' }

const PACKAGING = [
  ['SARD-120', 'CAJA', 'CAJA24', 24], ['ATUN-P3', 'CAJA', 'CAJA12', 12], ['TOMA-400', 'CAJA', 'CAJA24', 24],
  ['AGUA-150', 'PACK', 'PACK6', 6], ['AGUA-150', 'PALE', 'PALE504', 504], ['COLA-33', 'CAJA', 'CAJA24', 24],
  ['CERV-33', 'CAJA', 'CAJA24', 24], ['LECH-1L', 'CAJA', 'CAJA6', 6], ['ESPA-500', 'CAJA', 'CAJA20', 20],
  ['PAPH-12', 'PALE', 'PALE60', 60], ['VINO-CRI', 'CAJA', 'CAJA6', 6], ['ACEI-1L', 'CAJA', 'CAJA12', 12],
]

const ZONES = { CONS: 'Pasillo 1 · Estante A', DESA: 'Pasillo 1 · Estante C', PAST: 'Pasillo 2 · Estante A', LACT: 'Cámara frigorífica 1', PANA: 'Zona de panadería', FRUT: 'Cámara frigorífica 2', AGUA: 'Zona de palés · Calle 1', VINO: 'Pasillo 4 · Bodega', CAFE: 'Pasillo 2 · Estante D', LIMP: 'Pasillo 5 · Estante A', HIGI: 'Pasillo 5 · Estante C', FERR: 'Pasillo 6 · Estante B', MENA: 'Pasillo 6 · Estante D' }

// ------------------------------------------------------------------ carga

async function enter() {
  const response = await fetch(`${GATEWAY}/api/v1/auth/demo`, { method: 'POST' })
  if (!response.ok) throw new Error(`No se pudo entrar en la demo (${response.status}). ¿Está arrancado con scripts/start-local.ps1 -Demo?`)
  const body = await response.json()
  token = body.accessToken
  return body.companies[0]
}

async function configureCompany() {
  // Datos que salen en las facturas y los tickets. La pasarela no deja cambiarlos al visitante.
  const current = await identity('GET', '/api/v1/company-settings/current')
  await identity('PUT', '/api/v1/company-settings/current', {
    ...current,
    displayName: DEMO_LEGAL_NAME,
    contactEmail: 'hola@nevada-demo.example', invoiceEmail: 'facturas@nevada-demo.example', replyToEmail: null,
    phone: '+34 958 000 123', website: 'https://pedrogomez.dev/proyecto/pera-erp/',
    addressLine1: 'Polígono Juncaril, calle Loja 14', addressLine2: null, postalCode: '18220', city: 'Albolote', region: 'Granada',
  })
  // Veri*Factu en pruebas: genera registro, huella, cadena y QR reales. Sin certificado no se remite nada.
  await sales('PUT', '/api/v1/verifactu-settings/current', {
    enabled: true, mode: 'VERIFACTU', environment: 'TEST', issuerTaxId: DEMO_TAX_ID, issuerLegalName: DEMO_LEGAL_NAME,
    defaultRegimeKey: '01', defaultOperationQualification: 'S1', timeZone: 'Europe/Madrid',
  })
  step('Empresa y Veri*Factu (pruebas, sin remisión) configurados.')
}

async function seedPaymentMethods() {
  const existing = await api('GET', '/api/v1/payment-methods')
  const methods = {}
  for (const [code, name, rules] of [
    ['CONTADO', 'Contado', [[0, 100]]],
    ['TRANSF-30', 'Transferencia a 30 días', [[30, 100]]],
    ['RECIBO-30-60', 'Recibo domiciliado 30 y 60 días', [[30, 50], [60, 50]]],
  ]) {
    methods[code] = existing.find((item) => item.code === code)
      ?? await api('POST', '/api/v1/payment-methods', { code, name, rules: rules.map(([dueDays, percentage]) => ({ dueDays, percentage })) })
  }
  step('Formas de pago.')
  return methods
}

async function seedPeople() {
  const salespeople = []
  for (const [code, name, email, phone, commissionPercentage] of SALESPEOPLE) {
    salespeople.push(await ensure('/api/v1/salespeople', code, () => api('POST', '/api/v1/salespeople', { code, name, email, phone, commissionPercentage, active: true })))
  }
  const customers = {}
  const knownCustomers = new Map(content(await api('GET', '/api/v1/customers?size=200')).map((item) => [item.code, item]))
  for (const [code, legalName, tradeName, addressLine1, city, postalCode, region, phone, sp, n, contact, position] of CUSTOMERS) {
    if (knownCustomers.has(code)) { customers[code] = knownCustomers.get(code); continue }
    const customer = await api('POST', '/api/v1/customers', {
      code, legalName, tradeName, taxId: cif(n), taxIdentificationType: 'NIF', taxCountryCode: 'ES', phone,
      email: `pedidos@${code.toLowerCase()}.nevada-demo.example`,
      observations: 'Cliente inventado de la demo.', active: true, calculationMultiplier: 1,
      creditLimit: 6000 + between(0, 8) * 1000, riskWarningThreshold: 5000, riskPolicy: 'WARN',
      details: { addressLine1, city, region, postalCode, countryCode: 'ES' },
      classification: { salespersonId: salespeople[sp].id },
    })
    customers[code] = customer
    const base = `/api/v1/customers/${customer.id}`
    await api('POST', `${base}/contacts`, { name: contact, position, phone, mobile: `+34 6${between(10, 99)} ${between(100, 999)} ${between(100, 999)}`, email: null, notes: null, primaryContact: true })
    if (random() < 0.5) await api('POST', `${base}/addresses`, { label: 'Almacén', line1: `${addressLine1} (puerta trasera)`, line2: null, postalCode, city, province: region, country: 'ES', contactPhone: phone, primaryAddress: true, active: true })
    if (random() < 0.4) await api('POST', `${base}/notes`, { title: 'Horario de descarga', message: 'Entregar de 8:00 a 11:00. Avisar por teléfono al llegar.', showOnDocuments: true })
  }
  const suppliers = {}
  const knownSuppliers = new Map(content(await api('GET', '/api/v1/suppliers?size=200')).map((item) => [item.code, item]))
  for (const [code, legalName, tradeName, phone, n] of SUPPLIERS) {
    if (knownSuppliers.has(code)) { suppliers[code] = knownSuppliers.get(code); continue }
    suppliers[code] = await api('POST', '/api/v1/suppliers', {
      code, legalName, tradeName, taxId: cif(n), phone, email: null, observations: 'Proveedor inventado de la demo.',
      carrier: null, route: null, defaultPaymentMethodId: null, active: true,
      details: { addressLine1: 'Polígono industrial, nave 3', city: 'Granada', region: 'Granada', postalCode: '18000', countryCode: 'ES' },
    })
  }
  step(`${salespeople.length} comerciales, ${Object.keys(customers).length} clientes con contactos y ${Object.keys(suppliers).length} proveedores.`)
  return { salespeople, customers, suppliers }
}

async function seedCatalogue() {
  const nature = await ensure('/api/v1/product-natures?size=200', 'DIST', () => api('POST', '/api/v1/product-natures', { code: 'DIST', name: 'Distribución', active: true }))
  const supertype = await ensure('/api/v1/product-supertypes?size=200', 'DIST-GEN', () => api('POST', '/api/v1/product-supertypes', { code: 'DIST-GEN', name: 'Distribución y tienda', natureId: nature.id, active: true }))
  const groupIds = {}
  for (const type of TYPES) {
    const savedType = await ensure('/api/v1/product-types?size=200', type.code, () => api('POST', '/api/v1/product-types', { code: type.code, name: type.name, supertypeId: supertype.id, active: true }))
    for (const [code, name] of type.groups) {
      const group = await ensure('/api/v1/product-groups?size=200', code, () => api('POST', '/api/v1/product-groups', { code, name, productTypeId: savedType.id, active: true }))
      groupIds[code] = { id: group.id, typeId: savedType.id }
    }
  }
  const products = {}
  const knownProducts = new Map(content(await api('GET', '/api/v1/products?size=200')).map((item) => [item.code, item]))
  for (const [index, [code, name, retail, taxRate, group, unit]] of PRODUCTS.entries()) {
    if (knownProducts.has(code)) { products[code] = knownProducts.get(code); continue }
    // Precio base sin IVA con cuatro decimales: al sumarle el IVA da exactamente el PVP.
    const basePrice = Math.round((retail / (1 + taxRate / 100)) * 10000) / 10000
    products[code] = await api('POST', '/api/v1/products', {
      code, name, description: null, productTypeId: groupIds[group].typeId, productGroupId: groupIds[group].id, taxCodeId: null,
      familyId: null, categoryId: null, unitOfMeasure: unit, basePrice, taxRate, active: true,
      barcode: group === 'LOGI' ? null : internalEan(5001 + index),
    })
  }
  const typeIds = {}
  for (const [code, name, description] of [['CAJA', 'Caja de cartón', 'Caja de cartón para reposición'], ['PALE', 'Palé europeo', 'Palé de 120 × 80 cm'], ['PACK', 'Pack retractilado', 'Unidades agrupadas con film']]) {
    typeIds[code] = (await ensure('/api/v1/packaging-types?size=200', code, () => api('POST', '/api/v1/packaging-types', { code, name, description, returnable: code === 'PALE', active: true }))).id
  }
  const defaults = new Set()
  for (const [productCode, typeCode, shortCode, units] of PACKAGING) {
    const product = products[productCode]
    const current = content(await api('GET', `/api/v1/product-packaging?productId=${product.id}&size=50`))
    if (current.some((item) => item.code === `${shortCode}-${productCode}`)) { defaults.add(productCode); continue }
    await api('POST', '/api/v1/product-packaging', { productId: product.id, packagingTypeId: typeIds[typeCode], code: `${shortCode}-${productCode}`, unitsPerPackage: units, levels: null, unitsPerLevel: null, length: null, width: null, height: null, grossWeight: null, defaultPackaging: !defaults.has(productCode), active: true })
    defaults.add(productCode)
  }
  step(`Catálogo: ${PRODUCTS.length} artículos con código de barras y ${PACKAGING.length} embalajes.`)
  return products
}

async function seedWarehouses(products) {
  let warehouses = content(await api('GET', '/api/v1/warehouses?size=50'))
  if (!warehouses.some((item) => item.code === 'CENTRAL')) {
    await api('POST', '/api/v1/warehouses', { code: 'CENTRAL', name: 'Almacén central', location: 'Polígono Juncaril, Albolote', defaultWarehouse: !warehouses.some((item) => item.defaultWarehouse), active: true })
  }
  if (!warehouses.some((item) => item.code === 'TIENDA')) {
    await api('POST', '/api/v1/warehouses', { code: 'TIENDA', name: 'Tienda (mostrador)', location: 'Calle Mesones 12, Granada', defaultWarehouse: false, active: true })
  }
  warehouses = content(await api('GET', '/api/v1/warehouses?size=50'))
  const central = warehouses.find((item) => item.defaultWarehouse) ?? warehouses.find((item) => item.code === 'CENTRAL')
  const shop = warehouses.find((item) => item.code === 'TIENDA')
  for (const [code, , , , group, unit, quantity] of PRODUCTS) {
    if (!quantity) continue
    const product = products[code]
    if (content(await api('GET', `/api/v1/stock-levels?productId=${product.id}&size=1`)).length > 0) continue
    const line = { productId: product.id, productCode: product.code, productName: product.name, unitOfMeasure: unit }
    await api('POST', '/api/v1/stock-movements/adjustments', { ...line, warehouseId: central.id, type: 'ADJUSTMENT_IN', quantity, note: 'Inventario inicial' })
    await api('PUT', '/api/v1/stock-levels/location', { ...line, warehouseId: central.id, location: ZONES[group] })
    if (shop && random() < 0.6) await api('POST', '/api/v1/stock-movements/adjustments', { ...line, warehouseId: shop.id, type: 'ADJUSTMENT_IN', quantity: Math.max(6, Math.round(quantity / 20)), note: 'Inventario inicial de la tienda' })
  }
  step('Almacenes, existencias iniciales y ubicaciones.')
  return { central, shop }
}

const line = (products, code, quantity, discount = 0) => {
  const product = products[code]
  return { productId: product.id, productCode: product.code, description: product.name, quantity, unitPrice: product.basePrice, discountPercentage: discount, taxPercentage: product.taxRate, unitPriceOverridden: false, taxPercentageOverridden: false }
}

function basket(products, customerCode, size) {
  const codes = [...BASKETS[CUSTOMER_BASKET[customerCode]]]
  const lines = []
  for (let index = 0; index < size && codes.length; index += 1) {
    const code = codes.splice(Math.floor(random() * codes.length), 1)[0]
    const unit = products[code].unitOfMeasure
    const quantity = unit === 'KILOGRAM' ? between(5, 25) : pick([6, 12, 12, 24, 24, 36, 48])
    lines.push(line(products, code, quantity, pick([0, 0, 3, 5, 5, 8])))
  }
  if (random() < 0.35) lines.push(line(products, 'SRV-PORTE', 1))
  return lines
}

const documentBody = (type, customer, issueDate, lines, notes, paymentMethod) => ({
  type, customerId: customer.id, customerCode: customer.code, customerName: customer.legalName, issueDate, currency: 'EUR',
  paymentMethodId: paymentMethod?.id ?? null, notes, confirm: true, lines, salespersonId: customer.classification?.salespersonId ?? null,
})

async function seedPurchases(products, suppliers, central) {
  // Un pedido por proveedor y mes que pasa a albarán de entrada y a factura; el último queda pendiente.
  const productsBySupplier = {}
  for (const [code, , , , , , quantity, supplier] of PRODUCTS) if (supplier && quantity) (productsBySupplier[supplier] ??= []).push(code)
  if (content(await api('GET', '/api/v1/purchase-documents?size=1')).length > 0) { step('Compras ya cargadas.'); return }
  let count = 0
  for (let monthsAgo = 4; monthsAgo >= 0; monthsAgo -= 1) {
    for (const [index, code] of Object.keys(suppliers).entries()) {
      if (random() < 0.35) continue
      const supplier = suppliers[code]
      let date = new Date(today.getFullYear(), today.getMonth() - monthsAgo, 3 + index * 4, 12)
      if (date > today) date = addDays(today, -1)
      const codes = productsBySupplier[code]
      const lines = []
      for (let item = 0; item < between(3, 6); item += 1) {
        const product = products[pick(codes)]
        if (lines.some((existing) => existing.productId === product.id)) continue
        lines.push({ productId: product.id, productCode: product.code, description: product.name, unitOfMeasure: product.unitOfMeasure, quantity: pick([48, 96, 120, 240]), unitPrice: Math.round(product.basePrice * 0.62 * 10000) / 10000, discountPercentage: 0, taxPercentage: product.taxRate })
      }
      const order = await api('POST', '/api/v1/purchase-documents', { type: 'PURCHASE_ORDER', supplierId: supplier.id, supplierCode: supplier.code, supplierName: supplier.legalName, supplierTaxId: supplier.taxId, supplierReference: `PED-${iso(date).replaceAll('-', '')}-${index + 1}`, issueDate: iso(date), expectedDate: iso(addDays(date, 4)), warehouseId: central.id, currencyCode: 'EUR', notes: 'Reposición mensual', lines })
      await api('POST', `/api/v1/purchase-documents/${order.id}/confirm`)
      count += 1
      if (monthsAgo === 0 && index >= 3) continue
      const receiptDate = addDays(date, 3) > today ? today : addDays(date, 3)
      const receipt = await api('POST', `/api/v1/purchase-documents/${order.id}/convert`, { targetType: 'GOODS_RECEIPT', issueDate: iso(receiptDate) })
      if (receipt.status !== 'CONFIRMED') await api('POST', `/api/v1/purchase-documents/${receipt.id}/confirm`).catch(() => undefined)
      if (monthsAgo === 0) continue
      const invoice = await api('POST', `/api/v1/purchase-documents/${receipt.id}/convert`, { targetType: 'SUPPLIER_INVOICE', issueDate: iso(addDays(receiptDate, 2)) })
      if (invoice.status !== 'CONFIRMED') await api('POST', `/api/v1/purchase-documents/${invoice.id}/confirm`).catch(() => undefined)
    }
  }
  step(`Compras: ${count} pedidos con sus albaranes de entrada y facturas de proveedor.`)
}

async function seedSales(products, customers, methods) {
  // Facturas de los últimos cinco meses, en orden de fecha (la cadena Veri*Factu va en orden).
  const existing = content(await api('GET', '/api/v1/documents?type=INVOICE&size=500'))
  if (existing.length > 0) { step(`Ventas ya cargadas: ${existing.length} facturas.`); return existing.sort((a, b) => a.number.localeCompare(b.number)) }
  const customerCodes = Object.keys(customers)
  const perMonth = [9, 10, 12, 13, 15]
  const invoices = []
  for (let monthsAgo = 4; monthsAgo >= 0; monthsAgo -= 1) {
    const first = new Date(today.getFullYear(), today.getMonth() - monthsAgo, 1, 12)
    const last = monthsAgo === 0 ? addDays(today, -1) : new Date(today.getFullYear(), today.getMonth() - monthsAgo + 1, 0, 12)
    const days = []
    for (let day = new Date(first); day <= last; day = addDays(day, 1)) if (!isWeekend(day)) days.push(iso(day))
    if (!days.length) continue
    const count = monthsAgo === 0 ? Math.max(3, Math.round(perMonth[4] * days.length / 22)) : perMonth[4 - monthsAgo]
    const dates = Array.from({ length: count }, () => pick(days)).sort()
    for (const date of dates) {
      const code = pick(customerCodes)
      const method = pick([methods['TRANSF-30'], methods['TRANSF-30'], methods['RECIBO-30-60'], methods.CONTADO])
      const invoice = await api('POST', '/api/v1/documents', documentBody('INVOICE', customers[code], date, basket(products, code, between(3, 6)), pick(['Pedido semanal', 'Reposición de cámara', 'Pedido para el fin de semana', 'Pedido mensual', null]), method))
      invoices.push(invoice)
    }
  }
  step(`Ventas: ${invoices.length} facturas expedidas con su registro Veri*Factu.`)
  return invoices
}

async function seedCollections(invoices) {
  // Vencimientos y recibos de cada factura; lo vencido hace más de una semana, casi todo cobrado.
  let collected = 0, pending = 0, returned = 0
  for (const invoice of invoices) {
    if (!invoice.paymentMethodId) continue
    if (content(await api('GET', `/api/v1/receipts/by-document/${invoice.id}`)).length > 0) continue
    let receipts
    try {
      const dueDates = await api('GET', `/api/v1/due-dates?documentId=${invoice.id}`)
      if (content(dueDates).length === 0) {
        await api('POST', '/api/v1/due-dates/generate', { documentId: invoice.id, paymentMethodId: invoice.paymentMethodId, issueDate: invoice.issueDate, totalAmount: invoice.totalAmount })
      }
      receipts = await api('POST', '/api/v1/receipts/issue', { documentId: invoice.id, documentNumber: invoice.number, customerId: invoice.customerId, customerCode: invoice.customerCode, customerName: invoice.customerName, currencyCode: 'EUR' })
    } catch (error) {
      console.log(`  Recibos de ${invoice.number} omitidos: ${error.message}`)
      continue
    }
    for (const receipt of content(receipts)) {
      const due = new Date(`${receipt.dueDate}T12:00:00`)
      if (due < addDays(today, -7) && random() < 0.88) {
        const method = pick(['BANK_TRANSFER', 'BANK_TRANSFER', 'DIRECT_DEBIT', 'CARD'])
        await api('POST', `/api/v1/receipts/${receipt.id}/collect`, { collectionDate: iso(due), method, cashSessionId: null, notes: null })
        collected += 1
        if (returned === 0 && method === 'DIRECT_DEBIT') {
          await api('POST', `/api/v1/receipts/${receipt.id}/return`, { returnDate: iso(addDays(due, 3) > today ? today : addDays(due, 3)), reason: 'Devuelto por el banco: cuenta sin saldo' }).catch((error) => console.log(`  ${error.message}`))
          returned += 1
        }
      } else pending += 1
    }
  }
  step(`Cartera: ${collected} recibos cobrados, ${pending} pendientes y ${returned} devuelto.`)
}

async function seedQuotesAndNotes(products, customers, methods) {
  const codes = Object.keys(customers)
  const act = async (fn) => { try { await fn() } catch (error) { console.log(`  ${error.message}`) } }
  const quotes = content(await api('GET', '/api/v1/documents?type=QUOTE&size=50')).sort((a, b) => a.number.localeCompare(b.number))
  if (quotes.length === 0) {
    // Al confirmarlos quedan enviados al cliente.
    for (let index = 0; index < 8; index += 1) {
      const code = codes[(index * 5) % codes.length]
      quotes.push(await api('POST', '/api/v1/documents', documentBody('QUOTE', customers[code], iso(addDays(today, -between(1, 20))), basket(products, code, between(4, 7)), pick(['Propuesta para la temporada de invierno', 'Presupuesto para el evento', 'Renovación de suministros', 'Tarifa especial por volumen']), methods['TRANSF-30'])))
    }
  }
  // Uno aceptado y convertido, otro aceptado y uno rechazado; el resto, enviados.
  if (quotes.every((quote) => quote.quoteStatus === 'SENT' || quote.status === 'DRAFT')) {
    await act(async () => { await api('POST', `/api/v1/quotes/${quotes[0].id}/accept`); await api('POST', `/api/v1/quotes/${quotes[0].id}/convert?riskAcknowledged=true`) })
    await act(() => api('POST', `/api/v1/quotes/${quotes[1].id}/accept`))
    await act(() => api('POST', `/api/v1/quotes/${quotes[4].id}/reject`, { reason: 'El cliente ha elegido otro proveedor por precio' }))
  }
  // Dos en borrador, todavía sin enviar.
  if (!quotes.some((quote) => quote.status === 'DRAFT')) {
    for (const code of ['C-007', 'C-015']) {
      await act(() => api('POST', '/api/v1/documents', { ...documentBody('QUOTE', customers[code], TODAY, basket(products, code, between(3, 5)), 'Borrador pendiente de revisar', methods['TRANSF-30']), confirm: false }))
    }
  }

  // Albaranes de hoy: dos pasan a factura y uno queda pendiente de facturar.
  if (content(await api('GET', '/api/v1/documents?type=DELIVERY_NOTE&size=1')).length === 0) {
    for (const [index, code] of ['C-002', 'C-008', 'C-010'].entries()) {
      const note = await api('POST', '/api/v1/documents', documentBody('DELIVERY_NOTE', customers[code], TODAY, basket(products, code, between(3, 5)), 'Entrega de hoy', methods['TRANSF-30']))
      if (index < 2) await act(() => api('POST', `/api/v1/documents/${note.id}/convert?riskAcknowledged=true`))
    }
  }
  step('Presupuestos en todos los estados y albaranes de hoy.')
}

async function seedCommissions() {
  for (let monthsAgo = 4; monthsAgo >= 0; monthsAgo -= 1) {
    const first = new Date(today.getFullYear(), today.getMonth() - monthsAgo, 1, 12)
    const last = new Date(today.getFullYear(), today.getMonth() - monthsAgo + 1, 0, 12)
    await api('POST', '/api/v1/commissions/calculate', { salespersonId: null, fromDate: iso(first), toDate: iso(last) })
    // Lo de hace más de un mes ya está liquidado.
    if (monthsAgo >= 2) {
      const page = await api('GET', `/api/v1/commissions?fromDate=${iso(first)}&toDate=${iso(last)}&size=200`).catch(() => null)
      const ids = content(page).filter((item) => !item.settled && item.status !== 'SETTLED').map((item) => item.id)
      if (ids.length) await api('POST', '/api/v1/commissions/settle', { ids, settledOn: iso(addDays(last, 5)), note: 'Liquidación mensual' }).catch((error) => console.log(`  ${error.message}`))
    }
  }
  step('Comisiones calculadas por mes y liquidadas las antiguas.')
}

async function seedClaims(customers, invoices) {
  const knownClaims = content(await api('GET', '/api/v1/claims?size=50'))
  const knownCatalog = content(await api('GET', '/api/v1/claim-catalog'))
  const catalog = {}
  const entries = {
    REASON: ['Producto dañado', 'Entrega incompleta', 'Retraso en la entrega', 'Error de facturación'],
    NONCONFORMITY: ['Embalaje roto', 'Unidades de menos', 'Precio distinto al presupuesto'],
    CAUSE: ['Manipulación en el reparto', 'Error de preparación', 'Tarifa sin actualizar'],
    AREA: ['Almacén', 'Reparto', 'Administración'],
    RESPONSIBLE: ['Jefe de almacén', 'Responsable de reparto', 'Administración'],
    RESOLUTION: ['Reposición sin cargo', 'Abono en la próxima factura', 'Disculpas y seguimiento'],
    PREVENTIVE_ACTION: ['Revisar el flejado de palés', 'Doble comprobación del pedido', 'Actualizar tarifas cada trimestre'],
  }
  for (const [kind, names] of Object.entries(entries)) {
    catalog[kind] = []
    for (const name of names) catalog[kind].push(knownCatalog.find((item) => item.kind === kind && item.name === name) ?? await api('POST', '/api/v1/claim-catalog', { kind, name, active: true }))
  }
  const ids = (kind, index) => catalog[kind][index]?.id ?? null
  const cases = [
    ['C-001', 'Llegaron dos cajas de cerveza con latas abolladas.', 0, 0, 0, 1, 1, 0, 0, true],
    ['C-002', 'Faltaban 12 botellas de zumo en la entrega del lunes.', 1, 1, 1, 0, 0, 0, 1, false],
    ['C-006', 'La factura aplica el precio antiguo del café en cápsulas.', 3, 2, 2, 2, 2, 1, 2, true],
    ['C-012', 'El reparto llegó a las 13:00, fuera del horario de descarga.', 2, null, 0, 1, 1, 2, null, false],
  ]
  for (const [index, [code, description, reason, nonconformity, cause, area, responsible, resolution, preventive, close]] of cases.entries()) {
    if (knownClaims.some((known) => known.description === description)) continue
    const customer = customers[code]
    const claimDate = iso(addDays(today, -(index * 6 + 2)))
    // La factura de origen tiene que ser anterior a la reclamación.
    const source = invoices.filter((invoice) => invoice.customerId === customer.id && invoice.issueDate <= claimDate).at(-1)
    const claim = await api('POST', '/api/v1/claims', {
      claimDate, customerId: customer.id, customerCode: customer.code, customerName: customer.legalName,
      sourceDocumentId: source?.id ?? null, sourceDocumentNumber: source?.number ?? null, sourceDocumentDate: source?.issueDate ?? null,
      description, reasonId: ids('REASON', reason), nonconformityId: nonconformity === null ? null : ids('NONCONFORMITY', nonconformity),
      causeId: ids('CAUSE', cause), areaId: ids('AREA', area), responsibleId: ids('RESPONSIBLE', responsible),
      resolutionId: ids('RESOLUTION', resolution), preventiveActionId: preventive === null ? null : ids('PREVENTIVE_ACTION', preventive),
      followUpDate: iso(addDays(today, 3 + index)), lines: [],
    })
    await api('POST', `/api/v1/claims/${claim.id}/comments`, { text: 'Hablado con el cliente por teléfono; se revisa con el equipo.' })
    if (close) await api('POST', `/api/v1/claims/${claim.id}/close`, { closedOn: iso(addDays(today, -index)), closingNote: 'Resuelto a satisfacción del cliente.' })
  }
  step('Reclamaciones con su clasificación, comentarios y cierres.')
}

async function seedAgenda(customers, salespeople) {
  const knownTypes = await api('GET', '/api/v1/agenda-entry-types')
  if (knownTypes.some((type) => type.name === 'Reunión interna')) { step('Agenda ya cargada.'); return }
  const types = {}
  for (const [name, color] of [['Visita comercial', '#209a4b'], ['Llamada', '#496f7e'], ['Entrega', '#9b722d'], ['Reunión interna', '#7a5aa6']]) {
    types[name] = knownTypes.find((type) => type.name === name) ?? await api('POST', '/api/v1/agenda-entry-types', { name, color, active: true })
  }
  const monday = addDays(today, -((today.getDay() + 6) % 7))
  const plan = [
    [0, '09:00', '10:00', 'Visita: carta de vinos de otoño', 'Visita comercial', 'C-001', 0],
    [0, '12:30', '13:00', 'Confirmar pedido del fin de semana', 'Llamada', 'C-004', 1],
    [1, '08:30', '09:30', 'Entrega de palé de agua', 'Entrega', 'C-002', null],
    [1, '16:00', '17:00', 'Presentar tarifa de oficinas', 'Visita comercial', 'C-006', 2],
    [2, '10:00', '11:00', 'Revisión de la reclamación del zumo', 'Llamada', 'C-002', 0],
    [2, '17:30', '18:30', 'Planificación de rutas de reparto', 'Reunión interna', null, null],
    [3, '09:30', '10:30', 'Degustación de café para la escuela', 'Visita comercial', 'C-016', 0],
    [3, '12:00', '12:30', 'Seguimiento del presupuesto de invierno', 'Llamada', 'C-010', 0],
    [4, '08:00', '09:00', 'Entrega en Almuñécar', 'Entrega', 'C-012', null],
    [4, '13:00', '14:00', 'Cierre de comisiones del mes', 'Reunión interna', null, null],
    [7, '10:00', '11:00', 'Visita: nuevos productos de droguería', 'Visita comercial', 'C-011', 1],
    [8, '09:00', '09:30', 'Llamada de bienvenida', 'Llamada', 'C-014', 2],
    [9, '16:30', '17:30', 'Kit de material para el gimnasio', 'Visita comercial', 'C-005', 2],
  ]
  for (const [offset, startTime, endTime, title, type, code, sp] of plan) {
    const customer = code ? customers[code] : null
    await api('POST', '/api/v1/agenda-entries', { date: iso(addDays(monday, offset)), startTime, endTime, title, details: null, typeId: types[type].id, assigneeName: sp === null ? 'Equipo de reparto' : salespeople[sp].name, customerId: customer?.id ?? null, customerName: customer?.legalName ?? null, contactId: null, location: customer ? `${customer.details?.addressLine1 ?? ''}` : 'Oficina', documentReference: null })
  }
  for (const [name, organization, phone, city] of [['Gestoría Fiscal Albaida (demo)', 'Asesoría', '+34 958 000 201', 'Granada'], ['Taller Furgonetas Genil (demo)', 'Mantenimiento de vehículos', '+34 958 000 202', 'Armilla'], ['Banco del Sur, oficina 0042 (demo)', 'Banco', '+34 958 000 203', 'Granada']]) {
    await api('POST', '/api/v1/contacts', { name, organization, phone, mobile: null, email: null, address: null, postalCode: null, city, region: 'Granada', customerId: null, customerName: null, notes: null }).catch((error) => console.log(`  ${error.message}`))
  }
  step('Agenda de esta semana y la siguiente, y listín.')
}

async function seedShop(products) {
  // Ventas de hoy en las dos cajas del mostrador y movimientos del almacén desde el móvil.
  const existing = await api('GET', '/api/v1/cash-registers')
  if (existing.some((item) => item.code === 'CAJA-1')) { step('Tienda ya cargada.'); return }
  for (const [code, name] of [['CAJA-1', 'Caja 1 (mostrador)'], ['CAJA-2', 'Caja 2 (tienda)']]) {
    if (!existing.some((item) => item.code === code)) await api('POST', '/api/v1/cash-registers', { code, name, ownerName: null, active: true })
  }
  // Abre la marca de lectura de tickets en el almacén antes de vender, para que estas ventas descuenten.
  await api('GET', '/api/v1/sales-deliveries?size=1').catch(() => undefined)
  await sleep(1500)
  const registers = await api('GET', '/api/v1/pos/registers')
  const shopCodes = PRODUCTS.filter(([, , , , group, , quantity]) => quantity && group !== 'LOGI').map(([code]) => code)
  let sold = 0
  for (const [registerCode, count, opening] of [['CAJA-1', 16, 150], ['CAJA-2', 9, 120]]) {
    const register = registers.find((item) => item.code === registerCode)
    if (!register) continue
    const sessionId = register.openSession?.id ?? (await api('POST', `/api/v1/pos/registers/${register.id}/open`, { openingAmount: opening })).sessionId
    const tickets = []
    for (let index = 0; index < count; index += 1) {
      const lines = []
      for (let item = 0; item < between(1, 5); item += 1) {
        const code = pick(shopCodes)
        if (lines.some((existingLine) => existingLine.productId === products[code].id)) continue
        const kilos = products[code].unitOfMeasure === 'KILOGRAM'
        lines.push({ productId: products[code].id, quantity: kilos ? Math.round((0.4 + random() * 1.6) * 1000) / 1000 : between(1, 3), unitPrice: null, discountPercent: null })
      }
      const roll = random()
      const method = roll < 0.5 ? 'CASH' : roll < 0.9 || lines.length < 3 ? 'CARD' : 'MIXED'
      try {
        tickets.push(await api('POST', '/api/v1/pos/tickets', { sessionId, lines, discountPercent: null, paymentMethod: method, cashTendered: null, cardAmount: method === 'MIXED' ? 5 : null, customerName: null, customerTaxId: null, note: null, clientReference: null }))
        sold += 1
      } catch (error) { console.log(`  Ticket omitido: ${error.message}`) }
      await sleep(40)
    }
    if (registerCode === 'CAJA-1' && tickets.length) {
      const sale = tickets.find((ticket) => ticket.lines.some((ticketLine) => ticketLine.quantity >= 2))
      if (sale) {
        const refundLine = sale.lines.find((ticketLine) => ticketLine.quantity >= 2)
        await api('POST', `/api/v1/pos/tickets/${sale.id}/refund`, { sessionId, lines: [{ lineId: refundLine.id, quantity: 1 }], paymentMethod: sale.paymentMethod === 'CARD' ? 'CARD' : 'CASH', reason: 'El cliente se llevó una unidad de más', clientReference: null }).catch((error) => console.log(`  ${error.message}`))
      }
      await api('POST', `/api/v1/pos/sessions/${sessionId}/movements`, { type: 'WITHDRAWAL', amount: 50, concept: 'Retirada a caja fuerte' }).catch((error) => console.log(`  ${error.message}`))
    }
  }
  const scans = [
    ['IN', 'SARD-120', 48, 'RECEIPT', 'Albarán del proveedor · 2 cajas'],
    ['IN', 'AGUA-150', 120, 'RECEIPT', 'Albarán del proveedor'],
    ['OUT', 'HUEV-12', 2, 'DAMAGED', 'Se cayó la caja al descargar'],
    ['OUT', 'YOGU-P4', 3, 'EXPIRED', 'Caducados'],
    ['IN', 'CERV-33', 72, 'RECEIPT', '3 cajas de 24'],
    ['OUT', 'PAN-BARRA', 6, 'SPOILED', 'Pan del día anterior'],
    ['IN', 'PILA-AA4', 10, 'CUSTOMER_RETURN', 'Devolución de cliente sin abrir'],
    ['IN', 'LECH-1L', 36, 'COUNT', 'Recuento de la cámara'],
  ]
  for (const [direction, code, quantity, reason, note] of scans) {
    const product = products[code]
    await api('POST', '/api/v1/stock-movements/scan', { warehouseId: null, productId: product.id, productCode: product.code, productName: product.name, unitOfMeasure: product.unitOfMeasure, direction, quantity, reason, note, clientReference: null }).catch((error) => console.log(`  ${error.message}`))
  }
  step(`Tienda: ${sold} tickets de hoy en dos cajas y ${scans.length} movimientos del almacén móvil.`)
}

async function main() {
  const company = await enter()
  console.log(`Demo: ${company.name} (${company.code})`)
  await configureCompany()
  const methods = await seedPaymentMethods()
  const { salespeople, customers, suppliers } = await seedPeople()
  const products = await seedCatalogue()
  const { central } = await seedWarehouses(products)
  await seedPurchases(products, suppliers, central)
  const invoices = await seedSales(products, customers, methods)
  await seedCollections(invoices)
  await seedQuotesAndNotes(products, customers, methods)
  await seedCommissions()
  await seedClaims(customers, invoices)
  await seedAgenda(customers, salespeople)
  await seedShop(products)
  console.log('Listo. Abre la demo y recórrela como visitante.')
}

main().catch((error) => { console.error(error.message); process.exit(1) })
