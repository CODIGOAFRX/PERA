import { useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch } from '../lib/api'
import type { DocumentType } from '../types/api'
import { Field, FormActions, FormErrors, useFormErrors } from './Form'

export type ResetPeriod = 'YEARLY' | 'MONTHLY' | 'DAILY' | 'NEVER'

/** Comportamiento de la serie, como la ficha de series de DimproCristalWin (tabla fseries). */
export interface SeriesOptions {
  displayOrder: number
  color: string | null
  listingGroup: string | null
  nextSchemeId: string | null
  sameNumberAsSource: boolean
  sameDateAsSource: boolean
  asksNumber: boolean
  carriesNotes: boolean
  footerText: string | null
  printCopies: number
  priced: boolean
  internal: boolean
  zeroAmounts: boolean
  countsForRisk: boolean
  remindAfterDays: number
}

export interface DocumentSeries {
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
  options: SeriesOptions
}

export const defaultSeriesOptions: SeriesOptions = {
  displayOrder: 0, color: null, listingGroup: null, nextSchemeId: null, sameNumberAsSource: false, sameDateAsSource: false,
  asksNumber: false, carriesNotes: true, footerText: null, printCopies: 0, priced: true, internal: false, zeroAmounts: false,
  countsForRisk: true, remindAfterDays: 0,
}

/** A qué tipo pasa cada uno al convertir: presupuesto a albarán y albarán a factura. */
const conversionTarget: Partial<Record<DocumentType, DocumentType>> = { QUOTE: 'DELIVERY_NOTE', DELIVERY_NOTE: 'INVOICE' }

/**
 * Cómo queda un número con el formato de la serie; igual que NumberingPatternFormatter del servidor.
 * {yyyy} año, {yy} año con dos cifras, {MM} mes, {dd} día, {series} letras de la serie y {seq:N} el contador con N cifras.
 */
export function formatSeriesNumber(pattern: string, series: string, sequence: number, date = new Date()) {
  const pad = (value: number, width: number) => String(value).padStart(width, '0')
  return pattern
    .replaceAll('{yyyy}', String(date.getFullYear()))
    .replaceAll('{yy}', pad(date.getFullYear() % 100, 2))
    .replaceAll('{MM}', pad(date.getMonth() + 1, 2))
    .replaceAll('{dd}', pad(date.getDate(), 2))
    .replaceAll('{series}', series)
    .replace(/\{seq(?::(\d{1,2}))?\}/g, (_, width?: string) => pad(sequence, width ? Number(width) : 6))
}

/** Formatos habituales, para no tener que escribir el patrón. {n} son las cifras del contador. */
const numberFormats: { id: string; pattern: string; es: string; en: string }[] = [
  { id: 'series-year', pattern: '{series}-{yyyy}-{seq:n}', es: 'Serie, año y número', en: 'Series, year and number' },
  { id: 'year-series', pattern: '{yyyy}-{series}-{seq:n}', es: 'Año, serie y número', en: 'Year, series and number' },
  { id: 'series', pattern: '{series}-{seq:n}', es: 'Serie y número', en: 'Series and number' },
  { id: 'year', pattern: '{yy}{seq:n}', es: 'Año y número seguidos (como DimproCristalWin)', en: 'Year and number together' },
  { id: 'series-year-compact', pattern: '{series}{yy}{seq:n}', es: 'Serie, año y número seguidos', en: 'Series, year and number together' },
]

function formatOf(pattern: string) {
  const digits = Number(/\{seq(?::(\d{1,2}))?\}/.exec(pattern)?.[1] ?? 6)
  const found = numberFormats.find((item) => item.pattern.replace('{seq:n}', `{seq:${digits}}`) === pattern)
  return { id: found?.id ?? 'custom', digits }
}

const documentTypes: DocumentType[] = ['QUOTE', 'SALES_ORDER', 'DELIVERY_NOTE', 'INVOICE', 'RECTIFYING_INVOICE', 'WORK_ORDER']

export function SeriesForm({ value, all, typeLabel, resetLabel, onCancel, onSaved }: {
  value: DocumentSeries | null
  all: DocumentSeries[]
  typeLabel: (type: DocumentType) => string
  resetLabel: (period: ResetPeriod) => string
  onCancel: () => void
  onSaved: () => void
}) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const [form, setForm] = useState<Omit<DocumentSeries, 'id'>>(value ?? {
    code: '', name: '', documentType: 'INVOICE', series: 'A', pattern: '{yyyy}-{series}-{seq:6}', resetPeriod: 'YEARLY',
    initialValue: 1, active: true, defaultScheme: false, options: defaultSeriesOptions,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const options = form.options ?? defaultSeriesOptions
  const set = (next: Partial<SeriesOptions>) => setForm({ ...form, options: { ...options, ...next } })
  const target = conversionTarget[form.documentType]
  const nextCandidates = target ? all.filter((item) => item.documentType === target && item.id !== value?.id) : []
  const isInvoice = form.documentType === 'INVOICE' || form.documentType === 'RECTIFYING_INVOICE'
  const numberFormat = formatOf(form.pattern)
  const [customPattern, setCustomPattern] = useState(numberFormat.id === 'custom')
  const applyFormat = (id: string, digits: number) => {
    const preset = numberFormats.find((item) => item.id === id)
    if (preset) setForm({ ...form, pattern: preset.pattern.replace('{seq:n}', `{seq:${Math.min(Math.max(digits, 1), 12)}}`) })
  }
  const example = /\{seq/.test(form.pattern) ? formatSeriesNumber(form.pattern, form.series || 'A', form.initialValue || 1) : ''

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true)
    try {
      const body = { ...form, options: { ...options, nextSchemeId: target ? options.nextSchemeId : null } }
      await apiFetch(value ? `/api/v1/numbering-schemes/${value.id}` : '/api/v1/numbering-schemes', { method: value ? 'PUT' : 'POST', body: JSON.stringify(body) })
      onSaved()
    } catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  const check = (checked: boolean, onChange: (checked: boolean) => void, label: string, hint?: string, disabled = false): ReactNode =>
    <label className="switch-row" title={hint}><input type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} /><span>{label}{hint && <small className="series-hint">{hint}</small>}</span></label>

  return <form onSubmit={submit} className="series-form"><FormErrors value={formErrors.context}>
    <h3 className="series-section">{c('Serie y numeración', 'Series and numbering')}</h3>
    <div className="form-grid">
      <Field label={c('Código', 'Code')} htmlFor="number-code" name="code" required><input id="number-code" value={form.code} disabled={Boolean(value)} onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })} required /></Field>
      <Field label={c('Nombre', 'Name')} htmlFor="number-name" name="name" required hint={c('Por ejemplo: Factura Contado, Albarán sin valorar, Reposición.', 'For example: Cash invoice, Unpriced delivery note.')}><input id="number-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required /></Field>
      <Field label={c('Tipo documental', 'Document type')} htmlFor="number-type" name="documentType" required><select id="number-type" value={form.documentType} disabled={Boolean(value)} onChange={(event) => setForm({ ...form, documentType: event.target.value as DocumentType })}>{documentTypes.map((type) => <option key={type} value={type}>{typeLabel(type)}</option>)}</select></Field>
      <Field label={c('Serie', 'Series')} htmlFor="number-series" name="series" required><input id="number-series" value={form.series} onChange={(event) => setForm({ ...form, series: event.target.value.toUpperCase() })} required /></Field>
      <Field label={c('Formato del número', 'Number format')} htmlFor="number-format" name="pattern" required>
        <select id="number-format" value={customPattern ? 'custom' : numberFormat.id} onChange={(event) => { const id = event.target.value; setCustomPattern(id === 'custom'); if (id !== 'custom') applyFormat(id, numberFormat.digits) }}>
          {numberFormats.map((item) => <option key={item.id} value={item.id}>{c(item.es, item.en)}</option>)}
          <option value="custom">{c('Personalizado', 'Custom')}</option>
        </select>
      </Field>
      {customPattern
        ? <Field label={c('Patrón personalizado', 'Custom pattern')} htmlFor="number-pattern" name="pattern" required hint={c('{yyyy} año · {yy} año con dos cifras · {MM} mes · {dd} día · {series} serie · {seq:5} número con 5 cifras', '{yyyy} year · {yy} two-digit year · {MM} month · {dd} day · {series} series · {seq:5} 5-digit number')}><input id="number-pattern" value={form.pattern} onChange={(event) => setForm({ ...form, pattern: event.target.value })} required /></Field>
        : <Field label={c('Cifras del número', 'Number digits')} htmlFor="number-digits"><input id="number-digits" type="number" min="1" max="12" value={numberFormat.digits} onChange={(event) => applyFormat(numberFormat.id, Number(event.target.value))} /></Field>}
      <p className="series-example" aria-live="polite">{example ? <>{c('Así quedará el primer número: ', 'The first number will look like: ')}<strong>{example}</strong></> : c('El formato tiene que llevar el número ({seq}).', 'The format must include the number ({seq}).')}</p>
      <Field label={c('Reinicio', 'Reset period')} htmlFor="number-reset" name="resetPeriod"><select id="number-reset" value={form.resetPeriod} onChange={(event) => setForm({ ...form, resetPeriod: event.target.value as ResetPeriod })}>{(['YEARLY', 'MONTHLY', 'DAILY', 'NEVER'] as ResetPeriod[]).map((period) => <option key={period} value={period}>{resetLabel(period)}</option>)}</select></Field>
      <Field label={c('Valor inicial', 'Initial value')} htmlFor="number-initial" name="initialValue"><input id="number-initial" type="number" min="1" value={form.initialValue} onChange={(event) => setForm({ ...form, initialValue: Number(event.target.value) })} /></Field>
      <Field label={c('Orden en las listas', 'List order')} htmlFor="series-order"><input id="series-order" type="number" min="0" max="9999" value={options.displayOrder} onChange={(event) => set({ displayOrder: Number(event.target.value) })} /></Field>
      <Field label={c('Color', 'Colour')} htmlFor="series-color" hint={c('Para distinguir la serie en las listas.', 'To tell the series apart in lists.')}><input id="series-color" type="color" value={options.color ?? '#2f6b45'} onChange={(event) => set({ color: event.target.value })} /></Field>
      <Field label={c('Agrupación para listados', 'Listing group')} htmlFor="series-group"><input id="series-group" maxLength={60} value={options.listingGroup ?? ''} onChange={(event) => set({ listingGroup: event.target.value })} /></Field>
    </div>
    <div className="series-checks">
      {check(form.active, (checked) => setForm({ ...form, active: checked }), c('Activa', 'Active'))}
      {check(form.defaultScheme, (checked) => setForm({ ...form, defaultScheme: checked }), c('Principal: la que se propone al crear', 'Main: proposed when creating'))}
      {check(options.asksNumber, (checked) => set({ asksNumber: checked }), c('Pide el número al crear', 'Asks for the number'), c('Se escribe a mano y no cambia el contador.', 'Typed by hand; the counter is not changed.'))}
    </div>

    <h3 className="series-section">{c('Al pasar a otro documento', 'When converting')}</h3>
    <div className="form-grid">
      <Field label={c('Pasa a la serie', 'Converts into series')} htmlFor="series-next" hint={target ? c('Si no se elige, la principal del tipo siguiente.', 'If empty, the main series of the next type.') : c('Este tipo no se pasa a otro documento.', 'This type is not converted.')}>
        <select id="series-next" value={options.nextSchemeId ?? ''} disabled={!target} onChange={(event) => set({ nextSchemeId: event.target.value || null })}>
          <option value="">{c('La principal', 'The main one')}</option>
          {nextCandidates.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}
        </select>
      </Field>
      <Field label={c('Avisar si no se ha pasado en (días)', 'Warn if not converted after (days)')} htmlFor="series-remind" hint={c('0 = sin aviso.', '0 = no warning.')}><input id="series-remind" type="number" min="0" max="999" value={options.remindAfterDays} onChange={(event) => set({ remindAfterDays: Number(event.target.value) })} /></Field>
    </div>
    <div className="series-checks">
      {check(options.sameNumberAsSource, (checked) => set({ sameNumberAsSource: checked }), c('Número igual al documento anterior', 'Same number as the source'))}
      {check(options.sameDateAsSource, (checked) => set({ sameDateAsSource: checked }), c('Fecha igual al documento anterior', 'Same date as the source'))}
      {check(options.carriesNotes, (checked) => set({ carriesNotes: checked }), c('Pasa las notas al documento siguiente', 'Carries notes to the next document'))}
    </div>

    <h3 className="series-section">{c('Impresión', 'Printing')}</h3>
    <div className="form-grid">
      <Field label={c('Texto al pie', 'Footer text')} htmlFor="series-footer" wide hint={c('Por ejemplo la cuenta bancaria o un aviso legal. Máximo 200 caracteres.', 'For example the bank account. Up to 200 characters.')}><input id="series-footer" maxLength={200} value={options.footerText ?? ''} onChange={(event) => set({ footerText: event.target.value })} /></Field>
      <Field label={c('Copias además del original', 'Copies besides the original')} htmlFor="series-copies"><input id="series-copies" type="number" min="0" max="9" value={options.printCopies} onChange={(event) => set({ printCopies: Number(event.target.value) })} /></Field>
    </div>
    <div className="series-checks">
      {check(isInvoice || options.priced, (checked) => set({ priced: checked }), c('Documento valorado (con precios e importes)', 'Priced document'), isInvoice ? c('Una factura siempre va valorada.', 'An invoice is always priced.') : undefined, isInvoice)}
    </div>

    <h3 className="series-section">{c('Comportamiento', 'Behaviour')}</h3>
    <div className="series-checks">
      {check(options.countsForRisk, (checked) => set({ countsForRisk: checked }), c('Genera riesgo en el cliente', 'Counts towards customer risk'))}
      {check(options.internal, (checked) => set({ internal: checked }), c('Documento interno: no se envía al cliente', 'Internal document: never sent to the customer'))}
      {check(options.zeroAmounts, (checked) => set({ zeroAmounts: checked }), c('Reposición: todos los importes a cero', 'Replacement: every amount at zero'), undefined, isInvoice)}
    </div>
    {error && <div className="form-error">{error}</div>}
    <FormActions onCancel={onCancel} saving={saving} />
  </FormErrors></form>
}
