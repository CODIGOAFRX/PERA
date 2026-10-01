import { Ban, CheckCircle2, Pencil, Plus, Send } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from '../../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../../lib/api'
import { pick, receiptStatusLabels, receiptStatusTone, remittanceStatusLabels, remittanceStatusTone, remittanceStatuses } from '../../lib/collections'
import { localIsoDate } from '../../lib/date'
import { formatCurrency, formatDate } from '../../lib/format'
import type { PageResponse, Receipt, Remittance, RemittanceStatus } from '../../types/api'
import { useConfirm } from '../ConfirmDialog'
import { EmptyState, LoadingState } from '../DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../Form'
import { Modal } from '../Modal'
import { Pagination } from '../Pagination'
import { StatusBadge } from '../StatusBadge'
import { TableCaption } from '../TableCaption'
import { TableToolbar } from '../TableToolbar'
import { useToast } from '../Toast'

type DateAction = 'send' | 'settle'

export function RemittancesTab() {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [data, setData] = useState<PageResponse<Remittance> | null>(null)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<RemittanceStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<Remittance | null>(null)
  const [editing, setEditing] = useState<Remittance | 'new' | null>(null)
  const [dateAction, setDateAction] = useState<DateAction | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const confirm = useConfirm()

  useEffect(() => setPage(0), [status])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'creationDate,desc' })
    params.append('sort', 'remittanceNumber,desc')
    if (status) params.set('status', status)
    apiFetch<PageResponse<Remittance>>(`/api/v1/remittances?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, status, refresh])

  const close = () => { setSelected(null); setEditing(null); setDateAction(null); setRefresh((value) => value + 1) }
  const cancelRemittance = async (remittance: Remittance) => {
    if (!(await confirm({ message: c(`¿Anular la remesa ${remittance.remittanceNumber}? Sus recibos volverán a estar pendientes de cobro.`, `Cancel remittance ${remittance.remittanceNumber}? Its receipts will be pending again.`), danger: true }))) return
    try { await apiFetch(`/api/v1/remittances/${remittance.id}/cancel`, { method: 'POST' }); close(); notify(c('Remesa anulada.', 'Remittance cancelled.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }
  const newButton = <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nueva remesa', 'New remittance')}</button>

  return <>
    <section className="panel table-panel">
      <TableToolbar value="" onChange={() => undefined} placeholder="" hideSearch>
        <select aria-label={t('sales.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value as RemittanceStatus | '')}><option value="">{t('sales.allStatuses')}</option>{remittanceStatuses.map((item) => <option key={item} value={item}>{pick(remittanceStatusLabels[item], language)}</option>)}</select>
        {newButton}
      </TableToolbar>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table>
          <TableCaption es="Remesas" en="Remittances" />
          <thead><tr><th>{c('Remesa', 'Remittance')}</th><th>{c('Cuenta', 'Account')}</th><th>{c('Creada', 'Created')}</th><th>{c('Enviada', 'Sent')}</th><th>{c('Abonada', 'Settled')}</th><th>{c('Recibos', 'Receipts')}</th><th>{t('sales.status')}</th><th className="align-right">{t('sales.total')}</th></tr></thead>
          <tbody>{data.content.map((remittance) => <tr key={remittance.id} className="clickable-row" onClick={() => setSelected(remittance)}>
            <td><strong className="document-number">{remittance.remittanceNumber}</strong></td>
            <td>{remittance.bankAccount}</td>
            <td>{formatDate(remittance.creationDate, locale)}</td>
            <td>{formatDate(remittance.sentDate, locale)}</td>
            <td>{formatDate(remittance.settlementDate, locale)}</td>
            <td>{remittance.receipts.length}</td>
            <td><StatusBadge tone={remittanceStatusTone[remittance.status]}>{pick(remittanceStatusLabels[remittance.status], language)}</StatusBadge></td>
            <td className="align-right"><strong>{formatCurrency(remittance.totalAmount, remittance.currencyCode, locale)}</strong></td>
          </tr>)}</tbody>
        </table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title={c('No hay remesas', 'There are no remittances')} description={status ? t('common.noResults') : c('Agrupa recibos pendientes para presentarlos juntos al banco.', 'Group pending receipts to present them to the bank together.')} />}
    </section>

    <Modal open={selected !== null && dateAction === null} title={selected?.remittanceNumber ?? ''} description={selected ? `${selected.bankAccount} · ${pick(remittanceStatusLabels[selected.status], language)}` : ''} onClose={() => setSelected(null)} size="large">
      {selected && <div className="document-detail">
        <div className="detail-summary">
          <div><small>{t('sales.total')}</small><strong className="detail-total">{formatCurrency(selected.totalAmount, selected.currencyCode, locale)}</strong><span>{selected.receipts.length === 1 ? c('1 recibo', '1 receipt') : c(`${selected.receipts.length} recibos`, `${selected.receipts.length} receipts`)}</span></div>
          <div><small>{c('Creada', 'Created')}</small><strong>{formatDate(selected.creationDate, locale)}</strong><span>{selected.bankAccount}</span></div>
          <div><small>{c('Enviada al banco', 'Sent to the bank')}</small><strong>{formatDate(selected.sentDate, locale)}</strong><span>{selected.status === 'DRAFT' ? c('Todavía en borrador', 'Still a draft') : ''}</span></div>
          <div><small>{c('Abonada', 'Settled')}</small><strong>{formatDate(selected.settlementDate, locale)}</strong><span /></div>
        </div>
        <div className="table-scroll detail-lines"><table>
          <TableCaption es="Recibos de la remesa" en="Receipts of the remittance" />
          <thead><tr><th>{c('Recibo', 'Receipt')}</th><th>{t('sales.customer')}</th><th>{c('Vencimiento', 'Due date')}</th><th>{t('sales.status')}</th><th className="align-right">{c('Importe', 'Amount')}</th></tr></thead>
          <tbody>{selected.receipts.map((receipt) => <tr key={receipt.id}><td><strong>{receipt.receiptNumber}</strong><small>{receipt.documentNumber}</small></td><td>{receipt.customerName}</td><td>{formatDate(receipt.dueDate, locale)}</td><td><StatusBadge tone={receiptStatusTone[receipt.status]}>{pick(receiptStatusLabels[receipt.status], language)}</StatusBadge></td><td className="align-right"><strong>{formatCurrency(receipt.amount, receipt.currencyCode, locale)}</strong></td></tr>)}</tbody>
        </table></div>
        {selected.notes && <p className="detail-notes">{selected.notes}</p>}
        {(selected.status === 'DRAFT' || selected.status === 'SENT') && <div className="modal-action-strip">
          <button type="button" className="button button-danger" onClick={() => void cancelRemittance(selected)}><Ban size={17} />{c('Anular remesa', 'Cancel remittance')}</button>
          {selected.status === 'DRAFT' && <button type="button" className="button button-secondary" onClick={() => { setEditing(selected); setSelected(null) }}><Pencil size={17} />{c('Editar', 'Edit')}</button>}
          {selected.status === 'DRAFT' && <button type="button" className="button button-primary" onClick={() => setDateAction('send')}><Send size={17} />{c('Enviar al banco', 'Send to the bank')}</button>}
          {selected.status === 'SENT' && <button type="button" className="button button-primary" onClick={() => setDateAction('settle')}><CheckCircle2 size={17} />{c('Registrar abono', 'Record settlement')}</button>}
        </div>}
      </div>}
    </Modal>
    <Modal open={selected !== null && dateAction !== null} title={dateAction === 'send' ? c('Enviar al banco', 'Send to the bank') : c('Registrar abono del banco', 'Record bank settlement')} description={dateAction === 'send' ? c('Los recibos dejan de poder cobrarse por otra vía. Esta versión no genera el fichero para el banco.', 'The receipts can no longer be collected another way. This version does not generate the bank file.') : c('Se dan por cobrados los recibos que no hayan venido devueltos.', 'Receipts that were not returned are marked as collected.')} onClose={() => setDateAction(null)}>
      {selected && dateAction && <RemittanceDateForm remittance={selected} action={dateAction} onCancel={() => setDateAction(null)} onDone={(result) => {
        close()
        if (result.invoiceUpdated === false) notify(c('Abono registrado. No se pudo actualizar el estado de cobro de alguna factura en Ventas: revísalo allí.', 'Settlement recorded. The payment status of some invoice could not be updated in Sales: review it there.'), 'error')
        else notify(dateAction === 'send' ? c('Remesa enviada al banco.', 'Remittance sent to the bank.') : c('Abono registrado: recibos cobrados.', 'Settlement recorded: receipts collected.'))
      }} />}
    </Modal>
    <Modal open={editing !== null} title={editing === 'new' || editing === null ? c('Nueva remesa', 'New remittance') : c(`Editar ${editing.remittanceNumber}`, `Edit ${editing.remittanceNumber}`)} description={c('Elige los recibos pendientes que se presentan juntos al banco.', 'Choose the pending receipts presented to the bank together.')} onClose={() => setEditing(null)} size="large">
      {editing && <RemittanceForm key={editing === 'new' ? 'new' : editing.id} remittance={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { close(); notify(c('Remesa guardada.', 'Remittance saved.')) }} />}
    </Modal>
  </>
}

function RemittanceDateForm({ remittance, action, onCancel, onDone }: { remittance: Remittance; action: DateAction; onCancel: () => void; onDone: (result: Remittance) => void }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [date, setDate] = useState(localIsoDate())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { onDone(await apiFetch<Remittance>(`/api/v1/remittances/${remittance.id}/${action}`, { method: 'POST', body: JSON.stringify({ date }) })) }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={action === 'send' ? c('Fecha de envío', 'Sending date') : c('Fecha de abono', 'Settlement date')} htmlFor="remittance-date" required wide><input id="remittance-date" type="date" min={action === 'send' ? remittance.creationDate : remittance.sentDate ?? undefined} value={date} onChange={(event) => setDate(event.target.value)} required /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={action === 'send' ? c('Enviar al banco', 'Send to the bank') : c('Registrar abono', 'Record settlement')} /></form>
}

function RemittanceForm({ remittance, onCancel, onSaved }: { remittance: Remittance | null; onCancel: () => void; onSaved: () => void }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [available, setAvailable] = useState<Receipt[]>([])
  const [chosen, setChosen] = useState<Set<string>>(new Set(remittance?.receipts.map((receipt) => receipt.id) ?? []))
  const [form, setForm] = useState({ bankAccount: remittance?.bankAccount ?? '', notes: remittance?.notes ?? '' })
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  useEffect(() => {
    apiFetch<PageResponse<Receipt>>('/api/v1/receipts?available=true&size=200&sort=dueDate,asc')
      .then((response) => setAvailable(response.content))
      .catch((cause) => setError(errorMessage(cause))).finally(() => setLoading(false))
  }, [])

  // En edición, los recibos que ya están en la remesa se listan junto a los disponibles.
  const candidates = useMemo(() => [...(remittance?.receipts ?? []), ...available], [remittance, available])
  const selected = candidates.filter((receipt) => chosen.has(receipt.id))
  const currencies = new Set(selected.map((receipt) => receipt.currencyCode))
  const total = selected.reduce((sum, receipt) => sum + Number(receipt.amount), 0)
  const toggle = (id: string) => setChosen((current) => { const next = new Set(current); if (!next.delete(id)) next.add(id); return next })

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError('')
    if (selected.length === 0) { setError(c('Selecciona al menos un recibo.', 'Select at least one receipt.')); return }
    if (currencies.size > 1) { setError(c('Todos los recibos de una remesa deben estar en la misma moneda.', 'All receipts in a remittance must be in the same currency.')); return }
    setSaving(true)
    const payload = { bankAccount: form.bankAccount.trim(), creationDate: remittance?.creationDate ?? localIsoDate(), notes: form.notes.trim() || null, receiptIds: selected.map((receipt) => receipt.id) }
    try { await apiFetch<Remittance>(remittance ? `/api/v1/remittances/${remittance.id}` : '/api/v1/remittances', { method: remittance ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  if (loading) return <LoadingState />
  return <form onSubmit={submit}><FormErrors value={formErrors.context}>
    <div className="form-grid">
      <Field label={c('Cuenta de abono', 'Settlement account')} htmlFor="remittance-account" name="bankAccount" hint={c('IBAN o nombre de la cuenta de la empresa donde el banco abona la remesa.', 'IBAN or name of the company account where the bank settles the remittance.')} required><input id="remittance-account" value={form.bankAccount} onChange={(event) => setForm({ ...form, bankAccount: event.target.value })} maxLength={80} required /></Field>
      <Field label={c('Notas', 'Notes')} htmlFor="remittance-notes" name="notes"><input id="remittance-notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} maxLength={500} /></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{c('Recibos pendientes', 'Pending receipts')}</span><h3>{selected.length === 1 ? c('1 seleccionado', '1 selected') : c(`${selected.length} seleccionados`, `${selected.length} selected`)} · {formatCurrency(total, selected[0]?.currencyCode ?? 'EUR', locale)}</h3></div></div>
    {candidates.length === 0 ? <EmptyState title={c('No hay recibos disponibles', 'There are no available receipts')} description={c('Solo se pueden remesar recibos pendientes que no estén ya en otra remesa.', 'Only pending receipts that are not already in another remittance can be included.')} /> : <div className="table-scroll detail-lines"><table>
      <TableCaption es="Recibos disponibles para la remesa" en="Receipts available for the remittance" />
      <thead><tr><th><span className="sr-only">{c('Incluir', 'Include')}</span></th><th>{c('Recibo', 'Receipt')}</th><th>{c('Cliente', 'Customer')}</th><th>{c('Vencimiento', 'Due date')}</th><th className="align-right">{c('Importe', 'Amount')}</th></tr></thead>
      <tbody>{candidates.map((receipt) => <tr key={receipt.id}>
        <td><input type="checkbox" checked={chosen.has(receipt.id)} onChange={() => toggle(receipt.id)} aria-label={c(`Incluir ${receipt.receiptNumber}`, `Include ${receipt.receiptNumber}`)} /></td>
        <td><strong>{receipt.receiptNumber}</strong><small>{receipt.documentNumber}</small></td><td>{receipt.customerName}</td><td>{formatDate(receipt.dueDate, locale)}</td>
        <td className="align-right"><strong>{formatCurrency(receipt.amount, receipt.currencyCode, locale)}</strong></td>
      </tr>)}</tbody>
    </table></div>}
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={remittance ? c('Guardar remesa', 'Save remittance') : c('Crear remesa', 'Create remittance')} />
  </FormErrors></form>
}
