import { describe, expect, it } from 'vitest'
import { decodeAccessToken } from './AuthContext'

function token(payload: Record<string, unknown>) {
  const encode = (value: Record<string, unknown>) => btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
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

  it('does not restore an expired or malformed session', () => {
    expect(decodeAccessToken(token({ sub: 'user-1', username: 'admin', exp: 1 }))).toBeNull()
    expect(decodeAccessToken('not-a-jwt')).toBeNull()
  })
})
