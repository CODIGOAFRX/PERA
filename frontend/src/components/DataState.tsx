import { Inbox, LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'

export function LoadingState({ label }: { label?: string }) {
  const { t } = useTranslation()
  return <div className="data-state" role="status" aria-live="polite" aria-busy="true"><LoaderCircle className="spin" size={22} aria-hidden="true" /><span>{label ?? t('common.loading')}</span></div>
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-icon" aria-hidden="true"><Inbox size={22} /></span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  )
}
