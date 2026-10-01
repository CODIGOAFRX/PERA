import { Lock, LockOpen, Pencil, Plus, WalletCards } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from '../../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../../lib/api'
import { cashMovementLabels, pick } from '../../lib/collections'
import { formatCurrency, formatDateTime } from '../../lib/format'
import type { CashMovementType, CashRegister, CashSession, PageResponse } from '../../types/api'
import { EmptyState, LoadingState } from '../DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../Form'
import { Modal } from '../Modal'
import { StatusBadge } from '../StatusBadge'
import { TableCaption } from '../TableCaption'
import { useToast } from '../Toast'

type Editor = { kind: 'register'; item?: CashRegister } | { kind: 'open'; register: CashRegister } | { kind: 'movement'; session: CashSession } | { kind: 'close'; session: CashSession }

/** Las sesiones de caja todavía no guardan moneda: los importes se muestran en euros. */
const CASH_CURRENCY = 'EUR'

export function CashTab() {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [registers, setRegisters] = useState<CashRegister[]>([])
  const [sessions, setSessions] = useState<CashSession[]>([])
  const [selected, setSelected] = useState<CashSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editor, setEditor] = useState<Editor | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => {
    let active = true
    Promise.all([
      apiFetch<CashRegister[]>('/api/v1/cash-registers'),
      apiFetch<PageResponse<CashSession>>('/api/v1/cash-sessions?size=20&sort=openedAt,desc'),
    ]).then(([registerList, sessionPage]) => {
      if (!active) return
      setRegisters(registerList); setSessions(sessionPage.content); setError('')
      // La sesión que se está viendo se refresca con sus apuntes nuevos.
      setSelected((current) => current ? sessionPage.content.find((item) => item.id === current.id) ?? null : null)
    }).catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [refresh])

  const saved = (message: string) => { setEditor(null); setRefresh((value) => value + 1); notify(message) }
  const registerOf = (session: CashSession) => registers.find((item) => item.id === session.cashRegisterId)
  const money = (value: number | null) => formatCurrency(value, CASH_CURRENCY, locale)

  if (loading) return <LoadingState />
  return <>
    {error && <div className="inline-error" role="alert">{error}</div>}
    <section className="panel table-panel">
      <div className="panel-heading table-toolbar"><div><span className="eyebrow">{c('Puntos de cobro', 'Collection points')}</span><h2>{c('Cajas', 'Cash registers')}</h2></div><button className="button button-primary" type="button" onClick={() => setEditor({ kind: 'register' })}><Plus size={17} />{c('Nueva caja', 'New register')}</button></div>
      {registers.length === 0 ? <EmptyState title={c('No hay cajas', 'There are no cash registers')} description={c('Crea una caja para llevar el efectivo de un mostrador o de un cobrador.', 'Create a register to keep the cash of a counter or a collector.')} /> : <div className="table-scroll"><table>
        <TableCaption es="Cajas" en="Cash registers" />
        <thead><tr><th>{t('field.code')}</th><th>{c('Caja', 'Register')}</th><th>{c('Responsable', 'Owner')}</th><th>{c('Sesión', 'Session')}</th><th className="align-right">{c('Efectivo en caja', 'Cash in register')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead>
        <tbody>{registers.map((register) => {
          const open = sessions.find((item) => item.id === register.openSessionId)
          return <tr key={register.id}>
            <td><span className="code-cell">{register.code}</span></td>
            <td><strong>{register.name}</strong>{!register.active && <small>{t('common.inactive')}</small>}</td>
            <td>{register.ownerName || '—'}</td>
            <td><StatusBadge tone={register.openSessionId ? 'success' : 'neutral'}>{register.openSessionId ? c('Abierta', 'Open') : c('Cerrada', 'Closed')}</StatusBadge></td>
            <td className="align-right"><strong>{open ? money(open.balance) : '—'}</strong></td>
            <td>
              {open ? <button className="button button-secondary button-small" type="button" onClick={() => setSelected(open)}><WalletCards size={15} />{c('Ver diario', 'View journal')}</button>
                : register.active && <button className="button button-secondary button-small" type="button" onClick={() => setEditor({ kind: 'open', register })}><LockOpen size={15} />{c('Abrir sesión', 'Open session')}</button>}
              <button className="icon-button" type="button" onClick={() => setEditor({ kind: 'register', item: register })} aria-label={c(`Editar ${register.name}`, `Edit ${register.name}`)}><Pencil size={16} /></button>
            </td>
          </tr>
        })}</tbody>
      </table></div>}
    </section>

    <section className="panel table-panel">
      <div className="panel-heading table-toolbar"><div><span className="eyebrow">{c('Arqueos', 'Counts')}</span><h2>{c('Sesiones de caja', 'Cash sessions')}</h2></div></div>
      {sessions.length === 0 ? <EmptyState title={c('No hay sesiones', 'There are no sessions')} description={c('Abre una sesión en una caja para empezar a anotar cobros y gastos.', 'Open a session in a register to start recording collections and expenses.')} /> : <div className="table-scroll"><table>
        <TableCaption es="Sesiones de caja" en="Cash sessions" />
        <thead><tr><th>{c('Caja', 'Register')}</th><th>{c('Apertura', 'Opened')}</th><th>{c('Cierre', 'Closed')}</th><th className="align-right">{c('Fondo inicial', 'Opening float')}</th><th className="align-right">{c('Esperado', 'Expected')}</th><th className="align-right">{c('Contado', 'Counted')}</th><th className="align-right">{c('Diferencia', 'Difference')}</th></tr></thead>
        <tbody>{sessions.map((session) => <tr key={session.id} className="clickable-row" onClick={() => setSelected(session)}>
          <td><strong>{registerOf(session)?.code ?? '—'}</strong><small>{session.status === 'OPEN' ? c('Abierta', 'Open') : c('Cerrada', 'Closed')}</small></td>
          <td>{formatDateTime(session.openedAt, locale)}</td>
          <td>{formatDateTime(session.closedAt, locale)}</td>
          <td className="align-right">{money(session.openingAmount)}</td>
          <td className="align-right">{money(session.expectedClosingAmount ?? session.balance)}</td>
          <td className="align-right">{session.actualClosingAmount === null ? '—' : money(session.actualClosingAmount)}</td>
          <td className="align-right">{session.difference === null ? '—' : <StatusBadge tone={Number(session.difference) === 0 ? 'success' : 'danger'}>{money(session.difference)}</StatusBadge>}</td>
        </tr>)}</tbody>
      </table></div>}
    </section>

    <Modal open={selected !== null && editor === null} title={selected ? c(`Diario de caja · ${registerOf(selected)?.code ?? ''}`, `Cash journal · ${registerOf(selected)?.code ?? ''}`) : ''} description={selected ? `${registerOf(selected)?.name ?? ''} · ${formatDateTime(selected.openedAt, locale)}` : ''} onClose={() => setSelected(null)} size="large">
      {selected && <div className="document-detail">
        <div className="detail-summary">
          <div><small>{c('Fondo inicial', 'Opening float')}</small><strong>{money(selected.openingAmount)}</strong><span>{formatDateTime(selected.openedAt, locale)}</span></div>
          <div><small>{selected.status === 'OPEN' ? c('Efectivo en caja', 'Cash in register') : c('Esperado al cierre', 'Expected at close')}</small><strong className="detail-total">{money(selected.expectedClosingAmount ?? selected.balance)}</strong><span>{c(`${selected.movements.length} apuntes`, `${selected.movements.length} entries`)}</span></div>
          <div><small>{c('Contado', 'Counted')}</small><strong>{selected.actualClosingAmount === null ? '—' : money(selected.actualClosingAmount)}</strong><span>{selected.closingNote ?? ''}</span></div>
          <div><small>{c('Diferencia', 'Difference')}</small><strong>{selected.difference === null ? '—' : money(selected.difference)}</strong><span>{selected.status === 'OPEN' ? c('Sesión abierta', 'Open session') : formatDateTime(selected.closedAt, locale)}</span></div>
        </div>
        {selected.movements.length === 0 ? <EmptyState title={c('Sin apuntes', 'No entries')} description={c('Los cobros en efectivo y los apuntes manuales aparecerán aquí.', 'Cash collections and manual entries will appear here.')} /> : <div className="table-scroll detail-lines"><table>
          <TableCaption es="Apuntes de la sesión" en="Session entries" />
          <thead><tr><th>{c('Hora', 'Time')}</th><th>{c('Apunte', 'Entry')}</th><th>{c('Concepto', 'Concept')}</th><th className="align-right">{c('Importe', 'Amount')}</th></tr></thead>
          <tbody>{selected.movements.map((movement) => <tr key={movement.id}><td>{formatDateTime(movement.occurredAt, locale)}</td><td>{pick(cashMovementLabels[movement.type], language)}</td><td>{movement.concept}</td><td className="align-right"><strong>{Number(movement.signedAmount) > 0 ? '+' : ''}{money(movement.signedAmount)}</strong></td></tr>)}</tbody>
        </table></div>}
        {selected.status === 'OPEN' && <div className="modal-action-strip">
          <button type="button" className="button button-secondary" onClick={() => setEditor({ kind: 'movement', session: selected })}><Plus size={17} />{c('Nuevo apunte', 'New entry')}</button>
          <button type="button" className="button button-primary" onClick={() => setEditor({ kind: 'close', session: selected })}><Lock size={17} />{c('Arquear y cerrar', 'Count and close')}</button>
        </div>}
      </div>}
    </Modal>

    <Modal open={editor !== null} title={editor?.kind === 'register' ? editor.item ? c('Editar caja', 'Edit register') : c('Nueva caja', 'New register') : editor?.kind === 'open' ? c(`Abrir sesión en ${editor.register.code}`, `Open session in ${editor.register.code}`) : editor?.kind === 'movement' ? c('Nuevo apunte de caja', 'New cash entry') : c('Arqueo y cierre', 'Count and close')}
      description={editor?.kind === 'close' ? c(`El diario dice que debería haber ${money(editor.session.balance)}. Cuenta el efectivo y anota lo que hay.`, `The journal says there should be ${money(editor.session.balance)}. Count the cash and record what is there.`) : editor?.kind === 'open' ? c('Indica el efectivo con el que empieza el turno.', 'Enter the cash the shift starts with.') : undefined}
      onClose={() => setEditor(null)}>
      {editor?.kind === 'register' && <RegisterForm key={editor.item?.id ?? 'new'} item={editor.item} onCancel={() => setEditor(null)} onSaved={() => saved(c('Caja guardada.', 'Register saved.'))} />}
      {editor?.kind === 'open' && <AmountForm label={c('Fondo inicial', 'Opening float')} submitLabel={c('Abrir sesión', 'Open session')} onCancel={() => setEditor(null)} onSubmit={(amount) => apiFetch<CashSession>('/api/v1/cash-sessions', { method: 'POST', body: JSON.stringify({ cashRegisterId: editor.register.id, openingAmount: amount }) }).then(() => saved(c('Sesión de caja abierta.', 'Cash session opened.')))} />}
      {editor?.kind === 'movement' && <MovementForm session={editor.session} onCancel={() => setEditor(null)} onSaved={() => saved(c('Apunte registrado.', 'Entry recorded.'))} />}
      {editor?.kind === 'close' && <AmountForm label={c('Efectivo contado', 'Counted cash')} noteLabel={c('Observaciones del arqueo', 'Count notes')} submitLabel={c('Cerrar sesión', 'Close session')} onCancel={() => setEditor(null)} onSubmit={(amount, note) => apiFetch<CashSession>(`/api/v1/cash-sessions/${editor.session.id}/close`, { method: 'POST', body: JSON.stringify({ countedAmount: amount, note: note || null }) }).then(() => { setSelected(null); saved(c('Sesión de caja cerrada.', 'Cash session closed.')) })} />}
    </Modal>
  </>
}

function AmountForm({ label, noteLabel, submitLabel, onCancel, onSubmit }: { label: string; noteLabel?: string; submitLabel: string; onCancel: () => void; onSubmit: (amount: number, note: string) => Promise<void> }) {
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { await onSubmit(Number(amount), note.trim()) } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={label} htmlFor="cash-amount" required wide={!noteLabel}><input id="cash-amount" type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} required /></Field>
    {noteLabel && <Field label={noteLabel} htmlFor="cash-note"><input id="cash-note" value={note} onChange={(event) => setNote(event.target.value)} maxLength={300} /></Field>}
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={submitLabel} /></form>
}

function MovementForm({ session, onCancel, onSaved }: { session: CashSession; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ type: 'INCOME' as CashMovementType, amount: '', concept: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { await apiFetch(`/api/v1/cash-sessions/${session.id}/movements`, { method: 'POST', body: JSON.stringify({ type: form.type, amount: Number(form.amount), concept: form.concept.trim() }) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Tipo de apunte', 'Entry type')} htmlFor="cash-movement-type" name="type" required><select id="cash-movement-type" value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value as CashMovementType })}>{(['INCOME', 'EXPENSE', 'WITHDRAWAL'] as CashMovementType[]).map((item) => <option key={item} value={item}>{pick(cashMovementLabels[item], language)}</option>)}</select></Field>
    <Field label={c('Importe', 'Amount')} htmlFor="cash-movement-amount" name="amount" required><input id="cash-movement-amount" type="number" min="0.01" step="0.01" value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} required /></Field>
    <Field label={c('Concepto', 'Concept')} htmlFor="cash-movement-concept" name="concept" required wide><input id="cash-movement-concept" value={form.concept} onChange={(event) => setForm({ ...form, concept: event.target.value })} maxLength={300} required /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={c('Registrar apunte', 'Record entry')} /></FormErrors></form>
}

function RegisterForm({ item, onCancel, onSaved }: { item?: CashRegister; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ code: item?.code ?? '', name: item?.name ?? '', ownerName: item?.ownerName ?? '', active: item?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = { code: form.code.trim(), name: form.name.trim(), ownerName: form.ownerName.trim() || null, active: form.active }
    try { await apiFetch(item ? `/api/v1/cash-registers/${item.id}` : '/api/v1/cash-registers', { method: item ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={t('field.code')} htmlFor="register-code" name="code" required><input id="register-code" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} disabled={Boolean(item)} maxLength={40} required /></Field>
    <Field label={c('Nombre', 'Name')} htmlFor="register-name" name="name" required><input id="register-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={160} required /></Field>
    <Field label={c('Responsable', 'Owner')} htmlFor="register-owner" name="ownerName"><input id="register-owner" value={form.ownerName} onChange={(event) => setForm({ ...form, ownerName: event.target.value })} maxLength={160} /></Field>
    <Field label={t('field.status')} htmlFor="register-active" name="active"><label className="switch-row" htmlFor="register-active"><input id="register-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Caja activa', 'Active register')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? t('common.save') : c('Crear caja', 'Create register')} /></FormErrors></form>
}
