import {
  CircleDollarSign,
  Layers3,
  Package,
  Pencil,
  Plus,
  Power,
  ReceiptText,
  Settings2,
} from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'

type ConfigurationTab = 'hierarchy' | 'taxes' | 'tariffs' | 'packaging'
type ClassificationKind = 'nature' | 'supertype' | 'type' | 'group'
type PricingScope = 'GENERAL' | 'CUSTOMER' | 'PRODUCT_NATURE' | 'PRODUCT_SUPERTYPE' | 'PRODUCT_TYPE' | 'PRODUCT_GROUP' | 'PRODUCT'
type PricingTargetType = Exclude<PricingScope, 'GENERAL' | 'CUSTOMER'>

interface PageResponse<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

interface ActiveCatalogItem {
  id: string
  code: string
  name: string
  active: boolean
}

interface ProductNature extends ActiveCatalogItem {}
interface ProductSupertype extends ActiveCatalogItem { natureId: string }
interface ProductType extends ActiveCatalogItem { supertypeId: string }
interface ProductGroup extends ActiveCatalogItem { productTypeId: string }
type ClassificationEntity = ProductNature | ProductSupertype | ProductType | ProductGroup

interface TaxCode extends ActiveCatalogItem {
  countryCode: string
  percentage: number
  validFrom: string
  validUntil: string | null
  exempt: boolean
}

interface ProductLookup {
  id: string
  code: string
  name: string
  productTypeId: string | null
  productGroupId: string | null
  basePrice: number
  active: boolean
}

interface CustomerLookup {
  id: string
  code: string
  legalName: string
  active: boolean
}

interface Tariff extends ActiveCatalogItem {
  currency: string
  validFrom: string
  validUntil: string | null
  priority: number
  scope: PricingScope
  customerId: string | null
  productNatureId: string | null
  productSupertypeId: string | null
  productTypeId: string | null
  productGroupId: string | null
  productId: string | null
  parentTariffId: string | null
  generalSurchargePercentage: number | null
  energySurchargePercentage: number | null
  minimumBillingAmount: number | null
  unitMultiple: number | null
  minimumPerPiece: number | null
}

interface TariffItem {
  id: string
  tariffId: string
  productId: string
  customerId: string | null
  price: number
  discountPercentage: number
  surchargePercentage: number
  priority: number
  validFrom: string
  validUntil: string | null
  active: boolean
}

interface PricingRule {
  id: string
  tariffId: string
  targetType: PricingTargetType
  productNatureId: string | null
  productSupertypeId: string | null
  productTypeId: string | null
  productGroupId: string | null
  productId: string | null
  customerId: string | null
  fixedPrice: number | null
  discountPercentage: number
  surchargePercentage: number
  priority: number
  validFrom: string
  validUntil: string | null
  active: boolean
}

interface PricingTraceStep {
  order: number
  operation: string
  sourceId: string | null
  sourceCode: string | null
  description: string
  before: number | null
  after: number | null
}

interface PricingResolveResponse {
  tariffId: string | null
  tariffCode: string | null
  currency: string
  requestedQuantity: number
  billedQuantity: number
  baseUnitPrice: number
  finalUnitPrice: number
  subtotal: number
  finalPrice: number
  trace: PricingTraceStep[]
}

interface PackagingType extends ActiveCatalogItem {
  description: string | null
  internalLength: number | null
  internalWidth: number | null
  internalHeight: number | null
  externalLength: number | null
  externalWidth: number | null
  externalHeight: number | null
  tareWeight: number | null
  maximumWeight: number | null
  maximumVolume: number | null
  returnable: boolean
}

interface ProductPackaging {
  id: string
  productId: string
  packagingTypeId: string
  code: string | null
  unitsPerPackage: number
  levels: number | null
  unitsPerLevel: number | null
  length: number | null
  width: number | null
  height: number | null
  grossWeight: number | null
  defaultPackaging: boolean
  active: boolean
}

const localCopy = {
  es: {
    eyebrow: 'Configuración de maestros', title: 'Configuración del catálogo',
    description: 'Jerarquías, fiscalidad, precios y embalajes del catálogo de productos.',
    hierarchyTab: 'Jerarquía de producto', taxesTab: 'Impuestos', tariffsTab: 'Tarifas y reglas', packagingTab: 'Embalajes',
    nature: 'Naturaleza', natures: 'Naturalezas', supertype: 'Supertipo', supertypes: 'Supertipos',
    productType: 'Tipo', productTypes: 'Tipos', group: 'Grupo', groups: 'Grupos',
    searchCodeName: 'Buscar por código o nombre', noData: 'Todavía no hay registros.',
    noResults: 'No hay resultados para la búsqueda actual.', saved: 'Cambios guardados correctamente.',
    deactivated: 'Registro desactivado.', confirmDeactivate: '¿Quieres desactivar este registro?', deactivate: 'Desactivar',
    newRecord: 'Nuevo registro', createFirstClassification: 'Crea el primer elemento de esta clasificación.',
    code: 'Código', name: 'Nombre', parent: 'Padre', status: 'Estado', actions: 'Acciones', edit: 'Editar', create: 'Crear',
    codesLocked: 'Los códigos quedan bloqueados después del alta.', parentItem: 'Elemento padre', select: 'Selecciona…',
    active: 'Activo', saveChanges: 'Guardar cambios', noEnd: 'sin fin', yes: 'Sí', no: 'No',
    searchTax: 'Buscar por país, código o nombre', newTax: 'Nuevo impuesto', createFirstTax: 'Crea el primer código fiscal.',
    country: 'País', percentage: 'Porcentaje', validity: 'Vigencia', exempt: 'Exento', editTax: 'Editar impuesto',
    taxLocked: 'País y código quedan bloqueados tras el alta.', countryIso: 'País ISO-3166', validFrom: 'Válido desde',
    validUntil: 'Válido hasta', exemption: 'Exención', createTax: 'Crear impuesto',
    allCustomers: 'Todos los clientes', searchTariff: 'Buscar por número, código o nombre', newTariff: 'Nueva tarifa',
    allOptions: 'Todos', filterCustomer: 'Filtrar por cliente', filterNature: 'Filtrar por naturaleza',
    filterSupertype: 'Filtrar por supertipo', filterType: 'Filtrar por tipo', filterScope: 'Filtrar por ámbito',
    filterStatus: 'Filtrar por estado', filterValidity: 'Vigente en fecha', activeOnly: 'Solo activas',
    inactiveOnly: 'Solo inactivas', clearFilters: 'Limpiar filtros',
    createFirstTariff: 'Crea una tarifa para comenzar.', tariff: 'Tarifa', scope: 'Ámbito', currency: 'Moneda',
    priority: 'Prioridad', inheritsFrom: 'Hereda de', selected: 'Seleccionada', manage: 'Gestionar',
    linesOf: 'Líneas de', linesHint: 'precios y descuentos por artículo/cliente', newLine: 'Nueva línea',
    noLines: 'Sin líneas', noLinesDescription: 'La tarifa usará el precio base o sus reglas.', product: 'Producto',
    customer: 'Cliente', price: 'Precio', discount: 'Descuento', surcharge: 'Recargo', editLine: 'Editar línea',
    typedRulesOf: 'Reglas tipadas de', rulesHint: 'naturaleza, supertipo, tipo, grupo o producto', newRule: 'Nueva regla',
    noRules: 'Sin reglas', noRulesDescription: 'Añade reglas para ajustar el precio por clasificación.',
    target: 'Objetivo', item: 'Elemento', fixedPrice: 'Precio fijo', editRule: 'Editar regla',
    editTariff: 'Editar tarifa', tariffCodeLocked: 'El código queda bloqueado después del alta.',
    newTariffLine: 'Nueva línea de tarifa', editTariffLine: 'Editar línea de tarifa',
    newPricingRule: 'Nueva regla de precio', editPricingRule: 'Editar regla de precio',
    general: 'General', specificCustomer: 'Cliente específico', optionalSpecificCustomer: 'Cliente específico (opcional)',
    parentTariff: 'Tarifa padre', noInheritance: 'Sin herencia', currencyIso: 'Moneda ISO-4217',
    generalSurcharge: 'Recargo general (%)', energySurcharge: 'Recargo energético (%)',
    minimumBilling: 'Facturación mínima', unitMultiple: 'Múltiplo de unidades', minimumPerPiece: 'Mínimo por pieza',
    activeFeminine: 'Activa', createTariff: 'Crear tarifa', createLine: 'Crear línea', targetType: 'Tipo de objetivo',
    createRule: 'Crear regla',
    simulator: 'Simulador de precios', simulatorHint: 'Resuelve la tarifa efectiva y muestra una traza determinista.',
    optionalCustomer: 'Cliente (opcional)', optionalProduct: 'Producto (opcional)', optionalNature: 'Naturaleza (opcional)',
    optionalSupertype: 'Supertipo (opcional)', optionalType: 'Tipo (opcional)', optionalGroup: 'Grupo (opcional)',
    quantity: 'Cantidad', resolutionDate: 'Fecha de resolución', basePrice: 'Precio base', resolvePrice: 'Resolver precio',
    resolving: 'Resolviendo…', resolvedTariff: 'Tarifa aplicada', noTariff: 'Sin tarifa aplicable', requestedQuantity: 'Cantidad solicitada',
    billedQuantity: 'Cantidad facturada', baseUnitPrice: 'Precio unitario base', finalUnitPrice: 'Precio unitario final',
    subtotal: 'Subtotal', finalPrice: 'Precio final', trace: 'Traza de resolución', operation: 'Operación', source: 'Origen',
    descriptionLabel: 'Descripción', before: 'Antes', after: 'Después', noTrace: 'La resolución no produjo pasos de traza.',
    packagingTypes: 'Tipos de embalaje', productPackagingOptions: 'Opciones por producto', newPackagingType: 'Nuevo tipo',
    newPackagingOption: 'Nueva opción', searchPackagingType: 'Buscar tipo de embalaje',
    searchPackagingOption: 'Buscar por SKU, producto o tipo', createFirstPackagingType: 'Crea el primer tipo de embalaje.',
    associatePackaging: 'Asocia embalajes a los productos.', internalDimensions: 'Dimensiones internas',
    externalDimensions: 'Dimensiones externas', tare: 'Tara', capacity: 'Capacidad', returnable: 'Retornable', weight: 'peso',
    volume: 'volumen', sku: 'SKU', units: 'Unidades', levels: 'Niveles', dimensions: 'Dimensiones', grossWeight: 'Peso bruto',
    defaultLabel: 'Por defecto', defaultBadge: 'Predeterminado', editProductPackaging: 'Editar embalaje de producto',
    editPackagingType: 'Editar tipo de embalaje', editPackagingOption: 'Editar opción de embalaje',
    dimensionTriplets: 'Las dimensiones se informan como ternas completas.',
    packagingRefsLocked: 'Producto, tipo y código/SKU quedan bloqueados después del alta.', descriptionField: 'Descripción',
    internalLength: 'Largo interior', internalWidth: 'Ancho interior', internalHeight: 'Alto interior',
    externalLength: 'Largo exterior', externalWidth: 'Ancho exterior', externalHeight: 'Alto exterior',
    tareWeight: 'Peso tara', maximumWeight: 'Peso máximo', maximumVolume: 'Volumen máximo', returnablePackaging: 'Embalaje retornable',
    createPackagingType: 'Crear tipo', packagingType: 'Tipo de embalaje', unitsPerPackage: 'Unidades por paquete',
    unitsPerLevel: 'Unidades por nivel', length: 'Largo', width: 'Ancho', height: 'Alto', defaultPackaging: 'Embalaje por defecto',
    createPackagingOption: 'Crear opción',
  },
  en: {
    eyebrow: 'Master data configuration', title: 'Catalogue configuration',
    description: 'Product hierarchy, taxation, pricing and packaging configuration.',
    hierarchyTab: 'Product hierarchy', taxesTab: 'Taxes', tariffsTab: 'Tariffs and rules', packagingTab: 'Packaging',
    nature: 'Nature', natures: 'Natures', supertype: 'Supertype', supertypes: 'Supertypes',
    productType: 'Type', productTypes: 'Types', group: 'Group', groups: 'Groups',
    searchCodeName: 'Search by code or name', noData: 'There are no records yet.',
    noResults: 'No results match the current search.', saved: 'Changes saved successfully.',
    deactivated: 'Record deactivated.', confirmDeactivate: 'Do you want to deactivate this record?', deactivate: 'Deactivate',
    newRecord: 'New record', createFirstClassification: 'Create the first item in this classification.',
    code: 'Code', name: 'Name', parent: 'Parent', status: 'Status', actions: 'Actions', edit: 'Edit', create: 'Create',
    codesLocked: 'Codes cannot be changed after creation.', parentItem: 'Parent item', select: 'Select…',
    active: 'Active', saveChanges: 'Save changes', noEnd: 'no end date', yes: 'Yes', no: 'No',
    searchTax: 'Search by country, code or name', newTax: 'New tax', createFirstTax: 'Create the first tax code.',
    country: 'Country', percentage: 'Percentage', validity: 'Validity', exempt: 'Exempt', editTax: 'Edit tax',
    taxLocked: 'Country and code cannot be changed after creation.', countryIso: 'ISO-3166 country', validFrom: 'Valid from',
    validUntil: 'Valid until', exemption: 'Exemption', createTax: 'Create tax',
    allCustomers: 'All customers', searchTariff: 'Search by number, code or name', newTariff: 'New tariff',
    allOptions: 'All', filterCustomer: 'Filter by customer', filterNature: 'Filter by nature',
    filterSupertype: 'Filter by supertype', filterType: 'Filter by type', filterScope: 'Filter by scope',
    filterStatus: 'Filter by status', filterValidity: 'Valid on date', activeOnly: 'Active only',
    inactiveOnly: 'Inactive only', clearFilters: 'Clear filters',
    createFirstTariff: 'Create a tariff to get started.', tariff: 'Tariff', scope: 'Scope', currency: 'Currency',
    priority: 'Priority', inheritsFrom: 'Inherits from', selected: 'Selected', manage: 'Manage',
    linesOf: 'Lines for', linesHint: 'product/customer prices and discounts', newLine: 'New line',
    noLines: 'No lines', noLinesDescription: 'The tariff will use the base price or its rules.', product: 'Product',
    customer: 'Customer', price: 'Price', discount: 'Discount', surcharge: 'Surcharge', editLine: 'Edit line',
    typedRulesOf: 'Typed rules for', rulesHint: 'nature, supertype, type, group or product', newRule: 'New rule',
    noRules: 'No rules', noRulesDescription: 'Add rules to adjust prices by classification.',
    target: 'Target', item: 'Item', fixedPrice: 'Fixed price', editRule: 'Edit rule',
    editTariff: 'Edit tariff', tariffCodeLocked: 'The code cannot be changed after creation.',
    newTariffLine: 'New tariff line', editTariffLine: 'Edit tariff line',
    newPricingRule: 'New pricing rule', editPricingRule: 'Edit pricing rule',
    general: 'General', specificCustomer: 'Specific customer', optionalSpecificCustomer: 'Specific customer (optional)',
    parentTariff: 'Parent tariff', noInheritance: 'No inheritance', currencyIso: 'ISO-4217 currency',
    generalSurcharge: 'General surcharge (%)', energySurcharge: 'Energy surcharge (%)',
    minimumBilling: 'Minimum billing amount', unitMultiple: 'Unit multiple', minimumPerPiece: 'Minimum per item',
    activeFeminine: 'Active', createTariff: 'Create tariff', createLine: 'Create line', targetType: 'Target type',
    createRule: 'Create rule',
    simulator: 'Pricing simulator', simulatorHint: 'Resolve the effective tariff and inspect its deterministic trace.',
    optionalCustomer: 'Customer (optional)', optionalProduct: 'Product (optional)', optionalNature: 'Nature (optional)',
    optionalSupertype: 'Supertype (optional)', optionalType: 'Type (optional)', optionalGroup: 'Group (optional)',
    quantity: 'Quantity', resolutionDate: 'Resolution date', basePrice: 'Base price', resolvePrice: 'Resolve price',
    resolving: 'Resolving…', resolvedTariff: 'Applied tariff', noTariff: 'No applicable tariff', requestedQuantity: 'Requested quantity',
    billedQuantity: 'Billed quantity', baseUnitPrice: 'Base unit price', finalUnitPrice: 'Final unit price',
    subtotal: 'Subtotal', finalPrice: 'Final price', trace: 'Resolution trace', operation: 'Operation', source: 'Source',
    descriptionLabel: 'Description', before: 'Before', after: 'After', noTrace: 'The resolution did not produce any trace steps.',
    packagingTypes: 'Packaging types', productPackagingOptions: 'Product options', newPackagingType: 'New type',
    newPackagingOption: 'New option', searchPackagingType: 'Search packaging types',
    searchPackagingOption: 'Search by SKU, product or type', createFirstPackagingType: 'Create the first packaging type.',
    associatePackaging: 'Associate packaging options with products.', internalDimensions: 'Internal dimensions',
    externalDimensions: 'External dimensions', tare: 'Tare', capacity: 'Capacity', returnable: 'Returnable', weight: 'weight',
    volume: 'volume', sku: 'SKU', units: 'Units', levels: 'Levels', dimensions: 'Dimensions', grossWeight: 'Gross weight',
    defaultLabel: 'Default', defaultBadge: 'Default', editProductPackaging: 'Edit product packaging',
    editPackagingType: 'Edit packaging type', editPackagingOption: 'Edit packaging option',
    dimensionTriplets: 'Dimensions must be entered as complete triplets.',
    packagingRefsLocked: 'Product, type and code/SKU cannot be changed after creation.', descriptionField: 'Description',
    internalLength: 'Internal length', internalWidth: 'Internal width', internalHeight: 'Internal height',
    externalLength: 'External length', externalWidth: 'External width', externalHeight: 'External height',
    tareWeight: 'Tare weight', maximumWeight: 'Maximum weight', maximumVolume: 'Maximum volume', returnablePackaging: 'Returnable packaging',
    createPackagingType: 'Create type', packagingType: 'Packaging type', unitsPerPackage: 'Units per package',
    unitsPerLevel: 'Units per level', length: 'Length', width: 'Width', height: 'Height', defaultPackaging: 'Default packaging',
    createPackagingOption: 'Create option',
  },
} as const

function useLocalCopy() {
  const { language } = useTranslation()
  return localCopy[language]
}

type LocalCopy = ReturnType<typeof useLocalCopy>

function classificationLabel(kind: ClassificationKind, copy: LocalCopy, plural = false) {
  if (kind === 'nature') return plural ? copy.natures : copy.nature
  if (kind === 'supertype') return plural ? copy.supertypes : copy.supertype
  if (kind === 'type') return plural ? copy.productTypes : copy.productType
  return plural ? copy.groups : copy.group
}

const classificationMeta: Record<ClassificationKind, { endpoint: string }> = {
  nature: { endpoint: '/api/v1/product-natures' },
  supertype: { endpoint: '/api/v1/product-supertypes' },
  type: { endpoint: '/api/v1/product-types' },
  group: { endpoint: '/api/v1/product-groups' },
}

const today = () => new Date().toISOString().slice(0, 10)
const optionalNumber = (value: string) => value.trim() === '' ? null : Number(value)
const optionalInteger = (value: string) => value.trim() === '' ? null : Number.parseInt(value, 10)
const valueOf = (value: number | null | undefined) => value == null ? '' : String(value)
const optionalText = (value: string) => value.trim() || null
const lookupLabel = (item: { code: string; name?: string; legalName?: string }) => `${item.code} · ${item.name ?? item.legalName ?? ''}`
const matches = (query: string, ...values: Array<string | null | undefined>) => {
  const normalized = query.trim().toLocaleLowerCase()
  return !normalized || values.some((value) => value?.toLocaleLowerCase().includes(normalized))
}

function useCollection<T>(endpoint: string, refresh = 0, enabled = true) {
  const [items, setItems] = useState<T[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) {
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    const join = endpoint.includes('?') ? '&' : '?'
    apiFetch<PageResponse<T>>(`${endpoint}${join}page=0&size=200`).then((response) => {
      if (active) {
        setItems(response.content)
        setError('')
      }
    }).catch((cause) => {
      if (active) setError(errorMessage(cause))
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [endpoint, enabled, refresh])

  return { items, loading, error }
}

function useList<T>(endpoint: string, refresh = 0, enabled = true) {
  const [items, setItems] = useState<T[]>([])
  const [loading, setLoading] = useState(enabled)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) {
      setItems([])
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    apiFetch<T[]>(endpoint).then((response) => {
      if (active) {
        setItems(response)
        setError('')
      }
    }).catch((cause) => {
      if (active) setError(errorMessage(cause))
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [enabled, endpoint, refresh])

  return { items, loading, error }
}

function ActiveBadge({ active }: { active: boolean }) {
  const { t } = useTranslation()
  return <StatusBadge tone={active ? 'success' : 'neutral'}>{active ? t('common.active') : t('common.inactive')}</StatusBadge>
}

function TabButtons<T extends string>({ tabs, active, onChange }: {
  tabs: Array<{ id: T; label: string; icon?: typeof Settings2 }>
  active: T
  onChange: (tab: T) => void
}) {
  return <div className="toolbar-actions" role="tablist">{tabs.map((tab) => {
    const Icon = tab.icon
    return <button key={tab.id} type="button" role="tab" aria-selected={active === tab.id}
      className={`button ${active === tab.id ? 'button-primary' : 'button-ghost'}`}
      onClick={() => onChange(tab.id)}>{Icon && <Icon size={16} />}{tab.label}</button>
  })}</div>
}

function SectionTabs<T extends string>({ tabs, active, onChange, children }: {
  tabs: Array<{ id: T; label: string }>
  active: T
  onChange: (tab: T) => void
  children?: ReactNode
}) {
  return <section className="panel"><div className="table-toolbar">
    <TabButtons tabs={tabs} active={active} onChange={onChange} />
    {children && <div className="toolbar-actions">{children}</div>}
  </div></section>
}

function ErrorBlock({ error }: { error: string }) {
  return error ? <div className="inline-error" role="alert">{error}</div> : null
}

function RowActions({ onEdit, onDeactivate, active, editLabel }: {
  onEdit: () => void
  onDeactivate: () => void
  active: boolean
  editLabel: string
}) {
  const copy = useLocalCopy()
  return <div className="toolbar-actions">
    <button className="icon-button" type="button" onClick={onEdit} aria-label={editLabel}><Pencil size={16} /></button>
    {active && <button className="icon-button" type="button" onClick={onDeactivate} aria-label={copy.deactivate}><Power size={16} /></button>}
  </div>
}

export function CatalogConfigurationPage() {
  const [tab, setTab] = useState<ConfigurationTab>('hierarchy')
  const copy = useLocalCopy()
  const configurationTabs: Array<{ id: ConfigurationTab; label: string; icon: typeof Settings2 }> = [
    { id: 'hierarchy', label: copy.hierarchyTab, icon: Layers3 },
    { id: 'taxes', label: copy.taxesTab, icon: ReceiptText },
    { id: 'tariffs', label: copy.tariffsTab, icon: CircleDollarSign },
    { id: 'packaging', label: copy.packagingTab, icon: Package },
  ]

  return <div className="page-stack">
    <PageHeader eyebrow={copy.eyebrow} title={copy.title} description={copy.description} icon={Settings2} />
    <section className="panel"><div className="table-toolbar">
      <TabButtons tabs={configurationTabs} active={tab} onChange={setTab} />
    </div></section>
    {tab === 'hierarchy' && <HierarchyTab />}
    {tab === 'taxes' && <TaxesTab />}
    {tab === 'tariffs' && <TariffsTab />}
    {tab === 'packaging' && <PackagingTab />}
  </div>
}

function HierarchyTab() {
  const [kind, setKind] = useState<ClassificationKind>('nature')
  const [query, setQuery] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [editing, setEditing] = useState<{ kind: ClassificationKind; entity: ClassificationEntity | null } | null>(null)
  const [actionError, setActionError] = useState('')
  const copy = useLocalCopy()
  const { notify } = useToast()
  const natures = useCollection<ProductNature>(classificationMeta.nature.endpoint, refresh)
  const supertypes = useCollection<ProductSupertype>(classificationMeta.supertype.endpoint, refresh)
  const types = useCollection<ProductType>(classificationMeta.type.endpoint, refresh)
  const groups = useCollection<ProductGroup>(classificationMeta.group.endpoint, refresh)
  const resources = { nature: natures, supertype: supertypes, type: types, group: groups }
  const resource = resources[kind]
  const hierarchyError = natures.error || supertypes.error || types.error || groups.error
  const rows = useMemo(() => (resource.items as ClassificationEntity[])
    .filter((item) => matches(query, item.code, item.name)), [query, resource.items])

  const parentName = (item: ClassificationEntity) => {
    if ('natureId' in item) return natures.items.find((parent) => parent.id === item.natureId)?.name ?? item.natureId
    if ('supertypeId' in item) return supertypes.items.find((parent) => parent.id === item.supertypeId)?.name ?? item.supertypeId
    if ('productTypeId' in item) return types.items.find((parent) => parent.id === item.productTypeId)?.name ?? item.productTypeId
    return '—'
  }

  const deactivate = async (entity: ClassificationEntity) => {
    if (!window.confirm(copy.confirmDeactivate)) return
    setActionError('')
    try {
      await apiFetch(`${classificationMeta[kind].endpoint}/${entity.id}`, {
        method: 'PUT',
        body: JSON.stringify(classificationPayload(kind, entity, false)),
      })
      setRefresh((value) => value + 1)
      notify(copy.deactivated)
    } catch (cause) { setActionError(errorMessage(cause)) }
  }

  return <>
    <SectionTabs tabs={(Object.keys(classificationMeta) as ClassificationKind[]).map((id) => ({ id, label: classificationLabel(id, copy, true) }))}
      active={kind} onChange={(next) => { setKind(next); setQuery(''); setActionError('') }}>
      <button className="button button-primary" type="button" onClick={() => setEditing({ kind, entity: null })}><Plus size={16} />{copy.newRecord}</button>
    </SectionTabs>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={copy.searchCodeName} />
      <ErrorBlock error={actionError || resource.error || hierarchyError} />
      {resource.loading ? <LoadingState /> : rows.length === 0 ? <EmptyState title={copy.noData} description={query ? copy.noResults : copy.createFirstClassification} /> :
        <div className="table-scroll"><table><thead><tr><th>{copy.code}</th><th>{copy.name}</th>{kind !== 'nature' && <th>{copy.parent}</th>}<th>{copy.status}</th><th><span className="sr-only">{copy.actions}</span></th></tr></thead>
          <tbody>{rows.map((item) => <tr key={item.id}><td><span className="code-cell">{item.code}</span></td><td><strong>{item.name}</strong></td>{kind !== 'nature' && <td>{parentName(item)}</td>}<td><ActiveBadge active={item.active} /></td><td><RowActions active={item.active} editLabel={`${copy.edit} ${item.name}`} onEdit={() => setEditing({ kind, entity: item })} onDeactivate={() => deactivate(item)} /></td></tr>)}</tbody>
        </table></div>}
    </section>
    <Modal open={editing !== null} title={`${editing?.entity ? copy.edit : copy.create} ${editing ? classificationLabel(editing.kind, copy).toLocaleLowerCase() : ''}`}
      description={copy.codesLocked} onClose={() => setEditing(null)}>
      {editing && <ClassificationForm key={`${editing.kind}-${editing.entity?.id ?? 'new'}`} kind={editing.kind} entity={editing.entity}
        natures={natures.items} supertypes={supertypes.items} types={types.items} onCancel={() => setEditing(null)}
        onSaved={() => { setEditing(null); setRefresh((value) => value + 1); notify(copy.saved) }} />}
    </Modal>
  </>
}

function classificationPayload(kind: ClassificationKind, entity: ClassificationEntity, active = entity.active) {
  const base = { code: entity.code, name: entity.name, active }
  if (kind === 'supertype' && 'natureId' in entity) return { ...base, natureId: entity.natureId }
  if (kind === 'type' && 'supertypeId' in entity) return { ...base, supertypeId: entity.supertypeId }
  if (kind === 'group' && 'productTypeId' in entity) return { ...base, productTypeId: entity.productTypeId }
  return base
}

function ClassificationForm({ kind, entity, natures, supertypes, types, onCancel, onSaved }: {
  kind: ClassificationKind
  entity: ClassificationEntity | null
  natures: ProductNature[]
  supertypes: ProductSupertype[]
  types: ProductType[]
  onCancel: () => void
  onSaved: () => void
}) {
  const copy = useLocalCopy()
  const initialParent = entity && 'natureId' in entity ? entity.natureId
    : entity && 'supertypeId' in entity ? entity.supertypeId
      : entity && 'productTypeId' in entity ? entity.productTypeId : ''
  const [form, setForm] = useState({ code: entity?.code ?? '', name: entity?.name ?? '', parentId: initialParent, active: entity?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const parents = kind === 'supertype' ? natures : kind === 'type' ? supertypes : kind === 'group' ? types : []
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true); setError('')
    const payload: Record<string, unknown> = { code: form.code.trim(), name: form.name.trim(), active: form.active }
    if (kind === 'supertype') payload.natureId = form.parentId
    if (kind === 'type') payload.supertypeId = form.parentId
    if (kind === 'group') payload.productTypeId = form.parentId
    try {
      await apiFetch(entity ? `${classificationMeta[kind].endpoint}/${entity.id}` : classificationMeta[kind].endpoint,
        { method: entity ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      onSaved()
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={copy.code} htmlFor="classification-code" required><input id="classification-code" value={form.code} maxLength={40} disabled={Boolean(entity)} required onChange={(event) => update('code', event.target.value)} /></Field>
    <Field label={copy.name} htmlFor="classification-name" required><input id="classification-name" value={form.name} maxLength={140} required onChange={(event) => update('name', event.target.value)} /></Field>
    {kind !== 'nature' && <Field label={copy.parentItem} htmlFor="classification-parent" required><select id="classification-parent" value={form.parentId} required onChange={(event) => update('parentId', event.target.value)}><option value="">{copy.select}</option>{parents.filter((item) => item.active || item.id === form.parentId).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>}
    <Field label={copy.status} htmlFor="classification-active"><label className="switch-row" htmlFor="classification-active"><input id="classification-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{copy.active}</span></label></Field>
  </div><ErrorBlock error={error} /><FormActions onCancel={onCancel} saving={saving} submitLabel={entity ? copy.saveChanges : copy.create} /></form>
}

function TaxesTab() {
  const [query, setQuery] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [editing, setEditing] = useState<TaxCode | 'new' | null>(null)
  const [actionError, setActionError] = useState('')
  const copy = useLocalCopy()
  const taxes = useCollection<TaxCode>('/api/v1/tax-codes', refresh)
  const rows = useMemo(() => taxes.items.filter((item) => matches(query, item.code, item.name, item.countryCode)), [query, taxes.items])
  const { notify } = useToast()

  const deactivate = async (tax: TaxCode) => {
    if (!window.confirm(copy.confirmDeactivate)) return
    setActionError('')
    try {
      await apiFetch(`/api/v1/tax-codes/${tax.id}`, { method: 'PUT', body: JSON.stringify({
        countryCode: tax.countryCode, code: tax.code, name: tax.name, percentage: tax.percentage,
        validFrom: tax.validFrom, validUntil: tax.validUntil, exempt: tax.exempt, active: false,
      }) })
      setRefresh((value) => value + 1); notify(copy.deactivated)
    } catch (cause) { setActionError(errorMessage(cause)) }
  }

  return <>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={copy.searchTax}><button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={16} />{copy.newTax}</button></TableToolbar>
      <ErrorBlock error={actionError || taxes.error} />
      {taxes.loading ? <LoadingState /> : rows.length === 0 ? <EmptyState title={copy.noData} description={query ? copy.noResults : copy.createFirstTax} /> :
        <div className="table-scroll"><table><thead><tr><th>{copy.country}</th><th>{copy.code}</th><th>{copy.name}</th><th>{copy.percentage}</th><th>{copy.validity}</th><th>{copy.status}</th><th><span className="sr-only">{copy.actions}</span></th></tr></thead><tbody>
          {rows.map((tax) => <tr key={tax.id}><td>{tax.countryCode}</td><td><span className="code-cell">{tax.code}</span></td><td><strong>{tax.name}</strong>{tax.exempt && <small>{copy.exempt}</small>}</td><td>{tax.percentage} %</td><td>{tax.validFrom} → {tax.validUntil ?? copy.noEnd}</td><td><ActiveBadge active={tax.active} /></td><td><RowActions active={tax.active} editLabel={`${copy.edit} ${tax.name}`} onEdit={() => setEditing(tax)} onDeactivate={() => deactivate(tax)} /></td></tr>)}
        </tbody></table></div>}
    </section>
    <Modal open={editing !== null} title={editing === 'new' ? copy.newTax : copy.editTax} description={copy.taxLocked} onClose={() => setEditing(null)}>
      {editing && <TaxForm key={editing === 'new' ? 'new' : editing.id} tax={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); setRefresh((value) => value + 1); notify(copy.saved) }} />}
    </Modal>
  </>
}

function TaxForm({ tax, onCancel, onSaved }: { tax: TaxCode | null; onCancel: () => void; onSaved: () => void }) {
  const copy = useLocalCopy()
  const [form, setForm] = useState({ countryCode: tax?.countryCode ?? 'ES', code: tax?.code ?? '', name: tax?.name ?? '', percentage: String(tax?.percentage ?? 21), validFrom: tax?.validFrom ?? today(), validUntil: tax?.validUntil ?? '', exempt: tax?.exempt ?? false, active: tax?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = { countryCode: form.countryCode.trim().toUpperCase(), code: form.code.trim(), name: form.name.trim(), percentage: Number(form.percentage), validFrom: form.validFrom, validUntil: form.validUntil || null, exempt: form.exempt, active: form.active }
    try { await apiFetch(tax ? `/api/v1/tax-codes/${tax.id}` : '/api/v1/tax-codes', { method: tax ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={copy.countryIso} htmlFor="tax-country" required><input id="tax-country" value={form.countryCode} maxLength={2} disabled={Boolean(tax)} required onChange={(event) => update('countryCode', event.target.value)} /></Field>
    <Field label={copy.code} htmlFor="tax-code" required><input id="tax-code" value={form.code} maxLength={40} disabled={Boolean(tax)} required onChange={(event) => update('code', event.target.value)} /></Field>
    <Field label={copy.name} htmlFor="tax-name" required><input id="tax-name" value={form.name} maxLength={140} required onChange={(event) => update('name', event.target.value)} /></Field>
    <Field label={copy.percentage} htmlFor="tax-percentage" required><input id="tax-percentage" type="number" min="0" max="100" step="0.0001" value={form.percentage} required disabled={form.exempt} onChange={(event) => update('percentage', event.target.value)} /></Field>
    <Field label={copy.validFrom} htmlFor="tax-from" required><input id="tax-from" type="date" value={form.validFrom} required onChange={(event) => update('validFrom', event.target.value)} /></Field>
    <Field label={copy.validUntil} htmlFor="tax-until"><input id="tax-until" type="date" value={form.validUntil} min={form.validFrom} onChange={(event) => update('validUntil', event.target.value)} /></Field>
    <Field label={copy.exemption} htmlFor="tax-exempt"><label className="switch-row" htmlFor="tax-exempt"><input id="tax-exempt" type="checkbox" checked={form.exempt} onChange={(event) => setForm((current) => ({ ...current, exempt: event.target.checked, percentage: event.target.checked ? '0' : current.percentage }))} /><span>{copy.exempt}</span></label></Field>
    <Field label={copy.status} htmlFor="tax-active"><label className="switch-row" htmlFor="tax-active"><input id="tax-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{copy.active}</span></label></Field>
  </div><ErrorBlock error={error} /><FormActions onCancel={onCancel} saving={saving} submitLabel={tax ? copy.saveChanges : copy.createTax} /></form>
}

function TariffsTab() {
  const [query, setQuery] = useState('')
  const [filters, setFilters] = useState({ customerId: '', natureId: '', supertypeId: '', typeId: '', scope: '', active: '', validOn: '' })
  const [refresh, setRefresh] = useState(0)
  const [detailRefresh, setDetailRefresh] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [editingTariff, setEditingTariff] = useState<Tariff | 'new' | null>(null)
  const [editingItem, setEditingItem] = useState<TariffItem | 'new' | null>(null)
  const [editingRule, setEditingRule] = useState<PricingRule | 'new' | null>(null)
  const [actionError, setActionError] = useState('')
  const copy = useLocalCopy()
  const { notify } = useToast()
  const products = useCollection<ProductLookup>('/api/v1/products', refresh)
  const customers = useCollection<CustomerLookup>('/api/v1/customers', refresh)
  const natures = useCollection<ProductNature>('/api/v1/product-natures', refresh)
  const supertypes = useCollection<ProductSupertype>('/api/v1/product-supertypes', refresh)
  const types = useCollection<ProductType>('/api/v1/product-types', refresh)
  const groups = useCollection<ProductGroup>('/api/v1/product-groups', refresh)
  const tariffEndpoint = useMemo(() => {
    const parameters = new URLSearchParams()
    if (query.trim()) parameters.set('query', query.trim())
    Object.entries(filters).forEach(([key, value]) => { if (value) parameters.set(key, value) })
    const serialized = parameters.toString()
    return `/api/v1/tariffs${serialized ? `?${serialized}` : ''}`
  }, [filters, query])
  const tariffs = useCollection<Tariff>(tariffEndpoint, refresh)
  const tariffOptions = useCollection<Tariff>('/api/v1/tariffs', refresh)
  const selected = tariffs.items.find((tariff) => tariff.id === selectedId) ?? null
  const items = useList<TariffItem>(selectedId ? `/api/v1/tariffs/${selectedId}/items` : '', detailRefresh, Boolean(selectedId))
  const rules = useList<PricingRule>(selectedId ? `/api/v1/tariffs/${selectedId}/rules` : '', detailRefresh, Boolean(selectedId))
  const rows = tariffs.items
  const hasTariffFilters = Boolean(query.trim() || Object.values(filters).some(Boolean))
  const lookup = { products: products.items, customers: customers.items, natures: natures.items, supertypes: supertypes.items, types: types.items, groups: groups.items, tariffs: tariffOptions.items }
  const lookupError = products.error || customers.error || natures.error || supertypes.error || types.error || groups.error || tariffOptions.error

  useEffect(() => {
    if (selectedId && !tariffs.loading && !tariffs.items.some((item) => item.id === selectedId)) setSelectedId(null)
  }, [selectedId, tariffs.items, tariffs.loading])

  const productName = (id: string) => products.items.find((item) => item.id === id)?.name ?? id
  const customerName = (id: string | null) => id ? customers.items.find((item) => item.id === id)?.legalName ?? id : copy.allCustomers
  const targetName = (rule: PricingRule) => {
    const options = rule.targetType === 'PRODUCT_NATURE' ? natures.items
      : rule.targetType === 'PRODUCT_SUPERTYPE' ? supertypes.items
        : rule.targetType === 'PRODUCT_TYPE' ? types.items
          : rule.targetType === 'PRODUCT_GROUP' ? groups.items : products.items
    const id = rule.productNatureId ?? rule.productSupertypeId ?? rule.productTypeId ?? rule.productGroupId ?? rule.productId
    return options.find((item) => item.id === id)?.name ?? id ?? '—'
  }

  const deactivateTariff = async (tariff: Tariff) => {
    if (!window.confirm(copy.confirmDeactivate)) return
    setActionError('')
    try {
      await apiFetch(`/api/v1/tariffs/${tariff.id}`, { method: 'PUT', body: JSON.stringify(tariffPayload(tariff, false)) })
      setRefresh((value) => value + 1); notify(copy.deactivated)
    } catch (cause) { setActionError(errorMessage(cause)) }
  }

  const deactivateItem = async (item: TariffItem) => {
    if (!selected || !window.confirm(copy.confirmDeactivate)) return
    setActionError('')
    try {
      await apiFetch(`/api/v1/tariffs/${selected.id}/items/${item.id}`, { method: 'PUT', body: JSON.stringify({
        productId: item.productId, customerId: item.customerId, price: item.price,
        discountPercentage: item.discountPercentage, surchargePercentage: item.surchargePercentage,
        priority: item.priority, validFrom: item.validFrom, validUntil: item.validUntil, active: false,
      }) })
      setDetailRefresh((value) => value + 1); notify(copy.deactivated)
    } catch (cause) { setActionError(errorMessage(cause)) }
  }

  const deactivateRule = async (rule: PricingRule) => {
    if (!selected || !window.confirm(copy.confirmDeactivate)) return
    setActionError('')
    try {
      await apiFetch(`/api/v1/tariffs/${selected.id}/rules/${rule.id}`, { method: 'PUT', body: JSON.stringify(rulePayload(rule, false)) })
      setDetailRefresh((value) => value + 1); notify(copy.deactivated)
    } catch (cause) { setActionError(errorMessage(cause)) }
  }

  return <>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={copy.searchTariff}><button className="button button-primary" type="button" onClick={() => setEditingTariff('new')}><Plus size={16} />{copy.newTariff}</button></TableToolbar>
      <div className="form-grid">
        <Field label={copy.filterCustomer} htmlFor="tariff-filter-customer"><select id="tariff-filter-customer" value={filters.customerId} onChange={(event) => setFilters((current) => ({ ...current, customerId: event.target.value }))}><option value="">{copy.allOptions}</option>{customers.items.map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.filterNature} htmlFor="tariff-filter-nature"><select id="tariff-filter-nature" value={filters.natureId} onChange={(event) => setFilters((current) => ({ ...current, natureId: event.target.value }))}><option value="">{copy.allOptions}</option>{natures.items.map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.filterSupertype} htmlFor="tariff-filter-supertype"><select id="tariff-filter-supertype" value={filters.supertypeId} onChange={(event) => setFilters((current) => ({ ...current, supertypeId: event.target.value }))}><option value="">{copy.allOptions}</option>{supertypes.items.map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.filterType} htmlFor="tariff-filter-type"><select id="tariff-filter-type" value={filters.typeId} onChange={(event) => setFilters((current) => ({ ...current, typeId: event.target.value }))}><option value="">{copy.allOptions}</option>{types.items.map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.filterScope} htmlFor="tariff-filter-scope"><select id="tariff-filter-scope" value={filters.scope} onChange={(event) => setFilters((current) => ({ ...current, scope: event.target.value }))}><option value="">{copy.allOptions}</option>{(['GENERAL', 'CUSTOMER', 'PRODUCT_NATURE', 'PRODUCT_SUPERTYPE', 'PRODUCT_TYPE', 'PRODUCT_GROUP', 'PRODUCT'] as PricingScope[]).map((scope) => <option key={scope} value={scope}>{scopeLabel(scope, copy)}</option>)}</select></Field>
        <Field label={copy.filterStatus} htmlFor="tariff-filter-active"><select id="tariff-filter-active" value={filters.active} onChange={(event) => setFilters((current) => ({ ...current, active: event.target.value }))}><option value="">{copy.allOptions}</option><option value="true">{copy.activeOnly}</option><option value="false">{copy.inactiveOnly}</option></select></Field>
        <Field label={copy.filterValidity} htmlFor="tariff-filter-validity"><input id="tariff-filter-validity" type="date" value={filters.validOn} onChange={(event) => setFilters((current) => ({ ...current, validOn: event.target.value }))} /></Field>
        <div className="form-actions"><button className="button button-ghost" type="button" onClick={() => { setQuery(''); setFilters({ customerId: '', natureId: '', supertypeId: '', typeId: '', scope: '', active: '', validOn: '' }) }}>{copy.clearFilters}</button></div>
      </div>
      <ErrorBlock error={actionError || tariffs.error || lookupError} />
      {tariffs.loading ? <LoadingState /> : rows.length === 0 ? <EmptyState title={copy.noData} description={hasTariffFilters ? copy.noResults : copy.createFirstTariff} /> :
        <div className="table-scroll"><table><thead><tr><th>{copy.code}</th><th>{copy.tariff}</th><th>{copy.scope}</th><th>{copy.currency}</th><th>{copy.priority}</th><th>{copy.validity}</th><th>{copy.status}</th><th><span className="sr-only">{copy.actions}</span></th></tr></thead><tbody>
          {rows.map((tariff) => <tr key={tariff.id} className={selectedId === tariff.id ? 'clickable-row' : undefined}><td><span className="code-cell">{tariff.code}</span></td><td><strong>{tariff.name}</strong>{tariff.parentTariffId && <small>{copy.inheritsFrom} {tariffOptions.items.find((parent) => parent.id === tariff.parentTariffId)?.name ?? tariff.parentTariffId}</small>}</td><td>{scopeLabel(tariff.scope, copy)}</td><td>{tariff.currency}</td><td>{tariff.priority}</td><td>{tariff.validFrom} → {tariff.validUntil ?? copy.noEnd}</td><td><ActiveBadge active={tariff.active} /></td><td><div className="toolbar-actions"><button className={`button button-small ${selectedId === tariff.id ? 'button-secondary' : 'button-ghost'}`} type="button" onClick={() => setSelectedId(tariff.id)}>{selectedId === tariff.id ? copy.selected : copy.manage}</button><RowActions active={tariff.active} editLabel={`${copy.edit} ${tariff.name}`} onEdit={() => setEditingTariff(tariff)} onDeactivate={() => deactivateTariff(tariff)} /></div></td></tr>)}
        </tbody></table></div>}
    </section>
    {selected && <>
      <section className="panel table-panel">
        <div className="table-toolbar"><div><strong>{copy.linesOf} {selected.name}</strong><small> · {copy.linesHint}</small></div><button className="button button-secondary" type="button" onClick={() => setEditingItem('new')}><Plus size={16} />{copy.newLine}</button></div>
        <ErrorBlock error={items.error} />
        {items.loading ? <LoadingState /> : items.items.length === 0 ? <EmptyState title={copy.noLines} description={copy.noLinesDescription} /> : <div className="table-scroll"><table><thead><tr><th>{copy.product}</th><th>{copy.customer}</th><th>{copy.price}</th><th>{copy.discount}</th><th>{copy.surcharge}</th><th>{copy.priority}</th><th>{copy.validity}</th><th>{copy.status}</th><th><span className="sr-only">{copy.actions}</span></th></tr></thead><tbody>{items.items.map((item) => <tr key={item.id}><td>{productName(item.productId)}</td><td>{customerName(item.customerId)}</td><td>{item.price}</td><td>{item.discountPercentage} %</td><td>{item.surchargePercentage} %</td><td>{item.priority}</td><td>{item.validFrom} → {item.validUntil ?? copy.noEnd}</td><td><ActiveBadge active={item.active} /></td><td><RowActions active={item.active} editLabel={copy.editLine} onEdit={() => setEditingItem(item)} onDeactivate={() => deactivateItem(item)} /></td></tr>)}</tbody></table></div>}
      </section>
      <section className="panel table-panel">
        <div className="table-toolbar"><div><strong>{copy.typedRulesOf} {selected.name}</strong><small> · {copy.rulesHint}</small></div><button className="button button-secondary" type="button" onClick={() => setEditingRule('new')}><Plus size={16} />{copy.newRule}</button></div>
        <ErrorBlock error={rules.error} />
        {rules.loading ? <LoadingState /> : rules.items.length === 0 ? <EmptyState title={copy.noRules} description={copy.noRulesDescription} /> : <div className="table-scroll"><table><thead><tr><th>{copy.target}</th><th>{copy.item}</th><th>{copy.customer}</th><th>{copy.fixedPrice}</th><th>{copy.discount}</th><th>{copy.surcharge}</th><th>{copy.priority}</th><th>{copy.status}</th><th><span className="sr-only">{copy.actions}</span></th></tr></thead><tbody>{rules.items.map((rule) => <tr key={rule.id}><td>{scopeLabel(rule.targetType, copy)}</td><td>{targetName(rule)}</td><td>{customerName(rule.customerId)}</td><td>{rule.fixedPrice ?? '—'}</td><td>{rule.discountPercentage} %</td><td>{rule.surchargePercentage} %</td><td>{rule.priority}</td><td><ActiveBadge active={rule.active} /></td><td><RowActions active={rule.active} editLabel={copy.editRule} onEdit={() => setEditingRule(rule)} onDeactivate={() => deactivateRule(rule)} /></td></tr>)}</tbody></table></div>}
      </section>
    </>}
    <PricingSimulator lookup={lookup} />
    <Modal open={editingTariff !== null} title={editingTariff === 'new' ? copy.newTariff : copy.editTariff} description={copy.tariffCodeLocked} size="large" onClose={() => setEditingTariff(null)}>
      {editingTariff && <TariffForm key={editingTariff === 'new' ? 'new' : editingTariff.id} tariff={editingTariff === 'new' ? null : editingTariff} lookup={lookup} onCancel={() => setEditingTariff(null)} onSaved={() => { setEditingTariff(null); setRefresh((value) => value + 1); notify(copy.saved) }} />}
    </Modal>
    <Modal open={editingItem !== null} title={editingItem === 'new' ? copy.newTariffLine : copy.editTariffLine} onClose={() => setEditingItem(null)}>
      {editingItem && selected && <TariffItemForm key={editingItem === 'new' ? 'new' : editingItem.id} tariffId={selected.id} item={editingItem === 'new' ? null : editingItem} products={products.items} customers={customers.items} onCancel={() => setEditingItem(null)} onSaved={() => { setEditingItem(null); setDetailRefresh((value) => value + 1); notify(copy.saved) }} />}
    </Modal>
    <Modal open={editingRule !== null} title={editingRule === 'new' ? copy.newPricingRule : copy.editPricingRule} size="large" onClose={() => setEditingRule(null)}>
      {editingRule && selected && <PricingRuleForm key={editingRule === 'new' ? 'new' : editingRule.id} tariffId={selected.id} rule={editingRule === 'new' ? null : editingRule} lookup={lookup} onCancel={() => setEditingRule(null)} onSaved={() => { setEditingRule(null); setDetailRefresh((value) => value + 1); notify(copy.saved) }} />}
    </Modal>
  </>
}

function scopeLabel(scope: PricingScope | PricingTargetType, copy: LocalCopy) {
  return ({ GENERAL: copy.general, CUSTOMER: copy.customer, PRODUCT_NATURE: copy.nature, PRODUCT_SUPERTYPE: copy.supertype, PRODUCT_TYPE: copy.productType, PRODUCT_GROUP: copy.group, PRODUCT: copy.product } as Record<string, string>)[scope] ?? scope
}

function tariffPayload(tariff: Tariff, active = tariff.active) {
  return {
    code: tariff.code, name: tariff.name, currency: tariff.currency, validFrom: tariff.validFrom,
    validUntil: tariff.validUntil, active, priority: tariff.priority, scope: tariff.scope,
    customerId: tariff.customerId, productNatureId: tariff.productNatureId,
    productSupertypeId: tariff.productSupertypeId, productTypeId: tariff.productTypeId,
    productGroupId: tariff.productGroupId, productId: tariff.productId,
    parentTariffId: tariff.parentTariffId, generalSurchargePercentage: tariff.generalSurchargePercentage,
    energySurchargePercentage: tariff.energySurchargePercentage,
    minimumBillingAmount: tariff.minimumBillingAmount, unitMultiple: tariff.unitMultiple,
    minimumPerPiece: tariff.minimumPerPiece,
  }
}

function rulePayload(rule: PricingRule, active = rule.active) {
  return {
    targetType: rule.targetType, productNatureId: rule.productNatureId,
    productSupertypeId: rule.productSupertypeId, productTypeId: rule.productTypeId,
    productGroupId: rule.productGroupId, productId: rule.productId, customerId: rule.customerId,
    fixedPrice: rule.fixedPrice, discountPercentage: rule.discountPercentage,
    surchargePercentage: rule.surchargePercentage, priority: rule.priority,
    validFrom: rule.validFrom, validUntil: rule.validUntil, active,
  }
}

interface PricingLookups {
  products: ProductLookup[]
  customers: CustomerLookup[]
  natures: ProductNature[]
  supertypes: ProductSupertype[]
  types: ProductType[]
  groups: ProductGroup[]
  tariffs: Tariff[]
}

function pricingOperationLabel(operation: string, copy: LocalCopy) {
  const labels: Record<string, string> = {
    SELECTED_TARIFF: copy.resolvedTariff,
    INHERITED_TARIFF: copy.parentTariff,
    BASE_PRICE: copy.baseUnitPrice,
    FIXED_PRICE: copy.fixedPrice,
    DISCOUNT: copy.discount,
    RULE_SURCHARGE: copy.surcharge,
    MINIMUM_PER_PIECE: copy.minimumPerPiece,
    UNIT_MULTIPLE: copy.unitMultiple,
    GENERAL_SURCHARGE: copy.generalSurcharge,
    ENERGY_SURCHARGE: copy.energySurcharge,
    MINIMUM_BILLING: copy.minimumBilling,
  }
  return labels[operation] ?? operation
}

function PricingSimulator({ lookup }: { lookup: PricingLookups }) {
  const copy = useLocalCopy()
  const { language, locale } = useTranslation()
  const [form, setForm] = useState({
    customerId: '', productId: '', productNatureId: '', productSupertypeId: '', productTypeId: '', productGroupId: '',
    quantity: '1', date: today(), basePrice: '0', currency: 'EUR',
  })
  const [result, setResult] = useState<PricingResolveResponse | null>(null)
  const [resolving, setResolving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string) => setForm((current) => ({ ...current, [name]: value }))
  const selectProduct = (productId: string) => {
    const product = lookup.products.find((item) => item.id === productId)
    setForm((current) => ({
      ...current,
      productId,
      productNatureId: '', productSupertypeId: '', productTypeId: '', productGroupId: '',
      basePrice: product ? String(product.basePrice) : current.basePrice,
    }))
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setResolving(true); setError(''); setResult(null)
    const productSelected = Boolean(form.productId)
    const payload = {
      customerId: optionalText(form.customerId), productId: optionalText(form.productId),
      productNatureId: productSelected ? null : optionalText(form.productNatureId),
      productSupertypeId: productSelected ? null : optionalText(form.productSupertypeId),
      productTypeId: productSelected ? null : optionalText(form.productTypeId),
      productGroupId: productSelected ? null : optionalText(form.productGroupId),
      quantity: Number(form.quantity), date: form.date, basePrice: Number(form.basePrice),
      currency: form.currency.trim().toUpperCase(),
    }
    try { setResult(await apiFetch<PricingResolveResponse>('/api/v1/pricing/resolve', { method: 'POST', body: JSON.stringify(payload) })) }
    catch (cause) { setError(errorMessage(cause)) } finally { setResolving(false) }
  }
  const number = (value: number) => new Intl.NumberFormat(locale, { maximumFractionDigits: 6 }).format(value)
  const money = (value: number, currency: string) => new Intl.NumberFormat(locale, { style: 'currency', currency }).format(value)

  return <section className="panel table-panel">
    <div className="table-toolbar"><div><strong>{copy.simulator}</strong><small> · {copy.simulatorHint}</small></div></div>
    <form onSubmit={submit}>
      <div className="form-grid">
        <Field label={copy.optionalCustomer} htmlFor="pricing-customer"><select id="pricing-customer" value={form.customerId} onChange={(event) => update('customerId', event.target.value)}><option value="">{copy.allCustomers}</option>{lookup.customers.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.optionalProduct} htmlFor="pricing-product"><select id="pricing-product" value={form.productId} onChange={(event) => selectProduct(event.target.value)}><option value="">{copy.select}</option>{lookup.products.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.optionalNature} htmlFor="pricing-nature"><select id="pricing-nature" value={form.productNatureId} disabled={Boolean(form.productId)} onChange={(event) => update('productNatureId', event.target.value)}><option value="">{copy.select}</option>{lookup.natures.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.optionalSupertype} htmlFor="pricing-supertype"><select id="pricing-supertype" value={form.productSupertypeId} disabled={Boolean(form.productId)} onChange={(event) => update('productSupertypeId', event.target.value)}><option value="">{copy.select}</option>{lookup.supertypes.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.optionalType} htmlFor="pricing-type"><select id="pricing-type" value={form.productTypeId} disabled={Boolean(form.productId)} onChange={(event) => update('productTypeId', event.target.value)}><option value="">{copy.select}</option>{lookup.types.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <Field label={copy.optionalGroup} htmlFor="pricing-group"><select id="pricing-group" value={form.productGroupId} disabled={Boolean(form.productId)} onChange={(event) => update('productGroupId', event.target.value)}><option value="">{copy.select}</option>{lookup.groups.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
        <NumberField id="pricing-quantity" label={copy.quantity} value={form.quantity} onChange={(value) => update('quantity', value)} min="0.000001" step="0.000001" required />
        <Field label={copy.resolutionDate} htmlFor="pricing-date" required><input id="pricing-date" type="date" value={form.date} required onChange={(event) => update('date', event.target.value)} /></Field>
        <NumberField id="pricing-base" label={copy.basePrice} value={form.basePrice} onChange={(value) => update('basePrice', value)} min="0" required />
        <Field label={copy.currencyIso} htmlFor="pricing-currency" required><input id="pricing-currency" value={form.currency} minLength={3} maxLength={3} required onChange={(event) => update('currency', event.target.value)} /></Field>
      </div>
      <ErrorBlock error={error} />
      <div className="form-actions"><button className="button button-primary" type="submit" disabled={resolving}>{resolving ? copy.resolving : copy.resolvePrice}</button></div>
    </form>
    {result && <>
      <div className="metric-grid">
        <div className="metric-card"><span>{copy.resolvedTariff}</span><strong>{result.tariffCode ?? copy.noTariff}</strong></div>
        <div className="metric-card"><span>{copy.requestedQuantity}</span><strong>{number(result.requestedQuantity)}</strong></div>
        <div className="metric-card"><span>{copy.billedQuantity}</span><strong>{number(result.billedQuantity)}</strong></div>
        <div className="metric-card"><span>{copy.baseUnitPrice}</span><strong>{money(result.baseUnitPrice, result.currency)}</strong></div>
        <div className="metric-card"><span>{copy.finalUnitPrice}</span><strong>{money(result.finalUnitPrice, result.currency)}</strong></div>
        <div className="metric-card"><span>{copy.subtotal}</span><strong>{money(result.subtotal, result.currency)}</strong></div>
        <div className="metric-card"><span>{copy.finalPrice}</span><strong>{money(result.finalPrice, result.currency)}</strong></div>
      </div>
      <div className="table-toolbar"><strong>{copy.trace}</strong></div>
      {result.trace.length === 0 ? <EmptyState title={copy.trace} description={copy.noTrace} /> : <div className="table-scroll"><table><thead><tr><th>#</th><th>{copy.operation}</th><th>{copy.source}</th><th>{copy.descriptionLabel}</th><th>{copy.before}</th><th>{copy.after}</th></tr></thead><tbody>{result.trace.map((step) => <tr key={`${step.order}-${step.operation}`}><td>{step.order}</td><td>{pricingOperationLabel(step.operation, copy)}</td><td>{step.sourceCode ?? '—'}</td><td>{language === 'es' ? step.description : pricingOperationLabel(step.operation, copy)}</td><td>{step.before == null ? '—' : number(step.before)}</td><td>{step.after == null ? '—' : number(step.after)}</td></tr>)}</tbody></table></div>}
    </>}
  </section>
}

function TariffForm({ tariff, lookup, onCancel, onSaved }: { tariff: Tariff | null; lookup: PricingLookups; onCancel: () => void; onSaved: () => void }) {
  const copy = useLocalCopy()
  const [form, setForm] = useState({
    code: tariff?.code ?? '', name: tariff?.name ?? '', currency: tariff?.currency ?? 'EUR',
    validFrom: tariff?.validFrom ?? today(), validUntil: tariff?.validUntil ?? '', active: tariff?.active ?? true,
    priority: String(tariff?.priority ?? 0), scope: tariff?.scope ?? 'GENERAL' as PricingScope,
    customerId: tariff?.customerId ?? '', productNatureId: tariff?.productNatureId ?? '',
    productSupertypeId: tariff?.productSupertypeId ?? '', productTypeId: tariff?.productTypeId ?? '',
    productGroupId: tariff?.productGroupId ?? '', productId: tariff?.productId ?? '',
    parentTariffId: tariff?.parentTariffId ?? '', generalSurchargePercentage: valueOf(tariff?.generalSurchargePercentage),
    energySurchargePercentage: valueOf(tariff?.energySurchargePercentage), minimumBillingAmount: valueOf(tariff?.minimumBillingAmount),
    unitMultiple: valueOf(tariff?.unitMultiple), minimumPerPiece: valueOf(tariff?.minimumPerPiece),
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))
  const targetOptions: ActiveCatalogItem[] = form.scope === 'PRODUCT_NATURE' ? lookup.natures
    : form.scope === 'PRODUCT_SUPERTYPE' ? lookup.supertypes
      : form.scope === 'PRODUCT_TYPE' ? lookup.types
        : form.scope === 'PRODUCT_GROUP' ? lookup.groups
          : form.scope === 'PRODUCT' ? lookup.products : []
  const targetValue = form.scope === 'PRODUCT_NATURE' ? form.productNatureId
    : form.scope === 'PRODUCT_SUPERTYPE' ? form.productSupertypeId
      : form.scope === 'PRODUCT_TYPE' ? form.productTypeId
        : form.scope === 'PRODUCT_GROUP' ? form.productGroupId
          : form.scope === 'PRODUCT' ? form.productId : ''
  const targetField = form.scope === 'PRODUCT_NATURE' ? 'productNatureId'
    : form.scope === 'PRODUCT_SUPERTYPE' ? 'productSupertypeId'
      : form.scope === 'PRODUCT_TYPE' ? 'productTypeId'
        : form.scope === 'PRODUCT_GROUP' ? 'productGroupId' : 'productId'

  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const customerAllowed = form.scope !== 'GENERAL'
    const payload = {
      code: form.code.trim(), name: form.name.trim(), currency: form.currency.trim().toUpperCase(),
      validFrom: form.validFrom, validUntil: form.validUntil || null, active: form.active,
      priority: Number(form.priority), scope: form.scope,
      customerId: customerAllowed ? optionalText(form.customerId) : null,
      productNatureId: form.scope === 'PRODUCT_NATURE' ? optionalText(form.productNatureId) : null,
      productSupertypeId: form.scope === 'PRODUCT_SUPERTYPE' ? optionalText(form.productSupertypeId) : null,
      productTypeId: form.scope === 'PRODUCT_TYPE' ? optionalText(form.productTypeId) : null,
      productGroupId: form.scope === 'PRODUCT_GROUP' ? optionalText(form.productGroupId) : null,
      productId: form.scope === 'PRODUCT' ? optionalText(form.productId) : null,
      parentTariffId: optionalText(form.parentTariffId),
      generalSurchargePercentage: optionalNumber(form.generalSurchargePercentage),
      energySurchargePercentage: optionalNumber(form.energySurchargePercentage),
      minimumBillingAmount: optionalNumber(form.minimumBillingAmount), unitMultiple: optionalNumber(form.unitMultiple),
      minimumPerPiece: optionalNumber(form.minimumPerPiece),
    }
    try { await apiFetch(tariff ? `/api/v1/tariffs/${tariff.id}` : '/api/v1/tariffs', { method: tariff ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid">
    <Field label={copy.code} htmlFor="tariff-code" required><input id="tariff-code" value={form.code} maxLength={40} disabled={Boolean(tariff)} required onChange={(event) => update('code', event.target.value)} /></Field>
    <Field label={copy.name} htmlFor="tariff-name" required><input id="tariff-name" value={form.name} maxLength={140} required onChange={(event) => update('name', event.target.value)} /></Field>
    <Field label={copy.currencyIso} htmlFor="tariff-currency" required><input id="tariff-currency" value={form.currency} minLength={3} maxLength={3} required onChange={(event) => update('currency', event.target.value)} /></Field>
    <Field label={copy.priority} htmlFor="tariff-priority" required><input id="tariff-priority" type="number" min="0" step="1" value={form.priority} required onChange={(event) => update('priority', event.target.value)} /></Field>
    <Field label={copy.scope} htmlFor="tariff-scope" required><select id="tariff-scope" value={form.scope} onChange={(event) => update('scope', event.target.value as PricingScope)}>{(['GENERAL', 'CUSTOMER', 'PRODUCT_NATURE', 'PRODUCT_SUPERTYPE', 'PRODUCT_TYPE', 'PRODUCT_GROUP', 'PRODUCT'] as PricingScope[]).map((scope) => <option key={scope} value={scope}>{scopeLabel(scope, copy)}</option>)}</select></Field>
    {form.scope !== 'GENERAL' && <Field label={form.scope === 'CUSTOMER' ? copy.customer : copy.optionalSpecificCustomer} htmlFor="tariff-customer" required={form.scope === 'CUSTOMER'}><select id="tariff-customer" value={form.customerId} required={form.scope === 'CUSTOMER'} onChange={(event) => update('customerId', event.target.value)}><option value="">{form.scope === 'CUSTOMER' ? copy.select : copy.allCustomers}</option>{lookup.customers.filter((item) => item.active || item.id === form.customerId).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>}
    {targetOptions.length > 0 && <Field label={scopeLabel(form.scope, copy)} htmlFor="tariff-target" required><select id="tariff-target" value={targetValue} required onChange={(event) => update(targetField, event.target.value)}><option value="">{copy.select}</option>{targetOptions.filter((item) => item.active || item.id === targetValue).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>}
    <Field label={copy.parentTariff} htmlFor="tariff-parent"><select id="tariff-parent" value={form.parentTariffId} onChange={(event) => update('parentTariffId', event.target.value)}><option value="">{copy.noInheritance}</option>{lookup.tariffs.filter((item) => item.id !== tariff?.id && item.currency === form.currency.toUpperCase()).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
    <Field label={copy.validFrom} htmlFor="tariff-from" required><input id="tariff-from" type="date" value={form.validFrom} required onChange={(event) => update('validFrom', event.target.value)} /></Field>
    <Field label={copy.validUntil} htmlFor="tariff-until"><input id="tariff-until" type="date" min={form.validFrom} value={form.validUntil} onChange={(event) => update('validUntil', event.target.value)} /></Field>
    <NumberField id="tariff-general" label={copy.generalSurcharge} value={form.generalSurchargePercentage} onChange={(value) => update('generalSurchargePercentage', value)} min="0" max="100" />
    <NumberField id="tariff-energy" label={copy.energySurcharge} value={form.energySurchargePercentage} onChange={(value) => update('energySurchargePercentage', value)} min="0" max="100" />
    <NumberField id="tariff-minimum" label={copy.minimumBilling} value={form.minimumBillingAmount} onChange={(value) => update('minimumBillingAmount', value)} min="0" />
    <NumberField id="tariff-multiple" label={copy.unitMultiple} value={form.unitMultiple} onChange={(value) => update('unitMultiple', value)} min="0.000001" step="0.000001" />
    <NumberField id="tariff-piece" label={copy.minimumPerPiece} value={form.minimumPerPiece} onChange={(value) => update('minimumPerPiece', value)} min="0" />
    <Field label={copy.status} htmlFor="tariff-active"><label className="switch-row" htmlFor="tariff-active"><input id="tariff-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{copy.activeFeminine}</span></label></Field>
  </div><ErrorBlock error={error} /><FormActions onCancel={onCancel} saving={saving} submitLabel={tariff ? copy.saveChanges : copy.createTariff} /></form>
}

function TariffItemForm({ tariffId, item, products, customers, onCancel, onSaved }: { tariffId: string; item: TariffItem | null; products: ProductLookup[]; customers: CustomerLookup[]; onCancel: () => void; onSaved: () => void }) {
  const copy = useLocalCopy()
  const [form, setForm] = useState({ productId: item?.productId ?? '', customerId: item?.customerId ?? '', price: String(item?.price ?? 0), discountPercentage: String(item?.discountPercentage ?? 0), surchargePercentage: String(item?.surchargePercentage ?? 0), priority: String(item?.priority ?? 0), validFrom: item?.validFrom ?? today(), validUntil: item?.validUntil ?? '', active: item?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = { productId: form.productId, customerId: optionalText(form.customerId), price: Number(form.price), discountPercentage: Number(form.discountPercentage), surchargePercentage: Number(form.surchargePercentage), priority: Number(form.priority), validFrom: form.validFrom, validUntil: form.validUntil || null, active: form.active }
    try { await apiFetch(item ? `/api/v1/tariffs/${tariffId}/items/${item.id}` : `/api/v1/tariffs/${tariffId}/items`, { method: item ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={copy.product} htmlFor="item-product" required><select id="item-product" value={form.productId} disabled={Boolean(item)} required onChange={(event) => update('productId', event.target.value)}><option value="">{copy.select}</option>{products.filter((product) => product.active || product.id === form.productId).map((product) => <option key={product.id} value={product.id}>{lookupLabel(product)}</option>)}</select></Field>
    <Field label={copy.specificCustomer} htmlFor="item-customer"><select id="item-customer" value={form.customerId} disabled={Boolean(item)} onChange={(event) => update('customerId', event.target.value)}><option value="">{copy.allCustomers}</option>{customers.filter((customer) => customer.active || customer.id === form.customerId).map((customer) => <option key={customer.id} value={customer.id}>{lookupLabel(customer)}</option>)}</select></Field>
    <NumberField id="item-price" label={copy.price} value={form.price} onChange={(value) => update('price', value)} min="0" required />
    <NumberField id="item-discount" label={`${copy.discount} (%)`} value={form.discountPercentage} onChange={(value) => update('discountPercentage', value)} min="0" max="100" required />
    <NumberField id="item-surcharge" label={`${copy.surcharge} (%)`} value={form.surchargePercentage} onChange={(value) => update('surchargePercentage', value)} min="0" max="100" required />
    <NumberField id="item-priority" label={copy.priority} value={form.priority} onChange={(value) => update('priority', value)} min="0" step="1" required />
    <Field label={copy.validFrom} htmlFor="item-from" required><input id="item-from" type="date" value={form.validFrom} required onChange={(event) => update('validFrom', event.target.value)} /></Field>
    <Field label={copy.validUntil} htmlFor="item-until"><input id="item-until" type="date" min={form.validFrom} value={form.validUntil} onChange={(event) => update('validUntil', event.target.value)} /></Field>
    <Field label={copy.status} htmlFor="item-active"><label className="switch-row" htmlFor="item-active"><input id="item-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{copy.activeFeminine}</span></label></Field>
  </div><ErrorBlock error={error} /><FormActions onCancel={onCancel} saving={saving} submitLabel={item ? copy.saveChanges : copy.createLine} /></form>
}

function PricingRuleForm({ tariffId, rule, lookup, onCancel, onSaved }: { tariffId: string; rule: PricingRule | null; lookup: PricingLookups; onCancel: () => void; onSaved: () => void }) {
  const copy = useLocalCopy()
  const initialTarget = rule?.productNatureId ?? rule?.productSupertypeId ?? rule?.productTypeId ?? rule?.productGroupId ?? rule?.productId ?? ''
  const [form, setForm] = useState({ targetType: rule?.targetType ?? 'PRODUCT' as PricingTargetType, targetId: initialTarget, customerId: rule?.customerId ?? '', fixedPrice: valueOf(rule?.fixedPrice), discountPercentage: String(rule?.discountPercentage ?? 0), surchargePercentage: String(rule?.surchargePercentage ?? 0), priority: String(rule?.priority ?? 0), validFrom: rule?.validFrom ?? today(), validUntil: rule?.validUntil ?? '', active: rule?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))
  const options: ActiveCatalogItem[] = form.targetType === 'PRODUCT_NATURE' ? lookup.natures
    : form.targetType === 'PRODUCT_SUPERTYPE' ? lookup.supertypes
      : form.targetType === 'PRODUCT_TYPE' ? lookup.types
        : form.targetType === 'PRODUCT_GROUP' ? lookup.groups : lookup.products
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = {
      targetType: form.targetType,
      productNatureId: form.targetType === 'PRODUCT_NATURE' ? form.targetId : null,
      productSupertypeId: form.targetType === 'PRODUCT_SUPERTYPE' ? form.targetId : null,
      productTypeId: form.targetType === 'PRODUCT_TYPE' ? form.targetId : null,
      productGroupId: form.targetType === 'PRODUCT_GROUP' ? form.targetId : null,
      productId: form.targetType === 'PRODUCT' ? form.targetId : null,
      customerId: optionalText(form.customerId), fixedPrice: optionalNumber(form.fixedPrice),
      discountPercentage: Number(form.discountPercentage), surchargePercentage: Number(form.surchargePercentage),
      priority: Number(form.priority), validFrom: form.validFrom, validUntil: form.validUntil || null, active: form.active,
    }
    try { await apiFetch(rule ? `/api/v1/tariffs/${tariffId}/rules/${rule.id}` : `/api/v1/tariffs/${tariffId}/rules`, { method: rule ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={copy.targetType} htmlFor="rule-target-type" required><select id="rule-target-type" value={form.targetType} disabled={Boolean(rule)} onChange={(event) => setForm((current) => ({ ...current, targetType: event.target.value as PricingTargetType, targetId: '' }))}>{(['PRODUCT_NATURE', 'PRODUCT_SUPERTYPE', 'PRODUCT_TYPE', 'PRODUCT_GROUP', 'PRODUCT'] as PricingTargetType[]).map((target) => <option key={target} value={target}>{scopeLabel(target, copy)}</option>)}</select></Field>
    <Field label={scopeLabel(form.targetType, copy)} htmlFor="rule-target" required><select id="rule-target" value={form.targetId} disabled={Boolean(rule)} required onChange={(event) => update('targetId', event.target.value)}><option value="">{copy.select}</option>{options.filter((item) => item.active || item.id === form.targetId).map((item) => <option key={item.id} value={item.id}>{lookupLabel(item)}</option>)}</select></Field>
    <Field label={copy.specificCustomer} htmlFor="rule-customer"><select id="rule-customer" value={form.customerId} disabled={Boolean(rule)} onChange={(event) => update('customerId', event.target.value)}><option value="">{copy.allCustomers}</option>{lookup.customers.filter((customer) => customer.active || customer.id === form.customerId).map((customer) => <option key={customer.id} value={customer.id}>{lookupLabel(customer)}</option>)}</select></Field>
    <NumberField id="rule-price" label={copy.fixedPrice} value={form.fixedPrice} onChange={(value) => update('fixedPrice', value)} min="0" />
    <NumberField id="rule-discount" label={`${copy.discount} (%)`} value={form.discountPercentage} onChange={(value) => update('discountPercentage', value)} min="0" max="100" required />
    <NumberField id="rule-surcharge" label={`${copy.surcharge} (%)`} value={form.surchargePercentage} onChange={(value) => update('surchargePercentage', value)} min="0" max="100" required />
    <NumberField id="rule-priority" label={copy.priority} value={form.priority} onChange={(value) => update('priority', value)} min="0" step="1" required />
    <Field label={copy.validFrom} htmlFor="rule-from" required><input id="rule-from" type="date" value={form.validFrom} required onChange={(event) => update('validFrom', event.target.value)} /></Field>
    <Field label={copy.validUntil} htmlFor="rule-until"><input id="rule-until" type="date" min={form.validFrom} value={form.validUntil} onChange={(event) => update('validUntil', event.target.value)} /></Field>
    <Field label={copy.status} htmlFor="rule-active"><label className="switch-row" htmlFor="rule-active"><input id="rule-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{copy.activeFeminine}</span></label></Field>
  </div><ErrorBlock error={error} /><FormActions onCancel={onCancel} saving={saving} submitLabel={rule ? copy.saveChanges : copy.createRule} /></form>
}

function NumberField({ id, label, value, onChange, min, max, step = '0.0001', required = false }: { id: string; label: string; value: string; onChange: (value: string) => void; min?: string; max?: string; step?: string; required?: boolean }) {
  return <Field label={label} htmlFor={id} required={required}><input id={id} type="number" value={value} min={min} max={max} step={step} required={required} onChange={(event) => onChange(event.target.value)} /></Field>
}

function PackagingTab() {
  const [view, setView] = useState<'types' | 'options'>('types')
  const [query, setQuery] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [editingType, setEditingType] = useState<PackagingType | 'new' | null>(null)
  const [editingOption, setEditingOption] = useState<ProductPackaging | 'new' | null>(null)
  const [actionError, setActionError] = useState('')
  const copy = useLocalCopy()
  const { notify } = useToast()
  const packagingTypes = useCollection<PackagingType>('/api/v1/packaging-types', refresh)
  const options = useCollection<ProductPackaging>('/api/v1/product-packaging', refresh)
  const products = useCollection<ProductLookup>('/api/v1/products', refresh)
  const rows = useMemo(() => view === 'types'
    ? packagingTypes.items.filter((item) => matches(query, item.code, item.name, item.description))
    : options.items.filter((item) => matches(query, item.code, products.items.find((product) => product.id === item.productId)?.name, packagingTypes.items.find((type) => type.id === item.packagingTypeId)?.name)),
  [packagingTypes.items, options.items, products.items, query, view])
  const resource = view === 'types' ? packagingTypes : options

  const deactivateType = async (type: PackagingType) => {
    if (!window.confirm(copy.confirmDeactivate)) return
    setActionError('')
    try {
      await apiFetch(`/api/v1/packaging-types/${type.id}`, { method: 'PUT', body: JSON.stringify(packagingTypePayload(type, false)) })
      setRefresh((value) => value + 1); notify(copy.deactivated)
    } catch (cause) { setActionError(errorMessage(cause)) }
  }

  const deactivateOption = async (option: ProductPackaging) => {
    if (!window.confirm(copy.confirmDeactivate)) return
    setActionError('')
    try {
      await apiFetch(`/api/v1/product-packaging/${option.id}`, { method: 'PUT', body: JSON.stringify({
        productId: option.productId, packagingTypeId: option.packagingTypeId, code: option.code,
        unitsPerPackage: option.unitsPerPackage, levels: option.levels, unitsPerLevel: option.unitsPerLevel,
        length: option.length, width: option.width, height: option.height, grossWeight: option.grossWeight,
        defaultPackaging: false, active: false,
      }) })
      setRefresh((value) => value + 1); notify(copy.deactivated)
    } catch (cause) { setActionError(errorMessage(cause)) }
  }

  return <>
    <SectionTabs tabs={[{ id: 'types' as const, label: copy.packagingTypes }, { id: 'options' as const, label: copy.productPackagingOptions }]} active={view} onChange={(next) => { setView(next); setQuery(''); setActionError('') }}>
      <button className="button button-primary" type="button" onClick={() => view === 'types' ? setEditingType('new') : setEditingOption('new')}><Plus size={16} />{view === 'types' ? copy.newPackagingType : copy.newPackagingOption}</button>
    </SectionTabs>
    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={view === 'types' ? copy.searchPackagingType : copy.searchPackagingOption} />
      <ErrorBlock error={actionError || resource.error || products.error || packagingTypes.error} />
      {resource.loading ? <LoadingState /> : rows.length === 0 ? <EmptyState title={copy.noData} description={query ? copy.noResults : view === 'types' ? copy.createFirstPackagingType : copy.associatePackaging} /> : view === 'types' ?
        <div className="table-scroll"><table><thead><tr><th>{copy.code}</th><th>{copy.packagingType}</th><th>{copy.internalDimensions}</th><th>{copy.externalDimensions}</th><th>{copy.tare}</th><th>{copy.capacity}</th><th>{copy.returnable}</th><th>{copy.status}</th><th><span className="sr-only">{copy.actions}</span></th></tr></thead><tbody>{(rows as PackagingType[]).map((type) => <tr key={type.id}><td><span className="code-cell">{type.code}</span></td><td><strong>{type.name}</strong>{type.description && <small>{type.description}</small>}</td><td>{dimensions(type.internalLength, type.internalWidth, type.internalHeight)}</td><td>{dimensions(type.externalLength, type.externalWidth, type.externalHeight)}</td><td>{type.tareWeight ?? '—'}</td><td>{type.maximumWeight != null ? `${type.maximumWeight} ${copy.weight}` : '—'}{type.maximumVolume != null && <small>{type.maximumVolume} {copy.volume}</small>}</td><td>{type.returnable ? copy.yes : copy.no}</td><td><ActiveBadge active={type.active} /></td><td><RowActions active={type.active} editLabel={`${copy.edit} ${type.name}`} onEdit={() => setEditingType(type)} onDeactivate={() => deactivateType(type)} /></td></tr>)}</tbody></table></div> :
        <div className="table-scroll"><table><thead><tr><th>{copy.sku}</th><th>{copy.product}</th><th>{copy.packagingType}</th><th>{copy.units}</th><th>{copy.levels}</th><th>{copy.dimensions}</th><th>{copy.grossWeight}</th><th>{copy.defaultLabel}</th><th>{copy.status}</th><th><span className="sr-only">{copy.actions}</span></th></tr></thead><tbody>{(rows as ProductPackaging[]).map((option) => <tr key={option.id}><td>{option.code ? <span className="code-cell">{option.code}</span> : '—'}</td><td><strong>{products.items.find((product) => product.id === option.productId)?.name ?? option.productId}</strong></td><td>{packagingTypes.items.find((type) => type.id === option.packagingTypeId)?.name ?? option.packagingTypeId}</td><td>{option.unitsPerPackage}</td><td>{option.levels == null ? '—' : `${option.levels} × ${option.unitsPerLevel}`}</td><td>{dimensions(option.length, option.width, option.height)}</td><td>{option.grossWeight ?? '—'}</td><td>{option.defaultPackaging ? <StatusBadge tone="info">{copy.defaultBadge}</StatusBadge> : '—'}</td><td><ActiveBadge active={option.active} /></td><td><RowActions active={option.active} editLabel={copy.editProductPackaging} onEdit={() => setEditingOption(option)} onDeactivate={() => deactivateOption(option)} /></td></tr>)}</tbody></table></div>}
    </section>
    <Modal open={editingType !== null} title={editingType === 'new' ? copy.newPackagingType : copy.editPackagingType} description={copy.dimensionTriplets} size="large" onClose={() => setEditingType(null)}>
      {editingType && <PackagingTypeForm key={editingType === 'new' ? 'new' : editingType.id} packagingType={editingType === 'new' ? null : editingType} onCancel={() => setEditingType(null)} onSaved={() => { setEditingType(null); setRefresh((value) => value + 1); notify(copy.saved) }} />}
    </Modal>
    <Modal open={editingOption !== null} title={editingOption === 'new' ? copy.newPackagingOption : copy.editPackagingOption} description={copy.packagingRefsLocked} size="large" onClose={() => setEditingOption(null)}>
      {editingOption && <ProductPackagingForm key={editingOption === 'new' ? 'new' : editingOption.id} option={editingOption === 'new' ? null : editingOption} products={products.items} packagingTypes={packagingTypes.items} onCancel={() => setEditingOption(null)} onSaved={() => { setEditingOption(null); setRefresh((value) => value + 1); notify(copy.saved) }} />}
    </Modal>
  </>
}

function dimensions(length: number | null, width: number | null, height: number | null) {
  return length == null || width == null || height == null ? '—' : `${length} × ${width} × ${height}`
}

function packagingTypePayload(type: PackagingType, active = type.active) {
  return {
    code: type.code, name: type.name, description: type.description,
    internalLength: type.internalLength, internalWidth: type.internalWidth, internalHeight: type.internalHeight,
    externalLength: type.externalLength, externalWidth: type.externalWidth, externalHeight: type.externalHeight,
    tareWeight: type.tareWeight, maximumWeight: type.maximumWeight, maximumVolume: type.maximumVolume,
    returnable: type.returnable, active,
  }
}

function PackagingTypeForm({ packagingType, onCancel, onSaved }: { packagingType: PackagingType | null; onCancel: () => void; onSaved: () => void }) {
  const copy = useLocalCopy()
  const [form, setForm] = useState({
    code: packagingType?.code ?? '', name: packagingType?.name ?? '', description: packagingType?.description ?? '',
    internalLength: valueOf(packagingType?.internalLength), internalWidth: valueOf(packagingType?.internalWidth), internalHeight: valueOf(packagingType?.internalHeight),
    externalLength: valueOf(packagingType?.externalLength), externalWidth: valueOf(packagingType?.externalWidth), externalHeight: valueOf(packagingType?.externalHeight),
    tareWeight: valueOf(packagingType?.tareWeight), maximumWeight: valueOf(packagingType?.maximumWeight), maximumVolume: valueOf(packagingType?.maximumVolume),
    returnable: packagingType?.returnable ?? false, active: packagingType?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = {
      code: form.code.trim(), name: form.name.trim(), description: optionalText(form.description),
      internalLength: optionalNumber(form.internalLength), internalWidth: optionalNumber(form.internalWidth), internalHeight: optionalNumber(form.internalHeight),
      externalLength: optionalNumber(form.externalLength), externalWidth: optionalNumber(form.externalWidth), externalHeight: optionalNumber(form.externalHeight),
      tareWeight: optionalNumber(form.tareWeight), maximumWeight: optionalNumber(form.maximumWeight), maximumVolume: optionalNumber(form.maximumVolume),
      returnable: form.returnable, active: form.active,
    }
    try { await apiFetch(packagingType ? `/api/v1/packaging-types/${packagingType.id}` : '/api/v1/packaging-types', { method: packagingType ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={copy.code} htmlFor="pack-type-code" required><input id="pack-type-code" value={form.code} maxLength={40} disabled={Boolean(packagingType)} required onChange={(event) => update('code', event.target.value)} /></Field>
    <Field label={copy.name} htmlFor="pack-type-name" required><input id="pack-type-name" value={form.name} maxLength={140} required onChange={(event) => update('name', event.target.value)} /></Field>
    <Field label={copy.descriptionField} htmlFor="pack-type-description" wide><textarea id="pack-type-description" rows={3} maxLength={4000} value={form.description} onChange={(event) => update('description', event.target.value)} /></Field>
    <NumberField id="pack-in-length" label={copy.internalLength} value={form.internalLength} onChange={(value) => update('internalLength', value)} min="0.0001" />
    <NumberField id="pack-in-width" label={copy.internalWidth} value={form.internalWidth} onChange={(value) => update('internalWidth', value)} min="0.0001" />
    <NumberField id="pack-in-height" label={copy.internalHeight} value={form.internalHeight} onChange={(value) => update('internalHeight', value)} min="0.0001" />
    <NumberField id="pack-out-length" label={copy.externalLength} value={form.externalLength} onChange={(value) => update('externalLength', value)} min="0.0001" />
    <NumberField id="pack-out-width" label={copy.externalWidth} value={form.externalWidth} onChange={(value) => update('externalWidth', value)} min="0.0001" />
    <NumberField id="pack-out-height" label={copy.externalHeight} value={form.externalHeight} onChange={(value) => update('externalHeight', value)} min="0.0001" />
    <NumberField id="pack-tare" label={copy.tareWeight} value={form.tareWeight} onChange={(value) => update('tareWeight', value)} min="0.0001" />
    <NumberField id="pack-max-weight" label={copy.maximumWeight} value={form.maximumWeight} onChange={(value) => update('maximumWeight', value)} min="0.0001" />
    <NumberField id="pack-max-volume" label={copy.maximumVolume} value={form.maximumVolume} onChange={(value) => update('maximumVolume', value)} min="0.000001" step="0.000001" />
    <Field label={copy.returnable} htmlFor="pack-returnable"><label className="switch-row" htmlFor="pack-returnable"><input id="pack-returnable" type="checkbox" checked={form.returnable} onChange={(event) => update('returnable', event.target.checked)} /><span>{copy.returnablePackaging}</span></label></Field>
    <Field label={copy.status} htmlFor="pack-type-active"><label className="switch-row" htmlFor="pack-type-active"><input id="pack-type-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{copy.active}</span></label></Field>
  </div><ErrorBlock error={error} /><FormActions onCancel={onCancel} saving={saving} submitLabel={packagingType ? copy.saveChanges : copy.createPackagingType} /></form>
}

function ProductPackagingForm({ option, products, packagingTypes, onCancel, onSaved }: { option: ProductPackaging | null; products: ProductLookup[]; packagingTypes: PackagingType[]; onCancel: () => void; onSaved: () => void }) {
  const copy = useLocalCopy()
  const [form, setForm] = useState({
    productId: option?.productId ?? '', packagingTypeId: option?.packagingTypeId ?? '', code: option?.code ?? '',
    unitsPerPackage: String(option?.unitsPerPackage ?? 1), levels: valueOf(option?.levels), unitsPerLevel: valueOf(option?.unitsPerLevel),
    length: valueOf(option?.length), width: valueOf(option?.width), height: valueOf(option?.height), grossWeight: valueOf(option?.grossWeight),
    defaultPackaging: option?.defaultPackaging ?? false, active: option?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const payload = {
      productId: form.productId, packagingTypeId: form.packagingTypeId, code: optionalText(form.code),
      unitsPerPackage: Number(form.unitsPerPackage), levels: optionalInteger(form.levels), unitsPerLevel: optionalNumber(form.unitsPerLevel),
      length: optionalNumber(form.length), width: optionalNumber(form.width), height: optionalNumber(form.height), grossWeight: optionalNumber(form.grossWeight),
      defaultPackaging: form.defaultPackaging, active: form.active,
    }
    try { await apiFetch(option ? `/api/v1/product-packaging/${option.id}` : '/api/v1/product-packaging', { method: option ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={copy.product} htmlFor="product-pack-product" required><select id="product-pack-product" value={form.productId} disabled={Boolean(option)} required onChange={(event) => update('productId', event.target.value)}><option value="">{copy.select}</option>{products.filter((product) => product.active || product.id === form.productId).map((product) => <option key={product.id} value={product.id}>{lookupLabel(product)}</option>)}</select></Field>
    <Field label={copy.packagingType} htmlFor="product-pack-type" required><select id="product-pack-type" value={form.packagingTypeId} disabled={Boolean(option)} required onChange={(event) => update('packagingTypeId', event.target.value)}><option value="">{copy.select}</option>{packagingTypes.filter((type) => type.active || type.id === form.packagingTypeId).map((type) => <option key={type.id} value={type.id}>{lookupLabel(type)}</option>)}</select></Field>
    <Field label={`${copy.code}/SKU`} htmlFor="product-pack-code"><input id="product-pack-code" value={form.code} maxLength={80} disabled={Boolean(option)} onChange={(event) => update('code', event.target.value)} /></Field>
    <NumberField id="product-pack-units" label={copy.unitsPerPackage} value={form.unitsPerPackage} onChange={(value) => update('unitsPerPackage', value)} min="0.000001" step="0.000001" required />
    <NumberField id="product-pack-levels" label={copy.levels} value={form.levels} onChange={(value) => update('levels', value)} min="1" step="1" />
    <NumberField id="product-pack-level-units" label={copy.unitsPerLevel} value={form.unitsPerLevel} onChange={(value) => update('unitsPerLevel', value)} min="0.000001" step="0.000001" />
    <NumberField id="product-pack-length" label={copy.length} value={form.length} onChange={(value) => update('length', value)} min="0.0001" />
    <NumberField id="product-pack-width" label={copy.width} value={form.width} onChange={(value) => update('width', value)} min="0.0001" />
    <NumberField id="product-pack-height" label={copy.height} value={form.height} onChange={(value) => update('height', value)} min="0.0001" />
    <NumberField id="product-pack-gross" label={copy.grossWeight} value={form.grossWeight} onChange={(value) => update('grossWeight', value)} min="0.0001" />
    <Field label={copy.defaultBadge} htmlFor="product-pack-default"><label className="switch-row" htmlFor="product-pack-default"><input id="product-pack-default" type="checkbox" checked={form.defaultPackaging} disabled={!form.active} onChange={(event) => update('defaultPackaging', event.target.checked)} /><span>{copy.defaultPackaging}</span></label></Field>
    <Field label={copy.status} htmlFor="product-pack-active"><label className="switch-row" htmlFor="product-pack-active"><input id="product-pack-active" type="checkbox" checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked, defaultPackaging: event.target.checked ? current.defaultPackaging : false }))} /><span>{copy.active}</span></label></Field>
  </div><ErrorBlock error={error} /><FormActions onCancel={onCancel} saving={saving} submitLabel={option ? copy.saveChanges : copy.createPackagingOption} /></form>
}
