import { CheckCircle2, X, XCircle } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'

type ToastKind = 'success' | 'error'
interface ToastItem { id: number; message: string; kind: ToastKind }
interface ToastContextValue { notify: (message: string, kind?: ToastKind) => void }

const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [items, setItems] = useState<ToastItem[]>([])

  const dismiss = useCallback((id: number) => setItems((current) => current.filter((item) => item.id !== id)), [])
  const notify = useCallback((message: string, kind: ToastKind = 'success') => {
    const id = Date.now() + Math.random()
    setItems((current) => [...current, { id, message, kind }])
    // Errors stay longer so they can be read (WCAG 2.2.1).
    window.setTimeout(() => dismiss(id), kind === 'error' ? 8000 : 4000)
  }, [dismiss])

  const value = useMemo(() => ({ notify }), [notify])
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" role="region" aria-label={t('common.notifications')}>
        {items.map((item) => (
          <div className={`toast toast-${item.kind}`} key={item.id} role={item.kind === 'error' ? 'alert' : 'status'}>
            {item.kind === 'success' ? <CheckCircle2 size={18} aria-hidden="true" /> : <XCircle size={18} aria-hidden="true" />}
            <span>{item.message}</span>
            <button type="button" onClick={() => dismiss(item.id)} aria-label={t('common.close')}><X size={16} aria-hidden="true" /></button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast debe utilizarse dentro de ToastProvider')
  return context
}
