import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { LoginPage } from './pages/LoginPage'
import { AccessDeniedPage } from './pages/AccessDeniedPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { useRouter } from './routing/Router'
import { isRouteAllowed, matchAppRoute } from './routing/routes'

export default function App() {
  const { authenticated, identity } = useAuth()
  const { path } = useRouter()
  if (!authenticated) return <LoginPage />

  const match = matchAppRoute(path)
  const Page = match?.route.component
  const page = Page
    ? isRouteAllowed(match.route, identity?.roles ?? []) ? <Page /> : <AccessDeniedPage />
    : <NotFoundPage />

  return <AppShell>{page}</AppShell>
}
