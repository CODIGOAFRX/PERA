import { Pencil, Plus, Users } from 'lucide-react'
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
import { formatCurrency } from '../lib/format'
import type { Customer, CustomerInput, PageResponse, RiskPolicy } from '../types/api'

export function CustomersPage() {
  const [data, setData] = useState<PageResponse<Customer> | null>(null)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [debouncedQuery])
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'legalName,asc' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    apiFetch<PageResponse<Customer>>(`/api/v1/customers?${params}`)
      .then((response) => { if (active) setData(response) })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [debouncedQuery, page, refresh])

  const saved = () => {
    setEditing(null)
    setRefresh((value) => value + 1)
    notify('Cliente guardado correctamente.')
  }

  return <div className="page-stack">
    <PageHeader eyebrow="Maestros" title="Clientes" description="Información comercial, contacto y riesgo en una sola ficha." icon={Users} actions={<button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />Nuevo cliente</button>} />
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder="Buscar por nombre, código o NIF" />
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table><thead><tr><th>Código</th><th>Cliente</th><th>NIF / CIF</th><th>Contacto</th><th>Riesgo</th><th>Estado</th><th><span className="sr-only">Acciones</span></th></tr></thead><tbody>{data.content.map((customer) => <tr key={customer.id}><td><span className="code-cell">{customer.code}</span></td><td><strong>{customer.legalName}</strong>{customer.tradeName && <small>{customer.tradeName}</small>}</td><td>{customer.taxId || '—'}</td><td>{customer.email || customer.phone || '—'}</td><td>{formatCurrency(customer.creditLimit)}</td><td><StatusBadge tone={customer.active ? 'success' : 'neutral'}>{customer.active ? 'Activo' : 'Inactivo'}</StatusBadge></td><td><button className="icon-button" type="button" onClick={() => setEditing(customer)} aria-label={`Editar ${customer.legalName}`}><Pencil size={16} /></button></td></tr>)}</tbody></table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title="No hay clientes" description={query ? 'No se han encontrado coincidencias.' : 'Crea la primera ficha para empezar a vender.'} action={!query && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}>Crear cliente</button>} />}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? 'Nuevo cliente' : 'Editar cliente'} description="Datos básicos para operar y controlar el riesgo comercial." onClose={() => setEditing(null)} size="large">
      {editing && <CustomerForm key={editing === 'new' ? 'new' : editing.id} customer={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={saved} />}
    </Modal>
  </div>
}

function CustomerForm({ customer, onCancel, onSaved }: { customer: Customer | null; onCancel: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    code: customer?.code ?? '', legalName: customer?.legalName ?? '', tradeName: customer?.tradeName ?? '',
    taxId: customer?.taxId ?? '', phone: customer?.phone ?? '', email: customer?.email ?? '',
    observations: customer?.observations ?? '', creditLimit: String(customer?.creditLimit ?? 0),
    riskWarningThreshold: String(customer?.riskWarningThreshold ?? 0), riskPolicy: customer?.riskPolicy ?? 'WARN' as RiskPolicy,
    active: customer?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    const payload: CustomerInput = {
      code: form.code.trim(), legalName: form.legalName.trim(), tradeName: form.tradeName.trim() || null,
      taxId: form.taxId.trim() || null, phone: form.phone.trim() || null, email: form.email.trim() || null,
      observations: form.observations.trim() || null, creditLimit: Number(form.creditLimit || 0),
      riskWarningThreshold: Number(form.riskWarningThreshold || 0), riskPolicy: form.riskPolicy, active: form.active,
    }
    try {
      await apiFetch<Customer>(customer ? `/api/v1/customers/${customer.id}` : '/api/v1/customers', { method: customer ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      onSaved()
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSaving(false)
    }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label="Código" htmlFor="customer-code" required><input id="customer-code" value={form.code} onChange={(event) => update('code', event.target.value)} disabled={Boolean(customer)} maxLength={40} required /></Field>
    <Field label="Razón social" htmlFor="customer-name" required><input id="customer-name" value={form.legalName} onChange={(event) => update('legalName', event.target.value)} maxLength={180} required /></Field>
    <Field label="Nombre comercial" htmlFor="customer-trade"><input id="customer-trade" value={form.tradeName} onChange={(event) => update('tradeName', event.target.value)} maxLength={180} /></Field>
    <Field label="NIF / CIF" htmlFor="customer-tax"><input id="customer-tax" value={form.taxId} onChange={(event) => update('taxId', event.target.value)} maxLength={30} /></Field>
    <Field label="Teléfono" htmlFor="customer-phone"><input id="customer-phone" value={form.phone} onChange={(event) => update('phone', event.target.value)} maxLength={40} /></Field>
    <Field label="Correo electrónico" htmlFor="customer-email"><input id="customer-email" type="email" value={form.email} onChange={(event) => update('email', event.target.value)} maxLength={180} /></Field>
    <Field label="Límite de crédito" htmlFor="customer-credit"><input id="customer-credit" type="number" min="0" step="0.01" value={form.creditLimit} onChange={(event) => update('creditLimit', event.target.value)} /></Field>
    <Field label="Aviso de riesgo" htmlFor="customer-risk"><input id="customer-risk" type="number" min="0" step="0.01" value={form.riskWarningThreshold} onChange={(event) => update('riskWarningThreshold', event.target.value)} /></Field>
    <Field label="Política de riesgo" htmlFor="customer-policy"><select id="customer-policy" value={form.riskPolicy} onChange={(event) => update('riskPolicy', event.target.value)}><option value="WARN">Avisar</option><option value="REQUIRE_CONFIRMATION">Pedir confirmación</option><option value="BLOCK">Bloquear</option></select></Field>
    <Field label="Estado" htmlFor="customer-active"><label className="switch-row" htmlFor="customer-active"><input id="customer-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>Cliente activo</span></label></Field>
    <Field label="Observaciones" htmlFor="customer-notes" wide><textarea id="customer-notes" rows={3} value={form.observations} onChange={(event) => update('observations', event.target.value)} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={customer ? 'Guardar cambios' : 'Crear cliente'} /></form>
}
