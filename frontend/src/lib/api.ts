import type { ProblemDetail } from '../types/api'

const TOKEN_KEY = 'pera.auth.token'

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export function getStoredToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function storeToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearStoredToken() {
  sessionStorage.removeItem(TOKEN_KEY)
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getStoredToken()
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(path, { ...init, headers })
  if (response.status === 204) return undefined as T
  if (!response.ok) {
    const problem = await readProblem(response)
    if (response.status === 401 && token) window.dispatchEvent(new Event('pera:unauthorized'))
    throw new ApiError(response.status, problem?.detail || problem?.title || `Error HTTP ${response.status}`, problem)
  }
  return response.json() as Promise<T>
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return undefined
  }
}

export function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    const violations = error.problem?.violations
    if (violations && Object.keys(violations).length > 0) return Object.values(violations).join(' · ')
    return error.message
  }
  if (error instanceof Error) return error.message
  return 'Ha ocurrido un error inesperado.'
}
