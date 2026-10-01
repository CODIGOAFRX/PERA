import { ArrowLeftRight, Boxes, ClipboardList, Pencil, Plus, SlidersHorizontal, Warehouse as WarehouseIcon } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
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
import { unitKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDateTime, formatNumber } from '../lib/format'
import type { FlatPage, PageResponse, Product, StockLevel, StockMovement, StockMovementType, UnitOfMeasure, Warehouse } from '../types/api'

type Tab = 'stock' | 'movements' | 'warehouses'
type Editor = { kind: 'adjustment'; level?: StockLevel } | { kind: 'transfer'; level?: StockLevel } | { kind: 'warehouse'; item?: Warehouse }
type InventoryRow = StockLevel | StockMovement | Warehouse

const movementTypes: StockMovementType[] = ['PURCHASE_RECEIPT', 'PURCHASE_REVERSAL', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_IN', 'TRANSFER_OUT']
const movementLabels: Record<StockMovementType, [string, string]> = {
  PURCHASE_RECEIPT: ['Entrada de compra', 'Purchase receipt'],
  PURCHASE_REVERSAL: ['Anulación de compra', 'Purchase reversal'],
  ADJUSTMENT_IN: ['Ajuste de entrada', 'Adjustment in'],
  ADJUSTMENT_OUT: ['Ajuste de salida', 'Adjustment out'],
  TRANSFER_IN: ['Traspaso recibido', 'Transfer in'],
  TRANSFER_OUT: ['Traspaso enviado', 'Transfer out'],
}
const inbound = (type: StockMovementType) => type === 'PURCHASE_RECEIPT' || type === 'ADJUSTMENT_IN' || type === 'TRANSFER_IN'

export function InventoryPage() {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [tab, setTab] = useState<Tab>('stock')
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  const [data, setData] = useState<FlatPage<InventoryRow> | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim())
  const [warehouseId, setWarehouseId] = useState('')
  const [movementType, setMovementType] = useState<StockMovementType | ''>('')
  const [onlyInStock, setOnlyInStock] = useState(true)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editor, setEditor] = useState<Editor | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  // Los almacenes alimentan los filtros y los formularios de las tres pestañas.
  useEffect(() => {
    let active = true
    apiFetch<FlatPage<Warehouse>>('/api/v1/warehouses?size=200&sort=code,asc')
      .then((response) => { if (active) setWarehouses(response.content) })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
    return () => { active = false }
  }, [refresh])

  useEffect(() => setPage(0), [tab, debouncedQuery, warehouseId, movementType, onlyInStock])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '15' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    let path: string
    if (tab === 'stock') {
      path = '/api/v1/stock-levels'
      params.set('sort', 'productCodeSnapshot,asc')
      params.set('onlyInStock', String(onlyInStock))
      if (warehouseId) params.set('warehouseId', warehouseId)
    } else if (tab === 'movements') {
      path = '/api/v1/stock-movements'
      params.set('sort', 'occurredAt,desc')
      if (warehouseId) params.set('warehouseId', warehouseId)
      if (movementType) params.set('type', movementType)
    } else {
      path = '/api/v1/warehouses'
      params.set('sort', 'code,asc')
    }
    apiFetch<FlatPage<InventoryRow>>(`${path}?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [tab, page, debouncedQuery, warehouseId, movementType, onlyInStock, refresh])

  const changeTab = (next: Tab) => { setData(null); setLoading(true); setQuery(''); setTab(next) }
  const saved = (message: string) => { setEditor(null); setRefresh((value) => value + 1); notify(message) }
  const warehouseName = (id: string) => { const warehouse = warehouses.find((item) => item.id === id); return warehouse ? warehouse.code : '—' }
  const unitLabel = (unit: string) => unit in unitKey ? t(unitKey[unit as UnitOfMeasure]) : unit
  const activeWarehouses = warehouses.filter((item) => item.active)
  const rows = data?.content ?? []
  const warehouseFilter = <select aria-label={c('Filtrar por almacén', 'Filter by warehouse')} value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)}><option value="">{c('Todos los almacenes', 'All warehouses')}</option>{warehouses.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select>

  return <div className="page-stack">
    <PageHeader eyebrow={c('Aprovisionamiento', 'Procurement')} title={c('Almacén', 'Inventory')} description={c('Existencias por almacén, diario de movimientos, ajustes y traspasos.', 'Stock by warehouse, movement journal, adjustments and transfers.')} icon={Boxes} actions={<>
      <button className="button button-secondary" type="button" disabled={activeWarehouses.length < 2} onClick={() => setEditor({ kind: 'transfer' })}><ArrowLeftRight size={17} />{c('Traspaso', 'Transfer')}</button>
      <button className="button button-secondary" type="button" disabled={activeWarehouses.length === 0} onClick={() => setEditor({ kind: 'adjustment' })}><SlidersHorizontal size={17} />{c('Ajuste', 'Adjustment')}</button>
      <button className="button button-primary" type="button" onClick={() => setEditor({ kind: 'warehouse' })}><Plus size={17} />{c('Nuevo almacén', 'New warehouse')}</button>
    </>} />

    <nav className="workspace-tabs" aria-label={c('Áreas de almacén', 'Inventory areas')}>
      <button type="button" className={tab === 'stock' ? 'active' : ''} onClick={() => changeTab('stock')}><Boxes size={15} />{c('Existencias', 'Stock')}</button>
      <button type="button" className={tab === 'movements' ? 'active' : ''} onClick={() => changeTab('movements')}><ClipboardList size={15} />{c('Diario de almacén', 'Movement journal')}</button>
      <button type="button" className={tab === 'warehouses' ? 'active' : ''} onClick={() => changeTab('warehouses')}><WarehouseIcon size={15} />{c('Almacenes', 'Warehouses')}</button>
    </nav>

    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={tab === 'warehouses' ? c('Buscar almacén', 'Search warehouse') : tab === 'movements' ? c('Buscar por producto o documento', 'Search by product or document') : c('Buscar producto', 'Search product')}>
        {tab !== 'warehouses' && warehouseFilter}
        {tab === 'stock' && <select aria-label={c('Filtrar por existencias', 'Filter by stock')} value={onlyInStock ? 'in' : 'all'} onChange={(event) => setOnlyInStock(event.target.value === 'in')}><option value="in">{c('Con existencias', 'In stock')}</option><option value="all">{c('Incluir agotados', 'Include out of stock')}</option></select>}
        {tab === 'movements' && <select aria-label={t('sales.filterType')} value={movementType} onChange={(event) => setMovementType(event.target.value as StockMovementType | '')}><option value="">{t('sales.allTypes')}</option>{movementTypes.map((item) => <option key={item} value={item}>{movementLabels[item][language === 'es' ? 0 : 1]}</option>)}</select>}
      </TableToolbar>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading || !data ? <LoadingState /> : rows.length === 0 ? <EmptyState
        title={tab === 'stock' ? c('No hay existencias', 'There is no stock') : tab === 'movements' ? c('El diario está vacío', 'The journal is empty') : c('No hay almacenes', 'There are no warehouses')}
        description={query ? t('common.noResults') : tab === 'warehouses' ? c('Crea el primer almacén para poder recibir mercancía.', 'Create the first warehouse to be able to receive goods.') : c('Las existencias entran al confirmar un albarán de compra o con un ajuste.', 'Stock comes in when a goods receipt is confirmed or through an adjustment.')}
        action={tab === 'warehouses' && !query ? <button className="button button-secondary" type="button" onClick={() => setEditor({ kind: 'warehouse' })}>{c('Crear almacén', 'Create warehouse')}</button> : undefined} /> : <>
        <div className="table-scroll">
          {tab === 'stock' && <table>
            <TableCaption es="Existencias por almacén" en="Stock by warehouse" />
            <thead><tr><th>{t('field.code')}</th><th>{t('sales.product')}</th><th>{c('Almacén', 'Warehouse')}</th><th className="align-right">{c('Existencias', 'Stock')}</th><th>{c('Unidad', 'Unit')}</th><th>{c('Último movimiento', 'Last movement')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead>
            <tbody>{(rows as StockLevel[]).map((level) => <tr key={level.id}>
              <td><span className="code-cell">{level.productCode}</span></td>
              <td><strong>{level.productName}</strong></td>
              <td>{warehouseName(level.warehouseId)}</td>
              <td className="align-right"><strong>{formatNumber(level.quantity, locale, 6)}</strong></td>
              <td>{unitLabel(level.unitOfMeasure)}</td>
              <td>{formatDateTime(level.updatedAt, locale)}</td>
              <td><button className="icon-button" type="button" onClick={() => setEditor({ kind: 'adjustment', level })} aria-label={c(`Ajustar existencias de ${level.productCode}`, `Adjust stock of ${level.productCode}`)}><SlidersHorizontal size={16} /></button>{activeWarehouses.length > 1 && <button className="icon-button" type="button" onClick={() => setEditor({ kind: 'transfer', level })} aria-label={c(`Traspasar ${level.productCode}`, `Transfer ${level.productCode}`)}><ArrowLeftRight size={16} /></button>}</td>
            </tr>)}</tbody>
          </table>}
          {tab === 'movements' && <table>
            <TableCaption es="Diario de almacén" en="Movement journal" />
            <thead><tr><th>{t('sales.date')}</th><th>{c('Movimiento', 'Movement')}</th><th>{t('sales.product')}</th><th>{c('Almacén', 'Warehouse')}</th><th className="align-right">{t('sales.quantity')}</th><th className="align-right">{c('Saldo', 'Balance')}</th><th className="align-right">{c('Coste unitario', 'Unit cost')}</th><th>{c('Origen', 'Source')}</th></tr></thead>
            <tbody>{(rows as StockMovement[]).map((movement) => <tr key={movement.id}>
              <td>{formatDateTime(movement.occurredAt, locale)}</td>
              <td><StatusBadge tone={movementTone(movement.type)}>{movementLabels[movement.type][language === 'es' ? 0 : 1]}</StatusBadge></td>
              <td><strong>{movement.productName}</strong><small>{movement.productCode}</small></td>
              <td>{warehouseName(movement.warehouseId)}</td>
              <td className="align-right"><strong>{inbound(movement.type) ? '+' : '−'}{formatNumber(movement.quantity, locale, 6)}</strong></td>
              <td className="align-right">{formatNumber(movement.balanceAfter, locale, 6)}</td>
              <td className="align-right">{movement.unitCost === null ? '—' : formatCurrency(movement.unitCost, movement.costCurrencyCode ?? 'EUR', locale)}</td>
              <td>{movement.sourceNumber || movement.note || '—'}{movement.sourceNumber && movement.note && <small>{movement.note}</small>}</td>
            </tr>)}</tbody>
          </table>}
          {tab === 'warehouses' && <table>
            <TableCaption es="Almacenes" en="Warehouses" />
            <thead><tr><th>{t('field.code')}</th><th>{c('Almacén', 'Warehouse')}</th><th>{c('Ubicación', 'Location')}</th><th>{t('field.status')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead>
            <tbody>{(rows as Warehouse[]).map((warehouse) => <tr key={warehouse.id}>
              <td><span className="code-cell">{warehouse.code}</span></td>
              <td><strong>{warehouse.name}</strong>{warehouse.defaultWarehouse && <small>{c('Predeterminado', 'Default')}</small>}</td>
              <td>{warehouse.location || '—'}</td>
              <td><StatusBadge tone={warehouse.active ? 'success' : 'neutral'}>{warehouse.active ? t('common.active') : t('common.inactive')}</StatusBadge></td>
              <td><button className="icon-button" type="button" onClick={() => setEditor({ kind: 'warehouse', item: warehouse })} aria-label={c(`Editar ${warehouse.name}`, `Edit ${warehouse.name}`)}><Pencil size={16} /></button></td>
            </tr>)}</tbody>
          </table>}
        </div>
        <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
      </>}
    </section>

    <Modal open={editor !== null} title={editor?.kind === 'adjustment' ? c('Ajuste de existencias', 'Stock adjustment') : editor?.kind === 'transfer' ? c('Traspaso entre almacenes', 'Transfer between warehouses') : editor?.item ? c('Editar almacén', 'Edit warehouse') : c('Nuevo almacén', 'New warehouse')}
      description={editor?.kind === 'adjustment' ? c('Corrige las existencias tras un recuento, una rotura o una merma. Queda anotado en el diario.', 'Correct the stock after a count, a breakage or a loss. It is recorded in the journal.') : editor?.kind === 'transfer' ? c('Mueve existencias de un almacén a otro.', 'Move stock from one warehouse to another.') : c('Lugar donde se guarda la mercancía.', 'Place where goods are kept.')}
      onClose={() => setEditor(null)}>
      {editor?.kind === 'adjustment' && <MovementForm mode="adjustment" level={editor.level} warehouses={activeWarehouses} onCancel={() => setEditor(null)} onSaved={() => saved(c('Ajuste registrado.', 'Adjustment recorded.'))} />}
      {editor?.kind === 'transfer' && <MovementForm mode="transfer" level={editor.level} warehouses={activeWarehouses} onCancel={() => setEditor(null)} onSaved={() => saved(c('Traspaso registrado.', 'Transfer recorded.'))} />}
      {editor?.kind === 'warehouse' && <WarehouseForm key={editor.item?.id ?? 'new'} item={editor.item} onCancel={() => setEditor(null)} onSaved={() => saved(c('Almacén guardado.', 'Warehouse saved.'))} />}
    </Modal>
  </div>
}

function MovementForm({ mode, level, warehouses, onCancel, onSaved }: { mode: 'adjustment' | 'transfer'; level?: StockLevel; warehouses: Warehouse[]; onCancel: () => void; onSaved: () => void }) {
  const { language, locale, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [products, setProducts] = useState<Product[]>([])
  const [loadingOptions, setLoadingOptions] = useState(!level)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()
  const defaultWarehouse = level?.warehouseId ?? warehouses.find((item) => item.defaultWarehouse)?.id ?? warehouses[0]?.id ?? ''
  const [form, setForm] = useState({ warehouseId: defaultWarehouse, targetWarehouseId: warehouses.find((item) => item.id !== defaultWarehouse)?.id ?? '', productId: level?.productId ?? '', direction: 'ADJUSTMENT_IN' as 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT', quantity: '', note: '' })

  // Desde una fila de existencias el producto ya viene dado; si no, se elige del catálogo.
  useEffect(() => {
    if (level) return
    apiFetch<PageResponse<Product>>('/api/v1/products?size=100&sort=name,asc')
      .then((response) => setProducts(response.content.filter((item) => item.active)))
      .catch((cause) => setError(errorMessage(cause))).finally(() => setLoadingOptions(false))
  }, [level])

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const product = products.find((item) => item.id === form.productId)
    const snapshot = level ? { productId: level.productId, productCode: level.productCode, productName: level.productName, unitOfMeasure: level.unitOfMeasure }
      : product ? { productId: product.id, productCode: product.code, productName: product.name, unitOfMeasure: product.unitOfMeasure } : null
    if (!snapshot) { setError(c('Selecciona un producto.', 'Select a product.')); setSaving(false); return }
    try {
      if (mode === 'adjustment') await apiFetch('/api/v1/stock-movements/adjustments', { method: 'POST', body: JSON.stringify({ warehouseId: form.warehouseId, ...snapshot, type: form.direction, quantity: Number(form.quantity), note: form.note.trim() }) })
      else await apiFetch('/api/v1/stock-movements/transfers', { method: 'POST', body: JSON.stringify({ sourceWarehouseId: form.warehouseId, targetWarehouseId: form.targetWarehouseId, ...snapshot, quantity: Number(form.quantity), note: form.note.trim() || null }) })
      onSaved()
    } catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  if (loadingOptions) return <LoadingState />
  const warehouseOptions = warehouses.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)
  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={t('sales.product')} htmlFor="movement-product" name="productId" required>{level
      ? <input id="movement-product" value={`${level.productCode} · ${level.productName}`} readOnly />
      : <select id="movement-product" value={form.productId} onChange={(event) => setForm({ ...form, productId: event.target.value })} required><option value="">{c('Selecciona un producto', 'Select a product')}</option>{products.map((product) => <option key={product.id} value={product.id}>{product.code} · {product.name}</option>)}</select>}</Field>
    <Field label={mode === 'transfer' ? c('Almacén de origen', 'Source warehouse') : c('Almacén', 'Warehouse')} htmlFor="movement-warehouse" name={mode === 'transfer' ? 'sourceWarehouseId' : 'warehouseId'} required><select id="movement-warehouse" value={form.warehouseId} onChange={(event) => setForm({ ...form, warehouseId: event.target.value })} disabled={Boolean(level)} required>{warehouseOptions}</select></Field>
    {mode === 'transfer'
      ? <Field label={c('Almacén de destino', 'Target warehouse')} htmlFor="movement-target" name="targetWarehouseId" required><select id="movement-target" value={form.targetWarehouseId} onChange={(event) => setForm({ ...form, targetWarehouseId: event.target.value })} required><option value="">{c('Selecciona el destino', 'Select the target')}</option>{warehouses.filter((item) => item.id !== form.warehouseId).map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></Field>
      : <Field label={c('Tipo de ajuste', 'Adjustment type')} htmlFor="movement-direction" name="type" required><select id="movement-direction" value={form.direction} onChange={(event) => setForm({ ...form, direction: event.target.value as 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT' })}><option value="ADJUSTMENT_IN">{c('Entrada (suma existencias)', 'In (adds stock)')}</option><option value="ADJUSTMENT_OUT">{c('Salida (resta existencias)', 'Out (removes stock)')}</option></select></Field>}
    <Field label={t('sales.quantity')} htmlFor="movement-quantity" name="quantity" required hint={level ? c(`Existencias actuales: ${formatNumber(level.quantity, locale, 6)}`, `Current stock: ${formatNumber(level.quantity, locale, 6)}`) : undefined}><input id="movement-quantity" type="number" min="0.000001" step="0.000001" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: event.target.value })} required /></Field>
    <Field label={mode === 'adjustment' ? c('Motivo', 'Reason') : t('sales.notes')} htmlFor="movement-note" name="note" required={mode === 'adjustment'} wide><textarea id="movement-note" rows={2} maxLength={500} value={form.note} onChange={(event) => setForm({ ...form, note: event.target.value })} required={mode === 'adjustment'} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={mode === 'adjustment' ? c('Registrar ajuste', 'Record adjustment') : c('Registrar traspaso', 'Record transfer')} /></FormErrors></form>
}

function WarehouseForm({ item, onCancel, onSaved }: { item?: Warehouse; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({ code: item?.code ?? '', name: item?.name ?? '', location: item?.location ?? '', defaultWarehouse: item?.defaultWarehouse ?? false, active: item?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const formErrors = useFormErrors()

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = { code: form.code.trim(), name: form.name.trim(), location: form.location.trim() || null, defaultWarehouse: form.defaultWarehouse && form.active, active: form.active }
    try { await apiFetch<Warehouse>(item ? `/api/v1/warehouses/${item.id}` : '/api/v1/warehouses', { method: item ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(formErrors.capture(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><FormErrors value={formErrors.context}><div className="form-grid">
    <Field label={t('field.code')} htmlFor="warehouse-code" name="code" required><input id="warehouse-code" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} disabled={Boolean(item)} maxLength={40} pattern="[A-Za-z0-9][A-Za-z0-9_\-]*" required /></Field>
    <Field label={c('Nombre', 'Name')} htmlFor="warehouse-name" name="name" required><input id="warehouse-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={160} required /></Field>
    <Field label={c('Ubicación', 'Location')} htmlFor="warehouse-location" name="location" wide><input id="warehouse-location" value={form.location} onChange={(event) => setForm({ ...form, location: event.target.value })} maxLength={500} /></Field>
    <Field label={c('Predeterminado', 'Default')} htmlFor="warehouse-default" name="defaultWarehouse"><label className="switch-row" htmlFor="warehouse-default"><input id="warehouse-default" type="checkbox" checked={form.defaultWarehouse && form.active} disabled={!form.active} onChange={(event) => setForm({ ...form, defaultWarehouse: event.target.checked })} /><span>{c('Se propone en compras y ajustes', 'Suggested in purchases and adjustments')}</span></label></Field>
    <Field label={t('field.status')} htmlFor="warehouse-active" name="active"><label className="switch-row" htmlFor="warehouse-active"><input id="warehouse-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{c('Almacén activo', 'Active warehouse')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? t('common.save') : c('Crear almacén', 'Create warehouse')} /></FormErrors></form>
}

function movementTone(type: StockMovementType): BadgeTone {
  if (type === 'PURCHASE_REVERSAL' || type === 'ADJUSTMENT_OUT') return 'warning'
  if (type === 'TRANSFER_IN' || type === 'TRANSFER_OUT') return 'info'
  return 'success'
}
