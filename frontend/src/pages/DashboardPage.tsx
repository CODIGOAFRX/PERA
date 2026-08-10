import { ArrowRight, Boxes, CircleDollarSign, FileText, Plus, ReceiptText, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDate } from '../lib/format'
import { documentStatusKey, documentTypeKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import type { CommercialDocument, Customer, PageResponse, Product, Supplier } from '../types/api'
import { Link } from '../routing/Router'

interface DashboardData {
  customers: PageResponse<Customer>
  suppliers: PageResponse<Supplier>
  products: PageResponse<Product>
  documents: PageResponse<CommercialDocument>
}

export function DashboardPage() {
  const { locale, t } = useTranslation()
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

  const today = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())
  const visibleInvoices = data?.documents.content.filter((document) => document.type === 'INVOICE') ?? []
  const totalsByCurrency = visibleInvoices.reduce<Record<string, number>>((totals, document) => {
    totals[document.currency] = (totals[document.currency] ?? 0) + Number(document.totalAmount)
    return totals
  }, {})
  const invoiced = Object.entries(totalsByCurrency).map(([currency, total]) => formatCurrency(total, currency, locale)).join(' · ') || formatCurrency(0, 'EUR', locale)

  return (
    <div className="page-stack">
      <PageHeader eyebrow={today} title={t('dashboard.title')} description={t('dashboard.description')} actions={<Link className="button button-primary" to="/ventas"><Plus size={17} />{t('dashboard.newDocument')}</Link>} />
      {error && <div className="inline-error">{error}</div>}
      {!data && !error ? <LoadingState label={t('dashboard.loading')} /> : data && <>
        <section className="metric-grid" aria-label={t('dashboard.metricsLabel')}>
          <MetricCard label={t('dashboard.customers')} value={data.customers.page.totalElements} hint={t('dashboard.customerHint')} icon={Users} to="/clientes" />
          <MetricCard label={t('dashboard.products')} value={data.products.page.totalElements} hint={t('dashboard.productHint')} icon={Boxes} to="/catalogo" />
          <MetricCard label={t('dashboard.documents')} value={data.documents.page.totalElements} hint={t('dashboard.documentHint')} icon={FileText} to="/ventas" />
          <MetricCard label={t('dashboard.recentInvoicing')} value={invoiced} hint={t('dashboard.visibleInvoices', { count: visibleInvoices.length })} icon={CircleDollarSign} to="/ventas" featured />
        </section>

        <section className="dashboard-grid">
          <div className="panel recent-panel">
            <div className="panel-heading"><div><span className="eyebrow">{t('dashboard.activity')}</span><h2>{t('dashboard.recentDocuments')}</h2></div><Link to="/ventas" className="text-link">{t('dashboard.viewAll')} <ArrowRight size={15} /></Link></div>
            {data.documents.content.length === 0 ? <EmptyState title={t('dashboard.noDocuments')} description={t('dashboard.noDocumentsDescription')} action={<Link to="/ventas" className="button button-secondary">{t('dashboard.goToSales')}</Link>} /> :
              <div className="compact-list">{data.documents.content.map((document) => <div className="compact-row" key={document.id}><span className="row-icon"><ReceiptText size={17} /></span><span className="row-main"><strong>{document.number}</strong><small>{document.customerName} · {formatDate(document.issueDate, locale)}</small></span><span className="row-type">{t(documentTypeKey[document.type])}</span><StatusBadge tone={document.status === 'CONFIRMED' ? 'success' : document.status === 'CANCELLED' ? 'danger' : 'neutral'}>{t(documentStatusKey[document.status])}</StatusBadge><strong className="row-amount">{formatCurrency(document.totalAmount, document.currency, locale)}</strong></div>)}</div>}
          </div>

          <aside className="panel quick-panel">
            <div className="panel-heading"><div><span className="eyebrow">{t('dashboard.shortcuts')}</span><h2>{t('dashboard.frequentTasks')}</h2></div></div>
            <QuickLink to="/clientes" icon={Users} title={t('dashboard.newCustomer')} text={t('dashboard.newCustomerDescription')} />
            <QuickLink to="/catalogo" icon={Boxes} title={t('dashboard.newProduct')} text={t('dashboard.newProductDescription')} />
            <QuickLink to="/presupuestos" icon={FileText} title={t('dashboard.createQuote')} text={t('dashboard.createQuoteDescription')} />
            <QuickLink to="/finanzas" icon={ReceiptText} title={t('dashboard.reviewCollections')} text={t('dashboard.reviewCollectionsDescription')} />
            <div className="business-note"><span className="pear-dot" /><div><strong>{t('dashboard.suppliersActive', { count: data.suppliers.page.totalElements })}</strong><p>{t('dashboard.suppliersNote')}</p></div></div>
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
