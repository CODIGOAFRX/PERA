import { ArrowRight, CheckCircle2, FileText, Plus, ReceiptText, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge, type BadgeTone } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { apiFetch, errorMessage } from '../lib/api'
import { calculateDocumentPreview } from '../lib/document'
import { formatCurrency, formatDate, formatNumber } from '../lib/format'
import { documentStatusKey, documentTypeKey, paymentStatusKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import type { CommercialDocument, CreateDocumentInput, CurrencyDefinition, Customer, DocumentStatus, DocumentType, PageResponse, PaymentMethod, Product } from '../types/api'

const documentTypes = Object.keys(documentTypeKey) as DocumentType[]
const documentStatuses = Object.keys(documentStatusKey) as DocumentStatus[]

export function SalesPage() {
  const { locale, t } = useTranslation()
  const [data, setData] = useState<PageResponse<CommercialDocument> | null>(null)
  const [page, setPage] = useState(0)
  const [type, setType] = useState<DocumentType | ''>('')
  const [status, setStatus] = useState<DocumentStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<CommercialDocument | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [type, status])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'issueDate,desc' })
    if (type) params.set('type', type)
    if (status) params.set('status', status)
    apiFetch<PageResponse<CommercialDocument>>(`/api/v1/documents?${params}`).then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, type, status, refresh])

  const runAction = async (action: 'convert' | 'paid', document: CommercialDocument) => {
    try {
      if (action === 'convert') await apiFetch(`/api/v1/documents/${document.id}/convert`, { method: 'POST' })
      else await apiFetch(`/api/v1/documents/${document.id}/payment-status`, { method: 'PATCH', body: JSON.stringify({ status: 'PAID' }) })
      notify(action === 'convert' ? t('sales.converted') : t('sales.markedPaid'))
      setSelected(null); setRefresh((value) => value + 1)
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <div className="page-stack">
    <PageHeader eyebrow={t('sales.eyebrow')} title={t('sales.title')} description={t('sales.description')} icon={FileText} actions={<button className="button button-primary" type="button" onClick={() => setCreating(true)}><Plus size={17} />{t('sales.newDocument')}</button>} />
    <section className="panel table-panel">
      <TableToolbar value="" onChange={() => undefined} placeholder={t('sales.searchDocuments')} hideSearch>
        <select aria-label={t('sales.filterType')} value={type} onChange={(event) => setType(event.target.value as DocumentType | '')}><option value="">{t('sales.allTypes')}</option>{documentTypes.map((item) => <option key={item} value={item}>{t(documentTypeKey[item])}</option>)}</select>
        <select aria-label={t('sales.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value as DocumentStatus | '')}><option value="">{t('sales.allStatuses')}</option>{documentStatuses.map((item) => <option key={item} value={item}>{t(documentStatusKey[item])}</option>)}</select>
      </TableToolbar>
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <><div className="table-scroll"><table><thead><tr><th>{t('sales.number')}</th><th>{t('sales.type')}</th><th>{t('sales.customer')}</th><th>{t('sales.date')}</th><th>{t('sales.status')}</th><th>{t('sales.payment')}</th><th className="align-right">{t('sales.total')}</th></tr></thead><tbody>{data.content.map((document) => <tr key={document.id} className="clickable-row" onClick={() => setSelected(document)}><td><strong className="document-number">{document.number}</strong></td><td>{t(documentTypeKey[document.type])}</td><td><strong>{document.customerName}</strong><small>{document.customerCode}</small></td><td>{formatDate(document.issueDate, locale)}</td><td><StatusBadge tone={statusTone(document.status)}>{t(documentStatusKey[document.status])}</StatusBadge></td><td><StatusBadge tone={paymentTone(document.paymentStatus)}>{t(paymentStatusKey[document.paymentStatus])}</StatusBadge></td><td className="align-right"><strong>{formatCurrency(document.totalAmount, document.currency, locale)}</strong></td></tr>)}</tbody></table></div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title={t('sales.empty')} description={t('sales.emptyDescription')} action={<button className="button button-secondary" type="button" onClick={() => setCreating(true)}>{t('sales.createDocument')}</button>} />}
    </section>

    <Modal open={creating} title={t('sales.newDocument')} description={t('sales.newDescription')} onClose={() => setCreating(false)} size="large"><CreateDocumentForm onCancel={() => setCreating(false)} onSaved={() => { setCreating(false); setRefresh((value) => value + 1); notify(t('sales.created')) }} /></Modal>
    <Modal open={selected !== null} title={selected?.number || t('sales.document')} description={selected ? `${t(documentTypeKey[selected.type])} · ${selected.customerName}` : ''} onClose={() => setSelected(null)} size="large">{selected && <DocumentDetail document={selected} onAction={runAction} />}</Modal>
  </div>
}

function CreateDocumentForm({ onCancel, onSaved }: { onCancel: () => void; onSaved: () => void }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [customers, setCustomers] = useState<Customer[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([])
  const [currencies, setCurrencies] = useState<CurrencyDefinition[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ type: 'QUOTE' as DocumentType, customerId: '', issueDate: new Date().toISOString().slice(0, 10), dueDate: '', currency: 'EUR', paymentMethodId: '', notes: '', confirm: true })
  const [lines, setLines] = useState([{ productId: '', productCode: '', description: '', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21' }])

  useEffect(() => {
    Promise.all([
      apiFetch<PageResponse<Customer>>('/api/v1/customers?size=100&sort=legalName,asc'),
      apiFetch<PageResponse<Product>>('/api/v1/products?size=100&sort=name,asc'),
      apiFetch<PaymentMethod[]>('/api/v1/payment-methods'),
      apiFetch<CurrencyDefinition[]>('/api/v1/currencies').catch(() => []),
    ]).then(([customerPage, productPage, methods, currencyList]) => {
      setCustomers(customerPage.content.filter((item) => item.active))
      setProducts(productPage.content.filter((item) => item.active))
      setPaymentMethods(methods.filter((item) => item.active))
      const activeCurrencies = currencyList.filter((item) => item.active)
      setCurrencies(activeCurrencies)
      const preferredCurrency = activeCurrencies.find((item) => item.baseCurrency) ?? activeCurrencies[0]
      setForm((current) => ({ ...current, currency: preferredCurrency?.code ?? 'EUR' }))
    }).catch((cause) => setError(errorMessage(cause))).finally(() => setLoadingOptions(false))
  }, [])

  const updateLine = (index: number, name: string, value: string) => setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, [name]: value } : line))
  const chooseProduct = (index: number, productId: string) => {
    const product = products.find((item) => item.id === productId)
    setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, productId, productCode: product?.code || '', description: product?.name || '', unitPrice: String(product?.basePrice ?? 0), taxPercentage: String(product?.taxRate ?? 0) } : line))
  }
  const totals = useMemo(() => calculateDocumentPreview(lines), [lines])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const customer = customers.find((item) => item.id === form.customerId)
    if (!customer) { setError(t('sales.selectCustomer')); setSaving(false); return }
    if (lines.some((line) => !line.description.trim() || Number(line.quantity) <= 0)) { setError(t('sales.reviewLines')); setSaving(false); return }
    const payload: CreateDocumentInput = {
      type: form.type, customerId: customer.id, customerCode: customer.code, customerName: customer.legalName,
      issueDate: form.issueDate, dueDate: form.dueDate || null, currency: form.currency, paymentMethodId: form.paymentMethodId || null,
      notes: form.notes.trim() || null, confirm: form.confirm,
      lines: lines.map((line) => ({ productId: line.productId || null, productCode: line.productCode || null, description: line.description.trim(), quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), discountPercentage: Number(line.discountPercentage), taxPercentage: Number(line.taxPercentage) })),
    }
    try { await apiFetch<CommercialDocument>('/api/v1/documents', { method: 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  if (loadingOptions) return <LoadingState label={t('sales.loadingOptions')} />
  return <form onSubmit={submit}>
    <div className="form-grid document-header-form">
      <Field label={t('sales.type')} htmlFor="document-type" required><select id="document-type" value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value as DocumentType })}>{documentTypes.map((item) => <option key={item} value={item}>{t(documentTypeKey[item])}</option>)}</select></Field>
      <Field label={t('sales.customer')} htmlFor="document-customer" required><select id="document-customer" value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })} required><option value="">{t('sales.selectCustomer')}</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.code} · {customer.legalName}</option>)}</select></Field>
      <Field label={t('sales.issueDate')} htmlFor="document-date" required><input id="document-date" type="date" value={form.issueDate} onChange={(event) => setForm({ ...form, issueDate: event.target.value })} required /></Field>
      <Field label={t('sales.dueDate')} htmlFor="document-due"><input id="document-due" type="date" value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} /></Field>
      <Field label={c('Moneda', 'Currency')} htmlFor="document-currency" required><select id="document-currency" value={form.currency} onChange={(event) => setForm({ ...form, currency: event.target.value })}>{currencies.length ? currencies.map((currency) => <option key={currency.code} value={currency.code}>{currency.code} · {currency.name}</option>) : <option value="EUR">EUR</option>}</select></Field>
      <Field label={t('sales.paymentMethod')} htmlFor="document-payment"><select id="document-payment" value={form.paymentMethodId} onChange={(event) => setForm({ ...form, paymentMethodId: event.target.value })}><option value="">{t('sales.noPaymentMethod')}</option>{paymentMethods.map((method) => <option key={method.id} value={method.id}>{method.code} · {method.name}</option>)}</select></Field>
      <Field label={t('sales.initialStatus')} htmlFor="document-confirm"><label className="switch-row" htmlFor="document-confirm"><input id="document-confirm" type="checkbox" checked={form.confirm} onChange={(event) => setForm({ ...form, confirm: event.target.checked })} /><span>{t('sales.confirmOnSave')}</span></label></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{t('sales.detail')}</span><h3>{t('sales.documentLines')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setLines((current) => [...current, { productId: '', productCode: '', description: '', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21' }])}><Plus size={15} />{t('sales.addLine')}</button></div>
    <div className="line-editor">{lines.map((line, index) => <div className="line-editor-row" key={index}>
      <div className="line-product"><label htmlFor={`line-product-${index}`}>{t('sales.product')}</label><select id={`line-product-${index}`} value={line.productId} onChange={(event) => chooseProduct(index, event.target.value)}><option value="">{t('sales.freeLine')}</option>{products.map((product) => <option key={product.id} value={product.id}>{product.code} · {product.name}</option>)}</select></div>
      <div className="line-description"><label htmlFor={`line-description-${index}`}>{t('sales.lineDescription')}</label><input id={`line-description-${index}`} value={line.description} onChange={(event) => updateLine(index, 'description', event.target.value)} required /></div>
      <div><label htmlFor={`line-quantity-${index}`}>{t('sales.quantity')}</label><input id={`line-quantity-${index}`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => updateLine(index, 'quantity', event.target.value)} required /></div>
      <div><label htmlFor={`line-price-${index}`}>{t('sales.price')}</label><input id={`line-price-${index}`} type="number" min="0" step="0.0001" value={line.unitPrice} onChange={(event) => updateLine(index, 'unitPrice', event.target.value)} required /></div>
      <div><label htmlFor={`line-discount-${index}`}>{t('sales.discount')}</label><input id={`line-discount-${index}`} type="number" min="0" max="100" step="0.01" value={line.discountPercentage} onChange={(event) => updateLine(index, 'discountPercentage', event.target.value)} /></div>
      <div><label htmlFor={`line-tax-${index}`}>{t('sales.tax')}</label><input id={`line-tax-${index}`} type="number" min="0" max="100" step="0.01" value={line.taxPercentage} onChange={(event) => updateLine(index, 'taxPercentage', event.target.value)} /></div>
      <button className="icon-button line-remove" type="button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} aria-label={t('sales.deleteLine', { number: index + 1 })}><Trash2 size={16} /></button>
    </div>)}</div>
    <div className="document-footer-form"><Field label={t('sales.notes')} htmlFor="document-notes"><textarea id="document-notes" rows={3} value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} /></Field><div className="totals-card"><span><small>{t('sales.net')}</small><strong>{formatCurrency(totals.net, form.currency, locale)}</strong></span><span><small>{t('catalog.tax')}</small><strong>{formatCurrency(totals.tax, form.currency, locale)}</strong></span><span className="grand-total"><small>{t('sales.total')}</small><strong>{formatCurrency(totals.total, form.currency, locale)}</strong></span></div></div>
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={t('sales.createDocument')} />
  </form>
}

function DocumentDetail({ document, onAction }: { document: CommercialDocument; onAction: (action: 'convert' | 'paid', document: CommercialDocument) => void }) {
  const { locale, t } = useTranslation()
  const convertible = document.status === 'CONFIRMED' && (document.type === 'QUOTE' || document.type === 'DELIVERY_NOTE')
  const payable = document.type === 'INVOICE' && document.paymentStatus !== 'PAID'
  return <div className="document-detail">
    <div className="detail-summary"><div><small>{t('sales.customer')}</small><strong>{document.customerName}</strong><span>{document.customerCode}</span></div><div><small>{t('sales.issue')}</small><strong>{formatDate(document.issueDate, locale)}</strong><span>{t('sales.due', { date: formatDate(document.dueDate, locale) })}</span></div><div><small>{t('sales.status')}</small><StatusBadge tone={statusTone(document.status)}>{t(documentStatusKey[document.status])}</StatusBadge><span>{t(paymentStatusKey[document.paymentStatus])}</span></div><div><small>{t('sales.total')}</small><strong className="detail-total">{formatCurrency(document.totalAmount, document.currency, locale)}</strong><span>{document.currency}</span></div></div>
    <div className="table-scroll detail-lines"><table><thead><tr><th>#</th><th>{t('sales.lineDescription')}</th><th className="align-right">{t('sales.quantity')}</th><th className="align-right">{t('sales.price')}</th><th className="align-right">{t('sales.discount')}</th><th className="align-right">{t('sales.total')}</th></tr></thead><tbody>{document.lines.map((line) => <tr key={line.id || line.order}><td>{line.order}</td><td><strong>{line.description}</strong>{line.productCode && <small>{line.productCode}</small>}</td><td className="align-right">{formatNumber(line.quantity, locale, 6)}</td><td className="align-right">{formatCurrency(line.unitPrice, document.currency, locale)}</td><td className="align-right">{formatNumber(line.discountPercentage, locale, 4)} %</td><td className="align-right"><strong>{formatCurrency(line.totalAmount, document.currency, locale)}</strong></td></tr>)}</tbody></table></div>
    <div className="detail-totals"><span>{t('sales.net')} <strong>{formatCurrency(document.netAmount, document.currency, locale)}</strong></span><span>{t('catalog.tax')} <strong>{formatCurrency(document.taxAmount, document.currency, locale)}</strong></span><span>{t('sales.total')} <strong>{formatCurrency(document.totalAmount, document.currency, locale)}</strong></span></div>
    {(convertible || payable) && <div className="modal-action-strip">{convertible && <button type="button" className="button button-primary" onClick={() => onAction('convert', document)}>{t('sales.convertNext')} <ArrowRight size={17} /></button>}{payable && <button type="button" className="button button-secondary" onClick={() => onAction('paid', document)}><CheckCircle2 size={17} />{t('sales.markPaid')}</button>}</div>}
  </div>
}

function statusTone(status: DocumentStatus): BadgeTone {
  if (status === 'CONFIRMED') return 'success'
  if (status === 'CANCELLED') return 'danger'
  if (status === 'CONVERTED') return 'info'
  return 'neutral'
}

function paymentTone(status: CommercialDocument['paymentStatus']): BadgeTone {
  if (status === 'PAID') return 'success'
  if (status === 'PENDING' || status === 'PARTIALLY_PAID') return 'warning'
  return 'neutral'
}
