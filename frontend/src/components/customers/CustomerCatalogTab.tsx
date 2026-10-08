import { Pencil, Plus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from '../../i18n/I18nProvider'
import { apiFetch } from '../../lib/api'
import { customerCatalogKinds, customerCatalogLabels, customerCatalogPlurals, pickLabel } from '../../lib/customers'
import type { CustomerCatalogItem, CustomerCatalogKind } from '../../types/api'
import { EmptyState } from '../DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../Form'
import { Modal } from '../Modal'
import { StatusBadge } from '../StatusBadge'
import { TableCaption } from '../TableCaption'
import { useToast } from '../Toast'

/** Grupos, tipos, formas de entrega y motivos de baja con los que se clasifica a los clientes. */
export function CustomerCatalogTab({ items, canWrite, onChanged }: { items: CustomerCatalogItem[]; canWrite: boolean; onChanged: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [kind, setKind] = useState<CustomerCatalogKind>('GROUP')
  const [editing, setEditing] = useState<CustomerCatalogItem | 'new' | null>(null)
  const { notify } = useToast()
  const visible = items.filter((item) => item.kind === kind)

  return <>
    <nav className="workspace-tabs" aria-label={c('Tablas de clasificación', 'Classification tables')}>
      {customerCatalogKinds.map((item) => <button key={item} type="button" className={kind === item ? 'active' : ''} onClick={() => setKind(item)}>{pickLabel(customerCatalogPlurals[item], language)}<span className="tab-count">{items.filter((entry) => entry.kind === item && entry.active).length}</span></button>)}
    </nav>
    <section className="panel table-panel">
      <div className="panel-heading table-toolbar"><div><span className="eyebrow">{c('Tabla', 'Table')}</span><h2>{pickLabel(customerCatalogPlurals[kind], language)}</h2></div>{canWrite && <button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Añadir', 'Add')}</button>}</div>
      {visible.length === 0 ? <EmptyState title={c('Tabla vacía', 'Empty table')} description={c('Añade los valores que usáis para clasificar a los clientes.', 'Add the values you use to classify customers.')} /> : <div className="table-scroll"><table>
        <TableCaption es={pickLabel(customerCatalogPlurals[kind], 'es')} en={pickLabel(customerCatalogPlurals[kind], 'en')} />
        <thead><tr><th>{c('Nombre', 'Name')}</th><th>{t('field.status')}</th>{canWrite && <th><span className="sr-only">{t('common.actions')}</span></th>}</tr></thead>
        <tbody>{visible.map((item) => <tr key={item.id}>
          <td><strong>{item.name}</strong></td>
          <td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
          {canWrite && <td><button className="icon-button" type="button" onClick={() => setEditing(item)} aria-label={c(`Editar ${item.name}`, `Edit ${item.name}`)}><Pencil size={16} /></button></td>}
        </tr>)}</tbody>
      </table></div>}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? c(`Añadir a ${pickLabel(customerCatalogPlurals[kind], 'es').toLowerCase()}`, `Add to ${pickLabel(customerCatalogPlurals[kind], 'en').toLowerCase()}`) : pickLabel(customerCatalogLabels[kind], language)} onClose={() => setEditing(null)}>
      {editing && <CatalogItemForm key={editing === 'new' ? 'new' : editing.id} kind={kind} item={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); onChanged(); notify(c('Tabla actualizada.', 'Table updated.')) }} />}
    </Modal>
  </>
}

function CatalogItemForm({ kind, item, onCancel, onSaved }: { kind: CustomerCatalogKind; item: CustomerCatalogItem | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ name: item?.name ?? '', active: item?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = { kind, name: form.name.trim(), active: form.active }
    try { await apiFetch(item ? `/api/v1/customer-catalog/${item.id}` : '/api/v1/customer-catalog', { method: item ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Nombre', 'Name')} htmlFor="customer-catalog-name" name="name" required wide><input id="customer-catalog-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={160} required /></Field>
    <Field label={t('field.status')} htmlFor="customer-catalog-active" name="active"><label className="switch-row" htmlFor="customer-catalog-active"><input id="customer-catalog-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Se puede elegir en las fichas', 'Can be chosen in customer records')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? t('common.save') : c('Añadir', 'Add')} /></FormErrors></form>
}
