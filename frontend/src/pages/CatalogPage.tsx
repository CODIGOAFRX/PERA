import { Boxes, Pencil, Plus } from 'lucide-react'
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
import { formatCurrency, unitLabel } from '../lib/format'
import type { PageResponse, Product, ProductInput, UnitOfMeasure } from '../types/api'

const units = Object.keys(unitLabel) as UnitOfMeasure[]

export function CatalogPage() {
  const [data, setData] = useState<PageResponse<Product> | null>(null)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [debouncedQuery])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'name,asc' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    apiFetch<PageResponse<Product>>(`/api/v1/products?${params}`).then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [debouncedQuery, page, refresh])

  const saved = () => { setEditing(null); setRefresh((value) => value + 1); notify('Producto guardado correctamente.') }
  return <div className="page-stack">
    <PageHeader eyebrow="Maestros" title="Catálogo" description="Artículos y servicios con precios e impuestos siempre visibles." icon={Boxes} actions={<button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />Nuevo producto</button>} />
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder="Buscar por nombre o código" />
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <><div className="table-scroll"><table><thead><tr><th>Código</th><th>Artículo / servicio</th><th>Unidad</th><th>Precio base</th><th>IVA</th><th>Estado</th><th><span className="sr-only">Acciones</span></th></tr></thead><tbody>{data.content.map((product) => <tr key={product.id}><td><span className="code-cell">{product.code}</span></td><td><strong>{product.name}</strong>{product.description && <small>{product.description}</small>}</td><td>{unitLabel[product.unitOfMeasure]}</td><td><strong>{formatCurrency(product.basePrice)}</strong></td><td>{Number(product.taxRate).toLocaleString('es-ES')} %</td><td><StatusBadge tone={product.active ? 'success' : 'neutral'}>{product.active ? 'Activo' : 'Inactivo'}</StatusBadge></td><td><button className="icon-button" type="button" onClick={() => setEditing(product)} aria-label={`Editar ${product.name}`}><Pencil size={16} /></button></td></tr>)}</tbody></table></div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title="El catálogo está vacío" description={query ? 'No se han encontrado coincidencias.' : 'Añade un artículo o servicio para utilizarlo en ventas.'} action={!query && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}>Crear producto</button>} />}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? 'Nuevo producto' : 'Editar producto'} description="Información comercial utilizada en los documentos de venta." onClose={() => setEditing(null)}>{editing && <ProductForm key={editing === 'new' ? 'new' : editing.id} product={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={saved} />}</Modal>
  </div>
}

function ProductForm({ product, onCancel, onSaved }: { product: Product | null; onCancel: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ code: product?.code ?? '', name: product?.name ?? '', description: product?.description ?? '', unitOfMeasure: product?.unitOfMeasure ?? 'UNIT' as UnitOfMeasure, basePrice: String(product?.basePrice ?? 0), taxRate: String(product?.taxRate ?? 21), active: product?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload: ProductInput = { code: form.code.trim(), name: form.name.trim(), description: form.description.trim() || null, unitOfMeasure: form.unitOfMeasure, basePrice: Number(form.basePrice), taxRate: Number(form.taxRate), active: form.active }
    try { await apiFetch<Product>(product ? `/api/v1/products/${product.id}` : '/api/v1/products', { method: product ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label="Código" htmlFor="product-code" required><input id="product-code" value={form.code} onChange={(event) => update('code', event.target.value)} disabled={Boolean(product)} maxLength={60} required /></Field>
    <Field label="Nombre" htmlFor="product-name" required><input id="product-name" value={form.name} onChange={(event) => update('name', event.target.value)} maxLength={180} required /></Field>
    <Field label="Unidad de medida" htmlFor="product-unit" required><select id="product-unit" value={form.unitOfMeasure} onChange={(event) => update('unitOfMeasure', event.target.value)}>{units.map((unit) => <option key={unit} value={unit}>{unitLabel[unit]}</option>)}</select></Field>
    <Field label="Precio base" htmlFor="product-price" required><input id="product-price" type="number" min="0" step="0.0001" value={form.basePrice} onChange={(event) => update('basePrice', event.target.value)} required /></Field>
    <Field label="IVA (%)" htmlFor="product-tax" required><input id="product-tax" type="number" min="0" max="100" step="0.01" value={form.taxRate} onChange={(event) => update('taxRate', event.target.value)} required /></Field>
    <Field label="Estado" htmlFor="product-active"><label className="switch-row" htmlFor="product-active"><input id="product-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>Producto activo</span></label></Field>
    <Field label="Descripción" htmlFor="product-description" wide><textarea id="product-description" rows={3} value={form.description} onChange={(event) => update('description', event.target.value)} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={product ? 'Guardar cambios' : 'Crear producto'} /></form>
}
