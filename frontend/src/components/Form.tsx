import type { ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'

export function Field({ label, htmlFor, required, hint, children, wide = false }: { label: string; htmlFor: string; required?: boolean; hint?: string; children: ReactNode; wide?: boolean }) {
  return <div className={`form-field ${wide ? 'field-wide' : ''}`}><label htmlFor={htmlFor}>{label}{required && <span aria-hidden="true"> *</span>}</label>{children}{hint && <small>{hint}</small>}</div>
}

export function FormActions({ onCancel, saving, submitLabel }: { onCancel: () => void; saving: boolean; submitLabel?: string }) {
  const { t } = useTranslation()
  return <div className="form-actions"><button type="button" className="button button-ghost" onClick={onCancel} disabled={saving}>{t('common.cancel')}</button><button type="submit" className="button button-primary" disabled={saving}>{saving ? t('common.saving') : submitLabel || t('common.save')}</button></div>
}
