import {
  AlertTriangle,
  Archive,
  Boxes,
  CheckCircle2,
  ClipboardList,
  Copy,
  Download,
  FilePlus2,
  PackageCheck,
  Pencil,
  Play,
  Plus,
  RefreshCw,
  Route as RouteIcon,
  Send,
  SkipForward,
  Trash2,
  Truck,
  XCircle,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge, type BadgeTone } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { apiDownload, apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDateTime, formatNumber } from '../lib/format'

type Tab = 'templates' | 'executions' | 'carriers' | 'vehicles' | 'routes' | 'freight' | 'shipments'
type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED'
type ExecutionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
type StepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'CANCELLED'
type ShipmentStatus = 'PLANNED' | 'PACKING' | 'READY' | 'DISPATCHED' | 'IN_TRANSIT' | 'ARRIVED' | 'DELIVERED' | 'EXCEPTION' | 'CANCELLED'
type CarrierOwnership = 'OWN' | 'THIRD_PARTY'
type NumericValue = number | string
type FreightCalculationMethod = 'FIXED' | 'PER_KG' | 'PER_M3' | 'PER_KM' | 'FIXED_PLUS_PER_KG' | 'FIXED_PLUS_PER_M3' | 'FIXED_PLUS_PER_KM'

interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface WorkflowStep {
  id: string
  code: string
  name: string
  description: string | null
  sequence: number
  required: boolean
  estimatedMinutes: number | null
}

interface WorkflowTemplate {
  id: string
  code: string
  name: string
  referenceType: string
  status: TemplateStatus
  templateVersion: number
  steps: WorkflowStep[]
  createdAt: string
  updatedAt: string
}

interface WorkStep extends WorkflowStep {
  status: StepStatus
  startedAt: string | null
  finishedAt: string | null
  note: string | null
}

interface WorkExecution {
  id: string
  templateId: string
  templateCode: string
  templateName: string
  templateVersion: number
  referenceType: string
  referenceId: string
  status: ExecutionStatus
  startedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  steps: WorkStep[]
}

interface Carrier {
  id: string
  code: string
  name: string
  ownership: CarrierOwnership
  taxIdentifier: string | null
  externalIdentifier: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

interface Vehicle {
  id: string
  code: string
  registrationPlate: string | null
  vehicleType: string
  carrierId: string | null
  capacityWeightKg: NumericValue | null
  capacityVolumeM3: NumericValue | null
  active: boolean
  createdAt: string
  updatedAt: string
}

interface RouteStop {
  id: string
  sequence: number
  name: string
  location: string
  windowStart: string | null
  windowEnd: string | null
  instructions: string | null
}

interface DeliveryRoute {
  id: string
  code: string
  name: string
  origin: string
  destination: string
  carrierId: string | null
  vehicleId: string | null
  plannedDepartureAt: string | null
  plannedArrivalAt: string | null
  deliveryWindowStart: string | null
  deliveryWindowEnd: string | null
  distanceKm: NumericValue | null
  estimatedDurationMinutes: number | null
  active: boolean
  stops: RouteStop[]
  createdAt: string
  updatedAt: string
}

interface ShipmentLine {
  id: string
  sequence: number
  productId: string | null
  productCodeSnapshot: string | null
  productNameSnapshot: string
  quantity: NumericValue
  unitOfMeasureSnapshot: string
  sourceDocumentId: string | null
  sourceDocumentType: string | null
  sourceDocumentNumberSnapshot: string | null
}

interface ShipmentDocument {
  id: string
  documentType: string
  originalFileName: string
  storageKey: string
  mediaType: string
  sha256: string
  sizeBytes: number
  createdAt: string
}

interface Shipment {
  id: string
  shipmentNumber: string
  status: ShipmentStatus
  statusBeforeException: ShipmentStatus | null
  origin: string | null
  destination: string | null
  carrierId: string | null
  vehicleId: string | null
  routeId: string | null
  plannedDepartureAt: string | null
  plannedArrivalAt: string | null
  actualDepartureAt: string | null
  actualArrivalAt: string | null
  deliveredAt: string | null
  freightCost: NumericValue
  currencyCode: string
  freightRateId: string | null
  freightRateCodeSnapshot: string | null
  freightRateNameSnapshot: string | null
  freightMethodSnapshot: FreightCalculationMethod | null
  freightPricingDateSnapshot: string | null
  freightFixedComponentSnapshot: NumericValue | null
  freightVariableComponentSnapshot: NumericValue | null
  freightDistanceKmSnapshot: NumericValue | null
  freightMinimumAppliedSnapshot: boolean | null
  freightMaximumAppliedSnapshot: boolean | null
  totalWeightKg: NumericValue | null
  totalVolumeM3: NumericValue | null
  statusNote: string | null
  lines: ShipmentLine[]
  documents: ShipmentDocument[]
  createdAt: string
  updatedAt: string
}

interface FreightRate {
  id: string
  code: string
  name: string
  routeId: string | null
  carrierId: string | null
  currencyCode: string
  validFrom: string
  validTo: string | null
  active: boolean
  priority: number
  calculationMethod: FreightCalculationMethod
  fixedAmount: NumericValue | null
  unitAmount: NumericValue | null
  minimumCharge: NumericValue | null
  maximumCharge: NumericValue | null
  minimumWeightKg: NumericValue | null
  maximumWeightKg: NumericValue | null
  minimumVolumeM3: NumericValue | null
  maximumVolumeM3: NumericValue | null
  minimumDistanceKm: NumericValue | null
  maximumDistanceKm: NumericValue | null
  createdAt: string
  updatedAt: string
}

interface FreightQuote {
  freightRateId: string
  rateCode: string
  rateName: string
  calculationMethod: FreightCalculationMethod
  currencyCode: string
  pricingDate: string
  routeId: string | null
  carrierId: string | null
  weightKg: NumericValue | null
  volumeM3: NumericValue | null
  distanceKm: NumericValue | null
  fixedComponent: NumericValue
  variableComponent: NumericValue
  amount: NumericValue
  minimumApplied: boolean
  maximumApplied: boolean
  eligibleRateCount: number
}

type EditorState =
  | { kind: 'template'; item?: WorkflowTemplate }
  | { kind: 'execution' }
  | { kind: 'carrier'; item?: Carrier }
  | { kind: 'vehicle'; item?: Vehicle }
  | { kind: 'route'; item?: DeliveryRoute }
  | { kind: 'freight'; item?: FreightRate }
  | { kind: 'freight-simulation' }
  | { kind: 'shipment'; item?: Shipment }
  | { kind: 'shipment-freight'; shipment: Shipment }
  | { kind: 'shipment-document'; shipment: Shipment }

const tabs: Tab[] = ['templates', 'executions', 'carriers', 'vehicles', 'routes', 'freight', 'shipments']

const freightMethods: FreightCalculationMethod[] = ['FIXED', 'PER_KG', 'PER_M3', 'PER_KM', 'FIXED_PLUS_PER_KG', 'FIXED_PLUS_PER_M3', 'FIXED_PLUS_PER_KM']

const templateLabels = {
  es: { DRAFT: 'Borrador', PUBLISHED: 'Publicada', RETIRED: 'Retirada' },
  en: { DRAFT: 'Draft', PUBLISHED: 'Published', RETIRED: 'Retired' },
} satisfies Record<'es' | 'en', Record<TemplateStatus, string>>
const executionLabels = {
  es: { PENDING: 'Pendiente', IN_PROGRESS: 'En curso', COMPLETED: 'Completada', CANCELLED: 'Cancelada' },
  en: { PENDING: 'Pending', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', CANCELLED: 'Cancelled' },
} satisfies Record<'es' | 'en', Record<ExecutionStatus, string>>
const stepLabels = {
  es: { PENDING: 'Pendiente', IN_PROGRESS: 'En curso', COMPLETED: 'Completado', SKIPPED: 'Omitido', CANCELLED: 'Cancelado' },
  en: { PENDING: 'Pending', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', SKIPPED: 'Skipped', CANCELLED: 'Cancelled' },
} satisfies Record<'es' | 'en', Record<StepStatus, string>>
const shipmentLabels = {
  es: { PLANNED: 'Planificada', PACKING: 'Preparando', READY: 'Preparada', DISPATCHED: 'Expedida', IN_TRANSIT: 'En tránsito', ARRIVED: 'Llegada', DELIVERED: 'Entregada', EXCEPTION: 'Incidencia', CANCELLED: 'Cancelada' },
  en: { PLANNED: 'Planned', PACKING: 'Packing', READY: 'Ready', DISPATCHED: 'Dispatched', IN_TRANSIT: 'In transit', ARRIVED: 'Arrived', DELIVERED: 'Delivered', EXCEPTION: 'Exception', CANCELLED: 'Cancelled' },
} satisfies Record<'es' | 'en', Record<ShipmentStatus, string>>

const pageSize = 200

export function OperationsPage() {
  const { language, locale } = useTranslation()
  const { notify } = useToast()
  const [activeTab, setActiveTab] = useState<Tab>('templates')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('')
  const [secondaryFilter, setSecondaryFilter] = useState('')
  const [templates, setTemplates] = useState<WorkflowTemplate[]>([])
  const [executions, setExecutions] = useState<WorkExecution[]>([])
  const [carriers, setCarriers] = useState<Carrier[]>([])
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [routes, setRoutes] = useState<DeliveryRoute[]>([])
  const [freightRates, setFreightRates] = useState<FreightRate[]>([])
  const [shipments, setShipments] = useState<Shipment[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [selectedTemplate, setSelectedTemplate] = useState<WorkflowTemplate | null>(null)
  const [selectedExecution, setSelectedExecution] = useState<WorkExecution | null>(null)
  const [selectedRoute, setSelectedRoute] = useState<DeliveryRoute | null>(null)
  const [selectedShipment, setSelectedShipment] = useState<Shipment | null>(null)

  const loadAll = useCallback(async (initial = false) => {
    if (initial) setLoading(true)
    else setRefreshing(true)
    setError('')
    try {
      const [templatePage, executionPage, carrierPage, vehiclePage, routePage, freightPage, shipmentPage] = await Promise.all([
        apiFetch<PageResponse<WorkflowTemplate>>(`/api/v1/workflow-templates?size=${pageSize}`),
        apiFetch<PageResponse<WorkExecution>>(`/api/v1/work-executions?size=${pageSize}`),
        apiFetch<PageResponse<Carrier>>(`/api/v1/carriers?size=${pageSize}`),
        apiFetch<PageResponse<Vehicle>>(`/api/v1/vehicles?size=${pageSize}`),
        apiFetch<PageResponse<DeliveryRoute>>(`/api/v1/delivery-routes?size=${pageSize}`),
        apiFetch<PageResponse<FreightRate>>(`/api/v1/freight-rates?size=${pageSize}`),
        apiFetch<PageResponse<Shipment>>(`/api/v1/shipments?size=${pageSize}`),
      ])
      setTemplates(templatePage.content)
      setExecutions(executionPage.content)
      setCarriers(carrierPage.content)
      setVehicles(vehiclePage.content)
      setRoutes(routePage.content)
      setFreightRates(freightPage.content)
      setShipments(shipmentPage.content)
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => { void loadAll(true) }, [loadAll])

  const changeTab = (tab: Tab) => {
    setActiveTab(tab)
    setQuery('')
    setFilter('')
    setSecondaryFilter('')
  }

  const afterSave = async (message: string) => {
    setEditor(null)
    notify(message)
    await loadAll()
  }

  const deleteResource = async (kind: 'template' | 'carrier' | 'vehicle' | 'route' | 'freight' | 'shipment', id: string) => {
    const endpoints = {
      template: '/api/v1/workflow-templates', carrier: '/api/v1/carriers', vehicle: '/api/v1/vehicles',
      route: '/api/v1/delivery-routes', freight: '/api/v1/freight-rates', shipment: '/api/v1/shipments',
    }
    if (!window.confirm(local(language, 'Esta acción eliminará el registro. ¿Quieres continuar?', 'This action will delete the record. Do you want to continue?'))) return
    try {
      await apiFetch<void>(`${endpoints[kind]}/${id}`, { method: 'DELETE' })
      setSelectedTemplate(null)
      setSelectedRoute(null)
      setSelectedShipment(null)
      notify(local(language, 'Registro eliminado', 'Record deleted'))
      await loadAll()
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  const runTemplateAction = async (action: 'publish' | 'version' | 'retire', template: WorkflowTemplate) => {
    const suffix = action === 'version' ? 'versions' : action
    if (action === 'retire' && !window.confirm(local(language, 'La versión publicada dejará de estar disponible para nuevas ejecuciones.', 'The published version will no longer be available for new executions.'))) return
    try {
      const updated = await apiFetch<WorkflowTemplate>(`/api/v1/workflow-templates/${template.id}/${suffix}`, { method: 'POST' })
      setSelectedTemplate(updated)
      notify(action === 'publish' ? local(language, 'Plantilla publicada', 'Template published') : action === 'version' ? local(language, 'Nueva versión borrador creada', 'New draft version created') : local(language, 'Plantilla retirada', 'Template retired'))
      await loadAll()
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  const runStepAction = async (execution: WorkExecution, step: WorkStep, action: 'start' | 'complete' | 'skip' | 'cancel') => {
    let note: string | null = null
    if (action !== 'start') {
      note = window.prompt(action === 'cancel' ? local(language, 'Motivo o nota de cancelación (opcional)', 'Cancellation reason or note (optional)') : local(language, 'Nota de seguimiento (opcional)', 'Progress note (optional)'), '')
      if (note === null) return
    }
    if (action === 'cancel' && !window.confirm(local(language, 'Esta acción cancelará la ejecución y todos sus pasos pendientes.', 'This action will cancel the execution and all its pending steps.'))) return
    try {
      const updated = await apiFetch<WorkExecution>(`/api/v1/work-executions/${execution.id}/steps/${step.id}/${action}`, {
        method: 'POST',
        ...(action === 'start' ? {} : { body: JSON.stringify({ note: nullable(note ?? '') }) }),
      })
      setSelectedExecution(updated)
      setExecutions((current) => current.map((item) => item.id === updated.id ? updated : item))
      notify(local(language, 'Ejecución actualizada', 'Execution updated'))
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  const runShipmentTransition = async (shipment: Shipment, action: ShipmentTransition) => {
    let body: Record<string, string | null> | undefined
    if (action === 'report-exception' || action === 'cancel') {
      const reason = window.prompt(action === 'report-exception' ? local(language, 'Describe la incidencia', 'Describe the exception') : local(language, 'Indica el motivo de cancelación', 'Enter the cancellation reason'), '')
      if (reason === null) return
      if (!reason.trim()) { notify(local(language, 'El motivo es obligatorio', 'A reason is required'), 'error'); return }
      body = { reason: reason.trim() }
    }
    if (action === 'dispatch' || action === 'arrive' || action === 'deliver') {
      const value = window.prompt(local(language, 'Fecha y hora (vacío para usar la hora actual, formato AAAA-MM-DDTHH:mm)', 'Date and time (leave blank to use the current time, format YYYY-MM-DDTHH:mm)'), '')
      if (value === null) return
      if (value.trim()) {
        const occurredAt = toInstant(value)
        if (!occurredAt) { notify(local(language, 'La fecha y hora no son válidas', 'The date and time are invalid'), 'error'); return }
        body = { occurredAt }
      }
    }
    if (action === 'cancel' && !window.confirm(local(language, '¿Confirmas la cancelación de la expedición?', 'Do you confirm cancelling the shipment?'))) return
    try {
      const updated = await apiFetch<Shipment>(`/api/v1/shipments/${shipment.id}/transitions/${action}`, {
        method: 'POST', ...(body ? { body: JSON.stringify(body) } : {}),
      })
      setSelectedShipment(updated)
      setShipments((current) => current.map((item) => item.id === updated.id ? updated : item))
      notify(local(language, 'Estado de la expedición actualizado', 'Shipment status updated'))
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  const removeShipmentDocument = async (shipment: Shipment, documentId: string) => {
    if (!window.confirm(local(language, '¿Eliminar definitivamente el archivo y sus metadatos?', 'Permanently delete the file and its metadata?'))) return
    try {
      await apiFetch<void>(`/api/v1/shipments/${shipment.id}/documents/${documentId}`, { method: 'DELETE' })
      const updated = await apiFetch<Shipment>(`/api/v1/shipments/${shipment.id}`)
      setSelectedShipment(updated)
      setShipments((current) => current.map((item) => item.id === updated.id ? updated : item))
      notify(local(language, 'Documento eliminado', 'Document deleted'))
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  const downloadShipmentDocument = async (shipment: Shipment, document: ShipmentDocument) => {
    try {
      const download = await apiDownload(`/api/v1/shipments/${shipment.id}/documents/${document.id}`)
      const url = URL.createObjectURL(download.blob)
      const anchor = window.document.createElement('a')
      anchor.href = url
      anchor.download = download.filename || document.originalFileName
      anchor.rel = 'noopener'
      window.document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
      notify(local(language, 'Descarga iniciada', 'Download started'))
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  const visible = useMemo(() => ({
    templates: templates.filter((item) => matches(query, item.code, item.name, item.referenceType) && (!filter || item.status === filter)),
    executions: executions.filter((item) => matches(query, item.templateCode, item.templateName, item.referenceType, item.referenceId) && (!filter || item.status === filter)),
    carriers: carriers.filter((item) => matches(query, item.code, item.name, item.taxIdentifier, item.contactName) && (!filter || (filter === 'ACTIVE' ? item.active : filter === 'INACTIVE' ? !item.active : item.ownership === filter))),
    vehicles: vehicles.filter((item) => matches(query, item.code, item.registrationPlate, item.vehicleType, carrierLabel(item.carrierId, carriers, language)) && (!filter || (filter === 'ACTIVE' ? item.active : !item.active))),
    routes: routes.filter((item) => matches(query, item.code, item.name, item.origin, item.destination) && (!filter || (filter === 'ACTIVE' ? item.active : !item.active))),
    freight: freightRates.filter((item) => matches(query, item.code, item.name, item.currencyCode, carrierLabel(item.carrierId, carriers, language), routeLabel(item.routeId, routes, language))
      && (!filter || (filter === 'ACTIVE' ? item.active : !item.active)) && (!secondaryFilter || item.calculationMethod === secondaryFilter)),
    shipments: shipments.filter((item) => matches(query, item.shipmentNumber, item.origin, item.destination, item.lines.map((line) => line.productNameSnapshot).join(' ')) && (!filter || item.status === filter)),
  }), [carriers, executions, filter, freightRates, language, query, routes, secondaryFilter, shipments, templates, vehicles])

  const openCreate = () => setEditor(activeTab === 'templates' ? { kind: 'template' }
    : activeTab === 'executions' ? { kind: 'execution' }
      : activeTab === 'carriers' ? { kind: 'carrier' }
        : activeTab === 'vehicles' ? { kind: 'vehicle' }
          : activeTab === 'routes' ? { kind: 'route' }
            : activeTab === 'freight' ? { kind: 'freight' } : { kind: 'shipment' })

  return <div className="page-stack">
    <PageHeader eyebrow={local(language, 'Operaciones', 'Operations')} title={local(language, 'Workflows y logística', 'Workflows and logistics')} description={local(language, 'Planifica procesos, rutas y expediciones desde un único espacio operativo.', 'Plan processes, routes and shipments from a single operations workspace.')} icon={Boxes} actions={<>
      <button className="button button-secondary" type="button" onClick={() => void loadAll()} disabled={refreshing}><RefreshCw className={refreshing ? 'spin' : ''} size={17} />{local(language, 'Actualizar', 'Refresh')}</button>
      {activeTab === 'freight' && <button className="button button-secondary" type="button" onClick={() => setEditor({ kind: 'freight-simulation' })}><Play size={17} />{local(language, 'Simular flete', 'Simulate freight')}</button>}
      <button className="button button-primary" type="button" onClick={openCreate}><Plus size={17} />{createLabel(activeTab, language)}</button>
    </>} />

    <nav className="workspace-tabs" aria-label={local(language, 'Áreas de operaciones', 'Operations areas')}>
      {tabs.map((tab) => <button key={tab} type="button" className={activeTab === tab ? 'active' : ''} onClick={() => changeTab(tab)}>{tabIcon(tab)}{tabLabel(tab, language)}</button>)}
    </nav>

    <section className="panel table-panel">
      <TableToolbar value={query} onChange={setQuery} placeholder={searchPlaceholder(activeTab, language)}>
        <FilterSelect tab={activeTab} value={filter} onChange={setFilter} secondaryValue={secondaryFilter} onSecondaryChange={setSecondaryFilter} />
      </TableToolbar>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState label={local(language, 'Cargando operaciones…', 'Loading operations…')} /> : <OperationsTable tab={activeTab} data={visible} locale={locale} carriers={carriers} vehicles={vehicles} routes={routes}
        onTemplate={setSelectedTemplate} onExecution={setSelectedExecution} onCarrier={(item) => setEditor({ kind: 'carrier', item })}
        onVehicle={(item) => setEditor({ kind: 'vehicle', item })} onRoute={setSelectedRoute}
        onFreight={(item) => setEditor({ kind: 'freight', item })} onDeleteFreight={(item) => void deleteResource('freight', item.id)}
        onShipment={setSelectedShipment} onCreate={openCreate} />}
    </section>

    <Modal open={editor !== null} title={editorTitle(editor, language)} description={editorDescription(editor, language)} onClose={() => setEditor(null)} size="large">
      {editor?.kind === 'template' && <TemplateForm item={editor.item} onCancel={() => setEditor(null)} onSaved={() => void afterSave(editor.item ? local(language, 'Plantilla actualizada', 'Template updated') : local(language, 'Plantilla creada', 'Template created'))} />}
      {editor?.kind === 'execution' && <ExecutionForm templates={templates} onCancel={() => setEditor(null)} onSaved={(created) => { setSelectedExecution(created); void afterSave(local(language, 'Ejecución creada', 'Execution created')) }} />}
      {editor?.kind === 'carrier' && <CarrierForm item={editor.item} onCancel={() => setEditor(null)} onSaved={() => void afterSave(editor.item ? local(language, 'Transportista actualizado', 'Carrier updated') : local(language, 'Transportista creado', 'Carrier created'))} />}
      {editor?.kind === 'vehicle' && <VehicleForm item={editor.item} carriers={carriers} onCancel={() => setEditor(null)} onSaved={() => void afterSave(editor.item ? local(language, 'Vehículo actualizado', 'Vehicle updated') : local(language, 'Vehículo creado', 'Vehicle created'))} />}
      {editor?.kind === 'route' && <RouteForm item={editor.item} carriers={carriers} vehicles={vehicles} onCancel={() => setEditor(null)} onSaved={() => { setSelectedRoute(null); void afterSave(editor.item ? local(language, 'Ruta actualizada', 'Route updated') : local(language, 'Ruta creada', 'Route created')) }} />}
      {editor?.kind === 'freight' && <FreightRateForm item={editor.item} carriers={carriers} routes={routes} onCancel={() => setEditor(null)} onSaved={() => void afterSave(editor.item ? local(language, 'Tarifa de flete actualizada', 'Freight rate updated') : local(language, 'Tarifa de flete creada', 'Freight rate created'))} />}
      {editor?.kind === 'freight-simulation' && <FreightSimulationForm carriers={carriers} routes={routes} locale={locale} onCancel={() => setEditor(null)} />}
      {editor?.kind === 'shipment' && <ShipmentForm item={editor.item} carriers={carriers} vehicles={vehicles} routes={routes} onCancel={() => setEditor(null)} onSaved={() => { setSelectedShipment(null); void afterSave(editor.item ? local(language, 'Expedición actualizada', 'Shipment updated') : local(language, 'Expedición creada', 'Shipment created')) }} />}
      {editor?.kind === 'shipment-freight' && <ShipmentFreightForm shipment={editor.shipment} routes={routes} locale={locale} onCancel={() => setEditor(null)} onSaved={(updated) => {
        setSelectedShipment(updated); setShipments((current) => current.map((item) => item.id === updated.id ? updated : item)); setEditor(null); notify(local(language, 'Flete resuelto y guardado', 'Freight resolved and saved'))
      }} />}
      {editor?.kind === 'shipment-document' && <ShipmentDocumentForm shipment={editor.shipment} onCancel={() => setEditor(null)} onSaved={async () => {
        const updated = await apiFetch<Shipment>(`/api/v1/shipments/${editor.shipment.id}`)
        setSelectedShipment(updated); setShipments((current) => current.map((item) => item.id === updated.id ? updated : item)); setEditor(null); notify(local(language, 'Documento subido', 'Document uploaded'))
      }} />}
    </Modal>

    <Modal open={selectedTemplate !== null} title={selectedTemplate ? `${selectedTemplate.code} · v${selectedTemplate.templateVersion}` : local(language, 'Plantilla', 'Template')} description={selectedTemplate?.name} onClose={() => setSelectedTemplate(null)} size="large">
      {selectedTemplate && <TemplateDetail template={selectedTemplate} locale={locale} onEdit={() => { setEditor({ kind: 'template', item: selectedTemplate }); setSelectedTemplate(null) }}
        onAction={(action) => void runTemplateAction(action, selectedTemplate)} onDelete={() => void deleteResource('template', selectedTemplate.id)} />}
    </Modal>

    <Modal open={selectedExecution !== null} title={selectedExecution ? `${selectedExecution.templateCode} · v${selectedExecution.templateVersion}` : local(language, 'Ejecución', 'Execution')} description={selectedExecution ? `${selectedExecution.referenceType} · ${selectedExecution.referenceId}` : ''} onClose={() => setSelectedExecution(null)} size="large">
      {selectedExecution && <ExecutionDetail execution={selectedExecution} locale={locale} onAction={(step, action) => void runStepAction(selectedExecution, step, action)} />}
    </Modal>

    <Modal open={selectedRoute !== null} title={selectedRoute?.name ?? local(language, 'Ruta', 'Route')} description={selectedRoute ? `${selectedRoute.origin} → ${selectedRoute.destination}` : ''} onClose={() => setSelectedRoute(null)} size="large">
      {selectedRoute && <RouteDetail route={selectedRoute} locale={locale} carriers={carriers} vehicles={vehicles} onEdit={() => { setEditor({ kind: 'route', item: selectedRoute }); setSelectedRoute(null) }} onDelete={() => void deleteResource('route', selectedRoute.id)} />}
    </Modal>

    <Modal open={selectedShipment !== null} title={selectedShipment?.shipmentNumber ?? local(language, 'Expedición', 'Shipment')} description={selectedShipment ? `${selectedShipment.origin ?? local(language, 'Origen pendiente', 'Origin pending')} → ${selectedShipment.destination ?? local(language, 'Destino pendiente', 'Destination pending')}` : ''} onClose={() => setSelectedShipment(null)} size="large">
      {selectedShipment && <ShipmentDetail shipment={selectedShipment} locale={locale} carriers={carriers} vehicles={vehicles} routes={routes}
        onEdit={() => { setEditor({ kind: 'shipment', item: selectedShipment }); setSelectedShipment(null) }}
        onAddDocument={() => { setEditor({ kind: 'shipment-document', shipment: selectedShipment }); setSelectedShipment(null) }}
        onResolveFreight={() => { setEditor({ kind: 'shipment-freight', shipment: selectedShipment }); setSelectedShipment(null) }}
        onDownloadDocument={(document) => void downloadShipmentDocument(selectedShipment, document)}
        onDeleteDocument={(id) => void removeShipmentDocument(selectedShipment, id)}
        onTransition={(action) => void runShipmentTransition(selectedShipment, action)}
        onDelete={() => void deleteResource('shipment', selectedShipment.id)} />}
    </Modal>
  </div>
}

interface VisibleData {
  templates: WorkflowTemplate[]
  executions: WorkExecution[]
  carriers: Carrier[]
  vehicles: Vehicle[]
  routes: DeliveryRoute[]
  freight: FreightRate[]
  shipments: Shipment[]
}

function OperationsTable({ tab, data, locale, carriers, vehicles, routes, onTemplate, onExecution, onCarrier, onVehicle, onRoute, onFreight, onDeleteFreight, onShipment, onCreate }: {
  tab: Tab; data: VisibleData; locale: string; carriers: Carrier[]; vehicles: Vehicle[]; routes: DeliveryRoute[]
  onTemplate: (item: WorkflowTemplate) => void; onExecution: (item: WorkExecution) => void
  onCarrier: (item: Carrier) => void; onVehicle: (item: Vehicle) => void; onRoute: (item: DeliveryRoute) => void
  onFreight: (item: FreightRate) => void; onDeleteFreight: (item: FreightRate) => void
  onShipment: (item: Shipment) => void; onCreate: () => void
}) {
  const { language } = useTranslation()
  const records = data[tab]
  if (records.length === 0) return <EmptyState title={local(language, 'No hay resultados', 'No results')} description={local(language, 'Crea el primer registro o modifica los filtros de búsqueda.', 'Create the first record or adjust the search filters.')} action={<button className="button button-secondary" type="button" onClick={onCreate}><Plus size={16} />{local(language, 'Crear registro', 'Create record')}</button>} />
  if (tab === 'templates') return <div className="table-scroll"><table><thead><tr><th>{local(language, 'Código', 'Code')}</th><th>{local(language, 'Nombre', 'Name')}</th><th>{local(language, 'Referencia', 'Reference')}</th><th>{local(language, 'Versión', 'Version')}</th><th>{local(language, 'Pasos', 'Steps')}</th><th>{local(language, 'Estado', 'Status')}</th></tr></thead><tbody>{data.templates.map((item) => <tr className="clickable-row" key={item.id} onClick={() => onTemplate(item)}><td><strong className="code-cell">{item.code}</strong></td><td>{item.name}</td><td>{item.referenceType}</td><td>v{item.templateVersion}</td><td>{item.steps.length}</td><td><StatusBadge tone={statusTone(item.status)}>{templateLabels[language][item.status]}</StatusBadge></td></tr>)}</tbody></table></div>
  if (tab === 'executions') return <div className="table-scroll"><table><thead><tr><th>{local(language, 'Plantilla', 'Template')}</th><th>{local(language, 'Referencia', 'Reference')}</th><th>{local(language, 'Progreso', 'Progress')}</th><th>{local(language, 'Inicio', 'Started')}</th><th>{local(language, 'Estado', 'Status')}</th></tr></thead><tbody>{data.executions.map((item) => <tr className="clickable-row" key={item.id} onClick={() => onExecution(item)}><td><strong>{item.templateName}</strong><small>{item.templateCode} · v{item.templateVersion}</small></td><td><strong>{item.referenceType}</strong><small>{item.referenceId}</small></td><td>{finishedSteps(item)}/{item.steps.length}</td><td>{formatDateTime(item.startedAt, locale)}</td><td><StatusBadge tone={statusTone(item.status)}>{executionLabels[language][item.status]}</StatusBadge></td></tr>)}</tbody></table></div>
  if (tab === 'carriers') return <div className="table-scroll"><table><thead><tr><th>{local(language, 'Código', 'Code')}</th><th>{local(language, 'Transportista', 'Carrier')}</th><th>{local(language, 'Tipo', 'Type')}</th><th>{local(language, 'Contacto', 'Contact')}</th><th>{local(language, 'Estado', 'Status')}</th><th aria-label={local(language, 'Acciones', 'Actions')} /></tr></thead><tbody>{data.carriers.map((item) => <tr key={item.id}><td><strong className="code-cell">{item.code}</strong></td><td><strong>{item.name}</strong><small>{item.taxIdentifier ?? local(language, 'Sin identificación fiscal', 'No tax identifier')}</small></td><td>{item.ownership === 'OWN' ? local(language, 'Flota propia', 'Own fleet') : local(language, 'Tercero', 'Third party')}</td><td>{item.contactName ?? '—'}<small>{item.contactEmail ?? item.contactPhone ?? ''}</small></td><td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.active ? local(language, 'Activo', 'Active') : local(language, 'Inactivo', 'Inactive')}</StatusBadge></td><td><div className="row-actions"><button className="icon-button" type="button" onClick={() => onCarrier(item)} aria-label={`${local(language, 'Editar', 'Edit')} ${item.name}`}><Pencil size={16} /></button></div></td></tr>)}</tbody></table></div>
  if (tab === 'vehicles') return <div className="table-scroll"><table><thead><tr><th>{local(language, 'Código', 'Code')}</th><th>{local(language, 'Matrícula', 'Registration')}</th><th>{local(language, 'Tipo', 'Type')}</th><th>{local(language, 'Transportista', 'Carrier')}</th><th>{local(language, 'Capacidad', 'Capacity')}</th><th>{local(language, 'Estado', 'Status')}</th><th aria-label={local(language, 'Acciones', 'Actions')} /></tr></thead><tbody>{data.vehicles.map((item) => <tr key={item.id}><td><strong className="code-cell">{item.code}</strong></td><td>{item.registrationPlate ?? '—'}</td><td>{item.vehicleType}</td><td>{carrierLabel(item.carrierId, carriers, language)}</td><td>{item.capacityWeightKg == null ? '—' : `${formatNumber(item.capacityWeightKg, locale, 3)} kg`}<small>{item.capacityVolumeM3 == null ? '' : `${formatNumber(item.capacityVolumeM3, locale, 6)} m³`}</small></td><td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.active ? local(language, 'Activo', 'Active') : local(language, 'Inactivo', 'Inactive')}</StatusBadge></td><td><div className="row-actions"><button className="icon-button" type="button" onClick={() => onVehicle(item)} aria-label={`${local(language, 'Editar', 'Edit')} ${item.code}`}><Pencil size={16} /></button></div></td></tr>)}</tbody></table></div>
  if (tab === 'routes') return <div className="table-scroll"><table><thead><tr><th>{local(language, 'Código', 'Code')}</th><th>{local(language, 'Ruta', 'Route')}</th><th>{local(language, 'Asignación', 'Assignment')}</th><th>{local(language, 'Salida prevista', 'Planned departure')}</th><th>{local(language, 'Distancia / duración', 'Distance / duration')}</th><th>{local(language, 'Paradas', 'Stops')}</th><th>{local(language, 'Estado', 'Status')}</th></tr></thead><tbody>{data.routes.map((item) => <tr className="clickable-row" key={item.id} onClick={() => onRoute(item)}><td><strong className="code-cell">{item.code}</strong></td><td><strong>{item.name}</strong><small>{item.origin} → {item.destination}</small></td><td>{carrierLabel(item.carrierId, carriers, language)}<small>{vehicleLabel(item.vehicleId, vehicles, language)}</small></td><td>{formatDateTime(item.plannedDepartureAt, locale)}</td><td>{item.distanceKm == null ? '—' : `${formatNumber(item.distanceKm, locale, 3)} km`}<small>{item.estimatedDurationMinutes == null ? '' : `${formatNumber(item.estimatedDurationMinutes, locale, 0)} min`}</small></td><td>{item.stops.length}</td><td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.active ? local(language, 'Activa', 'Active') : local(language, 'Inactiva', 'Inactive')}</StatusBadge></td></tr>)}</tbody></table></div>
  if (tab === 'freight') return <div className="table-scroll"><table><thead><tr><th>{local(language, 'Código', 'Code')}</th><th>{local(language, 'Tarifa', 'Rate')}</th><th>{local(language, 'Ámbito', 'Scope')}</th><th>{local(language, 'Método', 'Method')}</th><th>{local(language, 'Vigencia', 'Validity')}</th><th className="align-right">{local(language, 'Importes', 'Amounts')}</th><th>{local(language, 'Estado', 'Status')}</th><th aria-label={local(language, 'Acciones', 'Actions')} /></tr></thead><tbody>{data.freight.map((item) => <tr key={item.id}><td><strong className="code-cell">{item.code}</strong><small>{local(language, 'Prioridad', 'Priority')} {item.priority}</small></td><td><strong>{item.name}</strong><small>{item.currencyCode}</small></td><td>{routeLabel(item.routeId, routes, language)}<small>{carrierLabel(item.carrierId, carriers, language)}</small></td><td>{freightMethodLabel(item.calculationMethod, language)}</td><td>{item.validFrom}<small>{item.validTo ? `${local(language, 'hasta', 'to')} ${item.validTo}` : local(language, 'sin fecha final', 'no end date')}</small></td><td className="align-right"><strong>{item.fixedAmount == null ? '—' : formatCurrency(item.fixedAmount, item.currencyCode, locale)}</strong><small>{item.unitAmount == null ? '' : `${formatCurrency(item.unitAmount, item.currencyCode, locale)} / ${freightMetricLabel(item.calculationMethod, language)}`}</small></td><td><StatusBadge tone={item.active ? 'success' : 'neutral'}>{item.active ? local(language, 'Activa', 'Active') : local(language, 'Inactiva', 'Inactive')}</StatusBadge></td><td><div className="row-actions"><button className="icon-button" type="button" onClick={() => onFreight(item)} aria-label={`${local(language, 'Editar', 'Edit')} ${item.code}`}><Pencil size={16} /></button><button className="icon-button" type="button" onClick={() => onDeleteFreight(item)} aria-label={`${local(language, 'Eliminar', 'Delete')} ${item.code}`}><Trash2 size={16} /></button></div></td></tr>)}</tbody></table></div>
  return <div className="table-scroll"><table><thead><tr><th>{local(language, 'Número', 'Number')}</th><th>{local(language, 'Trayecto', 'Journey')}</th><th>{local(language, 'Planificación', 'Schedule')}</th><th>{local(language, 'Asignación', 'Assignment')}</th><th>{local(language, 'Líneas', 'Lines')}</th><th className="align-right">{local(language, 'Coste', 'Cost')}</th><th>{local(language, 'Estado', 'Status')}</th></tr></thead><tbody>{data.shipments.map((item) => <tr className="clickable-row" key={item.id} onClick={() => onShipment(item)}><td><strong className="document-number">{item.shipmentNumber}</strong></td><td><strong>{item.origin ?? '—'}</strong><small>{item.destination ?? '—'}</small></td><td>{formatDateTime(item.plannedDepartureAt, locale)}<small>{formatDateTime(item.plannedArrivalAt, locale)}</small></td><td>{carrierLabel(item.carrierId, carriers, language)}<small>{vehicleLabel(item.vehicleId, vehicles, language)}</small></td><td>{item.lines.length}</td><td className="align-right"><strong>{formatCurrency(item.freightCost, item.currencyCode, locale)}</strong></td><td><StatusBadge tone={statusTone(item.status)}>{shipmentLabels[language][item.status]}</StatusBadge></td></tr>)}</tbody></table></div>
}

function FilterSelect({ tab, value, onChange, secondaryValue, onSecondaryChange }: { tab: Tab; value: string; onChange: (value: string) => void; secondaryValue: string; onSecondaryChange: (value: string) => void }) {
  const { language } = useTranslation()
  const stateLabel = local(language, 'Filtrar por estado', 'Filter by status')
  if (tab === 'templates') return <select aria-label={stateLabel} value={value} onChange={(event) => onChange(event.target.value)}><option value="">{local(language, 'Todos los estados', 'All statuses')}</option>{Object.entries(templateLabels[language]).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
  if (tab === 'executions') return <select aria-label={stateLabel} value={value} onChange={(event) => onChange(event.target.value)}><option value="">{local(language, 'Todos los estados', 'All statuses')}</option>{Object.entries(executionLabels[language]).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
  if (tab === 'shipments') return <select aria-label={stateLabel} value={value} onChange={(event) => onChange(event.target.value)}><option value="">{local(language, 'Todos los estados', 'All statuses')}</option>{Object.entries(shipmentLabels[language]).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
  if (tab === 'carriers') return <select aria-label={local(language, 'Filtrar transportistas', 'Filter carriers')} value={value} onChange={(event) => onChange(event.target.value)}><option value="">{local(language, 'Todos', 'All')}</option><option value="ACTIVE">{local(language, 'Activos', 'Active')}</option><option value="INACTIVE">{local(language, 'Inactivos', 'Inactive')}</option><option value="OWN">{local(language, 'Flota propia', 'Own fleet')}</option><option value="THIRD_PARTY">{local(language, 'Terceros', 'Third parties')}</option></select>
  if (tab === 'freight') return <><select aria-label={stateLabel} value={value} onChange={(event) => onChange(event.target.value)}><option value="">{local(language, 'Todos los estados', 'All statuses')}</option><option value="ACTIVE">{local(language, 'Activas', 'Active')}</option><option value="INACTIVE">{local(language, 'Inactivas', 'Inactive')}</option></select><select aria-label={local(language, 'Filtrar por método', 'Filter by method')} value={secondaryValue} onChange={(event) => onSecondaryChange(event.target.value)}><option value="">{local(language, 'Todos los métodos', 'All methods')}</option>{freightMethods.map((method) => <option key={method} value={method}>{freightMethodLabel(method, language)}</option>)}</select></>
  return <select aria-label={stateLabel} value={value} onChange={(event) => onChange(event.target.value)}><option value="">{local(language, 'Todos', 'All')}</option><option value="ACTIVE">{local(language, 'Activos', 'Active')}</option><option value="INACTIVE">{local(language, 'Inactivos', 'Inactive')}</option></select>
}

function TemplateForm({ item, onCancel, onSaved }: { item?: WorkflowTemplate; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation()
  const [form, setForm] = useState({ code: item?.code ?? '', name: item?.name ?? '', referenceType: item?.referenceType ?? '' })
  const [steps, setSteps] = useState(() => item?.steps.map((step) => ({ code: step.code, name: step.name, description: step.description ?? '', required: step.required, estimatedMinutes: step.estimatedMinutes == null ? '' : String(step.estimatedMinutes) })) ?? [emptyWorkflowStep()])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const updateStep = (index: number, field: string, value: string | boolean) => setSteps((current) => current.map((step, stepIndex) => stepIndex === index ? { ...step, [field]: value } : step))
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    if (steps.some((step) => !step.code.trim() || !step.name.trim())) { setError(local(language, 'Cada paso necesita código y nombre.', 'Every step needs a code and name.')); setSaving(false); return }
    const body = {
      code: form.code.trim(), name: form.name.trim(), referenceType: form.referenceType.trim(),
      steps: steps.map((step, index) => ({ code: step.code.trim(), name: step.name.trim(), description: nullable(step.description), sequence: index + 1, required: step.required, estimatedMinutes: step.estimatedMinutes === '' ? null : Number(step.estimatedMinutes) })),
    }
    try { await apiFetch<WorkflowTemplate>(item ? `/api/v1/workflow-templates/${item.id}` : '/api/v1/workflow-templates', { method: item ? 'PUT' : 'POST', body: JSON.stringify(body) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <div className="form-grid">
      <Field label={local(language, 'Código', 'Code')} htmlFor="workflow-code" required><input id="workflow-code" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,59}" maxLength={60} disabled={Boolean(item)} required /></Field>
      <Field label={local(language, 'Nombre', 'Name')} htmlFor="workflow-name" required><input id="workflow-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={180} required /></Field>
      <Field label={local(language, 'Tipo de referencia', 'Reference type')} htmlFor="workflow-reference" required hint={local(language, 'Ej.: SALES_ORDER, SHIPMENT o SERVICE_REQUEST', 'E.g. SALES_ORDER, SHIPMENT or SERVICE_REQUEST')}><input id="workflow-reference" value={form.referenceType} onChange={(event) => setForm({ ...form, referenceType: event.target.value })} pattern="[A-Za-z0-9][A-Za-z0-9_.-]{0,79}" maxLength={80} required /></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Definición', 'Definition')}</span><h3>{local(language, 'Pasos del workflow', 'Workflow steps')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setSteps((current) => [...current, emptyWorkflowStep()])}><Plus size={15} />{local(language, 'Añadir paso', 'Add step')}</button></div>
    <div className="line-editor">{steps.map((step, index) => <div className="panel" key={index}>
      <div className="form-grid">
        <Field label={`${local(language, 'Código del paso', 'Step code')} ${index + 1}`} htmlFor={`step-code-${index}`} required><input id={`step-code-${index}`} value={step.code} onChange={(event) => updateStep(index, 'code', event.target.value)} pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,59}" maxLength={60} required /></Field>
        <Field label={local(language, 'Nombre', 'Name')} htmlFor={`step-name-${index}`} required><input id={`step-name-${index}`} value={step.name} onChange={(event) => updateStep(index, 'name', event.target.value)} maxLength={180} required /></Field>
        <Field label={local(language, 'Minutos estimados', 'Estimated minutes')} htmlFor={`step-minutes-${index}`}><input id={`step-minutes-${index}`} type="number" min="0" step="1" value={step.estimatedMinutes} onChange={(event) => updateStep(index, 'estimatedMinutes', event.target.value)} /></Field>
        <Field label={local(language, 'Obligatorio', 'Required')} htmlFor={`step-required-${index}`}><label className="switch-row" htmlFor={`step-required-${index}`}><input id={`step-required-${index}`} type="checkbox" checked={step.required} onChange={(event) => updateStep(index, 'required', event.target.checked)} /><span>{step.required ? local(language, 'Sí', 'Yes') : local(language, 'No', 'No')}</span></label></Field>
        <Field label={local(language, 'Descripción', 'Description')} htmlFor={`step-description-${index}`} wide><textarea id={`step-description-${index}`} rows={2} value={step.description} onChange={(event) => updateStep(index, 'description', event.target.value)} maxLength={2000} /></Field>
      </div>
      <div className="row-actions"><button className="button button-ghost button-small" type="button" disabled={steps.length === 1} onClick={() => setSteps((current) => current.filter((_, stepIndex) => stepIndex !== index))}><Trash2 size={15} />{local(language, 'Quitar paso', 'Remove step')}</button></div>
    </div>)}</div>
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? local(language, 'Guardar plantilla', 'Save template') : local(language, 'Crear plantilla', 'Create template')} />
  </form>
}

function ExecutionForm({ templates, onCancel, onSaved }: { templates: WorkflowTemplate[]; onCancel: () => void; onSaved: (execution: WorkExecution) => void }) {
  const { language } = useTranslation()
  const published = templates.filter((item) => item.status === 'PUBLISHED')
  const [templateId, setTemplateId] = useState(published[0]?.id ?? '')
  const [referenceId, setReferenceId] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const selected = published.find((item) => item.id === templateId)
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    if (!selected) { setError(local(language, 'Selecciona una plantilla publicada.', 'Select a published template.')); setSaving(false); return }
    try {
      const created = await apiFetch<WorkExecution>('/api/v1/work-executions', { method: 'POST', body: JSON.stringify({ templateId: selected.id, referenceType: selected.referenceType, referenceId: referenceId.trim() }) })
      onSaved(created)
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  if (published.length === 0) return <EmptyState title={local(language, 'No hay plantillas publicadas', 'No published templates')} description={local(language, 'Publica una plantilla antes de crear una ejecución.', 'Publish a template before creating an execution.')} />
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={local(language, 'Plantilla publicada', 'Published template')} htmlFor="execution-template" required><select id="execution-template" value={templateId} onChange={(event) => setTemplateId(event.target.value)} required>{published.map((item) => <option key={item.id} value={item.id}>{item.code} · v{item.templateVersion} · {item.name}</option>)}</select></Field>
    <Field label={local(language, 'Tipo de referencia', 'Reference type')} htmlFor="execution-reference-type"><input id="execution-reference-type" value={selected?.referenceType ?? ''} readOnly /></Field>
    <Field label={local(language, 'ID de referencia', 'Reference ID')} htmlFor="execution-reference-id" required hint={local(language, 'UUID del pedido, expedición, servicio u objeto asociado', 'UUID of the related order, shipment, service or object')}><input id="execution-reference-id" value={referenceId} onChange={(event) => setReferenceId(event.target.value)} pattern="[0-9a-fA-F-]{36}" maxLength={36} required /></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={local(language, 'Crear ejecución', 'Create execution')} /></form>
}

function CarrierForm({ item, onCancel, onSaved }: { item?: Carrier; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation()
  const [form, setForm] = useState({ code: item?.code ?? '', name: item?.name ?? '', ownership: item?.ownership ?? 'THIRD_PARTY' as CarrierOwnership, taxIdentifier: item?.taxIdentifier ?? '', externalIdentifier: item?.externalIdentifier ?? '', contactName: item?.contactName ?? '', contactEmail: item?.contactEmail ?? '', contactPhone: item?.contactPhone ?? '', active: item?.active ?? true })
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const body = { code: form.code.trim(), name: form.name.trim(), ownership: form.ownership, taxIdentifier: nullable(form.taxIdentifier), externalIdentifier: nullable(form.externalIdentifier), contactName: nullable(form.contactName), contactEmail: nullable(form.contactEmail), contactPhone: nullable(form.contactPhone), active: form.active }
    try { await apiFetch<Carrier>(item ? `/api/v1/carriers/${item.id}` : '/api/v1/carriers', { method: item ? 'PUT' : 'POST', body: JSON.stringify(body) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={local(language, 'Código', 'Code')} htmlFor="carrier-code" required><input id="carrier-code" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,59}" maxLength={60} disabled={Boolean(item)} required /></Field>
    <Field label={local(language, 'Nombre', 'Name')} htmlFor="carrier-name" required><input id="carrier-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={180} required /></Field>
    <Field label={local(language, 'Titularidad', 'Ownership')} htmlFor="carrier-ownership" required><select id="carrier-ownership" value={form.ownership} onChange={(event) => setForm({ ...form, ownership: event.target.value as CarrierOwnership })}><option value="OWN">{local(language, 'Flota propia', 'Own fleet')}</option><option value="THIRD_PARTY">{local(language, 'Tercero', 'Third party')}</option></select></Field>
    <Field label={local(language, 'NIF / identificación fiscal', 'Tax identifier')} htmlFor="carrier-tax"><input id="carrier-tax" value={form.taxIdentifier} onChange={(event) => setForm({ ...form, taxIdentifier: event.target.value })} maxLength={40} /></Field>
    <Field label={local(language, 'Identificador externo', 'External identifier')} htmlFor="carrier-external"><input id="carrier-external" value={form.externalIdentifier} onChange={(event) => setForm({ ...form, externalIdentifier: event.target.value })} maxLength={100} /></Field>
    <Field label={local(language, 'Persona de contacto', 'Contact person')} htmlFor="carrier-contact"><input id="carrier-contact" value={form.contactName} onChange={(event) => setForm({ ...form, contactName: event.target.value })} maxLength={180} /></Field>
    <Field label={local(language, 'Correo', 'Email')} htmlFor="carrier-email"><input id="carrier-email" type="email" value={form.contactEmail} onChange={(event) => setForm({ ...form, contactEmail: event.target.value })} maxLength={254} /></Field>
    <Field label={local(language, 'Teléfono', 'Phone')} htmlFor="carrier-phone"><input id="carrier-phone" value={form.contactPhone} onChange={(event) => setForm({ ...form, contactPhone: event.target.value })} maxLength={40} /></Field>
    <Field label={local(language, 'Estado', 'Status')} htmlFor="carrier-active"><label className="switch-row" htmlFor="carrier-active"><input id="carrier-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{form.active ? local(language, 'Activo', 'Active') : local(language, 'Inactivo', 'Inactive')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? local(language, 'Guardar transportista', 'Save carrier') : local(language, 'Crear transportista', 'Create carrier')} /></form>
}

function VehicleForm({ item, carriers, onCancel, onSaved }: { item?: Vehicle; carriers: Carrier[]; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation()
  const [form, setForm] = useState({ code: item?.code ?? '', registrationPlate: item?.registrationPlate ?? '', vehicleType: item?.vehicleType ?? '', carrierId: item?.carrierId ?? '', capacityWeightKg: item?.capacityWeightKg == null ? '' : String(item.capacityWeightKg), capacityVolumeM3: item?.capacityVolumeM3 == null ? '' : String(item.capacityVolumeM3), active: item?.active ?? true })
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    const body = { code: form.code.trim(), registrationPlate: nullable(form.registrationPlate), vehicleType: form.vehicleType.trim(), carrierId: nullable(form.carrierId), capacityWeightKg: nullableNumber(form.capacityWeightKg), capacityVolumeM3: nullableNumber(form.capacityVolumeM3), active: form.active }
    try { await apiFetch<Vehicle>(item ? `/api/v1/vehicles/${item.id}` : '/api/v1/vehicles', { method: item ? 'PUT' : 'POST', body: JSON.stringify(body) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={local(language, 'Código', 'Code')} htmlFor="vehicle-code" required><input id="vehicle-code" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,59}" maxLength={60} disabled={Boolean(item)} required /></Field>
    <Field label={local(language, 'Matrícula', 'Registration plate')} htmlFor="vehicle-plate"><input id="vehicle-plate" value={form.registrationPlate} onChange={(event) => setForm({ ...form, registrationPlate: event.target.value })} maxLength={30} /></Field>
    <Field label={local(language, 'Tipo de vehículo', 'Vehicle type')} htmlFor="vehicle-type" required><input id="vehicle-type" value={form.vehicleType} onChange={(event) => setForm({ ...form, vehicleType: event.target.value })} maxLength={80} placeholder={local(language, 'Furgoneta, camión, turismo…', 'Van, truck, car…')} required /></Field>
    <Field label={local(language, 'Transportista', 'Carrier')} htmlFor="vehicle-carrier"><select id="vehicle-carrier" value={form.carrierId} onChange={(event) => setForm({ ...form, carrierId: event.target.value })}><option value="">{local(language, 'Sin asignar', 'Unassigned')}</option>{carriers.map((carrier) => <option key={carrier.id} value={carrier.id}>{carrier.code} · {carrier.name}</option>)}</select></Field>
    <Field label={local(language, 'Capacidad de peso (kg)', 'Weight capacity (kg)')} htmlFor="vehicle-weight"><input id="vehicle-weight" type="number" min="0" step="0.001" value={form.capacityWeightKg} onChange={(event) => setForm({ ...form, capacityWeightKg: event.target.value })} /></Field>
    <Field label={local(language, 'Capacidad de volumen (m³)', 'Volume capacity (m³)')} htmlFor="vehicle-volume"><input id="vehicle-volume" type="number" min="0" step="0.000001" value={form.capacityVolumeM3} onChange={(event) => setForm({ ...form, capacityVolumeM3: event.target.value })} /></Field>
    <Field label={local(language, 'Estado', 'Status')} htmlFor="vehicle-active"><label className="switch-row" htmlFor="vehicle-active"><input id="vehicle-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{form.active ? local(language, 'Activo', 'Active') : local(language, 'Inactivo', 'Inactive')}</span></label></Field>
  </div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? local(language, 'Guardar vehículo', 'Save vehicle') : local(language, 'Crear vehículo', 'Create vehicle')} /></form>
}

function RouteForm({ item, carriers, vehicles, onCancel, onSaved }: { item?: DeliveryRoute; carriers: Carrier[]; vehicles: Vehicle[]; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation()
  const [form, setForm] = useState({
    code: item?.code ?? '', name: item?.name ?? '', origin: item?.origin ?? '', destination: item?.destination ?? '',
    carrierId: item?.carrierId ?? '', vehicleId: item?.vehicleId ?? '',
    plannedDepartureAt: toDateTimeInput(item?.plannedDepartureAt), plannedArrivalAt: toDateTimeInput(item?.plannedArrivalAt),
    deliveryWindowStart: toDateTimeInput(item?.deliveryWindowStart), deliveryWindowEnd: toDateTimeInput(item?.deliveryWindowEnd),
    distanceKm: item?.distanceKm == null ? '' : String(item.distanceKm), estimatedDurationMinutes: item?.estimatedDurationMinutes == null ? '' : String(item.estimatedDurationMinutes),
    active: item?.active ?? true,
  })
  const [stops, setStops] = useState(() => item?.stops.map((stop) => ({ name: stop.name, location: stop.location, windowStart: toDateTimeInput(stop.windowStart), windowEnd: toDateTimeInput(stop.windowEnd), instructions: stop.instructions ?? '' })) ?? [])
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const matchingVehicles = vehicles.filter((vehicle) => !form.carrierId || !vehicle.carrierId || vehicle.carrierId === form.carrierId || vehicle.id === form.vehicleId)
  const updateStop = (index: number, field: string, value: string) => setStops((current) => current.map((stop, stopIndex) => stopIndex === index ? { ...stop, [field]: value } : stop))
  const chooseCarrier = (carrierId: string) => {
    const currentVehicle = vehicles.find((vehicle) => vehicle.id === form.vehicleId)
    setForm({ ...form, carrierId, vehicleId: currentVehicle?.carrierId && currentVehicle.carrierId !== carrierId ? '' : form.vehicleId })
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    if (stops.some((stop) => !stop.name.trim() || !stop.location.trim())) { setError(local(language, 'Cada parada necesita nombre y ubicación.', 'Every stop needs a name and location.')); setSaving(false); return }
    if (!validChronology(form.plannedDepartureAt, form.plannedArrivalAt) || !validChronology(form.deliveryWindowStart, form.deliveryWindowEnd) || stops.some((stop) => !validChronology(stop.windowStart, stop.windowEnd))) {
      setError(local(language, 'Las fechas finales no pueden ser anteriores a sus fechas iniciales.', 'End dates cannot be earlier than their start dates.')); setSaving(false); return
    }
    const body = {
      code: form.code.trim(), name: form.name.trim(), origin: form.origin.trim(), destination: form.destination.trim(),
      carrierId: nullable(form.carrierId), vehicleId: nullable(form.vehicleId),
      plannedDepartureAt: toInstant(form.plannedDepartureAt), plannedArrivalAt: toInstant(form.plannedArrivalAt),
      deliveryWindowStart: toInstant(form.deliveryWindowStart), deliveryWindowEnd: toInstant(form.deliveryWindowEnd),
      distanceKm: nullableNumber(form.distanceKm), estimatedDurationMinutes: nullableNumber(form.estimatedDurationMinutes), active: form.active,
      stops: stops.map((stop, index) => ({ sequence: index + 1, name: stop.name.trim(), location: stop.location.trim(), windowStart: toInstant(stop.windowStart), windowEnd: toInstant(stop.windowEnd), instructions: nullable(stop.instructions) })),
    }
    try { await apiFetch<DeliveryRoute>(item ? `/api/v1/delivery-routes/${item.id}` : '/api/v1/delivery-routes', { method: item ? 'PUT' : 'POST', body: JSON.stringify(body) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <div className="form-grid">
      <Field label={local(language, 'Código', 'Code')} htmlFor="route-code" required><input id="route-code" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,59}" maxLength={60} disabled={Boolean(item)} required /></Field>
      <Field label={local(language, 'Nombre', 'Name')} htmlFor="route-name" required><input id="route-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={180} required /></Field>
      <Field label={local(language, 'Origen', 'Origin')} htmlFor="route-origin" required><input id="route-origin" value={form.origin} onChange={(event) => setForm({ ...form, origin: event.target.value })} maxLength={500} required /></Field>
      <Field label={local(language, 'Destino', 'Destination')} htmlFor="route-destination" required><input id="route-destination" value={form.destination} onChange={(event) => setForm({ ...form, destination: event.target.value })} maxLength={500} required /></Field>
      <Field label={local(language, 'Transportista', 'Carrier')} htmlFor="route-carrier"><select id="route-carrier" value={form.carrierId} onChange={(event) => chooseCarrier(event.target.value)}><option value="">{local(language, 'Sin asignar', 'Unassigned')}</option>{carriers.filter((carrier) => carrier.active || carrier.id === form.carrierId).map((carrier) => <option key={carrier.id} value={carrier.id}>{carrier.code} · {carrier.name}</option>)}</select></Field>
      <Field label={local(language, 'Vehículo', 'Vehicle')} htmlFor="route-vehicle"><select id="route-vehicle" value={form.vehicleId} onChange={(event) => setForm({ ...form, vehicleId: event.target.value })}><option value="">{local(language, 'Sin asignar', 'Unassigned')}</option>{matchingVehicles.filter((vehicle) => vehicle.active || vehicle.id === form.vehicleId).map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.code} · {vehicle.registrationPlate ?? vehicle.vehicleType}</option>)}</select></Field>
      <Field label={local(language, 'Salida prevista', 'Planned departure')} htmlFor="route-departure"><input id="route-departure" type="datetime-local" value={form.plannedDepartureAt} onChange={(event) => setForm({ ...form, plannedDepartureAt: event.target.value })} /></Field>
      <Field label={local(language, 'Llegada prevista', 'Planned arrival')} htmlFor="route-arrival"><input id="route-arrival" type="datetime-local" value={form.plannedArrivalAt} onChange={(event) => setForm({ ...form, plannedArrivalAt: event.target.value })} /></Field>
      <Field label={local(language, 'Inicio ventana de entrega', 'Delivery window start')} htmlFor="route-window-start"><input id="route-window-start" type="datetime-local" value={form.deliveryWindowStart} onChange={(event) => setForm({ ...form, deliveryWindowStart: event.target.value })} /></Field>
      <Field label={local(language, 'Fin ventana de entrega', 'Delivery window end')} htmlFor="route-window-end"><input id="route-window-end" type="datetime-local" value={form.deliveryWindowEnd} onChange={(event) => setForm({ ...form, deliveryWindowEnd: event.target.value })} /></Field>
      <Field label={local(language, 'Distancia (km)', 'Distance (km)')} htmlFor="route-distance" hint={local(language, 'Se usa automáticamente al resolver fletes por kilómetro.', 'Used automatically when resolving per-kilometre freight.')}><input id="route-distance" type="number" min="0.001" step="0.001" value={form.distanceKm} onChange={(event) => setForm({ ...form, distanceKm: event.target.value })} /></Field>
      <Field label={local(language, 'Duración estimada (min)', 'Estimated duration (min)')} htmlFor="route-duration"><input id="route-duration" type="number" min="1" step="1" value={form.estimatedDurationMinutes} onChange={(event) => setForm({ ...form, estimatedDurationMinutes: event.target.value })} /></Field>
      <Field label={local(language, 'Estado', 'Status')} htmlFor="route-active"><label className="switch-row" htmlFor="route-active"><input id="route-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{form.active ? local(language, 'Activa', 'Active') : local(language, 'Inactiva', 'Inactive')}</span></label></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Itinerario', 'Itinerary')}</span><h3>{local(language, 'Paradas', 'Stops')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setStops((current) => [...current, emptyRouteStop()])}><Plus size={15} />{local(language, 'Añadir parada', 'Add stop')}</button></div>
    {stops.length === 0 ? <EmptyState title={local(language, 'Ruta directa', 'Direct route')} description={local(language, 'No hay paradas intermedias configuradas.', 'No intermediate stops are configured.')} /> : <div className="line-editor">{stops.map((stop, index) => <div className="panel" key={index}>
      <div className="form-grid">
        <Field label={`${local(language, 'Parada', 'Stop')} ${index + 1}`} htmlFor={`stop-name-${index}`} required><input id={`stop-name-${index}`} value={stop.name} onChange={(event) => updateStop(index, 'name', event.target.value)} maxLength={180} required /></Field>
        <Field label={local(language, 'Ubicación', 'Location')} htmlFor={`stop-location-${index}`} required><input id={`stop-location-${index}`} value={stop.location} onChange={(event) => updateStop(index, 'location', event.target.value)} maxLength={500} required /></Field>
        <Field label={local(language, 'Inicio de ventana', 'Window start')} htmlFor={`stop-start-${index}`}><input id={`stop-start-${index}`} type="datetime-local" value={stop.windowStart} onChange={(event) => updateStop(index, 'windowStart', event.target.value)} /></Field>
        <Field label={local(language, 'Fin de ventana', 'Window end')} htmlFor={`stop-end-${index}`}><input id={`stop-end-${index}`} type="datetime-local" value={stop.windowEnd} onChange={(event) => updateStop(index, 'windowEnd', event.target.value)} /></Field>
        <Field label={local(language, 'Instrucciones', 'Instructions')} htmlFor={`stop-instructions-${index}`} wide><textarea id={`stop-instructions-${index}`} rows={2} value={stop.instructions} onChange={(event) => updateStop(index, 'instructions', event.target.value)} maxLength={1000} /></Field>
      </div><div className="row-actions"><button className="button button-ghost button-small" type="button" onClick={() => setStops((current) => current.filter((_, stopIndex) => stopIndex !== index))}><Trash2 size={15} />{local(language, 'Quitar parada', 'Remove stop')}</button></div>
    </div>)}</div>}
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? local(language, 'Guardar ruta', 'Save route') : local(language, 'Crear ruta', 'Create route')} />
  </form>
}

function FreightRateForm({ item, carriers, routes, onCancel, onSaved }: { item?: FreightRate; carriers: Carrier[]; routes: DeliveryRoute[]; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation()
  const [form, setForm] = useState({
    code: item?.code ?? '', name: item?.name ?? '', routeId: item?.routeId ?? '', carrierId: item?.carrierId ?? '',
    currencyCode: item?.currencyCode ?? 'EUR', validFrom: item?.validFrom ?? today(), validTo: item?.validTo ?? '',
    active: item?.active ?? true, priority: String(item?.priority ?? 0), calculationMethod: item?.calculationMethod ?? 'FIXED' as FreightCalculationMethod,
    fixedAmount: item?.fixedAmount == null ? '0' : String(item.fixedAmount), unitAmount: item?.unitAmount == null ? '' : String(item.unitAmount),
    minimumCharge: item?.minimumCharge == null ? '' : String(item.minimumCharge), maximumCharge: item?.maximumCharge == null ? '' : String(item.maximumCharge),
    minimumWeightKg: item?.minimumWeightKg == null ? '' : String(item.minimumWeightKg), maximumWeightKg: item?.maximumWeightKg == null ? '' : String(item.maximumWeightKg),
    minimumVolumeM3: item?.minimumVolumeM3 == null ? '' : String(item.minimumVolumeM3), maximumVolumeM3: item?.maximumVolumeM3 == null ? '' : String(item.maximumVolumeM3),
    minimumDistanceKm: item?.minimumDistanceKm == null ? '' : String(item.minimumDistanceKm), maximumDistanceKm: item?.maximumDistanceKm == null ? '' : String(item.maximumDistanceKm),
  })
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const selectedRoute = routes.find((route) => route.id === form.routeId)
  const availableCarriers = selectedRoute?.carrierId ? carriers.filter((carrier) => carrier.id === selectedRoute.carrierId) : carriers
  const requiresFixed = freightRequiresFixed(form.calculationMethod)
  const requiresUnit = freightRequiresUnit(form.calculationMethod)
  const chooseRoute = (routeId: string) => {
    const route = routes.find((candidate) => candidate.id === routeId)
    setForm({ ...form, routeId, carrierId: route?.carrierId ?? form.carrierId })
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    if (form.validTo && form.validTo < form.validFrom) { setError(local(language, 'La vigencia final no puede ser anterior a la inicial.', 'The end of validity cannot be earlier than the start.')); setSaving(false); return }
    if ((requiresFixed && form.fixedAmount === '') || (requiresUnit && form.unitAmount === '')) { setError(local(language, 'Completa los importes requeridos por el método seleccionado.', 'Complete the amounts required by the selected method.')); setSaving(false); return }
    const ranges = [[form.minimumCharge, form.maximumCharge], [form.minimumWeightKg, form.maximumWeightKg], [form.minimumVolumeM3, form.maximumVolumeM3], [form.minimumDistanceKm, form.maximumDistanceKm]]
    if (ranges.some(([minimum, maximum]) => minimum && maximum && Number(maximum) < Number(minimum))) { setError(local(language, 'Un máximo no puede ser inferior a su mínimo.', 'A maximum cannot be lower than its minimum.')); setSaving(false); return }
    const body = {
      code: form.code.trim(), name: form.name.trim(), routeId: nullable(form.routeId), carrierId: nullable(form.carrierId), currencyCode: form.currencyCode.trim().toUpperCase(),
      validFrom: form.validFrom, validTo: nullable(form.validTo), active: form.active, priority: Number(form.priority), calculationMethod: form.calculationMethod,
      fixedAmount: requiresFixed ? Number(form.fixedAmount) : null, unitAmount: requiresUnit ? Number(form.unitAmount) : null,
      minimumCharge: nullableNumber(form.minimumCharge), maximumCharge: nullableNumber(form.maximumCharge),
      minimumWeightKg: nullableNumber(form.minimumWeightKg), maximumWeightKg: nullableNumber(form.maximumWeightKg),
      minimumVolumeM3: nullableNumber(form.minimumVolumeM3), maximumVolumeM3: nullableNumber(form.maximumVolumeM3),
      minimumDistanceKm: nullableNumber(form.minimumDistanceKm), maximumDistanceKm: nullableNumber(form.maximumDistanceKm),
    }
    try { await apiFetch<FreightRate>(item ? `/api/v1/freight-rates/${item.id}` : '/api/v1/freight-rates', { method: item ? 'PUT' : 'POST', body: JSON.stringify(body) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <div className="form-grid">
      <Field label={local(language, 'Código', 'Code')} htmlFor="freight-code" required><input id="freight-code" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,59}" maxLength={60} disabled={Boolean(item)} required /></Field>
      <Field label={local(language, 'Nombre', 'Name')} htmlFor="freight-name" required><input id="freight-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} maxLength={180} required /></Field>
      <Field label={local(language, 'Ruta aplicable', 'Applicable route')} htmlFor="freight-route"><select id="freight-route" value={form.routeId} onChange={(event) => chooseRoute(event.target.value)}><option value="">{local(language, 'Cualquier ruta', 'Any route')}</option>{routes.map((route) => <option key={route.id} value={route.id}>{route.code} · {route.name}</option>)}</select></Field>
      <Field label={local(language, 'Transportista aplicable', 'Applicable carrier')} htmlFor="freight-carrier"><select id="freight-carrier" value={form.carrierId} onChange={(event) => setForm({ ...form, carrierId: event.target.value })}><option value="">{local(language, 'Cualquier transportista', 'Any carrier')}</option>{availableCarriers.map((carrier) => <option key={carrier.id} value={carrier.id}>{carrier.code} · {carrier.name}</option>)}</select></Field>
      <Field label={local(language, 'Moneda ISO', 'ISO currency')} htmlFor="freight-currency" required><input id="freight-currency" value={form.currencyCode} onChange={(event) => setForm({ ...form, currencyCode: event.target.value })} pattern="[A-Za-z]{3}" minLength={3} maxLength={3} required /></Field>
      <Field label={local(language, 'Prioridad', 'Priority')} htmlFor="freight-priority" required hint={local(language, 'La prioridad mayor gana entre tarifas elegibles.', 'The highest priority wins among eligible rates.')}><input id="freight-priority" type="number" min="-1000000" max="1000000" step="1" value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })} required /></Field>
      <Field label={local(language, 'Válida desde', 'Valid from')} htmlFor="freight-valid-from" required><input id="freight-valid-from" type="date" value={form.validFrom} onChange={(event) => setForm({ ...form, validFrom: event.target.value })} required /></Field>
      <Field label={local(language, 'Válida hasta', 'Valid to')} htmlFor="freight-valid-to"><input id="freight-valid-to" type="date" value={form.validTo} onChange={(event) => setForm({ ...form, validTo: event.target.value })} /></Field>
      <Field label={local(language, 'Método de cálculo', 'Calculation method')} htmlFor="freight-method" required><select id="freight-method" value={form.calculationMethod} onChange={(event) => setForm({ ...form, calculationMethod: event.target.value as FreightCalculationMethod })}>{freightMethods.map((method) => <option key={method} value={method}>{freightMethodLabel(method, language)}</option>)}</select></Field>
      <Field label={local(language, 'Estado', 'Status')} htmlFor="freight-active"><label className="switch-row" htmlFor="freight-active"><input id="freight-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{form.active ? local(language, 'Activa', 'Active') : local(language, 'Inactiva', 'Inactive')}</span></label></Field>
      {requiresFixed && <Field label={local(language, 'Importe fijo', 'Fixed amount')} htmlFor="freight-fixed" required><input id="freight-fixed" type="number" min="0" step="0.0001" value={form.fixedAmount} onChange={(event) => setForm({ ...form, fixedAmount: event.target.value })} required /></Field>}
      {requiresUnit && <Field label={`${local(language, 'Importe por', 'Amount per')} ${freightMetricLabel(form.calculationMethod, language)}`} htmlFor="freight-unit" required><input id="freight-unit" type="number" min="0" step="0.000001" value={form.unitAmount} onChange={(event) => setForm({ ...form, unitAmount: event.target.value })} required /></Field>}
      <Field label={local(language, 'Cargo mínimo', 'Minimum charge')} htmlFor="freight-min-charge"><input id="freight-min-charge" type="number" min="0" step="0.0001" value={form.minimumCharge} onChange={(event) => setForm({ ...form, minimumCharge: event.target.value })} /></Field>
      <Field label={local(language, 'Cargo máximo', 'Maximum charge')} htmlFor="freight-max-charge"><input id="freight-max-charge" type="number" min="0" step="0.0001" value={form.maximumCharge} onChange={(event) => setForm({ ...form, maximumCharge: event.target.value })} /></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Elegibilidad', 'Eligibility')}</span><h3>{local(language, 'Rangos admitidos', 'Accepted ranges')}</h3></div></div>
    <div className="form-grid">
      <Field label={local(language, 'Peso mínimo (kg)', 'Minimum weight (kg)')} htmlFor="freight-min-weight"><input id="freight-min-weight" type="number" min="0" step="0.001" value={form.minimumWeightKg} onChange={(event) => setForm({ ...form, minimumWeightKg: event.target.value })} /></Field>
      <Field label={local(language, 'Peso máximo (kg)', 'Maximum weight (kg)')} htmlFor="freight-max-weight"><input id="freight-max-weight" type="number" min="0" step="0.001" value={form.maximumWeightKg} onChange={(event) => setForm({ ...form, maximumWeightKg: event.target.value })} /></Field>
      <Field label={local(language, 'Volumen mínimo (m³)', 'Minimum volume (m³)')} htmlFor="freight-min-volume"><input id="freight-min-volume" type="number" min="0" step="0.000001" value={form.minimumVolumeM3} onChange={(event) => setForm({ ...form, minimumVolumeM3: event.target.value })} /></Field>
      <Field label={local(language, 'Volumen máximo (m³)', 'Maximum volume (m³)')} htmlFor="freight-max-volume"><input id="freight-max-volume" type="number" min="0" step="0.000001" value={form.maximumVolumeM3} onChange={(event) => setForm({ ...form, maximumVolumeM3: event.target.value })} /></Field>
      <Field label={local(language, 'Distancia mínima (km)', 'Minimum distance (km)')} htmlFor="freight-min-distance"><input id="freight-min-distance" type="number" min="0" step="0.001" value={form.minimumDistanceKm} onChange={(event) => setForm({ ...form, minimumDistanceKm: event.target.value })} /></Field>
      <Field label={local(language, 'Distancia máxima (km)', 'Maximum distance (km)')} htmlFor="freight-max-distance"><input id="freight-max-distance" type="number" min="0" step="0.001" value={form.maximumDistanceKm} onChange={(event) => setForm({ ...form, maximumDistanceKm: event.target.value })} /></Field>
    </div>
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? local(language, 'Guardar tarifa', 'Save rate') : local(language, 'Crear tarifa', 'Create rate')} />
  </form>
}

function FreightSimulationForm({ carriers, routes, locale, onCancel }: { carriers: Carrier[]; routes: DeliveryRoute[]; locale: string; onCancel: () => void }) {
  const { language } = useTranslation()
  const [form, setForm] = useState({ pricingDate: today(), routeId: '', carrierId: '', currencyCode: 'EUR', weightKg: '', volumeM3: '', distanceKm: '' })
  const [quote, setQuote] = useState<FreightQuote | null>(null)
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const selectedRoute = routes.find((route) => route.id === form.routeId)
  const availableCarriers = selectedRoute?.carrierId ? carriers.filter((carrier) => carrier.id === selectedRoute.carrierId) : carriers
  const chooseRoute = (routeId: string) => {
    const route = routes.find((candidate) => candidate.id === routeId)
    setForm({ ...form, routeId, carrierId: route?.carrierId ?? form.carrierId })
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError(''); setQuote(null)
    try {
      const result = await apiFetch<FreightQuote>('/api/v1/freight-rates/simulate', { method: 'POST', body: JSON.stringify({ pricingDate: form.pricingDate, routeId: nullable(form.routeId), carrierId: nullable(form.carrierId), currencyCode: form.currencyCode.trim().toUpperCase(), weightKg: nullableNumber(form.weightKg), volumeM3: nullableNumber(form.volumeM3), distanceKm: nullableNumber(form.distanceKm) }) })
      setQuote(result)
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <div className="form-grid">
      <Field label={local(language, 'Fecha de tarificación', 'Pricing date')} htmlFor="simulation-date" required><input id="simulation-date" type="date" value={form.pricingDate} onChange={(event) => setForm({ ...form, pricingDate: event.target.value })} required /></Field>
      <Field label={local(language, 'Moneda ISO', 'ISO currency')} htmlFor="simulation-currency" required><input id="simulation-currency" value={form.currencyCode} onChange={(event) => setForm({ ...form, currencyCode: event.target.value })} pattern="[A-Za-z]{3}" minLength={3} maxLength={3} required /></Field>
      <Field label={local(language, 'Ruta', 'Route')} htmlFor="simulation-route"><select id="simulation-route" value={form.routeId} onChange={(event) => chooseRoute(event.target.value)}><option value="">{local(language, 'Sin ruta', 'No route')}</option>{routes.map((route) => <option key={route.id} value={route.id}>{route.code} · {route.name}</option>)}</select></Field>
      <Field label={local(language, 'Transportista', 'Carrier')} htmlFor="simulation-carrier"><select id="simulation-carrier" value={form.carrierId} onChange={(event) => setForm({ ...form, carrierId: event.target.value })}><option value="">{local(language, 'Sin transportista', 'No carrier')}</option>{availableCarriers.map((carrier) => <option key={carrier.id} value={carrier.id}>{carrier.code} · {carrier.name}</option>)}</select></Field>
      <Field label={local(language, 'Peso (kg)', 'Weight (kg)')} htmlFor="simulation-weight"><input id="simulation-weight" type="number" min="0" step="0.001" value={form.weightKg} onChange={(event) => setForm({ ...form, weightKg: event.target.value })} /></Field>
      <Field label={local(language, 'Volumen (m³)', 'Volume (m³)')} htmlFor="simulation-volume"><input id="simulation-volume" type="number" min="0" step="0.000001" value={form.volumeM3} onChange={(event) => setForm({ ...form, volumeM3: event.target.value })} /></Field>
      <Field label={local(language, 'Distancia (km)', 'Distance (km)')} htmlFor="simulation-distance"><input id="simulation-distance" type="number" min="0" step="0.001" value={form.distanceKm} onChange={(event) => setForm({ ...form, distanceKm: event.target.value })} /></Field>
    </div>
    {quote && <FreightQuoteDetail quote={quote} locale={locale} />}
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={local(language, 'Calcular flete', 'Calculate freight')} />
  </form>
}

function ShipmentFreightForm({ shipment, routes, locale, onCancel, onSaved }: { shipment: Shipment; routes: DeliveryRoute[]; locale: string; onCancel: () => void; onSaved: (shipment: Shipment) => void }) {
  const { language } = useTranslation()
  const assignedRoute = routes.find((route) => route.id === shipment.routeId)
  const [pricingDate, setPricingDate] = useState(shipment.freightPricingDateSnapshot ?? today())
  const [distanceKm, setDistanceKm] = useState(shipment.freightDistanceKmSnapshot == null ? '' : String(shipment.freightDistanceKmSnapshot))
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const updated = await apiFetch<Shipment>(`/api/v1/shipments/${shipment.id}/freight/resolve`, { method: 'POST', body: JSON.stringify({ pricingDate, distanceKm: nullableNumber(distanceKm) }) })
      onSaved(updated)
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <div className="detail-summary">
      <div><small>{local(language, 'Moneda', 'Currency')}</small><strong>{shipment.currencyCode}</strong><span>{local(language, 'La tarifa debe usar esta moneda', 'The rate must use this currency')}</span></div>
      <div><small>{local(language, 'Peso', 'Weight')}</small><strong>{shipment.totalWeightKg == null ? '—' : `${formatNumber(shipment.totalWeightKg, locale, 3)} kg`}</strong></div>
      <div><small>{local(language, 'Volumen', 'Volume')}</small><strong>{shipment.totalVolumeM3 == null ? '—' : `${formatNumber(shipment.totalVolumeM3, locale, 6)} m³`}</strong></div>
      <div><small>{local(language, 'Ámbito', 'Scope')}</small><strong>{assignedRoute ? `${assignedRoute.code} · ${assignedRoute.name}` : shipment.routeId ? local(language, 'Ruta asignada', 'Assigned route') : local(language, 'Tarifa general', 'General rate')}</strong><span>{assignedRoute?.distanceKm == null ? (shipment.carrierId ? local(language, 'Transportista asignado', 'Assigned carrier') : local(language, 'Sin transportista', 'No carrier')) : `${formatNumber(assignedRoute.distanceKm, locale, 3)} km`}</span></div>
    </div>
    <div className="form-grid">
      <Field label={local(language, 'Fecha de tarificación', 'Pricing date')} htmlFor="shipment-freight-date" required><input id="shipment-freight-date" type="date" value={pricingDate} onChange={(event) => setPricingDate(event.target.value)} required /></Field>
      <Field label={local(language, 'Distancia (km)', 'Distance (km)')} htmlFor="shipment-freight-distance" hint={local(language, 'Opcional: si se deja vacía, se usa la distancia configurada en la ruta. Para tarifas por kilómetro debe existir uno de los dos valores.', 'Optional: when left empty, the configured route distance is used. Per-kilometre rates require one of the two values.')}><input id="shipment-freight-distance" type="number" min="0.001" step="0.001" value={distanceKm} onChange={(event) => setDistanceKm(event.target.value)} /></Field>
    </div>
    {shipment.freightRateId && <div className="inline-error">{local(language, 'Al resolver de nuevo se sustituirá el snapshot de flete actual.', 'Resolving again will replace the current freight snapshot.')}</div>}
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={local(language, 'Resolver y aplicar tarifa', 'Resolve and apply rate')} />
  </form>
}

function FreightQuoteDetail({ quote, locale }: { quote: FreightQuote; locale: string }) {
  const { language } = useTranslation()
  return <div className="document-detail">
    <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Resultado', 'Result')}</span><h3>{quote.rateCode} · {quote.rateName}</h3></div></div>
    <div className="detail-summary">
      <div><small>{local(language, 'Total', 'Total')}</small><strong>{formatCurrency(quote.amount, quote.currencyCode, locale)}</strong><span>{freightMethodLabel(quote.calculationMethod, language)}</span></div>
      <div><small>{local(language, 'Componente fijo', 'Fixed component')}</small><strong>{formatCurrency(quote.fixedComponent, quote.currencyCode, locale)}</strong></div>
      <div><small>{local(language, 'Componente variable', 'Variable component')}</small><strong>{formatCurrency(quote.variableComponent, quote.currencyCode, locale)}</strong></div>
      <div><small>{local(language, 'Selección', 'Selection')}</small><strong>{quote.eligibleRateCount} {local(language, 'tarifas elegibles', 'eligible rates')}</strong><span>{quote.minimumApplied ? local(language, 'Aplicado mínimo', 'Minimum applied') : quote.maximumApplied ? local(language, 'Aplicado máximo', 'Maximum applied') : local(language, 'Sin límites aplicados', 'No limits applied')}</span></div>
    </div>
  </div>
}

interface ShipmentLineForm {
  productId: string
  productCodeSnapshot: string
  productNameSnapshot: string
  quantity: string
  unitOfMeasureSnapshot: string
  sourceDocumentId: string
  sourceDocumentType: string
  sourceDocumentNumberSnapshot: string
}

function ShipmentForm({ item, carriers, vehicles, routes, onCancel, onSaved }: { item?: Shipment; carriers: Carrier[]; vehicles: Vehicle[]; routes: DeliveryRoute[]; onCancel: () => void; onSaved: () => void }) {
  const { language } = useTranslation()
  const [form, setForm] = useState({
    shipmentNumber: item?.shipmentNumber ?? '', origin: item?.origin ?? '', destination: item?.destination ?? '',
    carrierId: item?.carrierId ?? '', vehicleId: item?.vehicleId ?? '', routeId: item?.routeId ?? '',
    plannedDepartureAt: toDateTimeInput(item?.plannedDepartureAt), plannedArrivalAt: toDateTimeInput(item?.plannedArrivalAt),
    freightCost: String(item?.freightCost ?? 0), currencyCode: item?.currencyCode ?? 'EUR',
    totalWeightKg: item?.totalWeightKg == null ? '' : String(item.totalWeightKg), totalVolumeM3: item?.totalVolumeM3 == null ? '' : String(item.totalVolumeM3),
  })
  const [lines, setLines] = useState<ShipmentLineForm[]>(() => item?.lines.map(shipmentLineToForm) ?? [emptyShipmentLine()])
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const matchingVehicles = vehicles.filter((vehicle) => !form.carrierId || !vehicle.carrierId || vehicle.carrierId === form.carrierId || vehicle.id === form.vehicleId)
  const updateLine = (index: number, field: keyof ShipmentLineForm, value: string) => setLines((current) => current.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line))
  const chooseCarrier = (carrierId: string) => {
    const currentVehicle = vehicles.find((vehicle) => vehicle.id === form.vehicleId)
    setForm({ ...form, carrierId, vehicleId: currentVehicle?.carrierId && currentVehicle.carrierId !== carrierId ? '' : form.vehicleId })
  }
  const chooseRoute = (routeId: string) => {
    const route = routes.find((candidate) => candidate.id === routeId)
    if (!route) { setForm({ ...form, routeId }); return }
    setForm({ ...form, routeId, carrierId: route.carrierId ?? form.carrierId, vehicleId: route.vehicleId ?? form.vehicleId,
      origin: route.origin, destination: route.destination,
      plannedDepartureAt: toDateTimeInput(route.plannedDepartureAt), plannedArrivalAt: toDateTimeInput(route.plannedArrivalAt) })
  }
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    if (!validChronology(form.plannedDepartureAt, form.plannedArrivalAt)) { setError(local(language, 'La llegada prevista no puede ser anterior a la salida.', 'Planned arrival cannot be earlier than departure.')); setSaving(false); return }
    if (lines.some((line) => !line.productNameSnapshot.trim() || Number(line.quantity) <= 0 || !line.unitOfMeasureSnapshot.trim())) { setError(local(language, 'Todas las líneas necesitan descripción, cantidad positiva y unidad.', 'Every line needs a description, a positive quantity and a unit.')); setSaving(false); return }
    if (lines.some((line) => line.productId.trim() && !line.productCodeSnapshot.trim())) { setError(local(language, 'Una línea con ID de producto también necesita su código snapshot.', 'A line with a product ID also needs its code snapshot.')); setSaving(false); return }
    if (lines.some((line) => {
      const documentFields = [line.sourceDocumentId, line.sourceDocumentType, line.sourceDocumentNumberSnapshot].filter((value) => value.trim()).length
      return documentFields !== 0 && documentFields !== 3
    })) { setError(local(language, 'La referencia documental de cada línea debe incluir ID, tipo y número, o dejar los tres campos vacíos.', 'Each line document reference must include ID, type and number, or leave all three fields empty.')); setSaving(false); return }
    const body = {
      shipmentNumber: form.shipmentNumber.trim(), origin: nullable(form.origin), destination: nullable(form.destination),
      carrierId: nullable(form.carrierId), vehicleId: nullable(form.vehicleId), routeId: nullable(form.routeId),
      plannedDepartureAt: toInstant(form.plannedDepartureAt), plannedArrivalAt: toInstant(form.plannedArrivalAt),
      freightCost: Number(form.freightCost), currencyCode: form.currencyCode.trim().toUpperCase(),
      totalWeightKg: nullableNumber(form.totalWeightKg), totalVolumeM3: nullableNumber(form.totalVolumeM3),
      lines: lines.map((line, index) => ({ sequence: index + 1, productId: nullable(line.productId), productCodeSnapshot: nullable(line.productCodeSnapshot), productNameSnapshot: line.productNameSnapshot.trim(), quantity: Number(line.quantity), unitOfMeasureSnapshot: line.unitOfMeasureSnapshot.trim(), sourceDocumentId: nullable(line.sourceDocumentId), sourceDocumentType: nullable(line.sourceDocumentType), sourceDocumentNumberSnapshot: nullable(line.sourceDocumentNumberSnapshot) })),
    }
    try { await apiFetch<Shipment>(item ? `/api/v1/shipments/${item.id}` : '/api/v1/shipments', { method: item ? 'PUT' : 'POST', body: JSON.stringify(body) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <div className="form-grid">
      <Field label={local(language, 'Número de expedición', 'Shipment number')} htmlFor="shipment-number" required><input id="shipment-number" value={form.shipmentNumber} onChange={(event) => setForm({ ...form, shipmentNumber: event.target.value })} pattern="[A-Za-z0-9][A-Za-z0-9_./-]{0,79}" maxLength={80} disabled={Boolean(item)} required /></Field>
      <Field label={local(language, 'Ruta', 'Route')} htmlFor="shipment-route"><select id="shipment-route" value={form.routeId} onChange={(event) => chooseRoute(event.target.value)}><option value="">{local(language, 'Sin ruta predefinida', 'No predefined route')}</option>{routes.filter((route) => route.active || route.id === form.routeId).map((route) => <option key={route.id} value={route.id}>{route.code} · {route.name}</option>)}</select></Field>
      <Field label={local(language, 'Origen', 'Origin')} htmlFor="shipment-origin"><input id="shipment-origin" value={form.origin} onChange={(event) => setForm({ ...form, origin: event.target.value })} maxLength={500} /></Field>
      <Field label={local(language, 'Destino', 'Destination')} htmlFor="shipment-destination"><input id="shipment-destination" value={form.destination} onChange={(event) => setForm({ ...form, destination: event.target.value })} maxLength={500} /></Field>
      <Field label={local(language, 'Transportista', 'Carrier')} htmlFor="shipment-carrier"><select id="shipment-carrier" value={form.carrierId} onChange={(event) => chooseCarrier(event.target.value)}><option value="">{local(language, 'Sin asignar', 'Unassigned')}</option>{carriers.filter((carrier) => carrier.active || carrier.id === form.carrierId).map((carrier) => <option key={carrier.id} value={carrier.id}>{carrier.code} · {carrier.name}</option>)}</select></Field>
      <Field label={local(language, 'Vehículo', 'Vehicle')} htmlFor="shipment-vehicle"><select id="shipment-vehicle" value={form.vehicleId} onChange={(event) => setForm({ ...form, vehicleId: event.target.value })}><option value="">{local(language, 'Sin asignar', 'Unassigned')}</option>{matchingVehicles.filter((vehicle) => vehicle.active || vehicle.id === form.vehicleId).map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.code} · {vehicle.registrationPlate ?? vehicle.vehicleType}</option>)}</select></Field>
      <Field label={local(language, 'Salida prevista', 'Planned departure')} htmlFor="shipment-departure"><input id="shipment-departure" type="datetime-local" value={form.plannedDepartureAt} onChange={(event) => setForm({ ...form, plannedDepartureAt: event.target.value })} /></Field>
      <Field label={local(language, 'Llegada prevista', 'Planned arrival')} htmlFor="shipment-arrival"><input id="shipment-arrival" type="datetime-local" value={form.plannedArrivalAt} onChange={(event) => setForm({ ...form, plannedArrivalAt: event.target.value })} /></Field>
      <Field label={local(language, 'Coste de transporte', 'Freight cost')} htmlFor="shipment-cost" required><input id="shipment-cost" type="number" min="0" step="0.0001" value={form.freightCost} onChange={(event) => setForm({ ...form, freightCost: event.target.value })} required /></Field>
      <Field label={local(language, 'Moneda ISO', 'ISO currency')} htmlFor="shipment-currency" required><input id="shipment-currency" value={form.currencyCode} onChange={(event) => setForm({ ...form, currencyCode: event.target.value })} pattern="[A-Za-z]{3}" minLength={3} maxLength={3} required /></Field>
      <Field label={local(language, 'Peso total (kg)', 'Total weight (kg)')} htmlFor="shipment-weight"><input id="shipment-weight" type="number" min="0" step="0.001" value={form.totalWeightKg} onChange={(event) => setForm({ ...form, totalWeightKg: event.target.value })} /></Field>
      <Field label={local(language, 'Volumen total (m³)', 'Total volume (m³)')} htmlFor="shipment-volume"><input id="shipment-volume" type="number" min="0" step="0.000001" value={form.totalVolumeM3} onChange={(event) => setForm({ ...form, totalVolumeM3: event.target.value })} /></Field>
    </div>
    <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Contenido', 'Contents')}</span><h3>{local(language, 'Items y documentos de origen', 'Items and source documents')}</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setLines((current) => [...current, emptyShipmentLine()])}><Plus size={15} />{local(language, 'Añadir línea', 'Add line')}</button></div>
    <div className="line-editor">{lines.map((line, index) => <div className="panel" key={index}>
      <div className="form-grid">
        <Field label={`${local(language, 'Descripción del item', 'Item description')} ${index + 1}`} htmlFor={`shipment-line-name-${index}`} required><input id={`shipment-line-name-${index}`} value={line.productNameSnapshot} onChange={(event) => updateLine(index, 'productNameSnapshot', event.target.value)} maxLength={300} required /></Field>
        <Field label={local(language, 'Cantidad', 'Quantity')} htmlFor={`shipment-line-quantity-${index}`} required><input id={`shipment-line-quantity-${index}`} type="number" min="0.000001" step="0.000001" value={line.quantity} onChange={(event) => updateLine(index, 'quantity', event.target.value)} required /></Field>
        <Field label={local(language, 'Unidad', 'Unit')} htmlFor={`shipment-line-unit-${index}`} required><input id={`shipment-line-unit-${index}`} value={line.unitOfMeasureSnapshot} onChange={(event) => updateLine(index, 'unitOfMeasureSnapshot', event.target.value)} maxLength={30} placeholder="UNIT, KG, BOX…" required /></Field>
        <Field label={local(language, 'ID de producto', 'Product ID')} htmlFor={`shipment-line-product-id-${index}`}><input id={`shipment-line-product-id-${index}`} value={line.productId} onChange={(event) => updateLine(index, 'productId', event.target.value)} maxLength={36} /></Field>
        <Field label={local(language, 'Código snapshot', 'Code snapshot')} htmlFor={`shipment-line-product-code-${index}`}><input id={`shipment-line-product-code-${index}`} value={line.productCodeSnapshot} onChange={(event) => updateLine(index, 'productCodeSnapshot', event.target.value)} maxLength={100} /></Field>
        <Field label={local(language, 'ID de documento origen', 'Source document ID')} htmlFor={`shipment-line-document-id-${index}`}><input id={`shipment-line-document-id-${index}`} value={line.sourceDocumentId} onChange={(event) => updateLine(index, 'sourceDocumentId', event.target.value)} maxLength={36} /></Field>
        <Field label={local(language, 'Tipo de documento', 'Document type')} htmlFor={`shipment-line-document-type-${index}`}><input id={`shipment-line-document-type-${index}`} value={line.sourceDocumentType} onChange={(event) => updateLine(index, 'sourceDocumentType', event.target.value)} maxLength={80} placeholder="SALES_ORDER" /></Field>
        <Field label={local(language, 'Número snapshot', 'Number snapshot')} htmlFor={`shipment-line-document-number-${index}`}><input id={`shipment-line-document-number-${index}`} value={line.sourceDocumentNumberSnapshot} onChange={(event) => updateLine(index, 'sourceDocumentNumberSnapshot', event.target.value)} maxLength={100} /></Field>
      </div><div className="row-actions"><button className="button button-ghost button-small" type="button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, lineIndex) => lineIndex !== index))}><Trash2 size={15} />{local(language, 'Quitar línea', 'Remove line')}</button></div>
    </div>)}</div>
    {item?.freightRateId && <div className="inline-error">{local(language, 'Guardar cambios en el plan eliminará el snapshot de tarifa actual; vuelve a resolver el flete después.', 'Saving plan changes will clear the current rate snapshot; resolve freight again afterwards.')}</div>}
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={item ? local(language, 'Guardar expedición', 'Save shipment') : local(language, 'Crear expedición', 'Create shipment')} />
  </form>
}

function ShipmentDocumentForm({ shipment, onCancel, onSaved }: { shipment: Shipment; onCancel: () => void; onSaved: () => void | Promise<void> }) {
  const { language } = useTranslation()
  const [documentType, setDocumentType] = useState('DELIVERY_NOTE')
  const [file, setFile] = useState<File | null>(null)
  const [saving, setSaving] = useState(false); const [error, setError] = useState('')
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('')
    if (!file) { setError(local(language, 'Selecciona un archivo.', 'Select a file.')); setSaving(false); return }
    try {
      const body = new FormData()
      body.append('documentType', documentType.trim())
      body.append('file', file, file.name)
      await apiFetch<ShipmentDocument>(`/api/v1/shipments/${shipment.id}/documents/upload`, { method: 'POST', body })
      await onSaved()
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}><div className="form-grid">
    <Field label={local(language, 'Tipo documental', 'Document type')} htmlFor="shipment-document-type" required><input id="shipment-document-type" value={documentType} onChange={(event) => setDocumentType(event.target.value)} pattern="[A-Za-z0-9][A-Za-z0-9_.-]{0,79}" maxLength={80} required /></Field>
    <Field label={local(language, 'Archivo', 'File')} htmlFor="shipment-document-file" required wide hint={local(language, 'Formatos admitidos: PDF, PNG, JPEG, WEBP, TXT, CSV, JSON y XML.', 'Allowed formats: PDF, PNG, JPEG, WEBP, TXT, CSV, JSON and XML.')}><input id="shipment-document-file" type="file" accept="application/pdf,image/png,image/jpeg,image/webp,text/plain,text/csv,application/json,application/xml,text/xml" onChange={(event) => setFile(event.target.files?.[0] ?? null)} required /></Field>
  </div>{file && <div className="detail-summary"><div><small>{local(language, 'Archivo seleccionado', 'Selected file')}</small><strong>{file.name}</strong><span>{formatBytes(file.size, language === 'en' ? 'en-GB' : 'es-ES')} · {file.type || local(language, 'tipo desconocido', 'unknown type')}</span></div></div>}{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel={local(language, 'Subir documento', 'Upload document')} /></form>
}

function TemplateDetail({ template, locale, onEdit, onAction, onDelete }: { template: WorkflowTemplate; locale: string; onEdit: () => void; onAction: (action: 'publish' | 'version' | 'retire') => void; onDelete: () => void }) {
  const { language } = useTranslation()
  return <div className="document-detail">
    <div className="detail-summary">
      <div><small>{local(language, 'Estado', 'Status')}</small><StatusBadge tone={statusTone(template.status)}>{templateLabels[language][template.status]}</StatusBadge><span>{local(language, 'Versión', 'Version')} {template.templateVersion}</span></div>
      <div><small>{local(language, 'Referencia', 'Reference')}</small><strong>{template.referenceType}</strong><span>{local(language, 'Tipo de objeto asociado', 'Associated object type')}</span></div>
      <div><small>{local(language, 'Pasos', 'Steps')}</small><strong>{template.steps.length}</strong><span>{template.steps.filter((step) => step.required).length} {local(language, 'obligatorios', 'required')}</span></div>
      <div><small>{local(language, 'Actualizada', 'Updated')}</small><strong>{formatDateTime(template.updatedAt, locale)}</strong><span>{local(language, 'Creada', 'Created')} {formatDateTime(template.createdAt, locale)}</span></div>
    </div>
    <div className="table-scroll detail-lines"><table><thead><tr><th>#</th><th>{local(language, 'Paso', 'Step')}</th><th>{local(language, 'Descripción', 'Description')}</th><th>{local(language, 'Obligatorio', 'Required')}</th><th className="align-right">{local(language, 'Estimación', 'Estimate')}</th></tr></thead><tbody>{template.steps.map((step) => <tr key={step.id}><td>{step.sequence}</td><td><strong>{step.name}</strong><small>{step.code}</small></td><td>{step.description ?? '—'}</td><td>{step.required ? local(language, 'Sí', 'Yes') : local(language, 'No', 'No')}</td><td className="align-right">{step.estimatedMinutes == null ? '—' : `${step.estimatedMinutes} min`}</td></tr>)}</tbody></table></div>
    <div className="modal-action-strip">
      {template.status === 'DRAFT' && <><button className="button button-ghost" type="button" onClick={onDelete}><Trash2 size={16} />{local(language, 'Eliminar', 'Delete')}</button><button className="button button-secondary" type="button" onClick={onEdit}><Pencil size={16} />{local(language, 'Editar', 'Edit')}</button><button className="button button-primary" type="button" onClick={() => onAction('publish')}><Send size={16} />{local(language, 'Publicar', 'Publish')}</button></>}
      {template.status !== 'DRAFT' && <button className="button button-secondary" type="button" onClick={() => onAction('version')}><Copy size={16} />{local(language, 'Crear nueva versión', 'Create new version')}</button>}
      {template.status === 'PUBLISHED' && <button className="button button-primary" type="button" onClick={() => onAction('retire')}><Archive size={16} />{local(language, 'Retirar versión', 'Retire version')}</button>}
    </div>
  </div>
}

function ExecutionDetail({ execution, locale, onAction }: { execution: WorkExecution; locale: string; onAction: (step: WorkStep, action: 'start' | 'complete' | 'skip' | 'cancel') => void }) {
  const { language } = useTranslation()
  return <div className="document-detail">
    <div className="detail-summary">
      <div><small>{local(language, 'Estado', 'Status')}</small><StatusBadge tone={statusTone(execution.status)}>{executionLabels[language][execution.status]}</StatusBadge><span>{finishedSteps(execution)}/{execution.steps.length} {local(language, 'pasos finalizados', 'steps finished')}</span></div>
      <div><small>{local(language, 'Inicio', 'Started')}</small><strong>{formatDateTime(execution.startedAt, locale)}</strong><span>{execution.status === 'COMPLETED' ? `${local(language, 'Fin', 'Finished')} ${formatDateTime(execution.completedAt, locale)}` : execution.status === 'CANCELLED' ? `${local(language, 'Cancelada', 'Cancelled')} ${formatDateTime(execution.cancelledAt, locale)}` : local(language, 'Ejecución abierta', 'Open execution')}</span></div>
      <div><small>{local(language, 'Plantilla', 'Template')}</small><strong>{execution.templateName}</strong><span>{execution.templateCode} · v{execution.templateVersion}</span></div>
      <div><small>{local(language, 'Referencia', 'Reference')}</small><strong>{execution.referenceType}</strong><span>{execution.referenceId}</span></div>
    </div>
    <div className="table-scroll detail-lines"><table><thead><tr><th>#</th><th>{local(language, 'Paso', 'Step')}</th><th>{local(language, 'Estado', 'Status')}</th><th>{local(language, 'Seguimiento', 'Tracking')}</th><th>{local(language, 'Acciones', 'Actions')}</th></tr></thead><tbody>{execution.steps.map((step, index) => {
      const predecessorsFinished = execution.steps.slice(0, index).every((candidate) => isTerminalStep(candidate.status))
      const active = execution.status !== 'COMPLETED' && execution.status !== 'CANCELLED'
      return <tr key={step.id}><td>{step.sequence}</td><td><strong>{step.name}</strong><small>{step.required ? local(language, 'Obligatorio', 'Required') : local(language, 'Opcional', 'Optional')}{step.estimatedMinutes == null ? '' : ` · ${step.estimatedMinutes} min`}</small></td><td><StatusBadge tone={statusTone(step.status)}>{stepLabels[language][step.status]}</StatusBadge></td><td>{step.note ?? '—'}<small>{step.finishedAt ? formatDateTime(step.finishedAt, locale) : step.startedAt ? `${local(language, 'Inicio', 'Started')} ${formatDateTime(step.startedAt, locale)}` : ''}</small></td><td><div className="row-actions">
        {active && step.status === 'PENDING' && predecessorsFinished && <button className="button button-secondary button-small" type="button" onClick={() => onAction(step, 'start')}><Play size={14} />{local(language, 'Iniciar', 'Start')}</button>}
        {active && step.status === 'PENDING' && predecessorsFinished && !step.required && <button className="button button-ghost button-small" type="button" onClick={() => onAction(step, 'skip')}><SkipForward size={14} />{local(language, 'Omitir', 'Skip')}</button>}
        {active && step.status === 'IN_PROGRESS' && <button className="button button-primary button-small" type="button" onClick={() => onAction(step, 'complete')}><CheckCircle2 size={14} />{local(language, 'Completar', 'Complete')}</button>}
        {active && !isTerminalStep(step.status) && <button className="icon-button" type="button" onClick={() => onAction(step, 'cancel')} aria-label={local(language, 'Cancelar ejecución desde este paso', 'Cancel execution from this step')}><XCircle size={16} /></button>}
      </div></td></tr>
    })}</tbody></table></div>
  </div>
}

function RouteDetail({ route, locale, carriers, vehicles, onEdit, onDelete }: { route: DeliveryRoute; locale: string; carriers: Carrier[]; vehicles: Vehicle[]; onEdit: () => void; onDelete: () => void }) {
  const { language } = useTranslation()
  return <div className="document-detail">
    <div className="detail-summary">
      <div><small>{local(language, 'Estado', 'Status')}</small><StatusBadge tone={route.active ? 'success' : 'neutral'}>{route.active ? local(language, 'Activa', 'Active') : local(language, 'Inactiva', 'Inactive')}</StatusBadge><span>{route.code}</span></div>
      <div><small>{local(language, 'Transportista', 'Carrier')}</small><strong>{carrierLabel(route.carrierId, carriers, language)}</strong><span>{vehicleLabel(route.vehicleId, vehicles, language)}</span></div>
      <div><small>{local(language, 'Plan', 'Schedule')}</small><strong>{formatDateTime(route.plannedDepartureAt, locale)}</strong><span>{local(language, 'Llegada', 'Arrival')} {formatDateTime(route.plannedArrivalAt, locale)}</span></div>
      <div><small>{local(language, 'Ventana de entrega', 'Delivery window')}</small><strong>{formatDateTime(route.deliveryWindowStart, locale)}</strong><span>{local(language, 'Hasta', 'Until')} {formatDateTime(route.deliveryWindowEnd, locale)}</span></div>
      <div><small>{local(language, 'Distancia y duración', 'Distance and duration')}</small><strong>{route.distanceKm == null ? '—' : `${formatNumber(route.distanceKm, locale, 3)} km`}</strong><span>{route.estimatedDurationMinutes == null ? local(language, 'Duración no indicada', 'Duration not provided') : `${formatNumber(route.estimatedDurationMinutes, locale, 0)} min`}</span></div>
    </div>
    {route.stops.length > 0 ? <div className="table-scroll detail-lines"><table><thead><tr><th>#</th><th>{local(language, 'Parada', 'Stop')}</th><th>{local(language, 'Ubicación', 'Location')}</th><th>{local(language, 'Ventana', 'Window')}</th><th>{local(language, 'Instrucciones', 'Instructions')}</th></tr></thead><tbody>{route.stops.map((stop) => <tr key={stop.id}><td>{stop.sequence}</td><td><strong>{stop.name}</strong></td><td>{stop.location}</td><td>{formatDateTime(stop.windowStart, locale)}<small>{formatDateTime(stop.windowEnd, locale)}</small></td><td>{stop.instructions ?? '—'}</td></tr>)}</tbody></table></div> : <EmptyState title={local(language, 'Ruta directa', 'Direct route')} description={local(language, 'El itinerario no contiene paradas intermedias.', 'The itinerary has no intermediate stops.')} />}
    <div className="modal-action-strip"><button className="button button-ghost" type="button" onClick={onDelete}><Trash2 size={16} />{local(language, 'Eliminar', 'Delete')}</button><button className="button button-primary" type="button" onClick={onEdit}><Pencil size={16} />{local(language, 'Editar ruta', 'Edit route')}</button></div>
  </div>
}

type ShipmentTransition = 'start-packing' | 'mark-ready' | 'dispatch' | 'mark-in-transit' | 'arrive' | 'deliver' | 'report-exception' | 'resolve-exception' | 'cancel'

function ShipmentDetail({ shipment, locale, carriers, vehicles, routes, onEdit, onAddDocument, onResolveFreight, onDownloadDocument, onDeleteDocument, onTransition, onDelete }: {
  shipment: Shipment; locale: string; carriers: Carrier[]; vehicles: Vehicle[]; routes: DeliveryRoute[]
  onEdit: () => void; onAddDocument: () => void; onResolveFreight: () => void; onDownloadDocument: (document: ShipmentDocument) => void
  onDeleteDocument: (id: string) => void; onTransition: (action: ShipmentTransition) => void; onDelete: () => void
}) {
  const { language } = useTranslation()
  const transitions = availableShipmentTransitions(shipment.status)
  const editable = shipment.status === 'PLANNED' || shipment.status === 'PACKING' || shipment.status === 'READY'
  return <div className="document-detail">
    <div className="detail-summary">
      <div><small>{local(language, 'Estado', 'Status')}</small><StatusBadge tone={statusTone(shipment.status)}>{shipmentLabels[language][shipment.status]}</StatusBadge><span>{shipment.statusNote ?? (shipment.statusBeforeException ? `${local(language, 'Estado anterior', 'Previous status')}: ${shipmentLabels[language][shipment.statusBeforeException]}` : local(language, 'Sin incidencias', 'No exceptions'))}</span></div>
      <div><small>{local(language, 'Asignación', 'Assignment')}</small><strong>{carrierLabel(shipment.carrierId, carriers, language)}</strong><span>{vehicleLabel(shipment.vehicleId, vehicles, language)} · {routeLabel(shipment.routeId, routes, language)}</span></div>
      <div><small>{local(language, 'Plan', 'Schedule')}</small><strong>{formatDateTime(shipment.plannedDepartureAt, locale)}</strong><span>{local(language, 'Llegada', 'Arrival')} {formatDateTime(shipment.plannedArrivalAt, locale)}</span></div>
      <div><small>{local(language, 'Coste', 'Cost')}</small><strong>{formatCurrency(shipment.freightCost, shipment.currencyCode, locale)}</strong><span>{shipment.totalWeightKg == null ? local(language, 'Peso pendiente', 'Weight pending') : `${formatNumber(shipment.totalWeightKg, locale, 3)} kg`} · {shipment.totalVolumeM3 == null ? local(language, 'Volumen pendiente', 'Volume pending') : `${formatNumber(shipment.totalVolumeM3, locale, 6)} m³`}</span></div>
    </div>
    {(shipment.actualDepartureAt || shipment.actualArrivalAt || shipment.deliveredAt) && <div className="detail-summary">
      <div><small>{local(language, 'Salida real', 'Actual departure')}</small><strong>{formatDateTime(shipment.actualDepartureAt, locale)}</strong></div>
      <div><small>{local(language, 'Llegada real', 'Actual arrival')}</small><strong>{formatDateTime(shipment.actualArrivalAt, locale)}</strong></div>
      <div><small>{local(language, 'Entrega', 'Delivery')}</small><strong>{formatDateTime(shipment.deliveredAt, locale)}</strong></div>
    </div>}
    {shipment.freightRateId && <>
      <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Tarificación', 'Pricing')}</span><h3>{local(language, 'Snapshot del flete aplicado', 'Applied freight snapshot')}</h3></div></div>
      <div className="detail-summary">
        <div><small>{local(language, 'Tarifa', 'Rate')}</small><strong>{shipment.freightRateCodeSnapshot}</strong><span>{shipment.freightRateNameSnapshot}</span></div>
        <div><small>{local(language, 'Método y fecha', 'Method and date')}</small><strong>{shipment.freightMethodSnapshot ? freightMethodLabel(shipment.freightMethodSnapshot, language) : '—'}</strong><span>{shipment.freightPricingDateSnapshot ?? '—'}</span></div>
        <div><small>{local(language, 'Desglose', 'Breakdown')}</small><strong>{formatCurrency(shipment.freightFixedComponentSnapshot, shipment.currencyCode, locale)}</strong><span>{local(language, 'Variable', 'Variable')} {formatCurrency(shipment.freightVariableComponentSnapshot, shipment.currencyCode, locale)}</span></div>
        <div><small>{local(language, 'Distancia y límites', 'Distance and limits')}</small><strong>{shipment.freightDistanceKmSnapshot == null ? '—' : `${formatNumber(shipment.freightDistanceKmSnapshot, locale, 3)} km`}</strong><span>{shipment.freightMinimumAppliedSnapshot ? local(language, 'Mínimo aplicado', 'Minimum applied') : shipment.freightMaximumAppliedSnapshot ? local(language, 'Máximo aplicado', 'Maximum applied') : local(language, 'Sin límites aplicados', 'No limits applied')}</span></div>
      </div>
    </>}
    <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Contenido', 'Contents')}</span><h3>{local(language, 'Items de la expedición', 'Shipment items')}</h3></div></div>
    <div className="table-scroll detail-lines"><table><thead><tr><th>#</th><th>{local(language, 'Artículo', 'Item')}</th><th className="align-right">{local(language, 'Cantidad', 'Quantity')}</th><th>{local(language, 'Documento origen', 'Source document')}</th></tr></thead><tbody>{shipment.lines.map((line) => <tr key={line.id}><td>{line.sequence}</td><td><strong>{line.productNameSnapshot}</strong><small>{line.productCodeSnapshot ?? line.productId ?? ''}</small></td><td className="align-right">{formatNumber(line.quantity, locale, 6)} {line.unitOfMeasureSnapshot}</td><td>{line.sourceDocumentNumberSnapshot ?? '—'}<small>{line.sourceDocumentType ?? ''}{line.sourceDocumentId ? ` · ${line.sourceDocumentId}` : ''}</small></td></tr>)}</tbody></table></div>
    <div className="document-lines-heading"><div><span className="eyebrow">{local(language, 'Trazabilidad', 'Traceability')}</span><h3>{local(language, 'Documentos vinculados', 'Linked documents')}</h3></div>{shipment.status !== 'CANCELLED' && <button className="button button-secondary button-small" type="button" onClick={onAddDocument}><FilePlus2 size={15} />{local(language, 'Subir documento', 'Upload document')}</button>}</div>
    {shipment.documents.length > 0 ? <div className="table-scroll detail-lines"><table><thead><tr><th>{local(language, 'Tipo', 'Type')}</th><th>{local(language, 'Archivo', 'File')}</th><th>{local(language, 'Formato', 'Format')}</th><th>{local(language, 'Tamaño', 'Size')}</th><th>{local(language, 'Alta', 'Created')}</th><th aria-label={local(language, 'Acciones', 'Actions')} /></tr></thead><tbody>{shipment.documents.map((document) => <tr key={document.id}><td><strong>{document.documentType}</strong></td><td>{document.originalFileName}</td><td>{document.mediaType}<small>SHA-256 {document.sha256.slice(0, 12)}…</small></td><td>{formatBytes(document.sizeBytes, locale)}</td><td>{formatDateTime(document.createdAt, locale)}</td><td><div className="row-actions"><button className="icon-button" type="button" onClick={() => onDownloadDocument(document)} aria-label={`${local(language, 'Descargar', 'Download')} ${document.originalFileName}`}><Download size={16} /></button>{shipment.status !== 'CANCELLED' && <button className="icon-button" type="button" onClick={() => onDeleteDocument(document.id)} aria-label={`${local(language, 'Eliminar', 'Delete')} ${document.originalFileName}`}><Trash2 size={16} /></button>}</div></td></tr>)}</tbody></table></div> : <EmptyState title={local(language, 'Sin documentos vinculados', 'No linked documents')} description={local(language, 'Sube albaranes, comprobantes u otros documentos de trazabilidad.', 'Upload delivery notes, receipts or other traceability documents.')} />}
    <div className="modal-action-strip">
      {shipment.status === 'PLANNED' && <button className="button button-ghost" type="button" onClick={onDelete}><Trash2 size={16} />{local(language, 'Eliminar', 'Delete')}</button>}
      {editable && <button className="button button-secondary" type="button" onClick={onEdit}><Pencil size={16} />{local(language, 'Editar plan', 'Edit plan')}</button>}
      {editable && <button className="button button-secondary" type="button" onClick={onResolveFreight}><RefreshCw size={16} />{local(language, 'Resolver flete', 'Resolve freight')}</button>}
      {transitions.map((action) => <button className={transitionPrimary(action) ? 'button button-primary' : 'button button-secondary'} type="button" key={action} onClick={() => onTransition(action)}>{transitionIcon(action)}{transitionLabel(action, language)}</button>)}
    </div>
  </div>
}

function availableShipmentTransitions(status: ShipmentStatus): ShipmentTransition[] {
  if (status === 'PLANNED') return ['start-packing', 'report-exception', 'cancel']
  if (status === 'PACKING') return ['mark-ready', 'report-exception', 'cancel']
  if (status === 'READY') return ['dispatch', 'report-exception', 'cancel']
  if (status === 'DISPATCHED') return ['mark-in-transit', 'arrive', 'report-exception']
  if (status === 'IN_TRANSIT') return ['arrive', 'report-exception']
  if (status === 'ARRIVED') return ['deliver', 'report-exception']
  if (status === 'EXCEPTION') return ['resolve-exception', 'cancel']
  return []
}

function transitionLabel(action: ShipmentTransition, language: 'es' | 'en') {
  const labels = {
    es: { 'start-packing': 'Iniciar preparación', 'mark-ready': 'Marcar preparada', dispatch: 'Registrar salida', 'mark-in-transit': 'En tránsito', arrive: 'Registrar llegada', deliver: 'Confirmar entrega', 'report-exception': 'Informar incidencia', 'resolve-exception': 'Resolver incidencia', cancel: 'Cancelar' },
    en: { 'start-packing': 'Start packing', 'mark-ready': 'Mark ready', dispatch: 'Record departure', 'mark-in-transit': 'Mark in transit', arrive: 'Record arrival', deliver: 'Confirm delivery', 'report-exception': 'Report exception', 'resolve-exception': 'Resolve exception', cancel: 'Cancel' },
  } satisfies Record<'es' | 'en', Record<ShipmentTransition, string>>
  return labels[language][action]
}

function transitionIcon(action: ShipmentTransition) {
  if (action === 'report-exception') return <AlertTriangle size={16} />
  if (action === 'resolve-exception') return <RefreshCw size={16} />
  if (action === 'cancel') return <XCircle size={16} />
  if (action === 'dispatch' || action === 'mark-in-transit') return <Truck size={16} />
  if (action === 'deliver') return <PackageCheck size={16} />
  return <CheckCircle2 size={16} />
}

function transitionPrimary(action: ShipmentTransition) {
  return action === 'start-packing' || action === 'mark-ready' || action === 'dispatch' || action === 'mark-in-transit' || action === 'arrive' || action === 'deliver' || action === 'resolve-exception'
}

function statusTone(status: TemplateStatus | ExecutionStatus | StepStatus | ShipmentStatus): BadgeTone {
  if (status === 'PUBLISHED' || status === 'COMPLETED' || status === 'DELIVERED') return 'success'
  if (status === 'IN_PROGRESS' || status === 'PACKING' || status === 'READY' || status === 'DISPATCHED' || status === 'IN_TRANSIT' || status === 'ARRIVED') return 'info'
  if (status === 'EXCEPTION') return 'warning'
  if (status === 'CANCELLED' || status === 'RETIRED') return 'danger'
  return 'neutral'
}

function isTerminalStep(status: StepStatus) {
  return status === 'COMPLETED' || status === 'SKIPPED' || status === 'CANCELLED'
}

function finishedSteps(execution: WorkExecution) {
  return execution.steps.filter((step) => isTerminalStep(step.status)).length
}

function carrierLabel(id: string | null, carriers: Carrier[], language: 'es' | 'en') {
  if (!id) return local(language, 'Sin transportista', 'No carrier')
  const carrier = carriers.find((item) => item.id === id)
  return carrier ? `${carrier.code} · ${carrier.name}` : id
}

function vehicleLabel(id: string | null, vehicles: Vehicle[], language: 'es' | 'en') {
  if (!id) return local(language, 'Sin vehículo', 'No vehicle')
  const vehicle = vehicles.find((item) => item.id === id)
  return vehicle ? `${vehicle.code} · ${vehicle.registrationPlate ?? vehicle.vehicleType}` : id
}

function routeLabel(id: string | null, routes: DeliveryRoute[], language: 'es' | 'en') {
  if (!id) return local(language, 'Sin ruta', 'No route')
  const route = routes.find((item) => item.id === id)
  return route ? `${route.code} · ${route.name}` : id
}

function tabLabel(tab: Tab, language: 'es' | 'en') {
  const labels = {
    es: { templates: 'Plantillas', executions: 'Ejecuciones', carriers: 'Transportistas', vehicles: 'Vehículos', routes: 'Rutas', freight: 'Fletes', shipments: 'Expediciones' },
    en: { templates: 'Templates', executions: 'Executions', carriers: 'Carriers', vehicles: 'Vehicles', routes: 'Routes', freight: 'Freight', shipments: 'Shipments' },
  } satisfies Record<'es' | 'en', Record<Tab, string>>
  return labels[language][tab]
}

function createLabel(tab: Tab, language: 'es' | 'en') {
  const labels = {
    es: { templates: 'Nueva plantilla', executions: 'Nueva ejecución', carriers: 'Nuevo transportista', vehicles: 'Nuevo vehículo', routes: 'Nueva ruta', freight: 'Nueva tarifa', shipments: 'Nueva expedición' },
    en: { templates: 'New template', executions: 'New execution', carriers: 'New carrier', vehicles: 'New vehicle', routes: 'New route', freight: 'New rate', shipments: 'New shipment' },
  } satisfies Record<'es' | 'en', Record<Tab, string>>
  return labels[language][tab]
}

function searchPlaceholder(tab: Tab, language: 'es' | 'en') {
  const labels = {
    es: { templates: 'Buscar plantilla…', executions: 'Buscar ejecución…', carriers: 'Buscar transportista…', vehicles: 'Buscar vehículo…', routes: 'Buscar ruta…', freight: 'Buscar tarifa…', shipments: 'Buscar expedición…' },
    en: { templates: 'Search templates…', executions: 'Search executions…', carriers: 'Search carriers…', vehicles: 'Search vehicles…', routes: 'Search routes…', freight: 'Search rates…', shipments: 'Search shipments…' },
  } satisfies Record<'es' | 'en', Record<Tab, string>>
  return labels[language][tab]
}

function tabIcon(tab: Tab) {
  if (tab === 'templates') return <ClipboardList size={15} />
  if (tab === 'executions') return <Play size={15} />
  if (tab === 'carriers' || tab === 'vehicles') return <Truck size={15} />
  if (tab === 'routes') return <RouteIcon size={15} />
  if (tab === 'freight') return <Send size={15} />
  return <PackageCheck size={15} />
}

function editorTitle(editor: EditorState | null, language: 'es' | 'en') {
  if (!editor) return local(language, 'Operaciones', 'Operations')
  if (editor.kind === 'template') return editor.item ? local(language, 'Editar plantilla', 'Edit template') : local(language, 'Nueva plantilla', 'New template')
  if (editor.kind === 'execution') return local(language, 'Nueva ejecución', 'New execution')
  if (editor.kind === 'carrier') return editor.item ? local(language, 'Editar transportista', 'Edit carrier') : local(language, 'Nuevo transportista', 'New carrier')
  if (editor.kind === 'vehicle') return editor.item ? local(language, 'Editar vehículo', 'Edit vehicle') : local(language, 'Nuevo vehículo', 'New vehicle')
  if (editor.kind === 'route') return editor.item ? local(language, 'Editar ruta', 'Edit route') : local(language, 'Nueva ruta', 'New route')
  if (editor.kind === 'freight') return editor.item ? local(language, 'Editar tarifa de flete', 'Edit freight rate') : local(language, 'Nueva tarifa de flete', 'New freight rate')
  if (editor.kind === 'freight-simulation') return local(language, 'Simulador de fletes', 'Freight simulator')
  if (editor.kind === 'shipment') return editor.item ? local(language, 'Editar expedición', 'Edit shipment') : local(language, 'Nueva expedición', 'New shipment')
  if (editor.kind === 'shipment-freight') return `${local(language, 'Resolver flete', 'Resolve freight')} · ${editor.shipment.shipmentNumber}`
  return `${local(language, 'Subir documento', 'Upload document')} · ${editor.shipment.shipmentNumber}`
}

function editorDescription(editor: EditorState | null, language: 'es' | 'en') {
  if (!editor) return ''
  if (editor.kind === 'template') return local(language, 'Define una secuencia reutilizable y versionada de trabajo.', 'Define a reusable, versioned work sequence.')
  if (editor.kind === 'execution') return local(language, 'Asocia una plantilla publicada con una referencia de negocio.', 'Associate a published template with a business reference.')
  if (editor.kind === 'carrier') return local(language, 'Datos operativos y de contacto del transportista.', 'Carrier contact and operational data.')
  if (editor.kind === 'vehicle') return local(language, 'Capacidad y asignación del recurso de transporte.', 'Transport resource capacity and assignment.')
  if (editor.kind === 'route') return local(language, 'Itinerario, ventanas horarias y paradas planificadas.', 'Itinerary, time windows and planned stops.')
  if (editor.kind === 'freight') return local(language, 'Ámbito, vigencia, prioridades y reglas de cálculo del transporte.', 'Transport scope, validity, priorities and calculation rules.')
  if (editor.kind === 'freight-simulation') return local(language, 'Comprueba qué tarifa resulta elegible antes de aplicarla.', 'Check which rate is eligible before applying it.')
  if (editor.kind === 'shipment') return local(language, 'Plan de transporte, items y documentos comerciales de origen.', 'Transport plan, items and source commercial documents.')
  if (editor.kind === 'shipment-freight') return local(language, 'Selecciona y congela la tarifa aplicable en la expedición.', 'Select and snapshot the applicable rate on the shipment.')
  return local(language, 'Sube el archivo, calcula su checksum y conserva sus metadatos.', 'Upload the file, calculate its checksum and retain its metadata.')
}

function freightMethodLabel(method: FreightCalculationMethod, language: 'es' | 'en') {
  const labels = {
    es: { FIXED: 'Importe fijo', PER_KG: 'Por kilogramo', PER_M3: 'Por metro cúbico', PER_KM: 'Por kilómetro', FIXED_PLUS_PER_KG: 'Fijo + kilogramo', FIXED_PLUS_PER_M3: 'Fijo + metro cúbico', FIXED_PLUS_PER_KM: 'Fijo + kilómetro' },
    en: { FIXED: 'Fixed amount', PER_KG: 'Per kilogram', PER_M3: 'Per cubic metre', PER_KM: 'Per kilometre', FIXED_PLUS_PER_KG: 'Fixed + kilogram', FIXED_PLUS_PER_M3: 'Fixed + cubic metre', FIXED_PLUS_PER_KM: 'Fixed + kilometre' },
  } satisfies Record<'es' | 'en', Record<FreightCalculationMethod, string>>
  return labels[language][method]
}

function freightMetricLabel(method: FreightCalculationMethod, language: 'es' | 'en') {
  if (method === 'PER_KG' || method === 'FIXED_PLUS_PER_KG') return local(language, 'kg', 'kg')
  if (method === 'PER_M3' || method === 'FIXED_PLUS_PER_M3') return local(language, 'm³', 'm³')
  if (method === 'PER_KM' || method === 'FIXED_PLUS_PER_KM') return local(language, 'km', 'km')
  return local(language, 'servicio', 'service')
}

function freightRequiresFixed(method: FreightCalculationMethod) {
  return method === 'FIXED' || method.startsWith('FIXED_PLUS_')
}

function freightRequiresUnit(method: FreightCalculationMethod) {
  return method !== 'FIXED'
}

function local(language: 'es' | 'en', spanish: string, english: string) {
  return language === 'en' ? english : spanish
}

function matches(query: string, ...values: Array<string | null | undefined>) {
  const normalized = query.trim().toLocaleLowerCase()
  return !normalized || values.some((value) => value?.toLocaleLowerCase().includes(normalized))
}

function nullable(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function nullableNumber(value: string) {
  return value.trim() ? Number(value) : null
}

function today() {
  const now = new Date()
  const localDate = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return localDate.toISOString().slice(0, 10)
}

function toDateTimeInput(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return shifted.toISOString().slice(0, 16)
}

function toInstant(value: string) {
  if (!value.trim()) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

function validChronology(start: string, end: string) {
  if (!start || !end) return true
  return new Date(end).getTime() >= new Date(start).getTime()
}

function emptyWorkflowStep() {
  return { code: '', name: '', description: '', required: true, estimatedMinutes: '' }
}

function emptyRouteStop() {
  return { name: '', location: '', windowStart: '', windowEnd: '', instructions: '' }
}

function emptyShipmentLine(): ShipmentLineForm {
  return { productId: '', productCodeSnapshot: '', productNameSnapshot: '', quantity: '1', unitOfMeasureSnapshot: 'UNIT', sourceDocumentId: '', sourceDocumentType: '', sourceDocumentNumberSnapshot: '' }
}

function shipmentLineToForm(line: ShipmentLine): ShipmentLineForm {
  return { productId: line.productId ?? '', productCodeSnapshot: line.productCodeSnapshot ?? '', productNameSnapshot: line.productNameSnapshot, quantity: String(line.quantity), unitOfMeasureSnapshot: line.unitOfMeasureSnapshot, sourceDocumentId: line.sourceDocumentId ?? '', sourceDocumentType: line.sourceDocumentType ?? '', sourceDocumentNumberSnapshot: line.sourceDocumentNumberSnapshot ?? '' }
}

function formatBytes(value: number, locale: string) {
  if (value < 1024) return `${formatNumber(value, locale, 0)} B`
  if (value < 1024 * 1024) return `${formatNumber(value / 1024, locale, 1)} KB`
  return `${formatNumber(value / (1024 * 1024), locale, 1)} MB`
}
