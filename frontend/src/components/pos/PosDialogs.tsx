import { ArrowDownToLine, ArrowUpFromLine, Ban, Banknote, Check, CreditCard, Printer, ReceiptText, RotateCcw, ScanBarcode, Search, Split, Undo2, Wallet } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import type { CompanyProfile } from '../../hooks/useCompanyProfile'
import { apiFetch, errorMessage } from '../../lib/api'
import { formatCurrency, formatDateTime, formatNumber } from '../../lib/format'
import { countDenominations, EURO_DENOMINATIONS, money, newClientReference, paymentLabels, quickCashOptions, type PosPaymentMethod, type PosSessionSummary, type PosTicket } from '../../lib/pos'
import { CameraScanner } from '../CameraScanner'
import { EmptyState, LoadingState } from '../DataState'
import { Field } from '../Form'
import { Modal } from '../Modal'
import { StatusBadge } from '../StatusBadge'
import { PosKeypad, parseKeypad, toKeypad } from './PosKeypad'
import { PosReceipt, PosSessionReport, printReceipt } from './PosReceipt'

type Lang = 'es' | 'en'
const tr = (language: Lang) => (es: string, en: string) => language === 'es' ? es : en

// ------------------------------------------------------------------ cobro

export interface PaymentResult {
  method: PosPaymentMethod
  cashTendered: number | null
  cardAmount: number | null
  customerName: string
  customerTaxId: string
}

export function PaymentDialog({ open, total, currency, locale, language, busy, error, onClose, onConfirm }: {
  open: boolean
  total: number
  currency: string
  locale: string
  language: Lang
  busy: boolean
  error: string
  onClose: () => void
  onConfirm: (payment: PaymentResult) => void
}) {
  const c = tr(language)
  const [method, setMethod] = useState<PosPaymentMethod>('CASH')
  const [tendered, setTendered] = useState('')
  const [card, setCard] = useState('')
  const [customer, setCustomer] = useState(false)
  const [customerName, setCustomerName] = useState('')
  const [customerTaxId, setCustomerTaxId] = useState('')
  useEffect(() => {
    if (!open) return
    setMethod('CASH'); setTendered(''); setCard(''); setCustomer(false); setCustomerName(''); setCustomerTaxId('')
  }, [open])
  const cardAmount = method === 'MIXED' ? parseKeypad(card) ?? 0 : method === 'CARD' ? total : 0
  const cashDue = money(total - cardAmount)
  const given = parseKeypad(tendered)
  const cashGiven = given ?? cashDue
  const change = method === 'CARD' ? 0 : money(cashGiven - cashDue)
  const valid = method === 'CARD' || (cashGiven >= cashDue && (method !== 'MIXED' || (cardAmount > 0 && cardAmount < total)))
  const fmt = (value: number) => formatCurrency(value, currency, locale)
  const confirm = () => {
    if (!valid || busy) return
    onConfirm({ method, cashTendered: method === 'CARD' ? null : cashGiven, cardAmount: method === 'MIXED' ? cardAmount : null, customerName, customerTaxId })
  }
  const submit = (event: FormEvent) => { event.preventDefault(); confirm() }
  const keypadTarget = method === 'MIXED' && !card ? 'card' : 'cash'

  return <Modal open={open} onClose={() => !busy && onClose()} title={c('Cobrar', 'Charge')} size="large">
    <form className="pos-payment" onSubmit={submit}>
      <div className="pos-payment-total"><span>{c('Total a cobrar', 'Amount due')}</span><strong>{fmt(total)}</strong></div>
      <div className="pos-method-tabs" role="radiogroup" aria-label={c('Forma de pago', 'Payment method')}>
        {(['CASH', 'CARD', 'MIXED'] as PosPaymentMethod[]).map((item) => {
          const Icon = item === 'CASH' ? Banknote : item === 'CARD' ? CreditCard : Split
          return <button key={item} type="button" role="radio" aria-checked={method === item} className={method === item ? 'active' : ''} onClick={() => setMethod(item)}><Icon size={20} />{paymentLabels[item][language === 'es' ? 0 : 1]}</button>
        })}
      </div>
      <div className="pos-payment-body">
        <div className="pos-payment-fields">
          {method === 'CARD' && <p className="pos-card-hint"><CreditCard size={18} />{c(`Cobra ${fmt(total)} en el datáfono y confirma cuando esté aprobado.`, `Charge ${fmt(total)} on the card terminal and confirm once approved.`)}</p>}
          {method === 'MIXED' && <Field label={c('Con tarjeta', 'By card')} htmlFor="pos-card-amount" hint={c(`El resto, ${fmt(Math.max(0, cashDue))}, en efectivo.`, `The rest, ${fmt(Math.max(0, cashDue))}, in cash.`)}><input id="pos-card-amount" inputMode="decimal" value={card} onChange={(event) => setCard(event.target.value)} autoComplete="off" /></Field>}
          {method !== 'CARD' && <>
            <Field label={method === 'MIXED' ? c('Efectivo entregado', 'Cash tendered') : c('Entregado por el cliente', 'Tendered by customer')} htmlFor="pos-tendered" hint={c('Vacío = importe exacto.', 'Empty = exact amount.')}><input id="pos-tendered" data-autofocus inputMode="decimal" value={tendered} placeholder={toKeypad(cashDue)} onChange={(event) => setTendered(event.target.value)} autoComplete="off" /></Field>
            <div className="pos-quick-cash" aria-label={c('Importes rápidos', 'Quick amounts')}>
              {quickCashOptions(cashDue).map((value) => <button key={value} type="button" className={given === value ? 'active' : ''} onClick={() => setTendered(toKeypad(value))}>{value === cashDue ? c('Exacto', 'Exact') : fmt(value)}</button>)}
            </div>
          </>}
          <div className={`pos-change ${change < 0 ? 'negative' : ''}`} aria-live="polite">
            <span>{change < 0 ? c('Falta', 'Missing') : c('Cambio', 'Change')}</span>
            <strong>{fmt(Math.abs(change))}</strong>
          </div>
          <button type="button" className="text-link pos-customer-toggle" onClick={() => setCustomer((value) => !value)} aria-expanded={customer}>{customer ? c('Quitar datos del cliente', 'Remove customer details') : c('Factura con datos del cliente (NIF)', 'Invoice with customer details (tax ID)')}</button>
          {customer && <div className="form-grid">
            <Field label={c('Nombre o razón social', 'Name')} htmlFor="pos-customer-name"><input id="pos-customer-name" value={customerName} maxLength={180} onChange={(event) => setCustomerName(event.target.value)} /></Field>
            <Field label="NIF" htmlFor="pos-customer-tax"><input id="pos-customer-tax" value={customerTaxId} maxLength={30} onChange={(event) => setCustomerTaxId(event.target.value.toUpperCase())} /></Field>
          </div>}
        </div>
        {method !== 'CARD' && <PosKeypad language={language} value={keypadTarget === 'card' ? card : tendered} onChange={keypadTarget === 'card' ? setCard : setTendered} />}
      </div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      <div className="pos-payment-actions">
        <button type="button" className="button button-ghost" onClick={onClose} disabled={busy}>{c('Volver al ticket', 'Back to ticket')}</button>
        <button type="submit" className="button button-primary pos-confirm" disabled={!valid || busy} aria-busy={busy || undefined}><Check size={20} />{busy ? c('Cobrando…', 'Charging…') : c(`Confirmar cobro · ${fmt(total)}`, `Confirm · ${fmt(total)}`)}</button>
      </div>
    </form>
  </Modal>
}

// ------------------------------------------------------------------ ticket emitido

export function ReceiptDialog({ ticket, company, locale, language, onClose, autoPrint, onToggleAutoPrint }: {
  ticket: PosTicket | null
  company: CompanyProfile
  locale: string
  language: Lang
  onClose: () => void
  autoPrint?: boolean
  onToggleAutoPrint?: (value: boolean) => void
}) {
  const c = tr(language)
  const receipt = useRef<HTMLDivElement>(null)
  const printed = useRef<string | null>(null)
  useEffect(() => {
    if (ticket && autoPrint && printed.current !== ticket.id) {
      printed.current = ticket.id
      window.setTimeout(() => printReceipt(receipt.current, ticket.number), 150)
    }
  }, [ticket, autoPrint])
  if (!ticket) return null
  const refund = ticket.kind === 'REFUND'
  return <Modal open onClose={onClose} title={refund ? c('Devolución registrada', 'Refund recorded') : c('Ticket cobrado', 'Ticket charged')} description={ticket.number} size="large">
    <div className="pos-receipt-dialog">
      <div className="pos-receipt-summary">
        {!refund && ticket.changeAmount > 0 ? <div className="pos-change big"><span>{c('Cambio a devolver', 'Change due')}</span><strong>{formatCurrency(ticket.changeAmount, ticket.currencyCode, locale)}</strong></div>
          : <div className="pos-change big neutral"><span>{refund ? c('Importe devuelto', 'Amount refunded') : c('Cobrado', 'Charged')}</span><strong>{formatCurrency(ticket.totalAmount, ticket.currencyCode, locale)}</strong></div>}
        <p>{paymentLabels[ticket.paymentMethod][language === 'es' ? 0 : 1]} · {formatDateTime(ticket.issuedAt, locale)}</p>
        <div className="pos-receipt-buttons">
          <button type="button" className="button button-secondary" onClick={() => printReceipt(receipt.current, ticket.number)}><Printer size={18} />{c('Imprimir ticket', 'Print receipt')}</button>
          <button type="button" className="button button-primary" data-autofocus onClick={onClose}><ReceiptText size={18} />{c('Nuevo ticket', 'New ticket')}</button>
        </div>
        {onToggleAutoPrint && <label className="switch-row pos-autoprint"><input type="checkbox" checked={Boolean(autoPrint)} onChange={(event) => onToggleAutoPrint(event.target.checked)} /><span>{c('Imprimir siempre al cobrar', 'Always print after charging')}</span></label>}
      </div>
      <div className="pos-receipt-preview" aria-label={c('Vista previa del ticket', 'Receipt preview')}><PosReceipt ref={receipt} ticket={ticket} company={company} locale={locale} language={language} /></div>
    </div>
  </Modal>
}

// ------------------------------------------------------------------ editar línea / descuento

export function NumberDialog({ open, title, description, initial, decimals, suffix, max, language, onClose, onConfirm }: {
  open: boolean
  title: string
  description?: string
  initial: number | null
  decimals: number
  suffix?: string
  max?: number
  language: Lang
  onClose: () => void
  onConfirm: (value: number | null) => void
}) {
  const c = tr(language)
  const [value, setValue] = useState('')
  useEffect(() => { if (open) setValue(toKeypad(initial, decimals)) }, [open, initial, decimals])
  const parsed = parseKeypad(value)
  const invalid = parsed != null && (parsed < 0 || (max != null && parsed > max))
  const confirm = () => { if (!invalid) onConfirm(parsed) }
  return <Modal open={open} onClose={onClose} title={title} description={description}>
    <form className="pos-number-dialog" onSubmit={(event) => { event.preventDefault(); confirm() }}>
      <div className="pos-number-display"><input aria-label={title} data-autofocus inputMode="decimal" value={value} onChange={(event) => setValue(event.target.value)} autoComplete="off" />{suffix && <span>{suffix}</span>}</div>
      {invalid && <div className="inline-error" role="alert">{max != null ? c(`Debe estar entre 0 y ${max}.`, `Must be between 0 and ${max}.`) : c('No puede ser negativo.', 'Cannot be negative.')}</div>}
      <PosKeypad language={language} value={value} onChange={setValue} decimals={decimals} onEnter={confirm} enterLabel={c('Aplicar', 'Apply')} />
    </form>
  </Modal>
}

// ------------------------------------------------------------------ entradas y salidas de efectivo

export function CashMovementDialog({ open, summary, currency, locale, language, onClose, onSaved }: {
  open: boolean
  summary: PosSessionSummary | null
  currency: string
  locale: string
  language: Lang
  onClose: () => void
  onSaved: (summary: PosSessionSummary, message: string) => void
}) {
  const c = tr(language)
  const [type, setType] = useState<'INCOME' | 'EXPENSE' | 'WITHDRAWAL'>('INCOME')
  const [amount, setAmount] = useState('')
  const [concept, setConcept] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { if (open) { setType('INCOME'); setAmount(''); setConcept(''); setError('') } }, [open])
  if (!summary) return null
  const manual = summary.movements.filter((movement) => movement.type === 'INCOME' || movement.type === 'EXPENSE' || movement.type === 'WITHDRAWAL')
  const labels = { INCOME: c('Entrada de efectivo', 'Cash in'), EXPENSE: c('Pago / gasto', 'Expense'), WITHDRAWAL: c('Retirada (a banco o caja fuerte)', 'Withdrawal (bank or safe)') }
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const value = parseKeypad(amount)
    if (!value || value <= 0 || !concept.trim()) { setError(c('Indica importe y concepto.', 'Enter amount and concept.')); return }
    setBusy(true); setError('')
    try {
      const updated = await apiFetch<PosSessionSummary>(`/api/v1/pos/sessions/${summary.sessionId}/movements`, { method: 'POST', body: JSON.stringify({ type, amount: value, concept: concept.trim() }) })
      onSaved(updated, c('Movimiento de caja anotado.', 'Cash movement recorded.'))
    } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }
  return <Modal open={open} onClose={onClose} title={c('Entradas y salidas de efectivo', 'Cash in and out')} description={c(`En el cajón ahora: ${formatCurrency(summary.expectedCash, currency, locale)}`, `In the drawer now: ${formatCurrency(summary.expectedCash, currency, locale)}`)} size="large">
    <form className="pos-movement" onSubmit={submit}>
      <div className="pos-method-tabs" role="radiogroup" aria-label={c('Tipo de movimiento', 'Movement type')}>
        {(['INCOME', 'EXPENSE', 'WITHDRAWAL'] as const).map((item) => <button key={item} type="button" role="radio" aria-checked={type === item} className={type === item ? 'active' : ''} onClick={() => setType(item)}>{item === 'INCOME' ? <ArrowDownToLine size={18} /> : <ArrowUpFromLine size={18} />}{labels[item]}</button>)}
      </div>
      <div className="form-grid">
        <Field label={c('Importe', 'Amount')} htmlFor="pos-movement-amount" required><input id="pos-movement-amount" data-autofocus inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} /></Field>
        <Field label={c('Concepto', 'Concept')} htmlFor="pos-movement-concept" required hint={c('Ej.: cambio del banco, pago al repartidor…', 'E.g. change from the bank, delivery payment…')}><input id="pos-movement-concept" value={concept} maxLength={300} onChange={(event) => setConcept(event.target.value)} /></Field>
      </div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      <div className="form-actions"><button type="button" className="button button-ghost" onClick={onClose}>{c('Cerrar', 'Close')}</button><button type="submit" className="button button-primary" disabled={busy}><Wallet size={17} />{busy ? c('Guardando…', 'Saving…') : c('Anotar movimiento', 'Record movement')}</button></div>
      <h3 className="pos-subtitle">{c('Movimientos del turno', 'Movements this shift')}</h3>
      {manual.length === 0 ? <p className="pos-muted">{c('Todavía no hay entradas ni salidas manuales.', 'No manual cash movements yet.')}</p> :
        <ul className="pos-movement-list">{manual.map((movement) => <li key={movement.id}><span>{formatDateTime(movement.occurredAt, locale)}</span><strong>{movement.concept}</strong><b className={movement.signedAmount < 0 ? 'out' : 'in'}>{formatCurrency(movement.signedAmount, currency, locale)}</b></li>)}</ul>}
    </form>
  </Modal>
}

// ------------------------------------------------------------------ apertura

export function OpenSessionDialog({ open, registerName, currency, locale, language, onClose, onOpen }: {
  open: boolean
  registerName: string
  currency: string
  locale: string
  language: Lang
  onClose: () => void
  onOpen: (amount: number) => Promise<void>
}) {
  const c = tr(language)
  const [amount, setAmount] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => { if (open) { setAmount(''); setError('') } }, [open])
  const submit = async (event?: FormEvent) => {
    event?.preventDefault()
    const value = parseKeypad(amount) ?? 0
    if (value < 0) return
    setBusy(true); setError('')
    try { await onOpen(value) } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }
  return <Modal open={open} onClose={onClose} title={c(`Abrir ${registerName}`, `Open ${registerName}`)} description={c('Cuenta el fondo de cambio con el que empiezas el turno.', 'Count the change float you start the shift with.')}>
    <form className="pos-number-dialog" onSubmit={submit}>
      <div className="pos-number-display"><input aria-label={c('Fondo inicial', 'Opening float')} data-autofocus inputMode="decimal" value={amount} placeholder="0,00" onChange={(event) => setAmount(event.target.value)} /><span>{currency === 'EUR' ? '€' : currency}</span></div>
      <div className="pos-quick-cash">{[50, 100, 150, 200, 300].map((value) => <button key={value} type="button" onClick={() => setAmount(toKeypad(value))}>{formatCurrency(value, currency, locale)}</button>)}</div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      <PosKeypad language={language} value={amount} onChange={setAmount} onEnter={() => void submit()} enterLabel={busy ? c('Abriendo…', 'Opening…') : c('Abrir caja', 'Open till')} />
    </form>
  </Modal>
}

// ------------------------------------------------------------------ cierre con arqueo

export function CloseSessionDialog({ open, summary, company, cashierName, locale, language, onClose, onClosed }: {
  open: boolean
  summary: PosSessionSummary | null
  company: CompanyProfile
  cashierName: string
  locale: string
  language: Lang
  onClose: () => void
  onClosed: (summary: PosSessionSummary) => void
}) {
  const c = tr(language)
  const [counts, setCounts] = useState<Record<string, number>>({})
  const [direct, setDirect] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const report = useRef<HTMLDivElement>(null)
  useEffect(() => { if (open) { setCounts({}); setDirect(''); setNote(''); setError('') } }, [open])
  if (!summary) return null
  const currency = company.baseCurrency
  const fmt = (value: number) => formatCurrency(value, currency, locale)
  const counted = direct.trim() ? parseKeypad(direct) ?? 0 : countDenominations(counts)
  const difference = money(counted - summary.expectedCash)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (difference !== 0 && !note.trim()) { setError(c('Hay descuadre: explica el motivo en la nota antes de cerrar.', 'There is a difference: explain it in the note before closing.')); return }
    setBusy(true); setError('')
    try {
      const closed = await apiFetch<PosSessionSummary>(`/api/v1/pos/sessions/${summary.sessionId}/close`, { method: 'POST', body: JSON.stringify({ countedAmount: counted, note: note.trim() || null }) })
      onClosed(closed)
    } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }
  return <Modal open={open} onClose={onClose} title={c(`Cerrar ${summary.registerName}`, `Close ${summary.registerName}`)} description={c('Cuenta el efectivo del cajón. El descuadre queda a la vista en el informe Z.', 'Count the cash in the drawer. Any difference shows on the Z report.')} size="large">
    <form className="pos-close" onSubmit={submit}>
      <div className="pos-close-grid">
        <section aria-label={c('Recuento de efectivo', 'Cash count')}>
          <h3 className="pos-subtitle">{c('Recuento por billetes y monedas', 'Count by notes and coins')}</h3>
          <div className="pos-denominations">{EURO_DENOMINATIONS.map((value) => <label key={value}>
            <span>{value >= 5 ? c('Billete', 'Note') : c('Moneda', 'Coin')} {fmt(value)}</span>
            <input type="number" min={0} step={1} inputMode="numeric" value={counts[String(value)] || ''} disabled={Boolean(direct.trim())} onChange={(event) => setCounts((current) => ({ ...current, [String(value)]: Math.max(0, Math.floor(Number(event.target.value) || 0)) }))} aria-label={c(`Cantidad de ${fmt(value)}`, `Count of ${fmt(value)}`)} />
            <b>{fmt(value * (counts[String(value)] || 0))}</b>
          </label>)}</div>
          <Field label={c('O escribe el total contado', 'Or type the counted total')} htmlFor="pos-close-direct"><input id="pos-close-direct" inputMode="decimal" value={direct} onChange={(event) => setDirect(event.target.value)} /></Field>
        </section>
        <section className="pos-close-summary" aria-label={c('Resumen del turno', 'Shift summary')}>
          <dl>
            <div><dt>{c('Fondo inicial', 'Opening float')}</dt><dd>{fmt(summary.openingAmount)}</dd></div>
            <div><dt>{c('Ventas en efectivo', 'Cash sales')}</dt><dd>{fmt(summary.figures.cashSales)}</dd></div>
            <div><dt>{c('Devoluciones en efectivo', 'Cash refunds')}</dt><dd>-{fmt(summary.figures.cashRefunds)}</dd></div>
            <div><dt>{c('Entradas', 'Cash in')}</dt><dd>{fmt(summary.manualIncome)}</dd></div>
            <div><dt>{c('Salidas y retiradas', 'Cash out')}</dt><dd>-{fmt(summary.manualOutflow)}</dd></div>
            {summary.otherCollections > 0 && <div><dt>{c('Cobros de recibos', 'Receipt collections')}</dt><dd>{fmt(summary.otherCollections)}</dd></div>}
            <div className="strong"><dt>{c('Debe haber', 'Expected')}</dt><dd>{fmt(summary.expectedCash)}</dd></div>
            <div className="strong"><dt>{c('Contado', 'Counted')}</dt><dd>{fmt(counted)}</dd></div>
          </dl>
          <div className={`pos-difference ${difference === 0 ? 'ok' : difference > 0 ? 'over' : 'short'}`} aria-live="polite"><span>{difference === 0 ? c('Caja cuadrada', 'Till balanced') : difference > 0 ? c('Sobra', 'Over') : c('Falta', 'Short')}</span><strong>{fmt(Math.abs(difference))}</strong></div>
          <p className="pos-muted">{c(`Con tarjeta se han cobrado ${fmt(summary.figures.cardSales - summary.figures.cardRefunds)}: compáralo con el cierre del datáfono.`, `Card total ${fmt(summary.figures.cardSales - summary.figures.cardRefunds)}: compare it with the card terminal closing.`)}</p>
          <Field label={c('Nota del cierre', 'Closing note')} htmlFor="pos-close-note"><textarea id="pos-close-note" value={note} maxLength={300} onChange={(event) => setNote(event.target.value)} /></Field>
        </section>
      </div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      <div className="form-actions">
        <button type="button" className="button button-ghost" onClick={() => printReceipt(report.current, `X ${summary.registerCode}`)}><Printer size={17} />{c('Imprimir informe X', 'Print X report')}</button>
        <button type="button" className="button button-ghost" onClick={onClose}>{c('Seguir vendiendo', 'Keep selling')}</button>
        <button type="submit" className="button button-primary" disabled={busy}><Check size={17} />{busy ? c('Cerrando…', 'Closing…') : c('Cerrar caja', 'Close till')}</button>
      </div>
      <div className="pos-hidden-print"><PosSessionReport ref={report} summary={summary} company={company} locale={locale} language={language} cashierName={cashierName} /></div>
    </form>
  </Modal>
}

export function SessionReportDialog({ summary, company, cashierName, locale, language, onClose }: {
  summary: PosSessionSummary | null
  company: CompanyProfile
  cashierName: string
  locale: string
  language: Lang
  onClose: () => void
}) {
  const c = tr(language)
  const report = useRef<HTMLDivElement>(null)
  if (!summary) return null
  return <Modal open onClose={onClose} title={summary.status === 'CLOSED' ? c('Caja cerrada · informe Z', 'Till closed · Z report') : c('Informe X', 'X report')} size="large">
    <div className="pos-receipt-dialog">
      <div className="pos-receipt-summary">
        <div className={`pos-difference ${summary.difference == null || summary.difference === 0 ? 'ok' : summary.difference > 0 ? 'over' : 'short'}`}><span>{summary.status === 'CLOSED' ? c('Descuadre', 'Difference') : c('Efectivo esperado', 'Expected cash')}</span><strong>{formatCurrency(summary.status === 'CLOSED' ? summary.difference ?? 0 : summary.expectedCash, company.baseCurrency, locale)}</strong></div>
        <p>{c('Ventas netas', 'Net sales')}: <strong>{formatCurrency(summary.figures.netTotal, company.baseCurrency, locale)}</strong> · {summary.figures.ticketCount} {c('tickets', 'tickets')}</p>
        <div className="pos-receipt-buttons">
          <button type="button" className="button button-secondary" onClick={() => printReceipt(report.current, `Z ${summary.registerCode}`)}><Printer size={18} />{c('Imprimir informe', 'Print report')}</button>
          <button type="button" className="button button-primary" data-autofocus onClick={onClose}>{c('Hecho', 'Done')}</button>
        </div>
      </div>
      <div className="pos-receipt-preview"><PosSessionReport ref={report} summary={summary} company={company} locale={locale} language={language} cashierName={cashierName} /></div>
    </div>
  </Modal>
}

// ------------------------------------------------------------------ tickets del turno

export function TicketsDialog({ open, sessionId, company, locale, language, onClose, onChanged, onRefund }: {
  open: boolean
  sessionId: string
  company: CompanyProfile
  locale: string
  language: Lang
  onClose: () => void
  onChanged: (message: string) => void
  onRefund: (ticket: PosTicket) => void
}) {
  const c = tr(language)
  const [tickets, setTickets] = useState<PosTicket[] | null>(null)
  const [selected, setSelected] = useState<PosTicket | null>(null)
  const [query, setQuery] = useState('')
  const [voiding, setVoiding] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const receipt = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    let active = true
    setError('')
    apiFetch<{ content: PosTicket[] }>(`/api/v1/pos/tickets?sessionId=${sessionId}&size=200&sort=issuedAt,desc`)
      .then((response) => { if (active) { setTickets(response.content); setSelected((current) => current ? response.content.find((item) => item.id === current.id) ?? response.content[0] ?? null : response.content[0] ?? null) } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
    return () => { active = false }
  }, [open, sessionId, refresh])
  useEffect(() => { setVoiding(false); setReason('') }, [selected?.id])
  const visible = useMemo(() => (tickets ?? []).filter((ticket) => !query.trim() || ticket.number.toLowerCase().includes(query.trim().toLowerCase())), [tickets, query])
  const fmt = (value: number) => formatCurrency(value, company.baseCurrency, locale)
  const voidTicket = async () => {
    if (!selected || !reason.trim()) return
    setError('')
    try {
      await apiFetch(`/api/v1/pos/tickets/${selected.id}/void`, { method: 'POST', body: JSON.stringify({ reason: reason.trim() }) })
      onChanged(c(`Ticket ${selected.number} anulado.`, `Ticket ${selected.number} voided.`))
      setRefresh((value) => value + 1)
    } catch (cause) { setError(errorMessage(cause)) }
  }
  return <Modal open={open} onClose={onClose} title={c('Tickets del turno', 'Tickets this shift')} size="large">
    <div className="pos-tickets">
      <div className="pos-tickets-list">
        <div className="input-prefix"><Search size={16} /><input aria-label={c('Buscar ticket', 'Search ticket')} value={query} onChange={(event) => setQuery(event.target.value)} placeholder={c('Número de ticket', 'Ticket number')} /></div>
        {tickets === null ? <LoadingState /> : visible.length === 0 ? <EmptyState title={c('Sin tickets', 'No tickets')} description={c('Los tickets cobrados en este turno aparecen aquí.', 'Tickets charged in this shift appear here.')} /> :
          <ul>{visible.map((ticket) => <li key={ticket.id}><button type="button" className={selected?.id === ticket.id ? 'active' : ''} onClick={() => setSelected(ticket)}>
            <span><strong>{ticket.number}</strong><small>{formatDateTime(ticket.issuedAt, locale)} · {paymentLabels[ticket.paymentMethod][language === 'es' ? 0 : 1]}</small></span>
            <span className="pos-ticket-amount">{ticket.kind === 'REFUND' ? '-' : ''}{fmt(ticket.totalAmount)}{ticket.status === 'VOIDED' && <StatusBadge tone="danger">{c('Anulado', 'Voided')}</StatusBadge>}{ticket.kind === 'REFUND' && <StatusBadge tone="warning">{c('Devolución', 'Refund')}</StatusBadge>}</span>
          </button></li>)}</ul>}
      </div>
      <div className="pos-ticket-detail">
        {error && <div className="inline-error" role="alert">{error}</div>}
        {selected ? <>
          <div className="pos-ticket-actions">
            <button type="button" className="button button-secondary button-small" onClick={() => printReceipt(receipt.current, selected.number)}><Printer size={15} />{c('Reimprimir', 'Reprint')}</button>
            {selected.refundable && <button type="button" className="button button-secondary button-small" onClick={() => onRefund(selected)}><Undo2 size={15} />{c('Devolver artículos', 'Refund items')}</button>}
            {selected.voidable && <button type="button" className="button button-danger button-small" onClick={() => setVoiding(true)}><Ban size={15} />{c('Anular ticket', 'Void ticket')}</button>}
          </div>
          {voiding && <form className="pos-void-form" onSubmit={(event) => { event.preventDefault(); void voidTicket() }}>
            <Field label={c('Motivo de la anulación', 'Reason for voiding')} htmlFor="pos-void-reason" required hint={c('Devuelve el efectivo del ticket y repone la mercancía en el almacén.', 'Returns the ticket cash and puts goods back in stock.')}><input id="pos-void-reason" data-autofocus value={reason} maxLength={300} onChange={(event) => setReason(event.target.value)} /></Field>
            <div className="form-actions"><button type="button" className="button button-ghost" onClick={() => setVoiding(false)}>{c('Cancelar', 'Cancel')}</button><button type="submit" className="button button-danger" disabled={!reason.trim()}><Ban size={16} />{c('Anular', 'Void')}</button></div>
          </form>}
          <div className="pos-receipt-preview compact"><PosReceipt ref={receipt} ticket={selected} company={company} locale={locale} language={language} /></div>
        </> : <p className="pos-muted">{c('Elige un ticket para verlo.', 'Choose a ticket to view it.')}</p>}
      </div>
    </div>
  </Modal>
}

// ------------------------------------------------------------------ devoluciones

export function RefundDialog({ open, sessionId, initialTicket, company, locale, language, onClose, onRefunded }: {
  open: boolean
  sessionId: string
  initialTicket: PosTicket | null
  company: CompanyProfile
  locale: string
  language: Lang
  onClose: () => void
  onRefunded: (ticket: PosTicket) => void
}) {
  const c = tr(language)
  const [number, setNumber] = useState('')
  const [ticket, setTicket] = useState<PosTicket | null>(null)
  const [quantities, setQuantities] = useState<Record<string, number>>({})
  const [method, setMethod] = useState<'CASH' | 'CARD'>('CASH')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [camera, setCamera] = useState(false)
  const reference = useRef(newClientReference())
  useEffect(() => {
    if (!open) return
    reference.current = newClientReference()
    setNumber(initialTicket?.number ?? ''); setTicket(initialTicket); setQuantities({}); setReason(''); setError('')
    setMethod(initialTicket?.paymentMethod === 'CARD' ? 'CARD' : 'CASH')
  }, [open, initialTicket])
  const load = async (value: string) => {
    const trimmed = value.trim()
    if (!trimmed) return
    setError(''); setNumber(trimmed)
    try {
      const found = await apiFetch<PosTicket>(`/api/v1/pos/tickets/by-number/${encodeURIComponent(trimmed)}`)
      setTicket(found); setQuantities({})
      setMethod(found.paymentMethod === 'CARD' ? 'CARD' : 'CASH')
      if (!found.refundable) setError(found.kind === 'REFUND' ? c('Es un ticket de devolución.', 'It is a refund ticket.') : found.status === 'VOIDED' ? c('El ticket está anulado.', 'The ticket is voided.') : c('Ya se devolvió todo lo de este ticket.', 'Everything on this ticket was already refunded.'))
    } catch (cause) { setTicket(null); setError(errorMessage(cause)) }
  }
  const fmt = (value: number) => formatCurrency(value, ticket?.currencyCode ?? company.baseCurrency, locale)
  const selectedLines = (ticket?.lines ?? []).filter((line) => (quantities[line.id] ?? 0) > 0)
  const estimate = money(selectedLines.reduce((sum, line) => sum + line.lineTotal * (quantities[line.id] / line.quantity), 0))
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!ticket || selectedLines.length === 0 || !reason.trim()) { setError(c('Elige qué se devuelve y el motivo.', 'Choose what is returned and the reason.')); return }
    setBusy(true); setError('')
    try {
      const refund = await apiFetch<PosTicket>(`/api/v1/pos/tickets/${ticket.id}/refund`, { method: 'POST', body: JSON.stringify({ sessionId, paymentMethod: method, reason: reason.trim(), clientReference: reference.current, lines: selectedLines.map((line) => ({ lineId: line.id, quantity: quantities[line.id] })) }) })
      onRefunded(refund)
    } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }
  return <Modal open={open} onClose={onClose} title={c('Devolución', 'Refund')} description={c('Escanea el código de barras del ticket o escribe su número.', 'Scan the receipt barcode or type its number.')} size="large">
    <form className="pos-refund" onSubmit={submit}>
      <div className="pos-refund-search">
        <div className="input-prefix"><ReceiptText size={16} /><input aria-label={c('Número de ticket', 'Ticket number')} data-autofocus value={number} onChange={(event) => setNumber(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void load(number) } }} placeholder="T2026-CAJA1-000001" /></div>
        <button type="button" className="button button-secondary" onClick={() => void load(number)}><Search size={16} />{c('Buscar', 'Find')}</button>
        <button type="button" className="button button-ghost" onClick={() => setCamera(true)} aria-label={c('Escanear ticket con la cámara', 'Scan receipt with camera')}><ScanBarcode size={16} /></button>
      </div>
      {ticket && <>
        <p className="pos-muted">{ticket.number} · {formatDateTime(ticket.issuedAt, locale)} · {ticket.registerName} · {fmt(ticket.totalAmount)}</p>
        <div className="table-scroll"><table className="pos-refund-table">
          <thead><tr><th>{c('Artículo', 'Item')}</th><th className="align-right">{c('Vendidas', 'Sold')}</th><th className="align-right">{c('Pendientes', 'Remaining')}</th><th className="align-right">{c('Importe', 'Amount')}</th><th>{c('Devolver', 'Return')}</th></tr></thead>
          <tbody>{ticket.lines.map((line) => <tr key={line.id}>
            <td><strong>{line.productName}</strong><small>{line.productCode}</small></td>
            <td className="align-right">{formatNumber(line.quantity, locale, 3)}</td>
            <td className="align-right">{formatNumber(line.refundableQuantity, locale, 3)}</td>
            <td className="align-right">{fmt(line.lineTotal)}</td>
            <td><div className="pos-stepper">
              <button type="button" aria-label={c('Una menos', 'One less')} disabled={!(quantities[line.id] > 0)} onClick={() => setQuantities((current) => ({ ...current, [line.id]: Math.max(0, (current[line.id] ?? 0) - 1) }))}>−</button>
              <input aria-label={c(`Unidades a devolver de ${line.productName}`, `Units to return of ${line.productName}`)} inputMode="decimal" value={quantities[line.id] ?? 0} onChange={(event) => setQuantities((current) => ({ ...current, [line.id]: Math.min(line.refundableQuantity, Math.max(0, parseKeypad(event.target.value) ?? 0)) }))} disabled={line.refundableQuantity <= 0} />
              <button type="button" aria-label={c('Una más', 'One more')} disabled={(quantities[line.id] ?? 0) + 1 > line.refundableQuantity} onClick={() => setQuantities((current) => ({ ...current, [line.id]: Math.min(line.refundableQuantity, (current[line.id] ?? 0) + 1) }))}>+</button>
              <button type="button" className="pos-stepper-all" disabled={line.refundableQuantity <= 0} onClick={() => setQuantities((current) => ({ ...current, [line.id]: line.refundableQuantity }))}>{c('Todo', 'All')}</button>
            </div></td>
          </tr>)}</tbody>
        </table></div>
        <div className="form-grid">
          <Field label={c('Se devuelve', 'Refund to')} htmlFor="pos-refund-method"><select id="pos-refund-method" value={method} onChange={(event) => setMethod(event.target.value as 'CASH' | 'CARD')}><option value="CASH">{c('En efectivo', 'Cash')}</option><option value="CARD">{c('A la tarjeta', 'Card')}</option></select></Field>
          <Field label={c('Motivo', 'Reason')} htmlFor="pos-refund-reason" required><input id="pos-refund-reason" value={reason} maxLength={300} onChange={(event) => setReason(event.target.value)} placeholder={c('Ej.: producto defectuoso, cambio de talla…', 'E.g. faulty product, size exchange…')} /></Field>
        </div>
        <div className="pos-refund-total"><span>{c('A devolver', 'To refund')}</span><strong>{fmt(estimate)}</strong></div>
      </>}
      {error && <div className="inline-error" role="alert">{error}</div>}
      <div className="form-actions"><button type="button" className="button button-ghost" onClick={onClose}>{c('Cancelar', 'Cancel')}</button><button type="submit" className="button button-primary" disabled={busy || !ticket || selectedLines.length === 0}><RotateCcw size={17} />{busy ? c('Registrando…', 'Recording…') : c('Registrar devolución', 'Record refund')}</button></div>
    </form>
    <CameraScanner open={camera} onClose={() => setCamera(false)} onDetected={(code) => { setCamera(false); void load(code) }} title={c('Escanear ticket', 'Scan receipt')} />
  </Modal>
}
