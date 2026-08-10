import { Inbox, LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'

export function LoadingState({ label }: { label?: string }) {
  const { t } = useTranslation()
  return <div className="data-state"><LoaderCircle className="spin" size={22} /><span>{label ?? t('common.loading')}</span></div>
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-icon"><Inbox size={22} /></span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  )
}
