import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { ApiError } from '../lib/api'
import type { CommercialDocument } from '../types/api'
import { SalesPage } from './SalesPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api, apiDownload: vi.fn() }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ hasRole: () => false }) }))
// Fiscal and e-mail blocks have their own tests; here they would only add unrelated requests.
vi.mock('../components/VerifactuBlock', () => ({ VerifactuBlock: () => null }))
vi.mock('../components/FiscalDelivery', () => ({ FiscalDelivery: () => null }))
vi.mock('../components/DocumentEmail', () => ({ DocumentEmail: () => null }))

const albaran = {
  id: 'doc-1', number: 'ALB-2026-000002', type: 'DELIVERY_NOTE', status: 'CONFIRMED', customerId: 'c1', customerCode: 'C001', customerName: 'Cliente Demo',
  issueDate: '2026-09-20', dueDate: null, currency: 'EUR', paymentStatus: 'NOT_APPLICABLE', netAmount: 55.9, taxAmount: 11.74, totalAmount: 67.64,
  lines: [{ id: 'l1', order: 1, description: 'Servicio', quantity: 2, unitPrice: 27.95, discountPercentage: 0, totalAmount: 67.64 }],
} as unknown as CommercialDocument

const page = (content: CommercialDocument[]) => ({ content, page: { size: 12, number: 0, totalElements: content.length, totalPages: 1 } })
const customer = { id: 'c1', code: 'C001', legalName: 'Cliente Demo', active: true }

function mockApi(overrides: Record<string, (init?: RequestInit) => unknown> = {}) {
  api.mockImplementation((path: string, init?: RequestInit) => {
    for (const [prefix, handler] of Object.entries(overrides)) if (path.startsWith(prefix)) return Promise.resolve().then(() => handler(init))
    if (path.startsWith('/api/v1/documents/credit-risk')) return Promise.resolve({ level: 'OK', policy: 'WARN', creditLimit: 0, warningThreshold: 0, outstanding: 0, documentAmount: 0, projected: 0, canOverride: false })
    if (path.startsWith('/api/v1/documents?')) return Promise.resolve(page([albaran]))
    if (path.startsWith('/api/v1/customers')) return Promise.resolve(page([customer as never]))
    if (path.startsWith('/api/v1/products')) return Promise.resolve(page([]))
    if (path.startsWith('/api/v1/payment-methods') || path.startsWith('/api/v1/currencies')) return Promise.resolve([])
    return Promise.resolve(undefined)
  })
}

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><SalesPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

beforeEach(() => { api.mockReset(); localStorage.clear(); mockApi() })

it('lists the documents and asks the server again when filtering by type', async () => {
  view()
  expect(await screen.findByText('ALB-2026-000002')).toBeInTheDocument()
  expect(screen.getByRole('table', { name: 'Documentos de venta' })).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Filtrar por tipo'), { target: { value: 'INVOICE' } })
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('type=INVOICE'))).toBe(true))
})

it('converts a document only after the user confirms, and only once', async () => {
  view()
  fireEvent.click(await screen.findByText('ALB-2026-000002'))
  const convert = await screen.findByRole('button', { name: /Convertir al siguiente documento/ })

  fireEvent.click(convert)
  const dialog = await screen.findByRole('dialog', { name: 'Confirmar acción' })
  expect(dialog).toHaveTextContent('ALB-2026-000002')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Confirmar acción' })).not.toBeInTheDocument())
  expect(calls('POST', '/api/v1/documents/doc-1/convert')).toHaveLength(0)

  fireEvent.click(convert)
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'Confirmar acción' })).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByText('Documento convertido correctamente.')).toBeInTheDocument()
  expect(calls('POST', '/api/v1/documents/doc-1/convert')).toHaveLength(1)
})

it('creates a document with the entered line', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Nuevo documento' }))
  fireEvent.change(await screen.findByLabelText(/^Cliente/), { target: { value: 'c1' } })
  fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Instalación' } })
  fireEvent.change(screen.getByLabelText('Precio'), { target: { value: '27.95' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear documento' }))

  expect(await screen.findByText('Documento creado correctamente.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/documents')
  const payload = JSON.parse(String(init?.body))
  expect(payload).toMatchObject({ customerId: 'c1', customerName: 'Cliente Demo' })
  expect(payload.lines).toEqual([expect.objectContaining({ description: 'Instalación', quantity: 1, unitPrice: 27.95, unitPriceOverridden: true })])
})

it('shows a server validation error on the exact line field', async () => {
  mockApi({ '/api/v1/documents/credit-risk': () => ({ level: 'OK' }), '/api/v1/documents': (init) => {
    if (init?.method !== 'POST') return page([albaran])
    throw new ApiError(400, 'Petición no válida', { status: 400, violations: { 'lines[0].quantity': 'debe ser mayor que 0' } })
  } })
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Nuevo documento' }))
  fireEvent.change(await screen.findByLabelText(/^Cliente/), { target: { value: 'c1' } })
  fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Instalación' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear documento' }))

  const quantity = screen.getByLabelText('Cantidad')
  await waitFor(() => expect(quantity).toHaveAttribute('aria-invalid', 'true'))
  expect(quantity).toHaveAccessibleDescription('debe ser mayor que 0')
  expect(screen.getByText('Revisa los campos marcados.')).toBeInTheDocument()
  expect(quantity).toHaveFocus()
})

// Built like apiFetch does: the error message is the problem detail.
const riskProblem = (extra: Record<string, unknown>) => {
  const problem = { type: 'https://pera-erp.local/problems/credit-risk', status: 409, detail: 'El documento supera el límite de crédito del cliente. Confirma para continuar.',
    policy: 'REQUIRE_CONFIRMATION', creditLimit: 1000, outstanding: 800, documentAmount: 400, projected: 1200, requiresAcknowledgement: true, blocked: false, ...extra }
  return new ApiError(409, problem.detail, problem as never)
}

async function fillNewDocument() {
  fireEvent.click(await screen.findByRole('button', { name: 'Nuevo documento' }))
  fireEvent.change(await screen.findByLabelText(/^Tipo/), { target: { value: 'INVOICE' } })
  fireEvent.change(screen.getByLabelText(/^Cliente/), { target: { value: 'c1' } })
  fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Instalación' } })
  fireEvent.change(screen.getByLabelText('Precio'), { target: { value: '400' } })
}

it('warns about the customer credit before saving', async () => {
  mockApi({ '/api/v1/documents/credit-risk': () => ({ level: 'OVER_LIMIT', policy: 'BLOCK', creditLimit: 0, warningThreshold: 0, outstanding: 0, documentAmount: 484, projected: 484, canOverride: false }) })
  view()
  await fillNewDocument()
  expect(await screen.findByText(/Venta bloqueada: el cliente supera su límite de crédito/, {}, { timeout: 2000 })).toBeInTheDocument()
})

it('asks for confirmation and repeats the save with the acknowledgement', async () => {
  const posted: string[] = []
  const base = api.getMockImplementation()!
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (init?.method === 'POST' && path.startsWith('/api/v1/documents')) {
      posted.push(path)
      if (!path.includes('riskAcknowledged=true')) return Promise.reject(riskProblem({}))
      return Promise.resolve({ ...albaran, id: 'new' })
    }
    return base(path, init)
  })
  view()
  await fillNewDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Crear documento' }))
  const dialog = await screen.findByRole('dialog', { name: 'Límite de crédito superado' })
  expect(dialog).toHaveTextContent('Pendiente de cobro')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Continuar igualmente' }))
  expect(await screen.findByText('Documento creado correctamente.')).toBeInTheDocument()
  expect(posted).toEqual(['/api/v1/documents', '/api/v1/documents?riskAcknowledged=true'])
})

it('does not offer to continue when the sale is blocked for the user', async () => {
  const base = api.getMockImplementation()!
  api.mockImplementation((path: string, init?: RequestInit) => init?.method === 'POST' && path.startsWith('/api/v1/documents')
    ? Promise.reject(riskProblem({ policy: 'BLOCK', requiresAcknowledgement: false, blocked: true, detail: 'El cliente supera su límite de crédito y tiene la venta bloqueada.' }))
    : base(path, init))
  view()
  await fillNewDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Crear documento' }))
  expect(await screen.findByText(/tiene la venta bloqueada/)).toBeInTheDocument()
  expect(screen.queryByRole('dialog', { name: 'Límite de crédito superado' })).not.toBeInTheDocument()
  expect(api.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1)
})

it('issues a draft invoice after warning that it becomes final', async () => {
  const draft = { ...albaran, id: 'inv-1', number: 'FAC-2026-000006', type: 'INVOICE', status: 'DRAFT', paymentStatus: 'PENDING' } as unknown as CommercialDocument
  mockApi({ '/api/v1/documents?': () => page([draft]) })
  view()
  fireEvent.click(await screen.findByText('FAC-2026-000006'))
  fireEvent.click(await screen.findByRole('button', { name: /Emitir factura/ }))
  const dialog = await screen.findByRole('dialog', { name: 'Confirmar acción' })
  expect(dialog).toHaveTextContent('quedará registrada en Veri*Factu')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByText('Documento confirmado correctamente.')).toBeInTheDocument()
  expect(calls('POST', '/api/v1/documents/inv-1/confirm')).toHaveLength(1)
})
