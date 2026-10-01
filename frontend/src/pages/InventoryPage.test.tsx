import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { InventoryPage } from './InventoryPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))

const flat = <T,>(content: T[]) => ({ content, page: 0, size: 15, totalElements: content.length, totalPages: 1 })
const warehouses = [{ id: 'w1', code: 'MAIN', name: 'Principal', location: null, defaultWarehouse: true, active: true }, { id: 'w2', code: 'SEC', name: 'Secundario', location: 'Nave 2', defaultWarehouse: false, active: true }]
const level = { id: 'lv1', warehouseId: 'w1', productId: 'p1', productCode: 'P-1', productName: 'Tornillo M6', unitOfMeasure: 'UNIT', quantity: 5.5, updatedAt: '2026-10-01T10:00:00Z' }
const movement = { id: 'm1', warehouseId: 'w1', productId: 'p1', productCode: 'P-1', productName: 'Tornillo M6', unitOfMeasure: 'UNIT', type: 'PURCHASE_RECEIPT', quantity: 2, balanceAfter: 2, unitCost: 27.95, costCurrencyCode: 'EUR', occurredAt: '2026-10-01T09:00:00Z', sourceType: 'PURCHASE_DOCUMENT', sourceId: 'd1', sourceNumber: 'AC-2026-000001', note: null }

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string) => {
    if (path.startsWith('/api/v1/stock-levels')) return Promise.resolve(flat([level]))
    if (path.startsWith('/api/v1/stock-movements?')) return Promise.resolve(flat([movement]))
    if (path.startsWith('/api/v1/warehouses')) return Promise.resolve(flat(warehouses))
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><InventoryPage /></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('shows the stock per warehouse and only products in stock by default', async () => {
  view()
  const table = await screen.findByRole('table', { name: 'Existencias por almacén' })
  expect(table).toHaveTextContent('Tornillo M6')
  expect(table).toHaveTextContent('MAIN')
  expect(table).toHaveTextContent('5,5')
  expect(api.mock.calls.some(([url]) => String(url).startsWith('/api/v1/stock-levels') && String(url).includes('onlyInStock=true'))).toBe(true)

  fireEvent.change(screen.getByLabelText('Filtrar por almacén'), { target: { value: 'w2' } })
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('warehouseId=w2'))).toBe(true))
})

it('shows the journal with the signed quantity, the cost and the source document', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Diario de almacén' }))
  const table = await screen.findByRole('table', { name: 'Diario de almacén' })
  expect(table).toHaveTextContent('Entrada de compra')
  expect(table).toHaveTextContent('+2')
  expect(table).toHaveTextContent('27,95')
  expect(table).toHaveTextContent('AC-2026-000001')
})

it('records an adjustment from a stock row with the product snapshot and a mandatory reason', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Ajustar existencias de P-1' }))
  expect(await screen.findByLabelText(/^Producto/)).toHaveValue('P-1 · Tornillo M6')
  expect(screen.getByLabelText(/^Motivo/)).toBeRequired()
  fireEvent.change(screen.getByLabelText(/^Tipo de ajuste/), { target: { value: 'ADJUSTMENT_OUT' } })
  fireEvent.change(screen.getByLabelText(/^Cantidad/), { target: { value: '1.5' } })
  fireEvent.change(screen.getByLabelText(/^Motivo/), { target: { value: ' Rotura ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Registrar ajuste' }))

  expect(await screen.findByText('Ajuste registrado.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/stock-movements/adjustments')
  expect(JSON.parse(String(init?.body))).toEqual({ warehouseId: 'w1', productId: 'p1', productCode: 'P-1', productName: 'Tornillo M6', unitOfMeasure: 'UNIT', type: 'ADJUSTMENT_OUT', quantity: 1.5, note: 'Rotura' })
})

it('creates a warehouse from the warehouses tab', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Almacenes' }))
  expect(await screen.findByRole('table', { name: 'Almacenes' })).toHaveTextContent('Nave 2')
  fireEvent.click(screen.getByRole('button', { name: 'Nuevo almacén' }))
  fireEvent.change(await screen.findByLabelText(/^Código/), { target: { value: 'TALLER' } })
  fireEvent.change(screen.getByLabelText(/^Nombre/), { target: { value: 'Taller' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear almacén' }))

  expect(await screen.findByText('Almacén guardado.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/warehouses')
  expect(JSON.parse(String(init?.body))).toEqual({ code: 'TALLER', name: 'Taller', location: null, defaultWarehouse: false, active: true })
})
