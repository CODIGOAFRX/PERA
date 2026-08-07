import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

const { loginMock } = vi.hoisted(() => ({ loginMock: vi.fn() }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ login: loginMock }) }))

describe('LoginPage', () => {
  beforeEach(() => loginMock.mockReset())

  it('submits the prepared local credentials', async () => {
    loginMock.mockResolvedValueOnce({ accessToken: 'token', companySelectionRequired: false, companies: [], expiresInSeconds: 3600, tokenType: 'Bearer' })
    render(<LoginPage />)

    fireEvent.click(screen.getByRole('button', { name: /entrar/i }))

    await waitFor(() => expect(loginMock).toHaveBeenCalledWith('admin', 'ChangeMe123!', undefined))
  })

  it('shows company choices returned by the backend', async () => {
    loginMock.mockResolvedValueOnce({ accessToken: null, companySelectionRequired: true, companies: [{ id: '1', code: 'DEMO', name: 'PERA Demo' }], expiresInSeconds: 0, tokenType: null })
    render(<LoginPage />)
    fireEvent.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByText('Elige una empresa')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /PERA Demo/i })).toBeInTheDocument()
  })
})
