import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { RouterProvider } from '../routing/Router'
import { AuthProvider, useAuth } from './AuthContext'

function token(payload: Record<string, unknown>) {
  const encode = (value: Record<string, unknown>) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `${encode({ alg: 'none' })}.${encode(payload)}.`
}

function LogoutButton() {
  const { logout, authenticated } = useAuth()
  return <button type="button" onClick={logout}>{authenticated ? 'Salir' : 'Fuera'}</button>
}

it('goes back to the start page on logout so the next user does not land on the previous one (TPV)', () => {
  sessionStorage.setItem('pera.auth.token', token({ sub: 'u1', username: 'admin', exp: Math.floor(Date.now() / 1000) + 600, roles: ['ADMIN'] }))
  window.history.replaceState(null, '', '/tpv')
  render(<RouterProvider><AuthProvider><LogoutButton /></AuthProvider></RouterProvider>)
  fireEvent.click(screen.getByRole('button', { name: 'Salir' }))
  expect(screen.getByRole('button', { name: 'Fuera' })).toBeInTheDocument()
  expect(window.location.pathname).toBe('/')
  expect(sessionStorage.getItem('pera.auth.token')).toBeNull()
})
