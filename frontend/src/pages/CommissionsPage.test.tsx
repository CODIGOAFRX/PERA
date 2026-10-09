import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { CommissionsPage } from './CommissionsPage'

const { api, permissions } = vi.hoisted(() => ({ api: vi.fn(), permissions: { list: [] as string[] } }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ hasPermission: (permission: string) => permissions.list.includes(permission) }) }))

const paged = <T,>(content: T[]) => ({ content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } })
const seller = { id: 's1', code: 'V01', name: 'Marta Ruiz', email: null, phone: null, commissionPercentage: 2, active: true }
const commission = {
  id: 'k1', documentId: 'd1', documentNumber: 'FAC-2026-000030', documentType: 'INVOICE', issueDate: '2026-10-09', customerName: 'Cliente Demo',
  paymentStatus: 'PAID', salespersonId: 's1', salespersonName: 'Marta Ruiz', baseAmount: 240, commissionAmount: 7.3, status: 'PENDING',
  calculatedAt: '2026-10-09T08:00:00Z', settledOn: null, settlementNote: null, lines: [],
}
const detail = { ...commission, lines: [
  { order: 1, description: 'Tornillo', baseAmount: 100, percentage: 5, commissionAmount: 5, origin: 'RULE' },
  { order: 2, description: 'Portes', baseAmount: 90, percentage: 2, commissionAmount: 1.8, origin: 'DEFAULT' },
] }

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  permissions.list = ['commissions:read', 'commissions:write']
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path.startsWith('/api/v1/salespeople')) return Promise.resolve([seller])
    if (path === '/api/v1/commissions/calculate') return Promise.resolve({ documents: 3, calculated: 2, settledSkipped: 1, commissionAmount: 12 })
    if (path === '/api/v1/commissions/settle') return Promise.resolve([{ ...commission, status: 'SETTLED' }])
    if (path.startsWith('/api/v1/commissions/totals')) return Promise.resolve({ count: 1, baseAmount: 240, commissionAmount: 7.3 })
    if (path === '/api/v1/commissions/k1') return Promise.resolve(detail)
    if (path.startsWith('/api/v1/commissions?')) return Promise.resolve(paged([commission]))
    if (path.startsWith('/api/v1/commission-rules')) return Promise.resolve(init?.method ? {} : [{ id: 'r1', salespersonId: 's1', productId: 'p1', productLabel: 'TOR-M6 · Tornillo', productGroupId: null, productGroupLabel: null, amountFrom: null, amountTo: null, percentage: 5, active: true }])
    if (path.startsWith('/api/v1/products')) return Promise.resolve(paged([{ id: 'p1', code: 'TOR-M6', name: 'Tornillo' }]))
    if (path.startsWith('/api/v1/product-groups')) return Promise.resolve(paged([]))
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><CommissionsPage /></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('lists the pending commissions with totals and recalculates the period', async () => {
  view()
  const table = await screen.findByRole('table', { name: 'Comisiones' })
  expect(table).toHaveTextContent('FAC-2026-000030')
  expect(table).toHaveTextContent('Cobrada')
  expect(api.mock.calls.some(([url]) => String(url).startsWith('/api/v1/commissions?') && String(url).includes('status=PENDING'))).toBe(true)
  fireEvent.change(screen.getByLabelText('Comercial'), { target: { value: 's1' } })
  fireEvent.click(screen.getByRole('button', { name: 'Recalcular' }))
  expect(await screen.findByText(/2 facturas recalculadas, 1 ya liquidadas/)).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/commissions/calculate')[0][1]?.body))).toMatchObject({ salespersonId: 's1' })
})

it('shows the commission of every line and settles the selected ones', async () => {
  view()
  fireEvent.click(await screen.findByText('FAC-2026-000030'))
  const dialog = await screen.findByRole('dialog')
  expect(within(dialog).getByRole('table', { name: 'Comisión por línea' })).toHaveTextContent('Por defecto del comercial')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cerrar' }))
  fireEvent.click(screen.getByLabelText('Seleccionar FAC-2026-000030'))
  fireEvent.click(screen.getByRole('button', { name: /Liquidar \(1\)/ }))
  fireEvent.change(await screen.findByLabelText(/^Nota/), { target: { value: 'Nómina de octubre' } })
  fireEvent.click(screen.getAllByRole('button', { name: 'Liquidar' }).at(-1)!)
  expect(await screen.findByText('Comisiones liquidadas.')).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/commissions/settle')[0][1]?.body))).toMatchObject({ ids: ['k1'], note: 'Nómina de octubre' })
})

it('lists and creates the rules of a salesperson', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Reglas' }))
  expect(await screen.findByRole('table', { name: 'Reglas de comisión' })).toHaveTextContent('TOR-M6 · Tornillo')
  expect(screen.getByText(/se aplica su comisión por defecto: 2/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Nueva regla' }))
  fireEvent.change(await screen.findByLabelText(/^Importe de línea desde/), { target: { value: '0' } })
  fireEvent.change(screen.getByLabelText(/^Hasta/), { target: { value: '60' } })
  fireEvent.change(screen.getByLabelText(/^Comisión/), { target: { value: '1' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear regla' }))
  await waitFor(() => expect(calls('POST', '/api/v1/commission-rules')).toHaveLength(1))
  expect(JSON.parse(String(calls('POST', '/api/v1/commission-rules')[0][1]?.body))).toMatchObject({ salespersonId: 's1', productId: null, amountFrom: 0, amountTo: 60, percentage: 1 })
})

it('read-only users cannot recalculate or settle', async () => {
  permissions.list = ['commissions:read']
  view()
  await screen.findByRole('table', { name: 'Comisiones' })
  expect(screen.queryByRole('button', { name: 'Recalcular' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Liquidar/ })).not.toBeInTheDocument()
})
