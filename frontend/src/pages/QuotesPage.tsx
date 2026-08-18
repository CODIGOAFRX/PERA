import { ArrowRight, CheckCircle2, FileCheck2, Plus, Send, Trash2, XCircle } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge, type BadgeTone } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { calculateDocumentPreview } from '../lib/document'
import { formatCurrency, formatDate, formatNumber } from '../lib/format'
import type { CommercialDocument, CreateQuoteInput, CurrencyDefinition, Customer, PageResponse, PaymentMethod, Product, QuoteStatus } from '../types/api'

const quoteStatuses: QuoteStatus[] = ['DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CONVERTED']

export function QuotesPage() {
  const { locale, t } = useTranslation()
  const [data, setData] = useState<PageResponse<CommercialDocument> | null>(null)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<QuoteStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<CommercialDocument | null>(null)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set())
  const [deleting, setDeleting] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [status])
  useEffect(() => setSelectedIds(new Set()), [page, status, refresh])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'issueDate,desc' })
    if (status) params.set('status', status)
    apiFetch<PageResponse<CommercialDocument>>(`/api/v1/quotes?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, status, refresh])

  const runAction = async (action: 'send' | 'accept' | 'reject' | 'convert', quote: CommercialDocument, reason?: string) => {
    try {
      await apiFetch(`/api/v1/quotes/${quote.id}/${action}`, {
        method: 'POST',
        ...(action === 'reject' ? { body: JSON.stringify({ reason }) } : {}),
      })
      notify(t(`quotes.action.${action}.success`))
      setSelected(null)
      setRefresh((value) => value + 1)
    } catch (cause) {
      notify(errorMessage(cause), 'error')
    }
  }

  const draftIds = data?.content.filter((quote) => (quote.quoteStatus ?? 'DRAFT') === 'DRAFT').map((quote) => quote.id) ?? []
  const allDraftsSelected = draftIds.length > 0 && draftIds.every((id) => selectedIds.has(id))
  const toggleSelection = (id: string, checked: boolean) => setSelectedIds((current) => {
    const next = new Set(current)
    if (checked) next.add(id); else next.delete(id)
    return next
  })
  const toggleAllDrafts = (checked: boolean) => setSelectedIds(checked ? new Set(draftIds) : new Set())
  const deleteSelected = async () => {
    if (selectedIds.size === 0 || !window.confirm(t('quotes.deleteConfirmation', { count: selectedIds.size }))) return
    setDeleting(true)
    try {
      await Promise.all([...selectedIds].map((id) => apiFetch(`/api/v1/quotes/${id}`, { method: 'DELETE' })))
      notify(t('quotes.deleted', { count: selectedIds.size }))
      setSelectedIds(new Set())
      setRefresh((value) => value + 1)
    } catch (cause) {
      notify(errorMessage(cause), 'error')
      setRefresh((value) => value + 1)
    } finally {
      setDeleting(false)
    }
  }

  return <div className="page-stack">
    <PageHeader eyebrow={t('quotes.eyebrow')} title={t('quotes.title')} description={t('quotes.description')} icon={FileCheck2}
      actions={<button className="button button-primary" type="button" onClick={() => setCreating(true)}><Plus size={17} />{t('quotes.new')}</button>} />
    <section className="panel table-panel">
      <TableToolbar value="" onChange={() => undefined} placeholder={t('quotes.search')} hideSearch>
        <select aria-label={t('quotes.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value as QuoteStatus | '')}>
          <option value="">{t('quotes.allStatuses')}</option>
          {quoteStatuses.map((item) => <option key={item} value={item}>{t(`quote.status.${item}`)}</option>)}
        </select>
        <button className="button button-danger" type="button" disabled={selectedIds.size === 0 || deleting} onClick={() => void deleteSelected()}><Trash2 size={16} />{deleting ? t('quotes.deleting') : t('quotes.deleteSelected')}{selectedIds.size > 0 && ` (${selectedIds.size})`}</button>
      </TableToolbar>
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table><thead><tr><th className="selection-cell"><input className="selection-checkbox" type="checkbox" aria-label={t('quotes.selectAllDrafts')} checked={allDraftsSelected} disabled={draftIds.length === 0} onChange={(event) => toggleAllDrafts(event.target.checked)} /></th><th>{t('sales.number')}</th><th>{t('sales.customer')}</th><th>{t('sales.issueDate')}</th><th>{t('quotes.validUntil')}</th><th>{t('sales.status')}</th><th className="align-right">{t('sales.total')}</th></tr></thead>
          <tbody>{data.content.map((quote) => <tr key={quote.id} className="clickable-row" onClick={() => setSelected(quote)}>
            <td className="selection-cell" onClick={(event) => event.stopPropagation()}>{(quote.quoteStatus ?? 'DRAFT') === 'DRAFT' ? <input className="selection-checkbox" type="checkbox" aria-label={t('quotes.selectQuote', { number: quote.number })} checked={selectedIds.has(quote.id)} onChange={(event) => toggleSelection(quote.id, event.target.checked)} /> : <span className="selection-unavailable" aria-hidden="true">—</span>}</td>
            <td><strong className="document-number">{quote.number}</strong></td>
            <td><strong>{quote.customerName}</strong><small>{quote.customerCode}</small></td>
            <td>{formatDate(quote.issueDate, locale)}</td><td>{formatDate(quote.quoteValidUntil, locale)}</td>
            <td><StatusBadge tone={quoteTone(quote.quoteStatus)}>{t(`quote.status.${quote.quoteStatus ?? 'DRAFT'}`)}</StatusBadge></td>
            <td className="align-right"><strong>{formatCurrency(quote.totalAmount, quote.currency, locale)}</strong></td>
          </tr>)}</tbody></table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title={t('quotes.empty')} description={t('quotes.emptyDescription')}
        action={<button className="button button-secondary" type="button" onClick={() => setCreating(true)}>{t('quotes.create')}</button>} />}
    </section>

    <Modal open={creating} title={t('quotes.new')} description={t('quotes.newDescription')} onClose={() => setCreating(false)} size="large">
      <CreateQuoteForm onCancel={() => setCreating(false)} onSaved={() => { setCreating(false); setRefresh((value) => value + 1); notify(t('quotes.created')) }} />
    </Modal>
    <Modal open={selected !== null} title={selected?.number ?? t('quotes.quote')} description={selected?.customerName ?? ''} onClose={() => setSelected(null)} size="large">
      {selected && <QuoteDetail quote={selected} onAction={runAction} />}
    </Modal>
  </div>
}

function CreateQuoteForm({ onCancel, onSaved }: { onCancel: () => void; onSaved: () => void }) {
  const { locale, t } = useTranslation()
  const [customers, setCustomers] = useState<Customer[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([])
  const [currencies, setCurrencies] = useState<CurrencyDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const today = new Date().toISOString().slice(0, 10)
  const defaultValidity = new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10)
  const [form, setForm] = useState({ customerId: '', issueDate: today, validUntil: defaultValidity, paymentMethodId: '', currency: 'EUR', notes: '', sendOnCreate: false })
  const [lines, setLines] = useState([emptyLine()])

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
      setCurrencies(currencyList.filter((item) => item.active))
      const base = currencyList.find((item) => item.baseCurrency)
      if (base) setForm((current) => ({ ...current, currency: base.code }))
    }).catch((cause) => setError(errorMessage(cause))).finally(() => setLoading(false))
  }, [])

  const updateLine = (index: number, name: string, value: string) => setLines((current) => current.map((line, itemIndex) => {
    if (itemIndex !== index) return line
    if (name === 'unitPrice') return { ...line, unitPrice: value, unitPriceOverridden: true }
    if (name === 'taxPercentage') return { ...line, taxPercentage: value, taxPercentageOverridden: true }
    return { ...line, [name]: value }
  }))
  const chooseProduct = (index: number, productId: string) => {
    const product = products.find((item) => item.id === productId)
    setLines((current) => current.map((line, itemIndex) => itemIndex === index ? {
      ...line, productId, productCode: product?.code ?? '', description: product?.name ?? '',
      unitPrice: String(product?.basePrice ?? 0), taxPercentage: String(product?.taxRate ?? 0),
      unitPriceOverridden: false, taxPercentageOverridden: false,
    } : line))
  }
  const totals = useMemo(() => calculateDocumentPreview(lines), [lines])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true); setError('')
    const customer = customers.find((item) => item.id === form.customerId)
    if (!customer) { setError(t('sales.selectCustomer')); setSaving(false); return }
    if (form.validUntil < form.issueDate) { setError(t('quotes.invalidValidity')); setSaving(false); return }
    if (lines.some((line) => !line.description.trim() || Number(line.quantity) <= 0)) { setError(t('sales.reviewLines')); setSaving(false); return }
    const payload: CreateQuoteInput = {
      customerId: customer.id, customerCode: customer.code, customerName: customer.legalName,
      issueDate: form.issueDate, validUntil: form.validUntil, currency: form.currency,
      paymentMethodId: form.paymentMethodId || null, notes: form.notes.trim() || null,
      sendOnCreate: form.sendOnCreate,
      lines: lines.map((line) => ({ productId: line.productId || null, productCode: line.productCode || null,
        description: line.description.trim(), quantity: Number(line.quantity), unitPrice: Number(line.unitPrice),
        discountPercentage: Number(line.discountPercentage), taxPercentage: Number(line.taxPercentage),
        unitPriceOverridden: line.unitPriceOverridden, taxPercentageOverridden: line.taxPercentageOverridden })),
    }
    try { await apiFetch('/api/v1/quotes', { method: 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  if (loading) return <LoadingState label={t('sales.loadingOptions')} />
  return <form onSubmit={submit}>
    <div className="form-grid document-header-form">
      <Field label={t('sales.customer')} htmlFor="quote-customer" required><select id="quote-customer" value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })} required><option value="">{t('sales.selectCustomer')}</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.code} · {customer.legalName}</option>)}</select></Field>
      <Field label={t('sales.issueDate')} htmlFor="quote-date" required><input id="quote-date" type="date" value={form.issueDate} onChange={(event) => setForm({ ...form, issueDate: event.target.value })} required /></Field>
      <Field label={t('quotes.validUntil')} htmlFor="quote-valid" required><input id="quote-valid" type="date" min={form.issueDate} value={form.validUntil} onChange={(event) => setForm({ ...form, validUntil: event.target.value })} required /></Field>
      <Field label={t('quotes.currency')} htmlFor="quote-currency" required><select id="quote-currency" value={form.currency} onChange={(event) => setForm({ ...form, currency: event.target.value })}>{currencies.length ? currencies.map((currency) => <option key={currency.code} value={currency.code}>{currency.code} · {currency.name}</option>) : <option value="EUR">EUR</option>}</select></Field>
      <Field label={t('sales.paymentMethod')} htmlFor="quote-payment"><select id="quote-payment" value={form.paymentMethodId} onChange={(event) => setForm({ ...form, paymentMethodId: event.target.value })}><option value="">{t('sales.noPaymentMethod')}</option>{paymentMethods.map((method) => <option key={method.id} value={method.id}>{method.code} · {method.name}</option>)}</select></Field>
      <Field label={t('sales.initialStatus')} htmlFor="quote-send"><label className="switch-row" htmlFor="quote-send"><input id="quote-send" type="checkbox" checked={form.sendOnCreate} onChange={(event) => setForm({ ...form, sendOnCreate: event.target.checked })} /><span>{t('quotes.sendOnCreate')}</span></label></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{t('sales.detail')}</span><h3>{t('sales.documentLines')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setLines((current) => [...current, emptyLine()])}><Plus size={15} />{t('sales.addLine')}</button></div>
    <div className="line-editor">{lines.map((line, index) => <div className="line-editor-row" key={index}>
      <div className="line-product"><label htmlFor={`quote-product-${index}`}>{t('sales.product')}</label><select id={`quote-product-${index}`} value={line.productId} onChange={(event) => chooseProduct(index, event.target.value)}><option value="">{t('sales.freeLine')}</option>{products.map((product) => <option key={product.id} value={product.id}>{product.code} · {product.name}</option>)}</select></div>
      <div className="line-description"><label htmlFor={`quote-description-${index}`}>{t('sales.lineDescription')}</label><input id={`quote-description-${index}`} value={line.description} onChange={(event) => updateLine(index, 'description', event.target.value)} required /></div>
      <div><label htmlFor={`quote-quantity-${index}`}>{t('sales.quantity')}</label><input id={`quote-quantity-${index}`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => updateLine(index, 'quantity', event.target.value)} required /></div>
      <div><label htmlFor={`quote-price-${index}`}>{t('sales.price')}</label><input id={`quote-price-${index}`} type="number" min="0" step="0.0001" value={line.unitPrice} onChange={(event) => updateLine(index, 'unitPrice', event.target.value)} required /></div>
      <div><label htmlFor={`quote-discount-${index}`}>{t('sales.discount')}</label><input id={`quote-discount-${index}`} type="number" min="0" max="100" step="0.01" value={line.discountPercentage} onChange={(event) => updateLine(index, 'discountPercentage', event.target.value)} /></div>
      <div><label htmlFor={`quote-tax-${index}`}>{t('sales.tax')}</label><input id={`quote-tax-${index}`} type="number" min="0" max="100" step="0.01" value={line.taxPercentage} onChange={(event) => updateLine(index, 'taxPercentage', event.target.value)} /></div>
      <button className="icon-button line-remove" type="button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, itemIndex) => itemIndex !== index))} aria-label={t('sales.deleteLine', { number: index + 1 })}><Trash2 size={16} /></button>
    </div>)}</div>
    <div className="document-footer-form"><Field label={t('sales.notes')} htmlFor="quote-notes"><textarea id="quote-notes" rows={3} value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} /></Field><div className="totals-card"><span><small>{t('sales.net')}</small><strong>{formatCurrency(totals.net, form.currency, locale)}</strong></span><span><small>{t('catalog.tax')}</small><strong>{formatCurrency(totals.tax, form.currency, locale)}</strong></span><span className="grand-total"><small>{t('sales.total')}</small><strong>{formatCurrency(totals.total, form.currency, locale)}</strong></span></div></div>
    {error && <div className="form-error" role="alert">{error}</div>}
    <FormActions onCancel={onCancel} saving={saving} submitLabel={t('quotes.create')} />
  </form>
}

function QuoteDetail({ quote, onAction }: { quote: CommercialDocument; onAction: (action: 'send' | 'accept' | 'reject' | 'convert', quote: CommercialDocument, reason?: string) => void }) {
  const { locale, t } = useTranslation()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const status = quote.quoteStatus ?? 'DRAFT'
  return <div className="document-detail">
    <div className="detail-summary"><div><small>{t('sales.customer')}</small><strong>{quote.customerName}</strong><span>{quote.customerCode}</span></div><div><small>{t('sales.issue')}</small><strong>{formatDate(quote.issueDate, locale)}</strong><span>{t('quotes.validThrough', { date: formatDate(quote.quoteValidUntil, locale) })}</span></div><div><small>{t('sales.status')}</small><StatusBadge tone={quoteTone(status)}>{t(`quote.status.${status}`)}</StatusBadge></div><div><small>{t('sales.total')}</small><strong className="detail-total">{formatCurrency(quote.totalAmount, quote.currency, locale)}</strong><span>{quote.currency}</span></div></div>
    <div className="table-scroll detail-lines"><table><thead><tr><th>#</th><th>{t('sales.lineDescription')}</th><th className="align-right">{t('sales.quantity')}</th><th className="align-right">{t('sales.price')}</th><th className="align-right">{t('sales.discount')}</th><th className="align-right">{t('sales.total')}</th></tr></thead><tbody>{quote.lines.map((line) => <tr key={line.id || line.order}><td>{line.order}</td><td><strong>{line.description}</strong>{line.productCode && <small>{line.productCode}</small>}</td><td className="align-right">{formatNumber(line.quantity, locale, 6)}</td><td className="align-right">{formatCurrency(line.unitPrice, quote.currency, locale)}</td><td className="align-right">{formatNumber(line.discountPercentage, locale, 4)} %</td><td className="align-right"><strong>{formatCurrency(line.totalAmount, quote.currency, locale)}</strong></td></tr>)}</tbody></table></div>
    <div className="detail-totals"><span>{t('sales.net')} <strong>{formatCurrency(quote.netAmount, quote.currency, locale)}</strong></span><span>{t('catalog.tax')} <strong>{formatCurrency(quote.taxAmount, quote.currency, locale)}</strong></span><span>{t('sales.total')} <strong>{formatCurrency(quote.totalAmount, quote.currency, locale)}</strong></span></div>
    {rejecting && <div className="quote-rejection"><Field label={t('quotes.rejectionReason')} htmlFor="quote-reason" required><textarea id="quote-reason" rows={2} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></Field></div>}
    <div className="modal-action-strip">
      {status === 'DRAFT' && <button className="button button-primary" type="button" onClick={() => onAction('send', quote)}><Send size={17} />{t('quotes.send')}</button>}
      {status === 'SENT' && <><button className="button button-secondary" type="button" onClick={() => setRejecting((value) => !value)}><XCircle size={17} />{t('quotes.reject')}</button>{rejecting && <button className="button button-danger" type="button" disabled={!reason.trim()} onClick={() => onAction('reject', quote, reason.trim())}>{t('quotes.confirmReject')}</button>}<button className="button button-primary" type="button" onClick={() => onAction('accept', quote)}><CheckCircle2 size={17} />{t('quotes.accept')}</button></>}
      {status === 'ACCEPTED' && <button className="button button-primary" type="button" onClick={() => onAction('convert', quote)}>{t('quotes.convert')}<ArrowRight size={17} /></button>}
    </div>
  </div>
}

function emptyLine() {
  return { productId: '', productCode: '', description: '', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21', unitPriceOverridden: false, taxPercentageOverridden: false }
}

function quoteTone(status: QuoteStatus | null): BadgeTone {
  if (status === 'ACCEPTED' || status === 'CONVERTED') return 'success'
  if (status === 'REJECTED' || status === 'EXPIRED') return 'danger'
  if (status === 'SENT') return 'info'
  return 'neutral'
}
