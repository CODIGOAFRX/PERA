import { forwardRef } from 'react'
import type { CompanyProfile } from '../../hooks/useCompanyProfile'
import { formatCurrency, formatDateTime, formatNumber } from '../../lib/format'
import { paymentLabels, type PosSessionSummary, type PosTicket } from '../../lib/pos'
import { printHtml } from '../../lib/print'
import { BarcodeSvg } from '../CodeGraphics'

/** Estilos del ticket, compartidos por la vista previa y el iframe de impresión (rollo de 80 mm). */
export const RECEIPT_CSS = `
@page { size: 80mm auto; margin: 3mm; }
.pos-receipt { width: 72mm; margin: 0 auto; color: #000; background: #fff; font: 11px/1.35 'Courier New', ui-monospace, monospace; }
.pos-receipt * { box-sizing: border-box; }
.pos-receipt header { text-align: center; padding-bottom: 6px; border-bottom: 1px dashed #000; margin-bottom: 6px; }
.pos-receipt header img { max-width: 46mm; max-height: 18mm; object-fit: contain; display: block; margin: 0 auto 4px; filter: grayscale(1); }
.pos-receipt header strong { display: block; font-size: 14px; }
.pos-receipt header span { display: block; }
.pos-receipt .r-title { text-align: center; font-weight: 700; text-transform: uppercase; margin: 6px 0 2px; letter-spacing: .04em; }
.pos-receipt .r-meta { display: grid; grid-template-columns: auto 1fr; gap: 0 6px; margin-bottom: 6px; }
.pos-receipt .r-meta dd { margin: 0; text-align: right; }
.pos-receipt .r-meta dt { margin: 0; }
.pos-receipt table { width: 100%; border-collapse: collapse; }
.pos-receipt th, .pos-receipt td { padding: 1px 0; vertical-align: top; text-align: left; font-weight: 400; }
.pos-receipt th { border-bottom: 1px dashed #000; font-size: 10px; }
.pos-receipt .num { text-align: right; white-space: nowrap; }
.pos-receipt .r-line-detail { font-size: 10px; color: #222; }
.pos-receipt .r-sep { border-top: 1px dashed #000; margin: 6px 0; }
.pos-receipt .r-total { display: flex; justify-content: space-between; font-size: 17px; font-weight: 700; margin: 4px 0; }
.pos-receipt .r-row { display: flex; justify-content: space-between; gap: 8px; }
.pos-receipt .r-small { font-size: 10px; }
.pos-receipt .r-barcode { margin: 8px auto 2px; width: 62mm; height: 14mm; display: block; }
.pos-receipt footer { text-align: center; margin-top: 6px; font-size: 10px; }
.pos-receipt .r-void { text-align: center; font-weight: 700; border: 2px solid #000; padding: 3px; margin: 6px 0; }
`

interface ReceiptProps {
  ticket: PosTicket
  company: CompanyProfile
  locale: string
  language: 'es' | 'en'
}

export const PosReceipt = forwardRef<HTMLDivElement, ReceiptProps>(function PosReceipt({ ticket, company, locale, language }, ref) {
  const c = (es: string, en: string) => language === 'es' ? es : en
  const money = (value: number) => formatCurrency(value, ticket.currencyCode, locale)
  const refund = ticket.kind === 'REFUND'
  const address = [company.addressLine1, [company.postalCode, company.city].filter(Boolean).join(' ')].filter(Boolean).join(' · ')
  return <div className="pos-receipt" ref={ref}>
    <header>
      {company.logo && <img src={company.logo} alt="" />}
      <strong>{company.displayName}</strong>
      {company.legalName && company.legalName !== company.displayName && <span>{company.legalName}</span>}
      {company.taxId && <span>NIF {company.taxId}</span>}
      {address && <span>{address}</span>}
      {company.phone && <span>{c('Tel.', 'Tel.')} {company.phone}</span>}
    </header>
    <div className="r-title">{refund ? c('Devolución', 'Refund') : c('Factura simplificada', 'Simplified invoice')}</div>
    <dl className="r-meta">
      <dt>{c('N.º', 'No.')}</dt><dd>{ticket.number}</dd>
      <dt>{c('Fecha', 'Date')}</dt><dd>{formatDateTime(ticket.issuedAt, locale)}</dd>
      <dt>{c('Caja', 'Till')}</dt><dd>{ticket.registerName}</dd>
      <dt>{c('Le atendió', 'Served by')}</dt><dd>{ticket.cashierName}</dd>
      {refund && ticket.originalTicketNumber && <><dt>{c('Rectifica', 'Corrects')}</dt><dd>{ticket.originalTicketNumber}</dd></>}
      {ticket.customerName && <><dt>{c('Cliente', 'Customer')}</dt><dd>{ticket.customerName}</dd></>}
      {ticket.customerTaxId && <><dt>NIF</dt><dd>{ticket.customerTaxId}</dd></>}
    </dl>
    {ticket.status === 'VOIDED' && <div className="r-void">{c('TICKET ANULADO', 'VOIDED TICKET')}</div>}
    <table>
      <thead><tr><th>{c('Uds.', 'Qty')}</th><th>{c('Artículo', 'Item')}</th><th className="num">{c('Importe', 'Amount')}</th></tr></thead>
      <tbody>{ticket.lines.map((line) => <tr key={line.id}>
        <td>{formatNumber(line.quantity, locale, 3)}</td>
        <td>{line.productName}<div className="r-line-detail">{money(line.unitPrice)}{line.discountPercent > 0 ? ` · -${formatNumber(line.discountPercent, locale)} %` : ''}{line.priceOverridden ? ` · ${c('precio manual', 'manual price')}` : ''}</div></td>
        <td className="num">{refund ? '-' : ''}{money(line.lineTotal)}</td>
      </tr>)}</tbody>
    </table>
    <div className="r-sep" />
    {ticket.discountAmount > 0 && <><div className="r-row"><span>{c('Suma', 'Subtotal')}</span><span>{money(ticket.grossAmount)}</span></div><div className="r-row"><span>{c('Descuentos', 'Discounts')}{ticket.discountPercent > 0 ? ` (${formatNumber(ticket.discountPercent, locale)} %)` : ''}</span><span>-{money(ticket.discountAmount)}</span></div></>}
    <div className="r-total"><span>{refund ? c('A DEVOLVER', 'REFUNDED') : 'TOTAL'}</span><span>{money(ticket.totalAmount)}</span></div>
    <table className="r-small">
      <thead><tr><th>{c('IVA', 'VAT')}</th><th className="num">{c('Base', 'Base')}</th><th className="num">{c('Cuota', 'Tax')}</th><th className="num">Total</th></tr></thead>
      <tbody>{ticket.taxes.map((tax) => <tr key={tax.rate}><td>{formatNumber(tax.rate, locale)} %</td><td className="num">{money(tax.base)}</td><td className="num">{money(tax.tax)}</td><td className="num">{money(tax.total)}</td></tr>)}</tbody>
    </table>
    <div className="r-sep" />
    <div className="r-row"><span>{c('Forma de pago', 'Payment')}</span><span>{paymentLabels[ticket.paymentMethod][language === 'es' ? 0 : 1]}</span></div>
    {ticket.cardAmount > 0 && <div className="r-row"><span>{c('Tarjeta', 'Card')}</span><span>{money(ticket.cardAmount)}</span></div>}
    {ticket.cashAmount > 0 && <div className="r-row"><span>{refund ? c('Efectivo devuelto', 'Cash refunded') : c('Efectivo', 'Cash')}</span><span>{money(ticket.cashAmount)}</span></div>}
    {!refund && ticket.cashTendered != null && ticket.cashAmount > 0 && <div className="r-row"><span>{c('Entregado', 'Tendered')}</span><span>{money(ticket.cashTendered)}</span></div>}
    {!refund && ticket.changeAmount > 0 && <div className="r-row"><strong>{c('Cambio', 'Change')}</strong><strong>{money(ticket.changeAmount)}</strong></div>}
    {refund && ticket.note && <div className="r-small">{c('Motivo', 'Reason')}: {ticket.note}</div>}
    <BarcodeSvg className="r-barcode" value={ticket.number} symbology="code128" height={40} />
    <footer>
      {refund ? c('Conserve este justificante.', 'Keep this receipt.') : <>{c('Gracias por su compra.', 'Thank you for your purchase.')}<br />{c('Conserve el ticket para cambios y devoluciones.', 'Keep your receipt for exchanges and refunds.')}</>}
      {company.website && <><br />{company.website}</>}
    </footer>
  </div>
})

export function printReceipt(element: HTMLElement | null, title: string) {
  if (element) printHtml(element.outerHTML, RECEIPT_CSS, title)
}

/** Informe X (sesión abierta) o Z (cierre) para imprimir en el mismo rollo. */
export const PosSessionReport = forwardRef<HTMLDivElement, { summary: PosSessionSummary; company: CompanyProfile; locale: string; language: 'es' | 'en'; cashierName: string }>(function PosSessionReport({ summary, company, locale, language, cashierName }, ref) {
  const c = (es: string, en: string) => language === 'es' ? es : en
  const money = (value: number | null) => value == null ? '—' : formatCurrency(value, company.baseCurrency, locale)
  const figures = summary.figures
  const closed = summary.status === 'CLOSED'
  return <div className="pos-receipt" ref={ref}>
    <header>
      {company.logo && <img src={company.logo} alt="" />}
      <strong>{company.displayName}</strong>
      {company.taxId && <span>NIF {company.taxId}</span>}
    </header>
    <div className="r-title">{closed ? c('Informe Z · cierre de caja', 'Z report · till closing') : c('Informe X · situación de caja', 'X report · till status')}</div>
    <dl className="r-meta">
      <dt>{c('Caja', 'Till')}</dt><dd>{summary.registerName}</dd>
      <dt>{c('Apertura', 'Opened')}</dt><dd>{formatDateTime(summary.openedAt, locale)}</dd>
      {closed && <><dt>{c('Cierre', 'Closed')}</dt><dd>{formatDateTime(summary.closedAt, locale)}</dd></>}
      <dt>{c('Emitido por', 'Printed by')}</dt><dd>{cashierName}</dd>
    </dl>
    <div className="r-sep" />
    <div className="r-row"><span>{c('Ventas', 'Sales')} ({figures.ticketCount})</span><span>{money(figures.salesTotal)}</span></div>
    <div className="r-row"><span>{c('Devoluciones', 'Refunds')} ({figures.refundCount})</span><span>-{money(figures.refundsTotal)}</span></div>
    <div className="r-total"><span>{c('NETO', 'NET')}</span><span>{money(figures.netTotal)}</span></div>
    <div className="r-row r-small"><span>{c('Ticket medio', 'Average ticket')}</span><span>{money(figures.averageTicket)}</span></div>
    <div className="r-row r-small"><span>{c('Descuentos aplicados', 'Discounts given')}</span><span>{money(figures.discountTotal)}</span></div>
    <div className="r-row r-small"><span>{c('Tickets anulados', 'Voided tickets')}</span><span>{figures.voidedCount}</span></div>
    <div className="r-sep" />
    <div className="r-row"><span>{c('Tarjeta (ventas)', 'Card (sales)')}</span><span>{money(figures.cardSales)}</span></div>
    <div className="r-row"><span>{c('Tarjeta (devoluciones)', 'Card (refunds)')}</span><span>-{money(figures.cardRefunds)}</span></div>
    <div className="r-sep" />
    <div className="r-row"><span>{c('Fondo inicial', 'Opening float')}</span><span>{money(summary.openingAmount)}</span></div>
    <div className="r-row"><span>{c('Efectivo de ventas', 'Cash sales')}</span><span>{money(figures.cashSales)}</span></div>
    <div className="r-row"><span>{c('Efectivo devuelto', 'Cash refunds')}</span><span>-{money(figures.cashRefunds)}</span></div>
    <div className="r-row"><span>{c('Entradas de efectivo', 'Cash in')}</span><span>{money(summary.manualIncome)}</span></div>
    <div className="r-row"><span>{c('Salidas y retiradas', 'Cash out')}</span><span>-{money(summary.manualOutflow)}</span></div>
    {summary.otherCollections > 0 && <div className="r-row"><span>{c('Cobros de recibos', 'Receipt collections')}</span><span>{money(summary.otherCollections)}</span></div>}
    <div className="r-total"><span>{c('EFECTIVO ESPERADO', 'EXPECTED CASH')}</span><span>{money(summary.expectedCash)}</span></div>
    {closed && <><div className="r-row"><span>{c('Efectivo contado', 'Counted cash')}</span><span>{money(summary.countedCash)}</span></div><div className="r-row"><strong>{c('Descuadre', 'Difference')}</strong><strong>{money(summary.difference)}</strong></div>{summary.closingNote && <div className="r-small">{summary.closingNote}</div>}</>}
    <div className="r-sep" />
    <table className="r-small">
      <thead><tr><th>{c('IVA', 'VAT')}</th><th className="num">{c('Base', 'Base')}</th><th className="num">{c('Cuota', 'Tax')}</th><th className="num">Total</th></tr></thead>
      <tbody>{figures.taxes.map((tax) => <tr key={tax.rate}><td>{formatNumber(tax.rate, locale)} %</td><td className="num">{money(tax.base)}</td><td className="num">{money(tax.tax)}</td><td className="num">{money(tax.total)}</td></tr>)}</tbody>
    </table>
    {figures.topProducts.length > 0 && <><div className="r-sep" /><div className="r-small"><strong>{c('Más vendidos', 'Best sellers')}</strong></div>
      <table className="r-small"><tbody>{figures.topProducts.slice(0, 5).map((product) => <tr key={product.productId}><td>{formatNumber(product.quantity, locale, 3)}</td><td>{product.productName}</td><td className="num">{money(product.total)}</td></tr>)}</tbody></table></>}
    <footer>PERA ERP · {formatDateTime(new Date().toISOString(), locale)}</footer>
  </div>
})
