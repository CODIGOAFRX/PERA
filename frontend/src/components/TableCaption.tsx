import { useTranslation } from '../i18n/I18nProvider'

/** Visually hidden table name, so screen-reader table navigation announces what each table lists. */
export function TableCaption({ es, en }: { es: string; en: string }) {
  const { language } = useTranslation()
  return <caption className="sr-only">{language === 'es' ? es : en}</caption>
}
