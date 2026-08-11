import { ArrowLeft, ShieldX } from 'lucide-react'
import { useTranslation } from '../i18n/I18nProvider'
import { Link } from '../routing/Router'

export function AccessDeniedPage() {
  const { t } = useTranslation()
  return (
    <section className="not-found-page">
      <span className="empty-icon"><ShieldX size={22} /></span>
      <span className="eyebrow">{t('accessDenied.eyebrow')}</span>
      <h1>{t('accessDenied.title')}</h1>
      <p>{t('accessDenied.description')}</p>
      <Link className="button button-secondary" to="/"><ArrowLeft size={17} />{t('accessDenied.back')}</Link>
    </section>
  )
}
