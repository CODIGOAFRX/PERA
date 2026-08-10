import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { translate, type TranslationKey, type TranslationParams } from './catalogs'
import { getStoredLanguage, languageTags, persistLanguage, type Language } from './language'

interface I18nContextValue {
  language: Language
  locale: string
  setLanguage: (language: Language) => void
  t: (key: TranslationKey, params?: TranslationParams) => string
}

const I18nContext = createContext<I18nContextValue | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<Language>(getStoredLanguage)

  const setLanguage = useCallback((nextLanguage: Language) => {
    persistLanguage(nextLanguage)
    setLanguageState(nextLanguage)
  }, [])

  useEffect(() => {
    document.documentElement.lang = language
    document.title = translate(language, 'app.title')
    document.querySelector<HTMLMetaElement>('meta[name="description"]')?.setAttribute('content', translate(language, 'app.description'))
  }, [language])

  const t = useCallback((key: TranslationKey, params?: TranslationParams) => (
    translate(language, key, params)
  ), [language])

  const value = useMemo(() => ({ language, locale: languageTags[language], setLanguage, t }), [language, setLanguage, t])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useTranslation() {
  const context = useContext(I18nContext)
  if (!context) throw new Error('useTranslation must be used inside I18nProvider')
  return context
}
