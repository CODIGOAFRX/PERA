import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { CustomersPage } from './CustomersPage'

const { api, permissions } = vi.hoisted(() => ({ api: vi.fn(), permissions: { list: [] as string[] } }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ hasPermission: (permission: string) => permissions.list.includes(permission) }) }))

const paged = <T,>(content: T[]) => ({ content, page: { size: 12, number: 0, totalElements: content.length, totalPages: 1 } })
const catalog = [
  { id: 'g1', kind: 'GROUP', name: 'Distribuidores', active: true },
  { id: 'g2', kind: 'GROUP', name: 'Antiguo', active: false },
  { id: 'r1', kind: 'INACTIVE_REASON', name: 'Cierre del negocio', active: true },
]
const salespeople = [{ id: 's1', code: 'V01', name: 'Marta Ruiz', email: null, phone: null, commissionPercentage: 3, active: true }]
const customer = {
  id: 'cu1', partyId: 'p1', code: 'C001', legalName: 'Cliente Demo', tradeName: null, taxId: 'B75777847', taxIdentificationType: 'NIF', taxCountryCode: 'ES',
  phone: '910000000', email: null, observations: null, active: true, priceListId: 't1', defaultPaymentMethodId: 'pm1', supplierCode: 'PROV-9',
  calculationMultiplier: 1, creditLimit: 1000, riskWarningThreshold: 800, riskPolicy: 'WARN', createdAt: '2026-10-01T10:00:00Z', details: {},
  classification: { groupId: 'g1', typeId: null, salespersonId: 's1', deliveryMethodId: null, inactiveReasonId: null, mobile: null, accountingAccount: '430000001' },
}

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  permissions.list = ['customers:read', 'customers:write', 'pricing:read', 'finance:read']
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path.startsWith('/api/v1/customer-catalog')) return Promise.resolve(catalog)
    if (path.startsWith('/api/v1/salespeople')) return Promise.resolve(salespeople)
    if (path.startsWith('/api/v1/tariffs')) return Promise.resolve(paged([{ id: 't1', code: 'MAY', name: 'Mayoristas', active: true }]))
    if (path.startsWith('/api/v1/payment-methods')) return Promise.resolve([{ id: 'pm1', code: 'TR', name: 'Transferencia', active: true, rules: [] }])
    if (path === '/api/v1/customers/cu1/contacts') return Promise.resolve(init?.method ? { id: 'k1' } : [])
    if (path.startsWith('/api/v1/customers?')) return Promise.resolve(paged([customer]))
    if (path.startsWith('/api/v1/customers')) return Promise.resolve(customer)
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><CustomersPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('shows group and salesperson in the list and filters by them', async () => {
  view()
  const table = await screen.findByRole('table', { name: 'Clientes' })
  await waitFor(() => expect(table).toHaveTextContent('Distribuidores'))
  expect(table).toHaveTextContent('Marta Ruiz')
  fireEvent.change(screen.getByLabelText('Filtrar por grupo'), { target: { value: 'g1' } })
  fireEvent.change(screen.getByLabelText('Filtrar por comercial'), { target: { value: 's1' } })
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('groupId=g1') && String(url).includes('salespersonId=s1'))).toBe(true))
})

it('keeps price list, payment method and supplier code when saving a customer', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: /Editar Cliente Demo/ }))
  const group = await screen.findByLabelText('Grupo')
  expect(group).toHaveTextContent('Distribuidores')
  expect(group).not.toHaveTextContent('Antiguo')
  await waitFor(() => expect(screen.getByLabelText('Tarifa')).toHaveValue('t1'))
  fireEvent.change(screen.getByLabelText(/^Razón social/), { target: { value: 'Cliente Demo SL' } })
  fireEvent.click(screen.getByRole('button', { name: /Guardar cambios/ }))

  await waitFor(() => expect(calls('PUT', '/api/v1/customers/cu1')).toHaveLength(1))
  const body = JSON.parse(String(calls('PUT', '/api/v1/customers/cu1')[0][1]?.body))
  expect(body).toMatchObject({ priceListId: 't1', defaultPaymentMethodId: 'pm1', supplierCode: 'PROV-9',
    classification: { groupId: 'g1', salespersonId: 's1', accountingAccount: '430000001', inactiveReasonId: null } })
})

it('asks for the discharge reason only when the customer is inactive', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: /Editar Cliente Demo/ }))
  expect(screen.queryByLabelText('Motivo de baja')).not.toBeInTheDocument()
  fireEvent.click(await screen.findByLabelText(/Cliente activo/))
  fireEvent.change(await screen.findByLabelText('Motivo de baja'), { target: { value: 'r1' } })
  fireEvent.click(screen.getByRole('button', { name: /Guardar cambios/ }))
  await waitFor(() => expect(calls('PUT', '/api/v1/customers/cu1')).toHaveLength(1))
  expect(JSON.parse(String(calls('PUT', '/api/v1/customers/cu1')[0][1]?.body))).toMatchObject({ active: false, classification: { inactiveReasonId: 'r1' } })
})

it('adds a contact from the customer record', async () => {
  view()
  fireEvent.click(await screen.findByText('Cliente Demo'))
  const dialog = await screen.findByRole('dialog')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Contactos' }))
  fireEvent.click(await within(dialog).findByRole('button', { name: 'Añadir contacto' }))
  fireEvent.change(await screen.findByLabelText(/^Nombre/), { target: { value: ' Luis Pérez ' } })
  fireEvent.change(screen.getByLabelText('Cargo'), { target: { value: 'Compras' } })
  fireEvent.click(screen.getAllByRole('button', { name: 'Añadir contacto' }).at(-1)!)

  expect(await screen.findByText('Contacto guardado.')).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/customers/cu1/contacts')[0][1]?.body))).toMatchObject({ name: 'Luis Pérez', position: 'Compras', email: null, primaryContact: false })
})

it('lets read-only users browse customers without edit buttons', async () => {
  permissions.list = ['customers:read']
  view()
  await screen.findByRole('table', { name: 'Clientes' })
  expect(screen.queryByRole('button', { name: /Editar Cliente Demo/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Importar' })).not.toBeInTheDocument()
  expect(api.mock.calls.some(([url]) => String(url).startsWith('/api/v1/tariffs'))).toBe(false)
})
