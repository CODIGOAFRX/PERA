import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { expect, it } from 'vitest'
import { I18nProvider } from '../i18n/I18nProvider'
import { ConfirmProvider, useConfirm } from './ConfirmDialog'

function Probe() {
  const confirm = useConfirm()
  const [result, setResult] = useState('none')
  return <button type="button" onClick={async () => setResult(String(await confirm({ message: '¿Borrar el registro?', confirmLabel: 'Borrar', danger: true })))}>Acción ({result})</button>
}

const view = () => render(<I18nProvider><ConfirmProvider><Probe /></ConfirmProvider></I18nProvider>)

it('resolves true when the user confirms and returns focus to the trigger', async () => {
  view()
  const trigger = screen.getByRole('button', { name: /Acción/ })
  trigger.focus()
  fireEvent.click(trigger)
  const dialog = await screen.findByRole('dialog')
  expect(dialog).toHaveTextContent('¿Borrar el registro?')
  expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus()
  fireEvent.click(screen.getByRole('button', { name: 'Borrar' }))
  await screen.findByRole('button', { name: 'Acción (true)' })
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  await waitFor(() => expect(screen.getByRole('button', { name: 'Acción (true)' })).toHaveFocus())
})

it('resolves false when the user cancels with Escape', async () => {
  view()
  fireEvent.click(screen.getByRole('button', { name: /Acción/ }))
  fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape' })
  await screen.findByRole('button', { name: 'Acción (false)' })
})
