import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { reportError } from '../lib/errorReport'
import { ErrorBoundary, ErrorNotice } from './ErrorReporting'

const fetchMock = vi.fn()
beforeEach(() => {
  sessionStorage.setItem('pera.auth.token', 'token')
  sessionStorage.setItem('pera.auth.company', JSON.stringify({ id: 'c', code: 'DEMO', name: 'Empresa demo' }))
  fetchMock.mockReset().mockResolvedValue(new Response(JSON.stringify({ id: 'r', emailed: true, message: 'Se ha avisado a soporte.' }), { status: 201 }))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => { vi.unstubAllGlobals(); sessionStorage.clear() })

it('shows the error window with its code, lets the user say what they were doing and sends it to support', async () => {
  fetchMock.mockImplementation((url: string) => Promise.resolve(new Response(JSON.stringify(url.endsWith('/send')
    ? { id: 'r1', emailed: true, message: 'Enviado a soporte desde facturas@empresa.es.' }
    : { id: 'r1', emailed: false, message: 'Guardado.' }), { status: url.endsWith('/send') ? 200 : 201 })))
  render(<I18nProvider><ErrorNotice /></I18nProvider>)
  void reportError('SERVIDOR', 'No se pudo completar la operación.', 'Petición: /api/v1/x', 'ABC-123')
  expect(await screen.findByRole('dialog', { name: 'Error en la aplicación' })).toBeInTheDocument()
  expect(screen.getByText('ABC-123')).toBeInTheDocument()
  const saved = JSON.parse(String(fetchMock.mock.calls[0][1].body))
  expect(saved).toMatchObject({ source: 'SERVIDOR', errorId: 'ABC-123', companyName: 'Empresa demo', appVersion: __APP_VERSION__ })
  fireEvent.change(await screen.findByLabelText(/Qué estabas haciendo/), { target: { value: 'Guardaba una factura' } })
  fireEvent.click(screen.getByRole('button', { name: 'Enviar a soporte' }))
  expect(await screen.findByText('Enviado a soporte desde facturas@empresa.es.')).toBeInTheDocument()
  expect(fetchMock.mock.calls[1][0]).toContain('/api/v1/error-reports/r1/send')
  expect(JSON.parse(String(fetchMock.mock.calls[1][1].body))).toEqual({ comment: 'Guardaba una factura' })
})

it('saves every error, even when it repeats', async () => {
  void reportError('PANTALLA', 'Fallo repetido')
  await reportError('PANTALLA', 'Fallo repetido')
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

function Broken(): never { throw new Error('La pantalla se ha roto') }

it('replaces a broken screen with an error page instead of a blank one', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => undefined)
  render(<ErrorBoundary><Broken /></ErrorBoundary>)
  expect(await screen.findByText('Error en la aplicación')).toBeInTheDocument()
  expect(screen.getByText('La pantalla se ha roto')).toBeInTheDocument()
})
