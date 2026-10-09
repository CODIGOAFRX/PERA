import { AlertTriangle, RefreshCw, Send, ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { EmptyState, LoadingState } from '../components/DataState'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge } from '../components/StatusBadge'
import { TableCaption } from '../components/TableCaption'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDate, formatDateTime } from '../lib/format'
import { verifactuMessage, verifactuStateLabel, verifactuStates, verifactuStateTone } from '../lib/verifactu'
import type { PageResponse, VerifactuRecord, VerifactuRemissionSummary, VerifactuState } from '../types/api'

/** Lo que la pantalla enseña al entrar: lo que pide atención. */
const attention: VerifactuState[] = ['REJECTED', 'ACCEPTED_WITH_ERRORS', 'SENT', 'PENDING']

/**
 * Seguimiento de la remisión a la AEAT: cómo está la conexión, cuántas facturas hay en cada estado y
 * cuáles necesitan atención. Es el equivalente al estado por factura del listado de DimproCristalWin.
 */
export function VerifactuPage() {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { hasPermission } = useAuth()
  const canWrite = hasPermission('verifactu:write')
  const [summary, setSummary] = useState<VerifactuRemissionSummary | null>(null)
  const [states, setStates] = useState<VerifactuState[]>(attention)
  const [data, setData] = useState<PageResponse<VerifactuRecord> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [sending, setSending] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => {
    apiFetch<VerifactuRemissionSummary>('/api/v1/verifactu-records/remission').then(setSummary).catch(() => setSummary(null))
  }, [refresh])
  useEffect(() => setPage(0), [states])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '20' })
    states.forEach((state) => params.append('state', state))
    apiFetch<PageResponse<VerifactuRecord>>(`/api/v1/verifactu-records/search?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [states, page, refresh])

  const remitNow = async () => {
    setSending(true)
    try {
      const outcome = await apiFetch<{ remitted: number; message: string }>('/api/v1/verifactu-records/remission', { method: 'POST' })
      notify(outcome.message || c('No había nada que remitir.', 'There was nothing to submit.'))
      setRefresh((value) => value + 1)
    } catch (cause) { notify(errorMessage(cause), 'error') } finally { setSending(false) }
  }

  const production = summary?.environment === 'PRODUCTION'
  const unanswered = (summary?.counts.PENDING ?? 0) + (summary?.counts.SENT ?? 0)
  const showing = (value: VerifactuState[]) => value.length === states.length && value.every((state) => states.includes(state))

  return <div className="page-stack">
    <PageHeader eyebrow={c('Facturación', 'Invoicing')} title="Veri*Factu" description={c('Remisión de las facturas a la AEAT: qué se ha presentado, qué espera y qué hay que revisar.', 'Invoice submission to AEAT: what has been reported, what is waiting and what needs review.')} icon={ShieldCheck}
      actions={<>
        <button className="button button-secondary" type="button" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={17} />{c('Actualizar', 'Refresh')}</button>
        {canWrite && summary?.connectionActive && unanswered > 0 && <button className="button button-primary" type="button" disabled={sending} onClick={() => void remitNow()}><Send size={17} />{sending ? c('Remitiendo…', 'Submitting…') : c('Remitir ahora', 'Submit now')}</button>}
      </>} />

    {summary && <section className="panel verifactu-connection" aria-label={c('Situación de la remisión', 'Submission status')}>
      <dl className="verifactu-facts">
        <div><dt>Veri*Factu</dt><dd><StatusBadge tone={summary.enabled ? 'success' : 'neutral'}>{summary.enabled ? c('Activado', 'Enabled') : c('Desactivado', 'Disabled')}</StatusBadge></dd></div>
        <div><dt>{c('Entorno', 'Environment')}</dt><dd><StatusBadge tone={production ? 'success' : 'info'}>{production ? c('Producción', 'Production') : c('Pruebas', 'Test')}</StatusBadge><small>{production ? c('Se presenta de verdad', 'Reported for real') : c('Preproducción, sin efecto fiscal', 'Pre-production, no tax effect')}</small></dd></div>
        <div><dt>{c('Remisión automática', 'Automatic submission')}</dt><dd><StatusBadge tone={summary.connectionActive ? 'success' : 'warning'}>{summary.connectionActive ? c('Activa', 'On') : summary.connectionConfigured ? c('Desactivada', 'Off') : c('Sin certificado', 'No certificate')}</StatusBadge></dd></div>
        <div><dt>{c('Último envío', 'Last submission')}</dt><dd><strong>{summary.lastSentAt ? formatDateTime(summary.lastSentAt, locale) : c('Todavía ninguno', 'None yet')}</strong>{summary.nextSendAt && new Date(summary.nextSendAt) > new Date() && <small>{c(`Próximo a partir de ${formatDateTime(summary.nextSendAt, locale)}`, `Next from ${formatDateTime(summary.nextSendAt, locale)}`)}</small>}</dd></div>
      </dl>
      <p className="verifactu-connection-note">{connectionText()}</p>
      {summary.lastError && summary.failures > 0 && <p className="certificate-warning" role="alert"><AlertTriangle size={15} aria-hidden="true" /> {summary.failures === 1 ? c('Último envío fallido: ', 'Last submission failed: ') : c(`${summary.failures} envíos fallidos seguidos. Último: `, `${summary.failures} failed submissions in a row. Last: `)}{summary.lastError}</p>}
    </section>}

    {summary && <div className="detail-summary verifactu-counts">
      {verifactuStates.map((state) => <button key={state} type="button" className={`verifactu-count ${showing([state]) ? 'active' : ''}`} onClick={() => setStates([state])} aria-pressed={showing([state])}>
        <small>{verifactuStateLabel(state, language)}</small>
        <strong>{summary.counts[state] ?? 0}</strong>
      </button>)}
    </div>}

    <section className="panel table-panel">
      <div className="panel-heading table-toolbar">
        <div><span className="eyebrow">{c('Registros de facturación', 'Invoicing records')}</span><h2>{showing(attention) ? c('Necesitan atención', 'Need attention') : states.length === 1 ? verifactuStateLabel(states[0], language) : c('Todos', 'All')}</h2></div>
        <div className="row-actions">
          <button type="button" className={`button ${showing(attention) ? 'button-primary' : 'button-ghost'}`} onClick={() => setStates(attention)}>{c('Necesitan atención', 'Need attention')}</button>
          <button type="button" className={`button ${states.length === verifactuStates.length ? 'button-primary' : 'button-ghost'}`} onClick={() => setStates([...verifactuStates])}>{c('Todos', 'All')}</button>
        </div>
      </div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {loading ? <LoadingState /> : data && data.content.length > 0 ? <>
        <div className="table-scroll"><table>
          <TableCaption es="Registros Veri*Factu" en="Veri*Factu records" />
          <thead><tr><th>{c('N.º', 'No.')}</th><th>{c('Factura', 'Invoice')}</th><th>{c('Fecha', 'Date')}</th><th className="align-right">{c('Importe', 'Amount')}</th><th>{c('Estado', 'Status')}</th><th>{c('Respuesta de la AEAT', 'AEAT answer')}</th><th>{c('Último intento', 'Last attempt')}</th></tr></thead>
          <tbody>{data.content.map((record) => <tr key={record.id}>
            <td>{record.sequenceNumber}</td>
            <td><strong className="document-number">{record.invoiceNumber}</strong>{record.invoiceKind && <small>{record.invoiceKind}</small>}</td>
            <td>{formatDate(record.invoiceDate, locale)}</td>
            <td className="align-right">{formatCurrency(record.totalAmount, 'EUR', locale)}</td>
            <td><StatusBadge tone={verifactuStateTone(record.state)}>{verifactuStateLabel(record.state, language)}</StatusBadge></td>
            <td>{verifactuMessage(record) || (record.aeatCsv ? `CSV ${record.aeatCsv}` : '—')}</td>
            <td>{record.lastAttemptAt ? formatDateTime(record.lastAttemptAt, locale) : '—'}{record.attemptCount ? <small>{record.attemptCount === 1 ? c('1 intento', '1 attempt') : c(`${record.attemptCount} intentos`, `${record.attemptCount} attempts`)}</small> : null}</td>
          </tr>)}</tbody>
        </table></div>
        <Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} />
      </> : <EmptyState title={showing(attention) ? c('Nada pendiente', 'Nothing pending') : c('Sin registros', 'No records')} description={showing(attention) ? c('Todas las facturas expedidas tienen respuesta de la AEAT.', 'Every issued invoice has an answer from AEAT.') : c('No hay registros en este estado.', 'There are no records in this status.')} />}
    </section>
  </div>

  function connectionText() {
    if (!summary) return ''
    if (!summary.enabled) return c('La empresa no tiene Veri*Factu activado: las facturas no generan registro. Se activa en Configuración › Veri*Factu.', 'The company does not have Veri*Factu enabled: invoices generate no record. Enable it in Settings › Veri*Factu.')
    if (!summary.connectionActive) return c('Las facturas generan su registro y su QR, pero no se remiten hasta que se cargue el certificado y se active la remisión en Conexiones.', 'Invoices get their record and QR, but are not submitted until the certificate is loaded and submission is enabled in Connections.')
    return c('Cada factura expedida se remite sola a la AEAT, en lotes y respetando el tiempo de espera que fija la AEAT entre envíos.', 'Every issued invoice is submitted to AEAT automatically, in batches and respecting the waiting time AEAT sets between submissions.')
  }

}
