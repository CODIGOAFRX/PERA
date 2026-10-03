import { CheckCircle2, ClipboardList, MessageSquarePlus, MessageSquareWarning, Pencil, Plus, RotateCcw, Settings2, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ClaimCatalogTab } from '../components/claims/ClaimCatalogTab'
import { useConfirm } from '../components/ConfirmDialog'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FieldControl, FormActions, FormErrors, useFormErrors } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { TableCaption } from '../components/TableCaption'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { catalogName, claimCatalogKinds, claimCatalogLabels, claimFieldByKind, pickLabel } from '../lib/claims'
import { localIsoDate } from '../lib/date'
import { formatDate, formatDateTime, formatNumber } from '../lib/format'
import type { Claim, ClaimCatalogItem, ClaimCatalogKind, Customer, FlatPage, PageResponse, Product } from '../types/api'

type Tab = 'claims' | 'tables'
type StatusFilter = 'OPEN' | 'CLOSED' | 'OVERDUE' | ''

export function ClaimsPage() {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [tab, setTab] = useState<Tab>('claims')
  const [catalog, setCatalog] = useState<ClaimCatalogItem[]>([])
  const [catalogVersion, setCatalogVersion] = useState(0)

  // Las tablas de clasificación se usan en la lista, en el formulario y en el detalle.
  useEffect(() => {
    apiFetch<ClaimCatalogItem[]>('/api/v1/claim-catalog').then(setCatalog).catch(() => setCatalog([]))
  }, [catalogVersion])

  return <div className="page-stack">
    <PageHeader eyebrow={c('Calidad', 'Quality')} title={c('Reclamaciones', 'Claims')} description={c('Reclamaciones de clientes, su causa, quién responde y cómo se resuelven.', 'Customer claims, their cause, who is responsible and how they are resolved.')} icon={MessageSquareWarning} />
    <nav className="workspace-tabs" aria-label={c('Áreas de reclamaciones', 'Claim areas')}>
      <button type="button" className={tab === 'claims' ? 'active' : ''} onClick={() => setTab('claims')}><ClipboardList size={15} />{c('Reclamaciones', 'Claims')}</button>
      <button type="button" className={tab === 'tables' ? 'active' : ''} onClick={() => setTab('tables')}><Settings2 size={15} />{c('Tablas de clasificación', 'Classification tables')}</button>
    </nav>
    {tab === 'claims' ? <ClaimsList catalog={catalog} /> : <ClaimCatalogTab items={catalog} onChanged={() => setCatalogVersion((value) => value + 1)} />}
  </div>
}

function ClaimsList({ catalog }: { catalog: ClaimCatalogItem[] }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [data, setData] = useState<FlatPage<Claim> | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim())
  const [status, setStatus] = useState<StatusFilter>('OPEN')
  const [reasonId, setReasonId] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<Claim | null>(null)
  const [editing, setEditing] = useState<Claim | 'new' | null>(null)
  const [closing, setClosing] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const confirm = useConfirm()
  const reasons = catalog.filter((item) => item.kind === 'REASON')

  useEffect(() => setPage(0), [debouncedQuery, status, reasonId])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'claimDate,desc' })
    params.append('sort', 'number,desc')
    if (debouncedQuery) params.set('query', debouncedQuery)
    if (status === 'OVERDUE') params.set('overdue', 'true')
    else if (status) params.set('status', status)
    if (reasonId) params.set('reasonId', reasonId)
    apiFetch<FlatPage<Claim>>(`/api/v1/claims?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, debouncedQuery, status, reasonId, refresh])

  const updated = useCallback((claim: Claim, message: string) => { setSelected(claim); setRefresh((value) => value + 1); notify(message) }, [notify])
  const reopen = async (claim: Claim) => {
    try { updated(await apiFetch<Claim>(`/api/v1/claims/${claim.id}/reopen`, { method: 'POST' }), c('Reclamación reabierta.', 'Claim reopened.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }
  const remove = async (claim: Claim) => {
    if (!(await confirm({ message: c(`¿Eliminar la reclamación ${claim.number}?`, `Delete claim ${claim.number}?`), danger: true }))) return
    try { await apiFetch(`/api/v1/claims/${claim.id}`, { method: 'DELETE' }); setSelected(null); setRefresh((value) => value + 1); notify(c('Reclamación eliminada.', 'Claim deleted.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={c('Buscar por número, cliente, documento o texto', 'Search by number, customer, document or text')}>
        <select aria-label={t('sales.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)}>
          <option value="OPEN">{c('Abiertas', 'Open')}</option><option value="OVERDUE">{c('Seguimiento vencido', 'Follow-up overdue')}</option><option value="CLOSED">{c('Cerradas', 'Closed')}</option><option value="">{t('sales.allStatuses')}</option>
        </select>
        <select aria-label={c('Filtrar por motivo', 'Filter by reason')} value={reasonId} onChange={(event) => setReasonId(event.target.value)}><option value="">{c('Todos los motivos', 'All reasons')}</option>{reasons.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>
        <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nueva reclamación', 'New claim')}</button>
      </TableToolbar>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table>
          <TableCaption es="Reclamaciones" en="Claims" />
          <thead><tr><th>{c('Número', 'Number')}</th><th>{t('sales.date')}</th><th>{t('sales.customer')}</th><th>{c('Documento', 'Document')}</th><th>{c('Motivo', 'Reason')}</th><th>{t('sales.status')}</th><th className="align-right">{c('Días', 'Days')}</th></tr></thead>
          <tbody>{data.content.map((claim) => <tr key={claim.id} className="clickable-row" onClick={() => setSelected(claim)}>
            <td><strong className="document-number">{claim.number}</strong></td>
            <td>{formatDate(claim.claimDate, locale)}</td>
            <td><strong>{claim.customerName}</strong>{claim.customerCode && <small>{claim.customerCode}</small>}</td>
            <td>{claim.sourceDocumentNumber || '—'}</td>
            <td>{catalogName(catalog, claim.reasonId)}</td>
            <td><ClaimStatusBadge claim={claim} /></td>
            <td className="align-right">{claim.daysOpen}</td>
          </tr>)}</tbody>
        </table></div>
        <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
      </> : <EmptyState title={c('No hay reclamaciones', 'There are no claims')} description={query || reasonId || (status && status !== 'OPEN') ? t('common.noResults') : c('No hay reclamaciones abiertas. Regístralas aquí cuando un cliente avise de un problema.', 'There are no open claims. Record them here when a customer reports a problem.')} />}
    </section>

    <Modal open={selected !== null && !closing && editing === null} title={selected?.number ?? ''} description={selected ? `${selected.customerName} · ${formatDate(selected.claimDate, locale)}` : ''} onClose={() => setSelected(null)} size="large">
      {selected && <ClaimDetail claim={selected} catalog={catalog} onCommented={(claim) => updated(claim, c('Comentario añadido.', 'Comment added.'))} actions={<div className="modal-action-strip">
        {selected.status === 'OPEN' && selected.comments.length === 0 && <button type="button" className="button button-danger" onClick={() => void remove(selected)}><Trash2 size={17} />{c('Eliminar', 'Delete')}</button>}
        {selected.status === 'OPEN' && <button type="button" className="button button-secondary" onClick={() => setEditing(selected)}><Pencil size={17} />{c('Editar', 'Edit')}</button>}
        {selected.status === 'OPEN' && <button type="button" className="button button-primary" onClick={() => setClosing(true)}><CheckCircle2 size={17} />{c('Cerrar reclamación', 'Close claim')}</button>}
        {selected.status === 'CLOSED' && <button type="button" className="button button-secondary" onClick={() => void reopen(selected)}><RotateCcw size={17} />{c('Reabrir', 'Reopen')}</button>}
      </div>} />}
    </Modal>
    <Modal open={selected !== null && closing} title={c(`Cerrar ${selected?.number ?? ''}`, `Close ${selected?.number ?? ''}`)} description={c('Una reclamación se cierra cuando tiene resolución.', 'A claim is closed once it has a resolution.')} onClose={() => setClosing(false)}>
      {selected && <CloseClaimForm claim={selected} onCancel={() => setClosing(false)} onClosed={(claim) => { setClosing(false); updated(claim, c('Reclamación cerrada.', 'Claim closed.')) }} />}
    </Modal>
    <Modal open={editing !== null} title={editing === 'new' || editing === null ? c('Nueva reclamación', 'New claim') : c(`Editar ${editing.number}`, `Edit ${editing.number}`)} description={c('La clasificación se puede completar más adelante, según se investiga.', 'The classification can be completed later, as it is investigated.')} onClose={() => setEditing(null)} size="large">
      {editing && <ClaimForm key={editing === 'new' ? 'new' : editing.id} claim={editing === 'new' ? null : editing} catalog={catalog} onCancel={() => setEditing(null)} onSaved={(claim) => { setEditing(null); updated(claim, c('Reclamación guardada.', 'Claim saved.')) }} />}
    </Modal>
  </>
}

function ClaimStatusBadge({ claim }: { claim: Claim }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  if (claim.status === 'CLOSED') return <StatusBadge tone="success">{c('Cerrada', 'Closed')}</StatusBadge>
  return claim.overdue ? <StatusBadge tone="danger">{c('Seguimiento vencido', 'Follow-up overdue')}</StatusBadge> : <StatusBadge tone="warning">{c('Abierta', 'Open')}</StatusBadge>
}

function ClaimDetail({ claim, catalog, actions, onCommented }: { claim: Claim; catalog: ClaimCatalogItem[]; actions: React.ReactNode; onCommented: (claim: Claim) => void }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [comment, setComment] = useState('')
  const [saving, setSaving] = useState(false)
  const { notify } = useToast()

  const addComment = async (event: FormEvent) => {
    event.preventDefault()
    if (!comment.trim()) return
    setSaving(true)
    try { onCommented(await apiFetch<Claim>(`/api/v1/claims/${claim.id}/comments`, { method: 'POST', body: JSON.stringify({ text: comment.trim() }) })); setComment('') }
    catch (cause) { notify(errorMessage(cause), 'error') } finally { setSaving(false) }
  }

  return <div className="document-detail">
    <div className="detail-summary">
      <div><small>{t('sales.customer')}</small><strong>{claim.customerName}</strong><span>{claim.customerCode ?? ''}</span></div>
      <div><small>{c('Documento de origen', 'Source document')}</small><strong>{claim.sourceDocumentNumber || '—'}</strong><span>{claim.sourceDocumentDate ? formatDate(claim.sourceDocumentDate, locale) : ''}</span></div>
      <div><small>{t('sales.status')}</small><ClaimStatusBadge claim={claim} /><span>{claim.status === 'CLOSED' ? c(`Cerrada el ${formatDate(claim.closedOn, locale)}`, `Closed on ${formatDate(claim.closedOn, locale)}`) : claim.followUpDate ? c(`Seguimiento: ${formatDate(claim.followUpDate, locale)}`, `Follow-up: ${formatDate(claim.followUpDate, locale)}`) : ''}</span></div>
      <div><small>{c('Días abierta', 'Days open')}</small><strong className="detail-total">{claim.daysOpen}</strong><span>{c(`Registrada por ${claim.reportedByName}`, `Recorded by ${claim.reportedByName}`)}</span></div>
    </div>
    <p className="detail-notes">{claim.description}</p>
    <div className="table-scroll detail-lines"><table>
      <TableCaption es="Clasificación de la reclamación" en="Claim classification" />
      <tbody>{claimCatalogKinds.map((kind) => <tr key={kind}><th scope="row">{pickLabel(claimCatalogLabels[kind], language)}</th><td>{catalogName(catalog, claim[claimFieldByKind[kind]])}</td></tr>)}</tbody>
    </table></div>
    {claim.lines.length > 0 && <div className="table-scroll detail-lines"><table>
      <TableCaption es="Productos reclamados" en="Claimed products" />
      <thead><tr><th>#</th><th>{t('sales.lineDescription')}</th><th className="align-right">{t('sales.quantity')}</th></tr></thead>
      <tbody>{claim.lines.map((line) => <tr key={line.sequence}><td>{line.sequence}</td><td><strong>{line.description}</strong>{line.productCode && <small>{line.productCode}</small>}</td><td className="align-right">{formatNumber(line.quantity, locale, 6)}</td></tr>)}</tbody>
    </table></div>}
    {claim.closingNote && <p className="detail-notes">{c('Cierre: ', 'Closing: ')}{claim.closingNote}</p>}
    <div className="document-lines-heading"><div><span className="eyebrow">{c('Seguimiento', 'Follow-up')}</span><h3>{claim.comments.length === 1 ? c('1 comentario', '1 comment') : c(`${claim.comments.length} comentarios`, `${claim.comments.length} comments`)}</h3></div></div>
    {claim.comments.map((item) => <p className="detail-notes" key={item.id}><strong>{item.authorName}</strong> · {formatDateTime(item.createdAt, locale)}<br />{item.text}</p>)}
    <form className="claim-comment-form" onSubmit={addComment}>
      <label className="sr-only" htmlFor="claim-comment">{c('Nuevo comentario', 'New comment')}</label>
      <textarea id="claim-comment" rows={2} maxLength={2000} value={comment} onChange={(event) => setComment(event.target.value)} placeholder={c('Añade una nota de seguimiento: llamada, visita, decisión...', 'Add a follow-up note: call, visit, decision...')} />
      <button className="button button-secondary" type="submit" disabled={saving || !comment.trim()}><MessageSquarePlus size={17} />{c('Añadir comentario', 'Add comment')}</button>
    </form>
    {actions}
  </div>
}

function CloseClaimForm({ claim, onCancel, onClosed }: { claim: Claim; onCancel: () => void; onClosed: (claim: Claim) => void }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ closedOn: localIsoDate(), note: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { onClosed(await apiFetch<Claim>(`/api/v1/claims/${claim.id}/close`, { method: 'POST', body: JSON.stringify({ closedOn: form.closedOn, closingNote: form.note.trim() || null }) })) }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={c('Fecha de cierre', 'Closing date')} htmlFor="claim-closed-on" required><input id="claim-closed-on" type="date" min={claim.claimDate} value={form.closedOn} onChange={(event) => setForm({ ...form, closedOn: event.target.value })} required /></Field>
    <Field label={c('Nota de cierre', 'Closing note')} htmlFor="claim-closing-note" wide><textarea id="claim-closing-note" rows={2} maxLength={1000} value={form.note} onChange={(event) => setForm({ ...form, note: event.target.value })} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={c('Cerrar reclamación', 'Close claim')} /></form>
}

const emptyLine = { productId: '', productCode: '', description: '', quantity: '1' }

function ClaimForm({ claim, catalog, onCancel, onSaved }: { claim: Claim | null; catalog: ClaimCatalogItem[]; onCancel: () => void; onSaved: (claim: Claim) => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [customers, setCustomers] = useState<Customer[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [form, setForm] = useState({
    claimDate: claim?.claimDate ?? localIsoDate(), customerId: claim?.customerId ?? '', sourceDocumentNumber: claim?.sourceDocumentNumber ?? '',
    sourceDocumentDate: claim?.sourceDocumentDate ?? '', description: claim?.description ?? '', followUpDate: claim?.followUpDate ?? '',
  })
  const [classification, setClassification] = useState<Record<ClaimCatalogKind, string>>(() => Object.fromEntries(claimCatalogKinds.map((kind) => [kind, claim?.[claimFieldByKind[kind]] ?? ''])) as Record<ClaimCatalogKind, string>)
  const [lines, setLines] = useState(claim ? claim.lines.map((line) => ({ productId: line.productId ?? '', productCode: line.productCode ?? '', description: line.description, quantity: String(line.quantity) })) : [])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  useEffect(() => {
    Promise.all([
      apiFetch<PageResponse<Customer>>('/api/v1/customers?size=100&sort=legalName,asc'),
      apiFetch<PageResponse<Product>>('/api/v1/products?size=100&sort=name,asc').catch(() => null),
    ]).then(([customerPage, productPage]) => {
      setCustomers(customerPage.content.filter((item) => item.active || item.id === claim?.customerId))
      setProducts(productPage?.content.filter((item) => item.active) ?? [])
    }).catch((cause) => setError(errorMessage(cause))).finally(() => setLoadingOptions(false))
  }, [claim])

  // Se ofrecen los valores activos y, si la reclamación ya tenía uno desactivado, también ese.
  const options = (kind: ClaimCatalogKind) => catalog.filter((item) => item.kind === kind && (item.active || item.id === classification[kind]))
  const chooseProduct = (index: number, productId: string) => {
    const product = products.find((item) => item.id === productId)
    setLines((current) => current.map((line, lineIndex) => lineIndex !== index ? line : { ...line, productId, productCode: product?.code ?? '', description: product?.name ?? line.description }))
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const customer = customers.find((item) => item.id === form.customerId)
    if (!customer) { setError(c('Selecciona el cliente.', 'Select the customer.')); setSaving(false); return }
    const payload = {
      claimDate: form.claimDate, customerId: customer.id, customerCode: customer.code, customerName: customer.legalName,
      sourceDocumentId: null, sourceDocumentNumber: form.sourceDocumentNumber.trim() || null, sourceDocumentDate: form.sourceDocumentDate || null,
      description: form.description.trim(), followUpDate: form.followUpDate || null,
      ...Object.fromEntries(claimCatalogKinds.map((kind) => [claimFieldByKind[kind], classification[kind] || null])),
      lines: lines.filter((line) => line.description.trim()).map((line) => ({ productId: line.productId || null, productCode: line.productCode || null, description: line.description.trim(), quantity: Number(line.quantity) })),
    }
    try { onSaved(await apiFetch<Claim>(claim ? `/api/v1/claims/${claim.id}` : '/api/v1/claims', { method: claim ? 'PUT' : 'POST', body: JSON.stringify(payload) })) }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  if (loadingOptions) return <LoadingState />
  return <form onSubmit={submit}><FormErrors value={formErrors.context}>
    <div className="form-grid document-header-form">
      <Field label={c('Fecha', 'Date')} htmlFor="claim-date" name="claimDate" required><input id="claim-date" type="date" value={form.claimDate} onChange={(event) => setForm({ ...form, claimDate: event.target.value })} required /></Field>
      <Field label={t('sales.customer')} htmlFor="claim-customer" name="customerId" required><select id="claim-customer" value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })} required><option value="">{t('sales.selectCustomer')}</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.code} · {customer.legalName}</option>)}</select></Field>
      <Field label={c('Documento de origen', 'Source document')} htmlFor="claim-document" name="sourceDocumentNumber" hint={c('Número del albarán o factura reclamados.', 'Number of the claimed delivery note or invoice.')}><input id="claim-document" value={form.sourceDocumentNumber} onChange={(event) => setForm({ ...form, sourceDocumentNumber: event.target.value })} maxLength={100} /></Field>
      <Field label={c('Fecha del documento', 'Document date')} htmlFor="claim-document-date" name="sourceDocumentDate"><input id="claim-document-date" type="date" max={form.claimDate} value={form.sourceDocumentDate} onChange={(event) => setForm({ ...form, sourceDocumentDate: event.target.value })} /></Field>
      <Field label={c('Qué ha pasado', 'What happened')} htmlFor="claim-description" name="description" required wide><textarea id="claim-description" rows={3} maxLength={2000} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} required /></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{c('Clasificación', 'Classification')}</span><h3>{c('Causa, responsable y solución', 'Cause, responsible and solution')}</h3></div></div>
    <div className="form-grid">
      {claimCatalogKinds.map((kind) => <Field key={kind} label={pickLabel(claimCatalogLabels[kind], language)} htmlFor={`claim-${kind}`} name={claimFieldByKind[kind]}><select id={`claim-${kind}`} value={classification[kind]} onChange={(event) => setClassification({ ...classification, [kind]: event.target.value })}><option value="">{c('Sin indicar', 'Not set')}</option>{options(kind).map((item) => <option key={item.id} value={item.id}>{item.name}{item.followUpDays !== null ? c(` · ${item.followUpDays} días`, ` · ${item.followUpDays} days`) : ''}</option>)}</select></Field>)}
      <Field label={c('Fecha de seguimiento', 'Follow-up date')} htmlFor="claim-follow-up" name="followUpDate" hint={c('Si la dejas vacía, la calcula la acción preventiva con su plazo.', 'If left empty, the preventive action calculates it with its period.')}><input id="claim-follow-up" type="date" min={form.claimDate} value={form.followUpDate} onChange={(event) => setForm({ ...form, followUpDate: event.target.value })} /></Field>
    </div>
    {catalog.length === 0 && <p className="detail-notes">{c('Las tablas de clasificación están vacías. Rellénalas en la pestaña «Tablas de clasificación».', 'The classification tables are empty. Fill them in the "Classification tables" tab.')}</p>}
    <div className="document-lines-heading"><div><span className="eyebrow">{c('Opcional', 'Optional')}</span><h3>{c('Productos reclamados', 'Claimed products')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setLines((current) => [...current, emptyLine])}><Plus size={15} />{t('sales.addLine')}</button></div>
    {lines.length > 0 && <div className="line-editor">{lines.map((line, index) => <div className="line-editor-row claim-line-row" key={index}>
      <div className="line-product"><label htmlFor={`claim-line-product-${index}`}>{t('sales.product')}</label><FieldControl htmlFor={`claim-line-product-${index}`} name={`lines[${index}].productId`}><select id={`claim-line-product-${index}`} value={line.productId} onChange={(event) => chooseProduct(index, event.target.value)}><option value="">{t('sales.freeLine')}</option>{products.map((product) => <option key={product.id} value={product.id}>{product.code} · {product.name}</option>)}</select></FieldControl></div>
      <div className="line-description"><label htmlFor={`claim-line-description-${index}`}>{t('sales.lineDescription')}</label><FieldControl htmlFor={`claim-line-description-${index}`} name={`lines[${index}].description`}><input id={`claim-line-description-${index}`} value={line.description} onChange={(event) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, description: event.target.value } : item))} maxLength={300} required /></FieldControl></div>
      <div><label htmlFor={`claim-line-quantity-${index}`}>{t('sales.quantity')}</label><FieldControl htmlFor={`claim-line-quantity-${index}`} name={`lines[${index}].quantity`}><input id={`claim-line-quantity-${index}`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => setLines((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, quantity: event.target.value } : item))} required /></FieldControl></div>
      <button className="icon-button line-remove" type="button" onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))} aria-label={t('sales.deleteLine', { number: index + 1 })}><Trash2 size={16} /></button>
    </div>)}</div>}
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={claim ? t('common.save') : c('Registrar reclamación', 'Record claim')} />
  </FormErrors></form>
}
