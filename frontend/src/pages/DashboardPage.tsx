import { ArrowRight, Boxes, CircleDollarSign, FileText, Gauge, Plus, ReceiptText, Target, TrendingDown, TrendingUp, Users } from 'lucide-react'
import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useAuth } from '../auth/AuthContext'
import { EmptyState, LoadingState } from '../components/DataState'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDate } from '../lib/format'
import { documentStatusKey, documentTypeKey } from '../i18n/businessLabels'
import { useTranslation } from '../i18n/I18nProvider'
import type { CommercialDocument, Customer, DailyRevenuePoint, PageResponse, Product, SalesDashboardAnalytics, Supplier } from '../types/api'
import { Link } from '../routing/Router'
import { appRoutes, isRouteAllowed } from '../routing/routes'

interface DashboardData {
  customers?: PageResponse<Customer>
  suppliers?: PageResponse<Supplier>
  products?: PageResponse<Product>
  documents?: PageResponse<CommercialDocument>
  analytics?: SalesDashboardAnalytics
}

export function DashboardPage() {
  const { language, locale, t } = useTranslation()
  const { hasPermission, identity } = useAuth()
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState('')
  const roles = identity?.roles ?? []
  const canVisit = (path: string) => {
    const route = appRoutes.find((candidate) => candidate.path === path)
    return Boolean(route && isRouteAllowed(route, roles))
  }
  const canCustomers = hasPermission('customers:read') && canVisit('/clientes')
  const canSuppliers = hasPermission('suppliers:read') && canVisit('/proveedores')
  const canProducts = hasPermission('products:read') && canVisit('/catalogo')
  const canDocuments = hasPermission('documents:read') && canVisit('/ventas')

  useEffect(() => {
    let active = true
    const next: DashboardData = {}
    const requests: Promise<unknown>[] = []
    if (canCustomers) requests.push(apiFetch<PageResponse<Customer>>('/api/v1/customers?size=1').then((value) => { next.customers = value }))
    if (canSuppliers) requests.push(apiFetch<PageResponse<Supplier>>('/api/v1/suppliers?size=1').then((value) => { next.suppliers = value }))
    if (canProducts) requests.push(apiFetch<PageResponse<Product>>('/api/v1/products?size=1').then((value) => { next.products = value }))
    if (canDocuments) {
      requests.push(apiFetch<PageResponse<CommercialDocument>>('/api/v1/documents?size=6&sort=issueDate,desc').then((value) => { next.documents = value }))
      requests.push(apiFetch<SalesDashboardAnalytics>('/api/v1/sales-dashboard?months=6').then((value) => { next.analytics = value }))
    }
    Promise.allSettled(requests).then((results) => {
      if (!active) return
      const failure = results.find((result): result is PromiseRejectedResult => result.status === 'rejected')
      setError(failure ? errorMessage(failure.reason) : '')
      setData(next)
    })
    return () => { active = false }
  }, [canCustomers, canDocuments, canProducts, canSuppliers])

  const today = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())
  const analytics = data?.analytics
  const samePointChange = analytics && Number(analytics.previousMonthToDate) !== 0
    ? ((Number(analytics.currentMonthTotal) - Number(analytics.previousMonthToDate)) / Number(analytics.previousMonthToDate)) * 100
    : null
  const quickLinks = useMemo(() => [
    { to: '/clientes', icon: Users, title: t('dashboard.newCustomer'), text: t('dashboard.newCustomerDescription') },
    { to: '/catalogo', icon: Boxes, title: t('dashboard.newProduct'), text: t('dashboard.newProductDescription') },
    { to: '/presupuestos', icon: FileText, title: t('dashboard.createQuote'), text: t('dashboard.createQuoteDescription') },
    { to: '/finanzas', icon: ReceiptText, title: t('dashboard.reviewCollections'), text: t('dashboard.reviewCollectionsDescription') },
    { to: '/operaciones', icon: Gauge, title: language === 'es' ? 'Gestionar operaciones' : 'Manage operations', text: language === 'es' ? 'Rutas, expediciones y procesos' : 'Routes, shipments and workflows' },
  ].filter((link) => {
    const route = appRoutes.find((candidate) => candidate.path === link.to)
    return route && isRouteAllowed(route, identity?.roles ?? [])
  }), [identity?.roles, language, t])

  return (
    <div className="page-stack">
      <PageHeader eyebrow={today} title={t('dashboard.title')} description={t('dashboard.description')} actions={canDocuments ? <Link className="button button-primary" to="/ventas"><Plus size={17} />{t('dashboard.newDocument')}</Link> : undefined} />
      {error && <div className="inline-error">{error}</div>}
      {!data ? <LoadingState label={t('dashboard.loading')} /> : <>
        {analytics && <>
          <section className="financial-kpi-grid" aria-label={language === 'es' ? 'Indicadores económicos del mes' : 'Monthly financial indicators'}>
            <FinancialKpi icon={CircleDollarSign} label={language === 'es' ? 'Facturado este mes' : 'Invoiced this month'} value={formatCurrency(analytics.currentMonthTotal, analytics.currency, locale)} detail={language === 'es' ? `Hasta el ${formatDate(analytics.asOfDate, locale)}` : `Through ${formatDate(analytics.asOfDate, locale)}`} />
            <FinancialKpi icon={samePointChange !== null && samePointChange < 0 ? TrendingDown : TrendingUp} label={language === 'es' ? 'Frente al mismo día' : 'Versus the same day'} value={samePointChange === null ? '—' : `${samePointChange >= 0 ? '+' : ''}${samePointChange.toFixed(1)}%`} detail={`${formatCurrency(analytics.previousMonthToDate, analytics.currency, locale)} ${language === 'es' ? 'el mes anterior' : 'last month'}`} tone={samePointChange === null ? 'neutral' : samePointChange >= 0 ? 'positive' : 'negative'} />
            <FinancialKpi icon={Target} label={language === 'es' ? 'Ritmo esperado hoy' : 'Expected pace today'} value={formatCurrency(analytics.expectedByToday, analytics.currency, locale)} detail={language === 'es' ? 'Objetivo proporcional al mes anterior' : 'Target paced from last month'} />
            <PaceCard analytics={analytics} locale={locale} language={language} />
          </section>

          <section className="analytics-grid">
            <div className="panel revenue-chart-panel">
              <div className="panel-heading"><div><span className="eyebrow">{language === 'es' ? 'Evolución acumulada' : 'Cumulative trend'}</span><h2>{language === 'es' ? 'Facturación diaria comparada' : 'Daily invoicing comparison'}</h2></div><span className={`trend-pill ${Number(analytics.varianceAmount) >= 0 ? 'positive' : 'negative'}`}>{Number(analytics.varianceAmount) >= 0 ? '+' : ''}{formatCurrency(analytics.varianceAmount, analytics.currency, locale)}</span></div>
              <RevenueTrendChart points={analytics.dailyRevenue} currency={analytics.currency} locale={locale} language={language} />
            </div>
            <div className="panel monthly-chart-panel">
              <div className="panel-heading"><div><span className="eyebrow">{language === 'es' ? 'Últimos 6 meses' : 'Last 6 months'}</span><h2>{language === 'es' ? 'Perspectiva mensual' : 'Monthly perspective'}</h2></div></div>
              <MonthlyRevenueChart analytics={analytics} locale={locale} language={language} />
            </div>
          </section>
        </>}

        {(data.customers || data.products || data.documents) && <section className="metric-grid dashboard-count-grid" aria-label={t('dashboard.metricsLabel')}>
          {data.customers && <MetricCard label={t('dashboard.customers')} value={data.customers.page.totalElements} hint={t('dashboard.customerHint')} icon={Users} to="/clientes" />}
          {data.products && <MetricCard label={t('dashboard.products')} value={data.products.page.totalElements} hint={t('dashboard.productHint')} icon={Boxes} to="/catalogo" />}
          {data.documents && <MetricCard label={t('dashboard.documents')} value={data.documents.page.totalElements} hint={t('dashboard.documentHint')} icon={FileText} to="/ventas" />}
        </section>}

        <section className="dashboard-grid">
          {data.documents && <div className="panel recent-panel">
            <div className="panel-heading"><div><span className="eyebrow">{t('dashboard.activity')}</span><h2>{t('dashboard.recentDocuments')}</h2></div><Link to="/ventas" className="text-link">{t('dashboard.viewAll')} <ArrowRight size={15} /></Link></div>
            {data.documents.content.length === 0 ? <EmptyState title={t('dashboard.noDocuments')} description={t('dashboard.noDocumentsDescription')} action={<Link to="/ventas" className="button button-secondary">{t('dashboard.goToSales')}</Link>} /> :
              <div className="compact-list">{data.documents.content.map((document) => <div className="compact-row" key={document.id}><span className="row-icon"><ReceiptText size={17} /></span><span className="row-main"><strong>{document.number}</strong><small>{document.customerName} · {formatDate(document.issueDate, locale)}</small></span><span className="row-type">{t(documentTypeKey[document.type])}</span><StatusBadge tone={document.status === 'CONFIRMED' ? 'success' : document.status === 'CANCELLED' ? 'danger' : 'neutral'}>{t(documentStatusKey[document.status])}</StatusBadge><strong className="row-amount">{formatCurrency(document.totalAmount, document.currency, locale)}</strong></div>)}</div>}
          </div>}

          <aside className="panel quick-panel">
            <div className="panel-heading"><div><span className="eyebrow">{t('dashboard.shortcuts')}</span><h2>{t('dashboard.frequentTasks')}</h2></div></div>
            {quickLinks.map((link) => <QuickLink key={link.to} {...link} />)}
            {data.suppliers && <div className="business-note"><span className="pear-dot" /><div><strong>{t('dashboard.suppliersActive', { count: data.suppliers.page.totalElements })}</strong><p>{t('dashboard.suppliersNote')}</p></div></div>}
          </aside>
        </section>
      </>}
    </div>
  )
}

function FinancialKpi({ icon: Icon, label, value, detail, tone = 'neutral' }: { icon: typeof Gauge; label: string; value: string; detail: string; tone?: 'neutral' | 'positive' | 'negative' }) {
  return <article className={`financial-kpi ${tone}`}><span className="metric-icon"><Icon size={20} /></span><span>{label}</span><strong>{value}</strong><small>{detail}</small></article>
}

function PaceCard({ analytics, locale, language }: { analytics: SalesDashboardAnalytics; locale: string; language: 'es' | 'en' }) {
  const performance = Number(analytics.performancePercentage)
  const hasBaseline = Number(analytics.expectedByToday) > 0
  const positive = !hasBaseline || Number(analytics.varianceAmount) >= 0
  const gaugeValue = Math.max(0, Math.min(100, performance))
  const status = !hasBaseline
    ? (language === 'es' ? 'Sin referencia anterior' : 'No previous baseline')
    : positive ? (language === 'es' ? 'Mes en buen ritmo' : 'Month on track') : (language === 'es' ? 'Mes por debajo del ritmo' : 'Month below pace')
  return <article className={`pace-card ${hasBaseline && !positive ? 'negative' : 'positive'} ${hasBaseline ? '' : 'without-baseline'}`}><div className="pace-gauge" style={{ '--pace': gaugeValue } as CSSProperties}><span><strong>{hasBaseline ? `${performance.toFixed(0)}%` : '—'}</strong><small>{language === 'es' ? 'del ritmo' : 'of pace'}</small></span></div><div><span>{status}</span><strong>{hasBaseline ? `${positive ? '+' : ''}${formatCurrency(analytics.varianceAmount, analytics.currency, locale)}` : formatCurrency(analytics.currentMonthTotal, analytics.currency, locale)}</strong><small>{language === 'es' ? `${analytics.monthProgressPercentage}% del mes transcurrido` : `${analytics.monthProgressPercentage}% of month elapsed`}</small></div></article>
}

function RevenueTrendChart({ points, currency, locale, language }: { points: DailyRevenuePoint[]; currency: string; locale: string; language: 'es' | 'en' }) {
  const width = 760; const height = 280; const left = 58; const right = 18; const top = 18; const bottom = 38
  const values = points.flatMap((point) => [point.currentCumulative, point.previousCumulative]).filter((value): value is number => value !== null).map(Number)
  const maximum = Math.max(...values, 1)
  const x = (day: number) => left + ((day - 1) / Math.max(1, points.length - 1)) * (width - left - right)
  const y = (value: number) => top + (1 - value / maximum) * (height - top - bottom)
  const path = (selector: (point: DailyRevenuePoint) => number | null) => points.filter((point) => selector(point) !== null).map((point, index) => `${index === 0 ? 'M' : 'L'}${x(point.day).toFixed(1)},${y(Number(selector(point))).toFixed(1)}`).join(' ')
  const currentPoints = points.filter((point) => point.currentCumulative !== null)
  const currentPath = path((point) => point.currentCumulative)
  const previousPath = path((point) => point.previousCumulative)
  const areaPath = currentPoints.length ? `${currentPath} L${x(currentPoints.at(-1)!.day).toFixed(1)},${height - bottom} L${x(currentPoints[0].day).toFixed(1)},${height - bottom} Z` : ''
  const yTicks = [0, .25, .5, .75, 1]
  return <div className="chart-wrap"><div className="chart-legend"><span className="current">{language === 'es' ? 'Mes actual' : 'Current month'}</span><span className="previous">{language === 'es' ? 'Mes anterior' : 'Previous month'}</span></div><svg className="revenue-line-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={language === 'es' ? 'Comparativa acumulada de facturación diaria' : 'Cumulative daily invoicing comparison'}>
    <defs><linearGradient id="revenue-area" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--pear-500)" stopOpacity=".25" /><stop offset="100%" stopColor="var(--pear-500)" stopOpacity=".02" /></linearGradient></defs>
    {yTicks.map((tick) => <g key={tick}><line x1={left} x2={width - right} y1={y(maximum * tick)} y2={y(maximum * tick)} className="chart-grid-line" /><text x={left - 10} y={y(maximum * tick) + 4} textAnchor="end">{compactAmount(maximum * tick, currency, locale)}</text></g>)}
    {[1, Math.ceil(points.length / 2), points.length].map((day) => <text key={day} x={x(day)} y={height - 12} textAnchor={day === 1 ? 'start' : day === points.length ? 'end' : 'middle'}>{language === 'es' ? `Día ${day}` : `Day ${day}`}</text>)}
    {areaPath && <path d={areaPath} fill="url(#revenue-area)" />}
    <path d={previousPath} className="previous-line" />
    <path d={currentPath} className="current-line" />
    {currentPoints.map((point) => <circle key={point.day} cx={x(point.day)} cy={y(Number(point.currentCumulative))} r="3" className="current-point"><title>{`${language === 'es' ? 'Día' : 'Day'} ${point.day}: ${formatCurrency(point.currentCumulative ?? 0, currency, locale)}`}</title></circle>)}
  </svg></div>
}

function MonthlyRevenueChart({ analytics, locale, language }: { analytics: SalesDashboardAnalytics; locale: string; language: 'es' | 'en' }) {
  const maximum = Math.max(...analytics.monthlyRevenue.map((point) => Number(point.total)), 1)
  return <div className="monthly-bars">{analytics.monthlyRevenue.map((point, index) => {
    const total = Number(point.total); const current = index === analytics.monthlyRevenue.length - 1
    const month = new Intl.DateTimeFormat(locale, { month: 'short' }).format(new Date(`${point.month}T12:00:00`)).replace('.', '')
    return <div className={current ? 'monthly-bar current' : 'monthly-bar'} key={point.month}><span className="bar-value">{compactAmount(total, analytics.currency, locale)}</span><div className="bar-track"><span style={{ height: `${Math.max(total > 0 ? 5 : 0, (total / maximum) * 100)}%` }} title={`${month}: ${formatCurrency(total, analytics.currency, locale)}`} /></div><strong>{month}</strong></div>
  })}<p>{language === 'es' ? 'El mes actual solo incluye lo facturado hasta hoy.' : 'The current month only includes invoicing through today.'}</p></div>
}

function compactAmount(value: number, currency: string, locale: string) {
  return new Intl.NumberFormat(locale, { style: 'currency', currency, notation: 'compact', maximumFractionDigits: 1 }).format(value)
}

function MetricCard({ label, value, hint, icon: Icon, to }: { label: string; value: string | number; hint: string; icon: typeof Users; to: string }) {
  return <Link to={to} className="metric-card"><span className="metric-icon"><Icon size={20} /></span><span className="metric-label">{label}</span><strong>{value}</strong><span className="metric-hint">{hint}<ArrowRight size={14} /></span></Link>
}

function QuickLink({ to, icon: Icon, title, text }: { to: string; icon: typeof Users; title: string; text: string }) {
  return <Link to={to} className="quick-link"><span><Icon size={18} /></span><div><strong>{title}</strong><small>{text}</small></div><ArrowRight size={16} /></Link>
}
