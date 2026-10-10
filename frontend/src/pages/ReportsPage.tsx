import {
  Boxes, Building2, Check, ClipboardList, FileCheck2, FileText, HandCoins, MonitorSmartphone, Scale,
  ListFilter, Printer, RefreshCw, Search, Settings2, Users, type LucideIcon,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useAuth, type UserRoleCode } from '../auth/AuthContext'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field } from '../components/Form'
import { PageHeader } from '../components/PageHeader'
import { DocumentPrintCenter } from '../components/DocumentPrintCenter'
import { PearBrandMark } from '../components/PearBrandMark'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDate, formatDateTime, formatNumber } from '../lib/format'
import { TableCaption } from '../components/TableCaption'
import { ReportPeriodPicker } from '../components/ReportPeriodPicker'
import { localIsoDate } from '../lib/date'
import { browserTimeZone, type PosTicket } from '../lib/pos'
import { reasonLabel } from '../lib/stockReasons'
import { movementOrigin } from '../components/InventoryActivity'
import type {
  CommercialDocument, Customer, DueDate, FlatPage, PageResponse, Product, StockMovement, Supplier,
} from '../types/api'

export type ReportModuleId = 'customers' | 'suppliers' | 'products' | 'quotes' | 'sales' | 'receivables' | 'collections' | 'debtors' | 'posTickets' | 'stockMovements'
export type ReportValue = string | number | boolean | null
export type ReportRow = { id: string; currency?: string; [key: string]: ReportValue | undefined }

type FieldType = 'text' | 'date' | 'datetime' | 'currency' | 'number' | 'percentage' | 'boolean' | 'status'

interface ReportField {
  key: string
  label: [string, string]
  type: FieldType
  defaultSelected?: boolean
  summable?: boolean
}

interface ReportModule {
  id: ReportModuleId
  icon: LucideIcon
  label: [string, string]
  description: [string, string]
  roles: UserRoleCode[]
  fields: ReportField[]
  defaultSort: string
  defaultDirection: SortDirection
  filterKind: 'master' | 'quotes' | 'sales' | 'receivables' | 'collections' | 'debtors' | 'pos' | 'stock'
}

type SortDirection = 'asc' | 'desc'

interface ReportFilters {
  query: string
  active: string
  status: string
  type: string
  fromDate: string
  toDate: string
  /** Nombre del periodo elegido con los atajos (p. ej. «Trimestre 3 · 2026»). */
  period: string
}

interface ReportSnapshot {
  moduleId: ReportModuleId
  title: string
  titleCustomized: boolean
  fieldKeys: string[]
  rows: ReportRow[]
  generatedAt: string
  filters: ReportFilters
}

interface CompanyPrintSettings {
  baseCurrency: string
  displayName: string
}

const emptyFilters: ReportFilters = { query: '', active: '', status: '', type: '', fromDate: '', toDate: '', period: '' }
const administrators: UserRoleCode[] = ['OWNER', 'ADMIN']

const reportModules: ReportModule[] = [
  {
    id: 'customers', icon: Users, label: ['Clientes', 'Customers'],
    description: ['Directorio, contacto, identificación y riesgo comercial.', 'Directory, contact, identification and commercial risk.'],
    roles: [...administrators, 'ECONOMY'], defaultSort: 'legalName', defaultDirection: 'asc', filterKind: 'master',
    fields: [
      field('createdAt', 'Fecha de alta', 'Created at', 'datetime'), field('code', 'Código', 'Code', 'text', true),
      field('legalName', 'Razón social', 'Legal name', 'text', true), field('tradeName', 'Nombre comercial', 'Trade name', 'text'),
      field('taxId', 'NIF / CIF', 'Tax ID', 'text', true), field('phone', 'Teléfono', 'Phone', 'text', true),
      field('email', 'Correo electrónico', 'Email', 'text', true), field('creditLimit', 'Límite de crédito', 'Credit limit', 'currency'),
      field('riskWarningThreshold', 'Aviso de riesgo', 'Risk warning', 'currency'), field('riskPolicy', 'Política de riesgo', 'Risk policy', 'status'),
      field('observations', 'Observaciones', 'Notes', 'text'), field('active', 'Estado', 'Status', 'boolean', true),
    ],
  },
  {
    id: 'suppliers', icon: Building2, label: ['Proveedores', 'Suppliers'],
    description: ['Contactos, identificación y referencias logísticas.', 'Contacts, identification and logistics references.'],
    roles: [...administrators, 'LOGISTICS'], defaultSort: 'legalName', defaultDirection: 'asc', filterKind: 'master',
    fields: [
      field('createdAt', 'Fecha de alta', 'Created at', 'datetime'), field('code', 'Código', 'Code', 'text', true),
      field('legalName', 'Razón social', 'Legal name', 'text', true), field('tradeName', 'Nombre comercial', 'Trade name', 'text'),
      field('taxId', 'NIF / CIF', 'Tax ID', 'text', true), field('phone', 'Teléfono', 'Phone', 'text', true),
      field('email', 'Correo electrónico', 'Email', 'text', true), field('carrier', 'Transportista', 'Carrier', 'text'),
      field('route', 'Ruta', 'Route', 'text'), field('observations', 'Observaciones', 'Notes', 'text'), field('active', 'Estado', 'Status', 'boolean', true),
    ],
  },
  {
    id: 'products', icon: Boxes, label: ['Productos', 'Products'],
    description: ['Catálogo con unidades, precios, impuestos y estado.', 'Catalogue with units, prices, taxes and status.'],
    roles: [...administrators, 'CATALOG'], defaultSort: 'name', defaultDirection: 'asc', filterKind: 'master',
    fields: [
      field('createdAt', 'Fecha de alta', 'Created at', 'datetime'), field('code', 'Código', 'Code', 'text', true),
      field('name', 'Producto / servicio', 'Product / service', 'text', true), field('description', 'Descripción', 'Description', 'text'),
      field('unitOfMeasure', 'Unidad', 'Unit', 'status', true), field('basePrice', 'Precio base', 'Base price', 'currency', true),
      field('taxRate', 'Impuesto', 'Tax', 'percentage', true), field('active', 'Estado', 'Status', 'boolean', true),
    ],
  },
  {
    id: 'quotes', icon: FileCheck2, label: ['Presupuestos', 'Quotes'],
    description: ['Importes, cliente, vigencia y estado de decisión.', 'Amounts, customer, validity and decision status.'],
    roles: [...administrators, 'ECONOMY'], defaultSort: 'issueDate', defaultDirection: 'desc', filterKind: 'quotes',
    fields: documentFields(true),
  },
  {
    id: 'sales', icon: FileText, label: ['Ventas', 'Sales'],
    description: ['Pedidos, albaranes, facturas y partes comerciales.', 'Orders, delivery notes, invoices and commercial work orders.'],
    roles: [...administrators, 'ECONOMY'], defaultSort: 'issueDate', defaultDirection: 'desc', filterKind: 'sales',
    fields: documentFields(false),
  },
  {
    id: 'receivables', icon: HandCoins, label: ['Cobros pendientes', 'Outstanding receivables'],
    description: ['Facturas con deuda y saldo pendiente calculado.', 'Invoices with debt and calculated outstanding balance.'],
    roles: [...administrators, 'ECONOMY'], defaultSort: 'dueDate', defaultDirection: 'asc', filterKind: 'receivables',
    fields: [
      field('number', 'Factura', 'Invoice', 'text', true), field('customerCode', 'Código cliente', 'Customer code', 'text'),
      field('customerName', 'Cliente', 'Customer', 'text', true), field('issueDate', 'Fecha de emisión', 'Issue date', 'date', true),
      field('dueDate', 'Próximo vencimiento', 'Next due date', 'date', true), field('currency', 'Moneda', 'Currency', 'text'),
      field('totalAmount', 'Total factura', 'Invoice total', 'currency', true, true),
      field('paidAmount', 'Cobrado', 'Paid', 'currency', true, true),
      field('outstandingAmount', 'Pendiente', 'Outstanding', 'currency', true, true),
      field('paymentStatus', 'Estado de cobro', 'Payment status', 'status', true),
    ],
  },
  {
    id: 'collections', icon: ClipboardList, label: ['Cobros: pagados y pendientes', 'Collections: paid and unpaid'],
    description: ['Recibos por vencimiento: quién ha pagado, quién no y qué está vencido.', 'Receipts by due date: who paid, who did not and what is overdue.'],
    roles: [...administrators, 'ECONOMY'], defaultSort: 'dueDate', defaultDirection: 'asc', filterKind: 'collections',
    fields: [
      field('receiptNumber', 'Recibo', 'Receipt', 'text', true), field('documentNumber', 'Factura', 'Invoice', 'text', true),
      field('customerCode', 'Código cliente', 'Customer code', 'text'), field('customerName', 'Cliente', 'Customer', 'text', true),
      field('dueDate', 'Vencimiento', 'Due date', 'date', true), field('collectionState', 'Situación', 'Situation', 'status', true),
      field('daysOverdue', 'Días vencido', 'Days overdue', 'number', true), field('amount', 'Importe', 'Amount', 'currency', true, true),
      field('paidAmount', 'Cobrado', 'Collected', 'currency', true, true), field('pendingAmount', 'Pendiente', 'Pending', 'currency', true, true),
      field('collectionDate', 'Fecha de cobro', 'Collection date', 'date'), field('collectionMethod', 'Forma de cobro', 'Collection method', 'status'),
      field('returnReason', 'Motivo de devolución', 'Return reason', 'text'), field('currency', 'Moneda', 'Currency', 'text'),
    ],
  },
  {
    id: 'debtors', icon: Scale, label: ['Deuda por cliente', 'Debt by customer'],
    description: ['Facturado, cobrado, pendiente y vencido de cada cliente en el periodo.', 'Invoiced, collected, pending and overdue for each customer in the period.'],
    roles: [...administrators, 'ECONOMY'], defaultSort: 'outstandingAmount', defaultDirection: 'desc', filterKind: 'debtors',
    fields: [
      field('customerCode', 'Código cliente', 'Customer code', 'text', true), field('customerName', 'Cliente', 'Customer', 'text', true),
      field('invoiceCount', 'Facturas', 'Invoices', 'number', true), field('totalAmount', 'Facturado', 'Invoiced', 'currency', true, true),
      field('paidAmount', 'Cobrado', 'Collected', 'currency', true, true), field('outstandingAmount', 'Pendiente', 'Pending', 'currency', true, true),
      field('overdueAmount', 'Vencido', 'Overdue', 'currency', true, true), field('oldestDueDate', 'Vencimiento más antiguo', 'Oldest due date', 'date', true),
      field('currency', 'Moneda', 'Currency', 'text'),
    ],
  },
  {
    id: 'posTickets', icon: MonitorSmartphone, label: ['Ventas TPV', 'POS sales'],
    description: ['Tickets y devoluciones de caja con forma de pago e impuestos.', 'Till tickets and refunds with payment method and taxes.'],
    roles: [...administrators, 'ECONOMY'], defaultSort: 'issuedAt', defaultDirection: 'desc', filterKind: 'pos',
    fields: [
      field('number', 'Ticket', 'Ticket', 'text', true), field('issuedAt', 'Fecha y hora', 'Date and time', 'datetime', true),
      field('kind', 'Tipo', 'Type', 'status', true), field('registerName', 'Caja', 'Till', 'text', true), field('cashierName', 'Cajero', 'Cashier', 'text', true),
      field('paymentMethod', 'Forma de pago', 'Payment method', 'status', true), field('customerName', 'Cliente', 'Customer', 'text'),
      field('lineCount', 'Líneas', 'Lines', 'number'), field('taxBaseAmount', 'Base imponible', 'Tax base', 'currency', false, true),
      field('taxAmount', 'IVA', 'VAT', 'currency', false, true), field('totalAmount', 'Total', 'Total', 'currency', true, true),
      field('status', 'Estado', 'Status', 'status', true), field('originalTicketNumber', 'Ticket devuelto', 'Refunded ticket', 'text'),
    ],
  },
  {
    id: 'stockMovements', icon: Boxes, label: ['Movimientos de almacén', 'Stock movements'],
    description: ['Entradas, salidas, mermas, ventas del TPV y escaneos con su autor y motivo.', 'Entries, exits, losses, POS sales and scans with author and reason.'],
    roles: [...administrators, 'LOGISTICS'], defaultSort: 'occurredAt', defaultDirection: 'desc', filterKind: 'stock',
    fields: [
      field('occurredAt', 'Fecha y hora', 'Date and time', 'datetime', true), field('productCode', 'Código', 'Code', 'text', true),
      field('productName', 'Producto', 'Product', 'text', true), field('warehouse', 'Almacén', 'Warehouse', 'text'),
      field('type', 'Movimiento', 'Movement', 'status', true), field('quantity', 'Cantidad', 'Quantity', 'number', true, true),
      field('balanceAfter', 'Saldo', 'Balance', 'number'), field('origin', 'Origen', 'Origin', 'text', true),
      field('reason', 'Motivo', 'Reason', 'text', true), field('document', 'Documento', 'Document', 'text'),
      field('performedBy', 'Quién', 'Who', 'text', true), field('note', 'Nota', 'Note', 'text'),
    ],
  },
]

function field(key: string, es: string, en: string, type: FieldType, defaultSelected = false, summable = false): ReportField {
  return { key, label: [es, en], type, defaultSelected, summable }
}

function documentFields(quote: boolean): ReportField[] {
  return [
    field('number', quote ? 'Presupuesto' : 'Documento', quote ? 'Quote' : 'Document', 'text', true),
    ...(!quote ? [field('type', 'Tipo', 'Type', 'status', true)] : []),
    field('customerCode', 'Código cliente', 'Customer code', 'text'), field('customerName', 'Cliente', 'Customer', 'text', true),
    field('issueDate', 'Fecha de emisión', 'Issue date', 'date', true),
    ...(quote ? [field('quoteValidUntil', 'Válido hasta', 'Valid until', 'date', true), field('quoteStatus', 'Estado', 'Status', 'status', true)]
      : [field('dueDate', 'Vencimiento', 'Due date', 'date'), field('status', 'Estado documental', 'Document status', 'status', true), field('paymentStatus', 'Estado de cobro', 'Payment status', 'status')]),
    field('lineCount', 'N.º de líneas', 'Line count', 'number'), field('currency', 'Moneda', 'Currency', 'text'), field('netAmount', 'Base imponible', 'Net amount', 'currency', false, true),
    field('taxAmount', 'Impuestos', 'Tax amount', 'currency', false, true), field('totalAmount', 'Total', 'Total', 'currency', true, true),
    field('notes', 'Notas', 'Notes', 'text'),
  ]
}

const copy = {
  es: {
    eyebrow: 'Centro de impresión', title: 'Informes e impresión', description: 'Obtén facturas y presupuestos completos o prepara informes con los registros que necesites.',
    choose: '1. Elige qué quieres imprimir', chooseHint: 'Solo aparecen los módulos incluidos en tu perfil de acceso.', fields: '2. Selecciona los datos', fieldsHint: 'Marca únicamente las columnas que necesites.',
    recommended: 'Recomendados', all: 'Todos', none: 'Ninguno', options: '3. Filtra y ordena', search: 'Buscar en los registros', searchPlaceholder: 'Código, nombre, documento…',
    status: 'Estado', type: 'Tipo de documento', from: 'Desde', to: 'Hasta', activeAll: 'Todos', active: 'Solo activos', inactive: 'Solo inactivos',
    allStatuses: 'Todos los estados', allTypes: 'Todos los tipos', sort: 'Ordenar por', direction: 'Dirección', ascending: 'Ascendente', descending: 'Descendente',
    reportTitle: 'Título del informe', generate: 'Generar vista previa', generating: 'Preparando todos los datos…', selectedColumns: 'columnas seleccionadas',
    preview: 'Vista previa', print: 'Imprimir listado / guardar PDF', regenerate: 'Actualizar informe', rows: 'registros', generated: 'Generado', filters: 'Filtros', noFilters: 'Todos los registros, sin filtros adicionales.',
    noRows: 'No hay datos para este informe', noRowsHint: 'Cambia los filtros o comprueba que existan registros en el módulo.', selectModule: 'Selecciona uno de los módulos para configurar el informe.',
    selectionRequired: 'Selecciona al menos una columna.', loadError: 'No se ha podido preparar el informe.', totals: 'Totales', accessHint: 'Los permisos del usuario se respetan también al consultar los datos.',
  },
  en: {
    eyebrow: 'Print centre', title: 'Reports and printing', description: 'Get complete invoices and quotes or prepare reports with the records you need.',
    choose: '1. Choose what to print', chooseHint: 'Only modules included in your access profile are shown.', fields: '2. Select the data', fieldsHint: 'Select only the columns you need.',
    recommended: 'Recommended', all: 'All', none: 'None', options: '3. Filter and sort', search: 'Search records', searchPlaceholder: 'Code, name, document…',
    status: 'Status', type: 'Document type', from: 'From', to: 'To', activeAll: 'All', active: 'Active only', inactive: 'Inactive only',
    allStatuses: 'All statuses', allTypes: 'All types', sort: 'Sort by', direction: 'Direction', ascending: 'Ascending', descending: 'Descending',
    reportTitle: 'Report title', generate: 'Generate preview', generating: 'Preparing all data…', selectedColumns: 'selected columns',
    preview: 'Preview', print: 'Print list / save PDF', regenerate: 'Refresh report', rows: 'records', generated: 'Generated', filters: 'Filters', noFilters: 'All records, with no additional filters.',
    noRows: 'There is no data for this report', noRowsHint: 'Change the filters or check that the module contains records.', selectModule: 'Select one of the modules to configure the report.',
    selectionRequired: 'Select at least one column.', loadError: 'The report could not be prepared.', totals: 'Totals', accessHint: 'User permissions are also enforced when retrieving data.',
  },
}

export function ReportsPage() {
  const { language, locale } = useTranslation()
  const { company, identity } = useAuth()
  const c = copy[language]
  const canPrintDocuments = (identity?.roles ?? []).some(role => ['OWNER', 'ADMIN', 'ECONOMY'].includes(role.toUpperCase()))
  const [mode, setMode] = useState<'documents' | 'reports'>('documents')
  const showDocuments = canPrintDocuments && mode === 'documents'
  const availableModules = useMemo(() => reportModulesForRoles(identity?.roles ?? []), [identity?.roles])
  const [moduleId, setModuleId] = useState<ReportModuleId | null>(null)
  const selectedModule = reportModules.find((module) => module.id === moduleId) ?? null
  const [fieldKeys, setFieldKeys] = useState<string[]>([])
  const [filters, setFilters] = useState<ReportFilters>(emptyFilters)
  const [sortKey, setSortKey] = useState('')
  const [direction, setDirection] = useState<SortDirection>('asc')
  const [reportTitle, setReportTitle] = useState('')
  const [titleCustomized, setTitleCustomized] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [snapshot, setSnapshot] = useState<ReportSnapshot | null>(null)
  const [settings, setSettings] = useState<CompanyPrintSettings>({ baseCurrency: 'EUR', displayName: company?.name ?? 'PERA ERP' })

  useEffect(() => {
    apiFetch<CompanyPrintSettings>('/api/v1/company-settings/current')
      .then((value) => setSettings({ baseCurrency: value.baseCurrency || 'EUR', displayName: value.displayName || company?.name || 'PERA ERP' }))
      .catch(() => undefined)
  }, [company?.name])

  useEffect(() => {
    if (selectedModule && !titleCustomized) setReportTitle(label(selectedModule.label, language))
  }, [language, selectedModule, titleCustomized])

  const chooseModule = (module: ReportModule) => {
    setModuleId(module.id)
    setFieldKeys(module.fields.filter((item) => item.defaultSelected).map((item) => item.key))
    setFilters(emptyFilters)
    setSortKey(module.defaultSort)
    setDirection(module.defaultDirection)
    setReportTitle(label(module.label, language))
    setTitleCustomized(false)
    setSnapshot(null)
    setError('')
  }

  const generate = async () => {
    if (!selectedModule || fieldKeys.length === 0) { setError(c.selectionRequired); return }
    setLoading(true); setError('')
    try {
      const loaded = await loadReportRows(selectedModule.id, filters, settings.baseCurrency, language)
      const searched = filterRows(loaded, filters.query)
      const rows = sortReportRows(searched, sortKey, direction, locale)
      setSnapshot({ moduleId: selectedModule.id, title: reportTitle.trim() || label(selectedModule.label, language), titleCustomized, fieldKeys, rows, generatedAt: new Date().toISOString(), filters: { ...filters } })
      window.setTimeout(() => document.querySelector('.report-print-surface')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0)
    } catch (cause) {
      setError(`${c.loadError} ${errorMessage(cause)}`)
    } finally { setLoading(false) }
  }

  const print = () => {
    if (!snapshot?.rows.length) return
    const previousTitle = document.title
    const module = reportModules.find((item) => item.id === snapshot.moduleId)
    document.title = `${snapshot.titleCustomized || !module ? snapshot.title : label(module.label, language)} - ${settings.displayName}`
    document.body.classList.add('report-printing')
    try { window.print() } finally {
      document.body.classList.remove('report-printing')
      document.title = previousTitle
    }
  }

  return <div className="page-stack report-page">
    <PageHeader eyebrow={c.eyebrow} title={c.title} description={c.description} icon={Printer} />

    {canPrintDocuments && <div className="document-print-actions" role="group" aria-label={language === 'es' ? 'Qué quieres obtener' : 'What to produce'}>
      <button className={`button ${showDocuments ? 'button-primary' : 'button-secondary'}`} aria-pressed={showDocuments} onClick={() => setMode('documents')}>{language === 'es' ? 'Facturas y presupuestos completos' : 'Complete invoices and quotes'}</button>
      <button className={`button ${!showDocuments ? 'button-primary' : 'button-secondary'}`} aria-pressed={!showDocuments} onClick={() => setMode('reports')}>{language === 'es' ? 'Informes de registros' : 'Record reports'}</button>
    </div>}
    {showDocuments ? <DocumentPrintCenter /> : <>
    <section className="report-module-section" aria-labelledby="report-module-title">
      <div className="section-heading"><div><h2 id="report-module-title">{c.choose}</h2><p>{c.chooseHint}</p></div><span className="permission-note"><Check size={14} />{c.accessHint}</span></div>
      <div className="report-module-grid">
        {availableModules.map((module) => {
          const Icon = module.icon
          const selected = module.id === moduleId
          return <button key={module.id} type="button" className={`report-module-card ${selected ? 'selected' : ''}`} aria-pressed={selected} onClick={() => chooseModule(module)}>
            <span className="report-module-icon"><Icon size={22} /></span>
            <span><strong>{label(module.label, language)}</strong><small>{label(module.description, language)}</small></span>
            <span className="report-module-check"><Check size={15} /></span>
          </button>
        })}
      </div>
    </section>

    {!selectedModule ? <section className="panel"><EmptyState title={c.selectModule} description={c.chooseHint} /></section> :
      <section className="panel report-builder">
        <div className="report-builder-heading">
          <div><span className="eyebrow">{label(selectedModule.label, language)}</span><h2>{c.fields}</h2><p>{c.fieldsHint}</p></div>
          <span className="selection-counter">{fieldKeys.length} {c.selectedColumns}</span>
        </div>
        <div className="report-builder-layout">
          <div className="report-field-selector">
            <div className="report-field-actions">
              <button className="button button-secondary button-small" type="button" onClick={() => setFieldKeys(selectedModule.fields.filter((item) => item.defaultSelected).map((item) => item.key))}>{c.recommended}</button>
              <button className="button button-ghost button-small" type="button" onClick={() => setFieldKeys(selectedModule.fields.map((item) => item.key))}>{c.all}</button>
              <button className="button button-ghost button-small" type="button" onClick={() => setFieldKeys([])}>{c.none}</button>
            </div>
            <div className="report-field-grid">
              {selectedModule.fields.map((item) => <label key={item.key} className={fieldKeys.includes(item.key) ? 'selected' : ''}>
                <input type="checkbox" checked={fieldKeys.includes(item.key)} onChange={() => setFieldKeys((current) => current.includes(item.key) ? current.filter((key) => key !== item.key) : [...current, item.key])} />
                <span>{label(item.label, language)}</span><Check size={14} />
              </label>)}
            </div>
          </div>
          <div className="report-options">
            <div className="report-options-title"><Settings2 size={18} /><div><strong>{c.options}</strong><small>{label(selectedModule.description, language)}</small></div></div>
            <div className="form-grid report-options-grid">
              <Field label={c.reportTitle} htmlFor="report-title" wide><input id="report-title" value={reportTitle} maxLength={120} onChange={(event) => { setReportTitle(event.target.value); setTitleCustomized(true) }} /></Field>
              <Field label={c.search} htmlFor="report-query" wide><div className="input-prefix"><Search size={16} /><input id="report-query" value={filters.query} placeholder={c.searchPlaceholder} onChange={(event) => setFilters((current) => ({ ...current, query: event.target.value }))} /></div></Field>
              {selectedModule.filterKind === 'master' && <Field label={c.status} htmlFor="report-active"><select id="report-active" value={filters.active} onChange={(event) => setFilters((current) => ({ ...current, active: event.target.value }))}><option value="">{c.activeAll}</option><option value="true">{c.active}</option><option value="false">{c.inactive}</option></select></Field>}
              {selectedModule.filterKind === 'quotes' && <Field label={c.status} htmlFor="report-status"><select id="report-status" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}><option value="">{c.allStatuses}</option>{['DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CONVERTED'].map((value) => <option key={value} value={value}>{statusLabel(value, language)}</option>)}</select></Field>}
              {selectedModule.filterKind === 'sales' && <><Field label={c.type} htmlFor="report-type"><select id="report-type" value={filters.type} onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))}><option value="">{c.allTypes}</option>{['SALES_ORDER', 'DELIVERY_NOTE', 'INVOICE', 'RECTIFYING_INVOICE', 'WORK_ORDER'].map((value) => <option key={value} value={value}>{statusLabel(value, language)}</option>)}</select></Field><Field label={c.status} htmlFor="report-status"><select id="report-status" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}><option value="">{c.allStatuses}</option>{['DRAFT', 'CONFIRMED', 'CONVERTED', 'CANCELLED'].map((value) => <option key={value} value={value}>{statusLabel(value, language)}</option>)}</select></Field></>}
              {selectedModule.filterKind === 'collections' && <Field label={c.status} htmlFor="report-status"><select id="report-status" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}><option value="">{c.allStatuses}</option>{['PAID', 'UNPAID', 'OVERDUE', 'RETURNED'].map((value) => <option key={value} value={value}>{statusLabel(value, language)}</option>)}</select></Field>}
              {selectedModule.filterKind === 'debtors' && <Field label={c.status} htmlFor="report-status"><select id="report-status" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}><option value="">{language === 'es' ? 'Todos los clientes con facturas' : 'All customers with invoices'}</option><option value="UNPAID">{language === 'es' ? 'Solo con deuda pendiente' : 'Only with pending debt'}</option><option value="OVERDUE">{language === 'es' ? 'Solo con deuda vencida' : 'Only with overdue debt'}</option></select></Field>}
              {selectedModule.filterKind === 'pos' && <Field label={c.type} htmlFor="report-type"><select id="report-type" value={filters.type} onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))}><option value="">{c.allTypes}</option>{['SALE', 'REFUND'].map((value) => <option key={value} value={value}>{statusLabel(value, language)}</option>)}</select></Field>}
              {selectedModule.filterKind === 'stock' && <Field label={c.type} htmlFor="report-type"><select id="report-type" value={filters.type} onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))}><option value="">{c.allTypes}</option>{['PURCHASE_RECEIPT', 'PURCHASE_REVERSAL', 'SALES_ISSUE', 'SALES_RETURN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'TRANSFER_IN', 'TRANSFER_OUT'].map((value) => <option key={value} value={value}>{statusLabel(value, language)}</option>)}</select></Field>}
              {selectedModule.filterKind === 'receivables' && <Field label={c.status} htmlFor="report-status"><select id="report-status" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}><option value="">{c.allStatuses}</option>{['PENDING', 'PARTIALLY_PAID'].map((value) => <option key={value} value={value}>{statusLabel(value, language)}</option>)}</select></Field>}
              {selectedModule.filterKind !== 'master' && <ReportPeriodPicker key={selectedModule.id} language={language} onChange={(range, period) => setFilters((current) => ({ ...current, ...range, period }))} />}
              {selectedModule.filterKind !== 'master' && <><Field label={c.from} htmlFor="report-from"><input id="report-from" type="date" value={filters.fromDate} max={filters.toDate || undefined} onChange={(event) => setFilters((current) => ({ ...current, fromDate: event.target.value, period: '' }))} /></Field><Field label={c.to} htmlFor="report-to"><input id="report-to" type="date" min={filters.fromDate || undefined} value={filters.toDate} onChange={(event) => setFilters((current) => ({ ...current, toDate: event.target.value, period: '' }))} /></Field></>}
              <Field label={c.sort} htmlFor="report-sort"><select id="report-sort" value={sortKey} onChange={(event) => setSortKey(event.target.value)}>{selectedModule.fields.map((item) => <option key={item.key} value={item.key}>{label(item.label, language)}</option>)}</select></Field>
              <Field label={c.direction} htmlFor="report-direction"><select id="report-direction" value={direction} onChange={(event) => setDirection(event.target.value as SortDirection)}><option value="asc">{c.ascending}</option><option value="desc">{c.descending}</option></select></Field>
            </div>
          </div>
        </div>
        {error && <div className="inline-error report-error">{error}</div>}
        <div className="report-generate-bar">
          <div><ListFilter size={18} /><span><strong>{fieldKeys.length}</strong> {c.selectedColumns}</span></div>
          <button className="button button-primary" type="button" disabled={loading || fieldKeys.length === 0} onClick={generate}>{loading ? <RefreshCw className="spin" size={17} /> : <Printer size={17} />}{loading ? c.generating : c.generate}</button>
        </div>
      </section>}

    {loading && <section className="panel"><LoadingState label={c.generating} /></section>}
    {snapshot && <ReportPreview snapshot={snapshot} settings={settings} language={language} locale={locale} onPrint={print} onRefresh={generate} loading={loading} />}
    </>}
  </div>
}

function ReportPreview({ snapshot, settings, language, locale, onPrint, onRefresh, loading }: { snapshot: ReportSnapshot; settings: CompanyPrintSettings; language: 'es' | 'en'; locale: string; onPrint: () => void; onRefresh: () => void; loading: boolean }) {
  const c = copy[language]
  const module = reportModules.find((item) => item.id === snapshot.moduleId)!
  const fields = snapshot.fieldKeys.map((key) => module.fields.find((item) => item.key === key)).filter((item): item is ReportField => Boolean(item))
  const totals = calculateReportTotals(snapshot.rows, fields, settings.baseCurrency)

  return <section className="panel report-print-surface" aria-labelledby="report-preview-title">
    <div className="report-print-actions">
      <div><span className="eyebrow">{c.preview}</span><strong>{snapshot.rows.length} {c.rows}</strong></div>
      <div><button className="button button-ghost" type="button" disabled={loading} onClick={onRefresh}><RefreshCw size={16} />{c.regenerate}</button><button className="button button-primary" type="button" disabled={snapshot.rows.length === 0} onClick={onPrint}><Printer size={17} />{c.print}</button></div>
    </div>
    <header className="report-document-header">
      <div className="report-document-brand"><PearBrandMark /><div><strong>{settings.displayName}</strong><span>PERA ERP</span></div></div>
      <div className="report-document-meta"><span>{c.generated}</span><strong>{formatDateTime(snapshot.generatedAt, locale)}</strong></div>
    </header>
    <div className="report-document-title"><span>{label(module.label, language)}</span><h2 id="report-preview-title">{snapshot.titleCustomized ? snapshot.title : label(module.label, language)}</h2><p><strong>{c.filters}:</strong> {filterDescription(snapshot.filters, module, language)}</p></div>
    {totals.length > 0 && <div className="report-total-grid" aria-label={c.totals}>{totals.map((total) => <div key={`${total.currency}-${total.key}`}><span>{label(total.field.label, language)} · {total.currency}</span><strong>{formatCurrency(total.value, total.currency, locale)}</strong></div>)}</div>}
    {snapshot.rows.length === 0 ? <EmptyState title={c.noRows} description={c.noRowsHint} /> : <div className="table-scroll report-table-scroll"><table className="report-table"><TableCaption es="Resultados del informe" en="Report results" /><thead><tr>{fields.map((item) => <th key={item.key} className={numericField(item) ? 'align-right' : undefined}>{label(item.label, language)}</th>)}</tr></thead><tbody>{snapshot.rows.map((row) => <tr key={row.id}>{fields.map((item) => <td key={item.key} className={numericField(item) ? 'align-right' : undefined}>{formatReportValue(row[item.key] ?? null, item, row.currency || settings.baseCurrency, language, locale)}</td>)}</tr>)}</tbody></table></div>}
    <footer className="report-document-footer"><span>{settings.displayName}</span><span>{snapshot.rows.length} {c.rows}</span></footer>
  </section>
}

async function loadReportRows(moduleId: ReportModuleId, filters: ReportFilters, baseCurrency: string, language: 'es' | 'en'): Promise<ReportRow[]> {
  if (moduleId === 'customers') {
    const items = await fetchAllPages<Customer>(withParameters('/api/v1/customers', { query: filters.query }))
    return items.filter((item) => filters.active === '' || String(item.active) === filters.active).map((item) => ({ id: item.id, currency: baseCurrency, createdAt: item.createdAt, code: item.code, legalName: item.legalName, tradeName: item.tradeName, taxId: item.taxId, phone: item.phone, email: item.email, creditLimit: item.creditLimit, riskWarningThreshold: item.riskWarningThreshold, riskPolicy: item.riskPolicy, observations: item.observations, active: item.active }))
  }
  if (moduleId === 'suppliers') {
    const items = await fetchAllPages<Supplier>(withParameters('/api/v1/suppliers', { query: filters.query }))
    return items.filter((item) => filters.active === '' || String(item.active) === filters.active).map((item) => ({ id: item.id, createdAt: item.createdAt, code: item.code, legalName: item.legalName, tradeName: item.tradeName, taxId: item.taxId, phone: item.phone, email: item.email, carrier: item.carrier, route: item.route, observations: item.observations, active: item.active }))
  }
  if (moduleId === 'products') {
    const items = await fetchAllPages<Product>(withParameters('/api/v1/products', { query: filters.query }))
    return items.filter((item) => filters.active === '' || String(item.active) === filters.active).map((item) => ({ id: item.id, currency: baseCurrency, createdAt: item.createdAt, code: item.code, name: item.name, description: item.description, unitOfMeasure: item.unitOfMeasure, basePrice: item.basePrice, taxRate: item.taxRate, active: item.active }))
  }
  if (moduleId === 'quotes') {
    const items = await fetchAllPages<CommercialDocument>(withParameters('/api/v1/quotes', { status: filters.status, fromDate: filters.fromDate, toDate: filters.toDate }))
    return items.map(documentRow)
  }
  if (moduleId === 'sales') {
    const items = await fetchAllPages<CommercialDocument>(withParameters('/api/v1/documents', { type: filters.type, status: filters.status, fromDate: filters.fromDate, toDate: filters.toDate }))
    return items.filter((item) => item.type !== 'QUOTE').map(documentRow)
  }
  if (moduleId === 'collections') {
    const receipts = await fetchAllPages<ReceiptRow>(withParameters('/api/v1/receipts', { dueFrom: filters.fromDate, dueTo: filters.toDate }))
    return receipts.map((receipt) => collectionRow(receipt)).filter((row) => !filters.status || matchesCollectionFilter(row, filters.status))
  }
  if (moduleId === 'debtors') {
    const invoices = await fetchAllPages<CommercialDocument>(withParameters('/api/v1/documents', { type: 'INVOICE', fromDate: filters.fromDate, toDate: filters.toDate }))
    const issued = invoices.filter((invoice) => invoice.status !== 'DRAFT' && invoice.status !== 'CANCELLED')
    const withDueDates = await mapConcurrent(issued, 8, async (invoice) => ({ invoice, dueDates: await apiFetch<DueDate[]>(`/api/v1/due-dates?documentId=${encodeURIComponent(invoice.id)}`) }))
    return debtorRows(withDueDates, localIsoDate()).filter((row) => filters.status === 'OVERDUE' ? Number(row.overdueAmount) > 0 : filters.status === 'UNPAID' ? Number(row.outstandingAmount) > 0 : true)
  }
  if (moduleId === 'posTickets') {
    const tickets = await fetchAllPages<PosTicket>(withParameters('/api/v1/pos/tickets', { from: filters.fromDate, to: filters.toDate, kind: filters.type, timeZone: browserTimeZone() }))
    return tickets.map((ticket) => posTicketRow(ticket))
  }
  if (moduleId === 'stockMovements') {
    const from = filters.fromDate ? new Date(`${filters.fromDate}T00:00:00`).toISOString() : ''
    const toDate = filters.toDate ? new Date(`${filters.toDate}T00:00:00`) : null
    if (toDate) toDate.setDate(toDate.getDate() + 1)
    const [movements, warehouses] = await Promise.all([
      fetchAllFlatPages<StockMovement>(withParameters('/api/v1/stock-movements', { occurredFrom: from, occurredTo: toDate ? toDate.toISOString() : '', type: filters.type })),
      apiFetch<FlatPage<{ id: string; code: string }>>('/api/v1/warehouses?size=200').then((page) => page.content).catch(() => []),
    ])
    return movements.map((movement) => stockMovementRow(movement, warehouses.find((item) => item.id === movement.warehouseId)?.code ?? '', language))
  }
  const invoices = await fetchAllPages<CommercialDocument>(withParameters('/api/v1/documents', { type: 'INVOICE', fromDate: filters.fromDate, toDate: filters.toDate }))
  const candidates = invoices.filter((invoice) => invoice.paymentStatus !== 'PAID' && invoice.paymentStatus !== 'NOT_APPLICABLE' && (!filters.status || invoice.paymentStatus === filters.status))
  const rows = await mapConcurrent(candidates, 8, async (invoice) => receivableRow(invoice, await apiFetch<DueDate[]>(`/api/v1/due-dates?documentId=${encodeURIComponent(invoice.id)}`)))
  return rows.filter((row) => Number(row.outstandingAmount ?? 0) > 0)
}

function documentRow(item: CommercialDocument): ReportRow {
  return { id: item.id, number: item.number, type: item.type, status: item.status, customerCode: item.customerCode, customerName: item.customerName, issueDate: item.issueDate, dueDate: item.dueDate, currency: item.currency, paymentStatus: item.paymentStatus, netAmount: item.netAmount, taxAmount: item.taxAmount, totalAmount: item.totalAmount, lineCount: item.lines.length, notes: item.notes, quoteStatus: item.quoteStatus, quoteValidUntil: item.quoteValidUntil }
}

export function receivableRow(invoice: CommercialDocument, dueDates: DueDate[]): ReportRow {
  const activeDueDates = dueDates.filter((item) => item.status !== 'CANCELLED')
  const unpaid = activeDueDates.filter((item) => item.status !== 'PAID' && Number(item.amount) - Number(item.paidAmount) > 0)
  const paidAmount = activeDueDates.length ? activeDueDates.reduce((total, item) => total + Number(item.paidAmount), 0) : invoice.paymentStatus === 'PAID' ? Number(invoice.totalAmount) : 0
  const outstandingAmount = activeDueDates.length ? activeDueDates.reduce((total, item) => total + Math.max(0, Number(item.amount) - Number(item.paidAmount)), 0) : invoice.paymentStatus === 'PAID' ? 0 : Number(invoice.totalAmount)
  const nextDueDate = unpaid.map((item) => item.dueDate).sort()[0] ?? invoice.dueDate
  return { id: invoice.id, number: invoice.number, customerCode: invoice.customerCode, customerName: invoice.customerName, issueDate: invoice.issueDate, dueDate: nextDueDate, currency: invoice.currency, totalAmount: invoice.totalAmount, paidAmount, outstandingAmount, paymentStatus: invoice.paymentStatus }
}

interface ReceiptRow {
  id: string; receiptNumber: string; customerCode: string | null; customerName: string; documentNumber: string
  amount: number; currencyCode: string; dueDate: string; status: 'PENDING' | 'REMITTED' | 'COLLECTED' | 'RETURNED' | 'CANCELLED'
  collectionDate: string | null; collectionMethod: string | null; returnReason: string | null
}

/** Situación de un recibo para el listado de cobros: cobrado, pendiente, vencido o devuelto. */
export function collectionRow(receipt: ReceiptRow, today = localIsoDate()): ReportRow {
  const paid = receipt.status === 'COLLECTED'
  const open = receipt.status === 'PENDING' || receipt.status === 'REMITTED'
  const overdueDays = open && receipt.dueDate < today ? Math.round((new Date(`${today}T00:00:00`).getTime() - new Date(`${receipt.dueDate}T00:00:00`).getTime()) / 86_400_000) : 0
  const state = receipt.status === 'CANCELLED' ? 'CANCELLED' : receipt.status === 'RETURNED' ? 'RETURNED' : paid ? 'PAID' : overdueDays > 0 ? 'OVERDUE' : receipt.status === 'REMITTED' ? 'REMITTED' : 'UNPAID'
  return {
    id: receipt.id, receiptNumber: receipt.receiptNumber, documentNumber: receipt.documentNumber, customerCode: receipt.customerCode,
    customerName: receipt.customerName, dueDate: receipt.dueDate, collectionState: state, daysOverdue: overdueDays || null,
    amount: receipt.status === 'CANCELLED' ? 0 : Number(receipt.amount), paidAmount: paid ? Number(receipt.amount) : 0,
    pendingAmount: open || receipt.status === 'RETURNED' ? Number(receipt.amount) : 0, collectionDate: receipt.collectionDate,
    collectionMethod: receipt.collectionMethod, returnReason: receipt.returnReason, currency: receipt.currencyCode,
  }
}

function matchesCollectionFilter(row: ReportRow, filter: string) {
  if (filter === 'PAID') return row.collectionState === 'PAID'
  if (filter === 'OVERDUE') return row.collectionState === 'OVERDUE'
  if (filter === 'RETURNED') return row.collectionState === 'RETURNED'
  return row.collectionState === 'UNPAID' || row.collectionState === 'OVERDUE' || row.collectionState === 'REMITTED' || row.collectionState === 'RETURNED'
}

/** Deuda agregada por cliente a partir de sus facturas y vencimientos. */
export function debtorRows(items: Array<{ invoice: CommercialDocument; dueDates: DueDate[] }>, today = localIsoDate()): ReportRow[] {
  const byCustomer = new Map<string, ReportRow & { totalAmount: number; paidAmount: number; outstandingAmount: number; overdueAmount: number; invoiceCount: number }>()
  for (const { invoice, dueDates } of items) {
    const row = receivableRow(invoice, dueDates)
    const active = dueDates.filter((item) => item.status !== 'CANCELLED')
    const overdue = active.length
      ? active.filter((item) => item.dueDate < today).reduce((sum, item) => sum + Math.max(0, Number(item.amount) - Number(item.paidAmount)), 0)
      : invoice.paymentStatus !== 'PAID' && (invoice.dueDate ?? invoice.issueDate) < today ? Number(invoice.totalAmount) : 0
    const key = `${invoice.customerCode ?? invoice.customerName}|${invoice.currency}`
    const current = byCustomer.get(key) ?? { id: key, customerCode: invoice.customerCode, customerName: invoice.customerName, currency: invoice.currency, invoiceCount: 0, totalAmount: 0, paidAmount: 0, outstandingAmount: 0, overdueAmount: 0, oldestDueDate: null }
    current.invoiceCount += 1
    current.totalAmount += Number(invoice.totalAmount)
    current.paidAmount += Number(row.paidAmount ?? 0)
    current.outstandingAmount += Number(row.outstandingAmount ?? 0)
    current.overdueAmount += overdue
    if (Number(row.outstandingAmount ?? 0) > 0 && row.dueDate && (!current.oldestDueDate || String(row.dueDate) < String(current.oldestDueDate))) current.oldestDueDate = row.dueDate
    byCustomer.set(key, current)
  }
  return [...byCustomer.values()].map((row) => ({ ...row, totalAmount: round(row.totalAmount), paidAmount: round(row.paidAmount), outstandingAmount: round(row.outstandingAmount), overdueAmount: round(row.overdueAmount) }))
}

function posTicketRow(ticket: PosTicket): ReportRow {
  const sign = ticket.kind === 'REFUND' ? -1 : 1
  const counted = ticket.status === 'VOIDED' ? 0 : sign
  return {
    id: ticket.id, number: ticket.number, issuedAt: ticket.issuedAt, kind: ticket.kind, registerName: ticket.registerName,
    cashierName: ticket.cashierName, paymentMethod: ticket.paymentMethod, customerName: ticket.customerName,
    lineCount: ticket.lines.length, taxBaseAmount: counted * Number(ticket.taxBaseAmount), taxAmount: counted * Number(ticket.taxAmount),
    totalAmount: counted * Number(ticket.totalAmount), status: ticket.status, originalTicketNumber: ticket.originalTicketNumber, currency: ticket.currencyCode,
  }
}

function stockMovementRow(movement: StockMovement, warehouse: string, language: 'es' | 'en'): ReportRow {
  const inbound = ['PURCHASE_RECEIPT', 'ADJUSTMENT_IN', 'TRANSFER_IN', 'SALES_RETURN'].includes(movement.type)
  return {
    id: movement.id, occurredAt: movement.occurredAt, productCode: movement.productCode, productName: movement.productName, warehouse,
    type: movement.type, quantity: (inbound ? 1 : -1) * Number(movement.quantity), balanceAfter: Number(movement.balanceAfter),
    origin: movementOrigin(movement, language), reason: reasonLabel(movement.reasonCode, language) || null,
    document: movement.sourceNumber, performedBy: movement.performedByName ?? null, note: movement.note,
  }
}

const round = (value: number) => Math.round(value * 100) / 100

/** Listados de operations-service: devuelven la página plana ({content, totalPages}). */
async function fetchAllFlatPages<T>(path: string): Promise<T[]> {
  const url = new URL(path, window.location.origin)
  const rows: T[] = []
  let page = 0
  let totalPages = 1
  do {
    url.searchParams.set('page', String(page))
    url.searchParams.set('size', '500')
    const response = await apiFetch<FlatPage<T>>(`${url.pathname}${url.search}`)
    rows.push(...response.content)
    totalPages = response.totalPages
    page += 1
    if (page > 100) throw new Error('El informe supera el límite operativo de 50.000 registros.')
  } while (page < totalPages)
  return rows
}

export function sortReportRows(rows: ReportRow[], key: string, direction: SortDirection, locale: string): ReportRow[] {
  const factor = direction === 'asc' ? 1 : -1
  return [...rows].sort((left, right) => compareValues(left[key], right[key], locale) * factor)
}

function compareValues(left: ReportValue | undefined, right: ReportValue | undefined, locale: string) {
  if (left == null && right == null) return 0
  if (left == null) return 1
  if (right == null) return -1
  if (typeof left === 'number' && typeof right === 'number') return left - right
  if (typeof left === 'boolean' && typeof right === 'boolean') return Number(left) - Number(right)
  return String(left).localeCompare(String(right), locale, { numeric: true, sensitivity: 'base' })
}

function filterRows(rows: ReportRow[], query: string) {
  const normalized = query.trim().toLocaleLowerCase()
  if (!normalized) return rows
  return rows.filter((row) => Object.values(row).some((value) => value != null && String(value).toLocaleLowerCase().includes(normalized)))
}

async function fetchAllPages<T>(path: string): Promise<T[]> {
  const url = new URL(path, window.location.origin)
  const rows: T[] = []
  let page = 0
  let totalPages = 1
  do {
    url.searchParams.set('page', String(page))
    url.searchParams.set('size', '200')
    const response = await apiFetch<PageResponse<T>>(`${url.pathname}${url.search}`)
    rows.push(...response.content)
    totalPages = response.page.totalPages
    page += 1
    if (page > 250) throw new Error('El informe supera el límite operativo de 50.000 registros.')
  } while (page < totalPages)
  return rows
}

function withParameters(path: string, values: Record<string, string>) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => { if (value.trim()) params.set(key, value.trim()) })
  const query = params.toString()
  return query ? `${path}?${query}` : path
}

async function mapConcurrent<T, R>(items: T[], limit: number, mapper: (item: T) => Promise<R>): Promise<R[]> {
  const results = new Array<R>(items.length)
  let cursor = 0
  async function worker() {
    while (cursor < items.length) {
      const index = cursor++
      results[index] = await mapper(items[index])
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker))
  return results
}

function formatReportValue(value: ReportValue, field: ReportField, currency: string, language: 'es' | 'en', locale: string) {
  if (value == null || value === '') return '—'
  if (field.type === 'currency') return formatCurrency(value as number | string, currency, locale)
  if (field.type === 'number') return formatNumber(value as number | string, locale)
  if (field.type === 'percentage') return `${formatNumber(value as number | string, locale, 4)} %`
  if (field.type === 'date') return formatDate(String(value), locale)
  if (field.type === 'datetime') return formatDateTime(String(value), locale)
  if (field.type === 'boolean') return value ? (language === 'es' ? 'Activo' : 'Active') : (language === 'es' ? 'Inactivo' : 'Inactive')
  if (field.type === 'status') return statusLabel(String(value), language)
  return String(value)
}

function statusLabel(value: string, language: 'es' | 'en') {
  const values: Record<string, [string, string]> = {
    DRAFT: ['Borrador', 'Draft'], SENT: ['Enviado', 'Sent'], ACCEPTED: ['Aceptado', 'Accepted'], REJECTED: ['Rechazado', 'Rejected'], EXPIRED: ['Caducado', 'Expired'], CONVERTED: ['Convertido', 'Converted'],
    CONFIRMED: ['Confirmado', 'Confirmed'], CANCELLED: ['Cancelado', 'Cancelled'], PENDING: ['Pendiente', 'Pending'], PARTIALLY_PAID: ['Cobrado parcialmente', 'Partially paid'], PAID: ['Cobrado', 'Paid'], NOT_APPLICABLE: ['No aplicable', 'Not applicable'],
    SALES_ORDER: ['Pedido de venta', 'Sales order'], DELIVERY_NOTE: ['Albarán', 'Delivery note'], INVOICE: ['Factura', 'Invoice'], RECTIFYING_INVOICE: ['Factura rectificativa', 'Corrective invoice'], WORK_ORDER: ['Parte de trabajo', 'Work order'], QUOTE: ['Presupuesto', 'Quote'],
    UNIT: ['Unidad', 'Unit'], METER: ['Metro', 'Metre'], SQUARE_METER: ['Metro cuadrado', 'Square metre'], CUBIC_METER: ['Metro cúbico', 'Cubic metre'], KILOGRAM: ['Kilogramo', 'Kilogram'], LITER: ['Litro', 'Litre'], HOUR: ['Hora', 'Hour'],
    WARN: ['Avisar', 'Warn'], REQUIRE_CONFIRMATION: ['Requiere confirmación', 'Requires confirmation'], BLOCK: ['Bloquear', 'Block'],
    UNPAID: ['Pendiente', 'Unpaid'], OVERDUE: ['Vencido', 'Overdue'], RETURNED: ['Devuelto', 'Returned'], REMITTED: ['En remesa', 'In remittance'],
    COLLECTED: ['Cobrado', 'Collected'], SALE: ['Venta', 'Sale'], REFUND: ['Devolución', 'Refund'], COMPLETED: ['Completado', 'Completed'], VOIDED: ['Anulado', 'Voided'],
    CASH: ['Efectivo', 'Cash'], CARD: ['Tarjeta', 'Card'], MIXED: ['Mixto', 'Mixed'], BANK_TRANSFER: ['Transferencia', 'Bank transfer'], DIRECT_DEBIT: ['Domiciliación', 'Direct debit'], CHEQUE: ['Cheque', 'Cheque'], OTHER: ['Otro', 'Other'],
    PURCHASE_RECEIPT: ['Entrada de compra', 'Purchase receipt'], PURCHASE_REVERSAL: ['Anulación de compra', 'Purchase reversal'], ADJUSTMENT_IN: ['Entrada / ajuste', 'Entry / adjustment'], ADJUSTMENT_OUT: ['Salida / ajuste', 'Exit / adjustment'],
    TRANSFER_IN: ['Traspaso recibido', 'Transfer in'], TRANSFER_OUT: ['Traspaso enviado', 'Transfer out'], SALES_ISSUE: ['Salida de venta', 'Sales issue'], SALES_RETURN: ['Devolución de venta', 'Sales return'],
  }
  return values[value]?.[language === 'es' ? 0 : 1] ?? value
}

function filterDescription(filters: ReportFilters, module: ReportModule, language: 'es' | 'en') {
  const values: string[] = []
  if (filters.query.trim()) values.push(`${language === 'es' ? 'Búsqueda' : 'Search'}: “${filters.query.trim()}”`)
  if (filters.active) values.push(filters.active === 'true' ? copy[language].active : copy[language].inactive)
  if (filters.status) values.push(`${copy[language].status}: ${statusLabel(filters.status, language)}`)
  if (filters.type) values.push(`${copy[language].type}: ${statusLabel(filters.type, language)}`)
  if (filters.fromDate && !filters.period) values.push(`${copy[language].from}: ${formatDate(filters.fromDate, language === 'es' ? 'es-ES' : 'en-GB')}`)
  if (filters.period) values.push(`${language === 'es' ? 'Periodo' : 'Period'}: ${filters.period}`)
  else if (filters.toDate) values.push(`${copy[language].to}: ${formatDate(filters.toDate, language === 'es' ? 'es-ES' : 'en-GB')}`)
  if (!values.length && module.filterKind === 'receivables') return language === 'es' ? 'Todas las facturas con saldo pendiente.' : 'All invoices with an outstanding balance.'
  return values.join(' · ') || copy[language].noFilters
}

function calculateReportTotals(rows: ReportRow[], fields: ReportField[], baseCurrency: string) {
  const totals: Array<{ currency: string; key: string; field: ReportField; value: number }> = []
  const currencies = [...new Set(rows.map((row) => row.currency || baseCurrency))].sort()
  for (const currency of currencies) for (const item of fields.filter((fieldItem) => fieldItem.summable)) {
    totals.push({ currency, key: item.key, field: item, value: rows.filter((row) => (row.currency || baseCurrency) === currency).reduce((sum, row) => sum + Number(row[item.key] ?? 0), 0) })
  }
  return totals
}

function numericField(item: ReportField) { return item.type === 'currency' || item.type === 'number' || item.type === 'percentage' }
function label(value: [string, string], language: 'es' | 'en') { return value[language === 'es' ? 0 : 1] }

export function reportModulesForRoles(roles: readonly UserRoleCode[]) {
  return reportModules.filter((module) => module.roles.some((allowed) => roles.some((assigned) => assigned.toUpperCase() === allowed)))
}
