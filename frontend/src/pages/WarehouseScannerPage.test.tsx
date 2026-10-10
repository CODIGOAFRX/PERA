import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { RouterProvider } from '../routing/Router'
import { WarehouseScannerPage } from './WarehouseScannerPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ identity: { id: 'u2', username: 'almacen1', displayName: 'Iván Rojas · Almacén', roles: ['WAREHOUSE'], permissions: ['inventory:read', 'inventory:scan', 'products:read', 'packaging:read'] }, logout: vi.fn(), hasPermission: () => true }) }))

const flat = <T,>(content: T[]) => ({ content, page: 0, size: 50, totalElements: content.length, totalPages: 1 })
const warehouses = [{ id: 'w1', code: 'CENTRAL', name: 'Almacén central', location: null, defaultWarehouse: true, active: true }]
const sardines = { id: 'p9', code: 'SARD-120', name: 'Sardinas en aceite 120 g', description: null, productTypeId: null, familyId: null, categoryId: null, unitOfMeasure: 'UNIT', basePrice: 1.2, taxRate: 10, active: true, createdAt: '2026-10-01T00:00:00Z', barcode: '2000000000091' }

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path.startsWith('/api/v1/warehouses')) return Promise.resolve(flat(warehouses))
    if (path.startsWith('/api/v1/stock-movements?')) return Promise.resolve(flat([]))
    if (path.startsWith('/api/v1/products/lookup')) return Promise.resolve(sardines)
    if (path.startsWith('/api/v1/stock-levels')) return Promise.resolve(flat([{ id: 'l1', warehouseId: 'w1', productId: 'p9', productCode: 'SARD-120', productName: sardines.name, unitOfMeasure: 'UNIT', quantity: 40, updatedAt: '2026-10-10T08:00:00Z' }]))
    if (path.startsWith('/api/v1/product-packaging')) return Promise.resolve({ content: [{ id: 'k1', code: 'CAJA24', unitsPerPackage: 24, defaultPackaging: true, active: true }] })
    if (path === '/api/v1/stock-movements/scan' && init?.method === 'POST') return Promise.resolve({ id: 'm1', warehouseId: 'w1', productId: 'p9', productCode: 'SARD-120', productName: sardines.name, unitOfMeasure: 'UNIT', type: 'ADJUSTMENT_IN', quantity: 72, balanceAfter: 112, unitCost: null, costCurrencyCode: null, occurredAt: '2026-10-10T08:05:00Z', sourceType: 'SCAN', sourceId: null, sourceNumber: null, note: null, performedByName: 'Iván', reasonCode: 'RECEIPT' })
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><RouterProvider><ToastProvider><WarehouseScannerPage /></ToastProvider></RouterProvider></I18nProvider>)

it('adds three boxes of a product typed by hand, with its reason and the default warehouse', async () => {
  view()
  fireEvent.click(await screen.findByText('Añadir producto'))
  fireEvent.click(screen.getByText('Introducir a mano'))
  fireEvent.change(screen.getByLabelText('Código del artículo'), { target: { value: '2000000000091' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))

  expect(await screen.findByRole('heading', { name: sardines.name })).toBeInTheDocument()
  expect(await screen.findByText('40')).toBeInTheDocument()
  fireEvent.click(await screen.findByRole('radio', { name: /CAJA24/ }))
  fireEvent.change(screen.getByLabelText('Cantidad'), { target: { value: '3' } })
  expect(screen.getByText('72')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Añadir 72' }))

  expect(await screen.findByText('Entrada registrada')).toBeInTheDocument()
  const scan = api.mock.calls.find(([path, init]) => path === '/api/v1/stock-movements/scan' && init?.method === 'POST')!
  expect(JSON.parse(String(scan[1].body))).toMatchObject({ warehouseId: 'w1', productId: 'p9', direction: 'IN', quantity: 72, reason: 'RECEIPT', unitOfMeasure: 'UNIT', note: '3 × CAJA24' })
})

it('does not let the operator remove more than is recorded and asks for a reason', async () => {
  view()
  fireEvent.click(await screen.findByText('Retirar producto'))
  fireEvent.click(screen.getByText('Introducir a mano'))
  fireEvent.change(screen.getByLabelText('Código del artículo'), { target: { value: 'SARD-120' } })
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
  await screen.findByRole('heading', { name: sardines.name })
  await screen.findByText('40')

  fireEvent.change(screen.getByLabelText('Cantidad'), { target: { value: '50' } })
  expect(screen.getByRole('alert')).toHaveTextContent('Solo constan 40')
  expect(screen.getByRole('button', { name: 'Retirar 50' })).toBeDisabled()

  fireEvent.change(screen.getByLabelText('Cantidad'), { target: { value: '2' } })
  expect(screen.getByRole('button', { name: 'Retirar 2' })).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: 'Roto o dañado' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Retirar 2' })).toBeEnabled())
})
