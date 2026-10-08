import { Pencil, Plus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from '../../i18n/I18nProvider'
import { apiFetch } from '../../lib/api'
import { formatNumber } from '../../lib/format'
import type { Salesperson } from '../../types/api'
import { EmptyState } from '../DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../Form'
import { Modal } from '../Modal'
import { StatusBadge } from '../StatusBadge'
import { TableCaption } from '../TableCaption'
import { useToast } from '../Toast'

/** Comerciales que llevan clientes. La comisión es la de por defecto; el cálculo llegará con las comisiones. */
export function SalespeopleTab({ people, canWrite, onChanged }: { people: Salesperson[]; canWrite: boolean; onChanged: () => void }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [editing, setEditing] = useState<Salesperson | 'new' | null>(null)
  const { notify } = useToast()

  return <>
    <section className="panel table-panel">
      <div className="panel-heading table-toolbar"><div><span className="eyebrow">{c('Equipo comercial', 'Sales team')}</span><h2>{c('Comerciales', 'Salespeople')}</h2></div>{canWrite && <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nuevo comercial', 'New salesperson')}</button>}</div>
      {people.length === 0 ? <EmptyState title={c('No hay comerciales', 'There are no salespeople')} description={c('Da de alta a los comerciales para asignarles clientes.', 'Add salespeople to assign customers to them.')} /> : <div className="table-scroll"><table>
        <TableCaption es="Comerciales" en="Salespeople" />
        <thead><tr><th>{t('field.code')}</th><th>{c('Nombre', 'Name')}</th><th>{c('Contacto', 'Contact')}</th><th className="align-right">{c('Comisión', 'Commission')}</th><th>{t('field.status')}</th>{canWrite && <th><span className="sr-only">{t('common.actions')}</span></th>}</tr></thead>
        <tbody>{people.map((person) => <tr key={person.id}>
          <td><span className="code-cell">{person.code}</span></td>
          <td><strong>{person.name}</strong></td>
          <td>{person.email || person.phone || '—'}</td>
          <td className="align-right">{person.commissionPercentage === null ? '—' : `${formatNumber(person.commissionPercentage, locale, 2)} %`}</td>
          <td><StatusBadge tone={person.active ? 'success' : 'neutral'}>{person.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
          {canWrite && <td><button className="icon-button" type="button" onClick={() => setEditing(person)} aria-label={c(`Editar ${person.name}`, `Edit ${person.name}`)}><Pencil size={16} /></button></td>}
        </tr>)}</tbody>
      </table></div>}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? c('Nuevo comercial', 'New salesperson') : c('Editar comercial', 'Edit salesperson')} onClose={() => setEditing(null)}>
      {editing && <SalespersonForm key={editing === 'new' ? 'new' : editing.id} person={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); onChanged(); notify(c('Comercial guardado.', 'Salesperson saved.')) }} />}
    </Modal>
  </>
}

function SalespersonForm({ person, onCancel, onSaved }: { person: Salesperson | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({
    code: person?.code ?? '', name: person?.name ?? '', email: person?.email ?? '', phone: person?.phone ?? '',
    commission: person?.commissionPercentage === null || person?.commissionPercentage === undefined ? '' : String(person.commissionPercentage),
    active: person?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = { code: form.code.trim(), name: form.name.trim(), email: form.email.trim() || null, phone: form.phone.trim() || null, commissionPercentage: form.commission === '' ? null : Number(form.commission), active: form.active }
    try { await apiFetch(person ? `/api/v1/salespeople/${person.id}` : '/api/v1/salespeople', { method: person ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={t('field.code')} htmlFor="salesperson-code" name="code" required><input id="salesperson-code" value={form.code} onChange={(event) => update('code', event.target.value)} disabled={Boolean(person)} maxLength={20} required /></Field>
    <Field label={c('Nombre', 'Name')} htmlFor="salesperson-name" name="name" required><input id="salesperson-name" value={form.name} onChange={(event) => update('name', event.target.value)} maxLength={160} required /></Field>
    <Field label={t('field.email')} htmlFor="salesperson-email" name="email"><input id="salesperson-email" type="email" value={form.email} onChange={(event) => update('email', event.target.value)} maxLength={180} /></Field>
    <Field label={t('field.phone')} htmlFor="salesperson-phone" name="phone"><input id="salesperson-phone" value={form.phone} onChange={(event) => update('phone', event.target.value)} maxLength={40} /></Field>
    <Field label={c('Comisión por defecto (%)', 'Default commission (%)')} htmlFor="salesperson-commission" name="commissionPercentage" hint={c('Se usará al calcular las comisiones de sus ventas.', 'It will be used to calculate the commission on their sales.')}><input id="salesperson-commission" type="number" min="0" max="100" step="0.01" value={form.commission} onChange={(event) => update('commission', event.target.value)} /></Field>
    <Field label={t('field.status')} htmlFor="salesperson-active" name="active"><label className="switch-row" htmlFor="salesperson-active"><input id="salesperson-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{c('Se le pueden asignar clientes', 'Customers can be assigned')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={person ? t('common.save') : c('Crear comercial', 'Create salesperson')} /></FormErrors></form>
}
