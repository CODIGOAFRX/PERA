import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { Link, matchPath, RouterProvider, useRouter } from './Router'
import { appRoutes, isRouteAllowed, matchAppRoute } from './routes'

function Probe() {
  const { path, search } = useRouter()
  return <><span data-testid="path">{path}</span><span data-testid="search">{search}</span><Link to="/clientes?estado=activo">Clientes</Link></>
}

describe('RouterProvider', () => {
  beforeEach(() => window.history.replaceState(null, '', '/'))

  it('navigates internally without reloading the document', () => {
    render(<RouterProvider><Probe /></RouterProvider>)
    fireEvent.click(screen.getByRole('link', { name: 'Clientes' }))
    expect(screen.getByTestId('path')).toHaveTextContent('/clientes')
    expect(window.location.pathname).toBe('/clientes')
    expect(screen.getByTestId('search')).toHaveTextContent('?estado=activo')
  })

  it('matches parameterized paths for future detail pages', () => {
    expect(matchPath('/presupuestos/:id', '/presupuestos/abc-123')).toEqual({ params: { id: 'abc-123' } })
    expect(matchPath('/presupuestos/:id', '/presupuestos')).toBeNull()
  })

  it('does not silently map unknown application paths to the dashboard', () => {
    expect(matchAppRoute('/ruta-inexistente')).toBeNull()
  })

  it('keeps the current routes unique in the canonical registry', () => {
    const paths = appRoutes.map((route) => route.path)
    expect(new Set(paths).size).toBe(paths.length)
    expect(paths).toEqual(['/', '/impresion', '/clientes', '/proveedores', '/catalogo', '/maestros', '/presupuestos', '/ventas', '/compras', '/almacen', '/finanzas', '/comisiones', '/verifactu', '/cartera', '/contabilidad', '/operaciones', '/reclamaciones', '/agenda', '/historial', '/conexiones', '/configuracion', '/usuarios'])
  })

  it('keeps economic and logistics workspaces separated by role', () => {
    const connections = appRoutes.find((route) => route.id === 'connections')!
    expect(isRouteAllowed(connections, ['ADMIN'])).toBe(true)
    expect(isRouteAllowed(connections, ['ECONOMY'])).toBe(false)
    const finance = appRoutes.find((route) => route.id === 'finance')!
    const operations = appRoutes.find((route) => route.id === 'operations')!
    const accounting = appRoutes.find((route) => route.id === 'accounting')!
    expect(isRouteAllowed(finance, ['ECONOMY'])).toBe(true)
    expect(isRouteAllowed(finance, ['LOGISTICS'])).toBe(false)
    expect(isRouteAllowed(accounting, ['ECONOMY'])).toBe(true)
    expect(isRouteAllowed(accounting, ['LOGISTICS'])).toBe(false)
    const collections = appRoutes.find((route) => route.id === 'collections')!
    expect(isRouteAllowed(collections, ['ECONOMY'])).toBe(true)
    expect(isRouteAllowed(collections, ['LOGISTICS'])).toBe(false)
    expect(isRouteAllowed(operations, ['LOGISTICS'])).toBe(true)
    expect(isRouteAllowed(operations, ['ECONOMY'])).toBe(false)
    const claims = appRoutes.find((route) => route.id === 'claims')!
    expect(isRouteAllowed(claims, ['LOGISTICS'])).toBe(true)
    expect(isRouteAllowed(claims, ['CATALOG'])).toBe(false)
    expect(isRouteAllowed(appRoutes.find((route) => route.id === 'agenda')!, ['CATALOG'])).toBe(true)
    for (const id of ['purchases', 'inventory']) {
      const route = appRoutes.find((item) => item.id === id)!
      expect(isRouteAllowed(route, ['LOGISTICS'])).toBe(true)
      expect(isRouteAllowed(route, ['ECONOMY'])).toBe(false)
    }
  })
})
