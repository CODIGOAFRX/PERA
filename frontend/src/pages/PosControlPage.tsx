import { Banknote, ChartColumnBig, CreditCard, MonitorSmartphone, Printer, Receipt, RotateCcw, Search, ShoppingBag, Tag, TrendingUp, Undo2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { PosReceipt, printReceipt } from '../components/pos/PosReceipt'
import { StatusBadge } from '../components/StatusBadge'
import { TableCaption } from '../components/TableCaption'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useCompanyProfile } from '../hooks/useCompanyProfile'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { localIsoDate } from '../lib/date'
import { formatCurrency, formatDate, formatDateTime, formatNumber } from '../lib/format'
import { browserTimeZone, paymentLabels, type PosStats, type PosTicket } from '../lib/pos'
import { Link } from '../routing/Router'

type Preset = 'today' | 'yesterday' | 'week' | 'month' | 'custom'

export function rangeFor(preset: Preset, today = new Date()): { from: string; to: string } {
  const iso = (date: Date) => localIsoDate(date)
  const day = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  if (preset === 'yesterday') { const yesterday = new Date(day); yesterday.setDate(day.getDate() - 1); return { from: iso(yesterday), to: iso(yesterday) } }
  if (preset === 'week') { const monday = new Date(day); monday.setDate(day.getDate() - ((day.getDay() + 6) % 7)); return { from: iso(monday), to: iso(day) } }
  if (preset === 'month') return { from: iso(new Date(day.getFullYear(), day.getMonth(), 1)), to: iso(day) }
  return { from: iso(day), to: iso(day) }
}

/** Seguimiento de las ventas del TPV: lo que ha pasado en cada caja, por día, cajero y artículo. */
export function PosControlPage() {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const company = useCompanyProfile()
  const [preset, setPreset] = useState<Preset>('today')
  const [range, setRange] = useState(() => rangeFor('today'))
  const [stats, setStats] = useState<PosStats | null>(null)
  const [error, setError] = useState('')
  const [tickets, setTickets] = useState<{ content: PosTicket[]; page: { number: number; totalPages: number; totalElements: number } } | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const debounced = useDebouncedValue(query.trim())
  const [selected, setSelected] = useState<PosTicket | null>(null)
  const receipt = useRef<HTMLDivElement>(null)
  const currency = company.baseCurrency
  const fmt = (value: number) => formatCurrency(value, currency, locale)
  const zone = browserTimeZone()

  const choose = (next: Preset) => { setPreset(next); if (next !== 'custom') setRange(rangeFor(next)) }
  useEffect(() => {
    let active = true
    setStats(null); setError('')
    apiFetch<PosStats>(`/api/v1/pos/stats?from=${range.from}&to=${range.to}&timeZone=${encodeURIComponent(zone)}`)
      .then((value) => { if (active) setStats(value) })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
    return () => { active = false }
  }, [range, zone])
  useEffect(() => setPage(0), [range, debounced])
  useEffect(() => {
    let active = true
    const params = new URLSearchParams({ from: range.from, to: range.to, timeZone: zone, page: String(page), size: '15', sort: 'issuedAt,desc' })
    if (debounced) params.set('query', debounced)
    apiFetch<NonNullable<typeof tickets>>(`/api/v1/pos/tickets?${params}`).then((value) => { if (active) setTickets(value) }).catch(() => { if (active) setTickets(null) })
    return () => { active = false }
  }, [range, page, debounced, zone])

  const figures = stats?.figures
  const singleDay = range.from === range.to
  const bars = singleDay ? (figures?.hourly ?? []).map((item) => ({ key: String(item.hour), label: `${String(item.hour).padStart(2, '0')}h`, total: item.total, count: item.tickets }))
    : (stats?.byDay ?? []).map((item) => ({ key: item.date, label: formatDate(item.date, locale).replace(/ \d{4}$/, ''), total: item.total - item.refunds, count: item.tickets }))
  const maxBar = Math.max(1, ...bars.map((item) => item.total))

  return <div className="page-stack pos-control">
    <PageHeader eyebrow={c('Caja', 'Point of sale')} title={c('Ventas TPV', 'POS sales')} description={c('Todo lo vendido en las cajas: tickets, cobros, devoluciones, arqueos y artículos más vendidos.', 'Everything sold at the tills: tickets, payments, refunds, cash counts and best sellers.')} icon={ChartColumnBig}
      actions={<Link className="button button-primary" to="/tpv"><MonitorSmartphone size={17} />{c('Abrir el TPV', 'Open the POS')}</Link>} />

    <div className="pos-control-range" role="group" aria-label={c('Periodo', 'Period')}>
      {([['today', c('Hoy', 'Today')], ['yesterday', c('Ayer', 'Yesterday')], ['week', c('Esta semana', 'This week')], ['month', c('Este mes', 'This month')], ['custom', c('Elegir fechas', 'Pick dates')]] as const).map(([value, text]) =>
        <button key={value} type="button" className={`button ${preset === value ? 'button-primary' : 'button-secondary'} button-small`} aria-pressed={preset === value} onClick={() => choose(value)}>{text}</button>)}
      {preset === 'custom' && <div className="pos-control-dates">
        <Field label={c('Desde', 'From')} htmlFor="pos-from"><input id="pos-from" type="date" value={range.from} max={range.to} onChange={(event) => event.target.value && setRange((current) => ({ ...current, from: event.target.value }))} /></Field>
        <Field label={c('Hasta', 'To')} htmlFor="pos-to"><input id="pos-to" type="date" value={range.to} min={range.from} onChange={(event) => event.target.value && setRange((current) => ({ ...current, to: event.target.value }))} /></Field>
      </div>}
    </div>

    {error && <div className="inline-error" role="alert">{error}</div>}
    {!stats || !figures ? !error && <LoadingState /> : <>
      <div className="metric-grid pos-control-kpis">
        <article className="metric-card"><span className="metric-icon"><TrendingUp size={18} /></span><span className="metric-label">{c('Ventas netas', 'Net sales')}</span><strong>{fmt(figures.netTotal)}</strong><span className="metric-hint">{c(`${figures.ticketCount} tickets · ${formatNumber(figures.itemsSold, locale)} uds.`, `${figures.ticketCount} tickets · ${formatNumber(figures.itemsSold, locale)} units`)}</span></article>
        <article className="metric-card"><span className="metric-icon"><Receipt size={18} /></span><span className="metric-label">{c('Ticket medio', 'Average ticket')}</span><strong>{fmt(figures.averageTicket)}</strong><span className="metric-hint">{c(`Descuentos: ${fmt(figures.discountTotal)}`, `Discounts: ${fmt(figures.discountTotal)}`)}</span></article>
        <article className="metric-card"><span className="metric-icon"><Banknote size={18} /></span><span className="metric-label">{c('Efectivo', 'Cash')}</span><strong>{fmt(figures.cashSales - figures.cashRefunds)}</strong><span className="metric-hint">{c(`Devuelto: ${fmt(figures.cashRefunds)}`, `Refunded: ${fmt(figures.cashRefunds)}`)}</span></article>
        <article className="metric-card"><span className="metric-icon"><CreditCard size={18} /></span><span className="metric-label">{c('Tarjeta', 'Card')}</span><strong>{fmt(figures.cardSales - figures.cardRefunds)}</strong><span className="metric-hint">{c(`Devuelto: ${fmt(figures.cardRefunds)}`, `Refunded: ${fmt(figures.cardRefunds)}`)}</span></article>
        <article className="metric-card"><span className="metric-icon"><Undo2 size={18} /></span><span className="metric-label">{c('Devoluciones', 'Refunds')}</span><strong>{fmt(figures.refundsTotal)}</strong><span className="metric-hint">{c(`${figures.refundCount} devoluciones · ${figures.voidedCount} anulados`, `${figures.refundCount} refunds · ${figures.voidedCount} voided`)}</span></article>
      </div>

      <div className="pos-control-grid">
        <section className="panel pos-control-chart" aria-labelledby="pos-chart-title">
          <div className="panel-heading"><h2 id="pos-chart-title">{singleDay ? c('Ventas por hora', 'Sales by hour') : c('Ventas por día', 'Sales by day')}</h2></div>
          {bars.length === 0 ? <EmptyState title={c('Sin ventas en el periodo', 'No sales in this period')} description={c('Cuando se cobren tickets en el TPV aparecerán aquí.', 'Tickets charged at the POS will appear here.')} /> :
            <ol className="pos-bars">{bars.map((bar) => <li key={bar.key} title={`${bar.label}: ${fmt(bar.total)} · ${bar.count}`}>
              <span className="pos-bar-value">{fmt(bar.total)}</span>
              <span className="pos-bar" style={{ height: `${Math.max(3, (bar.total / maxBar) * 100)}%` }} />
              <span className="pos-bar-label">{bar.label}</span>
            </li>)}</ol>}
        </section>
        <section className="panel" aria-labelledby="pos-top-title">
          <div className="panel-heading"><h2 id="pos-top-title">{c('Más vendidos', 'Best sellers')}</h2></div>
          {figures.topProducts.length === 0 ? <p className="pos-muted panel-padding">{c('Sin datos.', 'No data.')}</p> :
            <ol className="pos-top-list">{figures.topProducts.map((product, index) => <li key={product.productId}><b>{index + 1}</b><span><strong>{product.productName}</strong><small>{product.productCode} · {formatNumber(product.quantity, locale, 3)} {c('uds.', 'units')}</small></span><em>{fmt(product.total)}</em></li>)}</ol>}
        </section>
      </div>

      <div className="pos-control-grid three">
        <section className="panel table-panel" aria-labelledby="pos-registers-title">
          <div className="panel-heading"><h2 id="pos-registers-title">{c('Por caja', 'By till')}</h2></div>
          <div className="table-scroll"><table><TableCaption es="Ventas por caja" en="Sales by till" /><thead><tr><th>{c('Caja', 'Till')}</th><th className="align-right">{c('Tickets', 'Tickets')}</th><th className="align-right">{c('Neto', 'Net')}</th></tr></thead>
            <tbody>{stats.byRegister.length === 0 ? <tr><td colSpan={3}>—</td></tr> : stats.byRegister.map((item) => <tr key={item.key}><td><strong>{item.label}</strong><small>{item.key}</small></td><td className="align-right">{item.tickets}</td><td className="align-right"><strong>{fmt(item.total)}</strong></td></tr>)}</tbody></table></div>
        </section>
        <section className="panel table-panel" aria-labelledby="pos-cashiers-title">
          <div className="panel-heading"><h2 id="pos-cashiers-title">{c('Por cajero', 'By cashier')}</h2></div>
          <div className="table-scroll"><table><TableCaption es="Ventas por cajero" en="Sales by cashier" /><thead><tr><th>{c('Cajero', 'Cashier')}</th><th className="align-right">{c('Tickets', 'Tickets')}</th><th className="align-right">{c('Neto', 'Net')}</th></tr></thead>
            <tbody>{stats.byCashier.length === 0 ? <tr><td colSpan={3}>—</td></tr> : stats.byCashier.map((item) => <tr key={item.key}><td><strong>{item.label}</strong></td><td className="align-right">{item.tickets}</td><td className="align-right"><strong>{fmt(item.total)}</strong></td></tr>)}</tbody></table></div>
        </section>
        <section className="panel table-panel" aria-labelledby="pos-taxes-title">
          <div className="panel-heading"><h2 id="pos-taxes-title">{c('IVA repercutido', 'Output VAT')}</h2></div>
          <div className="table-scroll"><table><TableCaption es="IVA por tipo" en="VAT by rate" /><thead><tr><th>{c('Tipo', 'Rate')}</th><th className="align-right">{c('Base', 'Base')}</th><th className="align-right">{c('Cuota', 'Tax')}</th></tr></thead>
            <tbody>{figures.taxes.length === 0 ? <tr><td colSpan={3}>—</td></tr> : figures.taxes.map((tax) => <tr key={tax.rate}><td>{formatNumber(tax.rate, locale)} %</td><td className="align-right">{fmt(tax.base)}</td><td className="align-right"><strong>{fmt(tax.tax)}</strong></td></tr>)}</tbody></table></div>
        </section>
      </div>

      <section className="panel table-panel" aria-labelledby="pos-sessions-title">
        <div className="panel-heading"><h2 id="pos-sessions-title">{c('Turnos y arqueos', 'Shifts and cash counts')}</h2></div>
        <div className="table-scroll"><table><TableCaption es="Turnos de caja" en="Till shifts" /><thead><tr><th>{c('Caja', 'Till')}</th><th>{c('Apertura', 'Opened')}</th><th>{c('Cierre', 'Closed')}</th><th>{c('Estado', 'Status')}</th><th className="align-right">{c('Tickets', 'Tickets')}</th><th className="align-right">{c('Neto', 'Net')}</th><th className="align-right">{c('Descuadre', 'Difference')}</th></tr></thead>
          <tbody>{stats.sessions.length === 0 ? <tr><td colSpan={7}>{c('Ningún turno en el periodo.', 'No shifts in this period.')}</td></tr> : stats.sessions.map((session) => <tr key={session.sessionId}>
            <td><strong>{session.registerName}</strong><small>{session.registerCode}</small></td>
            <td>{formatDateTime(session.openedAt, locale)}</td><td>{formatDateTime(session.closedAt, locale)}</td>
            <td><StatusBadge tone={session.status === 'OPEN' ? 'info' : 'neutral'}>{session.status === 'OPEN' ? c('Abierta', 'Open') : c('Cerrada', 'Closed')}</StatusBadge></td>
            <td className="align-right">{session.ticketCount}</td><td className="align-right"><strong>{fmt(session.netTotal)}</strong></td>
            <td className="align-right">{session.difference == null ? '—' : <StatusBadge tone={session.difference === 0 ? 'success' : 'danger'}>{fmt(session.difference)}</StatusBadge>}</td>
          </tr>)}</tbody></table></div>
      </section>
    </>}

    <section className="panel table-panel" aria-labelledby="pos-tickets-title">
      <div className="panel-heading"><h2 id="pos-tickets-title">{c('Tickets', 'Tickets')}</h2>
        <div className="input-prefix pos-control-search"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={c('Número, cajero o cliente', 'Number, cashier or customer')} aria-label={c('Buscar tickets', 'Search tickets')} /></div>
      </div>
      {!tickets ? <LoadingState /> : tickets.content.length === 0 ? <EmptyState title={c('No hay tickets', 'There are no tickets')} description={c('Cambia el periodo o la búsqueda.', 'Change the period or the search.')} /> : <>
        <div className="table-scroll"><table><TableCaption es="Tickets del TPV" en="POS tickets" /><thead><tr><th>{c('Ticket', 'Ticket')}</th><th>{c('Fecha', 'Date')}</th><th>{c('Caja', 'Till')}</th><th>{c('Cajero', 'Cashier')}</th><th>{c('Pago', 'Payment')}</th><th className="align-right">{c('Líneas', 'Lines')}</th><th className="align-right">{c('Total', 'Total')}</th><th>{c('Estado', 'Status')}</th></tr></thead>
          <tbody>{tickets.content.map((ticket) => <tr key={ticket.id} className="clickable-row" onClick={() => setSelected(ticket)} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter') setSelected(ticket) }}>
            <td><strong className="document-number">{ticket.number}</strong>{ticket.originalTicketNumber && <small>{c('Devuelve', 'Refunds')} {ticket.originalTicketNumber}</small>}</td>
            <td>{formatDateTime(ticket.issuedAt, locale)}</td><td>{ticket.registerName}</td><td>{ticket.cashierName}</td>
            <td>{paymentLabels[ticket.paymentMethod][language === 'es' ? 0 : 1]}</td><td className="align-right">{ticket.lines.length}</td>
            <td className="align-right"><strong>{ticket.kind === 'REFUND' ? '-' : ''}{fmt(ticket.totalAmount)}</strong></td>
            <td>{ticket.status === 'VOIDED' ? <StatusBadge tone="danger">{c('Anulado', 'Voided')}</StatusBadge> : ticket.kind === 'REFUND' ? <StatusBadge tone="warning"><RotateCcw size={12} /> {c('Devolución', 'Refund')}</StatusBadge> : ticket.lines.some((line) => line.refundedQuantity > 0) ? <StatusBadge tone="info">{c('Devuelto en parte', 'Partly refunded')}</StatusBadge> : <StatusBadge tone="success"><ShoppingBag size={12} /> {c('Venta', 'Sale')}</StatusBadge>}{ticket.lines.some((line) => line.priceOverridden) && <StatusBadge tone="warning"><Tag size={12} /> {c('Precio manual', 'Manual price')}</StatusBadge>}</td>
          </tr>)}</tbody></table></div>
        <Pagination page={tickets.page.number} totalPages={tickets.page.totalPages} totalElements={tickets.page.totalElements} onChange={setPage} />
      </>}
    </section>

    <Modal open={selected !== null} onClose={() => setSelected(null)} title={selected?.number ?? ''} description={selected ? `${selected.registerName} · ${selected.cashierName}` : undefined} size="large">
      {selected && <div className="pos-receipt-dialog">
        <div className="pos-receipt-summary">
          {selected.voidReason && <p><strong>{c('Anulado', 'Voided')}:</strong> {selected.voidReason}</p>}
          {selected.kind === 'REFUND' && selected.note && <p><strong>{c('Motivo', 'Reason')}:</strong> {selected.note}</p>}
          <div className="pos-receipt-buttons"><button type="button" className="button button-secondary" onClick={() => printReceipt(receipt.current, selected.number)}><Printer size={17} />{c('Reimprimir ticket', 'Reprint receipt')}</button></div>
        </div>
        <div className="pos-receipt-preview"><PosReceipt ref={receipt} ticket={selected} company={company} locale={locale} language={language} /></div>
      </div>}
    </Modal>
  </div>
}
