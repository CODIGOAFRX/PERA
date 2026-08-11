import { Boxes, Building2, FileCheck2, FileText, History as HistoryIcon, LayoutDashboard, Printer, ReceiptText, Settings, SlidersHorizontal, Truck, UserCog, Users, type LucideIcon } from 'lucide-react'
import type { ComponentType } from 'react'
import type { UserRoleCode } from '../auth/AuthContext'
import type { TranslationKey } from '../i18n/catalogs'
import { CatalogPage } from '../pages/CatalogPage'
import { CatalogConfigurationPage } from '../pages/CatalogConfigurationPage'
import { CustomersPage } from '../pages/CustomersPage'
import { DashboardPage } from '../pages/DashboardPage'
import { FinancePage } from '../pages/FinancePage'
import { HistoryPage } from '../pages/HistoryPage'
import { OperationsPage } from '../pages/OperationsPage'
import { SalesPage } from '../pages/SalesPage'
import { SettingsPage } from '../pages/SettingsPage'
import { QuotesPage } from '../pages/QuotesPage'
import { ReportsPage } from '../pages/ReportsPage'
import { SuppliersPage } from '../pages/SuppliersPage'
import { UsersPage } from '../pages/UsersPage'
import { matchPath } from './Router'

export type NavigationGroup = 'general' | 'masterData' | 'operations'

export interface AppRoute {
  id: string
  path: string
  component: ComponentType
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
  { id: 'operations', path: '/operaciones', component: OperationsPage, allowedRoles: ['OWNER', 'ADMIN', 'LOGISTICS'], navigation: { labelKey: 'nav.operations', group: 'operations', groupLabelKey: 'nav.group.operations', icon: Truck } },
  { id: 'history', path: '/historial', component: HistoryPage, allowedRoles: ['OWNER', 'ADMIN'], navigation: { labelKey: 'nav.history', group: 'operations', groupLabelKey: 'nav.group.operations', icon: HistoryIcon } },
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
