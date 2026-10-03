import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { localIsoDate } from '../lib/date'
import { AgendaPage, mondayOf } from './AgendaPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))

const today = localIsoDate()
const flat = <T,>(content: T[]) => ({ content, page: 0, size: 15, totalElements: content.length, totalPages: 1 })
const types = [{ id: 't1', name: 'Medición', color: '#2f6b3f', active: true }]
const entry = { id: 'e1', date: today, startTime: '09:30:00', endTime: '10:30:00', title: 'Medir ventanas', details: null, typeId: 't1', assigneeName: 'Juan', customerId: null, customerName: null, contactId: 'p1', contactName: 'Pedro Ruiz', contactPhone: '600111222', location: 'Calle Mayor 3', documentReference: 'PRE-1', status: 'PENDING', completedOn: null, outcome: null, createdByName: 'Raúl' }
const contact = { id: 'p1', name: 'Pedro Ruiz', organization: null, phone: null, mobile: '600111222', email: null, address: null, postalCode: null, city: 'Valencia', region: null, customerId: null, customerName: null, notes: null, active: true }

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string) => {
    if (path.startsWith('/api/v1/agenda-entry-types')) return Promise.resolve(types)
    if (path.startsWith('/api/v1/agenda-entries/assignees')) return Promise.resolve(['Juan'])
    if (path.startsWith('/api/v1/agenda-entries?')) return Promise.resolve([entry])
    if (path.startsWith('/api/v1/agenda-entries')) return Promise.resolve({ ...entry, status: 'DONE', completedOn: today })
    if (path.startsWith('/api/v1/contacts')) return Promise.resolve(flat([contact]))
    if (path.startsWith('/api/v1/customers')) return Promise.reject(new Error('sin permiso'))
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><AgendaPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('finds the monday of any day of the week', () => {
  expect(mondayOf('2026-10-05')).toBe('2026-10-05')
  expect(mondayOf('2026-10-08')).toBe('2026-10-05')
  expect(mondayOf('2026-10-11')).toBe('2026-10-05')
})

it('shows this week with the appointment on its day and moves week by week', async () => {
  view()
  const entryButton = await screen.findByRole('button', { name: /Medir ventanas/ })
  expect(entryButton).toHaveTextContent('09:30–10:30')
  expect(entryButton).toHaveTextContent('Medición · Juan · Pedro Ruiz')
  const monday = mondayOf(today)
  expect(api.mock.calls.some(([url]) => String(url).includes(`fromDate=${monday}`))).toBe(true)

  fireEvent.click(screen.getByRole('button', { name: 'Semana siguiente' }))
  const next = new Date(`${monday}T00:00:00`); next.setDate(next.getDate() + 7)
  await vi.waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes(`fromDate=${localIsoDate(next)}`))).toBe(true))
  // La cita es de esta semana: en la siguiente no se pinta aunque el servidor la devolviera.
  expect(await screen.findAllByText('Sin citas')).toHaveLength(7)
  expect(screen.queryByRole('button', { name: /Medir ventanas/ })).not.toBeInTheDocument()
})

it('creates an appointment even when the profile cannot read customers', async () => {
  view()
  await screen.findByRole('button', { name: /Medir ventanas/ })
  fireEvent.click(screen.getByRole('button', { name: 'Nueva cita' }))
  fireEvent.change(await screen.findByLabelText(/^Qué/), { target: { value: ' Montaje cocina ' } })
  fireEvent.change(screen.getByLabelText(/^Tipo/), { target: { value: 't1' } })
  fireEvent.change(screen.getByLabelText(/^Desde/), { target: { value: '16:00' } })
  fireEvent.change(screen.getByLabelText(/^Quién/), { target: { value: 'Juan' } })
  fireEvent.change(await screen.findByLabelText(/^Contacto del listín/), { target: { value: 'p1' } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear cita' }))

  expect(await screen.findByText('Cita guardada.')).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/agenda-entries')[0][1]?.body))).toMatchObject({ date: today, startTime: '16:00', endTime: null, title: 'Montaje cocina', typeId: 't1', assigneeName: 'Juan', customerId: null, contactId: 'p1' })
})

it('marks an appointment as done with what was done', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: /Medir ventanas/ }))
  const detail = await screen.findByRole('dialog', { name: 'Medir ventanas' })
  expect(detail).toHaveTextContent('600111222')
  fireEvent.click(within(detail).getByRole('button', { name: 'Marcar como hecha' }))
  fireEvent.change(await screen.findByLabelText(/^Qué se hizo/), { target: { value: '4 huecos medidos' } })
  fireEvent.click(screen.getAllByRole('button', { name: 'Marcar como hecha' }).at(-1)!)

  expect(await screen.findByText('Cita hecha.')).toBeInTheDocument()
  expect(JSON.parse(String(calls('POST', '/api/v1/agenda-entries/e1/complete')[0][1]?.body))).toEqual({ completedOn: today, outcome: '4 huecos medidos' })
})

it('searches the directory on the server', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Listín' }))
  expect(await screen.findByRole('table', { name: 'Listín de contactos' })).toHaveTextContent('600111222')
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'pedro' } })
  expect(await screen.findByRole('table', { name: 'Listín de contactos' })).toBeInTheDocument()
  await vi.waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('query=pedro'))).toBe(true))
})
