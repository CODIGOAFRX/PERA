import type { ReactNode } from 'react'

export function Field({ label, htmlFor, required, hint, children, wide = false }: { label: string; htmlFor: string; required?: boolean; hint?: string; children: ReactNode; wide?: boolean }) {
  return <div className={`form-field ${wide ? 'field-wide' : ''}`}><label htmlFor={htmlFor}>{label}{required && <span aria-hidden="true"> *</span>}</label>{children}{hint && <small>{hint}</small>}</div>
}

export function FormActions({ onCancel, saving, submitLabel = 'Guardar' }: { onCancel: () => void; saving: boolean; submitLabel?: string }) {
  return <div className="form-actions"><button type="button" className="button button-ghost" onClick={onCancel} disabled={saving}>Cancelar</button><button type="submit" className="button button-primary" disabled={saving}>{saving ? 'Guardando…' : submitLabel}</button></div>
}
