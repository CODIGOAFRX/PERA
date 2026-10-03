import { Ban, CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Contact as ContactIcon, Pencil, Plus, RotateCcw, Tags, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useConfirm } from '../components/ConfirmDialog'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge, type BadgeTone } from '../components/StatusBadge'
import { TableCaption } from '../components/TableCaption'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { localIsoDate } from '../lib/date'
import { formatDate } from '../lib/format'
import type { AgendaEntry, AgendaEntryStatus, AgendaEntryType, Contact, Customer, FlatPage, PageResponse } from '../types/api'

type Tab = 'agenda' | 'contacts' | 'types'

const statusLabels: Record<AgendaEntryStatus, [string, string]> = { PENDING: ['Pendiente', 'Pending'], DONE: ['Hecha', 'Done'], CANCELLED: ['Anulada', 'Cancelled'] }
const statusTone: Record<AgendaEntryStatus, BadgeTone> = { PENDING: 'warning', DONE: 'success', CANCELLED: 'neutral' }

/** Lunes de la semana de una fecha AAAA-MM-DD, en hora local. */
export function mondayOf(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return localIsoDate(date, -((date.getDay() + 6) % 7))
}

const addDays = (isoDate: string, days: number) => {
  const [year, month, day] = isoDate.split('-').map(Number)
  return localIsoDate(new Date(year, month - 1, day), days)
}
const time = (value: string | null) => value ? value.slice(0, 5) : ''

export function AgendaPage() {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [tab, setTab] = useState<Tab>('agenda')
  const [types, setTypes] = useState<AgendaEntryType[]>([])
  const [typesVersion, setTypesVersion] = useState(0)

  useEffect(() => {
    apiFetch<AgendaEntryType[]>('/api/v1/agenda-entry-types').then(setTypes).catch(() => setTypes([]))
  }, [typesVersion])

  return <div className="page-stack">
    <PageHeader eyebrow={c('Organización', 'Organisation')} title={c('Agenda', 'Agenda')} description={c('Citas y tareas del equipo, y el listín de contactos.', 'Team appointments and tasks, and the contact directory.')} icon={CalendarDays} />
    <nav className="workspace-tabs" aria-label={c('Áreas de agenda', 'Agenda areas')}>
      <button type="button" className={tab === 'agenda' ? 'active' : ''} onClick={() => setTab('agenda')}><CalendarDays size={15} />{c('Semana', 'Week')}</button>
      <button type="button" className={tab === 'contacts' ? 'active' : ''} onClick={() => setTab('contacts')}><ContactIcon size={15} />{c('Listín', 'Directory')}</button>
      <button type="button" className={tab === 'types' ? 'active' : ''} onClick={() => setTab('types')}><Tags size={15} />{c('Tipos de cita', 'Appointment types')}</button>
    </nav>
    {tab === 'agenda' && <WeekView types={types} />}
    {tab === 'contacts' && <ContactsTab />}
    {tab === 'types' && <TypesTab types={types} onChanged={() => setTypesVersion((value) => value + 1)} />}
  </div>
}

function WeekView({ types }: { types: AgendaEntryType[] }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const today = localIsoDate()
  const [weekStart, setWeekStart] = useState(() => mondayOf(today))
  const [entries, setEntries] = useState<AgendaEntry[]>([])
  const [assignees, setAssignees] = useState<string[]>([])
  const [assignee, setAssignee] = useState('')
  const [typeId, setTypeId] = useState('')
  const [hideClosed, setHideClosed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<AgendaEntry | null>(null)
  const [editing, setEditing] = useState<AgendaEntry | { date: string } | null>(null)
  const [action, setAction] = useState<'complete' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  const confirm = useConfirm()
  const days = useMemo(() => Array.from({ length: 7 }, (_, index) => addDays(weekStart, index)), [weekStart])

  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ fromDate: days[0], toDate: days[6] })
    if (assignee) params.set('assignee', assignee)
    if (typeId) params.set('typeId', typeId)
    if (hideClosed) params.set('status', 'PENDING')
    Promise.all([
      apiFetch<AgendaEntry[]>(`/api/v1/agenda-entries?${params}`),
      apiFetch<string[]>('/api/v1/agenda-entries/assignees').catch(() => []),
    ]).then(([list, people]) => { if (active) { setEntries(list); setAssignees(people); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [days, assignee, typeId, hideClosed, refresh])

  const typeOf = (id: string | null) => types.find((item) => item.id === id)
  const saved = (entry: AgendaEntry | null, message: string) => { setEditing(null); setAction(null); setSelected(entry); setRefresh((value) => value + 1); notify(message) }
  const run = async (entry: AgendaEntry, path: 'cancel' | 'reopen') => {
    if (path === 'cancel' && !(await confirm({ message: c(`¿Anular la cita «${entry.title}»?`, `Cancel the appointment "${entry.title}"?`), danger: true }))) return
    try { saved(await apiFetch<AgendaEntry>(`/api/v1/agenda-entries/${entry.id}/${path}`, { method: 'POST' }), path === 'cancel' ? c('Cita anulada.', 'Appointment cancelled.') : c('Cita reabierta.', 'Appointment reopened.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }
  const remove = async (entry: AgendaEntry) => {
    if (!(await confirm({ message: c(`¿Eliminar la cita «${entry.title}»?`, `Delete the appointment "${entry.title}"?`), danger: true }))) return
    try { await apiFetch(`/api/v1/agenda-entries/${entry.id}`, { method: 'DELETE' }); saved(null, c('Cita eliminada.', 'Appointment deleted.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }
  const dayTitle = (day: string) => new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'short' }).format(new Date(`${day}T00:00:00`))

  return <>
    <section className="panel table-panel">
      <div className="table-toolbar agenda-toolbar">
        <div className="agenda-week-nav">
          <button className="icon-button" type="button" onClick={() => setWeekStart(addDays(weekStart, -7))} aria-label={c('Semana anterior', 'Previous week')}><ChevronLeft size={18} /></button>
          <button className="button button-secondary button-small" type="button" onClick={() => setWeekStart(mondayOf(today))}>{c('Hoy', 'Today')}</button>
          <button className="icon-button" type="button" onClick={() => setWeekStart(addDays(weekStart, 7))} aria-label={c('Semana siguiente', 'Next week')}><ChevronRight size={18} /></button>
          <strong>{formatDate(days[0], locale)} – {formatDate(days[6], locale)}</strong>
        </div>
        <div className="toolbar-actions">
          <select aria-label={c('Filtrar por persona', 'Filter by person')} value={assignee} onChange={(event) => setAssignee(event.target.value)}><option value="">{c('Todas las personas', 'Everyone')}</option>{assignees.map((name) => <option key={name} value={name}>{name}</option>)}</select>
          <select aria-label={t('sales.filterType')} value={typeId} onChange={(event) => setTypeId(event.target.value)}><option value="">{t('sales.allTypes')}</option>{types.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select>
          <select aria-label={t('sales.filterStatus')} value={hideClosed ? 'PENDING' : ''} onChange={(event) => setHideClosed(event.target.value === 'PENDING')}><option value="">{c('Todas', 'All')}</option><option value="PENDING">{c('Solo pendientes', 'Pending only')}</option></select>
          <button className="button button-primary" type="button" onClick={() => setEditing({ date: days.includes(today) ? today : days[0] })}><Plus size={17} />{c('Nueva cita', 'New appointment')}</button>
        </div>
      </div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState /> : <div className="agenda-week">{days.map((day) => {
        const dayEntries = entries.filter((entry) => entry.date === day)
        return <section key={day} className={`agenda-day${day === today ? ' agenda-today' : ''}`} aria-label={dayTitle(day)}>
          <header><h3>{dayTitle(day)}</h3><button className="icon-button" type="button" onClick={() => setEditing({ date: day })} aria-label={c(`Nueva cita el ${dayTitle(day)}`, `New appointment on ${dayTitle(day)}`)}><Plus size={15} /></button></header>
          {dayEntries.length === 0 ? <p className="agenda-empty">{c('Sin citas', 'No appointments')}</p> : dayEntries.map((entry) => {
            const type = typeOf(entry.typeId)
            return <button key={entry.id} type="button" className={`agenda-entry agenda-${entry.status.toLowerCase()}`} style={type?.color ? { borderLeftColor: type.color } : undefined} onClick={() => setSelected(entry)}>
              <span className="agenda-time">{entry.startTime ? `${time(entry.startTime)}${entry.endTime ? `–${time(entry.endTime)}` : ''}` : c('Todo el día', 'All day')}</span>
              <strong>{entry.title}</strong>
              <small>{[type?.name, entry.assigneeName, entry.customerName ?? entry.contactName].filter(Boolean).join(' · ')}</small>
              {entry.status !== 'PENDING' && <StatusBadge tone={statusTone[entry.status]}>{statusLabels[entry.status][language === 'es' ? 0 : 1]}</StatusBadge>}
            </button>
          })}
        </section>
      })}</div>}
    </section>

    <Modal open={selected !== null && editing === null && action === null} title={selected?.title ?? ''} description={selected ? `${formatDate(selected.date, locale)}${selected.startTime ? ` · ${time(selected.startTime)}${selected.endTime ? `–${time(selected.endTime)}` : ''}` : ''}` : ''} onClose={() => setSelected(null)}>
      {selected && <div className="document-detail">
        <div className="detail-summary">
          <div><small>{c('Tipo', 'Type')}</small><strong>{typeOf(selected.typeId)?.name ?? '—'}</strong><span>{selected.assigneeName ?? ''}</span></div>
          <div><small>{t('sales.status')}</small><StatusBadge tone={statusTone[selected.status]}>{statusLabels[selected.status][language === 'es' ? 0 : 1]}</StatusBadge><span>{selected.completedOn ? formatDate(selected.completedOn, locale) : ''}</span></div>
          <div><small>{c('Con quién', 'With')}</small><strong>{selected.customerName ?? selected.contactName ?? '—'}</strong><span>{selected.contactPhone ?? ''}</span></div>
          <div><small>{c('Documento', 'Document')}</small><strong>{selected.documentReference ?? '—'}</strong><span>{c(`Creada por ${selected.createdByName}`, `Created by ${selected.createdByName}`)}</span></div>
        </div>
        {selected.location && <p className="detail-notes">{c('Dónde: ', 'Where: ')}{selected.location}</p>}
        {selected.details && <p className="detail-notes">{selected.details}</p>}
        {selected.outcome && <p className="detail-notes">{selected.status === 'DONE' ? c('Resultado: ', 'Outcome: ') : c('Motivo: ', 'Reason: ')}{selected.outcome}</p>}
        <div className="modal-action-strip">
          <button type="button" className="button button-danger" onClick={() => void remove(selected)}><Trash2 size={17} />{c('Eliminar', 'Delete')}</button>
          {selected.status === 'PENDING' ? <>
            <button type="button" className="button button-secondary" onClick={() => void run(selected, 'cancel')}><Ban size={17} />{c('Anular', 'Cancel')}</button>
            <button type="button" className="button button-secondary" onClick={() => setEditing(selected)}><Pencil size={17} />{c('Editar', 'Edit')}</button>
            <button type="button" className="button button-primary" onClick={() => setAction('complete')}><CheckCircle2 size={17} />{c('Marcar como hecha', 'Mark as done')}</button>
          </> : <button type="button" className="button button-secondary" onClick={() => void run(selected, 'reopen')}><RotateCcw size={17} />{c('Reabrir', 'Reopen')}</button>}
        </div>
      </div>}
    </Modal>
    <Modal open={selected !== null && action === 'complete'} title={c('Marcar como hecha', 'Mark as done')} description={selected?.title ?? ''} onClose={() => setAction(null)}>
      {selected && <CompleteForm entry={selected} onCancel={() => setAction(null)} onSaved={(entry) => saved(entry, c('Cita hecha.', 'Appointment done.'))} />}
    </Modal>
    <Modal open={editing !== null} title={editing && 'id' in editing ? c('Editar cita', 'Edit appointment') : c('Nueva cita', 'New appointment')} onClose={() => setEditing(null)} size="large">
      {editing && <EntryForm key={'id' in editing ? editing.id : editing.date} entry={'id' in editing ? editing : null} initialDate={editing.date} types={types} assignees={assignees} onCancel={() => setEditing(null)} onSaved={(entry) => saved(entry, c('Cita guardada.', 'Appointment saved.'))} />}
    </Modal>
  </>
}

function CompleteForm({ entry, onCancel, onSaved }: { entry: AgendaEntry; onCancel: () => void; onSaved: (entry: AgendaEntry) => void }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ completedOn: localIsoDate(), outcome: '' })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { onSaved(await apiFetch<AgendaEntry>(`/api/v1/agenda-entries/${entry.id}/complete`, { method: 'POST', body: JSON.stringify({ completedOn: form.completedOn, outcome: form.outcome.trim() || null }) })) }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={c('Fecha en que se hizo', 'Date done')} htmlFor="entry-completed" required><input id="entry-completed" type="date" value={form.completedOn} onChange={(event) => setForm({ ...form, completedOn: event.target.value })} required /></Field>
    <Field label={c('Qué se hizo', 'What was done')} htmlFor="entry-outcome" wide><textarea id="entry-outcome" rows={2} maxLength={1000} value={form.outcome} onChange={(event) => setForm({ ...form, outcome: event.target.value })} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={c('Marcar como hecha', 'Mark as done')} /></form>
}

function EntryForm({ entry, initialDate, types, assignees, onCancel, onSaved }: { entry: AgendaEntry | null; initialDate: string; types: AgendaEntryType[]; assignees: string[]; onCancel: () => void; onSaved: (entry: AgendaEntry) => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [customers, setCustomers] = useState<Customer[]>([])
  const [contacts, setContacts] = useState<Contact[]>([])
  const [form, setForm] = useState({
    date: entry?.date ?? initialDate, startTime: time(entry?.startTime ?? null), endTime: time(entry?.endTime ?? null), title: entry?.title ?? '',
    details: entry?.details ?? '', typeId: entry?.typeId ?? '', assigneeName: entry?.assigneeName ?? '', customerId: entry?.customerId ?? '',
    contactId: entry?.contactId ?? '', location: entry?.location ?? '', documentReference: entry?.documentReference ?? '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  // Cliente y contacto son opcionales: si el perfil no puede leer clientes, la cita se crea igual.
  useEffect(() => {
    apiFetch<PageResponse<Customer>>('/api/v1/customers?size=100&sort=legalName,asc').then((page) => setCustomers(page.content.filter((item) => item.active || item.id === entry?.customerId))).catch(() => setCustomers([]))
    apiFetch<FlatPage<Contact>>('/api/v1/contacts?active=true&size=200&sort=name,asc').then((page) => setContacts(page.content)).catch(() => setContacts([]))
  }, [entry])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const customer = customers.find((item) => item.id === form.customerId)
    const payload = {
      date: form.date, startTime: form.startTime || null, endTime: form.startTime && form.endTime ? form.endTime : null, title: form.title.trim(),
      details: form.details.trim() || null, typeId: form.typeId || null, assigneeName: form.assigneeName.trim() || null,
      customerId: customer?.id ?? null, customerName: customer?.legalName ?? null, contactId: form.contactId || null,
      location: form.location.trim() || null, documentReference: form.documentReference.trim() || null,
    }
    try { onSaved(await apiFetch<AgendaEntry>(entry ? `/api/v1/agenda-entries/${entry.id}` : '/api/v1/agenda-entries', { method: entry ? 'PUT' : 'POST', body: JSON.stringify(payload) })) }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Qué', 'What')} htmlFor="entry-title" name="title" required wide><input id="entry-title" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} maxLength={200} placeholder={c('Medir ventanas, montaje, llamar al cliente...', 'Measure windows, installation, call the customer...')} required /></Field>
    <Field label={c('Día', 'Day')} htmlFor="entry-date" name="date" required><input id="entry-date" type="date" value={form.date} onChange={(event) => setForm({ ...form, date: event.target.value })} required /></Field>
    <Field label={c('Tipo', 'Type')} htmlFor="entry-type" name="typeId"><select id="entry-type" value={form.typeId} onChange={(event) => setForm({ ...form, typeId: event.target.value })}><option value="">{c('Sin tipo', 'No type')}</option>{types.filter((type) => type.active || type.id === form.typeId).map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></Field>
    <Field label={c('Desde', 'From')} htmlFor="entry-start" name="startTime" hint={c('Vacío: todo el día.', 'Empty: all day.')}><input id="entry-start" type="time" value={form.startTime} onChange={(event) => setForm({ ...form, startTime: event.target.value })} /></Field>
    <Field label={c('Hasta', 'To')} htmlFor="entry-end" name="endTime"><input id="entry-end" type="time" value={form.endTime} disabled={!form.startTime} min={form.startTime || undefined} onChange={(event) => setForm({ ...form, endTime: event.target.value })} /></Field>
    <Field label={c('Quién', 'Who')} htmlFor="entry-assignee" name="assigneeName"><input id="entry-assignee" list="entry-assignees" value={form.assigneeName} onChange={(event) => setForm({ ...form, assigneeName: event.target.value })} maxLength={160} /><datalist id="entry-assignees">{assignees.map((name) => <option key={name} value={name} />)}</datalist></Field>
    <Field label={t('sales.customer')} htmlFor="entry-customer" name="customerId"><select id="entry-customer" value={form.customerId} onChange={(event) => setForm({ ...form, customerId: event.target.value })}><option value="">{c('Sin cliente', 'No customer')}</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.code} · {customer.legalName}</option>)}</select></Field>
    <Field label={c('Contacto del listín', 'Directory contact')} htmlFor="entry-contact" name="contactId"><select id="entry-contact" value={form.contactId} onChange={(event) => setForm({ ...form, contactId: event.target.value })}><option value="">{c('Sin contacto', 'No contact')}</option>{contacts.map((contact) => <option key={contact.id} value={contact.id}>{contact.name}{contact.mobile || contact.phone ? ` · ${contact.mobile ?? contact.phone}` : ''}</option>)}</select></Field>
    <Field label={c('Documento', 'Document')} htmlFor="entry-document" name="documentReference" hint={c('Presupuesto, pedido o albarán relacionado.', 'Related quote, order or delivery note.')}><input id="entry-document" value={form.documentReference} onChange={(event) => setForm({ ...form, documentReference: event.target.value })} maxLength={100} /></Field>
    <Field label={c('Dónde', 'Where')} htmlFor="entry-location" name="location" wide><input id="entry-location" value={form.location} onChange={(event) => setForm({ ...form, location: event.target.value })} maxLength={300} /></Field>
    <Field label={c('Notas', 'Notes')} htmlFor="entry-details" name="details" wide><textarea id="entry-details" rows={3} maxLength={2000} value={form.details} onChange={(event) => setForm({ ...form, details: event.target.value })} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={entry ? t('common.save') : c('Crear cita', 'Create appointment')} /></FormErrors></form>
}

function ContactsTab() {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [data, setData] = useState<FlatPage<Contact> | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<Contact | 'new' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [debouncedQuery])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '15', sort: 'name,asc' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    apiFetch<FlatPage<Contact>>(`/api/v1/contacts?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, debouncedQuery, refresh])

  return <>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={c('Buscar por nombre, empresa, teléfono, correo o población', 'Search by name, company, phone, email or town')}>
        <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nuevo contacto', 'New contact')}</button>
      </TableToolbar>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table>
          <TableCaption es="Listín de contactos" en="Contact directory" />
          <thead><tr><th>{c('Nombre', 'Name')}</th><th>{c('Teléfono', 'Phone')}</th><th>{c('Correo', 'Email')}</th><th>{c('Población', 'Town')}</th><th>{t('sales.customer')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead>
          <tbody>{data.content.map((contact) => <tr key={contact.id}>
            <td><strong>{contact.name}</strong>{contact.organization && <small>{contact.organization}</small>}</td>
            <td>{contact.mobile || contact.phone || '—'}{contact.mobile && contact.phone && <small>{contact.phone}</small>}</td>
            <td>{contact.email || '—'}</td>
            <td>{contact.city || '—'}</td>
            <td>{contact.customerName || '—'}</td>
            <td><button className="icon-button" type="button" onClick={() => setEditing(contact)} aria-label={c(`Editar ${contact.name}`, `Edit ${contact.name}`)}><Pencil size={16} /></button></td>
          </tr>)}</tbody>
        </table></div>
        <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
      </> : <EmptyState title={c('El listín está vacío', 'The directory is empty')} description={query ? t('common.noResults') : c('Guarda aquí proveedores de servicios, técnicos, arquitectos... cualquiera con quien habléis.', 'Keep service providers, technicians, architects... anyone you talk to.')} />}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? c('Nuevo contacto', 'New contact') : c('Editar contacto', 'Edit contact')} onClose={() => setEditing(null)} size="large">
      {editing && <ContactForm key={editing === 'new' ? 'new' : editing.id} contact={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); setRefresh((value) => value + 1); notify(c('Contacto guardado.', 'Contact saved.')) }} />}
    </Modal>
  </>
}

function ContactForm({ contact, onCancel, onSaved }: { contact: Contact | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [customers, setCustomers] = useState<Customer[]>([])
  const [form, setForm] = useState({
    name: contact?.name ?? '', organization: contact?.organization ?? '', phone: contact?.phone ?? '', mobile: contact?.mobile ?? '', email: contact?.email ?? '',
    address: contact?.address ?? '', postalCode: contact?.postalCode ?? '', city: contact?.city ?? '', region: contact?.region ?? '',
    customerId: contact?.customerId ?? '', notes: contact?.notes ?? '', active: contact?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const set = (name: keyof typeof form, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  useEffect(() => {
    apiFetch<PageResponse<Customer>>('/api/v1/customers?size=100&sort=legalName,asc').then((page) => setCustomers(page.content)).catch(() => setCustomers([]))
  }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const customer = customers.find((item) => item.id === form.customerId)
    const text = (value: string) => value.trim() || null
    const payload = { name: form.name.trim(), organization: text(form.organization), phone: text(form.phone), mobile: text(form.mobile), email: text(form.email), address: text(form.address), postalCode: text(form.postalCode), city: text(form.city), region: text(form.region), customerId: customer?.id ?? null, customerName: customer?.legalName ?? null, notes: text(form.notes), active: form.active }
    try { await apiFetch(contact ? `/api/v1/contacts/${contact.id}` : '/api/v1/contacts', { method: contact ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Nombre', 'Name')} htmlFor="contact-name" name="name" required><input id="contact-name" value={form.name} onChange={(event) => set('name', event.target.value)} maxLength={180} required /></Field>
    <Field label={c('Empresa', 'Company')} htmlFor="contact-organization" name="organization"><input id="contact-organization" value={form.organization} onChange={(event) => set('organization', event.target.value)} maxLength={180} /></Field>
    <Field label={c('Teléfono', 'Phone')} htmlFor="contact-phone" name="phone" hint={c('Al menos un teléfono, un móvil o un correo.', 'At least a phone, a mobile or an email.')}><input id="contact-phone" type="tel" value={form.phone} onChange={(event) => set('phone', event.target.value)} maxLength={40} /></Field>
    <Field label={c('Móvil', 'Mobile')} htmlFor="contact-mobile" name="mobile"><input id="contact-mobile" type="tel" value={form.mobile} onChange={(event) => set('mobile', event.target.value)} maxLength={40} /></Field>
    <Field label={c('Correo', 'Email')} htmlFor="contact-email" name="email"><input id="contact-email" type="email" value={form.email} onChange={(event) => set('email', event.target.value)} maxLength={180} /></Field>
    <Field label={t('sales.customer')} htmlFor="contact-customer" name="customerId" hint={c('Si este contacto trabaja para un cliente.', 'If this contact works for a customer.')}><select id="contact-customer" value={form.customerId} onChange={(event) => set('customerId', event.target.value)}><option value="">{c('No es de un cliente', 'Not from a customer')}</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.code} · {customer.legalName}</option>)}</select></Field>
    <Field label={c('Dirección', 'Address')} htmlFor="contact-address" name="address" wide><input id="contact-address" value={form.address} onChange={(event) => set('address', event.target.value)} maxLength={300} /></Field>
    <Field label={c('Código postal', 'Postal code')} htmlFor="contact-postal" name="postalCode"><input id="contact-postal" value={form.postalCode} onChange={(event) => set('postalCode', event.target.value)} maxLength={20} /></Field>
    <Field label={c('Población', 'Town')} htmlFor="contact-city" name="city"><input id="contact-city" value={form.city} onChange={(event) => set('city', event.target.value)} maxLength={120} /></Field>
    <Field label={c('Provincia', 'Province')} htmlFor="contact-region" name="region"><input id="contact-region" value={form.region} onChange={(event) => set('region', event.target.value)} maxLength={120} /></Field>
    <Field label={t('field.status')} htmlFor="contact-active" name="active"><label className="switch-row" htmlFor="contact-active"><input id="contact-active" type="checkbox" checked={form.active} onChange={(event) => set('active', event.target.checked)} /><span>{c('Contacto activo', 'Active contact')}</span></label></Field>
    <Field label={c('Notas', 'Notes')} htmlFor="contact-notes" name="notes" wide><textarea id="contact-notes" rows={2} maxLength={1000} value={form.notes} onChange={(event) => set('notes', event.target.value)} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={contact ? t('common.save') : c('Crear contacto', 'Create contact')} /></FormErrors></form>
}

function TypesTab({ types, onChanged }: { types: AgendaEntryType[]; onChanged: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [editing, setEditing] = useState<AgendaEntryType | 'new' | null>(null)
  const { notify } = useToast()

  return <>
    <section className="panel table-panel">
      <div className="panel-heading table-toolbar"><div><span className="eyebrow">{c('Configuración', 'Settings')}</span><h2>{c('Tipos de cita', 'Appointment types')}</h2></div><button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nuevo tipo', 'New type')}</button></div>
      {types.length === 0 ? <EmptyState title={c('No hay tipos de cita', 'There are no appointment types')} description={c('Por ejemplo: medición, montaje, visita comercial, reparación.', 'For example: measurement, installation, sales visit, repair.')} /> : <div className="table-scroll"><table>
        <TableCaption es="Tipos de cita" en="Appointment types" />
        <thead><tr><th>{c('Nombre', 'Name')}</th><th>{c('Color', 'Colour')}</th><th>{t('field.status')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead>
        <tbody>{types.map((type) => <tr key={type.id}>
          <td><strong>{type.name}</strong></td>
          <td>{type.color ? <span className="agenda-swatch" style={{ background: type.color }} aria-label={type.color} /> : '—'}</td>
          <td><StatusBadge tone={type.active ? 'success' : 'neutral'}>{type.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
          <td><button className="icon-button" type="button" onClick={() => setEditing(type)} aria-label={c(`Editar ${type.name}`, `Edit ${type.name}`)}><Pencil size={16} /></button></td>
        </tr>)}</tbody>
      </table></div>}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? c('Nuevo tipo de cita', 'New appointment type') : c('Editar tipo de cita', 'Edit appointment type')} onClose={() => setEditing(null)}>
      {editing && <TypeForm key={editing === 'new' ? 'new' : editing.id} type={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); onChanged(); notify(c('Tipo de cita guardado.', 'Appointment type saved.')) }} />}
    </Modal>
  </>
}

function TypeForm({ type, onCancel, onSaved }: { type: AgendaEntryType | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ name: type?.name ?? '', color: type?.color ?? '#2f6b3f', active: type?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try { await apiFetch(type ? `/api/v1/agenda-entry-types/${type.id}` : '/api/v1/agenda-entry-types', { method: type ? 'PUT' : 'POST', body: JSON.stringify({ name: form.name.trim(), color: form.color, active: form.active }) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Nombre', 'Name')} htmlFor="type-name" name="name" required><input id="type-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={80} required /></Field>
    <Field label={c('Color', 'Colour')} htmlFor="type-color" name="color"><input id="type-color" type="color" value={form.color} onChange={(event) => setForm({ ...form, color: event.target.value })} /></Field>
    <Field label={t('field.status')} htmlFor="type-active" name="active"><label className="switch-row" htmlFor="type-active"><input id="type-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Se puede elegir en citas nuevas', 'Can be chosen in new appointments')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={type ? t('common.save') : c('Crear tipo', 'Create type')} /></FormErrors></form>
}
