import { BookOpenCheck, Boxes, Building2, Cable, FileCheck2, FileText, History as HistoryIcon, LayoutDashboard, Printer, ReceiptText, Settings, SlidersHorizontal, Truck, UserCog, Users, type LucideIcon } from 'lucide-react'
import { lazy, type ComponentType, type LazyExoticComponent } from 'react'
import type { UserRoleCode } from '../auth/AuthContext'
import type { TranslationKey } from '../i18n/catalogs'
import { matchPath } from './Router'

// Cada página se descarga al visitarla por primera vez: el paquete inicial solo lleva el armazón.
const ConnectionsPage = lazy(() => import('../pages/ConnectionsPage').then((module) => ({ default: module.ConnectionsPage })))
const CatalogPage = lazy(() => import('../pages/CatalogPage').then((module) => ({ default: module.CatalogPage })))
const CatalogConfigurationPage = lazy(() => import('../pages/CatalogConfigurationPage').then((module) => ({ default: module.CatalogConfigurationPage })))
const CustomersPage = lazy(() => import('../pages/CustomersPage').then((module) => ({ default: module.CustomersPage })))
const DashboardPage = lazy(() => import('../pages/DashboardPage').then((module) => ({ default: module.DashboardPage })))
const FinancePage = lazy(() => import('../pages/FinancePage').then((module) => ({ default: module.FinancePage })))
const AccountingPage = lazy(() => import('../pages/AccountingPage').then((module) => ({ default: module.AccountingPage })))
const HistoryPage = lazy(() => import('../pages/HistoryPage').then((module) => ({ default: module.HistoryPage })))
const OperationsPage = lazy(() => import('../pages/OperationsPage').then((module) => ({ default: module.OperationsPage })))
const SalesPage = lazy(() => import('../pages/SalesPage').then((module) => ({ default: module.SalesPage })))
const SettingsPage = lazy(() => import('../pages/SettingsPage').then((module) => ({ default: module.SettingsPage })))
const QuotesPage = lazy(() => import('../pages/QuotesPage').then((module) => ({ default: module.QuotesPage })))
const ReportsPage = lazy(() => import('../pages/ReportsPage').then((module) => ({ default: module.ReportsPage })))
const SuppliersPage = lazy(() => import('../pages/SuppliersPage').then((module) => ({ default: module.SuppliersPage })))
const UsersPage = lazy(() => import('../pages/UsersPage').then((module) => ({ default: module.UsersPage })))

export type NavigationGroup = 'general' | 'masterData' | 'operations'

export interface AppRoute {
  id: string
  path: string
  component: ComponentType | LazyExoticComponent<ComponentType>
  allowedRoles?: UserRoleCode[]
  navigation: {
    labelKey: TranslationKey
    group: NavigationGroup
    groupLabelKey: TranslationKey
    icon: LucideIcon
  }
}

export const appRoutes: AppRoute[] = [
  { id: 'dashboard', path: '/', component: DashboardPage, navigation: { labelKey: 'nav.dashboard', group: 'general', groupLabelKey: 'nav.group.general', icon: LayoutDashboard } },
  { id: 'reports', path: '/impresion', component: ReportsPage, allowedRoles: ['OWNER', 'ADMIN', 'ECONOMY', 'LOGISTICS', 'CATALOG'], navigation: { labelKey: 'nav.printing', group: 'general', groupLabelKey: 'nav.group.general', icon: Printer } },
  { id: 'customers', path: '/clientes', component: CustomersPage, allowedRoles: ['OWNER', 'ADMIN', 'ECONOMY'], navigation: { labelKey: 'nav.customers', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: Users } },
  { id: 'suppliers', path: '/proveedores', component: SuppliersPage, allowedRoles: ['OWNER', 'ADMIN', 'LOGISTICS'], navigation: { labelKey: 'nav.suppliers', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: Building2 } },
  { id: 'catalog', path: '/catalogo', component: CatalogPage, allowedRoles: ['OWNER', 'ADMIN', 'CATALOG'], navigation: { labelKey: 'nav.catalog', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: Boxes } },
  { id: 'catalogConfiguration', path: '/maestros', component: CatalogConfigurationPage, allowedRoles: ['OWNER', 'ADMIN', 'CATALOG'], navigation: { labelKey: 'nav.catalogConfiguration', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: SlidersHorizontal } },
  { id: 'quotes', path: '/presupuestos', component: QuotesPage, allowedRoles: ['OWNER', 'ADMIN', 'ECONOMY'], navigation: { labelKey: 'nav.quotes', group: 'operations', groupLabelKey: 'nav.group.operations', icon: FileCheck2 } },
  { id: 'sales', path: '/ventas', component: SalesPage, allowedRoles: ['OWNER', 'ADMIN', 'ECONOMY'], navigation: { labelKey: 'nav.sales', group: 'operations', groupLabelKey: 'nav.group.operations', icon: FileText } },
  { id: 'finance', path: '/finanzas', component: FinancePage, allowedRoles: ['OWNER', 'ADMIN', 'ECONOMY'], navigation: { labelKey: 'nav.finance', group: 'operations', groupLabelKey: 'nav.group.operations', icon: ReceiptText } },
  { id: 'accounting', path: '/contabilidad', component: AccountingPage, allowedRoles: ['OWNER', 'ADMIN', 'ECONOMY'], navigation: { labelKey: 'nav.accounting', group: 'operations', groupLabelKey: 'nav.group.operations', icon: BookOpenCheck } },
  { id: 'operations', path: '/operaciones', component: OperationsPage, allowedRoles: ['OWNER', 'ADMIN', 'LOGISTICS'], navigation: { labelKey: 'nav.operations', group: 'operations', groupLabelKey: 'nav.group.operations', icon: Truck } },
  { id: 'history', path: '/historial', component: HistoryPage, allowedRoles: ['OWNER', 'ADMIN'], navigation: { labelKey: 'nav.history', group: 'operations', groupLabelKey: 'nav.group.operations', icon: HistoryIcon } },
  { id: 'connections', path: '/conexiones', component: ConnectionsPage, allowedRoles: ['OWNER', 'ADMIN'], navigation: { labelKey: 'nav.connections', group: 'general', groupLabelKey: 'nav.group.general', icon: Cable } },
  { id: 'settings', path: '/configuracion', component: SettingsPage, allowedRoles: ['OWNER', 'ADMIN'], navigation: { labelKey: 'nav.settings', group: 'general', groupLabelKey: 'nav.group.general', icon: Settings } },
  { id: 'users', path: '/usuarios', component: UsersPage, allowedRoles: ['OWNER', 'ADMIN'], navigation: { labelKey: 'nav.users', group: 'general', groupLabelKey: 'nav.group.general', icon: UserCog } },
]

export function isRouteAllowed(route: AppRoute, roles: readonly UserRoleCode[]) {
  if (!route.allowedRoles) return true
  return route.allowedRoles.some((allowed) => roles.some((role) => role.toUpperCase() === allowed.toUpperCase()))
}

export function matchAppRoute(path: string) {
  for (const route of appRoutes) {
    const match = matchPath(route.path, path)
    if (match) return { route, params: match.params }
  }
  return null
}

export function isRouteActive(route: AppRoute, path: string) {
  return matchPath(route.path, path) !== null || (route.path !== '/' && path.startsWith(`${route.path}/`))
}
