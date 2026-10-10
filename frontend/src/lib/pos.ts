/**
 * Cálculos del TPV en el navegador. Repiten las reglas del servidor (PosCalculator) para enseñar
 * el total mientras se marca; el importe que vale es siempre el que devuelve el servidor al cobrar.
 */
import type { Product } from '../types/api'

export type PosPaymentMethod = 'CASH' | 'CARD' | 'MIXED'
export type PosTicketKind = 'SALE' | 'REFUND'
export type PosTicketStatus = 'COMPLETED' | 'VOIDED'

export interface CartLine {
  key: string
  product: Pick<Product, 'id' | 'code' | 'name' | 'unitOfMeasure' | 'basePrice' | 'taxRate'> & { barcode?: string | null }
  quantity: number
  /** Precio con impuestos; null usa el PVP del catálogo. */
  unitPrice: number | null
  discountPercent: number
}

export interface TaxLine { rate: number; base: number; tax: number; total: number }

export interface CartTotals {
  lineTotals: number[]
  grossAmount: number
  discountAmount: number
  totalAmount: number
  taxes: TaxLine[]
  items: number
}

export const round2 = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100

/** Redondeo a céntimos como BigDecimal HALF_UP, evitando errores de coma flotante en x,xx5. */
export function money(value: number) {
  const sign = value < 0 ? -1 : 1
  const cents = Math.round(Number((Math.abs(value) * 100).toFixed(6)))
  return (sign * cents) / 100
}

export function retailPrice(basePrice: number, taxRate: number) {
  return money(Number(basePrice) * (100 + Number(taxRate)) / 100)
}

export function linePrice(line: CartLine) {
  return line.unitPrice ?? retailPrice(line.product.basePrice, line.product.taxRate)
}

export function cartTotals(lines: CartLine[], ticketDiscountPercent = 0): CartTotals {
  const lineTotals: number[] = []
  let gross = 0
  let items = 0
  for (const line of lines) {
    const lineGross = money(line.quantity * linePrice(line))
    const afterLine = money(lineGross * (100 - line.discountPercent) / 100)
    lineTotals.push(money(afterLine * (100 - ticketDiscountPercent) / 100))
    gross += lineGross
    items += line.quantity
  }
  const total = money(lineTotals.reduce((sum, value) => sum + value, 0))
  return {
    lineTotals,
    grossAmount: money(gross),
    discountAmount: money(gross - total),
    totalAmount: total,
    taxes: taxBreakdown(lineTotals, lines.map((line) => Number(line.product.taxRate))),
    items,
  }
}

export function taxBreakdown(totals: number[], rates: number[]): TaxLine[] {
  const byRate = new Map<number, number>()
  totals.forEach((total, index) => byRate.set(rates[index], (byRate.get(rates[index]) ?? 0) + total))
  return [...byRate.entries()].sort(([left], [right]) => left - right).map(([rate, total]) => {
    const base = money(total * 100 / (100 + rate))
    return { rate, base, tax: money(total - base), total: money(total) }
  })
}

/** Billetes que tiene sentido ofrecer como «entrega» para un total: el siguiente de cada valor. */
export function quickCashOptions(total: number): number[] {
  if (total <= 0) return []
  const options = new Set<number>([money(total)])
  for (const step of [1, 5, 10, 20, 50, 100]) {
    const rounded = Math.ceil(total / step) * step
    if (rounded > total) options.add(rounded)
  }
  for (const note of [5, 10, 20, 50, 100, 200]) if (note > total) options.add(note)
  return [...options].sort((left, right) => left - right).slice(0, 6)
}

/** Billetes y monedas de euro para contar el cajón al cerrar. */
export const EURO_DENOMINATIONS = [500, 200, 100, 50, 20, 10, 5, 2, 1, 0.5, 0.2, 0.1, 0.05, 0.02, 0.01]

export function countDenominations(counts: Record<string, number>) {
  return money(EURO_DENOMINATIONS.reduce((sum, value) => sum + value * (counts[String(value)] || 0), 0))
}

export interface PosTicketLine {
  id: string
  lineNumber: number
  productId: string
  productCode: string
  productName: string
  barcode: string | null
  unitOfMeasure: string
  quantity: number
  unitPrice: number
  priceOverridden: boolean
  discountPercent: number
  taxRate: number
  lineTotal: number
  refundedQuantity: number
  refundableQuantity: number
}

export interface PosTicket {
  id: string
  number: string
  kind: PosTicketKind
  status: PosTicketStatus
  issuedAt: string
  registerId: string
  registerCode: string
  registerName: string
  sessionId: string
  cashierName: string
  customerName: string | null
  customerTaxId: string | null
  originalTicketId: string | null
  originalTicketNumber: string | null
  currencyCode: string
  discountPercent: number
  grossAmount: number
  discountAmount: number
  totalAmount: number
  taxBaseAmount: number
  taxAmount: number
  taxes: TaxLine[]
  paymentMethod: PosPaymentMethod
  cashAmount: number
  cardAmount: number
  cashTendered: number | null
  changeAmount: number
  note: string | null
  voidedAt: string | null
  voidReason: string | null
  voidable: boolean
  refundable: boolean
  lines: PosTicketLine[]
}

export interface PosOpenSession {
  id: string
  openedAt: string
  openingAmount: number
  cashBalance: number
  salesTotal: number
  ticketCount: number
}

export interface PosRegister {
  id: string
  code: string
  name: string
  ownerName: string | null
  active: boolean
  openSession: PosOpenSession | null
  lastClosedAt: string | null
}

export interface ProductSales { productId: string; productCode: string; productName: string; quantity: number; total: number }
export interface HourSales { hour: number; total: number; tickets: number }

export interface PosFigures {
  salesTotal: number
  refundsTotal: number
  netTotal: number
  cashSales: number
  cardSales: number
  cashRefunds: number
  cardRefunds: number
  discountTotal: number
  ticketCount: number
  refundCount: number
  voidedCount: number
  averageTicket: number
  itemsSold: number
  taxes: TaxLine[]
  topProducts: ProductSales[]
  hourly: HourSales[]
}

export interface PosCashMovement {
  id: string
  occurredAt: string
  type: 'OPENING' | 'SALE_COLLECTION' | 'INCOME' | 'EXPENSE' | 'WITHDRAWAL' | 'CLOSING_ADJUSTMENT' | 'POS_SALE' | 'POS_REFUND'
  amount: number
  signedAmount: number
  receiptId: string | null
  concept: string
}

export interface PosSessionSummary {
  sessionId: string
  registerId: string
  registerCode: string
  registerName: string
  status: 'OPEN' | 'CLOSED'
  openedAt: string
  closedAt: string | null
  openingAmount: number
  manualIncome: number
  manualOutflow: number
  otherCollections: number
  expectedCash: number
  countedCash: number | null
  difference: number | null
  closingNote: string | null
  figures: PosFigures
  movements: PosCashMovement[]
}

export interface GroupSales { key: string; label: string; total: number; refunds: number; tickets: number }
export interface DaySales { date: string; total: number; refunds: number; tickets: number }
export interface SessionLine { sessionId: string; registerCode: string; registerName: string; status: 'OPEN' | 'CLOSED'; openedAt: string; closedAt: string | null; netTotal: number; ticketCount: number; difference: number | null }

export interface PosStats {
  from: string
  to: string
  figures: PosFigures
  byDay: DaySales[]
  byRegister: GroupSales[]
  byCashier: GroupSales[]
  sessions: SessionLine[]
}

export const paymentLabels: Record<PosPaymentMethod, [string, string]> = {
  CASH: ['Efectivo', 'Cash'],
  CARD: ['Tarjeta', 'Card'],
  MIXED: ['Mixto', 'Mixed'],
}

export function browserTimeZone() {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Madrid' } catch { return 'Europe/Madrid' }
}

/** Identificador para que reintentar un cobro (red lenta, doble pulsación) no cree otro ticket. */
export function newClientReference() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * 16)
    return (char === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
}
