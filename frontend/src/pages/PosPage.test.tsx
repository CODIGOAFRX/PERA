import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { RouterProvider } from '../routing/Router'
import { PosPage } from './PosPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ identity: { id: 'u1', username: 'cajero1', displayName: 'Lucía Martín · Caja', roles: ['CASHIER'], permissions: ['pos:sell'] }, company: { id: 'c1', code: 'DEMO', name: 'PERA ERP Demo' }, logout: vi.fn(), hasRole: () => false, hasPermission: () => true }) }))

const figures = { salesTotal: 0, refundsTotal: 0, netTotal: 0, cashSales: 0, cardSales: 0, cashRefunds: 0, cardRefunds: 0, discountTotal: 0, ticketCount: 0, refundCount: 0, voidedCount: 0, averageTicket: 0, itemsSold: 0, taxes: [], topProducts: [], hourly: [] }
const summary = { sessionId: 's1', registerId: 'r1', registerCode: 'CAJA1', registerName: 'Caja 1', status: 'OPEN', openedAt: '2026-10-10T07:00:00Z', closedAt: null, openingAmount: 100, manualIncome: 0, manualOutflow: 0, otherCollections: 0, expectedCash: 100, countedCash: null, difference: null, closingNote: null, figures, movements: [] }
const registers = [
  { id: 'r1', code: 'CAJA1', name: 'Caja 1', ownerName: null, active: true, openSession: { id: 's1', openedAt: '2026-10-10T07:00:00Z', openingAmount: 100, cashBalance: 100, salesTotal: 0, ticketCount: 0 }, lastClosedAt: null },
  { id: 'r2', code: 'CAJA2', name: 'Caja 2', ownerName: null, active: true, openSession: null, lastClosedAt: '2026-10-09T19:00:00Z' },
]
const water = { id: 'p1', code: 'AGUA-150', name: 'Agua mineral 1,5 L', description: null, productTypeId: null, productGroupId: null, familyId: null, categoryId: null, unitOfMeasure: 'UNIT', basePrice: 0.5, taxRate: 10, active: true, createdAt: '2026-10-01T00:00:00Z', barcode: '2000000000015' }
const bread = { ...water, id: 'p2', code: 'PAN-BAR', name: 'Barra de pan', basePrice: 1, taxRate: 4, barcode: null }
const page = <T,>(content: T[]) => ({ content, page: { size: 200, number: 0, totalElements: content.length, totalPages: 1 } })

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path === '/api/v1/pos/registers') return Promise.resolve(registers)
    if (path === '/api/v1/pos/sessions/s1/summary') return Promise.resolve(summary)
    if (path.startsWith('/api/v1/products?')) return Promise.resolve(page([water, bread]))
    if (path.startsWith('/api/v1/product-groups')) return Promise.resolve(page([]))
    if (path.startsWith('/api/v1/company-settings/current')) return Promise.resolve({ displayName: 'PERA ERP Demo', baseCurrency: 'EUR', logoStorageKey: null })
    if (path === '/api/v1/pos/tickets' && init?.method === 'POST') {
      const body = JSON.parse(String(init.body)) as { lines: unknown[] }
      return Promise.resolve({ id: 't1', number: 'T2026-CAJA1-000001', kind: 'SALE', status: 'COMPLETED', issuedAt: '2026-10-10T08:00:00Z', registerId: 'r1', registerCode: 'CAJA1', registerName: 'Caja 1', sessionId: 's1', cashierName: 'Lucía', customerName: null, customerTaxId: null, originalTicketId: null, originalTicketNumber: null, currencyCode: 'EUR', discountPercent: 0, grossAmount: 2.14, discountAmount: 0, totalAmount: 2.14, taxBaseAmount: 1.98, taxAmount: 0.16, taxes: [], paymentMethod: 'CASH', cashAmount: 2.14, cardAmount: 0, cashTendered: 5, changeAmount: 2.86, note: null, voidedAt: null, voidReason: null, voidable: true, refundable: true, lines: body.lines.map((_, index) => ({ id: `l${index}`, lineNumber: index + 1, productId: 'p1', productCode: 'AGUA-150', productName: 'Agua mineral 1,5 L', barcode: null, unitOfMeasure: 'UNIT', quantity: 2, unitPrice: 0.55, priceOverridden: false, discountPercent: 0, taxRate: 10, lineTotal: 1.1, refundedQuantity: 0, refundableQuantity: 2 })) })
    }
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><RouterProvider><ToastProvider><ConfirmProvider><PosPage /></ConfirmProvider></ToastProvider></RouterProvider></I18nProvider>)

it('lists the tills, opens the selling screen and charges a scanned ticket in cash', async () => {
  view()
  expect(await screen.findByText('Caja 2')).toBeInTheDocument()
  expect(screen.getByText('Abrir caja')).toBeInTheDocument()
  fireEvent.click(screen.getByText('Entrar a vender'))

  const search = await screen.findByRole('textbox', { name: 'Escanear o buscar artículo' })
  await screen.findByText('Barra de pan')
  // El lector escribe el código y pulsa Intro; «2*» multiplica las unidades.
  fireEvent.change(search, { target: { value: '2*2000000000015' } })
  fireEvent.keyDown(search, { key: 'Enter' })
  fireEvent.click(screen.getByText('Barra de pan'))

  const ticket = screen.getByRole('region', { name: 'Ticket actual' })
  await waitFor(() => expect(within(ticket).getByText('Agua mineral 1,5 L')).toBeInTheDocument())
  // 2 × 0,55 € + 1,04 € = 2,14 €
  expect(within(ticket).getAllByText(/2,14/).length).toBeGreaterThan(0)

  fireEvent.click(screen.getByRole('button', { name: /Cobrar/ }))
  const dialog = await screen.findByRole('dialog', { name: 'Cobrar' })
  fireEvent.change(within(dialog).getByLabelText('Entregado por el cliente'), { target: { value: '5' } })
  expect(within(dialog).getByText('2,86 €')).toBeInTheDocument()
  fireEvent.click(within(dialog).getByRole('button', { name: /Confirmar cobro/ }))

  expect(await screen.findByRole('dialog', { name: 'Ticket cobrado' })).toBeInTheDocument()
  const charge = api.mock.calls.find(([path, init]) => path === '/api/v1/pos/tickets' && init?.method === 'POST')!
  const body = JSON.parse(String(charge[1].body))
  expect(body).toMatchObject({ sessionId: 's1', paymentMethod: 'CASH', cashTendered: 5, cardAmount: null })
  expect(body.lines).toEqual([{ productId: 'p1', quantity: 2, unitPrice: null, discountPercent: null }, { productId: 'p2', quantity: 1, unitPrice: null, discountPercent: null }])
  expect(body.clientReference).toEqual(expect.any(String))
})
