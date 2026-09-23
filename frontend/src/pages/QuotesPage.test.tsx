import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { ApiError } from '../lib/api'
import type { CommercialDocument } from '../types/api'
import { QuotesPage } from './QuotesPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))
vi.mock('../components/DocumentEmail', () => ({ DocumentEmail: () => null }))

const quote = (id: string, number: string, quoteStatus: string) => ({
  id, number, type: 'QUOTE', status: 'CONFIRMED', customerId: 'c1', customerCode: 'C001', customerName: 'Cliente Demo',
  issueDate: '2026-09-20', dueDate: null, currency: 'EUR', paymentStatus: 'NOT_APPLICABLE', netAmount: 100, taxAmount: 21, totalAmount: 121,
  quoteStatus, quoteValidUntil: '2026-10-20', lines: [{ id: `${id}-l1`, order: 1, description: 'Servicio', quantity: 1, unitPrice: 100, discountPercentage: 0, totalAmount: 121 }],
}) as unknown as CommercialDocument

const draft = quote('q1', 'PRE-2026-000001', 'DRAFT')
const sent = quote('q2', 'PRE-2026-000002', 'SENT')
const page = (content: unknown[]) => ({ content, page: { size: 12, number: 0, totalElements: content.length, totalPages: 1 } })

function mockApi(onAction: (path: string, init?: RequestInit) => unknown = () => undefined) {
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path.startsWith('/api/v1/quotes?')) return Promise.resolve(page([draft, sent]))
    if (path.startsWith('/api/v1/customers')) return Promise.resolve(page([{ id: 'c1', code: 'C001', legalName: 'Cliente Demo', active: true }]))
    if (path.startsWith('/api/v1/products')) return Promise.resolve(page([]))
    if (path.startsWith('/api/v1/payment-methods') || path.startsWith('/api/v1/currencies')) return Promise.resolve([])
    return Promise.resolve().then(() => onAction(path, init))
  })
}

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><QuotesPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

beforeEach(() => { api.mockReset(); localStorage.clear(); mockApi() })

it('only lets drafts be selected and deletes them after confirmation', async () => {
  view()
  const checkbox = await screen.findByRole('checkbox', { name: 'Seleccionar el presupuesto PRE-2026-000001' })
  expect(screen.queryByRole('checkbox', { name: 'Seleccionar el presupuesto PRE-2026-000002' })).not.toBeInTheDocument()
  fireEvent.click(checkbox)
  fireEvent.click(screen.getByRole('button', { name: /Eliminar seleccionados/ }))

  const dialog = await screen.findByRole('dialog', { name: 'Confirmar acción' })
  expect(dialog).toHaveTextContent('¿Eliminar 1 presupuesto(s) en borrador?')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByText('Se han eliminado 1 presupuesto(s).')).toBeInTheDocument()
  expect(calls('DELETE', '/api/v1/quotes/q1')).toHaveLength(1)
  expect(calls('DELETE', '/api/v1/quotes/q2')).toHaveLength(0)
})

it('requires a reason before rejecting a sent quote', async () => {
  view()
  fireEvent.click(await screen.findByText('PRE-2026-000002'))
  fireEvent.click(await screen.findByRole('button', { name: 'Rechazar' }))
  const confirmReject = screen.getByRole('button', { name: 'Confirmar rechazo' })
  expect(confirmReject).toBeDisabled()
  fireEvent.change(screen.getByLabelText(/Motivo del rechazo/), { target: { value: '  Precio fuera de presupuesto  ' } })
  fireEvent.click(confirmReject)

  expect(await screen.findByText('Presupuesto rechazado.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/quotes/q2/reject')
  expect(JSON.parse(String(init?.body))).toEqual({ reason: 'Precio fuera de presupuesto' })
})

it('does not accept the same quote twice on a double click', async () => {
  let finish: () => void = () => undefined
  mockApi(() => new Promise<void>((resolve) => { finish = resolve }))
  view()
  fireEvent.click(await screen.findByText('PRE-2026-000002'))
  const accept = await screen.findByRole('button', { name: 'Aceptar' })
  fireEvent.click(accept)
  fireEvent.click(accept)
  await waitFor(() => expect(accept).toBeDisabled())
  finish()
  expect(await screen.findByText('Presupuesto aceptado.')).toBeInTheDocument()
  expect(calls('POST', '/api/v1/quotes/q2/accept')).toHaveLength(1)
})

it('marks the rejected line field when the server refuses a new quote', async () => {
  mockApi((path, init) => {
    if (path === '/api/v1/quotes' && init?.method === 'POST') throw new ApiError(400, 'Petición no válida', { status: 400, violations: { 'lines[0].unitPrice': 'no debe ser negativo' } })
  })
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Nuevo presupuesto' }))
  fireEvent.change(await screen.findByLabelText(/^Cliente/), { target: { value: 'c1' } })
  fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Mantenimiento' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear presupuesto' }))

  const price = screen.getByLabelText('Precio')
  await waitFor(() => expect(price).toHaveAttribute('aria-invalid', 'true'))
  expect(price).toHaveAccessibleDescription('no debe ser negativo')
  fireEvent.change(price, { target: { value: '10' } })
  expect(price).not.toHaveAttribute('aria-invalid')
})
