import { describe, expect, it } from 'vitest'
import type { CommercialDocument, DueDate } from '../types/api'
import { collectionRow, debtorRows, receivableRow, reportModulesForRoles, sortReportRows, type ReportRow } from './ReportsPage'

describe('report data preparation', () => {
  it('sorts text naturally and without depending on case', () => {
    const rows: ReportRow[] = [
      { id: '3', legalName: 'cliente 10' },
      { id: '1', legalName: 'Álvaro' },
      { id: '2', legalName: 'cliente 2' },
    ]

    expect(sortReportRows(rows, 'legalName', 'asc', 'es-ES').map((row) => row.id)).toEqual(['1', '2', '3'])
    expect(sortReportRows(rows, 'legalName', 'desc', 'es-ES').map((row) => row.id)).toEqual(['3', '2', '1'])
  })

  it('calculates the real outstanding balance from payment schedules', () => {
    const invoice = invoiceFixture({ paymentStatus: 'PARTIALLY_PAID', totalAmount: 200 })
    const dueDates: DueDate[] = [
      { id: 'd1', documentId: invoice.id, installment: 1, dueDate: '2026-08-15', amount: 100, paidAmount: 100, status: 'PAID' },
      { id: 'd2', documentId: invoice.id, installment: 2, dueDate: '2026-09-15', amount: 100, paidAmount: 25, status: 'PARTIALLY_PAID' },
    ]

    expect(receivableRow(invoice, dueDates)).toMatchObject({ paidAmount: 125, outstandingAmount: 75, dueDate: '2026-09-15' })
  })

  it('uses the invoice total when no payment schedule exists yet', () => {
    const invoice = invoiceFixture({ paymentStatus: 'PENDING', totalAmount: 325.5, dueDate: '2026-08-31' })
    expect(receivableRow(invoice, [])).toMatchObject({ paidAmount: 0, outstandingAmount: 325.5, dueDate: '2026-08-31' })
  })

  it('only exposes report modules included in each operational profile', () => {
    expect(reportModulesForRoles(['ECONOMY']).map((module) => module.id)).toEqual(['customers', 'quotes', 'sales', 'receivables', 'collections', 'debtors', 'posTickets'])
    expect(reportModulesForRoles(['LOGISTICS']).map((module) => module.id)).toEqual(['suppliers', 'stockMovements'])
    expect(reportModulesForRoles(['CATALOG']).map((module) => module.id)).toEqual(['products'])
    expect(reportModulesForRoles(['CASHIER'])).toHaveLength(0)
    expect(reportModulesForRoles(['ADMIN'])).toHaveLength(10)
  })

  it('classifies receipts as paid, pending, overdue or returned for the collections list', () => {
    const base = { id: 'r1', receiptNumber: 'REC-1', customerCode: 'C-001', customerName: 'Cliente Uno', documentNumber: 'F-001', amount: 100, currencyCode: 'EUR', collectionDate: null, collectionMethod: null, returnReason: null }
    expect(collectionRow({ ...base, dueDate: '2026-10-01', status: 'PENDING' }, '2026-10-11')).toMatchObject({ collectionState: 'OVERDUE', daysOverdue: 10, pendingAmount: 100, paidAmount: 0 })
    expect(collectionRow({ ...base, dueDate: '2026-10-20', status: 'PENDING' }, '2026-10-11')).toMatchObject({ collectionState: 'UNPAID', daysOverdue: null })
    expect(collectionRow({ ...base, dueDate: '2026-10-01', status: 'COLLECTED', collectionDate: '2026-10-02', collectionMethod: 'CASH' }, '2026-10-11')).toMatchObject({ collectionState: 'PAID', paidAmount: 100, pendingAmount: 0 })
    expect(collectionRow({ ...base, dueDate: '2026-10-01', status: 'RETURNED', returnReason: 'Sin fondos' }, '2026-10-11')).toMatchObject({ collectionState: 'RETURNED', pendingAmount: 100 })
  })

  it('adds up invoiced, collected, pending and overdue debt per customer', () => {
    const due = (id: string, documentId: string, dueDate: string, amount: number, paidAmount: number): DueDate => ({ id, documentId, installment: 1, dueDate, amount, paidAmount, status: paidAmount >= amount ? 'PAID' : 'PENDING' })
    const rows = debtorRows([
      { invoice: invoiceFixture({ id: 'i1', totalAmount: 121 }), dueDates: [due('d1', 'i1', '2026-09-01', 121, 21)] },
      { invoice: invoiceFixture({ id: 'i2', totalAmount: 60.5 }), dueDates: [due('d2', 'i2', '2026-12-01', 60.5, 0)] },
      { invoice: invoiceFixture({ id: 'i3', customerCode: 'C-002', customerName: 'Cliente Dos', totalAmount: 50, paymentStatus: 'PAID' }), dueDates: [due('d3', 'i3', '2026-09-15', 50, 50)] },
    ], '2026-10-11')
    expect(rows.find((row) => row.customerCode === 'C-001')).toMatchObject({ invoiceCount: 2, totalAmount: 181.5, paidAmount: 21, outstandingAmount: 160.5, overdueAmount: 100, oldestDueDate: '2026-09-01' })
    expect(rows.find((row) => row.customerCode === 'C-002')).toMatchObject({ invoiceCount: 1, outstandingAmount: 0, overdueAmount: 0 })
  })
})

function invoiceFixture(overrides: Partial<CommercialDocument>): CommercialDocument {
  return {
    id: 'invoice-1', number: 'F-001', type: 'INVOICE', status: 'CONFIRMED', customerId: 'customer-1',
    customerCode: 'C-001', customerName: 'Cliente Uno', issueDate: '2026-08-01', dueDate: null,
    currency: 'EUR', sourceDocumentId: null, paymentMethodId: null, paymentStatus: 'PENDING',
    netAmount: 100, taxAmount: 21, totalAmount: 121, notes: null, lines: [], quoteStatus: null,
    quoteValidUntil: null, quoteDecidedAt: null, quoteRejectionReason: null,
    invoiceKind: 'F1', rectificationType: null, rectifiedDocumentId: null, rectifiedNumber: null,
    rectifiedIssueDate: null, issued: true,
    customerTaxId: 'B75777847', customerTaxIdentificationType: 'NIF', customerTaxCountry: 'ES', ...overrides,
  }
}
