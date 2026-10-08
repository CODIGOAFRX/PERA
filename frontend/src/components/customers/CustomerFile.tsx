import { MapPin, NotebookPen, Pencil, Plus, Star, Trash2, UserRound } from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from '../../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../../lib/api'
import { catalogItemName, salespersonName } from '../../lib/customers'
import { formatCurrency, formatDateTime } from '../../lib/format'
import type { Customer, CustomerAddress, CustomerCatalogItem, CustomerContact, CustomerNote, Salesperson } from '../../types/api'
import { useConfirm } from '../ConfirmDialog'
import { EmptyState, LoadingState } from '../DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../Form'
import { Modal } from '../Modal'
import { StatusBadge } from '../StatusBadge'
import { TableCaption } from '../TableCaption'
import { useToast } from '../Toast'

type Section = 'summary' | 'contacts' | 'addresses' | 'notes'

/** Ficha del cliente: resumen, personas de contacto, direcciones de entrega y notas. */
export function CustomerFile({ customer, catalog, salespeople, canWrite, actions }: { customer: Customer; catalog: CustomerCatalogItem[]; salespeople: Salesperson[]; canWrite: boolean; actions: ReactNode }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [section, setSection] = useState<Section>('summary')

  return <div className="document-detail">
    <nav className="workspace-tabs" aria-label={c('Apartados de la ficha', 'Customer record sections')}>
      <button type="button" className={section === 'summary' ? 'active' : ''} onClick={() => setSection('summary')}>{c('Ficha', 'Record')}</button>
      <button type="button" className={section === 'contacts' ? 'active' : ''} onClick={() => setSection('contacts')}><UserRound size={15} />{c('Contactos', 'Contacts')}</button>
      <button type="button" className={section === 'addresses' ? 'active' : ''} onClick={() => setSection('addresses')}><MapPin size={15} />{c('Direcciones de entrega', 'Delivery addresses')}</button>
      <button type="button" className={section === 'notes' ? 'active' : ''} onClick={() => setSection('notes')}><NotebookPen size={15} />{c('Notas', 'Notes')}</button>
    </nav>
    {section === 'summary' && <CustomerSummary customer={customer} catalog={catalog} salespeople={salespeople} />}
    {section === 'contacts' && <ContactsSection customer={customer} canWrite={canWrite} />}
    {section === 'addresses' && <AddressesSection customer={customer} canWrite={canWrite} />}
    {section === 'notes' && <NotesSection customer={customer} canWrite={canWrite} />}
    {section === 'summary' && actions}
  </div>
}

function CustomerSummary({ customer, catalog, salespeople }: { customer: Customer; catalog: CustomerCatalogItem[]; salespeople: Salesperson[] }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const classification = customer.classification
  const address = [customer.details?.addressLine1, [customer.details?.postalCode, customer.details?.city].filter(Boolean).join(' '), customer.details?.region].filter(Boolean).join(', ')
  return <>
    <div className="detail-summary">
      <div><small>{t('field.taxId')}</small><strong>{customer.taxId || '—'}</strong><span>{customer.tradeName ?? ''}</span></div>
      <div><small>{c('Contacto', 'Contact')}</small><strong>{customer.phone || classification?.mobile || '—'}</strong><span>{customer.email ?? ''}</span></div>
      <div><small>{c('Comercial', 'Salesperson')}</small><strong>{salespersonName(salespeople, classification?.salespersonId)}</strong><span>{catalogItemName(catalog, classification?.groupId)}</span></div>
      <div><small>{t('field.status')}</small><StatusBadge tone={customer.active ? 'success' : 'neutral'}>{customer.active ? t('common.active') : t('common.inactive')}</StatusBadge><span>{customer.active ? '' : catalogItemName(catalog, classification?.inactiveReasonId)}</span></div>
    </div>
    <div className="table-scroll detail-lines"><table>
      <TableCaption es="Datos de la ficha" en="Record data" />
      <tbody>
        <tr><th scope="row">{c('Dirección fiscal', 'Billing address')}</th><td>{address || '—'}</td></tr>
        <tr><th scope="row">{c('Tipo de cliente', 'Customer type')}</th><td>{catalogItemName(catalog, classification?.typeId)}</td></tr>
        <tr><th scope="row">{c('Forma de entrega', 'Delivery method')}</th><td>{catalogItemName(catalog, classification?.deliveryMethodId)}</td></tr>
        <tr><th scope="row">{c('Móvil', 'Mobile')}</th><td>{classification?.mobile || '—'}</td></tr>
        <tr><th scope="row">{c('Cuenta contable', 'Ledger account')}</th><td>{classification?.accountingAccount || '—'}</td></tr>
        <tr><th scope="row">{t('customers.creditLimit')}</th><td>{formatCurrency(customer.creditLimit, 'EUR', locale)}</td></tr>
        <tr><th scope="row">{c('Código como proveedor', 'Supplier code')}</th><td>{customer.supplierCode || '—'}</td></tr>
      </tbody>
    </table></div>
    {customer.observations && <p className="detail-notes">{customer.observations}</p>}
  </>
}

/** Carga una lista de la ficha y la vuelve a pedir cuando cambia algo. */
function useCustomerList<T>(path: string) {
  const [items, setItems] = useState<T[] | null>(null)
  const [error, setError] = useState('')
  const [version, setVersion] = useState(0)
  useEffect(() => {
    let active = true
    apiFetch<T[]>(path).then((response) => { if (active) { setItems(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
    return () => { active = false }
  }, [path, version])
  return { items, error, reload: () => setVersion((value) => value + 1) }
}

function ContactsSection({ customer, canWrite }: { customer: Customer; canWrite: boolean }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const base = `/api/v1/customers/${customer.id}/contacts`
  const { items, error, reload } = useCustomerList<CustomerContact>(base)
  const [editing, setEditing] = useState<CustomerContact | 'new' | null>(null)
  const { notify } = useToast()
  const confirm = useConfirm()

  const remove = async (contact: CustomerContact) => {
    if (!(await confirm({ message: c(`¿Quitar a ${contact.name} de los contactos?`, `Remove ${contact.name} from the contacts?`), danger: true }))) return
    try { await apiFetch(`${base}/${contact.id}`, { method: 'DELETE' }); reload(); notify(c('Contacto quitado.', 'Contact removed.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <>
    <SectionHeading eyebrow={c('Personas', 'People')} title={c('Contactos', 'Contacts')} action={canWrite && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Añadir contacto', 'Add contact')}</button>} />
    {error && <div className="inline-error" role="alert">{error}</div>}
    {items === null ? <LoadingState /> : items.length === 0 ? <EmptyState title={c('Sin contactos', 'No contacts')} description={c('Añade a las personas con las que habláis: compras, administración, obra...', 'Add the people you talk to: purchasing, accounts, site...')} /> : <div className="table-scroll detail-lines"><table>
      <TableCaption es="Contactos del cliente" en="Customer contacts" />
      <thead><tr><th>{c('Nombre', 'Name')}</th><th>{c('Cargo', 'Position')}</th><th>{t('field.phone')}</th><th>{t('field.email')}</th>{canWrite && <th><span className="sr-only">{t('common.actions')}</span></th>}</tr></thead>
      <tbody>{items.map((contact) => <tr key={contact.id}>
        <td><strong>{contact.name}</strong>{contact.primaryContact && <small><Star size={11} aria-hidden="true" /> {c('Principal', 'Main')}</small>}</td>
        <td>{contact.position || '—'}</td>
        <td>{[contact.phone, contact.mobile].filter(Boolean).join(' · ') || '—'}</td>
        <td>{contact.email || '—'}</td>
        {canWrite && <td><div className="row-actions"><button className="icon-button" type="button" onClick={() => setEditing(contact)} aria-label={c(`Editar ${contact.name}`, `Edit ${contact.name}`)}><Pencil size={16} /></button><button className="icon-button" type="button" onClick={() => void remove(contact)} aria-label={c(`Quitar ${contact.name}`, `Remove ${contact.name}`)}><Trash2 size={16} /></button></div></td>}
      </tr>)}</tbody>
    </table></div>}
    <Modal open={editing !== null} title={editing === 'new' ? c('Nuevo contacto', 'New contact') : c('Editar contacto', 'Edit contact')} onClose={() => setEditing(null)}>
      {editing && <ContactForm key={editing === 'new' ? 'new' : editing.id} path={editing === 'new' ? base : `${base}/${editing.id}`} contact={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); notify(c('Contacto guardado.', 'Contact saved.')) }} />}
    </Modal>
  </>
}

function ContactForm({ path, contact, onCancel, onSaved }: { path: string; contact: CustomerContact | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ name: contact?.name ?? '', position: contact?.position ?? '', phone: contact?.phone ?? '', mobile: contact?.mobile ?? '', email: contact?.email ?? '', notes: contact?.notes ?? '', primaryContact: contact?.primaryContact ?? false })
  const { saving, error, formErrors, send } = useSubmit(onSaved)
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  return <form onSubmit={(event) => send(event, path, contact ? 'PUT' : 'POST', { ...form, name: form.name.trim() })}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Nombre', 'Name')} htmlFor="contact-name" name="name" required><input id="contact-name" value={form.name} onChange={(event) => update('name', event.target.value)} maxLength={160} required /></Field>
    <Field label={c('Cargo', 'Position')} htmlFor="contact-position" name="position"><input id="contact-position" value={form.position} onChange={(event) => update('position', event.target.value)} maxLength={120} placeholder={c('Compras, administración...', 'Purchasing, accounts...')} /></Field>
    <Field label={t('field.phone')} htmlFor="contact-phone" name="phone"><input id="contact-phone" value={form.phone} onChange={(event) => update('phone', event.target.value)} maxLength={40} /></Field>
    <Field label={c('Móvil', 'Mobile')} htmlFor="contact-mobile" name="mobile"><input id="contact-mobile" value={form.mobile} onChange={(event) => update('mobile', event.target.value)} maxLength={40} /></Field>
    <Field label={t('field.email')} htmlFor="contact-email" name="email"><input id="contact-email" type="email" value={form.email} onChange={(event) => update('email', event.target.value)} maxLength={180} /></Field>
    <Field label={c('Principal', 'Main contact')} htmlFor="contact-primary" name="primaryContact"><label className="switch-row" htmlFor="contact-primary"><input id="contact-primary" type="checkbox" checked={form.primaryContact} onChange={(event) => update('primaryContact', event.target.checked)} /><span>{c('Es el contacto habitual', 'Usual contact person')}</span></label></Field>
    <Field label={t('field.observations')} htmlFor="contact-notes" name="notes" wide><textarea id="contact-notes" rows={2} value={form.notes} onChange={(event) => update('notes', event.target.value)} maxLength={300} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={contact ? t('common.save') : c('Añadir contacto', 'Add contact')} /></FormErrors></form>
}

function AddressesSection({ customer, canWrite }: { customer: Customer; canWrite: boolean }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const base = `/api/v1/customers/${customer.id}/addresses`
  const { items, error, reload } = useCustomerList<CustomerAddress>(base)
  const [editing, setEditing] = useState<CustomerAddress | 'new' | null>(null)
  const { notify } = useToast()
  const confirm = useConfirm()

  const remove = async (address: CustomerAddress) => {
    if (!(await confirm({ message: c(`¿Quitar la dirección ${address.label || address.line1}?`, `Remove address ${address.label || address.line1}?`), danger: true }))) return
    try { await apiFetch(`${base}/${address.id}`, { method: 'DELETE' }); reload(); notify(c('Dirección quitada.', 'Address removed.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <>
    <SectionHeading eyebrow={c('Además de la fiscal', 'Besides the billing one')} title={c('Direcciones de entrega', 'Delivery addresses')} action={canWrite && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Añadir dirección', 'Add address')}</button>} />
    {error && <div className="inline-error" role="alert">{error}</div>}
    {items === null ? <LoadingState /> : items.length === 0 ? <EmptyState title={c('Sin direcciones de entrega', 'No delivery addresses')} description={c('Si se entrega siempre en la dirección fiscal, no hace falta añadir ninguna.', 'If goods always go to the billing address, there is no need to add any.')} /> : <div className="table-scroll detail-lines"><table>
      <TableCaption es="Direcciones de entrega" en="Delivery addresses" />
      <thead><tr><th>{c('Nombre', 'Name')}</th><th>{c('Dirección', 'Address')}</th><th>{t('field.phone')}</th><th>{t('field.status')}</th>{canWrite && <th><span className="sr-only">{t('common.actions')}</span></th>}</tr></thead>
      <tbody>{items.map((address) => <tr key={address.id}>
        <td><strong>{address.label || '—'}</strong>{address.primaryAddress && <small><Star size={11} aria-hidden="true" /> {c('Habitual', 'Usual')}</small>}</td>
        <td>{address.line1}{address.line2 && <small>{address.line2}</small>}<small>{[address.postalCode, address.city, address.province].filter(Boolean).join(' · ')}</small></td>
        <td>{address.contactPhone || '—'}</td>
        <td><StatusBadge tone={address.active ? 'success' : 'neutral'}>{address.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
        {canWrite && <td><div className="row-actions"><button className="icon-button" type="button" onClick={() => setEditing(address)} aria-label={c(`Editar ${address.label || address.line1}`, `Edit ${address.label || address.line1}`)}><Pencil size={16} /></button><button className="icon-button" type="button" onClick={() => void remove(address)} aria-label={c(`Quitar ${address.label || address.line1}`, `Remove ${address.label || address.line1}`)}><Trash2 size={16} /></button></div></td>}
      </tr>)}</tbody>
    </table></div>}
    <Modal open={editing !== null} title={editing === 'new' ? c('Nueva dirección de entrega', 'New delivery address') : c('Editar dirección de entrega', 'Edit delivery address')} onClose={() => setEditing(null)}>
      {editing && <AddressForm key={editing === 'new' ? 'new' : editing.id} path={editing === 'new' ? base : `${base}/${editing.id}`} address={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); notify(c('Dirección guardada.', 'Address saved.')) }} />}
    </Modal>
  </>
}

function AddressForm({ path, address, onCancel, onSaved }: { path: string; address: CustomerAddress | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ label: address?.label ?? '', line1: address?.line1 ?? '', line2: address?.line2 ?? '', postalCode: address?.postalCode ?? '', city: address?.city ?? '', province: address?.province ?? '', country: address?.country ?? '', contactPhone: address?.contactPhone ?? '', primaryAddress: address?.primaryAddress ?? false, active: address?.active ?? true })
  const { saving, error, formErrors, send } = useSubmit(onSaved)
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  return <form onSubmit={(event) => send(event, path, address ? 'PUT' : 'POST', { ...form, line1: form.line1.trim() })}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Nombre', 'Name')} htmlFor="address-label" name="label" hint={c('Para reconocerla: obra, almacén, tienda...', 'To recognise it: site, warehouse, shop...')}><input id="address-label" value={form.label} onChange={(event) => update('label', event.target.value)} maxLength={120} /></Field>
    <Field label={c('Teléfono en destino', 'Phone at destination')} htmlFor="address-phone" name="contactPhone"><input id="address-phone" value={form.contactPhone} onChange={(event) => update('contactPhone', event.target.value)} maxLength={40} /></Field>
    <Field label={c('Dirección', 'Address')} htmlFor="address-line1" name="line1" required wide><input id="address-line1" value={form.line1} onChange={(event) => update('line1', event.target.value)} maxLength={200} required /></Field>
    <Field label={c('Dirección (continuación)', 'Address (continued)')} htmlFor="address-line2" name="line2" wide><input id="address-line2" value={form.line2} onChange={(event) => update('line2', event.target.value)} maxLength={200} /></Field>
    <Field label={c('Código postal', 'Postal code')} htmlFor="address-postal" name="postalCode"><input id="address-postal" value={form.postalCode} onChange={(event) => update('postalCode', event.target.value)} maxLength={20} /></Field>
    <Field label={c('Población', 'Town')} htmlFor="address-city" name="city"><input id="address-city" value={form.city} onChange={(event) => update('city', event.target.value)} maxLength={100} /></Field>
    <Field label={c('Provincia', 'Province')} htmlFor="address-province" name="province"><input id="address-province" value={form.province} onChange={(event) => update('province', event.target.value)} maxLength={100} /></Field>
    <Field label={c('País', 'Country')} htmlFor="address-country" name="country"><input id="address-country" value={form.country} onChange={(event) => update('country', event.target.value)} maxLength={100} /></Field>
    <Field label={c('Habitual', 'Usual')} htmlFor="address-primary" name="primaryAddress"><label className="switch-row" htmlFor="address-primary"><input id="address-primary" type="checkbox" checked={form.primaryAddress && form.active} disabled={!form.active} onChange={(event) => update('primaryAddress', event.target.checked)} /><span>{c('Se propone en las entregas', 'Suggested for deliveries')}</span></label></Field>
    <Field label={t('field.status')} htmlFor="address-active" name="active"><label className="switch-row" htmlFor="address-active"><input id="address-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{c('En uso', 'In use')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={address ? t('common.save') : c('Añadir dirección', 'Add address')} /></FormErrors></form>
}

function NotesSection({ customer, canWrite }: { customer: Customer; canWrite: boolean }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const base = `/api/v1/customers/${customer.id}/notes`
  const { items, error, reload } = useCustomerList<CustomerNote>(base)
  const [editing, setEditing] = useState<CustomerNote | 'new' | null>(null)
  const { notify } = useToast()
  const confirm = useConfirm()

  const remove = async (note: CustomerNote) => {
    if (!(await confirm({ message: c(`¿Borrar la nota «${note.title}»?`, `Delete the note "${note.title}"?`), danger: true }))) return
    try { await apiFetch(`${base}/${note.id}`, { method: 'DELETE' }); reload(); notify(c('Nota borrada.', 'Note deleted.')) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <>
    <SectionHeading eyebrow={c('Internas', 'Internal')} title={c('Notas', 'Notes')} action={canWrite && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Añadir nota', 'Add note')}</button>} />
    {error && <div className="inline-error" role="alert">{error}</div>}
    {items === null ? <LoadingState /> : items.length === 0 ? <EmptyState title={c('Sin notas', 'No notes')} description={c('Apunta lo que conviene recordar de este cliente: horarios, avisos, acuerdos...', 'Write down what is worth remembering about this customer: hours, warnings, agreements...')} /> : items.map((note) => <div className="detail-notes customer-note" key={note.id}>
      <div><strong>{note.title}</strong>{note.showOnDocuments && <> <StatusBadge tone="info">{c('Sale en documentos', 'Shown on documents')}</StatusBadge></>}<small>{formatDateTime(note.updatedAt, locale)}</small></div>
      <p>{note.message}</p>
      {canWrite && <div className="row-actions"><button className="icon-button" type="button" onClick={() => setEditing(note)} aria-label={c(`Editar ${note.title}`, `Edit ${note.title}`)}><Pencil size={16} /></button><button className="icon-button" type="button" onClick={() => void remove(note)} aria-label={c(`Borrar ${note.title}`, `Delete ${note.title}`)}><Trash2 size={16} /></button></div>}
    </div>)}
    <Modal open={editing !== null} title={editing === 'new' ? c('Nueva nota', 'New note') : c('Editar nota', 'Edit note')} onClose={() => setEditing(null)}>
      {editing && <NoteForm key={editing === 'new' ? 'new' : editing.id} path={editing === 'new' ? base : `${base}/${editing.id}`} note={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); notify(c('Nota guardada.', 'Note saved.')) }} />}
    </Modal>
  </>
}

function NoteForm({ path, note, onCancel, onSaved }: { path: string; note: CustomerNote | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ title: note?.title ?? '', message: note?.message ?? '', showOnDocuments: note?.showOnDocuments ?? false })
  const { saving, error, formErrors, send } = useSubmit(onSaved)

  return <form onSubmit={(event) => send(event, path, note ? 'PUT' : 'POST', { ...form, title: form.title.trim(), message: form.message.trim() })}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Título', 'Title')} htmlFor="note-title" name="title" required wide><input id="note-title" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} maxLength={180} required /></Field>
    <Field label={c('Texto', 'Text')} htmlFor="note-message" name="message" required wide><textarea id="note-message" rows={4} value={form.message} onChange={(event) => setForm({ ...form, message: event.target.value })} maxLength={4000} required /></Field>
    <Field label={c('En documentos', 'On documents')} htmlFor="note-documents" name="showOnDocuments" wide hint={c('Sustituye al «texto para documentos» del programa anterior. Saldrá impreso cuando lleguen las plantillas de documento.', 'Replaces the old "document text". It will be printed once document templates arrive.')}><label className="switch-row" htmlFor="note-documents"><input id="note-documents" type="checkbox" checked={form.showOnDocuments} onChange={(event) => setForm({ ...form, showOnDocuments: event.target.checked })} /><span>{c('Mostrar en presupuestos, albaranes y facturas', 'Show on quotes, delivery notes and invoices')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={note ? t('common.save') : c('Añadir nota', 'Add note')} /></FormErrors></form>
}

function SectionHeading({ eyebrow, title, action }: { eyebrow: string; title: string; action?: ReactNode }) {
  return <div className="document-lines-heading"><div><span className="eyebrow">{eyebrow}</span><h3>{title}</h3></div>{action}</div>
}

/** Envío común de los formularios de la ficha: los campos vacíos viajan como nulos. */
function useSubmit(onSaved: () => void) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const send = async (event: FormEvent, path: string, method: 'POST' | 'PUT', values: Record<string, string | boolean>) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, typeof value === 'string' ? value.trim() || null : value]))
    try { await apiFetch(path, { method, body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }
  return { saving, error, formErrors, send }
}
