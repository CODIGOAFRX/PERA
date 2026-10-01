import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import type { PurchaseDocument } from '../types/api'
import { PurchasesPage } from './PurchasesPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))

const base = {
  supplierId: 's1', supplierCode: 'PR-1', supplierName: 'Proveedor Demo', supplierTaxId: 'B12345678', supplierReference: null, issueDate: '2026-10-01', expectedDate: null,
  warehouseId: 'w1', currencyCode: 'EUR', sourceDocumentId: null, stockReceivedUpstream: false, stockPosted: false, netAmount: 55.9, taxAmount: 11.74, totalAmount: 67.64, notes: null,
  lines: [{ id: 'l1', sequence: 1, productId: 'p1', productCode: 'P-1', description: 'Tornillo M6', unitOfMeasure: 'UNIT', quantity: 2, unitPrice: 27.95, discountPercentage: 0, taxPercentage: 21, netAmount: 55.9 }],
}
const order = { ...base, id: 'doc-1', type: 'PURCHASE_ORDER', number: 'PC-2026-000001', status: 'CONFIRMED' } as PurchaseDocument
const receipt = { ...base, id: 'doc-2', type: 'GOODS_RECEIPT', number: 'AC-2026-000001', status: 'DRAFT' } as PurchaseDocument

const flat = <T,>(content: T[]) => ({ content, page: 0, size: 12, totalElements: content.length, totalPages: 1 })
const paged = <T,>(content: T[]) => ({ content, page: { size: 100, number: 0, totalElements: content.length, totalPages: 1 } })
const supplier = { id: 's1', code: 'PR-1', legalName: 'Proveedor Demo', taxId: 'B12345678', active: true }
const product = { id: 'p1', code: 'P-1', name: 'Tornillo M6', unitOfMeasure: 'KILOGRAM', basePrice: 9, taxRate: 10, active: true }
const warehouses = [{ id: 'w1', code: 'MAIN', name: 'Principal', location: null, defaultWarehouse: true, active: true }, { id: 'w2', code: 'SEC', name: 'Secundario', location: null, defaultWarehouse: false, active: true }]

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string) => {
    if (path.startsWith('/api/v1/purchase-documents?')) return Promise.resolve(flat([order, receipt]))
    if (path.startsWith('/api/v1/suppliers')) return Promise.resolve(paged([supplier]))
    if (path.startsWith('/api/v1/products')) return Promise.resolve(paged([product]))
    if (path.startsWith('/api/v1/warehouses')) return Promise.resolve(flat(warehouses))
    if (path.startsWith('/api/v1/currencies')) return Promise.resolve([])
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><PurchasesPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('lists the purchase documents and asks the server again when filtering by type', async () => {
  view()
  expect(await screen.findByText('PC-2026-000001')).toBeInTheDocument()
  expect(screen.getByRole('table', { name: 'Documentos de compra' })).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Filtrar por tipo'), { target: { value: 'SUPPLIER_INVOICE' } })
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('type=SUPPLIER_INVOICE'))).toBe(true))
})

it('warns that confirming a goods receipt moves stock and confirms it only once', async () => {
  view()
  fireEvent.click(await screen.findByText('AC-2026-000001'))
  const confirmButton = await screen.findByRole('button', { name: 'Confirmar documento' })

  fireEvent.click(confirmButton)
  const dialog = await screen.findByRole('dialog', { name: 'Confirmar acción' })
  expect(dialog).toHaveTextContent('Se dará entrada en almacén')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Confirmar acción' })).not.toBeInTheDocument())
  expect(calls('POST', '/api/v1/purchase-documents/doc-2/confirm')).toHaveLength(0)

  fireEvent.click(confirmButton)
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'Confirmar acción' })).getByRole('button', { name: 'Confirmar' }))
  expect(await screen.findByText('Documento confirmado.')).toBeInTheDocument()
  expect(calls('POST', '/api/v1/purchase-documents/doc-2/confirm')).toHaveLength(1)
})

it('converts a confirmed order into a goods receipt', async () => {
  view()
  fireEvent.click(await screen.findByText('PC-2026-000001'))
  expect(screen.queryByRole('button', { name: 'Confirmar documento' })).not.toBeInTheDocument()
  fireEvent.click(await screen.findByRole('button', { name: /Recibir mercancía/ }))
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'Confirmar acción' })).getByRole('button', { name: 'Confirmar' }))

  expect(await screen.findByText('Documento convertido. Revisa el borrador creado.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/purchase-documents/doc-1/convert')
  expect(JSON.parse(String(init?.body))).toMatchObject({ targetType: 'GOODS_RECEIPT' })
})

it('creates a draft with the supplier and product snapshots and the default warehouse', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Nuevo documento' }))
  fireEvent.change(await screen.findByLabelText(/^Proveedor/), { target: { value: 's1' } })
  expect(screen.getByLabelText('Almacén de entrada')).toHaveValue('w1')
  fireEvent.change(screen.getByLabelText('Producto'), { target: { value: 'p1' } })
  fireEvent.change(screen.getByLabelText('Coste'), { target: { value: '4.5' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }))

  expect(await screen.findByText('Documento de compra guardado.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/purchase-documents')
  const payload = JSON.parse(String(init?.body))
  expect(payload).toMatchObject({ type: 'PURCHASE_ORDER', supplierId: 's1', supplierCode: 'PR-1', supplierName: 'Proveedor Demo', supplierTaxId: 'B12345678', warehouseId: 'w1', currencyCode: 'EUR' })
  // The sale price of the catalogue is not a purchase cost: only unit and tax come from the product.
  expect(payload.lines).toEqual([{ productId: 'p1', productCode: 'P-1', description: 'Tornillo M6', unitOfMeasure: 'KILOGRAM', quantity: 1, unitPrice: 4.5, discountPercentage: 0, taxPercentage: 10 }])
})

it('does not preselect a warehouse for a direct supplier invoice', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Nuevo documento' }))
  const type = await screen.findByLabelText(/^Tipo/)
  expect(screen.getByLabelText('Almacén de entrada')).toHaveValue('w1')
  fireEvent.change(type, { target: { value: 'SUPPLIER_INVOICE' } })
  expect(screen.getByLabelText('Almacén de entrada')).toHaveValue('')
  expect(screen.getByLabelText(/Nº de factura del proveedor/)).toBeRequired()
})
