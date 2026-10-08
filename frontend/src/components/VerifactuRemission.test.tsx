import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import type { VerifactuRecord } from '../types/api'
import { VerifactuRemission } from './VerifactuRemission'

const { api, permissions } = vi.hoisted(() => ({ api: vi.fn(), permissions: { list: [] as string[] } }))
vi.mock('../lib/api', () => ({ apiFetch: api, errorMessage: (e: Error) => e.message }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ hasPermission: (permission: string) => permissions.list.includes(permission) }) }))

const record = (overrides: Partial<VerifactuRecord> = {}): VerifactuRecord => ({
  id: 'r1', documentId: 'd1', recordType: 'ALTA', sequenceNumber: 7, issuerTaxId: 'B12345678', invoiceNumber: 'FAC-1',
  invoiceDate: '2026-10-08', invoiceKind: 'F1', totalTaxAmount: 21, totalAmount: 121, previousFingerprint: null,
  fingerprint: 'ABC', generatedAt: '2026-10-08T09:00:00Z', state: 'PENDING', aeatCsv: null, qrPayload: null,
  aeatErrorCode: null, aeatMessage: null, attemptCount: 0, lastAttemptAt: null, ...overrides,
})
const summary = (overrides = {}) => ({ enabled: true, environment: 'PRODUCTION', connectionConfigured: true, connectionActive: true, nextSendAt: null, lastSentAt: null, failures: 0, lastError: null, counts: {}, ...overrides })
const view = (value: VerifactuRecord, onChanged = vi.fn()) => render(<I18nProvider><VerifactuRemission record={value} onChanged={onChanged} /></I18nProvider>)

beforeEach(() => { api.mockReset(); localStorage.clear(); permissions.list = ['verifactu:read', 'verifactu:write'] })

it('explains that a pending record goes out automatically and lets it be sent now', async () => {
  api.mockResolvedValueOnce(summary()).mockResolvedValueOnce({ state: 'NOT_SENT', message: 'La AEAT fija un tiempo de espera entre envíos.' })
  const onChanged = vi.fn()
  view(record(), onChanged)
  expect(await screen.findByText('Producción')).toBeInTheDocument()
  expect(screen.getByText(/Se remitirá automáticamente en el próximo envío/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Enviar ahora' }))
  expect(await screen.findByText('La AEAT fija un tiempo de espera entre envíos.')).toBeInTheDocument()
  expect(api).toHaveBeenCalledWith('/api/v1/verifactu-records/r1/delivery', { method: 'POST' })
  expect(onChanged).toHaveBeenCalled()
})

it('shows the AEAT error of a rejected record and does not offer to resend it', async () => {
  api.mockResolvedValue(summary())
  view(record({ state: 'REJECTED', aeatErrorCode: '1100', aeatMessage: 'NIF no identificado', attemptCount: 1, lastAttemptAt: '2026-10-08T09:01:00Z' }))
  expect(await screen.findByText('1100 · NIF no identificado')).toBeInTheDocument()
  expect(screen.getByText(/no lo reenvía tal cual/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Enviar ahora' })).not.toBeInTheDocument()
})

it('tells that sending is disabled and offers nothing to read-only users', async () => {
  api.mockResolvedValue(summary({ connectionActive: false, environment: 'TEST' }))
  view(record())
  expect(await screen.findByText(/no está activada/)).toBeInTheDocument()
  expect(screen.getByText('Pruebas')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Enviar ahora' })).not.toBeInTheDocument()

  api.mockResolvedValue(summary())
  permissions.list = ['verifactu:read']
  view(record({ state: 'SENT' }))
  expect(await screen.findByText(/sin duplicarlo/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Enviar ahora' })).not.toBeInTheDocument()
})
