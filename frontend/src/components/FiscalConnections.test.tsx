import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { FiscalConnections } from './FiscalConnections'
import { I18nProvider } from '../i18n/I18nProvider'
const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', () => ({ apiFetch: api, errorMessage: (e: Error) => e.message }))
const stored = (provider: string) => ({ provider, configured: true, encryptionReady: true, account: provider === 'B2B' ? '1234' : '', enabled: false, environment: 'TEST' })
beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string) => Promise.resolve(stored(path.endsWith('B2B') ? 'B2B' : 'AEAT')))
})
it('asks for the password before uploading a new certificate', async () => {
  render(<I18nProvider><FiscalConnections /></I18nProvider>)
  const file = await screen.findByLabelText(/Certificado P12\/PFX/)
  fireEvent.change(file, { target: { files: [new File(['p12'], 'empresa.p12')] } })
  const save = screen.getAllByRole('button', { name: 'Guardar conexión' })[0]
  await waitFor(() => expect(save).toBeEnabled())
  fireEvent.click(save)
  expect(await screen.findByRole('alert')).toHaveTextContent('contraseña del certificado')
  expect(api).toHaveBeenCalledTimes(2)
})
it('only checks the stored configuration once pending changes are saved', async () => {
  render(<I18nProvider><FiscalConnections /></I18nProvider>)
  const check = await screen.findByRole('button', { name: 'Comprobar cuenta' })
  expect(check).toBeEnabled()
  fireEvent.change(screen.getByLabelText(/Identificador de la cuenta/), { target: { value: '999' } })
  expect(check).toBeDisabled()
})
it('shows the stored certificate and warns when it expires soon or belongs to another tax ID', async () => {
  const soon = new Date(Date.now() + 10 * 86_400_000).toISOString().slice(0, 10)
  api.mockImplementation((path: string) => Promise.resolve(path.endsWith('B2B') ? stored('B2B') : {
    ...stored('AEAT'),
    certificate: { holder: 'PRUEBA DEMO - 99999999R', personalTaxId: '99999999R', entityTaxId: null, issuer: 'AC FNMT Usuarios', validUntil: soon, expired: false },
    issuerMatches: false,
  }))
  render(<I18nProvider><FiscalConnections /></I18nProvider>)
  expect(await screen.findByText('PRUEBA DEMO - 99999999R')).toBeInTheDocument()
  expect(screen.getByText('AC FNMT Usuarios')).toBeInTheDocument()
  const alerts = screen.getAllByRole('alert').map((element) => element.textContent)
  expect(alerts.some((text) => text?.includes('caduca en'))).toBe(true)
  expect(alerts.some((text) => text?.includes('no coincide con el emisor'))).toBe(true)
})
