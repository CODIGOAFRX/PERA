import { Building2, Coins, FileKey2, Hash, Pencil, Plus, Settings, ShieldCheck, Trash2, Upload } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../components/Toast'
import { documentTypeKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import { apiDownload, apiFetch, errorMessage } from '../lib/api'
import { formatDate, formatDateTime } from '../lib/format'
import type { DocumentType, PageResponse } from '../types/api'

type SettingsTab = 'company' | 'numbering' | 'verifactu' | 'currencies' | 'licenses'
type ResetPeriod = 'YEARLY' | 'MONTHLY' | 'DAILY' | 'NEVER'

interface CompanySettingsValue {
  id: string
  companyId: string
  countryCode: string
  locale: string
  timezone: string
  baseCurrency: string
  displayName: string
  logoStorageKey: string | null
  logoContentType: string | null
  logoSha256: string | null
  contactEmail: string | null
  invoiceEmail: string | null
  replyToEmail: string | null
  phone: string | null
  website: string | null
  addressLine1: string | null
  addressLine2: string | null
  postalCode: string | null
  city: string | null
  region: string | null
  updatedAt: string
  version: number
}

interface NumberingScheme {
  id: string
  code: string
  name: string
  documentType: DocumentType
  series: string
  pattern: string
  resetPeriod: ResetPeriod
  initialValue: number
  active: boolean
  defaultScheme: boolean
}

interface CurrencyValue {
  id: string
  code: string
  name: string
  symbol: string
  decimalPlaces: number
  baseCurrency: boolean
  active: boolean
}

interface ExchangeRate {
  id: string
  baseCode: string
  quoteCode: string
  rate: number
  rateDate: string
  source: string
  active: boolean
}

interface CurrencyConversionResult {
  sourceAmount: number
  sourceCurrency: string
  targetAmount: number
  targetCurrency: string
  exchangeRate: number
  requestedDate: string
  rateDate: string
  rateSource: string
  inverseRate: boolean
}

interface LicenseSummary {
  id: string
  companyId: string
  displayName: string
  status: 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'REVOKED'
  validFrom: string
  validUntil: string
  graceUntil: string
  gracePeriodSeconds: number
  maxInstallations: number
  activeInstallations: number
  checkIntervalSeconds: number
  features: string[]
  firstActivatedAt: string | null
  createdAt: string
  updatedAt: string
}

interface LicensePage { content: LicenseSummary[]; page: number; size: number; totalElements: number; totalPages: number }

export function SettingsPage() {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [tab, setTab] = useState<SettingsTab>('company')
  const tabs: Array<[SettingsTab, typeof Building2, string]> = [
    ['company', Building2, c('Empresa', 'Company')],
    ['numbering', Hash, c('Numeraciones', 'Numbering')],
    ['verifactu', ShieldCheck, 'Veri*Factu'],
    ['currencies', Coins, c('Monedas', 'Currencies')],
    ['licenses', FileKey2, c('Licencias', 'Licences')],
  ]
  return <div className="page-stack">
    <PageHeader eyebrow={c('Administración', 'Administration')} title={c('Configuración', 'Settings')}
      description={c('Parámetros generales, numeraciones, Veri*Factu, divisas y licencias de la instalación.', 'General parameters, numbering, Veri*Factu, currencies and installation licences.')} icon={Settings} />
    <div className="workspace-tabs" role="tablist" aria-label={c('Secciones de configuración', 'Settings sections')}>
      {tabs.map(([id, Icon, label]) => <button key={id} type="button" className={tab === id ? 'active' : ''} onClick={() => setTab(id)}><Icon size={16} />{label}</button>)}
    </div>
    {tab === 'company' && <CompanyPanel />}
    {tab === 'numbering' && <NumberingPanel />}
    {tab === 'verifactu' && <VerifactuPanel />}
    {tab === 'currencies' && <CurrenciesPanel />}
    {tab === 'licenses' && <LicensesPanel />}
  </div>
}

function CompanyPanel() {
  const { language } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const [value, setValue] = useState<CompanySettingsValue | null>(null)
  const [form, setForm] = useState<CompanySettingsValue | null>(null)
  const [logoUrl, setLogoUrl] = useState('')
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const { notify } = useToast()
  const load = useCallback(async () => { setLoading(true); try { const response = await apiFetch<CompanySettingsValue>('/api/v1/company-settings/current'); setValue(response); setForm(response); setError('') } catch (cause) { setError(errorMessage(cause)) } finally { setLoading(false) } }, [])
  useEffect(() => { void load() }, [load])
  useEffect(() => {
    let active = true; let objectUrl = ''
    if (!value?.logoSha256) { setLogoUrl(''); return }
    apiDownload('/api/v1/company-settings/current/logo').then(({ blob }) => { if (active) { objectUrl = URL.createObjectURL(blob); setLogoUrl(objectUrl) } }).catch(() => setLogoUrl(''))
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [value?.logoSha256])
  const change = (key: keyof CompanySettingsValue, next: string) => setForm((current) => current ? { ...current, [key]: next } : current)
  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!form) return
    const { countryCode, locale, timezone, baseCurrency, displayName, logoStorageKey, logoContentType,
      logoSha256, contactEmail, invoiceEmail, replyToEmail, phone, website, addressLine1, addressLine2,
      postalCode, city, region } = form
    setSaving(true)
    setError('')
    try {
      const updated = await apiFetch<CompanySettingsValue>('/api/v1/company-settings/current', {
        method: 'PUT', body: JSON.stringify({ countryCode, locale, timezone, baseCurrency, displayName,
          logoStorageKey, logoContentType, logoSha256, contactEmail, invoiceEmail, replyToEmail, phone,
          website, addressLine1, addressLine2, postalCode, city, region }),
      })
      setValue(updated)
      setForm(updated)
      notify(c('Parámetros guardados.', 'Company settings saved.'))
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  const upload = async (file: File | undefined) => { if (!file) return; const data = new FormData(); data.append('file', file); try { const updated = await apiFetch<CompanySettingsValue>('/api/v1/company-settings/current/logo', { method: 'PUT', body: data }); setValue(updated); setForm(updated); notify(c('Logo actualizado.', 'Logo updated.')) } catch (cause) { notify(errorMessage(cause), 'error') } }
  const removeLogo = async () => { try { await apiFetch('/api/v1/company-settings/current/logo', { method: 'DELETE' }); await load(); notify(c('Logo eliminado.', 'Logo removed.')) } catch (cause) { notify(errorMessage(cause), 'error') } }
  if (loading) return <section className="panel"><LoadingState /></section>
  if (!form) return <section className="panel"><EmptyState title={c('Sin parámetros', 'No settings')} description={error || c('No se pudo cargar la empresa.', 'The company could not be loaded.')} /></section>
  return <section className="panel settings-panel"><form onSubmit={save}><div className="settings-logo-row"><div className="settings-logo-preview">{logoUrl ? <img src={logoUrl} alt={c('Logo de empresa', 'Company logo')} /> : <Building2 size={28} />}</div><div><strong>{c('Identidad visual', 'Visual identity')}</strong><p>{c('PNG, JPEG o WebP; máximo 2 MiB.', 'PNG, JPEG or WebP; 2 MiB maximum.')}</p><div className="row-actions"><label className="button button-secondary button-small"><Upload size={15} />{c('Subir logo', 'Upload logo')}<input className="visually-hidden" type="file" accept="image/png,image/jpeg,image/webp" onChange={(event) => void upload(event.target.files?.[0])} /></label>{value?.logoSha256 && <button className="button button-danger button-small" type="button" onClick={() => void removeLogo()}><Trash2 size={14} />{c('Eliminar', 'Delete')}</button>}</div></div></div>
    <div className="form-grid settings-form-grid"><Field label={c('Nombre visible', 'Display name')} htmlFor="company-display" required><input id="company-display" value={form.displayName} onChange={(event) => change('displayName', event.target.value)} required /></Field><Field label={c('País ISO', 'ISO country')} htmlFor="company-country" required><input id="company-country" maxLength={2} value={form.countryCode} onChange={(event) => change('countryCode', event.target.value.toUpperCase())} required /></Field><Field label={c('Idioma regional', 'Locale')} htmlFor="company-locale" required><input id="company-locale" value={form.locale} onChange={(event) => change('locale', event.target.value)} required /></Field><Field label={c('Zona horaria', 'Time zone')} htmlFor="company-zone" required><input id="company-zone" value={form.timezone} onChange={(event) => change('timezone', event.target.value)} required /></Field><Field label={c('Moneda base', 'Base currency')} htmlFor="company-currency" required><input id="company-currency" maxLength={3} value={form.baseCurrency} onChange={(event) => change('baseCurrency', event.target.value.toUpperCase())} required /></Field><Field label={c('Teléfono', 'Phone')} htmlFor="company-phone"><input id="company-phone" value={form.phone ?? ''} onChange={(event) => change('phone', event.target.value)} /></Field><Field label={c('Correo de contacto', 'Contact email')} htmlFor="company-contact"><input id="company-contact" type="email" value={form.contactEmail ?? ''} onChange={(event) => change('contactEmail', event.target.value)} /></Field><Field label={c('Correo de facturación', 'Invoice email')} htmlFor="company-invoice"><input id="company-invoice" type="email" value={form.invoiceEmail ?? ''} onChange={(event) => change('invoiceEmail', event.target.value)} /></Field><Field label={c('Correo de respuesta', 'Reply-to email')} htmlFor="company-reply"><input id="company-reply" type="email" value={form.replyToEmail ?? ''} onChange={(event) => change('replyToEmail', event.target.value)} /></Field><Field label={c('Sitio web', 'Website')} htmlFor="company-web"><input id="company-web" type="url" value={form.website ?? ''} onChange={(event) => change('website', event.target.value)} /></Field><Field label={c('Dirección', 'Address')} htmlFor="company-address" wide><input id="company-address" value={form.addressLine1 ?? ''} onChange={(event) => change('addressLine1', event.target.value)} /></Field><Field label={c('Dirección adicional', 'Additional address')} htmlFor="company-address2" wide><input id="company-address2" value={form.addressLine2 ?? ''} onChange={(event) => change('addressLine2', event.target.value)} /></Field><Field label={c('Código postal', 'Postcode')} htmlFor="company-postal"><input id="company-postal" value={form.postalCode ?? ''} onChange={(event) => change('postalCode', event.target.value)} /></Field><Field label={c('Ciudad', 'City')} htmlFor="company-city"><input id="company-city" value={form.city ?? ''} onChange={(event) => change('city', event.target.value)} /></Field><Field label={c('Provincia / región', 'Region')} htmlFor="company-region"><input id="company-region" value={form.region ?? ''} onChange={(event) => change('region', event.target.value)} /></Field></div>{error && <div className="form-error">{error}</div>}<div className="form-actions"><button className="button button-primary" disabled={saving}>{saving ? c('Guardando…', 'Saving…') : c('Guardar parámetros', 'Save settings')}</button></div></form></section>
}

function NumberingPanel() {
  const { language, t } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const [items, setItems] = useState<NumberingScheme[]>([]); const [editing, setEditing] = useState<NumberingScheme | null | undefined>(undefined); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [preview, setPreview] = useState('')
  const load = useCallback(async () => { setLoading(true); try { const response = await apiFetch<PageResponse<NumberingScheme>>('/api/v1/numbering-schemes?size=100'); setItems(response.content); setError('') } catch (cause) { setError(errorMessage(cause)) } finally { setLoading(false) } }, [])
  useEffect(() => { void load() }, [load])
  const showPreview = async (item: NumberingScheme) => { try { const response = await apiFetch<{ value: string }>(`/api/v1/numbering-schemes/${item.id}/preview?sequence=${item.initialValue}`); setPreview(`${item.name}: ${response.value}`) } catch (cause) { setError(errorMessage(cause)) } }
  return <section className="panel table-panel"><div className="panel-heading settings-heading"><div><strong>{c('Series documentales', 'Document series')}</strong><small>{preview || c('Patrones con fecha, serie y secuencia segura.', 'Patterns with date, series and safe sequence.')}</small></div><button className="button button-primary button-small" type="button" onClick={() => setEditing(null)}><Plus size={15} />{c('Nueva serie', 'New series')}</button></div>{error && <div className="inline-error">{error}</div>}{loading ? <LoadingState /> : items.length ? <div className="table-scroll"><table><thead><tr><th>{c('Código', 'Code')}</th><th>{c('Documento', 'Document')}</th><th>{c('Patrón', 'Pattern')}</th><th>{c('Reinicio', 'Reset')}</th><th>{c('Estado', 'Status')}</th><th>{c('Acciones', 'Actions')}</th></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td><strong>{item.code}</strong><small>{item.name}</small></td><td>{t(documentTypeKey[item.documentType])}</td><td><code>{item.pattern}</code></td><td>{resetPeriodLabel(item.resetPeriod, language)}</td><td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.defaultScheme ? c('Predeterminada', 'Default') : item.active ? c('Activa', 'Active') : c('Inactiva', 'Inactive')}</StatusBadge></td><td><div className="row-actions"><button className="button button-secondary button-small" type="button" onClick={() => void showPreview(item)}>{c('Vista previa', 'Preview')}</button><button className="icon-button" type="button" aria-label={c('Editar serie', 'Edit series')} onClick={() => setEditing(item)}><Pencil size={15} /></button></div></td></tr>)}</tbody></table></div> : <EmptyState title={c('No hay numeraciones', 'No numbering schemes')} description={c('Crea una serie para facturas, pedidos o albaranes.', 'Create a series for invoices, orders or delivery notes.')} />}
    <Modal open={editing !== undefined} title={editing ? c('Editar numeración', 'Edit numbering') : c('Nueva numeración', 'New numbering')} onClose={() => setEditing(undefined)}><NumberingForm value={editing ?? null} onCancel={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); void load() }} /></Modal></section>
}

function NumberingForm({ value, onCancel, onSaved }: { value: NumberingScheme | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState<Omit<NumberingScheme, 'id'>>(value ?? { code: '', name: '', documentType: 'INVOICE', series: 'A', pattern: '{yyyy}-{series}-{seq:6}', resetPeriod: 'YEARLY', initialValue: 1, active: true, defaultScheme: false }); const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => { event.preventDefault(); setSaving(true); try { await apiFetch(value ? `/api/v1/numbering-schemes/${value.id}` : '/api/v1/numbering-schemes', { method: value ? 'PUT' : 'POST', body: JSON.stringify(form) }); onSaved() } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) } }
  return <form onSubmit={submit}><div className="form-grid"><Field label={c('Código', 'Code')} htmlFor="number-code" required><input id="number-code" value={form.code} disabled={Boolean(value)} onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })} required /></Field><Field label={c('Nombre', 'Name')} htmlFor="number-name" required><input id="number-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required /></Field><Field label={c('Tipo documental', 'Document type')} htmlFor="number-type" required><select id="number-type" value={form.documentType} onChange={(event) => setForm({ ...form, documentType: event.target.value as DocumentType })}>{(['QUOTE', 'SALES_ORDER', 'DELIVERY_NOTE', 'INVOICE', 'RECTIFYING_INVOICE', 'WORK_ORDER'] as DocumentType[]).map((type) => <option key={type} value={type}>{t(documentTypeKey[type])}</option>)}</select></Field><Field label={c('Serie', 'Series')} htmlFor="number-series" required><input id="number-series" value={form.series} onChange={(event) => setForm({ ...form, series: event.target.value.toUpperCase() })} required /></Field><Field label={c('Patrón', 'Pattern')} htmlFor="number-pattern" required wide hint="{yyyy} {yy} {MM} {dd} {series} {seq:6}"><input id="number-pattern" value={form.pattern} onChange={(event) => setForm({ ...form, pattern: event.target.value })} required /></Field><Field label={c('Reinicio', 'Reset period')} htmlFor="number-reset"><select id="number-reset" value={form.resetPeriod} onChange={(event) => setForm({ ...form, resetPeriod: event.target.value as ResetPeriod })}>{(['YEARLY', 'MONTHLY', 'DAILY', 'NEVER'] as ResetPeriod[]).map((period) => <option key={period} value={period}>{resetPeriodLabel(period, language)}</option>)}</select></Field><Field label={c('Valor inicial', 'Initial value')} htmlFor="number-initial"><input id="number-initial" type="number" min="1" value={form.initialValue} onChange={(event) => setForm({ ...form, initialValue: Number(event.target.value) })} /></Field><Field label={c('Opciones', 'Options')} htmlFor="number-active"><label className="switch-row"><input id="number-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Activa', 'Active')}</span></label><label className="switch-row"><input type="checkbox" checked={form.defaultScheme} onChange={(event) => setForm({ ...form, defaultScheme: event.target.checked })} /><span>{c('Predeterminada', 'Default')}</span></label></Field></div>{error && <div className="form-error">{error}</div>}<FormActions onCancel={onCancel} saving={saving} /></form>
}

function CurrenciesPanel() {
  const { language, locale } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const [currencies, setCurrencies] = useState<CurrencyValue[]>([]); const [rates, setRates] = useState<ExchangeRate[]>([]); const [currencyEdit, setCurrencyEdit] = useState<CurrencyValue | null | undefined>(undefined); const [rateEdit, setRateEdit] = useState<ExchangeRate | null | undefined>(undefined); const [loading, setLoading] = useState(true); const [error, setError] = useState('')
  const load = useCallback(async () => { setLoading(true); try { const [currencyData, rateData] = await Promise.all([apiFetch<CurrencyValue[]>('/api/v1/currencies'), apiFetch<PageResponse<ExchangeRate>>('/api/v1/exchange-rates?size=100')]); setCurrencies(currencyData); setRates(rateData.content); setError('') } catch (cause) { setError(errorMessage(cause)) } finally { setLoading(false) } }, [])
  useEffect(() => { void load() }, [load])
  if (loading) return <section className="panel"><LoadingState /></section>
  const canCreateRate = currencies.filter((currency) => currency.active).length >= 2
  return <div className="settings-columns"><section className="panel table-panel"><div className="panel-heading settings-heading"><div><strong>{c('Monedas', 'Currencies')}</strong><small>{c('Catálogo operativo ISO 4217.', 'Operational ISO 4217 catalogue.')}</small></div><button className="button button-primary button-small" type="button" onClick={() => setCurrencyEdit(null)}><Plus size={15} />{c('Nueva', 'New')}</button></div>{error && <div className="inline-error">{error}</div>}<div className="table-scroll"><table><thead><tr><th>{c('Moneda', 'Currency')}</th><th>{c('Decimales', 'Decimals')}</th><th>{c('Estado', 'Status')}</th><th></th></tr></thead><tbody>{currencies.map((item) => <tr key={item.id}><td><strong>{item.code}</strong><small>{item.symbol} · {item.name}</small></td><td>{item.decimalPlaces}</td><td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.baseCurrency ? c('Base', 'Base') : item.active ? c('Activa', 'Active') : c('Inactiva', 'Inactive')}</StatusBadge></td><td><button className="icon-button" type="button" onClick={() => setCurrencyEdit(item)}><Pencil size={15} /></button></td></tr>)}</tbody></table></div></section><section className="panel table-panel"><div className="panel-heading settings-heading"><div><strong>{c('Tipos de cambio', 'Exchange rates')}</strong><small>{c('Histórico por fecha y fuente.', 'History by date and source.')}</small></div><button className="button button-primary button-small" type="button" disabled={!canCreateRate} title={!canCreateRate ? c('Añade al menos dos monedas activas.', 'Add at least two active currencies.') : undefined} onClick={() => setRateEdit(null)}><Plus size={15} />{c('Nuevo', 'New')}</button></div><div className="table-scroll"><table><thead><tr><th>{c('Par', 'Pair')}</th><th>{c('Cambio', 'Rate')}</th><th>{c('Fecha', 'Date')}</th><th></th></tr></thead><tbody>{rates.map((item) => <tr key={item.id}><td><strong>{item.baseCode}/{item.quoteCode}</strong><small>{item.source}</small></td><td>{new Intl.NumberFormat(locale, { maximumFractionDigits: 8 }).format(item.rate)}</td><td>{formatDate(item.rateDate, locale)}</td><td><button className="icon-button" type="button" onClick={() => setRateEdit(item)}><Pencil size={15} /></button></td></tr>)}</tbody></table></div></section>
    <CurrencyConverter currencies={currencies} />
    <Modal open={currencyEdit !== undefined} title={currencyEdit ? c('Editar moneda', 'Edit currency') : c('Nueva moneda', 'New currency')} onClose={() => setCurrencyEdit(undefined)}><CurrencyForm value={currencyEdit ?? null} onCancel={() => setCurrencyEdit(undefined)} onSaved={() => { setCurrencyEdit(undefined); void load() }} /></Modal><Modal open={rateEdit !== undefined} title={rateEdit ? c('Editar cambio', 'Edit exchange rate') : c('Nuevo tipo de cambio', 'New exchange rate')} onClose={() => setRateEdit(undefined)}><RateForm value={rateEdit ?? null} currencies={currencies} onCancel={() => setRateEdit(undefined)} onSaved={() => { setRateEdit(undefined); void load() }} /></Modal></div>
}

function CurrencyConverter({ currencies }: { currencies: CurrencyValue[] }) {
  const { language, locale } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const activeCurrencies = currencies.filter((currency) => currency.active)
  const today = new Date().toISOString().slice(0, 10)
  const [amount, setAmount] = useState(1)
  const [fromCurrency, setFromCurrency] = useState('')
  const [toCurrency, setToCurrency] = useState('')
  const [date, setDate] = useState(today)
  const [result, setResult] = useState<CurrencyConversionResult | null>(null)
  const [converting, setConverting] = useState(false)
  const [error, setError] = useState('')
  const hasConversionPair = activeCurrencies.length >= 2
  useEffect(() => {
    const available = currencies.filter((currency) => currency.active)
    const preferredFrom = available.find((currency) => currency.baseCurrency)?.code ?? available[0]?.code ?? ''
    setFromCurrency((current) => available.some((currency) => currency.code === current) ? current : preferredFrom)
    setToCurrency((current) => available.some((currency) => currency.code === current && currency.code !== preferredFrom)
      ? current
      : available.find((currency) => currency.code !== preferredFrom)?.code ?? preferredFrom)
    setResult(null)
  }, [currencies])
  const convert = async (event: FormEvent) => {
    event.preventDefault()
    if (!hasConversionPair || !fromCurrency || !toCurrency || fromCurrency === toCurrency) {
      setError(c('Configura al menos dos monedas activas distintas para poder convertir.', 'Configure at least two different active currencies before converting.'))
      return
    }
    setConverting(true); setResult(null)
    try {
      setResult(await apiFetch<CurrencyConversionResult>('/api/v1/currency-conversions', { method: 'POST', body: JSON.stringify({ amount, fromCurrency, toCurrency, date }) }))
      setError('')
    } catch (cause) { setError(errorMessage(cause)) } finally { setConverting(false) }
  }
  const currencyOptions = activeCurrencies.length
    ? activeCurrencies.map((currency) => <option key={currency.id} value={currency.code}>{currency.code} · {currency.name}</option>)
    : <option value="">{c('No hay monedas activas', 'No active currencies')}</option>
  return <section className="panel settings-wide"><div className="panel-heading settings-heading"><div><strong>{c('Conversor de moneda', 'Currency converter')}</strong><small>{c('Comprueba el cambio histórico que aplicará el ERP.', 'Check the historical exchange rate the ERP will apply.')}</small></div></div><form className="settings-converter" onSubmit={convert}><Field label={c('Importe', 'Amount')} htmlFor="conversion-amount" required><input id="conversion-amount" type="number" min="0" step="any" value={amount} onChange={(event) => setAmount(Number(event.target.value))} required /></Field><Field label={c('Desde', 'From')} htmlFor="conversion-from" required><select id="conversion-from" value={fromCurrency} disabled={!activeCurrencies.length} onChange={(event) => { setFromCurrency(event.target.value); setResult(null); setError('') }}>{currencyOptions}</select></Field><Field label={c('A', 'To')} htmlFor="conversion-to" required><select id="conversion-to" value={toCurrency} disabled={!activeCurrencies.length} onChange={(event) => { setToCurrency(event.target.value); setResult(null); setError('') }}>{currencyOptions}</select></Field><Field label={c('Fecha', 'Date')} htmlFor="conversion-date" required><input id="conversion-date" type="date" value={date} onChange={(event) => setDate(event.target.value)} required /></Field><button className="button button-primary" type="submit" disabled={converting || !hasConversionPair || fromCurrency === toCurrency}>{converting ? c('Convirtiendo…', 'Converting…') : c('Convertir', 'Convert')}</button></form>{!hasConversionPair && <div className="converter-notice" role="status">{c('Añade al menos dos monedas activas en el catálogo para utilizar el conversor.', 'Add at least two active currencies to the catalogue to use the converter.')}</div>}{error && <div className="inline-error settings-inline-message">{error}</div>}{result && <div className="conversion-result"><strong>{new Intl.NumberFormat(locale, { style: 'currency', currency: result.sourceCurrency }).format(result.sourceAmount)} = {new Intl.NumberFormat(locale, { style: 'currency', currency: result.targetCurrency }).format(result.targetAmount)}</strong><small>{c('Tipo', 'Rate')} {new Intl.NumberFormat(locale, { maximumFractionDigits: 8 }).format(result.exchangeRate)} · {formatDate(result.rateDate, locale)} · {result.rateSource}{result.inverseRate ? ` · ${c('Inverso', 'Inverse')}` : ''}</small></div>}</section>
}

function CurrencyForm({ value, onCancel, onSaved }: { value: CurrencyValue | null; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState<Omit<CurrencyValue, 'id'>>(value ?? { code: '', name: '', symbol: '', decimalPlaces: 2, baseCurrency: false, active: true }); const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => { event.preventDefault(); setSaving(true); try { await apiFetch(value ? `/api/v1/currencies/${value.id}` : '/api/v1/currencies', { method: value ? 'PUT' : 'POST', body: JSON.stringify(form) }); onSaved() } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) } }
  return <form onSubmit={submit}><div className="form-grid"><Field label={c('Código ISO', 'ISO code')} htmlFor="currency-code" required><input id="currency-code" maxLength={3} disabled={Boolean(value)} value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })} required /></Field><Field label={c('Nombre', 'Name')} htmlFor="currency-name" required><input id="currency-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required /></Field><Field label={c('Símbolo', 'Symbol')} htmlFor="currency-symbol" required><input id="currency-symbol" value={form.symbol} onChange={(event) => setForm({ ...form, symbol: event.target.value })} required /></Field><Field label={c('Decimales', 'Decimal places')} htmlFor="currency-decimals"><input id="currency-decimals" type="number" min="0" max="6" value={form.decimalPlaces} onChange={(event) => setForm({ ...form, decimalPlaces: Number(event.target.value) })} /></Field><Field label={c('Opciones', 'Options')} htmlFor="currency-active" wide><label className="switch-row"><input id="currency-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Activa', 'Active')}</span></label><label className="switch-row"><input type="checkbox" checked={form.baseCurrency} onChange={(event) => setForm({ ...form, baseCurrency: event.target.checked })} /><span>{c('Moneda base', 'Base currency')}</span></label></Field></div>{error && <div className="form-error">{error}</div>}<FormActions onCancel={onCancel} saving={saving} /></form>
}

function RateForm({ value, currencies, onCancel, onSaved }: { value: ExchangeRate | null; currencies: CurrencyValue[]; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const today = new Date().toISOString().slice(0, 10); const [form, setForm] = useState(value ?? { id: '', baseCode: currencies[0]?.code ?? 'EUR', quoteCode: currencies[1]?.code ?? 'USD', rate: 1, rateDate: today, source: 'MANUAL', active: true }); const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => { event.preventDefault(); setSaving(true); try { await apiFetch(value ? `/api/v1/exchange-rates/${value.id}` : '/api/v1/exchange-rates', { method: value ? 'PUT' : 'POST', body: JSON.stringify(value ? { rate: form.rate, active: form.active } : form) }); onSaved() } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) } }
  return <form onSubmit={submit}><div className="form-grid"><Field label={c('Moneda base', 'Base currency')} htmlFor="rate-base" required><select id="rate-base" disabled={Boolean(value)} value={form.baseCode} onChange={(event) => setForm({ ...form, baseCode: event.target.value })}>{currencies.map((item) => <option key={item.id}>{item.code}</option>)}</select></Field><Field label={c('Moneda destino', 'Quote currency')} htmlFor="rate-quote" required><select id="rate-quote" disabled={Boolean(value)} value={form.quoteCode} onChange={(event) => setForm({ ...form, quoteCode: event.target.value })}>{currencies.map((item) => <option key={item.id}>{item.code}</option>)}</select></Field><Field label={c('Tipo de cambio', 'Exchange rate')} htmlFor="rate-value" required><input id="rate-value" type="number" min="0.00000001" step="any" value={form.rate} onChange={(event) => setForm({ ...form, rate: Number(event.target.value) })} required /></Field><Field label={c('Fecha', 'Date')} htmlFor="rate-date" required><input id="rate-date" type="date" disabled={Boolean(value)} value={form.rateDate} onChange={(event) => setForm({ ...form, rateDate: event.target.value })} required /></Field><Field label={c('Fuente', 'Source')} htmlFor="rate-source" required><input id="rate-source" disabled={Boolean(value)} value={form.source} onChange={(event) => setForm({ ...form, source: event.target.value })} required /></Field><Field label={c('Estado', 'Status')} htmlFor="rate-active"><label className="switch-row"><input id="rate-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Activo', 'Active')}</span></label></Field></div>{error && <div className="form-error">{error}</div>}<FormActions onCancel={onCancel} saving={saving} /></form>
}

function LicensesPanel() {
  const { language, locale } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const [items, setItems] = useState<LicenseSummary[]>([]); const [creating, setCreating] = useState(false); const [activationCode, setActivationCode] = useState(''); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const { notify } = useToast()
  const load = useCallback(async () => { setLoading(true); try { const response = await apiFetch<LicensePage>('/api/v1/licenses?size=100'); setItems(response.content); setError('') } catch (cause) { setError(errorMessage(cause)) } finally { setLoading(false) } }, [])
  useEffect(() => { void load() }, [load])
  const action = async (item: LicenseSummary, operation: 'suspend' | 'resume' | 'revoke') => { try { await apiFetch(`/api/v1/licenses/${item.id}/${operation}`, { method: 'POST' }); notify(c('Licencia actualizada.', 'Licence updated.')); await load() } catch (cause) { notify(errorMessage(cause), 'error') } }
  return <section className="panel table-panel"><div className="panel-heading settings-heading"><div><strong>{c('Licencias emitidas', 'Issued licences')}</strong><small>{c('Activación, vigencia y comprobación periódica.', 'Activation, validity and periodic validation.')}</small></div><button className="button button-primary button-small" type="button" onClick={() => setCreating(true)}><Plus size={15} />{c('Nueva licencia', 'New licence')}</button></div>{error && <div className="inline-error">{error}</div>}{loading ? <LoadingState /> : items.length ? <div className="table-scroll"><table><thead><tr><th>{c('Licencia', 'Licence')}</th><th>{c('Vigencia', 'Validity')}</th><th>{c('Instalaciones', 'Installations')}</th><th>{c('Estado', 'Status')}</th><th>{c('Acciones', 'Actions')}</th></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td><strong>{item.displayName}</strong><small>{item.features.join(', ') || c('Sin funciones limitadas', 'No restricted features')}</small></td><td>{formatDateTime(item.validFrom, locale)}<small>{c('Hasta', 'Until')} {formatDateTime(item.validUntil, locale)}</small></td><td>{item.activeInstallations} / {item.maxInstallations}</td><td><StatusBadge tone={item.status === 'ACTIVE' ? 'success' : item.status === 'SUSPENDED' ? 'warning' : 'danger'}>{licenseStatusLabel(item.status, language)}</StatusBadge></td><td><div className="row-actions">{item.status === 'ACTIVE' && <button className="button button-secondary button-small" type="button" onClick={() => void action(item, 'suspend')}>{c('Suspender', 'Suspend')}</button>}{item.status === 'SUSPENDED' && <button className="button button-secondary button-small" type="button" onClick={() => void action(item, 'resume')}>{c('Reanudar', 'Resume')}</button>}{item.status !== 'REVOKED' && <button className="button button-danger button-small" type="button" onClick={() => void action(item, 'revoke')}>{c('Revocar', 'Revoke')}</button>}</div></td></tr>)}</tbody></table></div> : <EmptyState title={c('No hay licencias', 'No licences')} description={c('Emite la primera licencia para una instalación cliente.', 'Issue the first licence for a customer installation.')} />}
    <Modal open={creating} title={c('Nueva licencia', 'New licence')} description={c('El código de activación solo se mostrará una vez.', 'The activation code will only be shown once.')} onClose={() => setCreating(false)}><LicenseForm onCancel={() => setCreating(false)} onCreated={(code) => { setCreating(false); setActivationCode(code); void load() }} /></Modal><Modal open={Boolean(activationCode)} title={c('Código de activación', 'Activation code')} description={c('Guárdalo ahora y envíalo por un canal seguro.', 'Save it now and send it through a secure channel.')} onClose={() => setActivationCode('')}><div className="secret-panel"><code>{activationCode}</code><button className="button button-primary" type="button" onClick={() => { void navigator.clipboard.writeText(activationCode); notify(c('Código copiado.', 'Code copied.')) }}>{c('Copiar código', 'Copy code')}</button></div></Modal></section>
}

function LicenseForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: (code: string) => void }) {
  const { language } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const now = new Date(); const until = new Date(now); until.setFullYear(until.getFullYear() + 1)
  const [form, setForm] = useState({ displayName: '', validFrom: now.toISOString().slice(0, 16), validUntil: until.toISOString().slice(0, 16), gracePeriodSeconds: 604800, maxInstallations: 1, checkIntervalSeconds: 3600, features: '' }); const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => { event.preventDefault(); setSaving(true); try { const response = await apiFetch<{ activationCode: string }>('/api/v1/licenses', { method: 'POST', body: JSON.stringify({ ...form, validFrom: form.validFrom ? new Date(form.validFrom).toISOString() : null, validUntil: new Date(form.validUntil).toISOString(), features: form.features.split(',').map((value) => value.trim()).filter(Boolean) }) }); onCreated(response.activationCode) } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) } }
  return <form onSubmit={submit}><div className="form-grid"><Field label={c('Nombre', 'Name')} htmlFor="license-name" required wide><input id="license-name" value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} required /></Field><Field label={c('Válida desde', 'Valid from')} htmlFor="license-from"><input id="license-from" type="datetime-local" value={form.validFrom} onChange={(event) => setForm({ ...form, validFrom: event.target.value })} /></Field><Field label={c('Válida hasta', 'Valid until')} htmlFor="license-until"><input id="license-until" type="datetime-local" value={form.validUntil} onChange={(event) => setForm({ ...form, validUntil: event.target.value })} required /></Field><Field label={c('Gracia (segundos)', 'Grace (seconds)')} htmlFor="license-grace"><input id="license-grace" type="number" min="0" value={form.gracePeriodSeconds} onChange={(event) => setForm({ ...form, gracePeriodSeconds: Number(event.target.value) })} /></Field><Field label={c('Máx. instalaciones', 'Max installations')} htmlFor="license-installs"><input id="license-installs" type="number" min="1" value={form.maxInstallations} onChange={(event) => setForm({ ...form, maxInstallations: Number(event.target.value) })} /></Field><Field label={c('Comprobación (segundos)', 'Check interval (seconds)')} htmlFor="license-check"><input id="license-check" type="number" min="60" value={form.checkIntervalSeconds} onChange={(event) => setForm({ ...form, checkIntervalSeconds: Number(event.target.value) })} /></Field><Field label={c('Funciones (separadas por comas)', 'Features (comma-separated)')} htmlFor="license-features" wide><input id="license-features" value={form.features} onChange={(event) => setForm({ ...form, features: event.target.value })} /></Field></div>{error && <div className="form-error">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={c('Emitir licencia', 'Issue licence')} /></form>
}

function resetPeriodLabel(period: ResetPeriod, language: 'es' | 'en') {
  const labels: Record<'es' | 'en', Record<ResetPeriod, string>> = {
    es: { YEARLY: 'Anual', MONTHLY: 'Mensual', DAILY: 'Diario', NEVER: 'Nunca' },
    en: { YEARLY: 'Yearly', MONTHLY: 'Monthly', DAILY: 'Daily', NEVER: 'Never' },
  }
  return labels[language][period]
}

function licenseStatusLabel(status: LicenseSummary['status'], language: 'es' | 'en') {
  const labels: Record<'es' | 'en', Record<LicenseSummary['status'], string>> = {
    es: { DRAFT: 'Borrador', ACTIVE: 'Activa', SUSPENDED: 'Suspendida', EXPIRED: 'Caducada', REVOKED: 'Revocada' },
    en: { DRAFT: 'Draft', ACTIVE: 'Active', SUSPENDED: 'Suspended', EXPIRED: 'Expired', REVOKED: 'Revoked' },
  }
  return labels[language][status]
}

interface VerifactuSettingsValue {
  configured: boolean
  enabled: boolean
  mode: 'VERIFACTU' | 'NO_VERIFACTU'
  environment: 'TEST' | 'PRODUCTION'
  issuerTaxId: string
  issuerLegalName: string
  defaultRegimeKey: string
  defaultOperationQualification: string
  timeZone: string
  qrValidationUrl: string
  softwareName: string
  softwareId: string
  softwareVersion: string
  developerTaxId: string
}

function VerifactuPanel() {
  const { language } = useTranslation(); const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState<VerifactuSettingsValue | null>(null)
  const [configured, setConfigured] = useState(false)
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const { notify } = useToast()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const response = await apiFetch<VerifactuSettingsValue>('/api/v1/verifactu-settings/current')
      setForm(response); setConfigured(response.configured); setError('')
    } catch (cause) { setError(errorMessage(cause)) } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])

  const change = <K extends keyof VerifactuSettingsValue>(key: K, next: VerifactuSettingsValue[K]) =>
    setForm((current) => current ? { ...current, [key]: next } : current)

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!form) return
    setSaving(true); setError('')
    try {
      const updated = await apiFetch<VerifactuSettingsValue>('/api/v1/verifactu-settings/current', {
        method: 'PUT',
        body: JSON.stringify({
          enabled: form.enabled, mode: form.mode, environment: form.environment,
          issuerTaxId: form.issuerTaxId, issuerLegalName: form.issuerLegalName,
          defaultRegimeKey: form.defaultRegimeKey,
          defaultOperationQualification: form.defaultOperationQualification,
          timeZone: form.timeZone,
        }),
      })
      setForm(updated); setConfigured(true)
      notify(c('Configuración de Veri*Factu guardada.', 'Veri*Factu settings saved.'))
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  if (loading) return <section className="panel"><LoadingState /></section>
  if (!form) return <section className="panel"><EmptyState title={c('Sin configuración', 'No settings')} description={error || c('No se pudo cargar la configuración.', 'Settings could not be loaded.')} /></section>

  const production = form.environment === 'PRODUCTION'
  return <section className="panel settings-panel">
    <form onSubmit={save}>
      <div className="settings-logo-row">
        <div className="settings-logo-preview"><ShieldCheck size={28} /></div>
        <div>
          <strong>{c('Sistema informático de facturación', 'Invoicing software system')}</strong>
          <p>{c('Registros de facturación encadenados y remitidos a la AEAT. Obligatorio desde el 1 de enero de 2027 para sociedades y el 1 de julio de 2027 para autónomos.', 'Chained invoicing records reported to the Spanish tax agency. Mandatory from 1 January 2027 for companies and 1 July 2027 for the self-employed.')}</p>
          <div className="row-actions">
            <StatusBadge tone={form.enabled ? 'success' : 'neutral'}>{form.enabled ? c('Activado', 'Enabled') : c('Desactivado', 'Disabled')}</StatusBadge>
            <StatusBadge tone={production ? 'success' : 'warning'}>{production ? c('Producción', 'Production') : c('Pruebas', 'Test')}</StatusBadge>
            {!configured && <StatusBadge tone="warning">{c('Sin guardar', 'Not saved yet')}</StatusBadge>}
          </div>
        </div>
      </div>

      <div className="form-grid settings-form-grid">
        <Field label={c('NIF del obligado', 'Issuer tax ID')} htmlFor="vf-nif" required>
          <input id="vf-nif" value={form.issuerTaxId} maxLength={20} required
            onChange={(event) => change('issuerTaxId', event.target.value.toUpperCase())} />
        </Field>
        <Field label={c('Razón social', 'Legal name')} htmlFor="vf-name" required>
          <input id="vf-name" value={form.issuerLegalName} maxLength={180} required
            onChange={(event) => change('issuerLegalName', event.target.value)} />
        </Field>
        <Field label={c('Entorno', 'Environment')} htmlFor="vf-env">
          <select id="vf-env" value={form.environment}
            onChange={(event) => change('environment', event.target.value as VerifactuSettingsValue['environment'])}>
            <option value="TEST">{c('Pruebas (preproducción)', 'Test (pre-production)')}</option>
            <option value="PRODUCTION">{c('Producción', 'Production')}</option>
          </select>
        </Field>
        <Field label={c('Zona horaria', 'Time zone')} htmlFor="vf-zone" required
          hint={c('Fija el huso de la fecha del registro.', 'Sets the offset of the record timestamp.')}>
          <input id="vf-zone" value={form.timeZone} maxLength={64} required
            onChange={(event) => change('timeZone', event.target.value)} />
        </Field>
        <Field label={c('Clave de régimen por defecto', 'Default regime key')} htmlFor="vf-regime"
          hint={c('01 = régimen general.', '01 = general regime.')}>
          <input id="vf-regime" value={form.defaultRegimeKey} maxLength={2} pattern="\d{2}"
            onChange={(event) => change('defaultRegimeKey', event.target.value)} />
        </Field>
        <Field label={c('Calificación por defecto', 'Default operation qualification')} htmlFor="vf-qualification"
          hint={c('S1 = sujeta y no exenta.', 'S1 = subject and not exempt.')}>
          <input id="vf-qualification" value={form.defaultOperationQualification} maxLength={2}
            onChange={(event) => change('defaultOperationQualification', event.target.value.toUpperCase())} />
        </Field>
        <Field label={c('Estado', 'Status')} htmlFor="vf-enabled" wide>
          <label className="switch-row">
            <input id="vf-enabled" type="checkbox" checked={form.enabled}
              onChange={(event) => change('enabled', event.target.checked)} />
            <span>{c('Generar y remitir registros de facturación', 'Generate and report invoicing records')}</span>
          </label>
        </Field>
      </div>

      <div className="form-grid settings-form-grid">
        <Field label={c('Productor del software', 'Software producer')} htmlFor="vf-software" wide
          hint={c('Identifica a PERA ante la AEAT. Se configura en el despliegue, no aquí.', 'Identifies PERA to the tax agency. Configured at deployment, not here.')}>
          <input id="vf-software" readOnly value={`${form.softwareName} ${form.softwareVersion} · ${form.softwareId} · ${form.developerTaxId || c('Sin NIF de productor', 'No producer tax ID')}`} />
        </Field>
        <Field label={c('Servicio de cotejo del QR', 'QR verification service')} htmlFor="vf-qr" wide>
          <input id="vf-qr" readOnly value={form.qrValidationUrl} />
        </Field>
      </div>

      {error && <div className="form-error">{error}</div>}
      <div className="form-actions">
        <button className="button button-primary" disabled={saving}>
          {saving ? c('Guardando…', 'Saving…') : c('Guardar configuración', 'Save settings')}
        </button>
      </div>
    </form>
  </section>
}
