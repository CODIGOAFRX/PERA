import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiFetch, clearStoredToken, getStoredToken, storeToken } from '../lib/api'
import { DEMO_MODE } from '../lib/demo'
import type { CompanyOption, LoginResponse } from '../types/api'

const COMPANY_KEY = 'pera.auth.company'

export type UserRoleCode = 'OWNER' | 'ADMIN' | 'ECONOMY' | 'LOGISTICS' | 'CATALOG' | string

export interface AuthIdentity {
  id: string
  username: string
  displayName: string
  roles: UserRoleCode[]
  permissions: string[]
}

interface AuthContextValue {
  authenticated: boolean
  company: CompanyOption | null
  identity: AuthIdentity | null
  hasPermission: (permission: string) => boolean
  hasRole: (role: string) => boolean
  login: (username: string, password: string, companyId?: string) => Promise<LoginResponse>
  logout: () => void
  /** Demo pública: error al entrar como visitante (null mientras entra o si ya ha entrado). */
  demoError: string | null
  retryDemo: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readCompany(): CompanyOption | null {
  const value = sessionStorage.getItem(COMPANY_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as CompanyOption
  } catch {
    sessionStorage.removeItem(COMPANY_KEY)
    return null
  }
}

export function decodeAccessToken(token: string | null): AuthIdentity | null {
  if (!token) return null
  try {
    const encoded = token.split('.')[1]
    if (!encoded) return null
    const normalized = encoded.replace(/-/g, '+').replace(/_/g, '/')
    // atob devuelve bytes: hay que leerlos como UTF-8 o «Lucía» y «Administración» salen con caracteres rotos.
    const binary = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='))
    const payload = JSON.parse(new TextDecoder().decode(Uint8Array.from(binary, (char) => char.charCodeAt(0)))) as Record<string, unknown>
    if (typeof payload.exp === 'number' && payload.exp <= Date.now() / 1000) return null
    if (typeof payload.sub !== 'string' || typeof payload.username !== 'string') return null
    return {
      id: payload.sub,
      username: payload.username,
      displayName: typeof payload.display_name === 'string' ? payload.display_name : payload.username,
      roles: Array.isArray(payload.roles) ? payload.roles.filter((value): value is string => typeof value === 'string') : [],
      permissions: Array.isArray(payload.permissions) ? payload.permissions.filter((value): value is string => typeof value === 'string') : [],
    }
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [identity, setIdentity] = useState<AuthIdentity | null>(() => decodeAccessToken(getStoredToken()))
  const [company, setCompany] = useState<CompanyOption | null>(readCompany)
  const [demoError, setDemoError] = useState<string | null>(null)
  const [demoAttempt, setDemoAttempt] = useState(0)
  const authenticated = identity !== null

  const logout = () => {
    clearStoredToken()
    sessionStorage.removeItem(COMPANY_KEY)
    setIdentity(null)
    setCompany(null)
  }

  useEffect(() => {
    window.addEventListener('pera:unauthorized', logout)
    return () => window.removeEventListener('pera:unauthorized', logout)
  }, [])

  // En la demo no hay pantalla de acceso: sin sesión (al llegar o al caducar) se entra como visitante.
  useEffect(() => {
    if (!DEMO_MODE || identity) return
    let active = true
    setDemoError(null)
    apiFetch<LoginResponse>('/api/v1/auth/demo', { method: 'POST' })
      .then((response) => { if (active) startSession(response) })
      .catch((error: unknown) => { if (active) setDemoError(error instanceof Error ? error.message : String(error)) })
    return () => { active = false }
  }, [identity, demoAttempt])

  const startSession = (response: LoginResponse) => {
    if (!response.accessToken) return
    const nextIdentity = decodeAccessToken(response.accessToken)
    if (!nextIdentity) throw new Error('El servidor devolvió una sesión no válida.')
    const selected = response.companies[0] ?? null
    storeToken(response.accessToken)
    if (selected) sessionStorage.setItem(COMPANY_KEY, JSON.stringify(selected))
    setCompany(selected)
    setIdentity(nextIdentity)
  }

  const login = async (username: string, password: string, companyId?: string) => {
    const response = await apiFetch<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password, companyId: companyId || null }),
    })
    startSession(response)
    return response
  }

  const value = useMemo<AuthContextValue>(() => ({
    authenticated,
    company,
    identity,
    hasPermission: (permission) => identity?.permissions.includes(permission) ?? false,
    hasRole: (role) => identity?.roles.some((assigned) => assigned.toUpperCase() === role.toUpperCase()) ?? false,
    login,
    logout,
    demoError,
    retryDemo: () => setDemoAttempt((attempt) => attempt + 1),
  }), [authenticated, company, identity, demoError])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe utilizarse dentro de AuthProvider')
  return context
}
