import { X } from 'lucide-react'
import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from '../i18n/I18nProvider'

interface ModalProps {
  open: boolean
  title: string
  description?: string
  onClose: () => void
  children: ReactNode
  size?: 'medium' | 'large'
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusableIn(panel: HTMLElement) {
  return Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((element) => !element.hasAttribute('inert') && element.getAttribute('aria-hidden') !== 'true')
}

export function Modal({ open, title, description, onClose, children, size = 'medium' }: ModalProps) {
  const { t } = useTranslation()
  const titleId = useId()
  const descriptionId = useId()
  const panelRef = useRef<HTMLElement>(null)
  // Callers often pass inline closures; keep the latest one without re-running the focus effect on every render.
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const panel = panelRef.current
    if (panel && !panel.contains(document.activeElement)) {
      const autofocus = panel.querySelector<HTMLElement>('[data-autofocus], [autofocus]')
      const firstField = panel.querySelector<HTMLElement>('.modal-body input:not([disabled]), .modal-body select:not([disabled]), .modal-body textarea:not([disabled]), form input:not([disabled]), form select:not([disabled]), form textarea:not([disabled])')
      ;(autofocus ?? firstField ?? panel).focus()
    }
    document.body.classList.add('modal-open')
    return () => {
      // Only the last open dialog removes the scroll lock.
      if (document.querySelectorAll('[role="dialog"][aria-modal="true"]').length <= 1) document.body.classList.remove('modal-open')
      if (previouslyFocused?.isConnected) previouslyFocused.focus()
    }
  }, [open])

  if (!open) return null

  const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onCloseRef.current()
      return
    }
    if (event.key !== 'Tab' || !panelRef.current) return
    const elements = focusableIn(panelRef.current)
    if (elements.length === 0) { event.preventDefault(); return }
    const first = elements[0]
    const last = elements[elements.length - 1]
    if (event.shiftKey && (document.activeElement === first || document.activeElement === panelRef.current)) {
      event.preventDefault(); last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault(); first.focus()
    }
  }

  return createPortal(
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onCloseRef.current()
    }}>
      <section ref={panelRef} className={`modal-panel modal-${size}`} role="dialog" aria-modal="true" aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined} tabIndex={-1} onKeyDown={onKeyDown}>
        <header className="modal-header">
          <div>
            <h2 id={titleId}>{title}</h2>
            {description && <p id={descriptionId}>{description}</p>}
          </div>
          <button type="button" className="icon-button" onClick={() => onCloseRef.current()} aria-label={t('common.close')}><X size={20} aria-hidden="true" /></button>
        </header>
        {children}
      </section>
    </div>,
    document.body,
  )
}
