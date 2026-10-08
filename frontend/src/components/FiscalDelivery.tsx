import { RefreshCw, Send } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { useConfirm } from './ConfirmDialog'
import { StatusBadge, type BadgeTone } from './StatusBadge'

type Delivery = { state: string; remoteId: string | null; message: string; updatedAt: string | null; connectionActive?: boolean }

const tones: Record<string, BadgeTone> = {
  REVIEW: 'danger', IN_PROGRESS: 'warning', UNKNOWN: 'warning', SUBMITTED: 'info', REMOTE: 'info',
}
/** Un envío cuyo resultado no consta no se ofrece de nuevo: repetirlo podría duplicar la factura en el proveedor. */
const uncertain = new Set(['IN_PROGRESS', 'UNKNOWN'])

/**
 * Envío de pruebas de una factura electrónica al sandbox de B2Brouter. «Consultar» pregunta al
 * proveedor por la factura ya creada.
 *
 * La remisión a la AEAT no pasa por aquí: es automática y su estado se ve en el bloque Veri*Factu.
 * `configureLink` (solo para quien puede editar Conexiones) se ofrece si la conexión no está activa.
 */
export function FiscalDelivery({ sourceId, provider, onSent, configureLink }: { sourceId: string; provider: 'B2B'; onSent?: () => void; configureLink?: ReactNode }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const confirm = useConfirm()
  const path = `/api/v1/documents/${sourceId}/b2b`
  const [delivery, setDelivery] = useState<Delivery | null>(null)
  const [contact, setContact] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    setDelivery(null); setError('')
    apiFetch<Delivery>(path)
      .then((value) => { if (active) setDelivery(value) })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
    return () => { active = false }
  }, [path, reload])

  const labels: Record<string, string> = {
    NOT_SENT: c('Sin enviar', 'Not sent'),
    IN_PROGRESS: c('Envío iniciado; pendiente de confirmación', 'Submission started; awaiting confirmation'),
    UNKNOWN: c('Resultado sin confirmar', 'Unconfirmed result'),
    DRAFT: c('Borrador creado en B2Brouter', 'Draft created in B2Brouter'),
    REVIEW: c('Requiere revisión', 'Needs review'),
    SUBMITTED: c('Envío solicitado', 'Submission requested'),
    REMOTE: c('Estado consultado en B2Brouter', 'Status retrieved from B2Brouter'),
  }
  const contactId = Number(contact)
  const validContact = /^\d+$/.test(contact.trim()) && Number.isSafeInteger(contactId) && contactId >= 1
  const canSend = delivery?.state === 'NOT_SENT' && validContact

  async function run(request: () => Promise<Delivery>) {
    setBusy(true); setError('')
    try { setDelivery(await request()); onSent?.() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function send() {
    const question = c(`Se creará la factura en el sandbox de B2Brouter y se solicitará su envío al contacto ${contactId}. ¿Continuar?`, `The invoice will be created in the B2Brouter sandbox and sent to contact ${contactId}. Continue?`)
    if (!(await confirm({ message: question, confirmLabel: c('Enviar a pruebas', 'Send to test') }))) return
    void run(() => apiFetch<Delivery>(path, { method: 'POST', body: JSON.stringify({ contactId }) }))
  }

  function refresh() {
    void run(() => apiFetch<Delivery>(`${path}/refresh`, { method: 'POST' }))
  }

  // Nothing has been sent and the company has not enabled B2Brouter: do not offer a form that would fail.
  if (delivery?.state === 'NOT_SENT' && delivery.connectionActive === false) {
    return configureLink ? <p className="fiscal-delivery-inactive">{c('La factura electrónica con B2Brouter no está activada.', 'B2Brouter e-invoicing is not enabled.')} {configureLink}</p> : null
  }

  return <section className="fiscal-delivery" aria-label={c('Factura electrónica en B2Brouter', 'B2Brouter e-invoice')} aria-busy={busy}>
    <div className="fiscal-delivery-heading">
      <h3>{c('Factura electrónica · B2Brouter', 'E-invoice · B2Brouter')}</h3>
      <StatusBadge tone="info">Sandbox</StatusBadge>
    </div>
    {!delivery && !error && <p>{c('Consultando el estado…', 'Checking status…')}</p>}
    {delivery && <p role="status">
      <StatusBadge tone={tones[delivery.state] ?? 'neutral'}>{labels[delivery.state] ?? delivery.state}</StatusBadge>
      {delivery.remoteId && <> · ID {delivery.remoteId}</>}
      {delivery.updatedAt && <> · {formatDateTime(delivery.updatedAt, locale)}</>}
    </p>}
    {delivery?.message && <p>{delivery.message}</p>}
    {delivery && uncertain.has(delivery.state) && <p>{c('Comprueba la factura en B2Brouter. PERA no repite el envío automáticamente para evitar duplicados.', 'Check the invoice in B2Brouter. PERA does not retry automatically to avoid duplicates.')}</p>}
    {error && <p role="alert" className="inline-error">{error}</p>}
    <div className="fiscal-delivery-actions">
      {delivery?.state === 'NOT_SENT' && <>
        <label>{c('ID del contacto sandbox (mismo NIF que el cliente)', 'Sandbox contact ID (same tax ID as the customer)')}<input type="text" inputMode="numeric" pattern="[0-9]*" value={contact} disabled={busy} onChange={(event) => setContact(event.target.value)} /></label>
        <button type="button" className="button button-primary" disabled={busy || !canSend} onClick={() => void send()}><Send size={16} />{busy ? c('Enviando…', 'Sending…') : c('Enviar a pruebas', 'Send to test')}</button>
      </>}
      {!delivery && error && <button type="button" className="button button-ghost" onClick={() => setReload((value) => value + 1)}><RefreshCw size={16} />{c('Reintentar', 'Retry')}</button>}
      {delivery && delivery.state !== 'NOT_SENT' && <button type="button" className="button button-ghost" disabled={busy} onClick={refresh}>
        <RefreshCw size={16} />{c('Consultar en B2Brouter', 'Check in B2Brouter')}
      </button>}
    </div>
  </section>
}
