import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { isOverdue } from '../lib/collections'
import { CollectionsPage } from './CollectionsPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))

const paged = <T,>(content: T[]) => ({ content, page: { size: 12, number: 0, totalElements: content.length, totalPages: 1 } })
const receipt = { id: 'r1', receiptNumber: 'REC-2026-000001', customerId: 'c1', customerCode: 'C001', customerName: 'Cliente Demo', documentId: 'd1', documentNumber: 'FAC-2026-000001', installment: 1, amount: 60, currencyCode: 'EUR', dueDate: '2026-01-15', status: 'PENDING', collectionDate: null, collectionMethod: null, returnDate: null, returnReason: null, remittanceId: null, notes: null }
const second = { ...receipt, id: 'r2', receiptNumber: 'REC-2026-000002', installment: 2, amount: 40, dueDate: '2026-02-15' }
const remittance = { id: 'm1', remittanceNumber: 'REM-2026-0001', bankAccount: 'ES00 1111', currencyCode: 'EUR', creationDate: '2026-10-01', sentDate: null, settlementDate: null, status: 'DRAFT', totalAmount: 60, notes: null, receipts: [{ ...receipt, remittanceId: 'm1' }], invoiceUpdated: null }
const register = { id: 'k1', code: 'CAJA1', name: 'Mostrador', ownerName: null, active: true, openSessionId: 's1' }
const session = { id: 's1', cashRegisterId: 'k1', status: 'OPEN', openedAt: '2026-10-01T08:00:00Z', closedAt: null, openingAmount: 100, balance: 150, expectedClosingAmount: null, actualClosingAmount: null, difference: null, closingNote: null, movements: [{ id: 'mv1', occurredAt: '2026-10-01T09:00:00Z', type: 'INCOME', amount: 50, signedAmount: 50, receiptId: null, concept: 'Venta de mostrador' }] }

let operation = { receipts: [{ ...receipt, status: 'COLLECTED' }], invoiceUpdated: true }

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  operation = { receipts: [{ ...receipt, status: 'COLLECTED' }], invoiceUpdated: true }
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path.startsWith('/api/v1/receipts?')) return Promise.resolve(paged([receipt, second]))
    if (path.startsWith('/api/v1/receipts/')) return Promise.resolve(operation)
    if (path.startsWith('/api/v1/remittances?')) return Promise.resolve(paged([remittance]))
    if (path.startsWith('/api/v1/remittances')) return Promise.resolve(remittance)
    if (path.startsWith('/api/v1/cash-registers')) return Promise.resolve(init?.method ? register : [register])
    if (path.startsWith('/api/v1/cash-sessions?')) return Promise.resolve(paged([session]))
    if (path.startsWith('/api/v1/cash-sessions')) return Promise.resolve(session)
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><CollectionsPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('flags a pending receipt as overdue only after its due date', () => {
  expect(isOverdue({ status: 'PENDING', dueDate: '2026-01-15' }, '2026-01-16')).toBe(true)
  expect(isOverdue({ status: 'PENDING', dueDate: '2026-01-15' }, '2026-01-15')).toBe(false)
  expect(isOverdue({ status: 'COLLECTED', dueDate: '2026-01-15' }, '2026-06-01')).toBe(false)
})

it('lists the pending receipts first and filters on the server', async () => {
  view()
  expect(await screen.findByRole('table', { name: 'Recibos de cobro' })).toHaveTextContent('REC-2026-000001')
  expect(api.mock.calls.some(([url]) => String(url).startsWith('/api/v1/receipts?') && String(url).includes('status=PENDING'))).toBe(true)
  fireEvent.change(screen.getByLabelText('Filtrar por estado'), { target: { value: 'RETURNED' } })
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('status=RETURNED'))).toBe(true))
})

it('collects a receipt in cash and records it in the open cash register', async () => {
  view()
  fireEvent.click(await screen.findByText('REC-2026-000001'))
  fireEvent.click(await screen.findByRole('button', { name: 'Cobrar' }))
  fireEvent.change(await screen.findByLabelText(/^Forma de cobro/), { target: { value: 'CASH' } })
  fireEvent.change(await screen.findByLabelText(/^Caja/), { target: { value: 's1' } })
  fireEvent.change(screen.getByLabelText(/^Fecha de cobro/), { target: { value: '2026-10-01' } })
  fireEvent.click(screen.getByRole('button', { name: 'Registrar cobro' }))

  expect(await screen.findByText('Recibo cobrado.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/receipts/r1/collect')
  expect(JSON.parse(String(init?.body))).toEqual({ collectionDate: '2026-10-01', method: 'CASH', cashSessionId: 's1', notes: null })
})

it('warns when the receipt was collected but the invoice could not be updated in sales', async () => {
  operation = { receipts: [{ ...receipt, status: 'COLLECTED' }], invoiceUpdated: false }
  view()
  fireEvent.click(await screen.findByText('REC-2026-000001'))
  fireEvent.click(await screen.findByRole('button', { name: 'Cobrar' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Registrar cobro' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo actualizar el estado de cobro de la factura en Ventas')
  const [[, init]] = calls('POST', '/api/v1/receipts/r1/collect')
  expect(JSON.parse(String(init?.body))).toMatchObject({ method: 'BANK_TRANSFER', cashSessionId: null })
})

it('creates a remittance with the selected pending receipts', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Remesas' }))
  expect(await screen.findByRole('table', { name: 'Remesas' })).toHaveTextContent('REM-2026-0001')
  fireEvent.click(screen.getByRole('button', { name: 'Nueva remesa' }))
  fireEvent.change(await screen.findByLabelText(/^Cuenta de abono/), { target: { value: 'ES00 2222' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear remesa' }))
  expect(await screen.findByText('Selecciona al menos un recibo.')).toBeInTheDocument()

  fireEvent.click(screen.getByLabelText('Incluir REC-2026-000002'))
  expect(screen.getByText(/1 seleccionados/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Crear remesa' }))

  expect(await screen.findByText('Remesa guardada.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/remittances')
  expect(JSON.parse(String(init?.body))).toMatchObject({ bankAccount: 'ES00 2222', notes: null, receiptIds: ['r2'] })
})

it('sends a draft remittance to the bank on the chosen date', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Remesas' }))
  fireEvent.click(await screen.findByText('REM-2026-0001'))
  fireEvent.click(await screen.findByRole('button', { name: 'Enviar al banco' }))
  const dialog = await screen.findByRole('dialog', { name: 'Enviar al banco' })
  fireEvent.change(within(dialog).getByLabelText(/^Fecha de envío/), { target: { value: '2026-10-02' } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Enviar al banco' }))

  expect(await screen.findByText('Remesa enviada al banco.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/remittances/m1/send')
  expect(JSON.parse(String(init?.body))).toEqual({ date: '2026-10-02' })
})

it('shows the cash journal of an open session and records a manual entry', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Caja' }))
  expect(await screen.findByRole('table', { name: 'Cajas' })).toHaveTextContent('150,00')
  fireEvent.click(screen.getByRole('button', { name: 'Ver diario' }))
  expect(await screen.findByRole('table', { name: 'Apuntes de la sesión' })).toHaveTextContent('Venta de mostrador')
  fireEvent.click(screen.getByRole('button', { name: 'Nuevo apunte' }))
  fireEvent.change(await screen.findByLabelText(/^Tipo de apunte/), { target: { value: 'EXPENSE' } })
  fireEvent.change(screen.getByLabelText(/^Importe/), { target: { value: '12.5' } })
  fireEvent.change(screen.getByLabelText(/^Concepto/), { target: { value: ' Mensajería ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Registrar apunte' }))

  expect(await screen.findByText('Apunte registrado.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/cash-sessions/s1/movements')
  expect(JSON.parse(String(init?.body))).toEqual({ type: 'EXPENSE', amount: 12.5, concept: 'Mensajería' })
})
