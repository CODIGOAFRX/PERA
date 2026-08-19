import { Pencil, Plus, Users } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency } from '../lib/format'
import { riskPolicyKey, taxIdentificationTypeKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import type { Customer, CustomerInput, PageResponse, RiskPolicy, TaxIdentificationType } from '../types/api'

export function CustomersPage() {
  const { locale, t } = useTranslation()
  const [data, setData] = useState<PageResponse<Customer> | null>(null)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [debouncedQuery])
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'legalName,asc' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    apiFetch<PageResponse<Customer>>(`/api/v1/customers?${params}`)
      .then((response) => { if (active) setData(response) })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [debouncedQuery, page, refresh])

  const saved = () => {
    setEditing(null)
    setRefresh((value) => value + 1)
    notify(t('customers.saved'))
  }

  return <div className="page-stack">
    <PageHeader eyebrow={t('masterData.eyebrow')} title={t('customers.title')} description={t('customers.description')} icon={Users} actions={<button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{t('customers.new')}</button>} />
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={t('customers.search')} />
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table><thead><tr><th>{t('field.code')}</th><th>{t('customers.customer')}</th><th>{t('field.taxId')}</th><th>{t('customers.contact')}</th><th>{t('customers.risk')}</th><th>{t('field.status')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead><tbody>{data.content.map((customer) => <tr key={customer.id}><td><span className="code-cell">{customer.code}</span></td><td><strong>{customer.legalName}</strong>{customer.tradeName && <small>{customer.tradeName}</small>}</td><td>{customer.taxId || '—'}</td><td>{customer.email || customer.phone || '—'}</td><td>{formatCurrency(customer.creditLimit, 'EUR', locale)}</td><td><StatusBadge tone={customer.active ? 'success' : 'neutral'}>{customer.active ? t('common.active') : t('common.inactive')}</StatusBadge></td><td><button className="icon-button" type="button" onClick={() => setEditing(customer)} aria-label={t('customers.editAria', { name: customer.legalName })}><Pencil size={16} /></button></td></tr>)}</tbody></table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title={t('customers.empty')} description={query ? t('common.noResults') : t('customers.emptyDescription')} action={!query && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}>{t('customers.create')}</button>} />}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? t('customers.new') : t('customers.edit')} description={t('customers.modalDescription')} onClose={() => setEditing(null)} size="large">
      {editing && <CustomerForm key={editing === 'new' ? 'new' : editing.id} customer={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={saved} />}
    </Modal>
  </div>
}

function CustomerForm({ customer, onCancel, onSaved }: { customer: Customer | null; onCancel: () => void; onSaved: () => void }) {
  const { t } = useTranslation()
  const [form, setForm] = useState({
    code: customer?.code ?? '', legalName: customer?.legalName ?? '', tradeName: customer?.tradeName ?? '',
    taxId: customer?.taxId ?? '', taxIdentificationType: customer?.taxIdentificationType ?? 'NIF',
    taxCountryCode: customer?.taxCountryCode ?? 'ES', phone: customer?.phone ?? '', email: customer?.email ?? '',
    observations: customer?.observations ?? '', creditLimit: String(customer?.creditLimit ?? 0),
    riskWarningThreshold: String(customer?.riskWarningThreshold ?? 0), riskPolicy: customer?.riskPolicy ?? 'WARN' as RiskPolicy,
    active: customer?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    const payload: CustomerInput = {
      code: form.code.trim(), legalName: form.legalName.trim(), tradeName: form.tradeName.trim() || null,
      taxId: form.taxId.trim() || null,
      taxIdentificationType: form.taxId.trim() ? form.taxIdentificationType : null,
      taxCountryCode: form.taxId.trim() ? (form.taxCountryCode.trim().toUpperCase() || null) : null,
      phone: form.phone.trim() || null, email: form.email.trim() || null,
      observations: form.observations.trim() || null, creditLimit: Number(form.creditLimit || 0),
      riskWarningThreshold: Number(form.riskWarningThreshold || 0), riskPolicy: form.riskPolicy, active: form.active,
    }
    try {
      await apiFetch<Customer>(customer ? `/api/v1/customers/${customer.id}` : '/api/v1/customers', { method: customer ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      onSaved()
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSaving(false)
    }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={t('field.code')} htmlFor="customer-code" required><input id="customer-code" value={form.code} onChange={(event) => update('code', event.target.value)} disabled={Boolean(customer)} maxLength={40} required /></Field>
    <Field label={t('field.legalName')} htmlFor="customer-name" required><input id="customer-name" value={form.legalName} onChange={(event) => update('legalName', event.target.value)} maxLength={180} required /></Field>
    <Field label={t('field.tradeName')} htmlFor="customer-trade"><input id="customer-trade" value={form.tradeName} onChange={(event) => update('tradeName', event.target.value)} maxLength={180} /></Field>
    <Field label={t('field.taxId')} htmlFor="customer-tax"><input id="customer-tax" value={form.taxId} onChange={(event) => update('taxId', event.target.value)} maxLength={30} /></Field>
    <Field label={t('field.taxIdentificationType')} htmlFor="customer-tax-type"><select id="customer-tax-type" value={form.taxIdentificationType} disabled={!form.taxId.trim()} onChange={(event) => update('taxIdentificationType', event.target.value as TaxIdentificationType)}>{(['NIF', 'VAT_NUMBER', 'PASSPORT', 'FOREIGN_OFFICIAL_ID', 'RESIDENCE_CERTIFICATE', 'OTHER_DOCUMENT', 'NOT_REGISTERED'] as TaxIdentificationType[]).map((value) => <option key={value} value={value}>{t(taxIdentificationTypeKey[value])}</option>)}</select></Field>
    <Field label={t('field.taxCountryCode')} htmlFor="customer-tax-country"><input id="customer-tax-country" value={form.taxCountryCode} disabled={!form.taxId.trim()} onChange={(event) => update('taxCountryCode', event.target.value.toUpperCase())} maxLength={2} placeholder="ES" /></Field>
    <Field label={t('field.phone')} htmlFor="customer-phone"><input id="customer-phone" value={form.phone} onChange={(event) => update('phone', event.target.value)} maxLength={40} /></Field>
    <Field label={t('field.email')} htmlFor="customer-email"><input id="customer-email" type="email" value={form.email} onChange={(event) => update('email', event.target.value)} maxLength={180} /></Field>
    <Field label={t('customers.creditLimit')} htmlFor="customer-credit"><input id="customer-credit" type="number" min="0" step="0.01" value={form.creditLimit} onChange={(event) => update('creditLimit', event.target.value)} /></Field>
    <Field label={t('customers.riskWarning')} htmlFor="customer-risk"><input id="customer-risk" type="number" min="0" step="0.01" value={form.riskWarningThreshold} onChange={(event) => update('riskWarningThreshold', event.target.value)} /></Field>
    <Field label={t('customers.riskPolicy')} htmlFor="customer-policy"><select id="customer-policy" value={form.riskPolicy} onChange={(event) => update('riskPolicy', event.target.value)}>{(Object.keys(riskPolicyKey) as RiskPolicy[]).map((policy) => <option key={policy} value={policy}>{t(riskPolicyKey[policy])}</option>)}</select></Field>
    <Field label={t('field.status')} htmlFor="customer-active"><label className="switch-row" htmlFor="customer-active"><input id="customer-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{t('customers.active')}</span></label></Field>
    <Field label={t('field.observations')} htmlFor="customer-notes" wide><textarea id="customer-notes" rows={3} value={form.observations} onChange={(event) => update('observations', event.target.value)} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={customer ? t('customers.saveChanges') : t('customers.create')} /></form>
}
