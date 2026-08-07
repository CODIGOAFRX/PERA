import { ArrowRight, Boxes, CircleDollarSign, FileText, Plus, ReceiptText, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { apiFetch, errorMessage } from '../lib/api'
import { documentStatusLabel, documentTypeLabel, formatCurrency, formatDate } from '../lib/format'
import type { CommercialDocument, Customer, PageResponse, Product, Supplier } from '../types/api'
import { Link } from '../routing/Router'

interface DashboardData {
  customers: PageResponse<Customer>
  suppliers: PageResponse<Supplier>
  products: PageResponse<Product>
  documents: PageResponse<CommercialDocument>
}

export function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([
      apiFetch<PageResponse<Customer>>('/api/v1/customers?size=1'),
      apiFetch<PageResponse<Supplier>>('/api/v1/suppliers?size=1'),
      apiFetch<PageResponse<Product>>('/api/v1/products?size=1'),
      apiFetch<PageResponse<CommercialDocument>>('/api/v1/documents?size=6&sort=issueDate,desc'),
    ]).then(([customers, suppliers, products, documents]) => setData({ customers, suppliers, products, documents }))
      .catch((cause) => setError(errorMessage(cause)))
  }, [])

  const today = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())
  const visibleInvoices = data?.documents.content.filter((document) => document.type === 'INVOICE') ?? []
  const invoiced = visibleInvoices.reduce((total, document) => total + Number(document.totalAmount), 0)

  return (
    <div className="page-stack">
      <PageHeader eyebrow={today} title="Resumen del negocio" description="Una vista rápida de la actividad más reciente." actions={<Link className="button button-primary" to="/ventas"><Plus size={17} />Nuevo documento</Link>} />
      {error && <div className="inline-error">{error}</div>}
      {!data && !error ? <LoadingState label="Preparando el resumen…" /> : data && <>
        <section className="metric-grid" aria-label="Indicadores principales">
          <MetricCard label="Clientes" value={data.customers.page.totalElements} hint="Fichas comerciales" icon={Users} to="/clientes" />
          <MetricCard label="Productos" value={data.products.page.totalElements} hint="Artículos y servicios" icon={Boxes} to="/catalogo" />
          <MetricCard label="Documentos" value={data.documents.page.totalElements} hint="Actividad comercial" icon={FileText} to="/ventas" />
          <MetricCard label="Facturado reciente" value={formatCurrency(invoiced)} hint={`${visibleInvoices.length} facturas visibles`} icon={CircleDollarSign} to="/ventas" featured />
        </section>

        <section className="dashboard-grid">
          <div className="panel recent-panel">
            <div className="panel-heading"><div><span className="eyebrow">Actividad</span><h2>Documentos recientes</h2></div><Link to="/ventas" className="text-link">Ver todos <ArrowRight size={15} /></Link></div>
            {data.documents.content.length === 0 ? <EmptyState title="Aún no hay documentos" description="Crea el primer presupuesto o factura para empezar." action={<Link to="/ventas" className="button button-secondary">Ir a ventas</Link>} /> :
              <div className="compact-list">{data.documents.content.map((document) => <div className="compact-row" key={document.id}><span className="row-icon"><ReceiptText size={17} /></span><span className="row-main"><strong>{document.number}</strong><small>{document.customerName} · {formatDate(document.issueDate)}</small></span><span className="row-type">{documentTypeLabel[document.type]}</span><StatusBadge tone={document.status === 'CONFIRMED' ? 'success' : document.status === 'CANCELLED' ? 'danger' : 'neutral'}>{documentStatusLabel[document.status]}</StatusBadge><strong className="row-amount">{formatCurrency(document.totalAmount)}</strong></div>)}</div>}
          </div>

          <aside className="panel quick-panel">
            <div className="panel-heading"><div><span className="eyebrow">Accesos</span><h2>Tareas frecuentes</h2></div></div>
            <QuickLink to="/clientes" icon={Users} title="Nuevo cliente" text="Añade sus datos comerciales" />
            <QuickLink to="/catalogo" icon={Boxes} title="Nuevo producto" text="Actualiza el catálogo" />
            <QuickLink to="/ventas" icon={FileText} title="Crear presupuesto" text="Inicia una nueva venta" />
            <QuickLink to="/finanzas" icon={ReceiptText} title="Revisar cobros" text="Consulta formas y vencimientos" />
            <div className="business-note"><span className="pear-dot" /><div><strong>{data.suppliers.page.totalElements} proveedores activos en el maestro</strong><p>La base está preparada para el futuro flujo de compras.</p></div></div>
          </aside>
        </section>
      </>}
    </div>
  )
}

function MetricCard({ label, value, hint, icon: Icon, to, featured = false }: { label: string; value: string | number; hint: string; icon: typeof Users; to: string; featured?: boolean }) {
  return <Link to={to} className={`metric-card ${featured ? 'metric-featured' : ''}`}><span className="metric-icon"><Icon size={20} /></span><span className="metric-label">{label}</span><strong>{value}</strong><span className="metric-hint">{hint}<ArrowRight size={14} /></span></Link>
}

function QuickLink({ to, icon: Icon, title, text }: { to: string; icon: typeof Users; title: string; text: string }) {
  return <Link to={to} className="quick-link"><span><Icon size={18} /></span><div><strong>{title}</strong><small>{text}</small></div><ArrowRight size={16} /></Link>
}
