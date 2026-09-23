import { RefreshCw, Send } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { useConfirm } from './ConfirmDialog'
import { StatusBadge, type BadgeTone } from './StatusBadge'

type Provider = 'AEAT' | 'B2B'
type Delivery = { state: string; remoteId: string | null; message: string; updatedAt: string | null; connectionActive?: boolean }

const tones: Record<string, BadgeTone> = {
  ACCEPTED: 'success', ACCEPTED_WITH_ERRORS: 'warning', REJECTED: 'danger', REVIEW: 'danger',
  IN_PROGRESS: 'warning', UNKNOWN: 'warning', SUBMITTED: 'info', REMOTE: 'info',
}
/** Un envío cuyo resultado no consta no se ofrece de nuevo: repetirlo podría duplicar la factura en el proveedor. */
const uncertain = new Set(['IN_PROGRESS', 'UNKNOWN'])

/**
 * Remisión manual de pruebas de una factura a la AEAT (preproducción) o al sandbox de B2Brouter.
 *
 * En AEAT, «Ver estado guardado» relee el resultado que PERA guardó al remitir: no consulta el
 * servicio de la AEAT. En B2Brouter sí se pregunta al proveedor por la factura ya creada.
 */
/** `configureLink` (only for users who may edit Conexiones) is offered when the connection is not active yet. */
export function FiscalDelivery({ sourceId, provider, onSent, configureLink }: { sourceId: string; provider: Provider; onSent?: () => void; configureLink?: ReactNode }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const confirm = useConfirm()
  const path = provider === 'AEAT' ? `/api/v1/verifactu-records/${sourceId}/delivery` : `/api/v1/documents/${sourceId}/b2b`
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
    ACCEPTED: c('Aceptado por la AEAT (pruebas)', 'Accepted by AEAT (test)'),
    ACCEPTED_WITH_ERRORS: c('Aceptado con errores (pruebas)', 'Accepted with errors (test)'),
    REJECTED: c('Rechazado por la AEAT (pruebas)', 'Rejected by AEAT (test)'),
    DRAFT: c('Borrador creado en B2Brouter', 'Draft created in B2Brouter'),
    REVIEW: c('Requiere revisión', 'Needs review'),
    SUBMITTED: c('Envío solicitado', 'Submission requested'),
    REMOTE: c('Estado consultado en B2Brouter', 'Status retrieved from B2Brouter'),
  }
  const contactId = Number(contact)
  const validContact = /^\d+$/.test(contact.trim()) && Number.isSafeInteger(contactId) && contactId >= 1
  const canSend = delivery?.state === 'NOT_SENT' && (provider === 'AEAT' || validContact)

  async function run(request: () => Promise<Delivery>) {
    setBusy(true); setError('')
    try { setDelivery(await request()); onSent?.() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setBusy(false) }
  }

  async function send() {
    const question = provider === 'AEAT'
      ? c('Se remitirá este registro a la AEAT de pruebas (preproducción). La remisión no se puede deshacer desde PERA. ¿Continuar?', 'This record will be submitted to the AEAT test environment (pre-production). The submission cannot be undone from PERA. Continue?')
      : c(`Se creará la factura en el sandbox de B2Brouter y se solicitará su envío al contacto ${contactId}. ¿Continuar?`, `The invoice will be created in the B2Brouter sandbox and sent to contact ${contactId}. Continue?`)
    if (!(await confirm({ message: question, confirmLabel: c('Enviar a pruebas', 'Send to test') }))) return
    void run(() => apiFetch<Delivery>(path, { method: 'POST', ...(provider === 'B2B' ? { body: JSON.stringify({ contactId }) } : {}) }))
  }

  function refresh() {
    void run(() => provider === 'B2B'
      ? apiFetch<Delivery>(`${path}/refresh`, { method: 'POST' })
      : apiFetch<Delivery>(path, { method: 'GET' }))
  }

  // Nothing has been sent and the company has not enabled this provider: do not offer a form that would fail.
  const inactive = delivery?.state === 'NOT_SENT' && delivery.connectionActive === false
  const inactiveNote = provider === 'AEAT'
    ? c('La remisión a la AEAT de pruebas no está activada.', 'Submission to the AEAT test environment is not enabled.')
    : c('La factura electrónica con B2Brouter no está activada.', 'B2Brouter e-invoicing is not enabled.')
  if (inactive && provider === 'B2B') {
    return configureLink ? <p className="fiscal-delivery-inactive">{inactiveNote} {configureLink}</p> : null
  }

  return <section className="fiscal-delivery" aria-label={provider === 'AEAT' ? c('Remisión a la AEAT de pruebas', 'Submission to the AEAT test environment') : c('Factura electrónica en B2Brouter', 'B2Brouter e-invoice')} aria-busy={busy}>
    <div className="fiscal-delivery-heading">
      <h3>{provider === 'AEAT' ? c('Remisión a la AEAT', 'AEAT submission') : c('Factura electrónica · B2Brouter', 'E-invoice · B2Brouter')}</h3>
      <StatusBadge tone="info">{provider === 'AEAT' ? c('Preproducción', 'Pre-production') : 'Sandbox'}</StatusBadge>
    </div>
    {!delivery && !error && <p>{c('Consultando el estado…', 'Checking status…')}</p>}
    {delivery && <p role="status">
      <StatusBadge tone={tones[delivery.state] ?? 'neutral'}>{labels[delivery.state] ?? delivery.state}</StatusBadge>
      {delivery.remoteId && <> · ID {delivery.remoteId}</>}
      {delivery.updatedAt && <> · {formatDateTime(delivery.updatedAt, locale)}</>}
    </p>}
    {delivery?.message && <p>{delivery.message}</p>}
    {delivery && uncertain.has(delivery.state) && <p>{provider === 'AEAT'
      ? c('Comprueba el registro en el portal de pruebas de la AEAT antes de volver a remitirlo. PERA no lo reintenta automáticamente para evitar duplicados.', 'Check the record on the AEAT test portal before submitting again. PERA does not retry automatically to avoid duplicates.')
      : c('Comprueba la factura en B2Brouter. PERA no repite el envío automáticamente para evitar duplicados.', 'Check the invoice in B2Brouter. PERA does not retry automatically to avoid duplicates.')}</p>}
    {error && <p role="alert" className="inline-error">{error}</p>}
    <div className="fiscal-delivery-actions">
      {inactive && <p className="fiscal-delivery-inactive">{inactiveNote} {configureLink}</p>}
      {delivery?.state === 'NOT_SENT' && !inactive && <>
        {provider === 'B2B' && <label>{c('ID del contacto sandbox (mismo NIF que el cliente)', 'Sandbox contact ID (same tax ID as the customer)')}<input type="text" inputMode="numeric" pattern="[0-9]*" value={contact} disabled={busy} onChange={(event) => setContact(event.target.value)} /></label>}
        <button type="button" className="button button-primary" disabled={busy || !canSend} onClick={() => void send()}><Send size={16} />{busy ? c('Enviando…', 'Sending…') : c('Enviar a pruebas', 'Send to test')}</button>
      </>}
      {!delivery && error && <button type="button" className="button button-ghost" onClick={() => setReload((value) => value + 1)}><RefreshCw size={16} />{c('Reintentar', 'Retry')}</button>}
      {delivery && delivery.state !== 'NOT_SENT' && <button type="button" className="button button-ghost" disabled={busy} onClick={refresh} title={provider === 'AEAT' ? c('Relee el último resultado guardado en PERA; no consulta a la AEAT.', 'Reloads the last result saved in PERA; it does not query AEAT.') : undefined}>
        <RefreshCw size={16} />{provider === 'AEAT' ? c('Ver estado guardado', 'Show saved status') : c('Consultar en B2Brouter', 'Check in B2Brouter')}
      </button>}
    </div>
  </section>
}
