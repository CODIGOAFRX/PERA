import { createContext, useCallback, useContext, useEffect, useMemo, useState, type AnchorHTMLAttributes, type MouseEvent, type ReactNode } from 'react'

interface RouterValue {
  path: string
  navigate: (to: string, replace?: boolean) => void
}

const RouterContext = createContext<RouterValue | null>(null)

function currentPath() {
  return window.location.pathname.replace(/\/$/, '') || '/'
}

export function RouterProvider({ children }: { children: ReactNode }) {
  const [path, setPath] = useState(currentPath)
  useEffect(() => {
    const onPopState = () => setPath(currentPath())
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const navigate = useCallback((to: string, replace = false) => {
    if (replace) window.history.replaceState(null, '', to)
    else window.history.pushState(null, '', to)
    setPath(currentPath())
    window.scrollTo({ top: 0, behavior: 'auto' })
  }, [])

  const value = useMemo(() => ({ path, navigate }), [path, navigate])
  return <RouterContext.Provider value={value}>{children}</RouterContext.Provider>
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
  return <a href={to} onClick={handleClick} {...props}>{children}</a>
}
