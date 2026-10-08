import { Send } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { verifactuMessage, verifactuStateLabel, verifactuStateTone } from '../lib/verifactu'
import type { VerifactuRecord, VerifactuRemissionSummary } from '../types/api'
import { StatusBadge } from './StatusBadge'

type Delivery = { state: string; message: string }

/**
 * Situación del registro ante la AEAT. La remisión es automática; aquí se explica en qué punto está y,
 * si aún no tiene respuesta, se puede adelantar con «Enviar ahora».
 */
export function VerifactuRemission({ record, configureLink, onChanged }: { record: VerifactuRecord; configureLink?: ReactNode; onChanged: () => void }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const { hasPermission } = useAuth()
  const [summary, setSummary] = useState<VerifactuRemissionSummary | null>(null)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    apiFetch<VerifactuRemissionSummary>('/api/v1/verifactu-records/remission')
      .then((value) => { if (active) setSummary(value) })
      .catch(() => { if (active) setSummary(null) })
    return () => { active = false }
  }, [record.state, record.lastAttemptAt])

  const unanswered = record.state === 'PENDING' || record.state === 'SENT'
  const production = summary?.environment === 'PRODUCTION'
  const canSend = unanswered && summary?.connectionActive === true && hasPermission('verifactu:write')
  const message = verifactuMessage(record)

  const send = async () => {
    setBusy(true); setError(''); setNotice('')
    try {
      const result = await apiFetch<Delivery>(`/api/v1/verifactu-records/${record.id}/delivery`, { method: 'POST' })
      if (result.message) setNotice(result.message)
      onChanged()
    } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }

  return <section className="fiscal-delivery" aria-label={c('Remisión a la AEAT', 'AEAT submission')} aria-busy={busy}>
    <div className="fiscal-delivery-heading">
      <h3>{c('Remisión a la AEAT', 'AEAT submission')}</h3>
      {summary && <StatusBadge tone={production ? 'success' : 'info'}>{production ? c('Producción', 'Production') : c('Pruebas', 'Test')}</StatusBadge>}
    </div>
    <p role="status">
      <StatusBadge tone={verifactuStateTone(record.state)}>{verifactuStateLabel(record.state, language)}</StatusBadge>
      {record.lastAttemptAt && <> · {c('último intento', 'last attempt')} {formatDateTime(record.lastAttemptAt, locale)}</>}
      {record.attemptCount ? <> · {record.attemptCount === 1 ? c('1 intento', '1 attempt') : c(`${record.attemptCount} intentos`, `${record.attemptCount} attempts`)}</> : null}
    </p>
    {message && <p>{message}</p>}
    <p className="fiscal-delivery-note">{explanation()}</p>
    {notice && <p role="status">{notice}</p>}
    {error && <p role="alert" className="inline-error">{error}</p>}
    {canSend && <div className="fiscal-delivery-actions">
      <button type="button" className="button button-secondary" disabled={busy} onClick={() => void send()}><Send size={16} />{busy ? c('Enviando…', 'Sending…') : c('Enviar ahora', 'Send now')}</button>
    </div>}
  </section>

  function explanation(): ReactNode {
    if (record.state === 'ACCEPTED') return c('La AEAT tiene este registro. No hay que hacer nada más.', 'AEAT holds this record. Nothing else to do.')
    if (record.state === 'ACCEPTED_WITH_ERRORS') return c('La AEAT lo ha admitido, pero señala un defecto que conviene corregir con una subsanación.', 'AEAT admitted it but flags a defect that should be corrected.')
    if (record.state === 'REJECTED') return c('La AEAT no lo ha admitido. Hay que corregir el motivo y subsanar el registro; PERA no lo reenvía tal cual.', 'AEAT did not admit it. The cause must be fixed and the record corrected; PERA does not resend it unchanged.')
    if (summary && !summary.connectionActive) return <>{c('La remisión a la AEAT no está activada: el registro espera hasta que se active.', 'AEAT submission is not enabled: the record waits until it is.')} {configureLink}</>
    if (record.state === 'SENT') return c('Remitido sin respuesta confirmada. PERA lo vuelve a remitir en unos minutos y la AEAT contesta con el estado que tenga, sin duplicarlo.', 'Submitted without a confirmed answer. PERA resubmits it in a few minutes and AEAT answers with its current state, without duplicating it.')
    return summary?.nextSendAt && new Date(summary.nextSendAt) > new Date()
      ? c(`Se remitirá automáticamente a partir de ${formatDateTime(summary.nextSendAt, locale)}, cuando termine el tiempo de espera de la AEAT.`, `It will be submitted automatically from ${formatDateTime(summary.nextSendAt, locale)}, once the AEAT waiting time ends.`)
      : c('Se remitirá automáticamente en el próximo envío, en menos de un minuto.', 'It will be submitted automatically in the next batch, within a minute.')
  }
}
