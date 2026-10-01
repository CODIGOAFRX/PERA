import type { BadgeTone } from '../components/StatusBadge'
import type { CashMovementType, CollectionMethod, Receipt, ReceiptStatus, RemittanceStatus } from '../types/api'

type Label = [es: string, en: string]

export const receiptStatuses: ReceiptStatus[] = ['PENDING', 'REMITTED', 'COLLECTED', 'RETURNED', 'CANCELLED']
export const receiptStatusLabels: Record<ReceiptStatus, Label> = {
  PENDING: ['Pendiente', 'Pending'],
  REMITTED: ['En el banco', 'At the bank'],
  COLLECTED: ['Cobrado', 'Collected'],
  RETURNED: ['Devuelto', 'Returned'],
  CANCELLED: ['Anulado', 'Cancelled'],
}
export const receiptStatusTone: Record<ReceiptStatus, BadgeTone> = { PENDING: 'warning', REMITTED: 'info', COLLECTED: 'success', RETURNED: 'danger', CANCELLED: 'neutral' }

export const collectionMethods: CollectionMethod[] = ['BANK_TRANSFER', 'CASH', 'CARD', 'CHEQUE', 'DIRECT_DEBIT', 'OTHER']
export const collectionMethodLabels: Record<CollectionMethod, Label> = {
  BANK_TRANSFER: ['Transferencia', 'Bank transfer'],
  CASH: ['Efectivo', 'Cash'],
  CARD: ['Tarjeta', 'Card'],
  CHEQUE: ['Cheque o pagaré', 'Cheque'],
  DIRECT_DEBIT: ['Domiciliación', 'Direct debit'],
  OTHER: ['Otro', 'Other'],
}

export const remittanceStatuses: RemittanceStatus[] = ['DRAFT', 'SENT', 'SETTLED', 'PARTIALLY_RETURNED', 'CANCELLED']
export const remittanceStatusLabels: Record<RemittanceStatus, Label> = {
  DRAFT: ['Borrador', 'Draft'],
  SENT: ['Enviada al banco', 'Sent to the bank'],
  SETTLED: ['Abonada', 'Settled'],
  PARTIALLY_RETURNED: ['Con devoluciones', 'With returns'],
  CANCELLED: ['Anulada', 'Cancelled'],
}
export const remittanceStatusTone: Record<RemittanceStatus, BadgeTone> = { DRAFT: 'neutral', SENT: 'info', SETTLED: 'success', PARTIALLY_RETURNED: 'warning', CANCELLED: 'danger' }

export const cashMovementLabels: Record<CashMovementType, Label> = {
  OPENING: ['Apertura', 'Opening'],
  SALE_COLLECTION: ['Cobro de recibo', 'Receipt collection'],
  INCOME: ['Ingreso', 'Income'],
  EXPENSE: ['Gasto', 'Expense'],
  WITHDRAWAL: ['Retirada', 'Withdrawal'],
  CLOSING_ADJUSTMENT: ['Ajuste de cierre', 'Closing adjustment'],
}

export const pick = (label: Label, language: string) => label[language === 'es' ? 0 : 1]

/** Un recibo pendiente cuyo vencimiento ya pasó. `today` es la fecha local AAAA-MM-DD. */
export function isOverdue(receipt: Pick<Receipt, 'status' | 'dueDate'>, today: string) {
  return receipt.status === 'PENDING' && receipt.dueDate < today
}
