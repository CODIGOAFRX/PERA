import { DocumentEmail } from '../components/DocumentEmail'
import { ArrowRight, CheckCircle2, Download, FileText, Plus, ReceiptText, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FieldControl, FormActions, FormErrors, useFormErrors } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge, type BadgeTone } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { VerifactuBlock } from '../components/VerifactuBlock'
import { FiscalDelivery } from '../components/FiscalDelivery'
import { useAuth } from '../auth/AuthContext'
import { Link } from '../routing/Router'
import { useConfirm } from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'
import { apiDownload, apiFetch, errorMessage } from '../lib/api'
import { calculateDocumentPreview } from '../lib/document'
import { formatCurrency, formatDate, formatNumber } from '../lib/format'
import { localIsoDate } from '../lib/date'
import { creditCheckedTypes, creditRiskFigures, withCreditRiskConfirmation, type CreditRiskAssessment } from '../lib/creditRisk'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { saveBlob } from '../lib/download'
import { documentStatusKey, documentTypeKey, paymentStatusKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import type { CommercialDocument, CreateDocumentInput, CurrencyDefinition, Customer, DocumentStatus, DocumentType, PageResponse, PaymentMethod, Product } from '../types/api'
import { TableCaption } from '../components/TableCaption'

const documentTypes = Object.keys(documentTypeKey) as DocumentType[]
const documentStatuses = Object.keys(documentStatusKey) as DocumentStatus[]

export function SalesPage() {
  const { language, locale, t } = useTranslation()
  const [data, setData] = useState<PageResponse<CommercialDocument> | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [type, setType] = useState<DocumentType | ''>('')
  const [status, setStatus] = useState<DocumentStatus | ''>('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [verifactuDocuments, setVerifactuDocuments] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<CommercialDocument | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const confirm = useConfirm()

  useEffect(() => { const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 250); return () => window.clearTimeout(timer) }, [query])
  useEffect(() => setPage(0), [debouncedQuery, type, status, fromDate, toDate])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'issueDate,desc' })
    if (debouncedQuery) params.set('q', debouncedQuery)
    if (type) params.set('type', type)
    if (status) params.set('status', status)
    if (fromDate) params.set('fromDate', fromDate)
    if (toDate) params.set('toDate', toDate)
    apiFetch<PageResponse<CommercialDocument>>(`/api/v1/documents?${params}`).then(async (response) => {
      if (!active) return
      setData(response); setError('')
      const invoiceIds = response.content.filter((item) => item.type === 'INVOICE' || item.type === 'RECTIFYING_INVOICE').map((item) => item.id)
      if (!invoiceIds.length) { setVerifactuDocuments(new Set()); return }
      const availability = new URLSearchParams()
      invoiceIds.forEach((id) => availability.append('documentIds', id))
      const registered = await apiFetch<string[]>(`/api/v1/verifactu-records/availability?${availability}`)
      if (active) setVerifactuDocuments(new Set(registered))
    })
      .catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, debouncedQuery, type, status, fromDate, toDate, refresh])

  const [acting, setActing] = useState(false)
  const runAction = async (action: DocumentAction, document: CommercialDocument) => {
    // Convertir o emitir puede expedir una factura: es irreversible y un doble clic no debe repetirla.
    const question = action === 'convert' ? 'sales.confirmConvert' : action === 'paid' ? 'sales.confirmPaid'
      : document.type === 'INVOICE' || document.type === 'RECTIFYING_INVOICE' ? 'sales.confirmIssue' : 'sales.confirmDraft'
    if (acting || !(await confirm({ message: t(question, { number: document.number }) }))) return
    setActing(true)
    try {
      if (action === 'convert') {
        // Converting a quote can exceed the customer's credit: the server asks for a decision.
        const converted = await withCreditRiskConfirmation((acknowledged) => apiFetch(`/api/v1/documents/${document.id}/convert${acknowledged ? '?riskAcknowledged=true' : ''}`, { method: 'POST' }), confirm, locale, language)
        if (converted === null) return
      } else if (action === 'confirm') {
        const confirmed = await withCreditRiskConfirmation((acknowledged) => apiFetch(`/api/v1/documents/${document.id}/confirm${acknowledged ? '?riskAcknowledged=true' : ''}`, { method: 'POST' }), confirm, locale, language)
        if (confirmed === null) return
      } else await apiFetch(`/api/v1/documents/${document.id}/payment-status`, { method: 'PATCH', body: JSON.stringify({ status: 'PAID' }) })
      notify(action === 'convert' ? t('sales.converted') : action === 'paid' ? t('sales.markedPaid') : t('sales.confirmed'))
      setSelected(null); setRefresh((value) => value + 1)
    } catch (cause) { notify(errorMessage(cause), 'error') } finally { setActing(false) }
  }

  // El PDF lo compone el servidor y aquí solo se descarga. Es un entregable —se envía al cliente y
  // se archiva—, así que tiene que ser un fichero idéntico cada vez, no lo que decida imprimir el
  // navegador de turno.
  const downloadInvoice = async (invoice: CommercialDocument) => {
    try {
      const { blob, filename } = await apiDownload(`/api/v1/documents/${invoice.id}/invoice.pdf`)
      saveBlob(blob, filename)
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <div className="page-stack">
    <PageHeader eyebrow={t('sales.eyebrow')} title={t('sales.title')} description={t('sales.description')} icon={FileText} actions={<button className="button button-primary" type="button" onClick={() => setCreating(true)}><Plus size={17} />{t('sales.newDocument')}</button>} />
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={t('sales.searchDocuments')}>
        <select aria-label={t('sales.filterType')} value={type} onChange={(event) => setType(event.target.value as DocumentType | '')}><option value="">{t('sales.allTypes')}</option>{documentTypes.map((item) => <option key={item} value={item}>{t(documentTypeKey[item])}</option>)}</select>
        <select aria-label={t('sales.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value as DocumentStatus | '')}><option value="">{t('sales.allStatuses')}</option>{documentStatuses.map((item) => <option key={item} value={item}>{t(documentStatusKey[item])}</option>)}</select>
        <label className="toolbar-date"><span>{t('common.from')}</span><input aria-label={t('common.from')} type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} /></label>
        <label className="toolbar-date"><span>{t('common.to')}</span><input aria-label={t('common.to')} type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} /></label>
      </TableToolbar>
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <><div className="table-scroll"><table><TableCaption es="Documentos de venta" en="Sales documents" /><thead><tr><th>{t('sales.number')}</th><th>{t('sales.type')}</th><th>{t('sales.customer')}</th><th>{t('sales.date')}</th><th>{t('sales.status')}</th><th>{t('sales.payment')}</th><th className="align-right">{t('sales.total')}</th></tr></thead><tbody>{data.content.map((document) => <tr key={document.id} className={`clickable-row${document.status === 'CONVERTED' ? ' document-row-converted' : ''}`} onClick={() => setSelected(document)}><td><span className="document-number-wrap"><strong className="document-number">{document.number}</strong>{verifactuDocuments.has(document.id) && <span className="verifactu-list-mark" title="VERI*FACTU" aria-label="VERI*FACTU"><CheckCircle2 size={16} /><span>V</span></span>}</span></td><td>{t(documentTypeKey[document.type])}</td><td><strong>{document.customerName}</strong><small>{document.customerCode}</small></td><td>{formatDate(document.issueDate, locale)}</td><td><StatusBadge tone={statusTone(document.status)}>{t(documentStatusKey[document.status])}</StatusBadge></td><td><StatusBadge tone={paymentTone(document.paymentStatus)}>{t(paymentStatusKey[document.paymentStatus])}</StatusBadge></td><td className="align-right"><strong>{formatCurrency(document.totalAmount, document.currency, locale)}</strong></td></tr>)}</tbody></table></div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title={t('sales.empty')} description={t('sales.emptyDescription')} action={<button className="button button-secondary" type="button" onClick={() => setCreating(true)}>{t('sales.createDocument')}</button>} />}
    </section>

    <Modal open={creating} title={t('sales.newDocument')} description={t('sales.newDescription')} onClose={() => setCreating(false)} size="large"><CreateDocumentForm onCancel={() => setCreating(false)} onSaved={() => { setCreating(false); setRefresh((value) => value + 1); notify(t('sales.created')) }} /></Modal>
    <Modal open={selected !== null} title={selected?.number || t('sales.document')} description={selected ? `${t(documentTypeKey[selected.type])} · ${selected.customerName}` : ''} onClose={() => setSelected(null)} size="large">{selected && <DocumentDetail document={selected} onAction={runAction} onDownload={downloadInvoice} />}</Modal>
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
  const formErrors = useFormErrors()
  const [form, setForm] = useState({ type: 'QUOTE' as DocumentType, customerId: '', issueDate: localIsoDate(), dueDate: '', currency: 'EUR', paymentMethodId: '', notes: '', confirm: true })
  const [lines, setLines] = useState([{ productId: '', productCode: '', description: '', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21', unitPriceOverridden: false, taxPercentageOverridden: false }])

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

  const updateLine = (index: number, name: string, value: string) => setLines((current) => current.map((line, lineIndex) => {
    if (lineIndex !== index) return line
    if (name === 'unitPrice') return { ...line, unitPrice: value, unitPriceOverridden: true }
    if (name === 'taxPercentage') return { ...line, taxPercentage: value, taxPercentageOverridden: true }
    return { ...line, [name]: value }
  }))
  const chooseProduct = (index: number, productId: string) => {
    const product = products.find((item) => item.id === productId)
    setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, productId, productCode: product?.code || '', description: product?.name || '', unitPrice: String(product?.basePrice ?? 0), taxPercentage: String(product?.taxRate ?? 0), unitPriceOverridden: false, taxPercentageOverridden: false } : line))
  }
  const totals = useMemo(() => calculateDocumentPreview(lines), [lines])
  const confirm = useConfirm()
  const [risk, setRisk] = useState<CreditRiskAssessment | null>(null)
  const riskQuery = useDebouncedValue(creditCheckedTypes.has(form.type) && form.customerId
    ? new URLSearchParams({ customerId: form.customerId, amount: String(totals.total), currency: form.currency, date: form.issueDate }).toString()
    : '', 400)
  useEffect(() => {
    if (!riskQuery) { setRisk(null); return }
    let active = true
    // Informative only: if the preview fails, the server still checks the risk when saving.
    apiFetch<CreditRiskAssessment>(`/api/v1/documents/credit-risk?${riskQuery}`)
      .then((value) => { if (active) setRisk(value) })
      .catch(() => { if (active) setRisk(null) })
    return () => { active = false }
  }, [riskQuery])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const customer = customers.find((item) => item.id === form.customerId)
    if (!customer) { setError(t('sales.selectCustomer')); setSaving(false); return }
    if (lines.some((line) => !line.description.trim() || Number(line.quantity) <= 0)) { setError(t('sales.reviewLines')); setSaving(false); return }
    const payload: CreateDocumentInput = {
      type: form.type, customerId: customer.id, customerCode: customer.code, customerName: customer.legalName,
      issueDate: form.issueDate, dueDate: form.dueDate || null, currency: form.currency, paymentMethodId: form.paymentMethodId || null,
      notes: form.notes.trim() || null, confirm: form.confirm,
      lines: lines.map((line) => ({ productId: line.productId || null, productCode: line.productCode || null, description: line.description.trim(), quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), discountPercentage: Number(line.discountPercentage), taxPercentage: Number(line.taxPercentage), unitPriceOverridden: line.unitPriceOverridden, taxPercentageOverridden: line.taxPercentageOverridden })),
    }
    try {
      const created = await withCreditRiskConfirmation((acknowledged) => apiFetch<CommercialDocument>(`/api/v1/documents${acknowledged ? '?riskAcknowledged=true' : ''}`, { method: 'POST', body: JSON.stringify(payload) }), confirm, locale, language)
      if (created !== null) onSaved()
    }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  if (loadingOptions) return <LoadingState label={t('sales.loadingOptions')} />
  return <form onSubmit={submit}><FormErrors value={formErrors.context}>
    <div className="form-grid document-header-form">
      <Field label={t('sales.type')} htmlFor="document-type" name="type" required><select id="document-type" value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value as DocumentType })}>{documentTypes.map((item) => <option key={item} value={item}>{t(documentTypeKey[item])}</option>)}</select></Field>
      <Field label={t('sales.customer')} htmlFor="document-customer" name="customerId" required><select id="document-customer" value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })} required><option value="">{t('sales.selectCustomer')}</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.code} · {customer.legalName}</option>)}</select></Field>
      <Field label={t('sales.issueDate')} htmlFor="document-date" name="issueDate" required><input id="document-date" type="date" value={form.issueDate} onChange={(event) => setForm({ ...form, issueDate: event.target.value })} required /></Field>
      <Field label={t('sales.dueDate')} htmlFor="document-due" name="dueDate"><input id="document-due" type="date" value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} /></Field>
      <Field label={c('Moneda', 'Currency')} htmlFor="document-currency" name="currency" required><select id="document-currency" value={form.currency} onChange={(event) => setForm({ ...form, currency: event.target.value })}>{currencies.length ? currencies.map((currency) => <option key={currency.code} value={currency.code}>{currency.code} · {currency.name}</option>) : <option value="EUR">EUR</option>}</select></Field>
      <Field label={t('sales.paymentMethod')} htmlFor="document-payment" name="paymentMethodId"><select id="document-payment" value={form.paymentMethodId} onChange={(event) => setForm({ ...form, paymentMethodId: event.target.value })}><option value="">{t('sales.noPaymentMethod')}</option>{paymentMethods.map((method) => <option key={method.id} value={method.id}>{method.code} · {method.name}</option>)}</select></Field>
      <Field label={t('sales.initialStatus')} htmlFor="document-confirm" name="confirm"><label className="switch-row" htmlFor="document-confirm"><input id="document-confirm" type="checkbox" checked={form.confirm} onChange={(event) => setForm({ ...form, confirm: event.target.checked })} /><span>{t('sales.confirmOnSave')}</span></label></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{t('sales.detail')}</span><h3>{t('sales.documentLines')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setLines((current) => [...current, { productId: '', productCode: '', description: '', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21', unitPriceOverridden: false, taxPercentageOverridden: false }])}><Plus size={15} />{t('sales.addLine')}</button></div>
    <div className="line-editor">{lines.map((line, index) => <div className="line-editor-row" key={index}>
      <div className="line-product"><label htmlFor={`line-product-${index}`}>{t('sales.product')}</label><FieldControl htmlFor={`line-product-${index}`} name={`lines[${index}].productId`}><select id={`line-product-${index}`} value={line.productId} onChange={(event) => chooseProduct(index, event.target.value)}><option value="">{t('sales.freeLine')}</option>{products.map((product) => <option key={product.id} value={product.id}>{product.code} · {product.name}</option>)}</select></FieldControl></div>
      <div className="line-description"><label htmlFor={`line-description-${index}`}>{t('sales.lineDescription')}</label><FieldControl htmlFor={`line-description-${index}`} name={`lines[${index}].description`}><input id={`line-description-${index}`} value={line.description} onChange={(event) => updateLine(index, 'description', event.target.value)} required /></FieldControl></div>
      <div><label htmlFor={`line-quantity-${index}`}>{t('sales.quantity')}</label><FieldControl htmlFor={`line-quantity-${index}`} name={`lines[${index}].quantity`}><input id={`line-quantity-${index}`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => updateLine(index, 'quantity', event.target.value)} required /></FieldControl></div>
      <div><label htmlFor={`line-price-${index}`}>{t('sales.price')}</label><FieldControl htmlFor={`line-price-${index}`} name={`lines[${index}].unitPrice`}><input id={`line-price-${index}`} type="number" min="0" step="0.0001" value={line.unitPrice} onChange={(event) => updateLine(index, 'unitPrice', event.target.value)} required /></FieldControl></div>
      <div><label htmlFor={`line-discount-${index}`}>{t('sales.discount')}</label><FieldControl htmlFor={`line-discount-${index}`} name={`lines[${index}].discountPercentage`}><input id={`line-discount-${index}`} type="number" min="0" max="100" step="0.01" value={line.discountPercentage} onChange={(event) => updateLine(index, 'discountPercentage', event.target.value)} /></FieldControl></div>
      <div><label htmlFor={`line-tax-${index}`}>{t('sales.tax')}</label><FieldControl htmlFor={`line-tax-${index}`} name={`lines[${index}].taxPercentage`}><input id={`line-tax-${index}`} type="number" min="0" max="100" step="0.01" value={line.taxPercentage} onChange={(event) => updateLine(index, 'taxPercentage', event.target.value)} /></FieldControl></div>
      <button className="icon-button line-remove" type="button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} aria-label={t('sales.deleteLine', { number: index + 1 })}><Trash2 size={16} /></button>
    </div>)}</div>
    <div className="document-footer-form"><Field label={t('sales.notes')} htmlFor="document-notes" name="notes"><textarea id="document-notes" rows={3} value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} /></Field><div className="totals-card"><span><small>{t('sales.net')}</small><strong>{formatCurrency(totals.net, form.currency, locale)}</strong></span><span><small>{t('catalog.tax')}</small><strong>{formatCurrency(totals.tax, form.currency, locale)}</strong></span><span className="grand-total"><small>{t('sales.total')}</small><strong>{formatCurrency(totals.total, form.currency, locale)}</strong></span></div></div>
    {risk && risk.level !== 'OK' && <CreditRiskNotice risk={risk} />}
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={t('sales.createDocument')} />
  </FormErrors></form>
}

type DocumentAction = 'convert' | 'paid' | 'confirm'

function DocumentDetail({ document, onAction, onDownload }: { document: CommercialDocument; onAction: (action: DocumentAction, document: CommercialDocument) => void; onDownload: (document: CommercialDocument) => void }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const { hasRole } = useAuth()
  // Only owners and administrators can open Conexiones to enable a provider.
  const configureLink = hasRole('OWNER') || hasRole('ADMIN') ? <Link to="/conexiones">{c('Configurar en Conexiones', 'Set up in Connections')}</Link> : undefined
  const convertible = document.status === 'CONFIRMED' && (document.type === 'QUOTE' || document.type === 'DELIVERY_NOTE')
  const payable = document.type === 'INVOICE' && document.paymentStatus !== 'PAID'
  // Solo se imprime lo que es una factura. Un presupuesto o un albarán tienen su propio formato y
  // no llevan ni QR ni leyenda; imprimirlos con esta hoja sería llamarlos factura.
  const printable = document.type === 'INVOICE' || document.type === 'RECTIFYING_INVOICE'
  // A draft saved without "confirm on save" is confirmed (or, for an invoice, issued) from here.
  const confirmable = document.status === 'DRAFT' && document.type !== 'QUOTE'
  return <div className="document-detail">
    <div className="detail-summary"><div><small>{t('sales.customer')}</small><strong>{document.customerName}</strong><span>{document.customerCode}</span></div><div><small>{t('sales.issue')}</small><strong>{formatDate(document.issueDate, locale)}</strong><span>{t('sales.due', { date: formatDate(document.dueDate, locale) })}</span></div><div><small>{t('sales.status')}</small><StatusBadge tone={statusTone(document.status)}>{t(documentStatusKey[document.status])}</StatusBadge><span>{t(paymentStatusKey[document.paymentStatus])}</span></div><div><small>{t('sales.total')}</small><strong className="detail-total">{formatCurrency(document.totalAmount, document.currency, locale)}</strong><span>{document.currency}</span></div></div>
    <div className="table-scroll detail-lines"><table><TableCaption es="Líneas del documento" en="Document lines" /><thead><tr><th>#</th><th>{t('sales.lineDescription')}</th><th className="align-right">{t('sales.quantity')}</th><th className="align-right">{t('sales.price')}</th><th className="align-right">{t('sales.discount')}</th><th className="align-right">{t('sales.total')}</th></tr></thead><tbody>{document.lines.map((line) => <tr key={line.id || line.order}><td>{line.order}</td><td><strong>{line.description}</strong>{line.productCode && <small>{line.productCode}</small>}</td><td className="align-right">{formatNumber(line.quantity, locale, 6)}</td><td className="align-right">{formatCurrency(line.unitPrice, document.currency, locale)}</td><td className="align-right">{formatNumber(line.discountPercentage, locale, 4)} %</td><td className="align-right"><strong>{formatCurrency(line.totalAmount, document.currency, locale)}</strong></td></tr>)}</tbody></table></div>
    <div className="detail-totals"><span>{t('sales.net')} <strong>{formatCurrency(document.netAmount, document.currency, locale)}</strong></span><span>{t('catalog.tax')} <strong>{formatCurrency(document.taxAmount, document.currency, locale)}</strong></span><span>{t('sales.total')} <strong>{formatCurrency(document.totalAmount, document.currency, locale)}</strong></span></div>
    {printable && <DocumentEmail id={document.id} disabled={document.status === 'DRAFT'} />}
    {document.type === 'INVOICE' || document.type === 'RECTIFYING_INVOICE' ? <VerifactuBlock documentId={document.id} configureLink={configureLink} /> : null}
    {document.type === 'INVOICE' && document.status !== 'DRAFT' && <FiscalDelivery sourceId={document.id} provider="B2B" configureLink={configureLink} />}
    {(confirmable || convertible || payable || printable) && <div className="modal-action-strip">{confirmable && <button type="button" className="button button-primary" onClick={() => onAction('confirm', document)}><CheckCircle2 size={17} />{t(printable ? 'sales.issueInvoice' : 'sales.confirmDocument')}</button>}{convertible && <button type="button" className="button button-primary" onClick={() => onAction('convert', document)}>{t('sales.convertNext')} <ArrowRight size={17} /></button>}{payable && <button type="button" className="button button-secondary" onClick={() => onAction('paid', document)}><CheckCircle2 size={17} />{t('sales.markPaid')}</button>}{printable && <button type="button" className="button button-ghost" onClick={() => onDownload(document)}><Download size={17} />{c('Descargar la factura', 'Download the invoice')}</button>}</div>}
  </div>
}

/** Explains the customer's credit position before saving, so the decision is not a surprise. */
function CreditRiskNotice({ risk }: { risk: CreditRiskAssessment }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  let message: string
  if (risk.level === 'WARNING') message = c('El cliente supera su umbral de aviso de riesgo.', 'The customer exceeds its risk warning threshold.')
  else if (risk.policy === 'WARN') message = c('El cliente supera su límite de crédito. Puedes guardar igualmente.', 'The customer exceeds its credit limit. You can still save.')
  else if (risk.policy === 'REQUIRE_CONFIRMATION') message = c('El cliente supera su límite de crédito. Se pedirá confirmación al guardar.', 'The customer exceeds its credit limit. You will be asked to confirm when saving.')
  else if (risk.canOverride) message = c('Venta bloqueada: el cliente supera su límite de crédito. Como propietario o administrador puedes autorizarla al guardar.', 'Sale blocked: the customer exceeds its credit limit. As an owner or administrator you can authorise it when saving.')
  else message = c('Venta bloqueada: el cliente supera su límite de crédito. Pide a un administrador que la autorice o que revise el límite.', 'Sale blocked: the customer exceeds its credit limit. Ask an administrator to authorise it or review the limit.')
  const blocking = risk.level === 'OVER_LIMIT' && risk.policy !== 'WARN'
  return <div className={`credit-risk-notice${blocking ? ' credit-risk-blocking' : ''}`} role="status">
    <strong>{message}</strong>
    <span>{creditRiskFigures(risk, locale, language)}</span>
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
