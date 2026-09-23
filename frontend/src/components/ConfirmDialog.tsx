import { AlertTriangle } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { Modal } from './Modal'

export interface ConfirmOptions {
  message: string
  title?: string
  confirmLabel?: string
  cancelLabel?: string
  /** Marks irreversible or destructive actions with the danger style. */
  danger?: boolean
}

type Confirm = (options: ConfirmOptions | string) => Promise<boolean>

const ConfirmContext = createContext<Confirm | null>(null)

interface PendingConfirm extends ConfirmOptions { resolve: (value: boolean) => void }

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [pending, setPending] = useState<PendingConfirm | null>(null)
  const pendingRef = useRef<PendingConfirm | null>(null)

  const settle = useCallback((value: boolean) => {
    const current = pendingRef.current
    pendingRef.current = null
    setPending(null)
    current?.resolve(value)
  }, [])

  const confirm = useCallback<Confirm>((options) => {
    const normalized = typeof options === 'string' ? { message: options } : options
    // A new request supersedes an unanswered one, which counts as cancelled.
    pendingRef.current?.resolve(false)
    return new Promise<boolean>((resolve) => {
      const next = { ...normalized, resolve }
      pendingRef.current = next
      setPending(next)
    })
  }, [])

  const value = useMemo(() => confirm, [confirm])
  return (
    <ConfirmContext.Provider value={value}>
      {children}
      <Modal open={pending !== null} title={pending?.title ?? t('common.confirmTitle')} onClose={() => settle(false)}>
        {pending && <div className="confirm-dialog">
          <p className="confirm-message">
            {pending.danger && <AlertTriangle size={20} aria-hidden="true" className="confirm-icon" />}
            <span>{pending.message}</span>
          </p>
          <footer className="form-actions">
            <button type="button" className="button button-ghost" onClick={() => settle(false)} data-autofocus>{pending.cancelLabel ?? t('common.cancel')}</button>
            <button type="button" className={`button ${pending.danger ? 'button-danger' : 'button-primary'}`} onClick={() => settle(true)}>{pending.confirmLabel ?? t('common.confirm')}</button>
          </footer>
        </div>}
      </Modal>
    </ConfirmContext.Provider>
  )
}

/**
 * Returns an async confirmation. Outside ConfirmProvider (isolated component tests) it falls back to the native dialog.
 */
export function useConfirm(): Confirm {
  const context = useContext(ConfirmContext)
  return useMemo<Confirm>(() => context ?? ((options) => Promise.resolve(window.confirm(typeof options === 'string' ? options : options.message))), [context])
}
