import { Leaf, LogOut, Menu, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useTranslation } from '../i18n/I18nProvider'
import { Link, useRouter } from '../routing/Router'
import { appRoutes, isRouteActive, type NavigationGroup } from '../routing/routes'
import { LanguageSelector } from './LanguageSelector'

export function AppShell({ children }: { children: ReactNode }) {
  const { company, logout } = useAuth()
  const { path } = useRouter()
  const { t } = useTranslation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const navigationGroups = appRoutes.reduce<Array<{ id: NavigationGroup; labelKey: typeof appRoutes[number]['navigation']['groupLabelKey']; routes: typeof appRoutes }>>((groups, route) => {
    const existing = groups.find((group) => group.id === route.navigation.group)
    if (existing) existing.routes.push(route)
    else groups.push({ id: route.navigation.group, labelKey: route.navigation.groupLabelKey, routes: [route] })
    return groups
  }, [])

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">P</span>
          <span><strong>PERA</strong><small>ERP</small></span>
          <button type="button" className="icon-button sidebar-close" onClick={() => setMobileOpen(false)} aria-label={t('nav.closeMenu')}><X size={20} /></button>
        </div>
        <div className="company-chip">
          <span className="company-avatar">{company?.code?.slice(0, 2) || 'PE'}</span>
          <span><small>{t('nav.activeCompany')}</small><strong>{company?.name || 'PERA ERP'}</strong></span>
        </div>
        <nav className="main-nav" aria-label={t('nav.main')}>
          {navigationGroups.map((group) => <div className="nav-group" key={group.id}>
            <span className="nav-label">{t(group.labelKey)}</span>
            {group.routes.map((route) => {
              const Icon = route.navigation.icon
              return <Link key={route.id} to={route.path} onClick={() => setMobileOpen(false)} className={isRouteActive(route, path) ? 'nav-link active' : 'nav-link'}>
                <Icon size={19} strokeWidth={1.8} /><span>{t(route.navigation.labelKey)}</span>
              </Link>
            })}
          </div>)}
        </nav>
        <div className="sidebar-footer">
          <LanguageSelector />
          <div className="sidebar-version"><Leaf size={16} /><span>PERA ERP <small>v0.1</small></span></div>
          <button type="button" className="nav-link logout-link" onClick={logout}><LogOut size={18} /><span>{t('nav.logout')}</span></button>
        </div>
      </aside>
      {mobileOpen && <button className="sidebar-scrim" type="button" aria-label={t('nav.closeMenu')} onClick={() => setMobileOpen(false)} />}
      <div className="content-shell">
        <header className="mobile-header">
          <button type="button" className="icon-button" onClick={() => setMobileOpen(true)} aria-label={t('nav.openMenu')}><Menu size={21} /></button>
          <div className="brand compact"><span className="brand-mark">P</span><span><strong>PERA</strong><small>ERP</small></span></div>
          <span className="mobile-company">{company?.code || 'DEMO'}</span>
        </header>
        <main className="app-main">{children}</main>
      </div>
    </div>
  )
}
