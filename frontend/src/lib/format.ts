import type { DocumentStatus, DocumentType, PaymentStatus, UnitOfMeasure } from '../types/api'

const currencyFormatter = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' })
const numberFormatter = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })
const dateFormatter = new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })

export function formatCurrency(value: number | string | null | undefined) {
  return currencyFormatter.format(Number(value ?? 0))
}

export function formatNumber(value: number | string | null | undefined) {
  return numberFormatter.format(Number(value ?? 0))
}

export function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  return dateFormatter.format(new Date(`${value}T00:00:00`))
}

export const documentTypeLabel: Record<DocumentType, string> = {
  QUOTE: 'Presupuesto',
  DELIVERY_NOTE: 'Albarán',
  INVOICE: 'Factura',
  WORK_ORDER: 'Parte de trabajo',
}

export const documentStatusLabel: Record<DocumentStatus, string> = {
  DRAFT: 'Borrador',
  CONFIRMED: 'Confirmado',
  CONVERTED: 'Convertido',
  CANCELLED: 'Cancelado',
}

export const paymentStatusLabel: Record<PaymentStatus, string> = {
  NOT_APPLICABLE: 'No aplica',
  PENDING: 'Pendiente',
  PARTIALLY_PAID: 'Cobro parcial',
  PAID: 'Cobrado',
}

export const unitLabel: Record<UnitOfMeasure, string> = {
  UNIT: 'Unidad',
  METER: 'Metro',
  SQUARE_METER: 'Metro cuadrado',
  CUBIC_METER: 'Metro cúbico',
  KILOGRAM: 'Kilogramo',
  LITER: 'Litro',
  HOUR: 'Hora',
}
