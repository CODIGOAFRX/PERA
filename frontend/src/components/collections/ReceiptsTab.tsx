import { Ban, CheckCircle2, RotateCcw, Undo2 } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { useTranslation } from '../../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../../lib/api'
import { collectionMethodLabels, collectionMethods, isOverdue, pick, receiptStatusLabels, receiptStatusTone, receiptStatuses } from '../../lib/collections'
import { localIsoDate } from '../../lib/date'
import { formatCurrency, formatDate } from '../../lib/format'
import type { CashRegister, CollectionMethod, PageResponse, Receipt, ReceiptOperation, ReceiptStatus } from '../../types/api'
import { useConfirm } from '../ConfirmDialog'
import { EmptyState, LoadingState } from '../DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../Form'
import { Modal } from '../Modal'
import { Pagination } from '../Pagination'
import { StatusBadge } from '../StatusBadge'
import { TableCaption } from '../TableCaption'
import { TableToolbar } from '../TableToolbar'
import { useToast } from '../Toast'

type ReceiptAction = 'collect' | 'return' | 'reopen'

export function ReceiptsTab() {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [data, setData] = useState<PageResponse<Receipt> | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim())
  const [status, setStatus] = useState<ReceiptStatus | ''>('PENDING')
  const [dueFrom, setDueFrom] = useState('')
  const [dueTo, setDueTo] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<Receipt | null>(null)
  const [action, setAction] = useState<ReceiptAction | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const confirm = useConfirm()
  const today = localIsoDate()

  useEffect(() => setPage(0), [debouncedQuery, status, dueFrom, dueTo])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'dueDate,asc' })
    params.append('sort', 'receiptNumber,asc')
    if (debouncedQuery) params.set('query', debouncedQuery)
    if (status) params.set('status', status)
    if (dueFrom) params.set('dueFrom', dueFrom)
    if (dueTo) params.set('dueTo', dueTo)
    apiFetch<PageResponse<Receipt>>(`/api/v1/receipts?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, debouncedQuery, status, dueFrom, dueTo, refresh])

  // La cartera queda actualizada aunque Ventas no responda; se avisa para que revisen la factura.
  const done = (operation: ReceiptOperation, message: string) => {
    setSelected(null); setAction(null); setRefresh((value) => value + 1)
    if (operation.invoiceUpdated) notify(message)
    else notify(c(`${message} No se pudo actualizar el estado de cobro de la factura en Ventas: revísalo allí.`, `${message} The payment status of the invoice could not be updated in Sales: review it there.`), 'error')
  }

  const cancelReceipt = async (receipt: Receipt) => {
    if (!(await confirm({ message: c(`¿Anular el recibo ${receipt.receiptNumber}? Su vencimiento podrá emitir un recibo nuevo.`, `Cancel receipt ${receipt.receiptNumber}? Its due date will be able to issue a new receipt.`), danger: true }))) return
    try { done(await apiFetch<ReceiptOperation>(`/api/v1/receipts/${receipt.id}/cancel`, { method: 'POST' }), c('Recibo anulado.', 'Receipt cancelled.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={c('Buscar por recibo, factura o cliente', 'Search by receipt, invoice or customer')}>
        <select aria-label={t('sales.filterStatus')} value={status} onChange={(event) => setStatus(event.target.value as ReceiptStatus | '')}><option value="">{t('sales.allStatuses')}</option>{receiptStatuses.map((item) => <option key={item} value={item}>{pick(receiptStatusLabels[item], language)}</option>)}</select>
        <label className="toolbar-date"><span>{c('Vence desde', 'Due from')}</span><input aria-label={c('Vence desde', 'Due from')} type="date" value={dueFrom} onChange={(event) => setDueFrom(event.target.value)} /></label>
        <label className="toolbar-date"><span>{c('Vence hasta', 'Due to')}</span><input aria-label={c('Vence hasta', 'Due to')} type="date" value={dueTo} onChange={(event) => setDueTo(event.target.value)} /></label>
      </TableToolbar>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table>
          <TableCaption es="Recibos de cobro" en="Receipts" />
          <thead><tr><th>{c('Recibo', 'Receipt')}</th><th>{c('Factura', 'Invoice')}</th><th>{t('sales.customer')}</th><th>{c('Vencimiento', 'Due date')}</th><th>{t('sales.status')}</th><th className="align-right">{c('Importe', 'Amount')}</th></tr></thead>
          <tbody>{data.content.map((receipt) => <tr key={receipt.id} className="clickable-row" onClick={() => setSelected(receipt)}>
            <td><strong className="document-number">{receipt.receiptNumber}</strong></td>
            <td>{receipt.documentNumber}<small>{c(`Plazo ${receipt.installment}`, `Instalment ${receipt.installment}`)}</small></td>
            <td><strong>{receipt.customerName}</strong>{receipt.customerCode && <small>{receipt.customerCode}</small>}</td>
            <td>{formatDate(receipt.dueDate, locale)}{isOverdue(receipt, today) && <small>{c('Vencido', 'Overdue')}</small>}</td>
            <td><StatusBadge tone={isOverdue(receipt, today) ? 'danger' : receiptStatusTone[receipt.status]}>{pick(receiptStatusLabels[receipt.status], language)}</StatusBadge>{receipt.remittanceId && receipt.status === 'PENDING' && <small>{c('En remesa', 'In a remittance')}</small>}</td>
            <td className="align-right"><strong>{formatCurrency(receipt.amount, receipt.currencyCode, locale)}</strong></td>
          </tr>)}</tbody>
        </table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title={c('No hay recibos', 'There are no receipts')} description={query || dueFrom || dueTo || (status && status !== 'PENDING') ? t('common.noResults') : status === 'PENDING' ? c('No queda ningún recibo pendiente de cobro.', 'There is no receipt pending collection.') : c('Los recibos se emiten desde Finanzas, al generar los vencimientos de una factura.', 'Receipts are issued from Finance, when the due dates of an invoice are generated.')} />}
    </section>

    <Modal open={selected !== null && action === null} title={selected?.receiptNumber ?? ''} description={selected ? `${selected.documentNumber} · ${selected.customerName}` : ''} onClose={() => setSelected(null)}>
      {selected && <div className="document-detail">
        <div className="detail-summary">
          <div><small>{c('Importe', 'Amount')}</small><strong className="detail-total">{formatCurrency(selected.amount, selected.currencyCode, locale)}</strong><span>{c(`Plazo ${selected.installment}`, `Instalment ${selected.installment}`)}</span></div>
          <div><small>{c('Vencimiento', 'Due date')}</small><strong>{formatDate(selected.dueDate, locale)}</strong><span>{isOverdue(selected, today) ? c('Vencido', 'Overdue') : ''}</span></div>
          <div><small>{t('sales.status')}</small><StatusBadge tone={receiptStatusTone[selected.status]}>{pick(receiptStatusLabels[selected.status], language)}</StatusBadge><span>{selected.collectionMethod ? pick(collectionMethodLabels[selected.collectionMethod], language) : ''}</span></div>
          <div><small>{selected.status === 'RETURNED' ? c('Devolución', 'Return') : c('Cobro', 'Collection')}</small><strong>{formatDate(selected.status === 'RETURNED' ? selected.returnDate : selected.collectionDate, locale)}</strong><span>{selected.returnReason ?? ''}</span></div>
        </div>
        {selected.notes && <p className="detail-notes">{selected.notes}</p>}
        {selected.remittanceId && selected.status === 'PENDING' && <p className="detail-notes">{c('Este recibo está en una remesa en borrador. Para cobrarlo por otra vía, sácalo antes de la remesa.', 'This receipt is in a draft remittance. To collect it another way, remove it from the remittance first.')}</p>}
        {selected.status !== 'CANCELLED' && <div className="modal-action-strip">
          {(selected.status === 'PENDING' || selected.status === 'RETURNED') && !(selected.status === 'PENDING' && selected.remittanceId) && <button type="button" className="button button-danger" onClick={() => void cancelReceipt(selected)}><Ban size={17} />{c('Anular', 'Cancel receipt')}</button>}
          {selected.status === 'RETURNED' && <button type="button" className="button button-secondary" onClick={() => setAction('reopen')}><RotateCcw size={17} />{c('Volver a poner al cobro', 'Put back for collection')}</button>}
          {(selected.status === 'COLLECTED' || selected.status === 'REMITTED') && <button type="button" className="button button-danger" onClick={() => setAction('return')}><Undo2 size={17} />{c('Registrar devolución', 'Record return')}</button>}
          {selected.status === 'PENDING' && !selected.remittanceId && <button type="button" className="button button-primary" onClick={() => setAction('collect')}><CheckCircle2 size={17} />{c('Cobrar', 'Collect')}</button>}
        </div>}
      </div>}
    </Modal>
    <Modal open={selected !== null && action !== null} title={action === 'collect' ? c(`Cobrar ${selected?.receiptNumber ?? ''}`, `Collect ${selected?.receiptNumber ?? ''}`) : action === 'return' ? c(`Devolución de ${selected?.receiptNumber ?? ''}`, `Return of ${selected?.receiptNumber ?? ''}`) : c(`Volver a poner al cobro ${selected?.receiptNumber ?? ''}`, `Put ${selected?.receiptNumber ?? ''} back for collection`)} description={selected ? `${selected.customerName} · ${formatCurrency(selected.amount, selected.currencyCode, locale)}` : ''} onClose={() => setAction(null)}>
      {selected && action && <ReceiptActionForm receipt={selected} action={action} onCancel={() => setAction(null)} onDone={done} />}
    </Modal>
  </>
}

function ReceiptActionForm({ receipt, action, onCancel, onDone }: { receipt: Receipt; action: ReceiptAction; onCancel: () => void; onDone: (operation: ReceiptOperation, message: string) => void }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [registers, setRegisters] = useState<CashRegister[]>([])
  const [form, setForm] = useState({ date: localIsoDate(), method: 'BANK_TRANSFER' as CollectionMethod, cashSessionId: '', text: '', newDueDate: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  // Un cobro en efectivo puede anotarse en una caja que tenga sesión abierta.
  useEffect(() => {
    if (action !== 'collect') return
    apiFetch<CashRegister[]>('/api/v1/cash-registers').then((list) => setRegisters(list.filter((item) => item.openSessionId))).catch(() => setRegisters([]))
  }, [action])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const body = action === 'collect'
      ? { collectionDate: form.date, method: form.method, cashSessionId: form.method === 'CASH' && form.cashSessionId ? form.cashSessionId : null, notes: form.text.trim() || null }
      : action === 'return' ? { returnDate: form.date, reason: form.text.trim() } : { newDueDate: form.newDueDate || null }
    try {
      const operation = await apiFetch<ReceiptOperation>(`/api/v1/receipts/${receipt.id}/${action}`, { method: 'POST', body: JSON.stringify(body) })
      onDone(operation, action === 'collect' ? c('Recibo cobrado.', 'Receipt collected.') : action === 'return' ? c('Devolución registrada.', 'Return recorded.') : c('El recibo vuelve a estar pendiente.', 'The receipt is pending again.'))
    } catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    {action === 'reopen'
      ? <Field label={c('Nuevo vencimiento', 'New due date')} htmlFor="receipt-new-due" name="newDueDate" hint={c('Déjalo vacío para conservar el vencimiento original.', 'Leave it empty to keep the original due date.')} wide><input id="receipt-new-due" type="date" value={form.newDueDate} onChange={(event) => setForm({ ...form, newDueDate: event.target.value })} /></Field>
      : <Field label={action === 'collect' ? c('Fecha de cobro', 'Collection date') : c('Fecha de devolución', 'Return date')} htmlFor="receipt-date" name={action === 'collect' ? 'collectionDate' : 'returnDate'} required><input id="receipt-date" type="date" value={form.date} onChange={(event) => setForm({ ...form, date: event.target.value })} required /></Field>}
    {action === 'collect' && <Field label={c('Forma de cobro', 'Collection method')} htmlFor="receipt-method" name="method" required><select id="receipt-method" value={form.method} onChange={(event) => setForm({ ...form, method: event.target.value as CollectionMethod })}>{collectionMethods.filter((item) => item !== 'DIRECT_DEBIT').map((item) => <option key={item} value={item}>{pick(collectionMethodLabels[item], language)}</option>)}</select></Field>}
    {action === 'collect' && form.method === 'CASH' && <Field label={c('Caja', 'Cash register')} htmlFor="receipt-cash" name="cashSessionId" hint={registers.length ? c('El cobro se anota en el diario de esa caja.', 'The collection is recorded in the journal of that register.') : c('No hay ninguna caja con sesión abierta.', 'There is no register with an open session.')} wide><select id="receipt-cash" value={form.cashSessionId} onChange={(event) => setForm({ ...form, cashSessionId: event.target.value })}><option value="">{c('No anotar en caja', 'Do not record in a register')}</option>{registers.map((register) => <option key={register.id} value={register.openSessionId ?? ''}>{register.code} · {register.name}</option>)}</select></Field>}
    {action !== 'reopen' && <Field label={action === 'collect' ? c('Notas', 'Notes') : c('Motivo de la devolución', 'Reason for the return')} htmlFor="receipt-text" name={action === 'collect' ? 'notes' : 'reason'} required={action === 'return'} wide><textarea id="receipt-text" rows={2} maxLength={action === 'collect' ? 500 : 300} value={form.text} onChange={(event) => setForm({ ...form, text: event.target.value })} required={action === 'return'} /></Field>}
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={action === 'collect' ? c('Registrar cobro', 'Record collection') : action === 'return' ? c('Registrar devolución', 'Record return') : c('Poner al cobro', 'Put for collection')} /></FormErrors></form>
}
