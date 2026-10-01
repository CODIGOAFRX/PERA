import { ArrowRight, Ban, CheckCircle2, Pencil, Plus, ShoppingCart, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useConfirm } from '../components/ConfirmDialog'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FieldControl, FormActions, FormErrors, useFormErrors } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge, type BadgeTone } from '../components/StatusBadge'
import { TableCaption } from '../components/TableCaption'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { documentStatusKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { localIsoDate } from '../lib/date'
import { formatCurrency, formatDate, formatNumber } from '../lib/format'
import { calculatePurchasePreview } from '../lib/purchase'
import type { CurrencyDefinition, DocumentStatus, FlatPage, PageResponse, Product, PurchaseDocument, PurchaseDocumentInput, PurchaseDocumentType, Supplier, Warehouse } from '../types/api'

const purchaseTypes: PurchaseDocumentType[] = ['PURCHASE_ORDER', 'GOODS_RECEIPT', 'SUPPLIER_INVOICE']
const statuses = Object.keys(documentStatusKey) as DocumentStatus[]
const typeLabels: Record<PurchaseDocumentType, [string, string]> = {
  PURCHASE_ORDER: ['Pedido a proveedor', 'Purchase order'],
  GOODS_RECEIPT: ['Albarán de entrada', 'Goods receipt'],
  SUPPLIER_INVOICE: ['Factura de proveedor', 'Supplier invoice'],
}
const conversionTargets: Record<PurchaseDocumentType, PurchaseDocumentType[]> = {
  PURCHASE_ORDER: ['GOODS_RECEIPT', 'SUPPLIER_INVOICE'],
  GOODS_RECEIPT: ['SUPPLIER_INVOICE'],
  SUPPLIER_INVOICE: [],
}

type PurchaseAction = { kind: 'confirm' } | { kind: 'cancel' } | { kind: 'delete' } | { kind: 'convert'; target: PurchaseDocumentType }

export function PurchasesPage() {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const typeLabel = (type: PurchaseDocumentType) => typeLabels[type][language === 'es' ? 0 : 1]
  const [data, setData] = useState<FlatPage<PurchaseDocument> | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim())
  const [type, setType] = useState<PurchaseDocumentType | ''>('')
  const [status, setStatus] = useState<DocumentStatus | ''>('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<PurchaseDocument | 'new' | null>(null)
  const [selected, setSelected] = useState<PurchaseDocument | null>(null)
  const [refresh, setRefresh] = useState(0)
  const [acting, setActing] = useState(false)
  const { notify } = useToast()
  const confirm = useConfirm()

  useEffect(() => setPage(0), [debouncedQuery, type, status, fromDate, toDate])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'issueDate,desc' })
    params.append('sort', 'number,desc')
    if (debouncedQuery) params.set('query', debouncedQuery)
    if (type) params.set('type', type)
    if (status) params.set('status', status)
    if (fromDate) params.set('fromDate', fromDate)
    if (toDate) params.set('toDate', toDate)
    apiFetch<FlatPage<PurchaseDocument>>(`/api/v1/purchase-documents?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, debouncedQuery, type, status, fromDate, toDate, refresh])

  const runAction = async (action: PurchaseAction, document: PurchaseDocument) => {
    const receivesStock = document.type === 'GOODS_RECEIPT' || (document.type === 'SUPPLIER_INVOICE' && !document.stockReceivedUpstream && document.warehouseId !== null)
    const message = action.kind === 'confirm'
      ? receivesStock
        ? c(`¿Confirmar ${document.number}? Se dará entrada en almacén a sus productos.`, `Confirm ${document.number}? Its products will be received into the warehouse.`)
        : c(`¿Confirmar ${document.number}?`, `Confirm ${document.number}?`)
      : action.kind === 'cancel'
        ? document.stockPosted
          ? c(`¿Anular ${document.number}? Se retirará del almacén la mercancía que entró con este documento.`, `Cancel ${document.number}? The goods received with this document will be removed from the warehouse.`)
          : c(`¿Anular ${document.number}?`, `Cancel ${document.number}?`)
        : action.kind === 'delete'
          ? c(`¿Eliminar el borrador ${document.number}?`, `Delete draft ${document.number}?`)
          : c(`¿Convertir ${document.number} en ${typeLabel(action.target).toLowerCase()}?`, `Convert ${document.number} into a ${typeLabel(action.target).toLowerCase()}?`)
    // Confirmar o anular mueve existencias: un doble clic no debe repetirlo.
    if (acting || !(await confirm({ message, danger: action.kind === 'cancel' || action.kind === 'delete' }))) return
    setActing(true)
    try {
      const base = `/api/v1/purchase-documents/${document.id}`
      if (action.kind === 'delete') await apiFetch(base, { method: 'DELETE' })
      else if (action.kind === 'convert') await apiFetch(`${base}/convert`, { method: 'POST', body: JSON.stringify({ targetType: action.target, issueDate: localIsoDate() }) })
      else await apiFetch(`${base}/${action.kind}`, { method: 'POST' })
      notify(action.kind === 'confirm' ? c('Documento confirmado.', 'Document confirmed.')
        : action.kind === 'cancel' ? c('Documento anulado.', 'Document cancelled.')
          : action.kind === 'delete' ? c('Borrador eliminado.', 'Draft deleted.')
            : c('Documento convertido. Revisa el borrador creado.', 'Document converted. Review the new draft.'))
      setSelected(null); setRefresh((value) => value + 1)
    } catch (cause) { notify(errorMessage(cause), 'error') } finally { setActing(false) }
  }

  const saved = () => { setEditing(null); setRefresh((value) => value + 1); notify(c('Documento de compra guardado.', 'Purchase document saved.')) }
  const newButton = <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nuevo documento', 'New document')}</button>

  return <div className="page-stack">
    <PageHeader eyebrow={c('Aprovisionamiento', 'Procurement')} title={c('Compras', 'Purchases')} description={c('Pedidos a proveedor, albaranes de entrada y facturas recibidas.', 'Purchase orders, goods receipts and supplier invoices.')} icon={ShoppingCart} actions={newButton} />
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={c('Buscar por número, proveedor o referencia', 'Search by number, supplier or reference')}>
        <select aria-label={t('sales.filterType')} value={type} onChange={(event) => setType(event.target.value as PurchaseDocumentType | '')}><option value="">{t('sales.allTypes')}</option>{purchaseTypes.map((item) => <option key={item} value={item}>{typeLabel(item)}</option>)}</select>
        <select aria-label={t('sales.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value as DocumentStatus | '')}><option value="">{t('sales.allStatuses')}</option>{statuses.map((item) => <option key={item} value={item}>{t(documentStatusKey[item])}</option>)}</select>
        <label className="toolbar-date"><span>{t('common.from')}</span><input aria-label={t('common.from')} type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} /></label>
        <label className="toolbar-date"><span>{t('common.to')}</span><input aria-label={t('common.to')} type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} /></label>
      </TableToolbar>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table>
          <TableCaption es="Documentos de compra" en="Purchase documents" />
          <thead><tr><th>{t('sales.number')}</th><th>{t('sales.type')}</th><th>{c('Proveedor', 'Supplier')}</th><th>{c('Ref. proveedor', 'Supplier ref.')}</th><th>{t('sales.date')}</th><th>{t('sales.status')}</th><th className="align-right">{t('sales.total')}</th></tr></thead>
          <tbody>{data.content.map((document) => <tr key={document.id} className={`clickable-row${document.status === 'CONVERTED' ? ' document-row-converted' : ''}`} onClick={() => setSelected(document)}>
            <td><strong className="document-number">{document.number}</strong></td>
            <td>{typeLabel(document.type)}</td>
            <td><strong>{document.supplierName}</strong><small>{document.supplierCode}</small></td>
            <td>{document.supplierReference || '—'}</td>
            <td>{formatDate(document.issueDate, locale)}</td>
            <td><StatusBadge tone={statusTone(document.status)}>{t(documentStatusKey[document.status])}</StatusBadge></td>
            <td className="align-right"><strong>{formatCurrency(document.totalAmount, document.currencyCode, locale)}</strong></td>
          </tr>)}</tbody>
        </table></div>
        <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
      </> : <EmptyState title={c('No hay documentos de compra', 'There are no purchase documents')} description={query || type || status || fromDate || toDate ? t('common.noResults') : c('Registra un pedido, un albarán de entrada o una factura de proveedor.', 'Record a purchase order, a goods receipt or a supplier invoice.')} action={!query && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}>{c('Crear documento', 'Create document')}</button>} />}
    </section>

    <Modal open={editing !== null} title={editing === 'new' || editing === null ? c('Nuevo documento de compra', 'New purchase document') : c(`Editar ${editing.number}`, `Edit ${editing.number}`)} description={c('Se guarda como borrador. Las existencias solo se mueven al confirmarlo.', 'It is saved as a draft. Stock only moves when it is confirmed.')} onClose={() => setEditing(null)} size="large">
      {editing && <PurchaseForm key={editing === 'new' ? 'new' : editing.id} document={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={saved} />}
    </Modal>
    <Modal open={selected !== null} title={selected?.number || c('Documento de compra', 'Purchase document')} description={selected ? `${typeLabel(selected.type)} · ${selected.supplierName}` : ''} onClose={() => setSelected(null)} size="large">
      {selected && <PurchaseDetail document={selected} busy={acting} onAction={(action) => void runAction(action, selected)} onEdit={() => { setEditing(selected); setSelected(null) }} />}
    </Modal>
  </div>
}

const emptyLine = { productId: '', productCode: '', description: '', unitOfMeasure: 'UNIT', quantity: '1', unitPrice: '0', discountPercentage: '0', taxPercentage: '21' }

function PurchaseForm({ document, onCancel, onSaved }: { document: PurchaseDocument | null; onCancel: () => void; onSaved: () => void }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [currencies, setCurrencies] = useState<CurrencyDefinition[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const [form, setForm] = useState({
    type: document?.type ?? 'PURCHASE_ORDER' as PurchaseDocumentType, supplierId: document?.supplierId ?? '', supplierReference: document?.supplierReference ?? '',
    issueDate: document?.issueDate ?? localIsoDate(), expectedDate: document?.expectedDate ?? '', warehouseId: document?.warehouseId ?? '',
    currencyCode: document?.currencyCode ?? 'EUR', notes: document?.notes ?? '',
  })
  const [lines, setLines] = useState(document ? document.lines.map((line) => ({ productId: line.productId ?? '', productCode: line.productCode ?? '', description: line.description, unitOfMeasure: line.unitOfMeasure, quantity: String(line.quantity), unitPrice: String(line.unitPrice), discountPercentage: String(line.discountPercentage), taxPercentage: String(line.taxPercentage) })) : [emptyLine])

  useEffect(() => {
    Promise.all([
      apiFetch<PageResponse<Supplier>>('/api/v1/suppliers?size=100&sort=legalName,asc'),
      apiFetch<PageResponse<Product>>('/api/v1/products?size=100&sort=name,asc'),
      apiFetch<FlatPage<Warehouse>>('/api/v1/warehouses?active=true&size=200&sort=code,asc').catch(() => null),
      apiFetch<CurrencyDefinition[]>('/api/v1/currencies').catch(() => []),
    ]).then(([supplierPage, productPage, warehousePage, currencyList]) => {
      setSuppliers(supplierPage.content.filter((item) => item.active || item.id === document?.supplierId))
      setProducts(productPage.content.filter((item) => item.active))
      const activeWarehouses = warehousePage?.content ?? []
      setWarehouses(activeWarehouses)
      const activeCurrencies = currencyList.filter((item) => item.active)
      setCurrencies(activeCurrencies)
      if (!document) {
        const preferredCurrency = activeCurrencies.find((item) => item.baseCurrency) ?? activeCurrencies[0]
        setForm((current) => ({ ...current, currencyCode: preferredCurrency?.code ?? 'EUR', warehouseId: activeWarehouses.find((item) => item.defaultWarehouse)?.id ?? '' }))
      }
    }).catch((cause) => setError(errorMessage(cause))).finally(() => setLoadingOptions(false))
  }, [document])

  const defaultWarehouseId = warehouses.find((item) => item.defaultWarehouse)?.id ?? ''
  // Una factura directa con almacén da entrada a la mercancía: no se preselecciona para no duplicar
  // la entrada de un albarán que ya se registró aparte.
  const changeType = (type: PurchaseDocumentType) => setForm((current) => ({ ...current, type, warehouseId: type === 'SUPPLIER_INVOICE' ? '' : current.warehouseId || defaultWarehouseId }))
  const updateLine = (index: number, name: string, value: string) => setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, [name]: value } : line))
  const chooseProduct = (index: number, productId: string) => {
    const product = products.find((item) => item.id === productId)
    setLines((current) => current.map((line, lineIndex) => lineIndex !== index ? line : product
      ? { ...line, productId, productCode: product.code, description: product.name, unitOfMeasure: product.unitOfMeasure, taxPercentage: String(product.taxRate) }
      : { ...line, productId: '', productCode: '', unitOfMeasure: 'UNIT' }))
  }
  const totals = useMemo(() => calculatePurchasePreview(lines), [lines])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const supplier = suppliers.find((item) => item.id === form.supplierId)
    if (!supplier) { setError(c('Selecciona un proveedor.', 'Select a supplier.')); setSaving(false); return }
    if (lines.some((line) => !line.description.trim() || Number(line.quantity) <= 0)) { setError(t('sales.reviewLines')); setSaving(false); return }
    const payload: PurchaseDocumentInput = {
      type: form.type, supplierId: supplier.id, supplierCode: supplier.code, supplierName: supplier.legalName, supplierTaxId: supplier.taxId,
      supplierReference: form.supplierReference.trim() || null, issueDate: form.issueDate, expectedDate: form.expectedDate || null,
      warehouseId: form.warehouseId || null, currencyCode: form.currencyCode, notes: form.notes.trim() || null,
      lines: lines.map((line) => ({ productId: line.productId || null, productCode: line.productCode || null, description: line.description.trim(), unitOfMeasure: line.unitOfMeasure, quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), discountPercentage: Number(line.discountPercentage), taxPercentage: Number(line.taxPercentage) })),
    }
    try {
      await apiFetch<PurchaseDocument>(document ? `/api/v1/purchase-documents/${document.id}` : '/api/v1/purchase-documents', { method: document ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      onSaved()
    } catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  if (loadingOptions) return <LoadingState label={t('sales.loadingOptions')} />
  const invoice = form.type === 'SUPPLIER_INVOICE'
  const warehouseHint = invoice
    ? document?.stockReceivedUpstream
      ? c('La mercancía ya entró con el albarán: esta factura no mueve existencias.', 'The goods were already received with the receipt: this invoice does not move stock.')
      : c('Indícalo solo si la mercancía llega con esta factura, sin albarán previo.', 'Set it only if the goods arrive with this invoice, without a previous receipt.')
    : form.type === 'GOODS_RECEIPT' ? c('Obligatorio para confirmar: aquí entra la mercancía.', 'Required to confirm: the goods are received here.') : undefined
  return <form onSubmit={submit}><FormErrors value={formErrors.context}>
    <div className="form-grid document-header-form">
      <Field label={t('sales.type')} htmlFor="purchase-type" name="type" required><select id="purchase-type" value={form.type} onChange={(event) => changeType(event.target.value as PurchaseDocumentType)} disabled={Boolean(document)}>{purchaseTypes.map((item) => <option key={item} value={item}>{typeLabels[item][language === 'es' ? 0 : 1]}</option>)}</select></Field>
      <Field label={c('Proveedor', 'Supplier')} htmlFor="purchase-supplier" name="supplierId" required><select id="purchase-supplier" value={form.supplierId} onChange={(event) => setForm({ ...form, supplierId: event.target.value })} disabled={Boolean(document?.sourceDocumentId)} required><option value="">{c('Selecciona un proveedor', 'Select a supplier')}</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.code} · {supplier.legalName}</option>)}</select></Field>
      <Field label={invoice ? c('Nº de factura del proveedor', 'Supplier invoice number') : c('Referencia del proveedor', 'Supplier reference')} htmlFor="purchase-reference" name="supplierReference" required={invoice}><input id="purchase-reference" value={form.supplierReference} onChange={(event) => setForm({ ...form, supplierReference: event.target.value })} maxLength={80} /></Field>
      <Field label={t('sales.issueDate')} htmlFor="purchase-date" name="issueDate" required><input id="purchase-date" type="date" value={form.issueDate} onChange={(event) => setForm({ ...form, issueDate: event.target.value })} required /></Field>
      <Field label={c('Fecha prevista', 'Expected date')} htmlFor="purchase-expected" name="expectedDate"><input id="purchase-expected" type="date" min={form.issueDate} value={form.expectedDate} onChange={(event) => setForm({ ...form, expectedDate: event.target.value })} /></Field>
      <Field label={c('Almacén de entrada', 'Receiving warehouse')} htmlFor="purchase-warehouse" name="warehouseId" hint={warehouseHint}><select id="purchase-warehouse" value={form.warehouseId} onChange={(event) => setForm({ ...form, warehouseId: event.target.value })}><option value="">{c('Sin almacén', 'No warehouse')}</option>{warehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.code} · {warehouse.name}</option>)}</select></Field>
      <Field label={c('Moneda', 'Currency')} htmlFor="purchase-currency" name="currencyCode" required><select id="purchase-currency" value={form.currencyCode} onChange={(event) => setForm({ ...form, currencyCode: event.target.value })}>{currencies.length ? currencies.map((currency) => <option key={currency.code} value={currency.code}>{currency.code} · {currency.name}</option>) : <option value={form.currencyCode}>{form.currencyCode}</option>}</select></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{t('sales.detail')}</span><h3>{t('sales.documentLines')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setLines((current) => [...current, emptyLine])}><Plus size={15} />{t('sales.addLine')}</button></div>
    <div className="line-editor">{lines.map((line, index) => <div className="line-editor-row" key={index}>
      <div className="line-product"><label htmlFor={`purchase-line-product-${index}`}>{t('sales.product')}</label><FieldControl htmlFor={`purchase-line-product-${index}`} name={`lines[${index}].productId`}><select id={`purchase-line-product-${index}`} value={line.productId} onChange={(event) => chooseProduct(index, event.target.value)}><option value="">{t('sales.freeLine')}</option>{products.map((product) => <option key={product.id} value={product.id}>{product.code} · {product.name}</option>)}</select></FieldControl></div>
      <div className="line-description"><label htmlFor={`purchase-line-description-${index}`}>{t('sales.lineDescription')}</label><FieldControl htmlFor={`purchase-line-description-${index}`} name={`lines[${index}].description`}><input id={`purchase-line-description-${index}`} value={line.description} onChange={(event) => updateLine(index, 'description', event.target.value)} maxLength={300} required /></FieldControl></div>
      <div><label htmlFor={`purchase-line-quantity-${index}`}>{t('sales.quantity')}</label><FieldControl htmlFor={`purchase-line-quantity-${index}`} name={`lines[${index}].quantity`}><input id={`purchase-line-quantity-${index}`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => updateLine(index, 'quantity', event.target.value)} required /></FieldControl></div>
      <div><label htmlFor={`purchase-line-price-${index}`}>{c('Coste', 'Cost')}</label><FieldControl htmlFor={`purchase-line-price-${index}`} name={`lines[${index}].unitPrice`}><input id={`purchase-line-price-${index}`} type="number" min="0" step="0.000001" value={line.unitPrice} onChange={(event) => updateLine(index, 'unitPrice', event.target.value)} required /></FieldControl></div>
      <div><label htmlFor={`purchase-line-discount-${index}`}>{t('sales.discount')}</label><FieldControl htmlFor={`purchase-line-discount-${index}`} name={`lines[${index}].discountPercentage`}><input id={`purchase-line-discount-${index}`} type="number" min="0" max="100" step="0.01" value={line.discountPercentage} onChange={(event) => updateLine(index, 'discountPercentage', event.target.value)} /></FieldControl></div>
      <div><label htmlFor={`purchase-line-tax-${index}`}>{t('sales.tax')}</label><FieldControl htmlFor={`purchase-line-tax-${index}`} name={`lines[${index}].taxPercentage`}><input id={`purchase-line-tax-${index}`} type="number" min="0" max="100" step="0.01" value={line.taxPercentage} onChange={(event) => updateLine(index, 'taxPercentage', event.target.value)} /></FieldControl></div>
      <button className="icon-button line-remove" type="button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} aria-label={t('sales.deleteLine', { number: index + 1 })}><Trash2 size={16} /></button>
    </div>)}</div>
    <div className="document-footer-form"><Field label={t('sales.notes')} htmlFor="purchase-notes" name="notes"><textarea id="purchase-notes" rows={3} maxLength={1000} value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} /></Field><div className="totals-card"><span><small>{t('sales.net')}</small><strong>{formatCurrency(totals.net, form.currencyCode, locale)}</strong></span><span><small>{t('catalog.tax')}</small><strong>{formatCurrency(totals.tax, form.currencyCode, locale)}</strong></span><span className="grand-total"><small>{t('sales.total')}</small><strong>{formatCurrency(totals.total, form.currencyCode, locale)}</strong></span></div></div>
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={document ? t('common.save') : c('Guardar borrador', 'Save draft')} />
  </FormErrors></form>
}

function PurchaseDetail({ document, busy, onAction, onEdit }: { document: PurchaseDocument; busy: boolean; onAction: (action: PurchaseAction) => void; onEdit: () => void }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const draft = document.status === 'DRAFT'
  const confirmed = document.status === 'CONFIRMED'
  const targets = confirmed ? conversionTargets[document.type] : []
  const stockNote = document.stockPosted ? c('Con entrada en almacén', 'Received into the warehouse')
    : document.stockReceivedUpstream ? c('Mercancía recibida con el albarán', 'Goods received with the receipt') : c('Sin movimiento de almacén', 'No stock movement')
  return <div className="document-detail">
    <div className="detail-summary">
      <div><small>{c('Proveedor', 'Supplier')}</small><strong>{document.supplierName}</strong><span>{document.supplierReference ? `Ref. ${document.supplierReference}` : document.supplierCode}</span></div>
      <div><small>{t('sales.issue')}</small><strong>{formatDate(document.issueDate, locale)}</strong><span>{document.expectedDate ? c(`Prevista ${formatDate(document.expectedDate, locale)}`, `Expected ${formatDate(document.expectedDate, locale)}`) : c('Sin fecha prevista', 'No expected date')}</span></div>
      <div><small>{t('sales.status')}</small><StatusBadge tone={statusTone(document.status)}>{t(documentStatusKey[document.status])}</StatusBadge><span>{stockNote}</span></div>
      <div><small>{t('sales.total')}</small><strong className="detail-total">{formatCurrency(document.totalAmount, document.currencyCode, locale)}</strong><span>{document.currencyCode}</span></div>
    </div>
    <div className="table-scroll detail-lines"><table>
      <TableCaption es="Líneas del documento" en="Document lines" />
      <thead><tr><th>#</th><th>{t('sales.lineDescription')}</th><th className="align-right">{t('sales.quantity')}</th><th className="align-right">{c('Coste', 'Cost')}</th><th className="align-right">{t('sales.discount')}</th><th className="align-right">{t('sales.tax')}</th><th className="align-right">{t('sales.net')}</th></tr></thead>
      <tbody>{document.lines.map((line) => <tr key={line.id}><td>{line.sequence}</td><td><strong>{line.description}</strong>{line.productCode && <small>{line.productCode}</small>}</td><td className="align-right">{formatNumber(line.quantity, locale, 6)}</td><td className="align-right">{formatCurrency(line.unitPrice, document.currencyCode, locale)}</td><td className="align-right">{formatNumber(line.discountPercentage, locale, 4)} %</td><td className="align-right">{formatNumber(line.taxPercentage, locale, 4)} %</td><td className="align-right"><strong>{formatCurrency(line.netAmount, document.currencyCode, locale)}</strong></td></tr>)}</tbody>
    </table></div>
    <div className="detail-totals"><span>{t('sales.net')} <strong>{formatCurrency(document.netAmount, document.currencyCode, locale)}</strong></span><span>{t('catalog.tax')} <strong>{formatCurrency(document.taxAmount, document.currencyCode, locale)}</strong></span><span>{t('sales.total')} <strong>{formatCurrency(document.totalAmount, document.currencyCode, locale)}</strong></span></div>
    {document.notes && <p className="detail-notes">{document.notes}</p>}
    {(draft || confirmed) && <div className="modal-action-strip">
      {draft && <button type="button" className="button button-danger" disabled={busy} onClick={() => onAction({ kind: 'delete' })}><Trash2 size={17} />{c('Eliminar borrador', 'Delete draft')}</button>}
      {draft && <button type="button" className="button button-secondary" disabled={busy} onClick={onEdit}><Pencil size={17} />{c('Editar', 'Edit')}</button>}
      {draft && <button type="button" className="button button-primary" disabled={busy} onClick={() => onAction({ kind: 'confirm' })}><CheckCircle2 size={17} />{t('sales.confirmDocument')}</button>}
      {confirmed && <button type="button" className="button button-danger" disabled={busy} onClick={() => onAction({ kind: 'cancel' })}><Ban size={17} />{c('Anular', 'Cancel document')}</button>}
      {targets.map((target) => <button key={target} type="button" className="button button-primary" disabled={busy} onClick={() => onAction({ kind: 'convert', target })}>{target === 'GOODS_RECEIPT' ? c('Recibir mercancía', 'Receive goods') : c('Registrar factura', 'Record invoice')} <ArrowRight size={17} /></button>)}
    </div>}
  </div>
}

function statusTone(status: DocumentStatus): BadgeTone {
  if (status === 'CONFIRMED') return 'success'
  if (status === 'CANCELLED') return 'danger'
  if (status === 'CONVERTED') return 'info'
  return 'neutral'
}
