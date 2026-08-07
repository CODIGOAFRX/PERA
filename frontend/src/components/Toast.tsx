import { CheckCircle2, X, XCircle } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

type ToastKind = 'success' | 'error'
interface ToastItem { id: number; message: string; kind: ToastKind }
interface ToastContextValue { notify: (message: string, kind?: ToastKind) => void }

const ToastContext = createContext<ToastContextValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const dismiss = useCallback((id: number) => setItems((current) => current.filter((item) => item.id !== id)), [])
  const notify = useCallback((message: string, kind: ToastKind = 'success') => {
    const id = Date.now() + Math.random()
    setItems((current) => [...current, { id, message, kind }])
    window.setTimeout(() => dismiss(id), 3600)
  }, [dismiss])

  const value = useMemo(() => ({ notify }), [notify])
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" aria-live="polite">
        {items.map((item) => (
          <div className={`toast toast-${item.kind}`} key={item.id}>
            {item.kind === 'success' ? <CheckCircle2 size={18} /> : <XCircle size={18} />}
            <span>{item.message}</span>
            <button type="button" onClick={() => dismiss(item.id)} aria-label="Cerrar aviso"><X size={16} /></button>
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
