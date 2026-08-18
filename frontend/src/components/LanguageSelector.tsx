import { Languages } from 'lucide-react'
import { useTranslation } from '../i18n/I18nProvider'
import type { Language } from '../i18n/language'

export function LanguageSelector({ compact = false }: { compact?: boolean }) {
  const { language, setLanguage, t } = useTranslation()
  return (
    <label className={`language-selector ${compact ? 'language-selector-compact' : ''}`}>
      <Languages size={16} aria-hidden="true" />
      {!compact && <span>{t('language.label')}</span>}
      <select aria-label={t('language.label')} value={language} onChange={(event) => setLanguage(event.target.value as Language)}>
        <option value="es">{t('language.es')}</option>
        <option value="en">{t('language.en')}</option>
      </select>
    </label>
  )
}
