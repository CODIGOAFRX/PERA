import { createContext, useCallback, useContext, useEffect, useMemo, useState, type AnchorHTMLAttributes, type MouseEvent, type ReactNode } from 'react'
import { APP_BASE } from '../lib/demo'

interface RouterValue {
  path: string
  search: string
  navigate: (to: string, replace?: boolean) => void
}

const RouterContext = createContext<RouterValue | null>(null)

// Las rutas de la aplicación son siempre '/clientes', '/ventas'...; si se sirve bajo un prefijo
// (la demo del portfolio), el prefijo solo existe en la barra de direcciones.
function stripBase(pathname: string) {
  if (!APP_BASE) return pathname
  if (pathname === APP_BASE) return '/'
  return pathname.startsWith(`${APP_BASE}/`) ? pathname.slice(APP_BASE.length) : pathname
}

export function browserPath(to: string) {
  return APP_BASE && to.startsWith('/') ? `${APP_BASE}${to === '/' ? '/' : to}` : to
}

function currentLocation() {
  return {
    path: stripBase(window.location.pathname).replace(/\/$/, '') || '/',
    search: window.location.search,
  }
}

export function RouterProvider({ children }: { children: ReactNode }) {
  const [location, setLocation] = useState(currentLocation)
  useEffect(() => {
    const onPopState = () => setLocation(currentLocation())
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const navigate = useCallback((to: string, replace = false) => {
    if (replace) window.history.replaceState(null, '', browserPath(to))
    else window.history.pushState(null, '', browserPath(to))
    setLocation(currentLocation())
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [])

  const value = useMemo(() => ({ ...location, navigate }), [location, navigate])
  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>
}

export interface PathMatch {
  params: Record<string, string>
}

export function matchPath(pattern: string, path: string): PathMatch | null {
  const normalize = (value: string) => value.replace(/\/$/, '') || '/'
  const patternParts = normalize(pattern).split('/').filter(Boolean)
  const pathParts = normalize(path).split('/').filter(Boolean)
  if (patternParts.length !== pathParts.length) return null

  const params: Record<string, string> = {}
  for (let index = 0; index < patternParts.length; index += 1) {
    const expected = patternParts[index]
    const actual = pathParts[index]
    if (expected.startsWith(':')) params[expected.slice(1)] = decodeURIComponent(actual)
    else if (expected !== actual) return null
  }
  return { params }
}

export function useRouter() {
  const context = useContext(RouterContext)
  if (!context) throw new Error('useRouter debe utilizarse dentro de RouterProvider')
  return context
}

interface LinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href'> {
  to: string
  replace?: boolean
}

export function Link({ to, replace, onClick, children, ...props }: LinkProps) {
  const { navigate } = useRouter()
  const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
    onClick?.(event)
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || props.target) return
    event.preventDefault()
    navigate(to, replace)
  }
  return <a href={browserPath(to)} onClick={handleClick} {...props}>{children}</a>
}
