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
import { documentStatusLabel, documentTypeLabel, formatCurrency, formatDate, paymentStatusLabel } from '../lib/format'
import type { CommercialDocument, CreateDocumentInput, Customer, DocumentStatus, DocumentType, PageResponse, PaymentMethod, Product } from '../types/api'

const documentTypes = Object.keys(documentTypeLabel) as DocumentType[]
const documentStatuses = Object.keys(documentStatusLabel) as DocumentStatus[]

export function SalesPage() {
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
      notify(action === 'convert' ? 'Documento convertido correctamente.' : 'Factura marcada como cobrada.')
      setSelected(null); setRefresh((value) => value + 1)
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <div className="page-stack">
    <PageHeader eyebrow="Operaciones" title="Ventas" description="Del presupuesto a la factura, con importes y trazabilidad." icon={FileText} actions={<button className="button button-primary" type="button" onClick={() => setCreating(true)}><Plus size={17} />Nuevo documento</button>} />
    <section className="panel table-panel">
      <TableToolbar value="" onChange={() => undefined} placeholder="Buscar documentos" hideSearch>
        <select aria-label="Filtrar por tipo" value={type} onChange={(event) => setType(event.target.value as DocumentType | '')}><option value="">Todos los tipos</option>{documentTypes.map((item) => <option key={item} value={item}>{documentTypeLabel[item]}</option>)}</select>
        <select aria-label="Filtrar por estado" value={status} onChange={(event) => setStatus(event.target.value as DocumentStatus | '')}><option value="">Todos los estados</option>{documentStatuses.map((item) => <option key={item} value={item}>{documentStatusLabel[item]}</option>)}</select>
      </TableToolbar>
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <><div className="table-scroll"><table><thead><tr><th>Número</th><th>Tipo</th><th>Cliente</th><th>Fecha</th><th>Estado</th><th>Cobro</th><th className="align-right">Total</th></tr></thead><tbody>{data.content.map((document) => <tr key={document.id} className="clickable-row" onClick={() => setSelected(document)}><td><strong className="document-number">{document.number}</strong></td><td>{documentTypeLabel[document.type]}</td><td><strong>{document.customerName}</strong><small>{document.customerCode}</small></td><td>{formatDate(document.issueDate)}</td><td><StatusBadge tone={statusTone(document.status)}>{documentStatusLabel[document.status]}</StatusBadge></td><td><StatusBadge tone={paymentTone(document.paymentStatus)}>{paymentStatusLabel[document.paymentStatus]}</StatusBadge></td><td className="align-right"><strong>{formatCurrency(document.totalAmount)}</strong></td></tr>)}</tbody></table></div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title="No hay documentos" description="Crea el primer presupuesto, albarán o factura." action={<button className="button button-secondary" type="button" onClick={() => setCreating(true)}>Crear documento</button>} />}
    </section>

    <Modal open={creating} title="Nuevo documento" description="Selecciona cliente y productos; PERA calculará los importes." onClose={() => setCreating(false)} size="large"><CreateDocumentForm onCancel={() => setCreating(false)} onSaved={() => { setCreating(false); setRefresh((value) => value + 1); notify('Documento creado correctamente.') }} /></Modal>
    <Modal open={selected !== null} title={selected?.number || 'Documento'} description={selected ? `${documentTypeLabel[selected.type]} · ${selected.customerName}` : ''} onClose={() => setSelected(null)} size="large">{selected && <DocumentDetail document={selected} onAction={runAction} />}</Modal>
  </div>
}

function CreateDocumentForm({ onCancel, onSaved }: { onCancel: () => void; onSaved: () => void }) {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethod[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState({ type: 'QUOTE' as DocumentType, customerId: '', issueDate: new Date().toISOString().slice(0, 10), dueDate: '', paymentMethodId: '', notes: '', confirm: true })
  const [lines, setLines] = useState([{ productId: '', productCode: '', description: '', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21' }])

  useEffect(() => {
    Promise.all([
      apiFetch<PageResponse<Customer>>('/api/v1/customers?size=100&sort=legalName,asc'),
      apiFetch<PageResponse<Product>>('/api/v1/products?size=100&sort=name,asc'),
      apiFetch<PaymentMethod[]>('/api/v1/payment-methods'),
    ]).then(([customerPage, productPage, methods]) => {
      setCustomers(customerPage.content.filter((item) => item.active))
      setProducts(productPage.content.filter((item) => item.active))
      setPaymentMethods(methods.filter((item) => item.active))
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
    if (!customer) { setError('Selecciona un cliente.'); setSaving(false); return }
    if (lines.some((line) => !line.description.trim() || Number(line.quantity) <= 0)) { setError('Revisa las líneas del documento.'); setSaving(false); return }
    const payload: CreateDocumentInput = {
      type: form.type, customerId: customer.id, customerCode: customer.code, customerName: customer.legalName,
      issueDate: form.issueDate, dueDate: form.dueDate || null, currency: 'EUR', paymentMethodId: form.paymentMethodId || null,
      notes: form.notes.trim() || null, confirm: form.confirm,
      lines: lines.map((line) => ({ productId: line.productId || null, productCode: line.productCode || null, description: line.description.trim(), quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), discountPercentage: Number(line.discountPercentage), taxPercentage: Number(line.taxPercentage) })),
    }
    try { await apiFetch<CommercialDocument>('/api/v1/documents', { method: 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  if (loadingOptions) return <LoadingState label="Cargando clientes y catálogo…" />
  return <form onSubmit={submit}>
    <div className="form-grid document-header-form">
      <Field label="Tipo" htmlFor="document-type" required><select id="document-type" value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value as DocumentType })}>{documentTypes.map((item) => <option key={item} value={item}>{documentTypeLabel[item]}</option>)}</select></Field>
      <Field label="Cliente" htmlFor="document-customer" required><select id="document-customer" value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })} required><option value="">Seleccionar cliente</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.code} · {customer.legalName}</option>)}</select></Field>
      <Field label="Fecha de emisión" htmlFor="document-date" required><input id="document-date" type="date" value={form.issueDate} onChange={(event) => setForm({ ...form, issueDate: event.target.value })} required /></Field>
      <Field label="Fecha de vencimiento" htmlFor="document-due"><input id="document-due" type="date" value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} /></Field>
      <Field label="Forma de pago" htmlFor="document-payment"><select id="document-payment" value={form.paymentMethodId} onChange={(event) => setForm({ ...form, paymentMethodId: event.target.value })}><option value="">Sin forma asignada</option>{paymentMethods.map((method) => <option key={method.id} value={method.id}>{method.code} · {method.name}</option>)}</select></Field>
      <Field label="Estado inicial" htmlFor="document-confirm"><label className="switch-row" htmlFor="document-confirm"><input id="document-confirm" type="checkbox" checked={form.confirm} onChange={(event) => setForm({ ...form, confirm: event.target.checked })} /><span>Confirmar al guardar</span></label></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">Detalle</span><h3>Líneas del documento</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setLines((current) => [...current, { productId: '', productCode: '', description: '', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21' }])}><Plus size={15} />Añadir línea</button></div>
    <div className="line-editor">{lines.map((line, index) => <div className="line-editor-row" key={index}>
      <div className="line-product"><label htmlFor={`line-product-${index}`}>Producto</label><select id={`line-product-${index}`} value={line.productId} onChange={(event) => chooseProduct(index, event.target.value)}><option value="">Línea libre</option>{products.map((product) => <option key={product.id} value={product.id}>{product.code} · {product.name}</option>)}</select></div>
      <div className="line-description"><label htmlFor={`line-description-${index}`}>Descripción</label><input id={`line-description-${index}`} value={line.description} onChange={(event) => updateLine(index, 'description', event.target.value)} required /></div>
      <div><label htmlFor={`line-quantity-${index}`}>Cantidad</label><input id={`line-quantity-${index}`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => updateLine(index, 'quantity', event.target.value)} required /></div>
      <div><label htmlFor={`line-price-${index}`}>Precio</label><input id={`line-price-${index}`} type="number" min="0" step="0.0001" value={line.unitPrice} onChange={(event) => updateLine(index, 'unitPrice', event.target.value)} required /></div>
      <div><label htmlFor={`line-discount-${index}`}>Dto. %</label><input id={`line-discount-${index}`} type="number" min="0" max="100" step="0.01" value={line.discountPercentage} onChange={(event) => updateLine(index, 'discountPercentage', event.target.value)} /></div>
      <div><label htmlFor={`line-tax-${index}`}>IVA %</label><input id={`line-tax-${index}`} type="number" min="0" max="100" step="0.01" value={line.taxPercentage} onChange={(event) => updateLine(index, 'taxPercentage', event.target.value)} /></div>
      <button className="icon-button line-remove" type="button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} aria-label={`Eliminar línea ${index + 1}`}><Trash2 size={16} /></button>
    </div>)}</div>
    <div className="document-footer-form"><Field label="Notas" htmlFor="document-notes"><textarea id="document-notes" rows={3} value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} /></Field><div className="totals-card"><span><small>Base</small><strong>{formatCurrency(totals.net)}</strong></span><span><small>IVA</small><strong>{formatCurrency(totals.tax)}</strong></span><span className="grand-total"><small>Total</small><strong>{formatCurrency(totals.total)}</strong></span></div></div>
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel="Crear documento" />
  </form>
}

function DocumentDetail({ document, onAction }: { document: CommercialDocument; onAction: (action: 'convert' | 'paid', document: CommercialDocument) => void }) {
  const convertible = document.status === 'CONFIRMED' && (document.type === 'QUOTE' || document.type === 'DELIVERY_NOTE')
  const payable = document.type === 'INVOICE' && document.paymentStatus !== 'PAID'
  return <div className="document-detail">
    <div className="detail-summary"><div><small>Cliente</small><strong>{document.customerName}</strong><span>{document.customerCode}</span></div><div><small>Emisión</small><strong>{formatDate(document.issueDate)}</strong><span>Vence {formatDate(document.dueDate)}</span></div><div><small>Estado</small><StatusBadge tone={statusTone(document.status)}>{documentStatusLabel[document.status]}</StatusBadge><span>{paymentStatusLabel[document.paymentStatus]}</span></div><div><small>Total</small><strong className="detail-total">{formatCurrency(document.totalAmount)}</strong><span>{document.currency}</span></div></div>
    <div className="table-scroll detail-lines"><table><thead><tr><th>#</th><th>Descripción</th><th className="align-right">Cantidad</th><th className="align-right">Precio</th><th className="align-right">Dto.</th><th className="align-right">Total</th></tr></thead><tbody>{document.lines.map((line) => <tr key={line.id || line.order}><td>{line.order}</td><td><strong>{line.description}</strong>{line.productCode && <small>{line.productCode}</small>}</td><td className="align-right">{Number(line.quantity).toLocaleString('es-ES')}</td><td className="align-right">{formatCurrency(line.unitPrice)}</td><td className="align-right">{Number(line.discountPercentage).toLocaleString('es-ES')} %</td><td className="align-right"><strong>{formatCurrency(line.totalAmount)}</strong></td></tr>)}</tbody></table></div>
    <div className="detail-totals"><span>Base <strong>{formatCurrency(document.netAmount)}</strong></span><span>IVA <strong>{formatCurrency(document.taxAmount)}</strong></span><span>Total <strong>{formatCurrency(document.totalAmount)}</strong></span></div>
    {(convertible || payable) && <div className="modal-action-strip">{convertible && <button type="button" className="button button-primary" onClick={() => onAction('convert', document)}>Convertir al siguiente documento <ArrowRight size={17} /></button>}{payable && <button type="button" className="button button-secondary" onClick={() => onAction('paid', document)}><CheckCircle2 size={17} />Marcar como cobrada</button>}</div>}
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
