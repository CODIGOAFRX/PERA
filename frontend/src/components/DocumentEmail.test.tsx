import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { DocumentEmail } from './DocumentEmail'
import { I18nProvider } from '../i18n/I18nProvider'
const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', () => ({ apiFetch: api, errorMessage: (e: Error) => e.message }))
beforeEach(() => { api.mockReset(); localStorage.clear(); Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true }) })
it('queues a quote and disables a duplicate delivery', async () => {
  const onQueued = vi.fn()
  api.mockResolvedValueOnce({ status: 'NOT_SENT', recipient: 'client@example.test', connectionEnabled: true })
  api.mockResolvedValueOnce({ status: 'PENDING', recipient: 'client@example.test', connectionEnabled: true })
  render(<I18nProvider><DocumentEmail id="quote-1" quote onQueued={onQueued} /></I18nProvider>)
  await screen.findByText('client@example.test')
  fireEvent.click(screen.getByRole('button', { name: 'Enviar por correo' }))
  await screen.findByText('En cola')
  expect(api).toHaveBeenCalledWith('/api/v1/quotes/quote-1/email', { method: 'POST' })
  expect(screen.getByRole('button', { name: 'Enviar por correo' })).toBeDisabled()
  expect(onQueued).toHaveBeenCalledOnce()
})
it('requires explicit reconciliation before retrying an uncertain delivery', async () => {
  api.mockResolvedValue({ status: 'UNKNOWN', recipient: 'client@example.test', connectionEnabled: true })
  render(<I18nProvider><DocumentEmail id="invoice-1" /></I18nProvider>)
  const retry = await screen.findByRole('button', { name: 'Reintentar envío' })
  expect(retry).toBeDisabled()
  fireEvent.click(screen.getByRole('checkbox'))
  fireEvent.click(retry)
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/v1/documents/invoice-1/email/retry', { method: 'POST', body: JSON.stringify({ confirmedNotDelivered: true }) }))
})
