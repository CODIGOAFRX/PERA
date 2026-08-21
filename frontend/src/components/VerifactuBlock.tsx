import { ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import { apiFetch } from '../lib/api'
import { useTranslation } from '../i18n/I18nProvider'
import { StatusBadge, type BadgeTone } from './StatusBadge'
import type { VerifactuRecord, VerifactuState } from '../types/api'

/**
 * Bloque Veri*Factu de una factura: huella, encadenado, estado ante la AEAT y QR de cotejo.
 *
 * El contenido del QR llega ya construido del servidor. Aquí solo se dibuja: el orden de los
 * parámetros y el formato de fecha e importe son especificación y no deben reconstruirse en el
 * navegador, donde acabarían divergiendo del registro remitido.
 */
export function VerifactuBlock({ documentId }: { documentId: string }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const [record, setRecord] = useState<VerifactuRecord | null>(null)
  const [qr, setQr] = useState('')
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    let active = true
    apiFetch<VerifactuRecord[]>(`/api/v1/verifactu-records?documentId=${documentId}`)
      .then((records) => {
        if (!active) return
        setRecord(records.find((item) => item.recordType === 'ALTA') ?? null)
      })
      .catch(() => { if (active) setRecord(null) })
      .finally(() => { if (active) setLoaded(true) })
    return () => { active = false }
  }, [documentId])

  useEffect(() => {
    let active = true
    if (!record?.qrPayload) { setQr(''); return }
    // Nivel de corrección M y margen de 2 módulos, como pide la especificación del QR tributario.
    QRCode.toDataURL(record.qrPayload, { errorCorrectionLevel: 'M', margin: 2, width: 320 })
      .then((url) => { if (active) setQr(url) })
      .catch(() => { if (active) setQr('') })
    return () => { active = false }
  }, [record?.qrPayload])

  if (!loaded || !record) return null

  return (
    <div className="verifactu-block">
      <div className="verifactu-data">
        <div className="verifactu-heading">
          <ShieldCheck size={17} />
          <strong>VERI*FACTU</strong>
          <StatusBadge tone={stateTone(record.state)}>{stateLabel(record.state, language)}</StatusBadge>
        </div>
        <dl>
          <div><dt>{c('Registro', 'Record')}</dt><dd>{c('N.º', 'No.')} {record.sequenceNumber}</dd></div>
          <div><dt>{c('Emisor', 'Issuer')}</dt><dd>{record.issuerTaxId}</dd></div>
          <div><dt>{c('Generado', 'Generated')}</dt><dd>{new Date(record.generatedAt).toLocaleString(language === 'es' ? 'es-ES' : 'en-GB')}</dd></div>
          <div><dt>{c('Huella', 'Fingerprint')}</dt><dd><code>{record.fingerprint}</code></dd></div>
          <div><dt>{c('Encadenado con', 'Chained to')}</dt><dd><code>{record.previousFingerprint ?? c('Primer registro de la cadena', 'First record of the chain')}</code></dd></div>
          {record.aeatCsv && <div><dt>CSV</dt><dd><code>{record.aeatCsv}</code></dd></div>}
        </dl>
      </div>
      {qr && (
        <figure className="verifactu-qr">
          <img src={qr} alt={c('Código QR de cotejo de la factura', 'Invoice verification QR code')} />
          <figcaption>{c('QR tributario', 'Tax QR')}</figcaption>
        </figure>
      )}
    </div>
  )
}

function stateTone(state: VerifactuState): BadgeTone {
  if (state === 'ACCEPTED') return 'success'
  if (state === 'REJECTED') return 'danger'
  if (state === 'ACCEPTED_WITH_ERRORS') return 'warning'
  if (state === 'SENT') return 'info'
  return 'neutral'
}

function stateLabel(state: VerifactuState, language: string): string {
  const es: Record<VerifactuState, string> = {
    PENDING: 'Pendiente de remitir', SENT: 'Remitido', ACCEPTED: 'Aceptado',
    ACCEPTED_WITH_ERRORS: 'Aceptado con errores', REJECTED: 'Rechazado',
  }
  const en: Record<VerifactuState, string> = {
    PENDING: 'Pending submission', SENT: 'Submitted', ACCEPTED: 'Accepted',
    ACCEPTED_WITH_ERRORS: 'Accepted with errors', REJECTED: 'Rejected',
  }
  return language === 'es' ? es[state] : en[state]
}
