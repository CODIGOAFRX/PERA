import { Check, Copy } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from '../i18n/I18nProvider'

/**
 * Copia un texto al portapapeles. El API moderno solo existe en páginas seguras (HTTPS o
 * localhost); por la red local se usa el método antiguo para que también funcione.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // Se intenta con el método antiguo.
  }
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  let copied = false
  try { copied = document.execCommand('copy') } catch { copied = false }
  area.remove()
  return copied
}

/** Botón verde «Copiar código» de las tablas de artículos y existencias. */
export function CopyCodeButton({ code, label }: { code: string; label?: string }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')
  useEffect(() => {
    if (state === 'idle') return
    const timer = window.setTimeout(() => setState('idle'), 1600)
    return () => window.clearTimeout(timer)
  }, [state])
  return <button type="button" className="button button-primary button-small copy-code-button"
    onClick={async () => setState((await copyText(code)) ? 'copied' : 'failed')}
    aria-label={c(`Copiar el código ${code}`, `Copy code ${code}`)} title={code}>
    {state === 'copied' ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
    <span aria-live="polite">{state === 'copied' ? c('Copiado', 'Copied') : state === 'failed' ? c('No se pudo', 'Failed') : label ?? c('Copiar código', 'Copy code')}</span>
  </button>
}
