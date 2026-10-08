import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { VerifactuPage } from './VerifactuPage'

const { api, permissions } = vi.hoisted(() => ({ api: vi.fn(), permissions: { list: [] as string[] } }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ hasPermission: (permission: string) => permissions.list.includes(permission) }) }))

const paged = <T,>(content: T[]) => ({ content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } })
const summary = {
  enabled: true, environment: 'PRODUCTION', connectionConfigured: true, connectionActive: true, nextSendAt: null,
  lastSentAt: '2026-10-08T09:00:00Z', failures: 2, lastError: 'La AEAT rechazó el envío: Codigo[4102]',
  counts: { PENDING: 3, SENT: 1, ACCEPTED: 40, ACCEPTED_WITH_ERRORS: 0, REJECTED: 1 },
}
const rejected = {
  id: 'r1', documentId: 'd1', recordType: 'ALTA', sequenceNumber: 45, issuerTaxId: 'B12345678', invoiceNumber: 'FAC-2026-000045',
  invoiceDate: '2026-10-08', invoiceKind: 'F1', totalTaxAmount: 21, totalAmount: 121, previousFingerprint: 'X', fingerprint: 'Y',
  generatedAt: '2026-10-08T09:00:00Z', state: 'REJECTED', aeatCsv: null, qrPayload: null, aeatErrorCode: '1100', aeatMessage: 'NIF no identificado',
  attemptCount: 1, lastAttemptAt: '2026-10-08T09:01:00Z',
}

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  permissions.list = ['verifactu:read', 'verifactu:write']
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path === '/api/v1/verifactu-records/remission') return Promise.resolve(init?.method ? { remitted: 4, message: 'Remitidos 4 registros.' } : summary)
    if (path.startsWith('/api/v1/verifactu-records/search')) return Promise.resolve(paged([rejected]))
    return Promise.resolve(undefined)
  })
})

const view = () => render(<I18nProvider><ToastProvider><VerifactuPage /></ToastProvider></I18nProvider>)

it('opens on what needs attention, with the AEAT answer of each invoice and the connection status', async () => {
  view()
  const table = await screen.findByRole('table', { name: 'Registros Veri*Factu' })
  expect(table).toHaveTextContent('FAC-2026-000045')
  expect(table).toHaveTextContent('1100 · NIF no identificado')
  const first = String(api.mock.calls.find(([url]) => String(url).startsWith('/api/v1/verifactu-records/search'))?.[0])
  expect(first).toContain('state=REJECTED')
  expect(first).toContain('state=PENDING')
  expect(first).not.toContain('state=ACCEPTED&')
  expect(screen.getByText('Producción')).toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent('2 envíos fallidos seguidos')
})

it('filters by a state from its counter', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: /Aceptado\s*40/ }))
  await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).endsWith('size=20&state=ACCEPTED'))).toBe(true))
})

it('submits the pending records on demand only for users who may write', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Remitir ahora' }))
  expect(await screen.findByText('Remitidos 4 registros.')).toBeInTheDocument()
  expect(api).toHaveBeenCalledWith('/api/v1/verifactu-records/remission', { method: 'POST' })
})

it('does not offer to submit to read-only users', async () => {
  permissions.list = ['verifactu:read']
  view()
  await screen.findByRole('table', { name: 'Registros Veri*Factu' })
  expect(screen.queryByRole('button', { name: 'Remitir ahora' })).not.toBeInTheDocument()
})
