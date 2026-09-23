import { act, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { beforeEach, expect, it, vi } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { LoadingState } from './DataState'
import { Field } from './Form'
import { Modal } from './Modal'
import { Pagination } from './Pagination'
import { ToastProvider, useToast } from './Toast'

beforeEach(() => localStorage.clear())

function ModalHarness() {
  const [open, setOpen] = useState(false)
  return <I18nProvider>
    <button type="button" onClick={() => setOpen(true)}>Abrir</button>
    <Modal open={open} title="Editar cliente" description="Datos fiscales" onClose={() => setOpen(false)}>
      <form><input aria-label="Nombre" /><button type="submit">Guardar</button></form>
    </Modal>
  </I18nProvider>
}

it('labels the dialog, moves focus inside, traps Tab and restores focus on Escape', () => {
  render(<ModalHarness />)
  const opener = screen.getByRole('button', { name: 'Abrir' })
  opener.focus()
  fireEvent.click(opener)

  const dialog = screen.getByRole('dialog', { name: 'Editar cliente' })
  expect(dialog).toHaveAttribute('aria-modal', 'true')
  expect(dialog).toHaveAccessibleDescription('Datos fiscales')
  expect(screen.getByLabelText('Nombre')).toHaveFocus()

  screen.getByRole('button', { name: 'Guardar' }).focus()
  fireEvent.keyDown(dialog, { key: 'Tab' })
  expect(screen.getByRole('button', { name: 'Cerrar' })).toHaveFocus()
  fireEvent.keyDown(dialog, { key: 'Tab', shiftKey: true })
  expect(screen.getByRole('button', { name: 'Guardar' })).toHaveFocus()

  fireEvent.keyDown(dialog, { key: 'Escape' })
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(opener).toHaveFocus()
})

it('links hints, errors and required state to the field control', () => {
  render(<Field label="NIF" htmlFor="tax-id" required hint="Sin espacios" error="NIF no válido"><input id="tax-id" /></Field>)
  const input = screen.getByLabelText(/NIF/)
  expect(input).toHaveAttribute('aria-invalid', 'true')
  expect(input).toHaveAttribute('aria-required', 'true')
  expect(input).toHaveAccessibleDescription('Sin espacios NIF no válido')
  expect(screen.getByRole('alert')).toHaveTextContent('NIF no válido')
})

it('announces loading state and names the pagination landmark', () => {
  render(<I18nProvider><LoadingState /><Pagination page={0} totalPages={3} totalElements={60} onChange={vi.fn()} /></I18nProvider>)
  expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
  expect(screen.getByRole('navigation', { name: 'Paginación' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Página anterior' })).toBeDisabled()
})

function Notify() {
  const { notify } = useToast()
  return <><button type="button" onClick={() => notify('Guardado')}>ok</button><button type="button" onClick={() => notify('Fallo al guardar', 'error')}>ko</button></>
}

it('announces errors as alerts and keeps them longer than confirmations', () => {
  vi.useFakeTimers()
  try {
    render(<I18nProvider><ToastProvider><Notify /></ToastProvider></I18nProvider>)
    fireEvent.click(screen.getByRole('button', { name: 'ok' }))
    fireEvent.click(screen.getByRole('button', { name: 'ko' }))
    expect(screen.getByRole('status')).toHaveTextContent('Guardado')
    expect(screen.getByRole('alert')).toHaveTextContent('Fallo al guardar')
    act(() => { vi.advanceTimersByTime(5000) })
    expect(screen.queryByText('Guardado')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toBeInTheDocument()
  } finally {
    vi.useRealTimers()
  }
})
