import { Field } from './Form'
import { useTranslation } from '../i18n/I18nProvider'
import type { ContactDetails } from '../types/api'

export function ContactDetailsFields({ value, onChange, prefix }: { value: ContactDetails; onChange: (value: ContactDetails) => void; prefix: string }) {
  const { language } = useTranslation()
  const fields: [keyof ContactDetails, string, string, number][] = [
    ['addressLine1', 'Dirección', 'Street address', 240], ['city', 'Población', 'City / town', 120],
    ['region', 'Provincia', 'Province / region', 120], ['postalCode', 'Código postal', 'Postal code', 20],
    ['countryCode', 'País (ISO: ES, PT…)', 'Country (ISO: ES, PT…)', 2],
    ['iban', 'Cuenta bancaria · IBAN (opcional)', 'Bank account · IBAN (optional)', 42],
    ['bankAccountHolder', 'Titular de la cuenta (opcional)', 'Account holder (optional)', 180],
  ]
  return <>{fields.map(([key, es, en, maxLength]) => <Field key={key} label={language === 'es' ? es : en} htmlFor={`${prefix}-${key}`} name={`details.${key}`}>
    <input id={`${prefix}-${key}`} value={value[key] ?? ''} maxLength={maxLength} spellCheck={false}
      onChange={event => onChange({ ...value, [key]: key === 'iban' || key === 'countryCode' ? event.target.value.toUpperCase() : event.target.value })} />
  </Field>)}</>
}
