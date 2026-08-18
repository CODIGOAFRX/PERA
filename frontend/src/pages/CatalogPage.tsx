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
import { formatCurrency, formatNumber } from '../lib/format'
import { unitKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import type { PageResponse, Product, ProductInput, UnitOfMeasure } from '../types/api'

const units = Object.keys(unitKey) as UnitOfMeasure[]

interface ProductTypeOption {
  id: string
  code: string
  name: string
  active: boolean
}

interface ProductGroupOption extends ProductTypeOption {
  productTypeId: string
}

interface TaxCodeOption extends ProductTypeOption {
  countryCode: string
  percentage: number
  validFrom: string
  validUntil: string | null
}

export function CatalogPage() {
  const { locale, t } = useTranslation()
  const [data, setData] = useState<PageResponse<Product> | null>(null)
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [baseCurrency, setBaseCurrency] = useState('EUR')
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => setPage(0), [debouncedQuery])
  useEffect(() => {
    let active = true
    apiFetch<{ baseCurrency: string }>('/api/v1/company-settings/current')
      .then((settings) => { if (active && /^[A-Z]{3}$/i.test(settings.baseCurrency)) setBaseCurrency(settings.baseCurrency.toUpperCase()) })
      .catch(() => undefined)
    return () => { active = false }
  }, [])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '12', sort: 'name,asc' })
    if (debouncedQuery) params.set('query', debouncedQuery)
    apiFetch<PageResponse<Product>>(`/api/v1/products?${params}`).then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [debouncedQuery, page, refresh])

  const saved = () => { setEditing(null); setRefresh((value) => value + 1); notify(t('catalog.saved')) }
  return <div className="page-stack">
    <PageHeader eyebrow={t('masterData.eyebrow')} title={t('catalog.title')} description={t('catalog.description')} icon={Boxes} actions={<button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{t('catalog.new')}</button>} />
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={t('catalog.search')} />
      {error && <div className="inline-error">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <><div className="table-scroll"><table><thead><tr><th>{t('field.code')}</th><th>{t('catalog.product')}</th><th>{t('catalog.unit')}</th><th>{t('catalog.basePrice')}</th><th>{t('catalog.tax')}</th><th>{t('field.status')}</th><th><span className="sr-only">{t('common.actions')}</span></th></tr></thead><tbody>{data.content.map((product) => <tr key={product.id}><td><span className="code-cell">{product.code}</span></td><td><strong>{product.name}</strong>{product.description && <small>{product.description}</small>}</td><td>{t(unitKey[product.unitOfMeasure])}</td><td><strong>{formatCurrency(product.basePrice, baseCurrency, locale)}</strong></td><td>{formatNumber(product.taxRate, locale)} %</td><td><StatusBadge tone={product.active ? 'success' : 'neutral'}>{product.active ? t('common.active') : t('common.inactive')}</StatusBadge></td><td><button className="icon-button" type="button" onClick={() => setEditing(product)} aria-label={t('catalog.editAria', { name: product.name })}><Pencil size={16} /></button></td></tr>)}</tbody></table></div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title={t('catalog.empty')} description={query ? t('common.noResults') : t('catalog.emptyDescription')} action={!query && <button className="button button-secondary" type="button" onClick={() => setEditing('new')}>{t('catalog.create')}</button>} />}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? t('catalog.new') : t('catalog.edit')} description={t('catalog.modalDescription')} onClose={() => setEditing(null)}>{editing && <ProductForm key={editing === 'new' ? 'new' : editing.id} product={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={saved} />}</Modal>
  </div>
}

function ProductForm({ product, onCancel, onSaved }: { product: Product | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const today = new Date().toISOString().slice(0, 10)
  const [productTypes, setProductTypes] = useState<ProductTypeOption[]>([])
  const [productGroups, setProductGroups] = useState<ProductGroupOption[]>([])
  const [taxCodes, setTaxCodes] = useState<TaxCodeOption[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [form, setForm] = useState({
    code: product?.code ?? '', name: product?.name ?? '', description: product?.description ?? '',
    productTypeId: product?.productTypeId ?? '', productGroupId: product?.productGroupId ?? '',
    taxCodeId: product?.taxCodeId ?? '', unitOfMeasure: product?.unitOfMeasure ?? 'UNIT' as UnitOfMeasure,
    basePrice: String(product?.basePrice ?? 0), taxRate: String(product?.taxRate ?? 21), active: product?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  useEffect(() => {
    let active = true
    Promise.all([
      apiFetch<PageResponse<ProductTypeOption>>('/api/v1/product-types?page=0&size=200&sort=name,asc'),
      apiFetch<PageResponse<ProductGroupOption>>('/api/v1/product-groups?page=0&size=200&sort=name,asc'),
      apiFetch<PageResponse<TaxCodeOption>>('/api/v1/tax-codes?page=0&size=200&sort=name,asc'),
    ]).then(([types, groups, taxes]) => {
      if (!active) return
      setProductTypes(types.content)
      setProductGroups(groups.content)
      setTaxCodes(taxes.content)
    }).catch((cause) => {
      if (active) setError(errorMessage(cause))
    }).finally(() => {
      if (active) setLoadingOptions(false)
    })
    return () => { active = false }
  }, [])

  const selectProductType = (productTypeId: string) => setForm((current) => {
    const selectedGroup = productGroups.find((group) => group.id === current.productGroupId)
    return { ...current, productTypeId, productGroupId: selectedGroup?.productTypeId === productTypeId ? current.productGroupId : '' }
  })

  const selectProductGroup = (productGroupId: string) => setForm((current) => {
    const selectedGroup = productGroups.find((group) => group.id === productGroupId)
    return { ...current, productGroupId, productTypeId: selectedGroup?.productTypeId ?? current.productTypeId }
  })

  const selectTaxCode = (taxCodeId: string) => setForm((current) => {
    const selectedTax = taxCodes.find((taxCode) => taxCode.id === taxCodeId)
    return { ...current, taxCodeId, taxRate: selectedTax ? String(selectedTax.percentage) : current.taxRate }
  })

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload: ProductInput = {
      code: form.code.trim(), name: form.name.trim(), description: form.description.trim() || null,
      productTypeId: form.productTypeId || null, productGroupId: form.productGroupId || null,
      taxCodeId: form.taxCodeId || null, familyId: product?.familyId ?? null,
      categoryId: product?.categoryId ?? null, unitOfMeasure: form.unitOfMeasure,
      basePrice: Number(form.basePrice), taxRate: Number(form.taxRate), active: form.active,
    }
    try { await apiFetch<Product>(product ? `/api/v1/products/${product.id}` : '/api/v1/products', { method: product ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  if (loadingOptions) return <LoadingState />
  const availableTypes = productTypes.filter((item) => item.active || item.id === form.productTypeId)
  const availableGroups = productGroups.filter((item) => (item.active || item.id === form.productGroupId) && item.productTypeId === form.productTypeId)
  const availableTaxes = taxCodes.filter((item) => item.id === form.taxCodeId
    || (item.active && item.validFrom <= today && (!item.validUntil || item.validUntil >= today)))

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={t('field.code')} htmlFor="product-code" required><input id="product-code" value={form.code} onChange={(event) => update('code', event.target.value)} disabled={Boolean(product)} maxLength={60} required /></Field>
    <Field label={t('field.name')} htmlFor="product-name" required><input id="product-name" value={form.name} onChange={(event) => update('name', event.target.value)} maxLength={180} required /></Field>
    <Field label={c('Tipo de producto', 'Product type')} htmlFor="product-type"><select id="product-type" value={form.productTypeId} onChange={(event) => selectProductType(event.target.value)}><option value="">{c('Sin tipo', 'No type')}</option>{availableTypes.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></Field>
    <Field label={c('Grupo de productos', 'Product group')} htmlFor="product-group" hint={!form.productTypeId ? c('Selecciona primero un tipo.', 'Select a product type first.') : undefined}><select id="product-group" value={form.productGroupId} disabled={!form.productTypeId} onChange={(event) => selectProductGroup(event.target.value)}><option value="">{c('Sin grupo', 'No group')}</option>{availableGroups.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></Field>
    <Field label={c('Código fiscal', 'Tax code')} htmlFor="product-tax-code"><select id="product-tax-code" value={form.taxCodeId} onChange={(event) => selectTaxCode(event.target.value)}><option value="">{c('Sin código fiscal', 'No tax code')}</option>{availableTaxes.map((item) => <option key={item.id} value={item.id}>{item.countryCode} · {item.code} · {item.name} ({formatNumber(item.percentage, language === 'es' ? 'es-ES' : 'en-GB')} %)</option>)}</select></Field>
    <Field label={t('catalog.unitOfMeasure')} htmlFor="product-unit" required><select id="product-unit" value={form.unitOfMeasure} onChange={(event) => update('unitOfMeasure', event.target.value)}>{units.map((unit) => <option key={unit} value={unit}>{t(unitKey[unit])}</option>)}</select></Field>
    <Field label={t('catalog.basePrice')} htmlFor="product-price" required><input id="product-price" type="number" min="0" step="0.0001" value={form.basePrice} onChange={(event) => update('basePrice', event.target.value)} required /></Field>
    <Field label={t('catalog.taxPercentage')} htmlFor="product-tax" required hint={form.taxCodeId ? c('Se deriva del código fiscal seleccionado.', 'Derived from the selected tax code.') : undefined}><input id="product-tax" type="number" min="0" max="100" step="0.01" value={form.taxRate} disabled={Boolean(form.taxCodeId)} onChange={(event) => update('taxRate', event.target.value)} required /></Field>
    <Field label={t('field.status')} htmlFor="product-active"><label className="switch-row" htmlFor="product-active"><input id="product-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{t('catalog.active')}</span></label></Field>
    <Field label={t('field.description')} htmlFor="product-description" wide><textarea id="product-description" rows={3} value={form.description} onChange={(event) => update('description', event.target.value)} /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={product ? t('catalog.saveChanges') : t('catalog.create')} /></form>
}
