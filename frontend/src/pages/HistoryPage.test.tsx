import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { ToastProvider } from '../components/Toast'
import { HistoryPage } from './HistoryPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))
const event = { id: 'e1', occurredAt: '2026-10-10T16:00:00Z', actorName: 'Ana', action: 'SEND', resourceType: 'REMITTANCE', resourceId: 'uuid-remesa', outcome: 'SUCCESS', metadata: { remittanceNumber: 'REM-2026-0002', totalAmount: 847, currencyCode: 'EUR', receiptCount: 3 } }
const view = () => render(<I18nProvider><ToastProvider><HistoryPage /></ToastProvider></I18nProvider>)

beforeEach(() => { localStorage.clear(); api.mockReset(); api.mockResolvedValue({ content: [event], page: { number: 0, totalPages: 1, totalElements: 1 } }) })

it('shows the actor and remittance number without claiming a bank transmission', async () => {
  view()
  const table = await screen.findByRole('table', { name: 'Historial de actividad' })
  expect(table).toHaveTextContent('Presentación registrada')
  expect(table).toHaveTextContent('sin transmisión bancaria')
  expect(table).toHaveTextContent('Ana')
  expect(table).toHaveTextContent('REM-2026-0002')
  fireEvent.click(screen.getByRole('button', { name: 'Más información técnica' }))
  expect(await screen.findByRole('dialog')).toHaveTextContent('847,00')
  expect(screen.getByRole('dialog')).toHaveTextContent('Recibos3')
})

it('filters remittances on the server and searches their document number', async () => {
  view()
  await screen.findByRole('table')
  fireEvent.change(screen.getByLabelText('Filtrar por recurso'), { target: { value: 'REMITTANCE' } })
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('resourceType=REMITTANCE'))).toBe(true))
})

it('labels recovered states with an unknown original actor', async () => {
  api.mockResolvedValue({ content: [{ ...event, action: 'RECOVER_STATE', actorName: null, metadata: { ...event.metadata, historicalActorUnknown: true } }], page: { number: 0, totalPages: 1, totalElements: 1 } })
  view()
  const table = await screen.findByRole('table')
  expect(table).toHaveTextContent('Estado previo recuperado')
  expect(table).toHaveTextContent('Autor anterior desconocido')
  expect(table).toHaveTextContent('no consta el autor de la operación original')
})

it('does not describe a failed attempt as a completed operation', async () => {
  api.mockResolvedValue({ content: [{ ...event, outcome: 'FAILURE' }], page: { number: 0, totalPages: 1, totalElements: 1 } })
  view()
  expect(await screen.findByRole('table')).toHaveTextContent('Intento sin completar')
})
