import { describe, expect, it } from 'vitest'
import { decodeAccessToken } from './AuthContext'

function token(payload: Record<string, unknown>) {
  // Como un JWT real: JSON en UTF-8 y después base64url.
  const encode = (value: Record<string, unknown>) => btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value)))).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `${encode({ alg: 'none' })}.${encode(payload)}.`
}

describe('decodeAccessToken', () => {
  it('restores roles and permissions used to adapt navigation', () => {
    const identity = decodeAccessToken(token({
      sub: 'user-1', username: 'economia', display_name: 'Equipo de economía', exp: Math.floor(Date.now() / 1000) + 600,
      roles: ['ECONOMY'], permissions: ['documents:read', 'finance:read'],
    }))

    expect(identity).toEqual({ id: 'user-1', username: 'economia', displayName: 'Equipo de economía', roles: ['ECONOMY'], permissions: ['documents:read', 'finance:read'] })
  })

  it('keeps accents and other UTF-8 characters of the display name', () => {
    const identity = decodeAccessToken(token({ sub: 'user-2', username: 'cajero1', display_name: 'Lucía Martín · Caja', exp: Math.floor(Date.now() / 1000) + 600 }))
    expect(identity?.displayName).toBe('Lucía Martín · Caja')
  })

  it('does not restore an expired or malformed session', () => {
    expect(decodeAccessToken(token({ sub: 'user-1', username: 'admin', exp: 1 }))).toBeNull()
    expect(decodeAccessToken('not-a-jwt')).toBeNull()
  })
})
