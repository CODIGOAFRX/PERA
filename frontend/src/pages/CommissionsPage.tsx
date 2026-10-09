import { BadgePercent, Calculator, CheckCircle2, ListChecks, Pencil, Plus, RotateCcw } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { TableCaption } from '../components/TableCaption'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { localIsoDate } from '../lib/date'
import { formatCurrency, formatDate, formatNumber } from '../lib/format'
import type { PageResponse, Product, Salesperson } from '../types/api'

type CommissionStatus = 'PENDING' | 'SETTLED'
interface CommissionLine { order: number; description: string; baseAmount: number; percentage: number; commissionAmount: number; origin: 'RULE' | 'DEFAULT' | 'NONE' }
interface Commission {
  id: string; documentId: string; documentNumber: string | null; documentType: string | null; issueDate: string | null
  customerName: string | null; paymentStatus: string | null; salespersonId: string; salespersonName: string
  baseAmount: number; commissionAmount: number; status: CommissionStatus; calculatedAt: string
  settledOn: string | null; settlementNote: string | null; lines: CommissionLine[]
}
interface Totals { count: number; baseAmount: number; commissionAmount: number }
interface Rule {
  id: string; salespersonId: string; productId: string | null; productLabel: string | null; productGroupId: string | null
  productGroupLabel: string | null; amountFrom: number | null; amountTo: number | null; percentage: number; active: boolean
}
interface ProductGroupOption { id: string; code: string; name: string; active: boolean }

const firstOfMonth = () => `${localIsoDate().slice(0, 8)}01`

/**
 * Comisiones de los comerciales, como «Comisiones vendedores» de DimproCristalWin: se recalculan por
 * periodo sobre las facturas, se revisan con su detalle por línea y se liquidan.
 */
export function CommissionsPage() {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { hasPermission } = useAuth()
  const canWrite = hasPermission('commissions:write')
  const [tab, setTab] = useState<'commissions' | 'rules'>('commissions')
  const [salespeople, setSalespeople] = useState<Salesperson[]>([])

  useEffect(() => {
    apiFetch<Salesperson[]>('/api/v1/salespeople').then((people) => setSalespeople(Array.isArray(people) ? people : [])).catch(() => setSalespeople([]))
  }, [])

  return <div className="page-stack">
    <PageHeader eyebrow={c('Ventas', 'Sales')} title={c('Comisiones', 'Commissions')} description={c('Comisiones de los comerciales sobre sus facturas: cálculo, revisión y liquidación.', 'Salespeople commissions on their invoices: calculation, review and settlement.')} icon={BadgePercent} />
    <nav className="workspace-tabs" aria-label={c('Áreas de comisiones', 'Commission areas')}>
      <button type="button" className={tab === 'commissions' ? 'active' : ''} onClick={() => setTab('commissions')}><ListChecks size={15} />{c('Comisiones', 'Commissions')}</button>
      <button type="button" className={tab === 'rules' ? 'active' : ''} onClick={() => setTab('rules')}><BadgePercent size={15} />{c('Reglas', 'Rules')}</button>
    </nav>
    {tab === 'commissions' ? <CommissionsTab salespeople={salespeople} canWrite={canWrite} /> : <RulesTab salespeople={salespeople} canWrite={canWrite} />}
  </div>
}

function CommissionsTab({ salespeople, canWrite }: { salespeople: Salesperson[]; canWrite: boolean }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [filters, setFilters] = useState({ salespersonId: '', fromDate: firstOfMonth(), toDate: localIsoDate(), status: 'PENDING', collected: '' })
  const [data, setData] = useState<PageResponse<Commission> | null>(null)
  const [totals, setTotals] = useState<Totals | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [detail, setDetail] = useState<Commission | null>(null)
  const [settling, setSettling] = useState(false)
  const [calculating, setCalculating] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const update = (name: string, value: string) => { setFilters((current) => ({ ...current, [name]: value })); setPage(0) }

  const params = () => {
    const query = new URLSearchParams()
    if (filters.salespersonId) query.set('salespersonId', filters.salespersonId)
    if (filters.fromDate) query.set('fromDate', filters.fromDate)
    if (filters.toDate) query.set('toDate', filters.toDate)
    if (filters.status) query.set('status', filters.status)
    if (filters.collected) query.set('collected', filters.collected)
    return query
  }

  useEffect(() => {
    let active = true
    setLoading(true); setSelected([])
    const query = params()
    const list = new URLSearchParams(query); list.set('page', String(page)); list.set('size', '20')
    Promise.all([
      apiFetch<PageResponse<Commission>>(`/api/v1/commissions?${list}`),
      apiFetch<Totals>(`/api/v1/commissions/totals?${query}`),
    ]).then(([response, sums]) => { if (active) { setData(response); setTotals(sums); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters, page, refresh])

  const calculate = async () => {
    setCalculating(true)
    try {
      const result = await apiFetch<{ documents: number; calculated: number; settledSkipped: number; commissionAmount: number }>('/api/v1/commissions/calculate', { method: 'POST', body: JSON.stringify({ salespersonId: filters.salespersonId || null, fromDate: filters.fromDate, toDate: filters.toDate }) })
      notify(c(`${result.calculated} ${result.calculated === 1 ? 'factura recalculada' : 'facturas recalculadas'}${result.settledSkipped ? `, ${result.settledSkipped} ya liquidadas sin tocar` : ''}.`, `${result.calculated} invoices recalculated${result.settledSkipped ? `, ${result.settledSkipped} already settled left untouched` : ''}.`))
      setRefresh((value) => value + 1)
    } catch (cause) { notify(errorMessage(cause), 'error') } finally { setCalculating(false) }
  }

  const openDetail = async (commission: Commission) => {
    try { setDetail(await apiFetch<Commission>(`/api/v1/commissions/${commission.id}`)) } catch (cause) { notify(errorMessage(cause), 'error') }
  }
  const reopen = async (commission: Commission) => {
    try { setDetail(await apiFetch<Commission>(`/api/v1/commissions/${commission.id}/reopen`, { method: 'POST' })); setRefresh((value) => value + 1); notify(c('Liquidación deshecha.', 'Settlement undone.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }
  const pending = (data?.content ?? []).filter((item) => item.status === 'PENDING')
  const toggle = (id: string) => setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])
  const selectedAmount = (data?.content ?? []).filter((item) => selected.includes(item.id)).reduce((sum, item) => sum + item.commissionAmount, 0)

  return <>
    <section className="panel table-panel">
      <div className="panel-heading table-toolbar commission-filters">
        <select aria-label={c('Comercial', 'Salesperson')} value={filters.salespersonId} onChange={(event) => update('salespersonId', event.target.value)}><option value="">{c('Todos los comerciales', 'All salespeople')}</option>{salespeople.map((person) => <option key={person.id} value={person.id}>{person.code} · {person.name}</option>)}</select>
        <input type="date" aria-label={c('Desde', 'From')} value={filters.fromDate} onChange={(event) => update('fromDate', event.target.value)} />
        <input type="date" aria-label={c('Hasta', 'To')} value={filters.toDate} onChange={(event) => update('toDate', event.target.value)} />
        <select aria-label={c('Estado', 'Status')} value={filters.status} onChange={(event) => update('status', event.target.value)}><option value="PENDING">{c('Pendientes', 'Pending')}</option><option value="SETTLED">{c('Liquidadas', 'Settled')}</option><option value="">{c('Todas', 'All')}</option></select>
        <select aria-label={c('Cobro', 'Collection')} value={filters.collected} onChange={(event) => update('collected', event.target.value)}><option value="">{c('Cobradas y sin cobrar', 'Collected or not')}</option><option value="true">{c('Solo cobradas', 'Collected only')}</option><option value="false">{c('Sin cobrar', 'Not collected')}</option></select>
        {canWrite && <button className="button button-secondary" type="button" disabled={calculating || !filters.fromDate || !filters.toDate} onClick={() => void calculate()}><Calculator size={17} />{calculating ? c('Calculando…', 'Calculating…') : c('Recalcular', 'Recalculate')}</button>}
        {canWrite && <button className="button button-primary" type="button" disabled={selected.length === 0} onClick={() => setSettling(true)}><CheckCircle2 size={17} />{c('Liquidar', 'Settle')}{selected.length ? ` (${selected.length})` : ''}</button>}
      </div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {totals && <div className="detail-summary commission-totals">
        <div><small>{c('Facturas', 'Invoices')}</small><strong>{totals.count}</strong></div>
        <div><small>{c('Base', 'Base')}</small><strong>{formatCurrency(totals.baseAmount, 'EUR', locale)}</strong></div>
        <div><small>{c('Comisión', 'Commission')}</small><strong className="detail-total">{formatCurrency(totals.commissionAmount, 'EUR', locale)}</strong></div>
        <div><small>{c('Seleccionado', 'Selected')}</small><strong>{formatCurrency(selectedAmount, 'EUR', locale)}</strong></div>
      </div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table>
          <TableCaption es="Comisiones" en="Commissions" />
          <thead><tr>{canWrite && <th><input type="checkbox" aria-label={c('Seleccionar las pendientes', 'Select pending')} checked={pending.length > 0 && pending.every((item) => selected.includes(item.id))} onChange={(event) => setSelected(event.target.checked ? pending.map((item) => item.id) : [])} /></th>}<th>{c('Factura', 'Invoice')}</th><th>{c('Fecha', 'Date')}</th><th>{c('Cliente', 'Customer')}</th><th>{c('Comercial', 'Salesperson')}</th><th>{c('Cobro', 'Collection')}</th><th className="align-right">{c('Base', 'Base')}</th><th className="align-right">{c('Comisión', 'Commission')}</th><th>{t('field.status')}</th></tr></thead>
          <tbody>{data.content.map((item) => <tr key={item.id} className="clickable-row" onClick={() => void openDetail(item)}>
            {canWrite && <td onClick={(event) => event.stopPropagation()}>{item.status === 'PENDING' && <input type="checkbox" aria-label={c(`Seleccionar ${item.documentNumber}`, `Select ${item.documentNumber}`)} checked={selected.includes(item.id)} onChange={() => toggle(item.id)} />}</td>}
            <td><strong className="document-number">{item.documentNumber}</strong>{item.documentType === 'RECTIFYING_INVOICE' && <small>{c('Rectificativa', 'Corrective')}</small>}</td>
            <td>{item.issueDate ? formatDate(item.issueDate, locale) : '—'}</td>
            <td>{item.customerName ?? '—'}</td>
            <td>{item.salespersonName}</td>
            <td><StatusBadge tone={item.paymentStatus === 'PAID' ? 'success' : 'neutral'}>{item.paymentStatus === 'PAID' ? c('Cobrada', 'Collected') : c('Sin cobrar', 'Not collected')}</StatusBadge></td>
            <td className="align-right">{formatCurrency(item.baseAmount, 'EUR', locale)}</td>
            <td className="align-right"><strong>{formatCurrency(item.commissionAmount, 'EUR', locale)}</strong></td>
            <td><StatusBadge tone={item.status === 'SETTLED' ? 'success' : 'warning'}>{item.status === 'SETTLED' ? c(`Liquidada ${item.settledOn ? formatDate(item.settledOn, locale) : ''}`, `Settled ${item.settledOn ? formatDate(item.settledOn, locale) : ''}`) : c('Pendiente', 'Pending')}</StatusBadge></td>
          </tr>)}</tbody>
        </table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title={c('Sin comisiones', 'No commissions')} description={canWrite ? c('Elige el periodo y pulsa «Recalcular» para calcular las comisiones de las facturas.', 'Choose the period and press "Recalculate" to calculate the commissions of the invoices.') : c('No hay comisiones con estos filtros.', 'There are no commissions with these filters.')} />}
    </section>
    <Modal open={detail !== null} title={detail?.documentNumber ?? ''} description={detail ? `${detail.customerName ?? ''} · ${detail.salespersonName}` : ''} onClose={() => setDetail(null)} size="large">
      {detail && <div className="document-detail">
        <div className="table-scroll detail-lines"><table>
          <TableCaption es="Comisión por línea" en="Commission per line" />
          <thead><tr><th>#</th><th>{c('Línea', 'Line')}</th><th className="align-right">{c('Base', 'Base')}</th><th className="align-right">%</th><th className="align-right">{c('Comisión', 'Commission')}</th><th>{c('Origen', 'Source')}</th></tr></thead>
          <tbody>{detail.lines.map((line) => <tr key={line.order}><td>{line.order}</td><td>{line.description}</td><td className="align-right">{formatCurrency(line.baseAmount, 'EUR', locale)}</td><td className="align-right">{formatNumber(line.percentage, locale, 2)} %</td><td className="align-right"><strong>{formatCurrency(line.commissionAmount, 'EUR', locale)}</strong></td><td>{line.origin === 'RULE' ? c('Regla', 'Rule') : line.origin === 'DEFAULT' ? c('Por defecto del comercial', 'Salesperson default') : c('Sin comisión', 'No commission')}</td></tr>)}</tbody>
        </table></div>
        {detail.settlementNote && <p className="detail-notes">{detail.settlementNote}</p>}
        {canWrite && detail.status === 'SETTLED' && <div className="modal-action-strip"><button type="button" className="button button-secondary" onClick={() => void reopen(detail)}><RotateCcw size={17} />{c('Deshacer la liquidación', 'Undo settlement')}</button></div>}
      </div>}
    </Modal>
    <Modal open={settling} title={c('Liquidar comisiones', 'Settle commissions')} description={c(`${selected.length} ${selected.length === 1 ? 'comisión' : 'comisiones'} por ${formatCurrency(selectedAmount, 'EUR', locale)}. Ya no se recalcularán.`, `${selected.length} commissions for ${formatCurrency(selectedAmount, 'EUR', locale)}. They will no longer be recalculated.`)} onClose={() => setSettling(false)}>
      {settling && <SettleForm ids={selected} onCancel={() => setSettling(false)} onDone={() => { setSettling(false); setRefresh((value) => value + 1); notify(c('Comisiones liquidadas.', 'Commissions settled.')) }} />}
    </Modal>
  </>
}

function SettleForm({ ids, onCancel, onDone }: { ids: string[]; onCancel: () => void; onDone: () => void }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ settledOn: localIsoDate(), note: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { await apiFetch('/api/v1/commissions/settle', { method: 'POST', body: JSON.stringify({ ids, settledOn: form.settledOn, note: form.note.trim() || null }) }); onDone() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Fecha de liquidación', 'Settlement date')} htmlFor="settle-date" name="settledOn" required><input id="settle-date" type="date" value={form.settledOn} onChange={(event) => setForm({ ...form, settledOn: event.target.value })} required /></Field>
    <Field label={c('Nota', 'Note')} htmlFor="settle-note" name="note" wide><input id="settle-note" value={form.note} maxLength={300} onChange={(event) => setForm({ ...form, note: event.target.value })} placeholder={c('Pagado en nómina de octubre…', 'Paid with the October payroll…')} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={c('Liquidar', 'Settle')} /></FormErrors></form>
}

function RulesTab({ salespeople, canWrite }: { salespeople: Salesperson[]; canWrite: boolean }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [salespersonId, setSalespersonId] = useState('')
  const [rules, setRules] = useState<Rule[] | null>(null)
  const [editing, setEditing] = useState<Rule | 'new' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const current = salespersonId || salespeople[0]?.id || ''
  const person = salespeople.find((item) => item.id === current)

  useEffect(() => {
    if (!current) { setRules([]); return }
    let active = true
    setRules(null)
    apiFetch<Rule[]>(`/api/v1/commission-rules?salespersonId=${current}`).then((value) => { if (active) setRules(value) }).catch(() => { if (active) setRules([]) })
    return () => { active = false }
  }, [current, refresh])

  const range = (rule: Rule) => rule.amountFrom === null && rule.amountTo === null ? c('Cualquier importe', 'Any amount')
    : `${rule.amountFrom === null ? '0' : formatNumber(rule.amountFrom, locale, 2)} – ${rule.amountTo === null ? '∞' : formatNumber(rule.amountTo, locale, 2)} €`

  return <section className="panel table-panel">
    <div className="panel-heading table-toolbar">
      <select aria-label={c('Comercial de las reglas', 'Salesperson of the rules')} value={current} onChange={(event) => setSalespersonId(event.target.value)}>{salespeople.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select>
      {canWrite && current && <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nueva regla', 'New rule')}</button>}
    </div>
    {person && <p className="detail-notes">{person.commissionPercentage === null ? c('Este comercial no tiene comisión por defecto: las líneas sin regla no comisionan.', 'This salesperson has no default commission: lines without a rule earn nothing.') : c(`Sin regla que encaje, se aplica su comisión por defecto: ${formatNumber(person.commissionPercentage, locale, 2)} %. Gana la regla más concreta: artículo, luego grupo, luego general, y el tramo más estrecho.`, `Without a matching rule, the default commission applies: ${formatNumber(person.commissionPercentage, locale, 2)} %. The most specific rule wins: item, then group, then general, and the narrowest range.`)}</p>}
    {salespeople.length === 0 ? <EmptyState title={c('No hay comerciales', 'There are no salespeople')} description={c('Da de alta los comerciales en Clientes › Comerciales.', 'Create salespeople in Customers › Salespeople.')} />
      : rules === null ? <LoadingState /> : rules.length === 0 ? <EmptyState title={c('Sin reglas', 'No rules')} description={c('Con su comisión por defecto basta si cobra lo mismo por todo.', 'Its default commission is enough if it earns the same on everything.')} /> : <div className="table-scroll"><table>
        <TableCaption es="Reglas de comisión" en="Commission rules" />
        <thead><tr><th>{c('Se aplica a', 'Applies to')}</th><th>{c('Importe de la línea', 'Line amount')}</th><th className="align-right">%</th><th>{t('field.status')}</th>{canWrite && <th><span className="sr-only">{t('common.actions')}</span></th>}</tr></thead>
        <tbody>{rules.map((rule) => <tr key={rule.id}>
          <td><strong>{rule.productLabel ?? rule.productGroupLabel ?? c('Todas las líneas', 'Every line')}</strong><small>{rule.productId ? c('Artículo', 'Item') : rule.productGroupId ? c('Grupo de artículos', 'Item group') : c('General', 'General')}</small></td>
          <td>{range(rule)}</td>
          <td className="align-right"><strong>{formatNumber(rule.percentage, locale, 2)} %</strong></td>
          <td><StatusBadge tone={rule.active ? 'success' : 'neutral'}>{rule.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
          {canWrite && <td><button className="icon-button" type="button" onClick={() => setEditing(rule)} aria-label={c('Editar regla', 'Edit rule')}><Pencil size={16} /></button></td>}
        </tr>)}</tbody>
      </table></div>}
    <Modal open={editing !== null} title={editing === 'new' ? c('Nueva regla de comisión', 'New commission rule') : c('Editar regla', 'Edit rule')} description={person ? `${person.code} · ${person.name}` : ''} onClose={() => setEditing(null)}>
      {editing && <RuleForm salespersonId={current} rule={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); setRefresh((value) => value + 1); notify(c('Regla guardada.', 'Rule saved.')) }} />}
    </Modal>
  </section>
}

function RuleForm({ salespersonId, rule, onCancel, onSaved }: { salespersonId: string; rule: Rule | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({
    target: rule?.productId ? 'PRODUCT' : rule?.productGroupId ? 'GROUP' : 'ALL',
    productId: rule?.productId ?? '', productGroupId: rule?.productGroupId ?? '',
    amountFrom: rule?.amountFrom === null || rule?.amountFrom === undefined ? '' : String(rule.amountFrom),
    amountTo: rule?.amountTo === null || rule?.amountTo === undefined ? '' : String(rule.amountTo),
    percentage: rule ? String(rule.percentage) : '', active: rule?.active ?? true,
  })
  const [products, setProducts] = useState<Product[]>([])
  const [groups, setGroups] = useState<ProductGroupOption[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  useEffect(() => {
    apiFetch<PageResponse<Product>>('/api/v1/products?size=200&sort=name,asc').then((page) => setProducts(page.content)).catch(() => setProducts([]))
    apiFetch<PageResponse<ProductGroupOption>>('/api/v1/product-groups?size=200').then((page) => setGroups(page.content)).catch(() => setGroups([]))
  }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = {
      salespersonId, productId: form.target === 'PRODUCT' ? form.productId || null : null,
      productGroupId: form.target === 'GROUP' ? form.productGroupId || null : null,
      amountFrom: form.amountFrom === '' ? null : Number(form.amountFrom), amountTo: form.amountTo === '' ? null : Number(form.amountTo),
      percentage: Number(form.percentage), active: form.active,
    }
    try { await apiFetch(rule ? `/api/v1/commission-rules/${rule.id}` : '/api/v1/commission-rules', { method: rule ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Se aplica a', 'Applies to')} htmlFor="rule-target" name="target"><select id="rule-target" value={form.target} onChange={(event) => update('target', event.target.value)}><option value="ALL">{c('Todas las líneas', 'Every line')}</option><option value="PRODUCT">{c('Un artículo', 'One item')}</option><option value="GROUP">{c('Un grupo de artículos', 'One item group')}</option></select></Field>
    {form.target === 'PRODUCT' && <Field label={c('Artículo', 'Item')} htmlFor="rule-product" name="productId" required><select id="rule-product" value={form.productId} onChange={(event) => update('productId', event.target.value)} required><option value="">{c('Elige un artículo', 'Choose an item')}</option>{products.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></Field>}
    {form.target === 'GROUP' && <Field label={c('Grupo de artículos', 'Item group')} htmlFor="rule-group" name="productGroupId" required><select id="rule-group" value={form.productGroupId} onChange={(event) => update('productGroupId', event.target.value)} required><option value="">{c('Elige un grupo', 'Choose a group')}</option>{groups.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></Field>}
    <Field label={c('Importe de línea desde', 'Line amount from')} htmlFor="rule-from" name="amountFrom" hint={c('Vacío: sin mínimo.', 'Empty: no minimum.')}><input id="rule-from" type="number" min="0" step="0.01" value={form.amountFrom} onChange={(event) => update('amountFrom', event.target.value)} /></Field>
    <Field label={c('Hasta', 'To')} htmlFor="rule-to" name="amountTo" hint={c('Vacío: sin máximo.', 'Empty: no maximum.')}><input id="rule-to" type="number" min="0" step="0.01" value={form.amountTo} onChange={(event) => update('amountTo', event.target.value)} /></Field>
    <Field label={c('Comisión (%)', 'Commission (%)')} htmlFor="rule-percentage" name="percentage" required><input id="rule-percentage" type="number" min="0" max="100" step="0.01" value={form.percentage} onChange={(event) => update('percentage', event.target.value)} required /></Field>
    <Field label={t('field.status')} htmlFor="rule-active" name="active"><label className="switch-row" htmlFor="rule-active"><input id="rule-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{c('Se aplica al recalcular', 'Applied when recalculating')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={rule ? t('common.save') : c('Crear regla', 'Create rule')} /></FormErrors></form>
}
