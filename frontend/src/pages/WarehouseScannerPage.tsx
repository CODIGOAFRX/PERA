import { ArrowLeft, Boxes, Camera, Check, ChevronRight, CircleUserRound, ClipboardList, Home, Keyboard, LogOut, MapPin, Minus, PackageMinus, PackagePlus, Plus, ScanBarcode, Search, Tags, Warehouse as WarehouseIcon } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { CameraScanner } from '../components/CameraScanner'
import { LoadingState } from '../components/DataState'
import { LanguageSelector } from '../components/LanguageSelector'
import { PearBrandMark } from '../components/PearBrandMark'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { unitKey } from '../i18n/businessLabels'
import { apiFetch, ApiError, errorMessage } from '../lib/api'
import { formatDateTime, formatNumber } from '../lib/format'
import { newClientReference } from '../lib/pos'
import { scanFeedback, useHardwareScanner } from '../lib/scanner'
import { REASONS, reasonLabels, type StockReason } from '../lib/stockReasons'
import { Link } from '../routing/Router'
import { LOCATION_EXAMPLES, saveStockLocation } from '../components/StockLocationForm'
import type { FlatPage, Product, StockLevel, StockMovement, UnitOfMeasure, Warehouse } from '../types/api'

type Direction = 'IN' | 'OUT'
type Screen = { name: 'home' } | { name: 'identify'; direction: Direction | 'LOOKUP' } | { name: 'product'; direction: Direction | 'LOOKUP'; product: ScannedProduct } | { name: 'done'; direction: Direction; movement: StockMovement; product: ScannedProduct } | { name: 'history' }
type ScannedProduct = Product & { barcode?: string | null }
interface Packaging { id: string; code: string; unitsPerPackage: number; defaultPackaging: boolean; active: boolean }

const WAREHOUSE_KEY = 'pera.warehouseApp.warehouse'
function startOfToday() {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  return date.toISOString()
}

/**
 * Aplicación de almacén para el móvil: añadir o retirar mercancía escaneando su código de barras
 * o QR (con la cámara o con un lector Bluetooth) o escribiéndolo. Todo queda en el diario de
 * almacén con el operario, la hora y el motivo.
 */
export function WarehouseScannerPage() {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { identity, logout, hasPermission } = useAuth()
  const { notify } = useToast()
  const [warehouses, setWarehouses] = useState<Warehouse[] | null>(null)
  const [warehouseId, setWarehouseId] = useState('')
  const [screen, setScreen] = useState<Screen>({ name: 'home' })
  const [camera, setCamera] = useState(false)
  const [manual, setManual] = useState(false)
  const [code, setCode] = useState('')
  const [looking, setLooking] = useState(false)
  const [lookupError, setLookupError] = useState('')
  const [today, setToday] = useState<StockMovement[]>([])
  const onlyWarehouseRole = (identity?.roles ?? []).every((role) => role.toUpperCase() === 'WAREHOUSE')
  const canLabels = hasPermission('products:read')

  useEffect(() => {
    apiFetch<FlatPage<Warehouse>>('/api/v1/warehouses?size=200&sort=code,asc').then((page) => {
      const active = page.content.filter((item) => item.active)
      setWarehouses(active)
      let stored = ''
      try { stored = localStorage.getItem(WAREHOUSE_KEY) ?? '' } catch { stored = '' }
      setWarehouseId(active.find((item) => item.id === stored)?.id ?? active.find((item) => item.defaultWarehouse)?.id ?? active[0]?.id ?? '')
    }).catch((cause) => { setWarehouses([]); notify(errorMessage(cause), 'error') })
  }, [notify])
  useEffect(() => { if (warehouseId) try { localStorage.setItem(WAREHOUSE_KEY, warehouseId) } catch { /* preferencia solo en memoria */ } }, [warehouseId])

  const loadToday = useCallback(() => {
    apiFetch<FlatPage<StockMovement>>(`/api/v1/stock-movements?mine=true&occurredFrom=${encodeURIComponent(startOfToday())}&sort=occurredAt,desc&size=200`)
      .then((page) => setToday(page.content)).catch(() => undefined)
  }, [])
  useEffect(() => { loadToday() }, [loadToday])

  const warehouse = warehouses?.find((item) => item.id === warehouseId)
  const lookup = useCallback(async (raw: string, direction: Direction | 'LOOKUP') => {
    const value = raw.trim()
    if (!value) return
    setLooking(true); setLookupError('')
    try {
      const product = await apiFetch<ScannedProduct>(`/api/v1/products/lookup?code=${encodeURIComponent(value)}`)
      scanFeedback(true)
      setCode(''); setManual(false)
      setScreen({ name: 'product', direction, product })
    } catch (cause) {
      scanFeedback(false)
      setLookupError(cause instanceof ApiError && cause.status === 404 ? c(`No hay ningún artículo con el código «${value}».`, `There is no product with code “${value}”.`) : errorMessage(cause))
    } finally { setLooking(false) }
  }, [language])

  const currentDirection: Direction | 'LOOKUP' = screen.name === 'identify' || screen.name === 'product' ? screen.direction : 'LOOKUP'
  useHardwareScanner((scanned) => void lookup(scanned, currentDirection), { enabled: !camera && (screen.name === 'home' || screen.name === 'identify' || screen.name === 'done' || screen.name === 'history') })

  const entries = today.filter((movement) => movement.type === 'ADJUSTMENT_IN' || movement.type === 'PURCHASE_RECEIPT' || movement.type === 'TRANSFER_IN' || movement.type === 'SALES_RETURN').length
  const exits = today.length - entries

  const header = <header className="wh-header">
    {screen.name === 'home' ? <div className="pos-brand"><PearBrandMark /><span><strong>PERA</strong><small>{c('Almacén', 'Warehouse')}</small></span></div>
      : <button type="button" className="wh-back" onClick={() => { setScreen({ name: 'home' }); setLookupError(''); setManual(false) }} aria-label={c('Volver al inicio', 'Back to start')}><ArrowLeft size={22} /></button>}
    <label className="wh-warehouse"><WarehouseIcon size={16} /><span className="sr-only">{c('Almacén', 'Warehouse')}</span>
      <select value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)} aria-label={c('Almacén en el que trabajas', 'Warehouse you work in')}>{(warehouses ?? []).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
    </label>
    <button type="button" className="wh-icon" onClick={logout} aria-label={c('Cerrar sesión', 'Sign out')}><LogOut size={20} /></button>
  </header>

  if (warehouses === null) return <div className="wh-app">{header}<LoadingState /></div>
  if (warehouses.length === 0) return <div className="wh-app">{header}<div className="wh-body"><p className="wh-message">{c('No hay almacenes activos. Pide a un responsable que cree uno en PERA ERP → Almacén.', 'There are no active warehouses. Ask a manager to create one in PERA ERP → Inventory.')}</p></div></div>

  return <div className="wh-app">
    {header}
    <main className="wh-body">
      {screen.name === 'home' && <>
        <div className="wh-hello">
          <span><CircleUserRound size={18} />{identity?.displayName}</span>
          <h1>{c('¿Qué vas a hacer?', 'What are you doing?')}</h1>
          <p>{c(`Hoy llevas ${entries} entradas y ${exits} salidas en el almacén.`, `Today you have ${entries} entries and ${exits} exits.`)}</p>
        </div>
        <div className="wh-actions">
          <button type="button" className="wh-action in" onClick={() => setScreen({ name: 'identify', direction: 'IN' })}><PackagePlus size={40} /><strong>{c('Añadir producto', 'Add stock')}</strong><small>{c('Entra mercancía', 'Goods come in')}</small></button>
          <button type="button" className="wh-action out" onClick={() => setScreen({ name: 'identify', direction: 'OUT' })}><PackageMinus size={40} /><strong>{c('Retirar producto', 'Remove stock')}</strong><small>{c('Rotura, caducado, merma…', 'Broken, expired, loss…')}</small></button>
        </div>
        <div className="wh-links">
          <button type="button" onClick={() => setScreen({ name: 'identify', direction: 'LOOKUP' })}><Search size={20} /><span>{c('Consultar existencias', 'Check stock')}</span><ChevronRight size={18} /></button>
          <button type="button" onClick={() => { loadToday(); setScreen({ name: 'history' }) }}><ClipboardList size={20} /><span>{c('Lo que he hecho hoy', 'My activity today')}</span><b>{today.length}</b><ChevronRight size={18} /></button>
          {canLabels && <Link to="/etiquetas"><Tags size={20} /><span>{c('Imprimir etiquetas', 'Print labels')}</span><ChevronRight size={18} /></Link>}
          {!onlyWarehouseRole && <Link to="/"><Home size={20} /><span>{c('Volver a PERA ERP', 'Back to PERA ERP')}</span><ChevronRight size={18} /></Link>}
        </div>
        <p className="wh-tip"><ScanBarcode size={16} />{c('Con un lector Bluetooth emparejado puedes escanear directamente en esta pantalla.', 'With a paired Bluetooth scanner you can scan straight from this screen.')}</p>
        <div className="wh-footer"><LanguageSelector compact /></div>
      </>}

      {screen.name === 'identify' && <section className="wh-identify">
        <span className={`wh-mode ${screen.direction === 'IN' ? 'in' : screen.direction === 'OUT' ? 'out' : ''}`}>{screen.direction === 'IN' ? c('Añadir producto', 'Add stock') : screen.direction === 'OUT' ? c('Retirar producto', 'Remove stock') : c('Consultar existencias', 'Check stock')}</span>
        <h1>{c('Identifica el artículo', 'Identify the product')}</h1>
        <button type="button" className="wh-big-button" onClick={() => setCamera(true)}><Camera size={34} /><span><strong>{c('Escanear con la cámara', 'Scan with the camera')}</strong><small>{c('Código de barras o código QR', 'Barcode or QR code')}</small></span></button>
        <button type="button" className={`wh-big-button secondary ${manual ? 'active' : ''}`} onClick={() => setManual((value) => !value)} aria-expanded={manual}><Keyboard size={30} /><span><strong>{c('Introducir a mano', 'Type it in')}</strong><small>{c('El número del código o la referencia', 'The code number or reference')}</small></span></button>
        {manual && <form className="wh-manual" onSubmit={(event: FormEvent) => { event.preventDefault(); void lookup(code, screen.direction) }}>
          <input autoFocus value={code} onChange={(event) => setCode(event.target.value)} inputMode="text" autoComplete="off" aria-label={c('Código del artículo', 'Product code')} placeholder={c('Ej.: 2000000000015 o SARD-120', 'E.g. 2000000000015 or SARD-120')} />
          <button type="submit" className="button button-primary" disabled={!code.trim() || looking}>{looking ? c('Buscando…', 'Searching…') : c('Buscar', 'Search')}</button>
        </form>}
        {looking && !manual && <LoadingState label={c('Buscando el artículo…', 'Looking up the product…')} />}
        {lookupError && <div className="inline-error" role="alert">{lookupError}</div>}
      </section>}

      {screen.name === 'product' && <ProductScreen key={`${screen.product.id}-${screen.direction}`} product={screen.product} direction={screen.direction} warehouse={warehouse} warehouses={warehouses} language={language} locale={locale} unitLabel={(unit) => unit in unitKey ? t(unitKey[unit as UnitOfMeasure]) : unit}
        onChoose={(direction) => setScreen({ name: 'product', direction, product: screen.product })}
        onDone={(movement, direction) => { loadToday(); setScreen({ name: 'done', direction, movement, product: screen.product }) }} />}

      {screen.name === 'done' && <section className="wh-done" aria-live="polite">
        <span className={`wh-done-icon ${screen.direction === 'IN' ? 'in' : 'out'}`}><Check size={44} /></span>
        <h1>{screen.direction === 'IN' ? c('Entrada registrada', 'Entry recorded') : c('Retirada registrada', 'Removal recorded')}</h1>
        <p><strong>{screen.direction === 'IN' ? '+' : '−'}{formatNumber(screen.movement.quantity, locale, 3)}</strong> · {screen.product.name}</p>
        <p className="wh-balance">{c('Quedan en', 'Now in')} {warehouse?.name}: <strong>{formatNumber(screen.movement.balanceAfter, locale, 3)}</strong></p>
        <button type="button" className="wh-big-button" onClick={() => setScreen({ name: 'identify', direction: screen.direction })}><ScanBarcode size={30} /><span><strong>{c('Escanear otro', 'Scan another')}</strong><small>{screen.direction === 'IN' ? c('Seguir añadiendo', 'Keep adding') : c('Seguir retirando', 'Keep removing')}</small></span></button>
        <button type="button" className="button button-ghost button-full" onClick={() => setScreen({ name: 'home' })}><Home size={18} />{c('Volver al inicio', 'Back to start')}</button>
      </section>}

      {screen.name === 'history' && <section className="wh-history">
        <h1>{c('Lo que he hecho hoy', 'My activity today')}</h1>
        {today.length === 0 ? <p className="wh-message">{c('Todavía no has registrado movimientos hoy.', 'You have not recorded any movements today.')}</p> :
          <ul>{today.map((movement) => {
            const inbound = movement.type === 'ADJUSTMENT_IN' || movement.type === 'PURCHASE_RECEIPT' || movement.type === 'TRANSFER_IN' || movement.type === 'SALES_RETURN'
            return <li key={movement.id} className={inbound ? 'in' : 'out'}>
              <b>{inbound ? '+' : '−'}{formatNumber(movement.quantity, locale, 3)}</b>
              <span><strong>{movement.productName}</strong><small>{formatDateTime(movement.occurredAt, locale)} · {movement.reasonCode ? reasonLabels[movement.reasonCode as StockReason][language === 'es' ? 0 : 1] : movement.note ?? ''}</small></span>
              <em>{warehouses.find((item) => item.id === movement.warehouseId)?.code}</em>
            </li>
          })}</ul>}
      </section>}
    </main>
    <CameraScanner open={camera} onClose={() => setCamera(false)} onDetected={(scanned) => { setCamera(false); void lookup(scanned, currentDirection) }} title={c('Escanear artículo', 'Scan product')} />
  </div>
}

function ProductScreen({ product, direction, warehouse, warehouses, language, locale, unitLabel, onChoose, onDone }: {
  product: ScannedProduct
  direction: Direction | 'LOOKUP'
  warehouse: Warehouse | undefined
  warehouses: Warehouse[]
  language: 'es' | 'en'
  locale: string
  unitLabel: (unit: string) => string
  onChoose: (direction: Direction) => void
  onDone: (movement: StockMovement, direction: Direction) => void
}) {
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { hasPermission } = useAuth()
  const [levels, setLevels] = useState<StockLevel[] | null>(null)
  const [packaging, setPackaging] = useState<Packaging[]>([])
  const [packageId, setPackageId] = useState('')
  const [count, setCount] = useState('1')
  const [reason, setReason] = useState<StockReason | ''>(direction === 'IN' ? 'RECEIPT' : '')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [editingLocation, setEditingLocation] = useState(false)
  const [locationDraft, setLocationDraft] = useState('')
  const [savingLocation, setSavingLocation] = useState(false)
  const reference = useRef(newClientReference())
  const canEditLocation = hasPermission('inventory:scan') || hasPermission('inventory:write')

  const canReadPackaging = hasPermission('packaging:read')
  useEffect(() => {
    apiFetch<FlatPage<StockLevel>>(`/api/v1/stock-levels?productId=${product.id}&size=50`).then((page) => setLevels(page.content)).catch(() => setLevels([]))
    if (canReadPackaging) {
      apiFetch<{ content: Packaging[] }>(`/api/v1/product-packaging?productId=${product.id}&size=20`)
        .then((page) => setPackaging(page.content.filter((item) => item.active && Number(item.unitsPerPackage) > 1))).catch(() => setPackaging([]))
    }
  }, [product.id, canReadPackaging])

  const here = levels?.find((level) => level.warehouseId === warehouse?.id)
  const stock = Number(here?.quantity ?? 0)
  const unit = here?.unitOfMeasure ?? product.unitOfMeasure
  const pack = packaging.find((item) => item.id === packageId)
  const entered = Number(count.replace(',', '.'))
  const quantity = Number.isFinite(entered) && entered > 0 ? Number((entered * (pack ? Number(pack.unitsPerPackage) : 1)).toFixed(6)) : 0
  const tooMany = direction === 'OUT' && quantity > stock
  const step = (delta: number) => setCount((value) => String(Math.max(0, Number((Number(value.replace(',', '.')) || 0) + delta))).replace('.', ','))

  // La ubicación se guarda aparte del movimiento: es dónde está el artículo, no cuánto hay.
  const saveLocation = async () => {
    if (!warehouse) return
    setSavingLocation(true); setError('')
    try {
      const saved = await saveStockLocation({ warehouseId: warehouse.id, productId: product.id, productCode: product.code, productName: product.name, unitOfMeasure: unit }, locationDraft)
      setLevels((current) => [...(current ?? []).filter((level) => level.warehouseId !== saved.warehouseId), saved])
      setEditingLocation(false)
      scanFeedback(true)
    } catch (cause) { setError(errorMessage(cause)) } finally { setSavingLocation(false) }
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (direction === 'LOOKUP') return
    if (!quantity) { setError(c('Indica cuántas unidades.', 'Enter how many units.')); return }
    if (!reason) { setError(c('Elige el motivo.', 'Choose the reason.')); return }
    setBusy(true); setError('')
    try {
      const movement = await apiFetch<StockMovement>('/api/v1/stock-movements/scan', { method: 'POST', body: JSON.stringify({
        warehouseId: warehouse?.id ?? null, productId: product.id, productCode: product.code, productName: product.name,
        unitOfMeasure: unit, direction, quantity, reason, note: [pack ? c(`${entered} × ${pack.code}`, `${entered} × ${pack.code}`) : '', note.trim()].filter(Boolean).join(' · ') || null,
        clientReference: reference.current,
      }) })
      scanFeedback(true)
      onDone(movement, direction)
    } catch (cause) { scanFeedback(false); setError(errorMessage(cause)) } finally { setBusy(false) }
  }

  return <form className="wh-product" onSubmit={submit}>
    <div className="wh-product-card">
      <span className="wh-product-icon"><Boxes size={26} /></span>
      <div><h1>{product.name}</h1><p>{product.code}{product.barcode ? ` · ${product.barcode}` : ''}</p></div>
    </div>
    <div className="wh-stock">
      <div><small>{c('En', 'In')} {warehouse?.name ?? '—'}</small><strong>{levels === null ? '…' : formatNumber(stock, locale, 3)}</strong><span>{unitLabel(unit)}</span></div>
      {levels && levels.filter((level) => level.warehouseId !== warehouse?.id && Number(level.quantity) > 0).map((level) => <div key={level.id} className="other"><small>{warehouses.find((item) => item.id === level.warehouseId)?.name ?? '—'}</small><strong>{formatNumber(level.quantity, locale, 3)}</strong>{level.location && <span><MapPin size={11} aria-hidden="true" /> {level.location}</span>}</div>)}
    </div>
    <div className="wh-location">
      <MapPin size={22} aria-hidden="true" />
      <div><small>{c('Ubicación en', 'Location in')} {warehouse?.name ?? '—'}</small><strong className={here?.location ? '' : 'empty'}>{levels === null ? '…' : here?.location || c('Sin indicar', 'Not set')}</strong></div>
      {canEditLocation && !editingLocation && <button type="button" className="button button-secondary button-small" onClick={() => { setLocationDraft(here?.location ?? ''); setEditingLocation(true) }}>{here?.location ? c('Cambiar', 'Change') : c('Indicar', 'Set')}</button>}
    </div>
    {editingLocation && <div className="wh-location-edit">
      <input autoFocus value={locationDraft} maxLength={120} onChange={(event) => setLocationDraft(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void saveLocation() } }} placeholder={LOCATION_EXAMPLES[1]} aria-label={c('Ubicación', 'Location')} />
      <div><button type="button" className="button button-ghost" onClick={() => setEditingLocation(false)}>{c('Cancelar', 'Cancel')}</button><button type="button" className="button button-primary" disabled={savingLocation} onClick={() => void saveLocation()}>{savingLocation ? c('Guardando…', 'Saving…') : c('Guardar ubicación', 'Save location')}</button></div>
    </div>}
    {direction === 'LOOKUP' ? <div className="wh-actions compact">
      <button type="button" className="wh-action in" onClick={() => onChoose('IN')} disabled={!hasPermission('inventory:scan') && !hasPermission('inventory:write')}><PackagePlus size={30} /><strong>{c('Añadir', 'Add')}</strong></button>
      <button type="button" className="wh-action out" onClick={() => onChoose('OUT')} disabled={!hasPermission('inventory:scan') && !hasPermission('inventory:write')}><PackageMinus size={30} /><strong>{c('Retirar', 'Remove')}</strong></button>
    </div> : <>
      <div className={`wh-mode ${direction === 'IN' ? 'in' : 'out'}`}>{direction === 'IN' ? c('¿Cuántas entran?', 'How many come in?') : c('¿Cuántas retiras?', 'How many are removed?')}</div>
      {packaging.length > 0 && <div className="wh-pack" role="radiogroup" aria-label={c('Contar por', 'Count by')}>
        <button type="button" role="radio" aria-checked={!packageId} className={!packageId ? 'active' : ''} onClick={() => setPackageId('')}>{c('Unidades', 'Units')}</button>
        {packaging.map((item) => <button key={item.id} type="button" role="radio" aria-checked={packageId === item.id} className={packageId === item.id ? 'active' : ''} onClick={() => setPackageId(item.id)}>{item.code} · {formatNumber(item.unitsPerPackage, locale, 3)} {c('uds.', 'units')}</button>)}
      </div>}
      <div className="wh-quantity">
        <button type="button" onClick={() => step(-1)} aria-label={c('Una menos', 'One less')}><Minus size={26} /></button>
        <input value={count} onChange={(event) => setCount(event.target.value)} inputMode="decimal" aria-label={c('Cantidad', 'Quantity')} onFocus={(event) => event.target.select()} />
        <button type="button" onClick={() => step(1)} aria-label={c('Una más', 'One more')}><Plus size={26} /></button>
      </div>
      <div className="wh-quick">{[5, 10, 12, 24, 50, 100].map((value) => <button key={value} type="button" onClick={() => setCount(String(value))}>{value}</button>)}</div>
      {pack && <p className="wh-total">= <strong>{formatNumber(quantity, locale, 3)}</strong> {unitLabel(unit)}</p>}
      {tooMany && <p className="wh-warning" role="alert">{c(`Solo constan ${formatNumber(stock, locale, 3)} en este almacén. Revisa el recuento antes de retirar más.`, `Only ${formatNumber(stock, locale, 3)} recorded in this warehouse. Check the count before removing more.`)}</p>}
      <fieldset className="wh-reasons"><legend>{c('Motivo', 'Reason')}</legend>
        {REASONS[direction].map((item) => <button key={item} type="button" className={reason === item ? 'active' : ''} aria-pressed={reason === item} onClick={() => setReason(item)}>{reasonLabels[item][language === 'es' ? 0 : 1]}</button>)}
      </fieldset>
      <label className="wh-note"><span>{c('Nota (opcional)', 'Note (optional)')}</span><input value={note} maxLength={300} onChange={(event) => setNote(event.target.value)} placeholder={direction === 'IN' ? c('Ej.: albarán del proveedor 2381', 'E.g. supplier note 2381') : c('Ej.: se cayó del palé', 'E.g. fell off the pallet')} /></label>
      {error && <div className="inline-error" role="alert">{error}</div>}
      <button type="submit" className={`wh-confirm ${direction === 'IN' ? 'in' : 'out'}`} disabled={busy || !quantity || !reason || tooMany}>{busy ? c('Guardando…', 'Saving…') : direction === 'IN' ? c(`Añadir ${formatNumber(quantity, locale, 3)}`, `Add ${formatNumber(quantity, locale, 3)}`) : c(`Retirar ${formatNumber(quantity, locale, 3)}`, `Remove ${formatNumber(quantity, locale, 3)}`)}</button>
    </>}
  </form>
}
