import { Suspense, useEffect } from 'react'
import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { LoadingState } from './components/DataState'
import { LoginPage } from './pages/LoginPage'
import { AccessDeniedPage } from './pages/AccessDeniedPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { useRouter } from './routing/Router'
import { homePathFor, isRouteAllowed, matchAppRoute } from './routing/routes'

export default function App() {
  const { authenticated, identity } = useAuth()
  const { path, navigate } = useRouter()
  const home = homePathFor(identity?.roles ?? [])
  // Un cajero o un operario de almacén no tiene resumen: entra directamente a su aplicación.
  useEffect(() => {
    if (authenticated && path === '/' && home !== '/') navigate(home, true)
  }, [authenticated, path, home, navigate])
  if (!authenticated) return <LoginPage />
  if (path === '/' && home !== '/') return <LoadingState />

  const match = matchAppRoute(path)
  const Page = match?.route.component
  const allowed = match ? isRouteAllowed(match.route, identity?.roles ?? []) : false
  const page = Page
    ? allowed ? <Page /> : <AccessDeniedPage />
    : <NotFoundPage />

  if (match?.route.standalone && allowed) return <Suspense fallback={<LoadingState />}>{page}</Suspense>
  return <AppShell><Suspense fallback={<LoadingState />}>{page}</Suspense></AppShell>
}
