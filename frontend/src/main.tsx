import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from './auth/AuthContext'
import { ConfirmProvider } from './components/ConfirmDialog'
import { ErrorBoundary, ErrorNotice } from './components/ErrorReporting'
import { ToastProvider } from './components/Toast'
import { I18nProvider } from './i18n/I18nProvider'
import { RouterProvider } from './routing/Router'
import { installGlobalErrorReporting } from './lib/errorReport'
import App from './App'
import './styles/index.css'
import './styles/apps.css'

installGlobalErrorReporting()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <RouterProvider>
        <AuthProvider>
          <ToastProvider>
            <ConfirmProvider>
              <ErrorBoundary>
                <App />
              </ErrorBoundary>
              <ErrorNotice />
            </ConfirmProvider>
          </ToastProvider>
        </AuthProvider>
      </RouterProvider>
    </I18nProvider>
  </StrictMode>,
)
