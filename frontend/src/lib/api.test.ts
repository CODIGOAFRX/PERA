import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, errorMessage, storeToken } from './api'
import { LANGUAGE_STORAGE_KEY } from '../i18n/language'

describe('apiFetch', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('sends the session token and parses JSON responses', async () => {
    storeToken('signed-token')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))

    await expect(apiFetch<{ ok: boolean }>('/api/test')).resolves.toEqual({ ok: true })
    const headers = fetchMock.mock.calls[0][1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer signed-token')
    expect(headers.get('Accept-Language')).toBe('es-ES')
  })

  it('uses the persisted language and preserves the browser boundary for form data', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))

    const body = new FormData()
    body.set('file', new Blob(['logo'], { type: 'text/plain' }), 'logo.txt')
    await apiFetch('/api/test', { method: 'POST', body })

    const headers = fetchMock.mock.calls[0][1]?.headers as Headers
    expect(headers.get('Accept-Language')).toBe('en-GB')
    expect(headers.has('Content-Type')).toBe(false)
  })

  it('preserves problem details and validation messages', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      title: 'Petición no válida', detail: 'Revisa los datos', violations: { code: 'es obligatorio' },
    }), { status: 400, headers: { 'Content-Type': 'application/problem+json' } }))

    const promise = apiFetch('/api/test')
    await expect(promise).rejects.toBeInstanceOf(ApiError)
    try { await promise } catch (error) { expect(errorMessage(error)).toBe('es obligatorio') }
  })
})
