import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { Link, RouterProvider, useRouter } from './Router'

function Probe() {
  const { path } = useRouter()
  return <><span data-testid="path">{path}</span><Link to="/clientes">Clientes</Link></>
}

describe('RouterProvider', () => {
  beforeEach(() => window.history.replaceState(null, '', '/'))

  it('navigates internally without reloading the document', () => {
    render(<RouterProvider><Probe /></RouterProvider>)
    fireEvent.click(screen.getByRole('link', { name: 'Clientes' }))
    expect(screen.getByTestId('path')).toHaveTextContent('/clientes')
    expect(window.location.pathname).toBe('/clientes')
  })
})
