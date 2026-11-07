import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { RouterProvider } from '../routing/Router'
import { GlobalSearch } from './GlobalSearch'

const { api, roles } = vi.hoisted(() => ({ api: vi.fn(), roles: { list: ['ADMIN'] } }))
vi.mock('../lib/api', () => ({ apiFetch: api, errorMessage: (e: Error) => e.message }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ identity: { roles: roles.list, permissions: [] } }) }))

beforeEach(() => {
  api.mockReset(); localStorage.clear(); roles.list = ['ADMIN']
  window.history.replaceState(null, '', '/')
  api.mockImplementation((path: string) => Promise.resolve(path.startsWith('/api/v1/customers')
    ? { content: [{ id: 'c1', code: 'C0001', legalName: 'MERCADONA, S.A.', taxId: 'A46103834' }] }
    : { content: [] }))
})

const view = () => render(<I18nProvider><RouterProvider><GlobalSearch /></RouterProvider></I18nProvider>)

it('opens with Ctrl+K and finds screens, options and data, then goes to the chosen one', async () => {
  view()
  fireEvent.keyDown(window, { key: 'k', ctrlKey: true })
  const input = await screen.findByRole('textbox', { name: 'Buscar en PERA' })
  fireEvent.change(input, { target: { value: 'certificado' } })
  expect(await screen.findByText('Certificado y conexión con la AEAT')).toBeInTheDocument()
  fireEvent.change(input, { target: { value: 'merca' } })
  fireEvent.click(await screen.findByText('MERCADONA, S.A.'))
  expect(window.location.pathname).toBe('/clientes')
  expect(window.location.search).toBe('?q=C0001')
})

it('does not offer screens or data the role cannot open', async () => {
  roles.list = ['CASHIER']
  view()
  fireEvent.keyDown(window, { key: 'k', ctrlKey: true })
  fireEvent.change(await screen.findByRole('textbox', { name: 'Buscar en PERA' }), { target: { value: 'clientes' } })
  expect(await screen.findByText('No se ha encontrado nada.')).toBeInTheDocument()
  expect(api).not.toHaveBeenCalled()
})
