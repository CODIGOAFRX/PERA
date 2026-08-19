import { describe, expect, it } from 'vitest'
import type { CommercialDocument, DueDate } from '../types/api'
import { receivableRow, reportModulesForRoles, sortReportRows, type ReportRow } from './ReportsPage'

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
    expect(reportModulesForRoles(['ECONOMY']).map((module) => module.id)).toEqual(['customers', 'quotes', 'sales', 'receivables'])
    expect(reportModulesForRoles(['LOGISTICS']).map((module) => module.id)).toEqual(['suppliers'])
    expect(reportModulesForRoles(['CATALOG']).map((module) => module.id)).toEqual(['products'])
    expect(reportModulesForRoles(['ADMIN'])).toHaveLength(6)
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
