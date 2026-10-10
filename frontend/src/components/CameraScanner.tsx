import { Camera, Flashlight, Keyboard, LoaderCircle, ScanLine, X } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from '../i18n/I18nProvider'
import { decodeImageFile, getDecoder, liveCameraAvailable } from '../lib/codeReader'
import { scanFeedback } from '../lib/scanner'

interface CameraScannerProps {
  open: boolean
  title?: string
  onClose: () => void
  onDetected: (code: string) => void
  /** Permite escribir el código a mano desde el mismo panel. */
  allowManual?: boolean
}

type CameraState = 'starting' | 'live' | 'photo' | 'error'

/**
 * Escáner con la cámara para códigos de barras y QR. En una página segura (HTTPS o localhost)
 * abre la cámara en directo; si no, el móvil hace una foto y se lee de la foto.
 */
export function CameraScanner({ open, title, onClose, onDetected, allowManual = true }: CameraScannerProps) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const titleId = useId()
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const photoRef = useRef<HTMLInputElement>(null)
  const detectedRef = useRef(onDetected)
  detectedRef.current = onDetected
  const [state, setState] = useState<CameraState>('starting')
  const [message, setMessage] = useState('')
  const [manual, setManual] = useState('')
  const [torch, setTorch] = useState<boolean | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    setManual(''); setMessage(''); setTorch(null)
    if (!liveCameraAvailable()) { setState('photo'); return }
    let cancelled = false
    let timer = 0
    setState('starting')
    const stop = () => { window.clearTimeout(timer); streamRef.current?.getTracks().forEach((track) => track.stop()); streamRef.current = null }
    ;(async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false })
        if (cancelled) { stream.getTracks().forEach((track) => track.stop()); return }
        streamRef.current = stream
        const track = stream.getVideoTracks()[0]
        const capabilities = (track?.getCapabilities?.() ?? {}) as { torch?: boolean }
        if (capabilities.torch) setTorch(false)
        const video = videoRef.current
        if (!video) return
        video.srcObject = stream
        await video.play()
        setState('live')
        const decoder = await getDecoder()
        const canvas = document.createElement('canvas')
        const tick = async () => {
          if (cancelled) return
          if (video.readyState >= 2 && video.videoWidth > 0) {
            // Se lee la franja central, donde el usuario apunta: es más rápido y evita leer otro código.
            const width = video.videoWidth
            const height = video.videoHeight
            const cropHeight = Math.round(height * 0.6)
            canvas.width = Math.min(width, 960)
            canvas.height = Math.round(cropHeight * canvas.width / width)
            canvas.getContext('2d', { willReadFrequently: true })?.drawImage(video, 0, Math.round((height - cropHeight) / 2), width, cropHeight, 0, 0, canvas.width, canvas.height)
            const code = await decoder(canvas).catch(() => null)
            if (code && !cancelled) { scanFeedback(true); stop(); detectedRef.current(code.trim()); return }
          }
          timer = window.setTimeout(() => void tick(), 180)
        }
        void tick()
      } catch (cause) {
        if (cancelled) return
        const denied = cause instanceof DOMException && (cause.name === 'NotAllowedError' || cause.name === 'SecurityError')
        setMessage(denied ? c('No hay permiso para usar la cámara. Puedes hacer una foto del código o escribirlo.', 'Camera permission denied. Take a photo of the code or type it.') : c('No se ha podido abrir la cámara. Puedes hacer una foto del código o escribirlo.', 'The camera could not be opened. Take a photo of the code or type it.'))
        setState('error')
      }
    })()
    return () => { cancelled = true; stop() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') { event.stopPropagation(); onClose() } }
    window.addEventListener('keydown', onKey)
    document.body.classList.add('modal-open')
    return () => { window.removeEventListener('keydown', onKey); if (document.querySelectorAll('[role="dialog"][aria-modal="true"]').length <= 1) document.body.classList.remove('modal-open') }
  }, [open, onClose])

  if (!open) return null

  const toggleTorch = async () => {
    const track = streamRef.current?.getVideoTracks()[0]
    if (!track || torch === null) return
    try { await track.applyConstraints({ advanced: [{ torch: !torch } as MediaTrackConstraintSet] }); setTorch(!torch) } catch { setTorch(null) }
  }
  const readPhoto = async (file: File | undefined) => {
    if (!file) return
    setBusy(true); setMessage('')
    try {
      const code = await decodeImageFile(file)
      if (code) { scanFeedback(true); detectedRef.current(code.trim()); return }
      scanFeedback(false)
      setMessage(c('No se ha encontrado ningún código en la foto. Acércate, que el código salga recto y con luz, y repite.', 'No code was found in the photo. Get closer, keep the code straight and well lit, and try again.'))
    } catch {
      setMessage(c('No se ha podido leer la foto.', 'The photo could not be read.'))
    } finally {
      setBusy(false)
      if (photoRef.current) photoRef.current.value = ''
    }
  }
  const submitManual = (event: FormEvent) => {
    event.preventDefault()
    if (manual.trim()) detectedRef.current(manual.trim())
  }

  return createPortal(<div className="scanner-overlay" role="dialog" aria-modal="true" aria-labelledby={titleId}>
    <header className="scanner-header">
      <div><ScanLine size={20} aria-hidden="true" /><h2 id={titleId}>{title ?? c('Escanear código', 'Scan code')}</h2></div>
      <button type="button" className="scanner-close" onClick={onClose} aria-label={c('Cerrar el escáner', 'Close scanner')}><X size={22} /></button>
    </header>
    <div className="scanner-body">
      {(state === 'starting' || state === 'live') && <div className="scanner-viewport">
        <video ref={videoRef} playsInline muted aria-label={c('Imagen de la cámara', 'Camera preview')} />
        <div className="scanner-frame" aria-hidden="true"><span /></div>
        {state === 'starting' && <div className="scanner-status"><LoaderCircle className="spin" size={22} />{c('Abriendo la cámara…', 'Opening the camera…')}</div>}
        {state === 'live' && <p className="scanner-hint">{c('Apunta al código de barras o al QR. Se lee solo.', 'Point at the barcode or QR code. It reads automatically.')}</p>}
        {torch !== null && <button type="button" className={`scanner-torch ${torch ? 'on' : ''}`} onClick={() => void toggleTorch()} aria-pressed={torch} aria-label={c('Linterna', 'Torch')}><Flashlight size={20} /></button>}
      </div>}
      {(state === 'photo' || state === 'error') && <div className="scanner-photo">
        <span className="scanner-photo-icon" aria-hidden="true"><Camera size={34} /></span>
        <h3>{c('Haz una foto del código', 'Take a photo of the code')}</h3>
        <p>{state === 'photo' ? c('La cámara en directo necesita una conexión segura (HTTPS). Con una foto funciona igual: encuadra el código de barras o el QR y dispara.', 'The live camera needs a secure (HTTPS) connection. A photo works just as well: frame the barcode or QR code and shoot.') : message}</p>
        <input ref={photoRef} className="sr-only" id={`${titleId}-photo`} type="file" accept="image/*" capture="environment" onChange={(event) => void readPhoto(event.target.files?.[0])} />
        <label htmlFor={`${titleId}-photo`} className={`button button-primary scanner-photo-button ${busy ? 'disabled' : ''}`}>{busy ? <LoaderCircle className="spin" size={18} /> : <Camera size={18} />}{busy ? c('Leyendo la foto…', 'Reading the photo…') : c('Abrir la cámara del móvil', 'Open the phone camera')}</label>
        {state === 'photo' && message && <p className="scanner-error" role="alert">{message}</p>}
      </div>}
      {allowManual && <form className="scanner-manual" onSubmit={submitManual}>
        <label htmlFor={`${titleId}-manual`}><Keyboard size={16} />{c('O escribe el código', 'Or type the code')}</label>
        <div><input id={`${titleId}-manual`} value={manual} inputMode="text" autoComplete="off" onChange={(event) => setManual(event.target.value)} placeholder={c('Código de barras o código del artículo', 'Barcode or product code')} /><button type="submit" className="button button-secondary" disabled={!manual.trim()}>{c('Buscar', 'Search')}</button></div>
      </form>}
    </div>
  </div>, document.body)
}
