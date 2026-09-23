import { act, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { expect, it } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { ApiError } from '../lib/api'
import { Field, FormErrors, useFormErrors } from './Form'

let fail: (cause: unknown) => void = () => undefined

function Probe() {
  const formErrors = useFormErrors()
  const [name, setName] = useState('')
  const [message, setMessage] = useState('')
  fail = (cause) => setMessage(formErrors.capture(cause))
  return <form><FormErrors value={formErrors.context}>
    <Field label="Razón social" htmlFor="probe-name" name="legalName"><input id="probe-name" value={name} onChange={(event) => setName(event.target.value)} /></Field>
    <Field label="IBAN" htmlFor="probe-iban" name="details.iban"><input id="probe-iban" /></Field>
  </FormErrors>{message && <div role="status">{message}</div>}</form>
}

const validation = (violations: Record<string, string>) => new ApiError(400, 'Petición no válida', { title: 'Petición no válida', status: 400, violations })

it('marks the rejected fields, focuses the first one and keeps unmatched violations visible', () => {
  render(<I18nProvider><Probe /></I18nProvider>)
  act(() => fail(validation({ legalName: 'no debe estar vacío', 'details.iban': 'IBAN no válido', code: 'código repetido' })))
  const name = screen.getByLabelText('Razón social')
  expect(name).toHaveAttribute('aria-invalid', 'true')
  expect(name).toHaveAccessibleDescription('no debe estar vacío')
  expect(name).toHaveFocus()
  expect(screen.getByLabelText('IBAN')).toHaveAccessibleDescription('IBAN no válido')
  expect(screen.getByRole('status')).toHaveTextContent('Revisa los campos marcados. · código repetido')
})

it('clears a field error as soon as the user edits it', () => {
  render(<I18nProvider><Probe /></I18nProvider>)
  act(() => fail(validation({ legalName: 'no debe estar vacío' })))
  const name = screen.getByLabelText('Razón social')
  fireEvent.change(name, { target: { value: 'PERA SL' } })
  expect(name).not.toHaveAttribute('aria-invalid')
  expect(screen.queryByText('no debe estar vacío')).not.toBeInTheDocument()
})

it('keeps the server message for non-validation errors', () => {
  render(<I18nProvider><Probe /></I18nProvider>)
  act(() => fail(new ApiError(409, 'El código ya existe')))
  expect(screen.getByRole('status')).toHaveTextContent('El código ya existe')
  expect(screen.getByLabelText('Razón social')).not.toHaveAttribute('aria-invalid')
})
