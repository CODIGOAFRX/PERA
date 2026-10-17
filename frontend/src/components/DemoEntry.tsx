import { RotateCcw } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { useTranslation } from '../i18n/I18nProvider'
import { DEMO_EXIT_URL } from '../lib/demo'
import { LoadingState } from './DataState'
import { PearBrandMark } from './PearBrandMark'

/** Lo que ve el visitante de la demo pública mientras entra (sustituye a la pantalla de acceso). */
export function DemoEntry() {
  const { demoError, retryDemo } = useAuth()
  const { t } = useTranslation()
  return (
    <main className="demo-entry">
      <div className="brand"><PearBrandMark /><span className="brand-wordmark"><strong>PERA</strong><small>ERP</small></span></div>
      {demoError
        ? <div className="demo-entry-error" role="alert">
            <p>{t('demo.unavailable')}</p>
            <small>{demoError}</small>
            <div>
              <button type="button" className="button button-primary" onClick={retryDemo}><RotateCcw size={16} aria-hidden="true" />{t('demo.retry')}</button>
              <a className="button button-secondary" href={DEMO_EXIT_URL}>{t('demo.back')}</a>
            </div>
          </div>
        : <LoadingState label={t('demo.entering')} />}
    </main>
  )
}
