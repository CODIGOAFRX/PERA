export type Language = 'es' | 'en'

export const LANGUAGE_STORAGE_KEY = 'pera.language'

export const languageTags: Record<Language, string> = {
  es: 'es-ES',
  en: 'en-GB',
}

export function isLanguage(value: string | null | undefined): value is Language {
  return value === 'es' || value === 'en'
}

export function getStoredLanguage(): Language {
  const stored = globalThis.localStorage?.getItem(LANGUAGE_STORAGE_KEY)
  if (isLanguage(stored)) return stored
  return 'es'
}

export function persistLanguage(language: Language) {
  globalThis.localStorage?.setItem(LANGUAGE_STORAGE_KEY, language)
}

export function getStoredLocale() {
  return languageTags[getStoredLanguage()]
}
