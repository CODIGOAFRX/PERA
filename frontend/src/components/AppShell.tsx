import { Boxes, Building2, FileText, LayoutDashboard, Leaf, LogOut, Menu, ReceiptText, Users, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Link, useRouter } from '../routing/Router'

const navItems = [
  { to: '/', label: 'Resumen', icon: LayoutDashboard, end: true },
  { to: '/clientes', label: 'Clientes', icon: Users },
  { to: '/proveedores', label: 'Proveedores', icon: Building2 },
  { to: '/catalogo', label: 'Catálogo', icon: Boxes },
  { to: '/ventas', label: 'Ventas', icon: FileText },
  { to: '/finanzas', label: 'Finanzas', icon: ReceiptText },
]

export function AppShell({ children }: { children: ReactNode }) {
  const { company, logout } = useAuth()
  const { path } = useRouter()
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">P</span>
          <span><strong>PERA</strong><small>ERP</small></span>
          <button type="button" className="icon-button sidebar-close" onClick={() => setMobileOpen(false)} aria-label="Cerrar menú"><X size={20} /></button>
        </div>
        <div className="company-chip">
          <span className="company-avatar">{company?.code?.slice(0, 2) || 'PE'}</span>
          <span><small>Empresa activa</small><strong>{company?.name || 'PERA ERP'}</strong></span>
        </div>
        <nav className="main-nav" aria-label="Navegación principal">
          <span className="nav-label">Gestión</span>
          {navItems.map(({ to, label, icon: Icon, end }) => (
            <Link key={to} to={to} onClick={() => setMobileOpen(false)} className={(end ? path === to : path.startsWith(to)) ? 'nav-link active' : 'nav-link'}>
              <Icon size={19} strokeWidth={1.8} /><span>{label}</span>
            </Link>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="sidebar-version"><Leaf size={16} /><span>PERA ERP <small>v0.1</small></span></div>
          <button type="button" className="nav-link logout-link" onClick={logout}><LogOut size={18} /><span>Cerrar sesión</span></button>
        </div>
      </aside>
      {mobileOpen && <button className="sidebar-scrim" type="button" aria-label="Cerrar menú" onClick={() => setMobileOpen(false)} />}
      <div className="content-shell">
        <header className="mobile-header">
          <button type="button" className="icon-button" onClick={() => setMobileOpen(true)} aria-label="Abrir menú"><Menu size={21} /></button>
          <div className="brand compact"><span className="brand-mark">P</span><span><strong>PERA</strong><small>ERP</small></span></div>
          <span className="mobile-company">{company?.code || 'DEMO'}</span>
        </header>
        <main className="app-main">{children}</main>
      </div>
    </div>
  )
}
