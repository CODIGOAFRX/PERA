import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConfirmProvider } from '../components/ConfirmDialog'
import { ToastProvider } from '../components/Toast'
import { I18nProvider } from '../i18n/I18nProvider'
import { SettingsPage } from './SettingsPage'

const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', async (original) => ({ ...(await original<typeof import('../lib/api')>()), apiFetch: api, apiDownload: vi.fn() }))

const company = {
  id: 's1', companyId: 'c1', countryCode: 'ES', locale: 'es-ES', timezone: 'Europe/Madrid', baseCurrency: 'EUR',
  displayName: 'Pera ERP S.L', logoStorageKey: null, logoContentType: null, logoSha256: null, contactEmail: null,
  invoiceEmail: null, replyToEmail: null, phone: null, website: null, addressLine1: null, addressLine2: null,
  postalCode: null, city: null, region: null, legalName: 'PERA ERP Demo', taxId: 'B00000000', updatedAt: '2026-10-11T10:00:00Z', version: 1,
}
const verifactu = {
  configured: false, enabled: false, mode: 'VERIFACTU', environment: 'TEST', issuerTaxId: '', issuerLegalName: '',
  defaultRegimeKey: '01', defaultOperationQualification: 'S1', timeZone: 'Europe/Madrid', qrValidationUrl: '',
  softwareName: 'PERA ERP', softwareId: '01', softwareVersion: '0.1.0', developerTaxId: '',
}

beforeEach(() => {
  api.mockReset(); localStorage.clear()
  api.mockImplementation((path: string, init?: RequestInit) => {
    if (path === '/api/v1/company-settings/current') return Promise.resolve(init?.method ? { ...company, ...JSON.parse(String(init.body)) } : company)
    if (path === '/api/v1/verifactu-settings/current') return Promise.resolve(verifactu)
    return Promise.resolve([])
  })
})

const view = () => render(<I18nProvider><ToastProvider><ConfirmProvider><SettingsPage /></ConfirmProvider></ToastProvider></I18nProvider>)
const calls = (method: string, path: string) => api.mock.calls.filter(([url, init]) => url === path && init?.method === method)

it('lets the company legal name and tax ID be changed, as receipts and invoices print them', async () => {
  view()
  const legalName = await screen.findByLabelText(/^Razón social/)
  expect(legalName).toHaveValue('PERA ERP Demo')
  fireEvent.change(legalName, { target: { value: 'Cristalería Ejemplo S.L.' } })
  fireEvent.change(screen.getByLabelText(/^NIF/), { target: { value: 'b75777847' } })
  fireEvent.click(screen.getByRole('button', { name: /Guardar/ }))
  await waitFor(() => expect(calls('PUT', '/api/v1/company-settings/current')).toHaveLength(1))
  expect(JSON.parse(String(calls('PUT', '/api/v1/company-settings/current')[0][1]?.body))).toMatchObject({ legalName: 'Cristalería Ejemplo S.L.', taxId: 'B75777847' })
})

it('proposes the company as Veri*Factu issuer and warns when the tax IDs differ', async () => {
  view()
  fireEvent.click(await screen.findByRole('button', { name: 'Veri*Factu' }))
  const nif = await screen.findByLabelText(/^NIF del obligado/)
  expect(nif).toHaveValue('B00000000')
  expect(screen.getByLabelText(/^Razón social/)).toHaveValue('PERA ERP Demo')
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  fireEvent.change(nif, { target: { value: 'B75777847' } })
  expect(await screen.findByRole('alert')).toHaveTextContent('no coincide con el de la empresa')
})
