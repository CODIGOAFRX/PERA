import { cloneElement, createContext, isValidElement, useCallback, useContext, useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactElement, type ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { ApiError, errorMessage } from '../lib/api'

type FieldControlProps = { 'aria-describedby'?: string; 'aria-invalid'?: boolean; 'aria-required'?: boolean; onChange?: (event: ChangeEvent<HTMLElement>) => void }

interface FormErrorsContextValue {
  errors: Record<string, string>
  register: (name: string, controlId: string) => () => void
  clear: (name: string) => void
}

const FormErrorsContext = createContext<FormErrorsContextValue | null>(null)

/**
 * Server-side validation errors of one form. Fields opt in with `name` (the request property, e.g.
 * `legalName` or `details.iban`); violations without a matching field stay in the form-level message
 * so that no error is hidden.
 */
export function useFormErrors() {
  const { t } = useTranslation()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const registry = useRef(new Map<string, string>())

  const register = useCallback((name: string, controlId: string) => {
    registry.current.set(name, controlId)
    return () => { registry.current.delete(name) }
  }, [])
  const clear = useCallback((name: string) => setErrors((current) => {
    if (!(name in current)) return current
    const next = { ...current }
    delete next[name]
    return next
  }), [])
  const reset = useCallback(() => setErrors({}), [])

  /** Stores field violations and returns the message for the form-level error. */
  const capture = useCallback((cause: unknown) => {
    const violations = cause instanceof ApiError ? cause.problem?.violations : undefined
    if (!violations || Object.keys(violations).length === 0) { setErrors({}); return errorMessage(cause) }
    setErrors(violations)
    const unmatched = Object.entries(violations).filter(([name]) => !registry.current.has(name)).map(([, message]) => message)
    return unmatched.length === Object.keys(violations).length ? unmatched.join(' · ') : [t('common.reviewFields'), ...unmatched].join(' · ')
  }, [t])

  // Move keyboard and screen-reader focus to the first field the server rejected.
  useEffect(() => {
    const first = Object.keys(errors).find((name) => registry.current.has(name))
    if (first) document.getElementById(registry.current.get(first)!)?.focus()
  }, [errors])

  const context = useMemo<FormErrorsContextValue>(() => ({ errors, register, clear }), [errors, register, clear])
  return { errors, capture, reset, context }
}

export function FormErrors({ value, children }: { value: FormErrorsContextValue; children: ReactNode }) {
  return <FormErrorsContext.Provider value={value}>{children}</FormErrorsContext.Provider>
}

/** Links an intrinsic control to its hint, error and required state, and clears a server error on edit. */
function useBoundControl(children: ReactNode, htmlFor: string, name: string | undefined, error: string | undefined, hint: string | undefined, required: boolean | undefined) {
  const formErrors = useContext(FormErrorsContext)
  const register = formErrors?.register
  useEffect(() => (name && register ? register(name, htmlFor) : undefined), [name, htmlFor, register])
  const shownError = error ?? (name ? formErrors?.errors[name] : undefined)
  const hintId = hint ? `${htmlFor}-hint` : undefined
  const errorId = shownError ? `${htmlFor}-error` : undefined
  let control = children
  if (isValidElement<FieldControlProps>(children) && typeof children.type === 'string') {
    const element = children as ReactElement<FieldControlProps>
    const describedBy = [element.props['aria-describedby'], hintId, errorId].filter(Boolean).join(' ') || undefined
    const onChange = element.props.onChange
    control = cloneElement(element, {
      'aria-describedby': describedBy,
      'aria-invalid': shownError ? true : element.props['aria-invalid'],
      'aria-required': required || element.props['aria-required'] || undefined,
      // A server error no longer applies once the user edits the value.
      ...(name && formErrors && onChange ? { onChange: (event: ChangeEvent<HTMLElement>) => { formErrors.clear(name); onChange(event) } } : {}),
    })
  }
  return { control, shownError, errorId, hintId }
}

export function Field({ label, htmlFor, name, required, hint, error, children, wide = false }: { label: string; htmlFor: string; name?: string; required?: boolean; hint?: string; error?: string; children: ReactNode; wide?: boolean }) {
  const { control, shownError, errorId, hintId } = useBoundControl(children, htmlFor, name, error, hint, required)
  return <div className={`form-field ${wide ? 'field-wide' : ''} ${shownError ? 'field-invalid' : ''}`}>
    <label htmlFor={htmlFor}>{label}{required && <span aria-hidden="true"> *</span>}</label>
    {control}
    {hint && <small id={hintId}>{hint}</small>}
    {shownError && <small id={errorId} className="field-error" role="alert">{shownError}</small>}
  </div>
}

/** Same error binding as Field for controls that keep their own label and layout, such as document lines. */
export function FieldControl({ htmlFor, name, children }: { htmlFor: string; name: string; children: ReactNode }) {
  const { control, shownError, errorId } = useBoundControl(children, htmlFor, name, undefined, undefined, undefined)
  return <>{control}{shownError && <small id={errorId} className="field-error" role="alert">{shownError}</small>}</>
}

export function FormActions({ onCancel, saving, submitLabel }: { onCancel: () => void; saving: boolean; submitLabel?: string }) {
  const { t } = useTranslation()
  return <div className="form-actions"><button type="button" className="button button-ghost" onClick={onCancel} disabled={saving}>{t('common.cancel')}</button><button type="submit" className="button button-primary" disabled={saving} aria-busy={saving || undefined}>{saving ? t('common.saving') : submitLabel || t('common.save')}</button></div>
}
