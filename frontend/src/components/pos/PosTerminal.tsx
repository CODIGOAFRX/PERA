import { ArrowLeftRight, Banknote, Camera, ChevronLeft, CircleUserRound, Clock3, LogOut, Minus, Pause, Percent, Play, Plus, Receipt, RotateCcw, ScanBarcode, Search, ShoppingBasket, Tag, Trash2, Wallet, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { useAuth } from '../../auth/AuthContext'
import type { CompanyProfile } from '../../hooks/useCompanyProfile'
import { apiFetch, ApiError, errorMessage } from '../../lib/api'
import { formatCurrency, formatDateTime, formatNumber } from '../../lib/format'
import { cartTotals, linePrice, money, newClientReference, retailPrice, type CartLine, type PosRegister, type PosSessionSummary, type PosTicket } from '../../lib/pos'
import { scanFeedback, useHardwareScanner } from '../../lib/scanner'
import type { PageResponse, Product } from '../../types/api'
import { CameraScanner } from '../CameraScanner'
import { useConfirm } from '../ConfirmDialog'
import { PearBrandMark } from '../PearBrandMark'
import { useToast } from '../Toast'
import { CashMovementDialog, CloseSessionDialog, NumberDialog, PaymentDialog, ReceiptDialog, RefundDialog, SessionReportDialog, TicketsDialog, type PaymentResult } from './PosDialogs'

type Lang = 'es' | 'en'
type PosProduct = Product & { barcode?: string | null }
interface ProductGroup { id: string; name: string; active: boolean }
interface ParkedTicket { id: string; parkedAt: string; lines: CartLine[]; discountPercent: number }
type LineDialog = { line: CartLine; field: 'quantity' | 'price' | 'discount' } | null

const AUTO_PRINT_KEY = 'pera.pos.autoPrint'
const parkedKey = (registerId: string) => `pera.pos.parked.${registerId}`

function readParked(registerId: string): ParkedTicket[] {
  try { return JSON.parse(localStorage.getItem(parkedKey(registerId)) ?? '[]') as ParkedTicket[] } catch { return [] }
}
function writeParked(registerId: string, parked: ParkedTicket[]) {
  try { localStorage.setItem(parkedKey(registerId), JSON.stringify(parked)) } catch { /* sin almacenamiento local */ }
}

/** Pantalla de venta de una caja abierta. */
export function PosTerminal({ register, initialSummary, company, language, locale, onExit, onClosed }: {
  register: PosRegister
  initialSummary: PosSessionSummary
  company: CompanyProfile
  language: Lang
  locale: string
  onExit: () => void
  onClosed: () => void
}) {
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { identity, logout } = useAuth()
  const { notify } = useToast()
  const confirm = useConfirm()
  const currency = company.baseCurrency
  const fmt = (value: number) => formatCurrency(value, currency, locale)
  const [summary, setSummary] = useState(initialSummary)
  const [products, setProducts] = useState<PosProduct[]>([])
  const [groups, setGroups] = useState<ProductGroup[]>([])
  const [groupId, setGroupId] = useState('')
  const [query, setQuery] = useState('')
  const [remote, setRemote] = useState<PosProduct[] | null>(null)
  const [loadingProducts, setLoadingProducts] = useState(true)
  const [lines, setLines] = useState<CartLine[]>([])
  const [selectedKey, setSelectedKey] = useState<string | null>(null)
  const [ticketDiscount, setTicketDiscount] = useState(0)
  const [parked, setParked] = useState<ParkedTicket[]>(() => readParked(register.id))
  const [mobileView, setMobileView] = useState<'products' | 'ticket'>('products')
  const [now, setNow] = useState(() => new Date())
  const [paying, setPaying] = useState(false)
  const [payBusy, setPayBusy] = useState(false)
  const [payError, setPayError] = useState('')
  const [lastTicket, setLastTicket] = useState<PosTicket | null>(null)
  const [lineDialog, setLineDialog] = useState<LineDialog>(null)
  const [discountDialog, setDiscountDialog] = useState(false)
  const [dialog, setDialog] = useState<'tickets' | 'refund' | 'movements' | 'close' | 'parked' | null>(null)
  const [refundTicket, setRefundTicket] = useState<PosTicket | null>(null)
  const [closedSummary, setClosedSummary] = useState<PosSessionSummary | null>(null)
  const [camera, setCamera] = useState(false)
  const [autoPrint, setAutoPrint] = useState(() => { try { return localStorage.getItem(AUTO_PRINT_KEY) === '1' } catch { return false } })
  const clientReference = useRef(newClientReference())
  const searchRef = useRef<HTMLInputElement>(null)
  const sessionId = summary.sessionId

  const refreshSummary = useCallback(async () => {
    try { setSummary(await apiFetch<PosSessionSummary>(`/api/v1/pos/sessions/${sessionId}/summary`)) } catch { /* se reintenta en el siguiente refresco */ }
  }, [sessionId])

  useEffect(() => {
    let active = true
    setLoadingProducts(true)
    Promise.all([
      apiFetch<PageResponse<PosProduct>>('/api/v1/products?size=1000&sort=name,asc'),
      apiFetch<PageResponse<ProductGroup>>('/api/v1/product-groups?active=true&size=200&sort=name,asc').catch(() => ({ content: [] as ProductGroup[] })),
    ]).then(([productPage, groupPage]) => {
      if (!active) return
      setProducts(productPage.content.filter((product) => product.active))
      setGroups(groupPage.content.filter((group) => group.active))
    }).catch((cause) => { if (active) notify(errorMessage(cause), 'error') })
      .finally(() => { if (active) setLoadingProducts(false) })
    const timer = window.setInterval(() => setNow(new Date()), 30_000)
    const summaryTimer = window.setInterval(() => void refreshSummary(), 60_000)
    return () => { active = false; window.clearInterval(timer); window.clearInterval(summaryTimer) }
  }, [notify, refreshSummary])

  useEffect(() => writeParked(register.id, parked), [register.id, parked])

  const usedGroups = useMemo(() => groups.filter((group) => products.some((product) => product.productGroupId === group.id)), [groups, products])
  const normalizedQuery = query.trim().toLowerCase()
  const visibleProducts = useMemo(() => {
    const source = remote ?? products
    return source.filter((product) => (!groupId || product.productGroupId === groupId) && (!normalizedQuery
      || product.name.toLowerCase().includes(normalizedQuery) || product.code.toLowerCase().includes(normalizedQuery)
      || (product.barcode ?? '').toLowerCase().includes(normalizedQuery)))
  }, [products, remote, groupId, normalizedQuery])

  // Con un catálogo grande la búsqueda también pregunta al servidor.
  useEffect(() => {
    if (!normalizedQuery || normalizedQuery.length < 3 || products.length < 1000) { setRemote(null); return }
    const timer = window.setTimeout(() => {
      apiFetch<PageResponse<PosProduct>>(`/api/v1/products?size=60&query=${encodeURIComponent(query.trim())}`)
        .then((page) => setRemote(page.content.filter((product) => product.active))).catch(() => setRemote(null))
    }, 250)
    return () => window.clearTimeout(timer)
  }, [normalizedQuery, products.length, query])

  const totals = useMemo(() => cartTotals(lines, ticketDiscount), [lines, ticketDiscount])
  const selected = lines.find((line) => line.key === selectedKey) ?? null

  // Las líneas se leen de una referencia para calcular la siguiente fuera del actualizador de estado.
  const linesRef = useRef(lines)
  linesRef.current = lines
  const addProduct = useCallback((product: PosProduct, quantity = 1): CartLine => {
    const current = linesRef.current
    const existing = current.find((line) => line.product.id === product.id && line.unitPrice === null && line.discountPercent === 0)
    if (existing) {
      const next = current.map((line) => line === existing ? { ...line, quantity: Number((line.quantity + quantity).toFixed(3)) } : line)
      linesRef.current = next
      setLines(next)
      setSelectedKey(existing.key)
      return next.find((line) => line.key === existing.key)!
    }
    const line: CartLine = { key: newClientReference(), product, quantity, unitPrice: null, discountPercent: 0 }
    linesRef.current = [...current, line]
    setLines(linesRef.current)
    setSelectedKey(line.key)
    return line
  }, [])
  /** Lo que se vende al peso pide el peso al añadirlo (si no vino ya con «1,25*código»). */
  const addAndWeigh = useCallback((product: PosProduct, quantity?: number) => {
    const line = addProduct(product, quantity ?? 1)
    if (quantity === undefined && product.unitOfMeasure === 'KILOGRAM') setLineDialog({ line, field: 'quantity' })
  }, [addProduct])

  /** Código leído por la pistola, la cámara o tecleado. Admite «3*código» para varias unidades. */
  const handleCode = useCallback(async (raw: string) => {
    const text = raw.trim()
    if (!text) return
    const multiplier = /^(\d{1,4}(?:[.,]\d{1,3})?)\s*[*xX]\s*(.+)$/.exec(text)
    const quantity = multiplier ? Number(multiplier[1].replace(',', '.')) : 1
    if (!(quantity > 0)) { setQuery(''); return }
    const code = (multiplier ? multiplier[2] : text).trim()
    const lower = code.toLowerCase()
    let product = products.find((item) => (item.barcode ?? '').toLowerCase() === lower) ?? products.find((item) => item.code.toLowerCase() === lower)
    if (!product) {
      try { product = await apiFetch<PosProduct>(`/api/v1/products/lookup?code=${encodeURIComponent(code)}`) } catch (cause) {
        if (!(cause instanceof ApiError && cause.status === 404)) { notify(errorMessage(cause), 'error'); return }
      }
    }
    if (product && !product.active) { scanFeedback(false); notify(c(`${product.name} está dado de baja.`, `${product.name} is deactivated.`), 'error'); return }
    if (!product) {
      // Si no es un código, se deja como búsqueda por nombre.
      const matches = products.filter((item) => item.name.toLowerCase().includes(lower) || item.code.toLowerCase().includes(lower))
      if (matches.length === 1) { addAndWeigh(matches[0], multiplier ? quantity : undefined); setQuery(''); scanFeedback(true); return }
      if (matches.length > 1) { setQuery(code); return }
      // El campo se vacía: la siguiente lectura del lector no debe pegarse a un código que no existe.
      setQuery('')
      scanFeedback(false)
      notify(c(`Ningún artículo tiene el código ${code}.`, `No product has code ${code}.`), 'error')
      return
    }
    addAndWeigh(product, multiplier ? quantity : undefined)
    setQuery('')
    scanFeedback(true)
  }, [products, addAndWeigh, notify, language])

  useHardwareScanner((code) => void handleCode(code), { enabled: !paying && dialog === null && !camera })

  const changeLine = (key: string, change: Partial<CartLine>) => setLines((current) => current.map((line) => line.key === key ? { ...line, ...change } : line))
  const removeLine = (key: string) => {
    const current = linesRef.current
    const index = current.findIndex((line) => line.key === key)
    const next = current.filter((line) => line.key !== key)
    linesRef.current = next
    setLines(next)
    setSelectedKey(next[Math.min(index, next.length - 1)]?.key ?? null)
  }
  const clearTicket = () => { setLines([]); setSelectedKey(null); setTicketDiscount(0); clientReference.current = newClientReference() }

  const park = () => {
    if (lines.length === 0) return
    setParked((current) => [...current, { id: newClientReference(), parkedAt: new Date().toISOString(), lines, discountPercent: ticketDiscount }])
    clearTicket()
    notify(c('Ticket aparcado. Recupéralo cuando el cliente vuelva.', 'Ticket parked. Resume it when the customer is back.'))
  }
  const resume = (ticket: ParkedTicket) => {
    if (lines.length > 0) setParked((current) => [...current.filter((item) => item.id !== ticket.id), { id: newClientReference(), parkedAt: new Date().toISOString(), lines, discountPercent: ticketDiscount }])
    else setParked((current) => current.filter((item) => item.id !== ticket.id))
    setLines(ticket.lines); setTicketDiscount(ticket.discountPercent); setSelectedKey(ticket.lines[0]?.key ?? null); setDialog(null)
    clientReference.current = newClientReference()
  }

  const startPayment = () => {
    if (lines.length === 0) { notify(c('El ticket está vacío.', 'The ticket is empty.'), 'error'); return }
    setPayError(''); setPaying(true)
  }
  const pay = async (payment: PaymentResult) => {
    setPayBusy(true); setPayError('')
    try {
      const ticket = await apiFetch<PosTicket>('/api/v1/pos/tickets', {
        method: 'POST', body: JSON.stringify({
          sessionId, discountPercent: ticketDiscount || null, paymentMethod: payment.method,
          cashTendered: payment.cashTendered, cardAmount: payment.cardAmount,
          customerName: payment.customerName.trim() || null, customerTaxId: payment.customerTaxId.trim() || null,
          clientReference: clientReference.current,
          lines: lines.map((line) => ({ productId: line.product.id, quantity: line.quantity, unitPrice: line.unitPrice, discountPercent: line.discountPercent || null })),
        }),
      })
      setPaying(false); clearTicket(); setLastTicket(ticket); setMobileView('products')
      void refreshSummary()
    } catch (cause) { setPayError(errorMessage(cause)) } finally { setPayBusy(false) }
  }

  const finishedTicket = () => { setLastTicket(null); window.setTimeout(() => searchRef.current?.focus(), 50) }
  const toggleAutoPrint = (value: boolean) => { setAutoPrint(value); try { localStorage.setItem(AUTO_PRINT_KEY, value ? '1' : '0') } catch { /* preferencia solo en memoria */ } }

  const leave = async () => {
    if (lines.length > 0 && !(await confirm({ message: c('Hay un ticket a medias. Se aparcará para recuperarlo después.', 'There is an unfinished ticket. It will be parked so you can resume it later.') }))) return
    if (lines.length > 0) park()
    onExit()
  }

  // Atajos de teclado de un TPV de escritorio.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (paying || dialog !== null || lineDialog !== null || discountDialog || lastTicket || closedSummary || camera) return
      if (event.key === 'F2') { event.preventDefault(); searchRef.current?.focus() }
      else if (event.key === 'F4') { event.preventDefault(); startPayment() }
      else if (event.key === 'F6') { event.preventDefault(); setDialog('tickets') }
      else if (event.key === 'F7') { event.preventDefault(); setRefundTicket(null); setDialog('refund') }
      else if (event.key === 'F8') { event.preventDefault(); setDialog('movements') }
      else if (event.key === 'F9') { event.preventDefault(); park() }
      else if (selected && document.activeElement === document.body) {
        if (event.key === '+') { event.preventDefault(); changeLine(selected.key, { quantity: selected.quantity + 1 }) }
        else if (event.key === '-' && selected.quantity > 1) { event.preventDefault(); changeLine(selected.key, { quantity: selected.quantity - 1 }) }
        else if (event.key === 'Delete') { event.preventDefault(); removeLine(selected.key) }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const onSearchKey = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') { event.preventDefault(); void handleCode(query) }
    if (event.key === 'Escape') setQuery('')
  }

  const cashInDrawer = summary.expectedCash
  const greeting = identity?.displayName ?? ''

  return <div className="pos-app">
    <header className="pos-topbar">
      <div className="pos-brand"><PearBrandMark /><span><strong>PERA</strong><small>TPV</small></span></div>
      <button type="button" className="pos-register-chip" onClick={() => void leave()} title={c('Cambiar de caja', 'Switch till')}><ChevronLeft size={16} /><span><strong>{register.name}</strong><small>{c('Abierta', 'Open')} {formatDateTime(summary.openedAt, locale)}</small></span></button>
      <dl className="pos-kpis">
        <div><dt>{c('Ventas del turno', 'Shift sales')}</dt><dd>{fmt(summary.figures.netTotal)}</dd></div>
        <div><dt>{c('Tickets', 'Tickets')}</dt><dd>{summary.figures.ticketCount}</dd></div>
        <div><dt>{c('Ticket medio', 'Average')}</dt><dd>{fmt(summary.figures.averageTicket)}</dd></div>
        <div><dt>{c('Efectivo en caja', 'Cash in drawer')}</dt><dd>{fmt(cashInDrawer)}</dd></div>
      </dl>
      <div className="pos-topbar-actions">
        <span className="pos-clock"><Clock3 size={15} />{now.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })}</span>
        <button type="button" onClick={() => setDialog('tickets')} title="F6"><Receipt size={18} /><span>{c('Tickets', 'Tickets')}</span></button>
        <button type="button" onClick={() => { setRefundTicket(null); setDialog('refund') }} title="F7"><RotateCcw size={18} /><span>{c('Devolución', 'Refund')}</span></button>
        <button type="button" onClick={() => setDialog('movements')} title="F8"><ArrowLeftRight size={18} /><span>{c('Caja', 'Cash')}</span></button>
        <button type="button" onClick={() => setDialog('close')}><Wallet size={18} /><span>{c('Cerrar caja', 'Close till')}</span></button>
        <span className="pos-user" title={greeting}><CircleUserRound size={18} /><span>{greeting}</span></span>
        <button type="button" onClick={logout} aria-label={c('Cerrar sesión', 'Sign out')} title={c('Cerrar sesión', 'Sign out')}><LogOut size={18} /></button>
      </div>
    </header>

    <div className={`pos-main view-${mobileView}`}>
      <section className="pos-catalog" aria-label={c('Artículos', 'Products')}>
        <div className="pos-search">
          <ScanBarcode size={22} aria-hidden="true" />
          <input ref={searchRef} autoFocus value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={onSearchKey} aria-label={c('Escanear o buscar artículo', 'Scan or search product')} placeholder={c('Escanea el código o busca por nombre (F2) · 3*código para varias unidades', 'Scan the code or search by name (F2) · 3*code for several units')} autoComplete="off" />
          {query && <button type="button" className="pos-search-clear" onClick={() => setQuery('')} aria-label={c('Limpiar búsqueda', 'Clear search')}><X size={16} /></button>}
          <button type="button" className="pos-camera-button" onClick={() => setCamera(true)} aria-label={c('Escanear con la cámara', 'Scan with camera')}><Camera size={20} /></button>
        </div>
        {usedGroups.length > 0 && <div className="pos-groups" role="tablist" aria-label={c('Familias', 'Families')}>
          <button type="button" role="tab" aria-selected={!groupId} className={!groupId ? 'active' : ''} onClick={() => setGroupId('')}>{c('Todos', 'All')}</button>
          {usedGroups.map((group) => <button key={group.id} type="button" role="tab" aria-selected={groupId === group.id} className={groupId === group.id ? 'active' : ''} onClick={() => setGroupId(group.id)}>{group.name}</button>)}
        </div>}
        <div className="pos-products">
          {loadingProducts ? <p className="pos-muted">{c('Cargando artículos…', 'Loading products…')}</p> : visibleProducts.length === 0 ? <p className="pos-muted">{query ? c('No hay artículos que coincidan. Pulsa Intro para buscar el código exacto.', 'No matching products. Press Enter to look up the exact code.') : c('No hay artículos en esta familia.', 'No products in this family.')}</p> :
            visibleProducts.slice(0, 240).map((product) => <button key={product.id} type="button" className="pos-product" onClick={() => { addAndWeigh(product); scanFeedback(true) }}>
              <span className="pos-product-name">{product.name}</span>
              <span className="pos-product-code">{product.code}</span>
              <strong>{fmt(retailPrice(product.basePrice, product.taxRate))}</strong>
            </button>)}
        </div>
      </section>

      <section className="pos-ticket" aria-label={c('Ticket actual', 'Current ticket')}>
        <div className="pos-ticket-head">
          <div><ShoppingBasket size={18} /><strong>{c('Ticket actual', 'Current ticket')}</strong><span>{formatNumber(totals.items, locale, 3)} {c('uds.', 'units')}</span></div>
          <div>
            <button type="button" className="icon-button" onClick={park} disabled={lines.length === 0} title="F9" aria-label={c('Aparcar ticket', 'Park ticket')}><Pause size={16} /></button>
            <button type="button" className="icon-button pos-parked-button" onClick={() => setDialog('parked')} disabled={parked.length === 0} aria-label={c('Tickets aparcados', 'Parked tickets')}><Play size={16} />{parked.length > 0 && <b>{parked.length}</b>}</button>
            <button type="button" className="icon-button" onClick={async () => { if (lines.length && await confirm({ message: c('¿Vaciar el ticket?', 'Clear the ticket?'), danger: true })) clearTicket() }} disabled={lines.length === 0} aria-label={c('Vaciar ticket', 'Clear ticket')}><Trash2 size={16} /></button>
          </div>
        </div>
        <ol className="pos-lines">
          {lines.length === 0 ? <li className="pos-empty"><ScanBarcode size={30} /><span>{c('Escanea un artículo o tócalo en la lista para empezar.', 'Scan a product or tap it in the list to start.')}</span></li> :
            lines.map((line, index) => <li key={line.key} className={line.key === selectedKey ? 'selected' : ''} onClick={() => setSelectedKey(line.key)}>
              <div className="pos-line-main">
                <strong>{line.product.name}</strong>
                <small>{fmt(linePrice(line))}{line.unitPrice !== null ? ` · ${c('precio manual', 'manual price')}` : ''}{line.discountPercent > 0 ? ` · -${formatNumber(line.discountPercent, locale)} %` : ''}</small>
              </div>
              <div className="pos-stepper" onClick={(event) => event.stopPropagation()}>
                <button type="button" aria-label={c('Una menos', 'One less')} onClick={() => line.quantity > 1 ? changeLine(line.key, { quantity: Number((line.quantity - 1).toFixed(3)) }) : removeLine(line.key)}>{line.quantity > 1 ? <Minus size={14} /> : <Trash2 size={14} />}</button>
                <button type="button" className="pos-qty" onClick={() => setLineDialog({ line, field: 'quantity' })} aria-label={c('Cambiar cantidad', 'Change quantity')}>{formatNumber(line.quantity, locale, 3)}</button>
                <button type="button" aria-label={c('Una más', 'One more')} onClick={() => changeLine(line.key, { quantity: Number((line.quantity + 1).toFixed(3)) })}><Plus size={14} /></button>
              </div>
              <b className="pos-line-total">{fmt(totals.lineTotals[index] ?? 0)}</b>
            </li>)}
        </ol>
        {selected && <div className="pos-line-tools" aria-label={c('Línea seleccionada', 'Selected line')}>
          <button type="button" onClick={() => setLineDialog({ line: selected, field: 'quantity' })}>{c('Cantidad', 'Qty')}</button>
          <button type="button" onClick={() => setLineDialog({ line: selected, field: 'price' })}><Tag size={14} />{c('Precio', 'Price')}</button>
          <button type="button" onClick={() => setLineDialog({ line: selected, field: 'discount' })}><Percent size={14} />{c('Dto. línea', 'Line disc.')}</button>
          <button type="button" className="danger" onClick={() => removeLine(selected.key)}><Trash2 size={14} />{c('Quitar', 'Remove')}</button>
        </div>}
        <div className="pos-totals">
          {totals.discountAmount > 0 && <div><span>{c('Suma', 'Subtotal')}</span><span>{fmt(totals.grossAmount)}</span></div>}
          {totals.discountAmount > 0 && <div className="discount"><span>{c('Descuentos', 'Discounts')}{ticketDiscount > 0 ? ` (${c('ticket', 'ticket')} ${formatNumber(ticketDiscount, locale)} %)` : ''}</span><span>-{fmt(totals.discountAmount)}</span></div>}
          {totals.taxes.map((tax) => <div key={tax.rate} className="tax"><span>{c('IVA', 'VAT')} {formatNumber(tax.rate, locale)} % · {c('base', 'base')} {fmt(tax.base)}</span><span>{fmt(tax.tax)}</span></div>)}
          <button type="button" className="text-link" onClick={() => setDiscountDialog(true)}><Percent size={13} />{ticketDiscount > 0 ? c('Cambiar descuento del ticket', 'Change ticket discount') : c('Descuento al ticket', 'Ticket discount')}</button>
        </div>
        <div className="pos-total-display" aria-live="polite"><span>{c('TOTAL', 'TOTAL')}</span><strong>{fmt(totals.totalAmount)}</strong></div>
        <button type="button" className="pos-pay-button" onClick={startPayment} disabled={lines.length === 0}><Banknote size={24} />{c('Cobrar', 'Charge')}<kbd>F4</kbd></button>
      </section>
    </div>

    <nav className="pos-mobile-tabs" aria-label={c('Vista', 'View')}>
      <button type="button" className={mobileView === 'products' ? 'active' : ''} onClick={() => setMobileView('products')}><Search size={18} />{c('Artículos', 'Products')}</button>
      <button type="button" className={mobileView === 'ticket' ? 'active' : ''} onClick={() => setMobileView('ticket')}><ShoppingBasket size={18} />{c('Ticket', 'Ticket')} ({lines.length}) · <strong>{fmt(totals.totalAmount)}</strong></button>
    </nav>

    <PaymentDialog open={paying} total={totals.totalAmount} currency={currency} locale={locale} language={language} busy={payBusy} error={payError} onClose={() => setPaying(false)} onConfirm={(payment) => void pay(payment)} />
    <ReceiptDialog ticket={lastTicket} company={company} locale={locale} language={language} onClose={finishedTicket} autoPrint={autoPrint} onToggleAutoPrint={toggleAutoPrint} />
    <NumberDialog open={lineDialog !== null} language={language}
      title={lineDialog?.field === 'quantity' ? lineDialog.line.product.unitOfMeasure === 'KILOGRAM' ? c('Peso en kg', 'Weight in kg') : c('Cantidad', 'Quantity') : lineDialog?.field === 'price' ? c('Precio unitario con IVA', 'Unit price incl. VAT') : c('Descuento de la línea', 'Line discount')}
      description={lineDialog?.line.product.name} suffix={lineDialog?.field === 'discount' ? '%' : lineDialog?.field === 'price' ? '€' : undefined}
      decimals={lineDialog?.field === 'quantity' ? 3 : 2} max={lineDialog?.field === 'discount' ? 100 : undefined}
      initial={lineDialog ? lineDialog.field === 'quantity' ? lineDialog.line.quantity : lineDialog.field === 'price' ? linePrice(lineDialog.line) : lineDialog.line.discountPercent : null}
      onClose={() => setLineDialog(null)}
      onConfirm={(value) => {
        if (!lineDialog) return
        const { line, field } = lineDialog
        if (field === 'quantity') { if (value && value > 0) changeLine(line.key, { quantity: value }); else removeLine(line.key) }
        else if (field === 'price') changeLine(line.key, { unitPrice: value == null || money(value) === retailPrice(line.product.basePrice, line.product.taxRate) ? null : money(value) })
        else changeLine(line.key, { discountPercent: Math.min(100, Math.max(0, value ?? 0)) })
        setLineDialog(null)
      }} />
    <NumberDialog open={discountDialog} language={language} title={c('Descuento a todo el ticket', 'Whole-ticket discount')} suffix="%" decimals={2} max={100} initial={ticketDiscount} onClose={() => setDiscountDialog(false)} onConfirm={(value) => { setTicketDiscount(Math.min(100, Math.max(0, value ?? 0))); setDiscountDialog(false) }} />
    <TicketsDialog open={dialog === 'tickets'} sessionId={sessionId} company={company} locale={locale} language={language} onClose={() => setDialog(null)} onChanged={(message) => { notify(message); void refreshSummary() }} onRefund={(ticket) => { setRefundTicket(ticket); setDialog('refund') }} />
    <RefundDialog open={dialog === 'refund'} sessionId={sessionId} initialTicket={refundTicket} company={company} locale={locale} language={language} onClose={() => setDialog(null)} onRefunded={(ticket) => { setDialog(null); setLastTicket(ticket); void refreshSummary() }} />
    <CashMovementDialog open={dialog === 'movements'} summary={summary} currency={currency} locale={locale} language={language} onClose={() => setDialog(null)} onSaved={(updated, message) => { setSummary(updated); notify(message); setDialog(null) }} />
    <CloseSessionDialog open={dialog === 'close'} summary={summary} company={company} cashierName={greeting} locale={locale} language={language} onClose={() => setDialog(null)} onClosed={(closed) => { setDialog(null); setClosedSummary(closed); setParked([]) }} />
    <SessionReportDialog summary={closedSummary} company={company} cashierName={greeting} locale={locale} language={language} onClose={() => { setClosedSummary(null); onClosed() }} />
    {dialog === 'parked' && <ParkedDialog parked={parked} fmt={fmt} locale={locale} language={language} onClose={() => setDialog(null)} onResume={resume} onDiscard={(id) => setParked((current) => current.filter((item) => item.id !== id))} />}
    <CameraScanner open={camera} onClose={() => setCamera(false)} onDetected={(code) => { setCamera(false); void handleCode(code) }} title={c('Escanear artículo', 'Scan product')} />
  </div>
}

function ParkedDialog({ parked, fmt, locale, language, onClose, onResume, onDiscard }: {
  parked: ParkedTicket[]
  fmt: (value: number) => string
  locale: string
  language: Lang
  onClose: () => void
  onResume: (ticket: ParkedTicket) => void
  onDiscard: (id: string) => void
}) {
  const c = (es: string, en: string) => language === 'es' ? es : en
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="modal-panel modal-medium" role="dialog" aria-modal="true" aria-label={c('Tickets aparcados', 'Parked tickets')} onKeyDown={(event) => { if (event.key === 'Escape') onClose() }}>
      <header className="modal-header"><div><h2>{c('Tickets aparcados', 'Parked tickets')}</h2><p>{c('Se guardan en este equipo hasta que se cobren o se descarten.', 'They are kept on this device until charged or discarded.')}</p></div><button type="button" className="icon-button" onClick={onClose} aria-label={c('Cerrar', 'Close')}><X size={20} /></button></header>
      <ul className="pos-parked-list">{parked.map((ticket) => {
        const total = cartTotals(ticket.lines, ticket.discountPercent).totalAmount
        return <li key={ticket.id}>
          <span><strong>{fmt(total)}</strong><small>{formatDateTime(ticket.parkedAt, locale)} · {ticket.lines.length} {c('líneas', 'lines')} · {ticket.lines.slice(0, 3).map((line) => line.product.name).join(', ')}</small></span>
          <button type="button" className="button button-primary button-small" data-autofocus onClick={() => onResume(ticket)}><Play size={14} />{c('Recuperar', 'Resume')}</button>
          <button type="button" className="icon-button" onClick={() => onDiscard(ticket.id)} aria-label={c('Descartar', 'Discard')}><Trash2 size={15} /></button>
        </li>
      })}</ul>
    </section>
  </div>
}
