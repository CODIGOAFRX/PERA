import { Leaf, LogOut, Menu, PanelLeftClose, PanelLeftOpen, X } from 'lucide-react'
import { useEffect, useMemo, useState, type CSSProperties, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useTranslation } from '../i18n/I18nProvider'
import { Link, useRouter } from '../routing/Router'
import { appRoutes, isRouteActive, isRouteAllowed, type NavigationGroup } from '../routing/routes'
import { LanguageSelector } from './LanguageSelector'
import { PearBrandMark } from './PearBrandMark'

const SIDEBAR_WIDTH_KEY = 'pera.sidebar.width'
const DEFAULT_WIDTH = 244
const MIN_VISIBLE_WIDTH = 104
const HIDE_THRESHOLD = 88
const COMPACT_THRESHOLD = 184
const MAX_WIDTH = 360

function readSidebarWidth() {
  const stored = Number(localStorage.getItem(SIDEBAR_WIDTH_KEY))
  return stored === 0 || (stored >= MIN_VISIBLE_WIDTH && stored <= MAX_WIDTH) ? stored : DEFAULT_WIDTH
}

function normalizeWidth(value: number) {
  if (value < HIDE_THRESHOLD) return 0
  return Math.min(MAX_WIDTH, Math.max(MIN_VISIBLE_WIDTH, value))
}

export function AppShell({ children }: { children: ReactNode }) {
  const { company, identity, logout } = useAuth()
  const { path } = useRouter()
  const { language, t } = useTranslation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [sidebarWidth, setSidebarWidth] = useState(readSidebarWidth)
  const [resizing, setResizing] = useState(false)
  const compact = sidebarWidth > 0 && sidebarWidth < COMPACT_THRESHOLD
  const visibleRoutes = useMemo(() => appRoutes.filter((route) => isRouteAllowed(route, identity?.roles ?? [])), [identity?.roles])
  const navigationGroups = visibleRoutes.reduce<Array<{ id: NavigationGroup; labelKey: typeof appRoutes[number]['navigation']['groupLabelKey']; routes: typeof appRoutes }>>((groups, route) => {
    const existing = groups.find((group) => group.id === route.navigation.group)
    if (existing) existing.routes.push(route)
    else groups.push({ id: route.navigation.group, labelKey: route.navigation.groupLabelKey, routes: [route] })
    return groups
  }, [])

  useEffect(() => localStorage.setItem(SIDEBAR_WIDTH_KEY, String(sidebarWidth)), [sidebarWidth])

  const resize = (event: PointerEvent<HTMLButtonElement>) => {
    if (resizing) setSidebarWidth(normalizeWidth(event.clientX))
  }
  const stopResize = (event: PointerEvent<HTMLButtonElement>) => {
    if (!resizing) return
    event.currentTarget.releasePointerCapture(event.pointerId)
    setResizing(false)
  }
  const startResize = (event: PointerEvent<HTMLButtonElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    setResizing(true)
  }
  const resizeWithKeyboard = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      setSidebarWidth((width) => width <= MIN_VISIBLE_WIDTH ? 0 : normalizeWidth(width - 12))
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      setSidebarWidth((width) => normalizeWidth((width || MIN_VISIBLE_WIDTH) + 12))
    } else if (event.key === 'Home') {
      event.preventDefault(); setSidebarWidth(0)
    } else if (event.key === 'End') {
      event.preventDefault(); setSidebarWidth(MAX_WIDTH)
    }
  }

  const shellStyle = { '--sidebar-width': `${sidebarWidth}px` } as CSSProperties
  const role = identity?.roles[0] ?? ''

  return (
    <div className={`${resizing ? 'app-shell resizing' : 'app-shell'} ${compact ? 'sidebar-compact' : ''}`} style={shellStyle}>
      <aside className={`sidebar ${sidebarWidth === 0 ? 'sidebar-hidden' : ''} ${mobileOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <PearBrandMark />
          <span className="brand-wordmark"><strong>PERA</strong><small>ERP</small></span>
          <button type="button" className="icon-button sidebar-collapse" onClick={() => setSidebarWidth(0)} aria-label={t('nav.collapseSidebar')} title={t('nav.collapseSidebar')}><PanelLeftClose size={18} /></button>
          <button type="button" className="icon-button sidebar-close" onClick={() => setMobileOpen(false)} aria-label={t('nav.closeMenu')}><X size={20} /></button>
        </div>
        <div className="company-chip">
          <span className="company-avatar">{company?.code?.slice(0, 2) || 'PE'}</span>
          <span className="company-copy"><small>{t('nav.activeCompany')}</small><strong>{company?.name || 'PERA ERP'}</strong></span>
        </div>
        <nav className="main-nav" aria-label={t('nav.main')}>
          {navigationGroups.map((group) => <div className="nav-group" key={group.id}>
            <span className="nav-label">{t(group.labelKey)}</span>
            {group.routes.map((route) => {
              const Icon = route.navigation.icon
              return <Link key={route.id} to={route.path} title={compact ? t(route.navigation.labelKey) : undefined} onClick={() => setMobileOpen(false)} className={isRouteActive(route, path) ? 'nav-link active' : 'nav-link'}>
                <Icon size={19} strokeWidth={1.8} /><span>{t(route.navigation.labelKey)}</span>
              </Link>
            })}
          </div>)}
        </nav>
        <div className="sidebar-footer">
          <div className="user-chip" title={`${identity?.displayName ?? ''} · ${roleLabel(role, language)}`}>
            <span>{identity?.displayName?.slice(0, 2).toUpperCase() || 'US'}</span>
            <div><strong>{identity?.displayName}</strong><small>{roleLabel(role, language)}</small></div>
          </div>
          <LanguageSelector compact={compact} />
          <div className="sidebar-version"><Leaf size={16} /><span>PERA ERP <small>v0.2</small></span></div>
          <button type="button" className="nav-link logout-link" onClick={logout} title={compact ? t('nav.logout') : undefined}><LogOut size={18} /><span>{t('nav.logout')}</span></button>
        </div>
        <button type="button" className="sidebar-resizer" role="separator" aria-label={t('nav.resizeSidebar')} aria-orientation="vertical" aria-valuemin={MIN_VISIBLE_WIDTH} aria-valuemax={MAX_WIDTH} aria-valuenow={sidebarWidth || MIN_VISIBLE_WIDTH} onPointerDown={startResize} onPointerMove={resize} onPointerUp={stopResize} onPointerCancel={stopResize} onDoubleClick={() => setSidebarWidth(DEFAULT_WIDTH)} onKeyDown={resizeWithKeyboard} />
      </aside>
      {sidebarWidth === 0 && <button type="button" className="sidebar-reveal" onClick={() => setSidebarWidth(DEFAULT_WIDTH)} aria-label={t('nav.expandSidebar')} title={t('nav.expandSidebar')}><PanelLeftOpen size={19} /></button>}
      {mobileOpen && <button className="sidebar-scrim" type="button" aria-label={t('nav.closeMenu')} onClick={() => setMobileOpen(false)} />}
      <div className="content-shell">
        <header className="mobile-header">
          <button type="button" className="icon-button" onClick={() => setMobileOpen(true)} aria-label={t('nav.openMenu')}><Menu size={21} /></button>
          <div className="brand compact"><PearBrandMark /><span><strong>PERA</strong><small>ERP</small></span></div>
          <span className="mobile-company">{company?.code || 'DEMO'}</span>
        </header>
        <main className="app-main">{children}</main>
      </div>
    </div>
  )
}

function roleLabel(role: string, language: 'es' | 'en') {
  const labels: Record<string, [string, string]> = {
    OWNER: ['Propietario', 'Owner'], ADMIN: ['Administrador', 'Administrator'], ECONOMY: ['Economía', 'Economy'],
    LOGISTICS: ['Logística', 'Logistics'], CATALOG: ['Catálogo', 'Catalogue'],
  }
  return labels[role]?.[language === 'es' ? 0 : 1] ?? role
}
