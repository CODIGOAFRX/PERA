import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { Link, matchPath, RouterProvider, useRouter } from './Router'
import { appRoutes, matchAppRoute } from './routes'

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
    expect(paths).toEqual(['/', '/clientes', '/proveedores', '/catalogo', '/maestros', '/presupuestos', '/ventas', '/finanzas', '/operaciones', '/historial', '/configuracion'])
  })
})
