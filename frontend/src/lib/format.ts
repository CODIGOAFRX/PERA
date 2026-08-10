const currencyFormatters = new Map<string, Intl.NumberFormat>()
const numberFormatters = new Map<string, Intl.NumberFormat>()
const dateFormatters = new Map<string, Intl.DateTimeFormat>()
const dateTimeFormatters = new Map<string, Intl.DateTimeFormat>()

export function formatCurrency(
  value: number | string | null | undefined,
  currency = 'EUR',
  locale = 'es-ES',
) {
  const normalizedCurrency = /^[A-Za-z]{3}$/.test(currency) ? currency.toUpperCase() : 'EUR'
  const key = `${locale}:${normalizedCurrency}`
  let formatter = currencyFormatters.get(key)
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { style: 'currency', currency: normalizedCurrency })
    currencyFormatters.set(key, formatter)
  }
  return formatter.format(Number(value ?? 0))
}

export function formatDateTime(value: string | null | undefined, locale = 'es-ES') {
  if (!value) return '—'
  let formatter = dateTimeFormatters.get(locale)
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    })
    dateTimeFormatters.set(locale, formatter)
  }
  return formatter.format(new Date(value))
}

export function formatNumber(
  value: number | string | null | undefined,
  locale = 'es-ES',
  maximumFractionDigits = 2,
) {
  const key = `${locale}:${maximumFractionDigits}`
  let formatter = numberFormatters.get(key)
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { maximumFractionDigits })
    numberFormatters.set(key, formatter)
  }
  return formatter.format(Number(value ?? 0))
}

export function formatDate(value: string | null | undefined, locale = 'es-ES') {
  if (!value) return '—'
  let formatter = dateFormatters.get(locale)
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'short', year: 'numeric' })
    dateFormatters.set(locale, formatter)
  }
  return formatter.format(new Date(`${value}T00:00:00`))
}
