import { Building2, Pencil, Plus } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { apiFetch, errorMessage } from '../lib/api'
import type { PageResponse, Supplier, SupplierInput } from '../types/api'

export function SuppliersPage() {
  const [data, setData] = useState<PageResponse<Supplier> | null>(null)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<Supplier | 'new' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [debouncedQuery])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'legalName,asc' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    apiFetch<PageResponse<Supplier>>(`/api/v1/suppliers?${params}`).then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [debouncedQuery, page, refresh])

  const saved = () => { setEditing(null); setRefresh((value) => value + 1); notify('Proveedor guardado correctamente.') }

  return <div className="page-stack">
    <PageHeader eyebrow="Maestros" title="Proveedores" description="Contactos y datos logísticos preparados para el ciclo de compras." icon={Building2} actions={<button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />Nuevo proveedor</button>} />
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder="Buscar por nombre, código o NIF" />
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <><div className="table-scroll"><table><thead><tr><th>Código</th><th>Proveedor</th><th>NIF / CIF</th><th>Contacto</th><th>Transportista</th><th>Ruta</th><th>Estado</th><th><span className="sr-only">Acciones</span></th></tr></thead><tbody>{data.content.map((supplier) => <tr key={supplier.id}><td><span className="code-cell">{supplier.code}</span></td><td><strong>{supplier.legalName}</strong>{supplier.tradeName && <small>{supplier.tradeName}</small>}</td><td>{supplier.taxId || '—'}</td><td>{supplier.email || supplier.phone || '—'}</td><td>{supplier.carrier || '—'}</td><td>{supplier.route || '—'}</td><td><StatusBadge tone={supplier.active ? 'success' : 'neutral'}>{supplier.active ? 'Activo' : 'Inactivo'}</StatusBadge></td><td><button className="icon-button" type="button" onClick={() => setEditing(supplier)} aria-label={`Editar ${supplier.legalName}`}><Pencil size={16} /></button></td></tr>)}</tbody></table></div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title="No hay proveedores" description={query ? 'No se han encontrado coincidencias.' : 'Añade el primer proveedor para preparar compras.'} action={!query && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}>Crear proveedor</button>} />}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? 'Nuevo proveedor' : 'Editar proveedor'} description="Información básica y logística opcional." onClose={() => setEditing(null)} size="large">{editing && <SupplierForm key={editing === 'new' ? 'new' : editing.id} supplier={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={saved} />}</Modal>
  </div>
}

function SupplierForm({ supplier, onCancel, onSaved }: { supplier: Supplier | null; onCancel: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ code: supplier?.code ?? '', legalName: supplier?.legalName ?? '', tradeName: supplier?.tradeName ?? '', taxId: supplier?.taxId ?? '', phone: supplier?.phone ?? '', email: supplier?.email ?? '', observations: '', carrier: supplier?.carrier ?? '', route: supplier?.route ?? '', active: supplier?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload: SupplierInput = { code: form.code.trim(), legalName: form.legalName.trim(), tradeName: form.tradeName.trim() || null, taxId: form.taxId.trim() || null, phone: form.phone.trim() || null, email: form.email.trim() || null, observations: form.observations.trim() || null, carrier: form.carrier.trim() || null, route: form.route.trim() || null, active: form.active }
    try { await apiFetch<Supplier>(supplier ? `/api/v1/suppliers/${supplier.id}` : '/api/v1/suppliers', { method: supplier ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label="Código" htmlFor="supplier-code" required><input id="supplier-code" value={form.code} onChange={(event) => update('code', event.target.value)} disabled={Boolean(supplier)} maxLength={40} required /></Field>
    <Field label="Razón social" htmlFor="supplier-name" required><input id="supplier-name" value={form.legalName} onChange={(event) => update('legalName', event.target.value)} maxLength={180} required /></Field>
    <Field label="Nombre comercial" htmlFor="supplier-trade"><input id="supplier-trade" value={form.tradeName} onChange={(event) => update('tradeName', event.target.value)} maxLength={180} /></Field>
    <Field label="NIF / CIF" htmlFor="supplier-tax"><input id="supplier-tax" value={form.taxId} onChange={(event) => update('taxId', event.target.value)} maxLength={30} /></Field>
    <Field label="Teléfono" htmlFor="supplier-phone"><input id="supplier-phone" value={form.phone} onChange={(event) => update('phone', event.target.value)} maxLength={40} /></Field>
    <Field label="Correo electrónico" htmlFor="supplier-email"><input id="supplier-email" type="email" value={form.email} onChange={(event) => update('email', event.target.value)} maxLength={180} /></Field>
    <Field label="Transportista" htmlFor="supplier-carrier"><input id="supplier-carrier" value={form.carrier} onChange={(event) => update('carrier', event.target.value)} maxLength={160} /></Field>
    <Field label="Ruta" htmlFor="supplier-route"><input id="supplier-route" value={form.route} onChange={(event) => update('route', event.target.value)} maxLength={160} /></Field>
    <Field label="Estado" htmlFor="supplier-active"><label className="switch-row" htmlFor="supplier-active"><input id="supplier-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>Proveedor activo</span></label></Field>
    <Field label="Observaciones" htmlFor="supplier-notes" wide><textarea id="supplier-notes" rows={3} value={form.observations} onChange={(event) => update('observations', event.target.value)} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={supplier ? 'Guardar cambios' : 'Crear proveedor'} /></form>
}
