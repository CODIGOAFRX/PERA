import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { CatalogPage } from './pages/CatalogPage'
import { CustomersPage } from './pages/CustomersPage'
import { DashboardPage } from './pages/DashboardPage'
import { FinancePage } from './pages/FinancePage'
import { LoginPage } from './pages/LoginPage'
import { SalesPage } from './pages/SalesPage'
import { SuppliersPage } from './pages/SuppliersPage'
import { useRouter } from './routing/Router'

export default function App() {
  const { authenticated } = useAuth()
  const { path } = useRouter()
  if (!authenticated) return <LoginPage />

  const page = (() => {
    switch (path) {
      case '/clientes': return <CustomersPage />
      case '/proveedores': return <SuppliersPage />
      case '/catalogo': return <CatalogPage />
      case '/ventas': return <SalesPage />
      case '/finanzas': return <FinancePage />
      default: return <DashboardPage />
    }
  })()

  return <AppShell>{page}</AppShell>
}
