import { Boxes, Building2, FileCheck2, FileText, History as HistoryIcon, LayoutDashboard, ReceiptText, Settings, SlidersHorizontal, Truck, Users, type LucideIcon } from 'lucide-react'
import type { ComponentType } from 'react'
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
import { SuppliersPage } from '../pages/SuppliersPage'
import { matchPath } from './Router'

export type NavigationGroup = 'general' | 'masterData' | 'operations'

export interface AppRoute {
  id: string
  path: string
  component: ComponentType
  navigation: {
    labelKey: TranslationKey
    group: NavigationGroup
    groupLabelKey: TranslationKey
    icon: LucideIcon
  }
}

export const appRoutes: AppRoute[] = [
  { id: 'dashboard', path: '/', component: DashboardPage, navigation: { labelKey: 'nav.dashboard', group: 'general', groupLabelKey: 'nav.group.general', icon: LayoutDashboard } },
  { id: 'customers', path: '/clientes', component: CustomersPage, navigation: { labelKey: 'nav.customers', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: Users } },
  { id: 'suppliers', path: '/proveedores', component: SuppliersPage, navigation: { labelKey: 'nav.suppliers', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: Building2 } },
  { id: 'catalog', path: '/catalogo', component: CatalogPage, navigation: { labelKey: 'nav.catalog', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: Boxes } },
  { id: 'catalogConfiguration', path: '/maestros', component: CatalogConfigurationPage, navigation: { labelKey: 'nav.catalogConfiguration', group: 'masterData', groupLabelKey: 'nav.group.masterData', icon: SlidersHorizontal } },
  { id: 'quotes', path: '/presupuestos', component: QuotesPage, navigation: { labelKey: 'nav.quotes', group: 'operations', groupLabelKey: 'nav.group.operations', icon: FileCheck2 } },
  { id: 'sales', path: '/ventas', component: SalesPage, navigation: { labelKey: 'nav.sales', group: 'operations', groupLabelKey: 'nav.group.operations', icon: FileText } },
  { id: 'finance', path: '/finanzas', component: FinancePage, navigation: { labelKey: 'nav.finance', group: 'operations', groupLabelKey: 'nav.group.operations', icon: ReceiptText } },
  { id: 'operations', path: '/operaciones', component: OperationsPage, navigation: { labelKey: 'nav.operations', group: 'operations', groupLabelKey: 'nav.group.operations', icon: Truck } },
  { id: 'history', path: '/historial', component: HistoryPage, navigation: { labelKey: 'nav.history', group: 'operations', groupLabelKey: 'nav.group.operations', icon: HistoryIcon } },
  { id: 'settings', path: '/configuracion', component: SettingsPage, navigation: { labelKey: 'nav.settings', group: 'general', groupLabelKey: 'nav.group.general', icon: Settings } },
]

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
