import { useEffect, useRef } from 'react'

/**
 * Lectores de código de barras USB o Bluetooth.
 *
 * Funcionan como un teclado que escribe muy deprisa y termina con Intro. Se distinguen de una
 * persona por el tiempo entre teclas: un lector escribe un carácter cada pocos milisegundos.
 * Si el foco está en un campo de texto, el propio campo recibe el código (y su Intro), así que
 * aquí solo se recogen las lecturas hechas con el foco fuera de los campos.
 */
export interface ScannerOptions {
  enabled?: boolean
  minLength?: number
  /** Milisegundos máximos entre teclas para considerar que escribe un lector. */
  maxInterval?: number
}

export function createScanBuffer(onScan: (code: string) => void, { minLength = 3, maxInterval = 60 }: ScannerOptions = {}) {
  let buffer = ''
  let last = 0
  return {
    key(key: string, at: number): boolean {
      if (key === 'Enter' || key === 'Tab') {
        const code = buffer
        buffer = ''
        if (code.length >= minLength) { onScan(code); return true }
        return false
      }
      if (key.length !== 1) return false
      if (buffer && at - last > maxInterval) buffer = ''
      buffer += key
      last = at
      return false
    },
    reset() { buffer = '' },
  }
}

function isEditable(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) return false
  if (target.isContentEditable) return true
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return true
  return target instanceof HTMLInputElement && !['checkbox', 'radio', 'button', 'submit', 'range', 'color', 'file'].includes(target.type)
}

export function useHardwareScanner(onScan: (code: string) => void, options: ScannerOptions = {}) {
  const callback = useRef(onScan)
  callback.current = onScan
  const { enabled = true, minLength = 3, maxInterval = 60 } = options
  useEffect(() => {
    if (!enabled) return
    const buffer = createScanBuffer((code) => callback.current(code), { minLength, maxInterval })
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.altKey || event.metaKey) return
      if (isEditable(event.target) || document.querySelector('[role="dialog"][aria-modal="true"]')) { buffer.reset(); return }
      if (buffer.key(event.key, event.timeStamp || performance.now())) event.preventDefault()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [enabled, minLength, maxInterval])
}

/** Aviso al leer: vibración en el móvil y un pitido corto, como un lector de verdad. */
export function scanFeedback(ok = true) {
  try { navigator.vibrate?.(ok ? 60 : [80, 60, 80]) } catch { /* sin vibración */ }
  try {
    const AudioContextClass = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!AudioContextClass) return
    const context = new AudioContextClass()
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    oscillator.frequency.value = ok ? 1250 : 320
    gain.gain.value = 0.08
    oscillator.connect(gain).connect(context.destination)
    oscillator.start()
    oscillator.stop(context.currentTime + (ok ? 0.09 : 0.25))
    oscillator.onended = () => { void context.close() }
  } catch { /* sin sonido */ }
}
