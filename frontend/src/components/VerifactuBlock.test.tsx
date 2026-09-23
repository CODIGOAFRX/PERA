import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { VerifactuBlock } from './VerifactuBlock'
import { I18nProvider } from '../i18n/I18nProvider'
const { api } = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', () => ({ apiFetch: api, apiFetchText: vi.fn(), errorMessage: (e: Error) => e.message }))
beforeEach(() => { api.mockReset(); localStorage.clear() })
it('reports a failed load instead of hiding the fiscal record, and retries on demand', async () => {
  api.mockRejectedValueOnce(new Error('Servicio no disponible')).mockResolvedValueOnce([])
  render(<I18nProvider><VerifactuBlock documentId="doc1" /></I18nProvider>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Servicio no disponible')
  fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  await vi.waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  expect(api).toHaveBeenCalledTimes(2)
})
