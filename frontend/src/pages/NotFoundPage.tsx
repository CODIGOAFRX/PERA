import { ArrowLeft, MapPinOff } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { useTranslation } from '../i18n/I18nProvider'
import { Link } from '../routing/Router'

export function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <div className="page-stack">
      <PageHeader eyebrow={t('notFound.eyebrow')} title={t('notFound.title')} description={t('notFound.description')} icon={MapPinOff} />
      <section className="panel not-found-panel">
        <Link className="button button-primary" to="/"><ArrowLeft size={17} />{t('notFound.back')}</Link>
      </section>
    </div>
  )
}
