import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { ClaimsPage } from './ClaimsPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))

const flat = <T,>(content: T[]) => ({ content, page: 0, size: 12, totalElements: content.length, totalPages: 1 })
const paged = <T,>(content: T[]) => ({ content, page: { size: 100, number: 0, totalElements: content.length, totalPages: 1 } })
const catalog = [
  { id: 'k1', kind: 'REASON', name: 'Rotura', followUpDays: null, active: true },
  { id: 'k2', kind: 'RESOLUTION', name: 'Reposición', followUpDays: null, active: true },
  { id: 'k3', kind: 'PREVENTIVE_ACTION', name: 'Revisar embalaje', followUpDays: 30, active: true },
  { id: 'k4', kind: 'REASON', name: 'Antiguo', followUpDays: null, active: false },
]
const claim = {
  id: 'c1', number: 'RCL-2026-000001', claimDate: '2026-10-01', status: 'OPEN', customerId: 'cu1', customerCode: 'C001', customerName: 'Cliente Demo',
  sourceDocumentId: null, sourceDocumentNumber: 'ALB-2026-000004', sourceDocumentDate: null, description: 'Llegaron dos piezas rotas', reportedByName: 'Ana',
  reasonId: 'k1', nonconformityId: null, causeId: null, areaId: null, responsibleId: null, resolutionId: 'k2', preventiveActionId: null,
  followUpDate: '2026-10-10', overdue: true, closedOn: null, closingNote: null, daysOpen: 2, lines: [], comments: [],
}
const customer = { id: 'cu1', code: 'C001', legalName: 'Cliente Demo', active: true }

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path.startsWith('/api/v1/claim-catalog')) return Promise.resolve(init?.method ? catalog[0] : catalog)
    if (path.startsWith('/api/v1/claims?')) return Promise.resolve(flat([claim]))
    if (path.startsWith('/api/v1/claims')) return Promise.resolve(claim)
    if (path.startsWith('/api/v1/customers')) return Promise.resolve(paged([customer]))
    if (path.startsWith('/api/v1/products')) return Promise.resolve(paged([]))
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><ClaimsPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('lists the open claims first, with their reason and an overdue follow-up', async () => {
  view()
  const table = await screen.findByRole('table', { name: 'Reclamaciones' })
  expect(table).toHaveTextContent('RCL-2026-000001')
  expect(table).toHaveTextContent('Rotura')
  expect(table).toHaveTextContent('Seguimiento vencido')
  expect(api.mock.calls.some(([url]) => String(url).startsWith('/api/v1/claims?') && String(url).includes('status=OPEN'))).toBe(true)
  fireEvent.change(screen.getByLabelText('Filtrar por estado'), { target: { value: 'OVERDUE' } })
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('overdue=true'))).toBe(true))
})

it('records a claim with only the active classification values on offer', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Nueva reclamación' }))
  fireEvent.change(await screen.findByLabelText(/^Cliente/), { target: { value: 'cu1' } })
  fireEvent.change(screen.getByLabelText(/^Qué ha pasado/), { target: { value: ' Cristal rayado ' } })
  const reason = screen.getByLabelText('Motivo')
  expect(reason).toHaveTextContent('Rotura')
  expect(reason).not.toHaveTextContent('Antiguo')
  fireEvent.change(reason, { target: { value: 'k1' } })
  fireEvent.change(screen.getByLabelText('Acción preventiva'), { target: { value: 'k3' } })
  fireEvent.click(screen.getByRole('button', { name: 'Registrar reclamación' }))

  expect(await screen.findByText('Reclamación guardada.')).toBeInTheDocument()
  const [[, init]] = calls('POST', '/api/v1/claims')
  expect(JSON.parse(String(init?.body))).toMatchObject({ customerId: 'cu1', customerName: 'Cliente Demo', description: 'Cristal rayado', reasonId: 'k1', preventiveActionId: 'k3', causeId: null, followUpDate: null, lines: [] })
})

it('adds a follow-up comment and closes the claim', async () => {
  view()
  fireEvent.click(await screen.findByText('RCL-2026-000001'))
  fireEvent.change(await screen.findByLabelText('Nuevo comentario'), { target: { value: 'Llamado el cliente' } })
  fireEvent.click(screen.getByRole('button', { name: 'Añadir comentario' }))
  expect(await screen.findByText('Comentario añadido.')).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/claims/c1/comments')[0][1]?.body))).toEqual({ text: 'Llamado el cliente' })

  fireEvent.click(screen.getByRole('button', { name: 'Cerrar reclamación' }))
  fireEvent.change(await screen.findByLabelText(/^Fecha de cierre/), { target: { value: '2026-10-03' } })
  fireEvent.click(screen.getAllByRole('button', { name: 'Cerrar reclamación' }).at(-1)!)
  expect(await screen.findByText('Reclamación cerrada.')).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/claims/c1/close')[0][1]?.body))).toEqual({ closedOn: '2026-10-03', closingNote: null })
})

it('maintains the classification tables, with days only for preventive actions', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Tablas de clasificación' }))
  fireEvent.click(await screen.findByRole('button', { name: /Acciones preventivas/ }))
  expect(await screen.findByRole('table', { name: 'Acciones preventivas' })).toHaveTextContent('30 días')
  fireEvent.click(screen.getByRole('button', { name: 'Añadir' }))
  fireEvent.change(await screen.findByLabelText(/^Nombre/), { target: { value: 'Formar al equipo' } })
  fireEvent.change(screen.getByLabelText(/^Plazo de seguimiento/), { target: { value: '60' } })
  fireEvent.click(screen.getAllByRole('button', { name: 'Añadir' }).at(-1)!)

  expect(await screen.findByText('Tabla actualizada.')).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/claim-catalog')[0][1]?.body))).toEqual({ kind: 'PREVENTIVE_ACTION', name: 'Formar al equipo', followUpDays: 60, active: true })
})
