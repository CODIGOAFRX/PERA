import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiFetch, clearStoredToken, getStoredToken, storeToken } from '../lib/api'
import type { CompanyOption, LoginResponse } from '../types/api'

const COMPANY_KEY = 'pera.auth.company'

interface AuthContextValue {
  authenticated: boolean
  company: CompanyOption | null
  login: (username: string, password: string, companyId?: string) => Promise<LoginResponse>
  logout: () => void
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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(Boolean(getStoredToken()))
  const [company, setCompany] = useState<CompanyOption | null>(readCompany)

  const logout = () => {
    clearStoredToken()
    sessionStorage.removeItem(COMPANY_KEY)
    setAuthenticated(false)
    setCompany(null)
  }

  useEffect(() => {
    window.addEventListener('pera:unauthorized', logout)
    return () => window.removeEventListener('pera:unauthorized', logout)
  }, [])

  const login = async (username: string, password: string, companyId?: string) => {
    const response = await apiFetch<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password, companyId: companyId || null }),
    })
    if (response.accessToken) {
      const selected = response.companies[0] ?? null
      storeToken(response.accessToken)
      if (selected) sessionStorage.setItem(COMPANY_KEY, JSON.stringify(selected))
      setCompany(selected)
      setAuthenticated(true)
    }
    return response
  }

  const value = useMemo(() => ({ authenticated, company, login, logout }), [authenticated, company])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe utilizarse dentro de AuthProvider')
  return context
}
