import { BriefcaseBusiness, FileUp, Pencil, Plus, Settings2, Users } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { EmptyState, LoadingState } from '../components/DataState'
import { ContactDetailsFields } from '../components/ContactDetailsFields'
import { CustomerCatalogTab } from '../components/customers/CustomerCatalogTab'
import { CustomerFile } from '../components/customers/CustomerFile'
import { SalespeopleTab } from '../components/customers/SalespeopleTab'
import { Field, FormActions, FormErrors, useFormErrors } from '../components/Form'
import { Modal } from '../components/Modal'
import { MasterDataImportModal } from '../components/MasterDataImportModal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { apiFetch, errorMessage } from '../lib/api'
import { catalogItemName, salespersonName, selectableItems } from '../lib/customers'
import { formatCurrency } from '../lib/format'
import { riskPolicyKey, taxIdentificationTypeKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import type { Customer, CustomerCatalogItem, CustomerCatalogKind, CustomerInput, PageResponse, PaymentMethod, RiskPolicy, Salesperson, TaxIdentificationType } from '../types/api'
import { TableCaption } from '../components/TableCaption'

type Tab = 'customers' | 'tables' | 'salespeople'

interface TariffOption { id: string; code: string; name: string; active: boolean }

export function CustomersPage() {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { hasPermission } = useAuth()
  const canWrite = hasPermission('customers:write')
  const [tab, setTab] = useState<Tab>('customers')
  const [catalog, setCatalog] = useState<CustomerCatalogItem[]>([])
  const [salespeople, setSalespeople] = useState<Salesperson[]>([])
  const [setupVersion, setSetupVersion] = useState(0)
  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [importing, setImporting] = useState(false)

  // Las tablas de clasificación y los comerciales se usan en la lista, en la ficha y en el formulario.
  useEffect(() => {
    apiFetch<CustomerCatalogItem[]>('/api/v1/customer-catalog').then(setCatalog).catch(() => setCatalog([]))
    apiFetch<Salesperson[]>('/api/v1/salespeople').then(setSalespeople).catch(() => setSalespeople([]))
  }, [setupVersion])
  const setupChanged = () => setSetupVersion((value) => value + 1)

  return <div className="page-stack">
    <PageHeader eyebrow={t('masterData.eyebrow')} title={t('customers.title')} description={t('customers.description')} icon={Users} actions={canWrite && tab === 'customers' ? <>
      <button className="button button-secondary" type="button" onClick={() => setImporting(true)}><FileUp size={17} />{c('Importar', 'Import')}</button>
      <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{t('customers.new')}</button>
    </> : undefined} />
    <nav className="workspace-tabs" aria-label={c('Áreas de clientes', 'Customer areas')}>
      <button type="button" className={tab === 'customers' ? 'active' : ''} onClick={() => setTab('customers')}><Users size={15} />{c('Clientes', 'Customers')}</button>
      <button type="button" className={tab === 'tables' ? 'active' : ''} onClick={() => setTab('tables')}><Settings2 size={15} />{c('Clasificación', 'Classification')}</button>
      <button type="button" className={tab === 'salespeople' ? 'active' : ''} onClick={() => setTab('salespeople')}><BriefcaseBusiness size={15} />{c('Comerciales', 'Salespeople')}</button>
    </nav>
    {tab === 'customers' && <CustomersList catalog={catalog} salespeople={salespeople} canWrite={canWrite} editing={editing} setEditing={setEditing} importing={importing} setImporting={setImporting} />}
    {tab === 'tables' && <CustomerCatalogTab items={catalog} canWrite={canWrite} onChanged={setupChanged} />}
    {tab === 'salespeople' && <SalespeopleTab people={salespeople} canWrite={canWrite} onChanged={setupChanged} />}
  </div>
}

function CustomersList({ catalog, salespeople, canWrite, editing, setEditing, importing, setImporting }: {
  catalog: CustomerCatalogItem[]; salespeople: Salesperson[]; canWrite: boolean
  editing: Customer | 'new' | null; setEditing: (value: Customer | 'new' | null) => void
  importing: boolean; setImporting: (value: boolean) => void
}) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [data, setData] = useState<PageResponse<Customer> | null>(null)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query)
  const [groupId, setGroupId] = useState('')
  const [salespersonId, setSalespersonId] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<Customer | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const groups = catalog.filter((item) => item.kind === 'GROUP')
  const filtered = Boolean(query || groupId || salespersonId || status)

  useEffect(() => setPage(0), [debouncedQuery, groupId, salespersonId, status])
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'legalName,asc' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    if (groupId) params.set('groupId', groupId)
    if (salespersonId) params.set('salespersonId', salespersonId)
    if (status) params.set('active', status)
    apiFetch<PageResponse<Customer>>(`/api/v1/customers?${params}`)
      .then((response) => { if (active) setData(response) })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [debouncedQuery, groupId, salespersonId, status, page, refresh])

  const saved = (customer: Customer) => {
    setEditing(null)
    if (selected) setSelected(customer)
    setRefresh((value) => value + 1)
    notify(t('customers.saved'))
  }

  return <>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={t('customers.search')}>
        <select aria-label={c('Filtrar por grupo', 'Filter by group')} value={groupId} onChange={(event) => setGroupId(event.target.value)}><option value="">{c('Todos los grupos', 'All groups')}</option>{groups.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
        <select aria-label={c('Filtrar por comercial', 'Filter by salesperson')} value={salespersonId} onChange={(event) => setSalespersonId(event.target.value)}><option value="">{c('Todos los comerciales', 'All salespeople')}</option>{salespeople.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select>
        <select aria-label={t('sales.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value)}><option value="">{c('Activos y de baja', 'Active and discharged')}</option><option value="true">{c('Solo activos', 'Active only')}</option><option value="false">{c('Solo de baja', 'Discharged only')}</option></select>
      </TableToolbar>
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table><TableCaption es="Clientes" en="Customers" /><thead><tr><th>{t('field.code')}</th><th>{t('customers.customer')}</th><th>{t('field.taxId')}</th><th>{t('customers.contact')}</th><th>{c('Grupo', 'Group')}</th><th>{c('Comercial', 'Salesperson')}</th><th>{t('customers.risk')}</th><th>{t('field.status')}</th>{canWrite && <th><span className="sr-only">{t('common.actions')}</span></th>}</tr></thead><tbody>{data.content.map((customer) => <tr key={customer.id} className="clickable-row" onClick={() => setSelected(customer)}>
          <td><span className="code-cell">{customer.code}</span></td>
          <td><strong>{customer.legalName}</strong>{customer.tradeName && <small>{customer.tradeName}</small>}</td>
          <td>{customer.taxId || '—'}</td>
          <td>{customer.email || customer.phone || customer.classification?.mobile || '—'}</td>
          <td>{catalogItemName(catalog, customer.classification?.groupId)}</td>
          <td>{salespersonName(salespeople, customer.classification?.salespersonId)}</td>
          <td>{formatCurrency(customer.creditLimit, 'EUR', locale)}</td>
          <td><StatusBadge tone={customer.active ? 'success' : 'neutral'}>{customer.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
          {canWrite && <td><button className="icon-button" type="button" onClick={(event) => { event.stopPropagation(); setEditing(customer) }} aria-label={t('customers.editAria', { name: customer.legalName })}><Pencil size={16} /></button></td>}
        </tr>)}</tbody></table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title={t('customers.empty')} description={filtered ? t('common.noResults') : t('customers.emptyDescription')} action={!filtered && canWrite && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}>{t('customers.create')}</button>} />}
    </section>
    <Modal open={selected !== null && editing === null} title={selected?.legalName ?? ''} description={selected ? `${selected.code}${selected.tradeName ? ` · ${selected.tradeName}` : ''}` : ''} onClose={() => setSelected(null)} size="large">
      {selected && <CustomerFile key={selected.id} customer={selected} catalog={catalog} salespeople={salespeople} canWrite={canWrite} actions={canWrite && <div className="modal-action-strip"><button type="button" className="button button-primary" onClick={() => setEditing(selected)}><Pencil size={17} />{c('Editar ficha', 'Edit record')}</button></div>} />}
    </Modal>
    <Modal open={editing !== null} title={editing === 'new' ? t('customers.new') : t('customers.edit')} description={t('customers.modalDescription')} onClose={() => setEditing(null)} size="large">
      {editing && <CustomerForm key={editing === 'new' ? 'new' : editing.id} customer={editing === 'new' ? null : editing} catalog={catalog} salespeople={salespeople} onCancel={() => setEditing(null)} onSaved={saved} />}
    </Modal>
    <MasterDataImportModal open={importing} entityName={c('clientes', 'customers')} basePath="/api/v1/customers" onClose={() => setImporting(false)} onImported={(count) => { setRefresh((value) => value + 1); notify(c(`${count} clientes importados.`, `${count} customers imported.`)) }} />
  </>
}

/** Tarifas y formas de pago vienen de otros módulos; si el usuario no puede leerlas, se conserva lo que haya. */
function useOptions() {
  const { hasPermission } = useAuth()
  const [tariffs, setTariffs] = useState<TariffOption[] | null>(null)
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[] | null>(null)
  const canPricing = hasPermission('pricing:read')
  const canFinance = hasPermission('finance:read')
  useEffect(() => {
    if (canPricing) apiFetch<PageResponse<TariffOption>>('/api/v1/tariffs?page=0&size=200').then((response) => setTariffs(response.content)).catch(() => setTariffs(null))
    if (canFinance) apiFetch<PaymentMethod[]>('/api/v1/payment-methods').then(setPaymentMethods).catch(() => setPaymentMethods(null))
  }, [canPricing, canFinance])
  return { tariffs, paymentMethods }
}

function CustomerForm({ customer, catalog, salespeople, onCancel, onSaved }: { customer: Customer | null; catalog: CustomerCatalogItem[]; salespeople: Salesperson[]; onCancel: () => void; onSaved: (customer: Customer) => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const classification = customer?.classification
  const [form, setForm] = useState({
    code: customer?.code ?? '', legalName: customer?.legalName ?? '', tradeName: customer?.tradeName ?? '',
    taxId: customer?.taxId ?? '', taxIdentificationType: customer?.taxIdentificationType ?? 'NIF',
    taxCountryCode: customer?.taxCountryCode ?? 'ES', phone: customer?.phone ?? '', email: customer?.email ?? '',
    observations: customer?.observations ?? '', creditLimit: String(customer?.creditLimit ?? 0),
    riskWarningThreshold: String(customer?.riskWarningThreshold ?? 0), riskPolicy: customer?.riskPolicy ?? 'WARN' as RiskPolicy,
    active: customer?.active ?? true,
    priceListId: customer?.priceListId ?? '', defaultPaymentMethodId: customer?.defaultPaymentMethodId ?? '', supplierCode: customer?.supplierCode ?? '',
    groupId: classification?.groupId ?? '', typeId: classification?.typeId ?? '', salespersonId: classification?.salespersonId ?? '',
    deliveryMethodId: classification?.deliveryMethodId ?? '', inactiveReasonId: classification?.inactiveReasonId ?? '',
    mobile: classification?.mobile ?? '', accountingAccount: classification?.accountingAccount ?? '',
  })
  const [details, setDetails] = useState(customer?.details ?? {})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const { tariffs, paymentMethods } = useOptions()
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))
  const options = (kind: CustomerCatalogKind, currentId: string) => selectableItems(catalog.filter((item) => item.kind === kind), currentId)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    // Tarifa, forma de pago y código de proveedor se envían siempre: si faltaran, el servidor los borraría.
    const payload: CustomerInput = { details,
      code: form.code.trim(), legalName: form.legalName.trim(), tradeName: form.tradeName.trim() || null,
      taxId: form.taxId.trim() || null,
      taxIdentificationType: form.taxId.trim() ? form.taxIdentificationType : null,
      taxCountryCode: form.taxId.trim() ? (form.taxCountryCode.trim().toUpperCase() || null) : null,
      phone: form.phone.trim() || null, email: form.email.trim() || null,
      observations: form.observations.trim() || null, creditLimit: Number(form.creditLimit || 0),
      riskWarningThreshold: Number(form.riskWarningThreshold || 0), riskPolicy: form.riskPolicy, active: form.active,
      priceListId: form.priceListId || null, defaultPaymentMethodId: form.defaultPaymentMethodId || null,
      supplierCode: form.supplierCode.trim() || null,
      classification: {
        groupId: form.groupId || null, typeId: form.typeId || null, salespersonId: form.salespersonId || null,
        deliveryMethodId: form.deliveryMethodId || null, inactiveReasonId: form.active ? null : form.inactiveReasonId || null,
        mobile: form.mobile.trim() || null, accountingAccount: form.accountingAccount.trim() || null,
      },
    }
    try {
      onSaved(await apiFetch<Customer>(customer ? `/api/v1/customers/${customer.id}` : '/api/v1/customers', { method: customer ? 'PUT' : 'POST', body: JSON.stringify(payload) }))
    } catch (cause) {
      setError(formErrors.capture(cause))
    } finally {
      setSaving(false)
    }
  }

  const catalogSelect = (kind: CustomerCatalogKind, name: keyof typeof form, id: string, label: string) => <Field label={label} htmlFor={id} name={`classification.${name}`}><select id={id} value={String(form[name])} onChange={(event) => update(name, event.target.value)}><option value="">{c('Sin asignar', 'Not assigned')}</option>{options(kind, String(form[name])).map((item) => <option key={item.id} value={item.id}>{item.name}{item.active ? '' : c(' (de baja)', ' (discharged)')}</option>)}</select></Field>

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={t('field.code')} htmlFor="customer-code" name="code" required><input id="customer-code" value={form.code} onChange={(event) => update('code', event.target.value)} disabled={Boolean(customer)} maxLength={40} required /></Field>
    <Field label={t('field.legalName')} htmlFor="customer-name" name="legalName" required><input id="customer-name" value={form.legalName} onChange={(event) => update('legalName', event.target.value)} maxLength={180} required /></Field>
    <Field label={t('field.tradeName')} htmlFor="customer-trade" name="tradeName"><input id="customer-trade" value={form.tradeName} onChange={(event) => update('tradeName', event.target.value)} maxLength={180} /></Field>
    <Field label={t('field.taxId')} htmlFor="customer-tax" name="taxId"><input id="customer-tax" value={form.taxId} onChange={(event) => update('taxId', event.target.value)} maxLength={30} /></Field>
    <Field label={t('field.taxIdentificationType')} htmlFor="customer-tax-type" name="taxIdentificationType"><select id="customer-tax-type" value={form.taxIdentificationType} disabled={!form.taxId.trim()} onChange={(event) => update('taxIdentificationType', event.target.value as TaxIdentificationType)}>{(['NIF', 'VAT_NUMBER', 'PASSPORT', 'FOREIGN_OFFICIAL_ID', 'RESIDENCE_CERTIFICATE', 'OTHER_DOCUMENT', 'NOT_REGISTERED'] as TaxIdentificationType[]).map((value) => <option key={value} value={value}>{t(taxIdentificationTypeKey[value])}</option>)}</select></Field>
    <Field label={t('field.taxCountryCode')} htmlFor="customer-tax-country" name="taxCountryCode"><input id="customer-tax-country" value={form.taxCountryCode} disabled={!form.taxId.trim()} onChange={(event) => update('taxCountryCode', event.target.value.toUpperCase())} maxLength={2} placeholder="ES" /></Field>
    <Field label={t('field.phone')} htmlFor="customer-phone" name="phone"><input id="customer-phone" value={form.phone} onChange={(event) => update('phone', event.target.value)} maxLength={40} /></Field>
    <Field label={c('Móvil', 'Mobile')} htmlFor="customer-mobile" name="classification.mobile"><input id="customer-mobile" value={form.mobile} onChange={(event) => update('mobile', event.target.value)} maxLength={40} /></Field>
    <Field label={t('field.email')} htmlFor="customer-email" name="email"><input id="customer-email" type="email" value={form.email} onChange={(event) => update('email', event.target.value)} maxLength={180} /></Field>
    <Field label={t('field.status')} htmlFor="customer-active" name="active"><label className="switch-row" htmlFor="customer-active"><input id="customer-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{t('customers.active')}</span></label></Field>
    {!form.active && catalogSelect('INACTIVE_REASON', 'inactiveReasonId', 'customer-inactive-reason', c('Motivo de baja', 'Discharge reason'))}
    <ContactDetailsFields value={details} onChange={setDetails} prefix="customer" />

    <h3 className="form-section-title">{c('Clasificación comercial', 'Commercial classification')}</h3>
    {catalogSelect('GROUP', 'groupId', 'customer-group', c('Grupo', 'Group'))}
    {catalogSelect('TYPE', 'typeId', 'customer-type', c('Tipo de cliente', 'Customer type'))}
    <Field label={c('Comercial', 'Salesperson')} htmlFor="customer-salesperson" name="classification.salespersonId"><select id="customer-salesperson" value={form.salespersonId} onChange={(event) => update('salespersonId', event.target.value)}><option value="">{c('Sin asignar', 'Not assigned')}</option>{selectableItems(salespeople, form.salespersonId).map((person) => <option key={person.id} value={person.id}>{person.name}{person.active ? '' : c(' (de baja)', ' (discharged)')}</option>)}</select></Field>
    {catalogSelect('DELIVERY_METHOD', 'deliveryMethodId', 'customer-delivery', c('Forma de entrega', 'Delivery method'))}
    <Field label={c('Tarifa', 'Price list')} htmlFor="customer-tariff" name="priceListId"><select id="customer-tariff" value={form.priceListId} disabled={tariffs === null} onChange={(event) => update('priceListId', event.target.value)}><option value="">{c('Tarifa general', 'General price list')}</option>{form.priceListId && !tariffs?.some((item) => item.id === form.priceListId) && <option value={form.priceListId}>{c('Tarifa asignada', 'Assigned price list')}</option>}{(tariffs ?? []).filter((item) => item.active || item.id === form.priceListId).map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></Field>
    <Field label={c('Forma de pago', 'Payment method')} htmlFor="customer-payment" name="defaultPaymentMethodId"><select id="customer-payment" value={form.defaultPaymentMethodId} disabled={paymentMethods === null} onChange={(event) => update('defaultPaymentMethodId', event.target.value)}><option value="">{c('Sin forma de pago fija', 'No fixed payment method')}</option>{form.defaultPaymentMethodId && !paymentMethods?.some((item) => item.id === form.defaultPaymentMethodId) && <option value={form.defaultPaymentMethodId}>{c('Forma de pago asignada', 'Assigned payment method')}</option>}{(paymentMethods ?? []).filter((item) => item.active || item.id === form.defaultPaymentMethodId).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></Field>
    <Field label={c('Cuenta contable', 'Ledger account')} htmlFor="customer-account" name="classification.accountingAccount" hint={c('Si se deja vacía, la contabilidad usa la cuenta general de clientes.', 'If empty, accounting uses the general customer account.')}><input id="customer-account" value={form.accountingAccount} onChange={(event) => update('accountingAccount', event.target.value)} maxLength={20} inputMode="numeric" /></Field>
    <Field label={c('Código como proveedor', 'Supplier code')} htmlFor="customer-supplier-code" name="supplierCode" hint={c('El código con el que este cliente nos tiene dados de alta.', 'The code this customer uses for us.')}><input id="customer-supplier-code" value={form.supplierCode} onChange={(event) => update('supplierCode', event.target.value)} maxLength={60} /></Field>

    <h3 className="form-section-title">{c('Riesgo', 'Risk')}</h3>
    <Field label={t('customers.creditLimit')} htmlFor="customer-credit" name="creditLimit"><input id="customer-credit" type="number" min="0" step="0.01" value={form.creditLimit} onChange={(event) => update('creditLimit', event.target.value)} /></Field>
    <Field label={t('customers.riskWarning')} htmlFor="customer-risk" name="riskWarningThreshold"><input id="customer-risk" type="number" min="0" step="0.01" value={form.riskWarningThreshold} onChange={(event) => update('riskWarningThreshold', event.target.value)} /></Field>
    <Field label={t('customers.riskPolicy')} htmlFor="customer-policy" name="riskPolicy"><select id="customer-policy" value={form.riskPolicy} onChange={(event) => update('riskPolicy', event.target.value)}>{(Object.keys(riskPolicyKey) as RiskPolicy[]).map((policy) => <option key={policy} value={policy}>{t(riskPolicyKey[policy])}</option>)}</select></Field>
    <Field label={t('field.observations')} htmlFor="customer-notes" name="observations" wide><textarea id="customer-notes" rows={3} value={form.observations} onChange={(event) => update('observations', event.target.value)} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={customer ? t('customers.saveChanges') : t('customers.create')} /></FormErrors></form>
}
