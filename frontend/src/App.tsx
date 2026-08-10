import { useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { useRouter } from './routing/Router'
import { matchAppRoute } from './routing/routes'

export default function App() {
  const { authenticated } = useAuth()
  const { path } = useRouter()
  if (!authenticated) return <LoginPage />

  const match = matchAppRoute(path)
  const Page = match?.route.component
  const page = Page ? <Page /> : <NotFoundPage />

  return <AppShell>{page}</AppShell>
}
