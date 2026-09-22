import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { ConnectionsPage } from './ConnectionsPage'
import { I18nProvider } from '../i18n/I18nProvider'
const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', () => ({ apiFetch: api, errorMessage: (e: Error) => e.message }))
beforeEach(() => { api.mockReset(); localStorage.clear() })
it('requires saving changed credentials before testing or enabling sends', async () => {
  api.mockResolvedValue({ configured: true, encryptionReady: true, host: 'smtp.example.test', port: 587, username: 'user', senderName: 'Demo', senderEmail: 'sender@example.test', security: 'STARTTLS', enabled: false, autoInvoices: false, verifiedAt: '2026-09-22', passwordStored: true })
  render(<I18nProvider><ConnectionsPage /></I18nProvider>)
  const host = await screen.findByLabelText(/Servidor SMTP/)
  expect(screen.getByLabelText('Activar envíos desde esta empresa')).toBeEnabled()
  fireEvent.change(host, { target: { value: 'smtp.new.test' } })
  expect(screen.getByLabelText('Activar envíos desde esta empresa')).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Comprobar conexión' })).toBeDisabled()
  expect(screen.getByLabelText(/Contraseña SMTP/)).toHaveValue('')
})

const saved = { configured: true, encryptionReady: true, host: 'smtp.example.test', port: 587, username: 'old@example.test', senderName: 'Demo', senderEmail: 'old@example.test', security: 'STARTTLS', enabled: false, autoInvoices: false, verifiedAt: '2026-09-22', passwordStored: true }
it('configures Gmail and normalizes its app password without sending mail', async () => {
  api.mockResolvedValue(saved)
  render(<I18nProvider><ConnectionsPage /></I18nProvider>)
  fireEvent.change(await screen.findByLabelText('¿Qué correo utilizas?'), { target: { value: 'gmail' } })
  expect(screen.queryByLabelText(/Servidor SMTP/)).not.toBeInTheDocument()
  expect(api).toHaveBeenCalledTimes(1)
  fireEvent.change(screen.getByLabelText(/Tu dirección de Gmail/), { target: { value: 'demo@gmail.com' } })
  fireEvent.change(screen.getByLabelText(/Contraseña de aplicación de Google/), { target: { value: 'abcd efgh ijkl mnop' } })
  fireEvent.click(screen.getByRole('button', { name: 'Guardar configuración' }))
  await screen.findByText('Configuración guardada.')
  expect(api).toHaveBeenCalledTimes(2)
  const [url, options] = api.mock.calls[1]
  expect(url).toBe('/api/v1/connections/email')
  expect(options.method).toBe('PUT')
  expect(JSON.parse(options.body)).toMatchObject({host: 'smtp.gmail.com', port: 587, security: 'STARTTLS', username: 'demo@gmail.com', senderEmail: 'demo@gmail.com', password: 'abcdefghijklmnop', enabled: false, verifiedAt: null})
})
it('rejects a regular password and preserves existing Gmail credentials when left blank', async () => {
  api.mockResolvedValue({...saved, host: 'smtp.gmail.com', port: 465, security: 'TLS'})
  render(<I18nProvider><ConnectionsPage /></I18nProvider>)
  expect(await screen.findByLabelText('¿Qué correo utilizas?')).toHaveValue('gmail')
  const password = screen.getByLabelText(/Contraseña de aplicación de Google/)
  expect(password).toHaveValue('')
  fireEvent.change(password, {target: {value: 'too-short'}})
  fireEvent.click(screen.getByRole('button', {name: 'Guardar configuración'}))
  expect(await screen.findByRole('alert')).toHaveTextContent('16 caracteres')
  expect(api).toHaveBeenCalledTimes(1)
  fireEvent.change(password, {target: {value: ''}})
  fireEvent.click(screen.getByRole('button', {name: 'Guardar configuración'}))
  await screen.findByText('Configuración guardada.')
  expect(JSON.parse(api.mock.calls[1][1].body)).toMatchObject({password: null, port: 465, security: 'TLS'})
})
