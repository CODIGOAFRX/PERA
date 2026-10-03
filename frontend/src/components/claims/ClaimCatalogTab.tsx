import { Pencil, Plus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from '../../i18n/I18nProvider'
import { apiFetch } from '../../lib/api'
import { claimCatalogKinds, claimCatalogLabels, claimCatalogPlurals, pickLabel } from '../../lib/claims'
import type { ClaimCatalogItem, ClaimCatalogKind } from '../../types/api'
import { EmptyState } from '../DataState'
import { Field, FormActions, FormErrors, useFormErrors } from '../Form'
import { Modal } from '../Modal'
import { StatusBadge } from '../StatusBadge'
import { TableCaption } from '../TableCaption'
import { useToast } from '../Toast'

/** Tablas con las que se clasifican las reclamaciones: motivos, causas, áreas... */
export function ClaimCatalogTab({ items, onChanged }: { items: ClaimCatalogItem[]; onChanged: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [kind, setKind] = useState<ClaimCatalogKind>('REASON')
  const [editing, setEditing] = useState<ClaimCatalogItem | 'new' | null>(null)
  const { notify } = useToast()
  const visible = items.filter((item) => item.kind === kind)

  return <>
    <nav className="workspace-tabs" aria-label={c('Tablas de clasificación', 'Classification tables')}>
      {claimCatalogKinds.map((item) => <button key={item} type="button" className={kind === item ? 'active' : ''} onClick={() => setKind(item)}>{pickLabel(claimCatalogPlurals[item], language)}<span className="tab-count">{items.filter((entry) => entry.kind === item && entry.active).length}</span></button>)}
    </nav>
    <section className="panel table-panel">
      <div className="panel-heading table-toolbar"><div><span className="eyebrow">{c('Tabla', 'Table')}</span><h2>{pickLabel(claimCatalogPlurals[kind], language)}</h2></div><button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Añadir', 'Add')}</button></div>
      {visible.length === 0 ? <EmptyState title={c('Tabla vacía', 'Empty table')} description={c('Añade los valores que usáis para clasificar las reclamaciones.', 'Add the values you use to classify claims.')} /> : <div className="table-scroll"><table>
        <TableCaption es={pickLabel(claimCatalogPlurals[kind], 'es')} en={pickLabel(claimCatalogPlurals[kind], 'en')} />
        <thead><tr><th>{c('Nombre', 'Name')}</th>{kind === 'PREVENTIVE_ACTION' && <th>{c('Plazo de seguimiento', 'Follow-up period')}</th>}<th>{t('field.status')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead>
        <tbody>{visible.map((item) => <tr key={item.id}>
          <td><strong>{item.name}</strong></td>
          {kind === 'PREVENTIVE_ACTION' && <td>{item.followUpDays === null ? '—' : c(`${item.followUpDays} días`, `${item.followUpDays} days`)}</td>}
          <td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
          <td><button className="icon-button" type="button" onClick={() => setEditing(item)} aria-label={c(`Editar ${item.name}`, `Edit ${item.name}`)}><Pencil size={16} /></button></td>
        </tr>)}</tbody>
      </table></div>}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? c(`Añadir a ${pickLabel(claimCatalogPlurals[kind], 'es').toLowerCase()}`, `Add to ${pickLabel(claimCatalogPlurals[kind], 'en').toLowerCase()}`) : pickLabel(claimCatalogLabels[kind], language)} onClose={() => setEditing(null)}>
      {editing && <CatalogItemForm key={editing === 'new' ? 'new' : editing.id} kind={kind} item={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); onChanged(); notify(c('Tabla actualizada.', 'Table updated.')) }} />}
    </Modal>
  </>
}

function CatalogItemForm({ kind, item, onCancel, onSaved }: { kind: ClaimCatalogKind; item: ClaimCatalogItem | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ name: item?.name ?? '', followUpDays: item?.followUpDays === null || item?.followUpDays === undefined ? '' : String(item.followUpDays), active: item?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = { kind, name: form.name.trim(), followUpDays: kind === 'PREVENTIVE_ACTION' && form.followUpDays !== '' ? Number(form.followUpDays) : null, active: form.active }
    try { await apiFetch(item ? `/api/v1/claim-catalog/${item.id}` : '/api/v1/claim-catalog', { method: item ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={c('Nombre', 'Name')} htmlFor="catalog-name" name="name" required wide><input id="catalog-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={200} required /></Field>
    {kind === 'PREVENTIVE_ACTION' && <Field label={c('Plazo de seguimiento (días)', 'Follow-up period (days)')} htmlFor="catalog-days" name="followUpDays" hint={c('Días para comprobar que la acción ha funcionado. La reclamación toma de aquí su fecha de seguimiento.', 'Days to check that the action worked. The claim takes its follow-up date from here.')}><input id="catalog-days" type="number" min="0" max="3650" value={form.followUpDays} onChange={(event) => setForm({ ...form, followUpDays: event.target.value })} /></Field>}
    <Field label={t('field.status')} htmlFor="catalog-active" name="active"><label className="switch-row" htmlFor="catalog-active"><input id="catalog-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Se puede elegir en reclamaciones nuevas', 'Can be chosen in new claims')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? t('common.save') : c('Añadir', 'Add')} /></FormErrors></form>
}
