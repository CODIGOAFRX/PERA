import { BellRing, Check, Download, History, Pencil, Plus, RefreshCw, Settings2 } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { StatusBadge, type BadgeTone } from '../components/StatusBadge'
import { TableToolbar } from '../components/TableToolbar'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { apiDownload, apiFetch, errorMessage } from '../lib/api'
import { formatDateTime } from '../lib/format'
import type { AlertConditionOperator, AlertItem, AlertRule, AlertRuleInput, AlertSeverity, AlertStatus, AuditEvent, AuditOutcome, PageResponse } from '../types/api'

type HistoryTab = 'history' | 'alerts' | 'rules'
const outcomes: AuditOutcome[] = ['SUCCESS', 'FAILURE', 'DENIED']
const alertStatuses: AlertStatus[] = ['OPEN', 'ACKNOWLEDGED', 'RESOLVED']
const conditionOperators: AlertConditionOperator[] = ['EXISTS', 'NOT_EXISTS', 'EQUALS', 'NOT_EQUALS', 'CONTAINS', 'GREATER_THAN', 'GREATER_THAN_OR_EQUAL', 'LESS_THAN', 'LESS_THAN_OR_EQUAL']

export function HistoryPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<HistoryTab>('history')
  const [newRule, setNewRule] = useState(false)
  return <div className="page-stack">
    <PageHeader eyebrow={t('history.eyebrow')} title={t('history.title')} description={t('history.description')} icon={History}
      actions={tab === 'rules' ? <button className="button button-primary" type="button" onClick={() => setNewRule(true)}><Plus size={17} />{t('history.newRule')}</button> : undefined} />
    <div className="workspace-tabs" role="tablist" aria-label={t('history.tabs')}>
      <button className={tab === 'history' ? 'active' : ''} type="button" onClick={() => setTab('history')}><History size={16} />{t('history.tab.events')}</button>
      <button className={tab === 'alerts' ? 'active' : ''} type="button" onClick={() => setTab('alerts')}><BellRing size={16} />{t('history.tab.alerts')}</button>
      <button className={tab === 'rules' ? 'active' : ''} type="button" onClick={() => setTab('rules')}><Settings2 size={16} />{t('history.tab.rules')}</button>
    </div>
    {tab === 'history' && <EventHistory />}
    {tab === 'alerts' && <AlertInbox />}
    {tab === 'rules' && <AlertRules createRequested={newRule} onCreateHandled={() => setNewRule(false)} />}
  </div>
}

function EventHistory() {
  const { locale, t } = useTranslation()
  const [data, setData] = useState<PageResponse<AuditEvent> | null>(null)
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [outcome, setOutcome] = useState<AuditOutcome | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => { const timer = window.setTimeout(() => setDebounced(query), 250); return () => window.clearTimeout(timer) }, [query])
  useEffect(() => setPage(0), [debounced, outcome])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '25' })
    if (debounced) params.set('q', debounced)
    if (outcome) params.set('outcome', outcome)
    apiFetch<PageResponse<AuditEvent>>(`/api/v1/history?${params}`)
      .then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, debounced, outcome, refresh])

  const download = async () => {
    try {
      const params = new URLSearchParams()
      if (debounced) params.set('q', debounced)
      if (outcome) params.set('outcome', outcome)
      const { blob, filename } = await apiDownload(`/api/v1/history/export?${params}`)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click()
      URL.revokeObjectURL(url)
      notify(t('history.exported'))
    } catch (cause) { notify(errorMessage(cause), 'error') }
  }

  return <section className="panel table-panel">
    <TableToolbar value={query} onChange={setQuery} placeholder={t('history.search')}>
      <select aria-label={t('history.filterOutcome')} value={outcome} onChange={(event) => setOutcome(event.target.value as AuditOutcome | '')}><option value="">{t('history.allOutcomes')}</option>{outcomes.map((item) => <option key={item} value={item}>{t(`history.outcome.${item}`)}</option>)}</select>
      <button className="button button-secondary button-small" type="button" onClick={() => setRefresh((value) => value + 1)}><RefreshCw size={15} />{t('history.refresh')}</button>
      <button className="button button-secondary button-small" type="button" onClick={download}><Download size={15} />{t('history.export')}</button>
    </TableToolbar>
    {error && <div className="inline-error">{error}</div>}
    {loading ? <LoadingState /> : data && data.content.length ? <><div className="table-scroll"><table><thead><tr><th>{t('history.when')}</th><th>{t('history.event')}</th><th>{t('history.actor')}</th><th>{t('history.resource')}</th><th>{t('history.result')}</th></tr></thead><tbody>{data.content.map((event) => <tr key={event.id}><td>{formatDateTime(event.occurredAt, locale)}<small>{event.sourceService}</small></td><td><strong>{event.action}</strong><small>{event.eventType}</small></td><td>{event.actorName ?? t('history.system')}</td><td><strong>{event.resourceType}</strong><small>{event.resourceId ?? '—'}</small></td><td><StatusBadge tone={outcomeTone(event.outcome)}>{t(`history.outcome.${event.outcome}`)}</StatusBadge></td></tr>)}</tbody></table></div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title={t('history.empty')} description={t('history.emptyDescription')} />}
  </section>
}

function AlertInbox() {
  const { locale, t } = useTranslation()
  const [data, setData] = useState<PageResponse<AlertItem> | null>(null)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<AlertStatus | ''>('OPEN')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  useEffect(() => setPage(0), [status])
  useEffect(() => {
    let active = true
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: '20' }); if (status) params.set('status', status)
    apiFetch<PageResponse<AlertItem>>(`/api/v1/alerts?${params}`).then((response) => { if (active) { setData(response); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, status, refresh])
  const action = async (alert: AlertItem, next: 'acknowledge' | 'resolve') => {
    try { await apiFetch(`/api/v1/alerts/${alert.id}/${next}`, { method: 'POST' }); notify(t(`history.alert.${next}d`)); setRefresh((value) => value + 1) }
    catch (cause) { notify(errorMessage(cause), 'error') }
  }
  return <section className="panel table-panel"><TableToolbar value="" onChange={() => undefined} placeholder="" hideSearch><select aria-label={t('history.filterAlertStatus')} value={status} onChange={(event) => setStatus(event.target.value as AlertStatus | '')}><option value="">{t('history.allAlertStatuses')}</option>{alertStatuses.map((item) => <option key={item} value={item}>{t(`history.alertStatus.${item}`)}</option>)}</select></TableToolbar>
    {error && <div className="inline-error">{error}</div>}
    {loading ? <LoadingState /> : data && data.content.length ? <><div className="alert-inbox">{data.content.map((alert) => <article key={alert.id} className={`alert-card severity-${alert.severity.toLowerCase()}`}><div className="alert-card-icon"><BellRing size={18} /></div><div><div className="alert-card-heading"><strong>{alert.title}</strong><StatusBadge tone={severityTone(alert.severity)}>{t(`history.severity.${alert.severity}`)}</StatusBadge></div><p>{alert.message}</p><small>{formatDateTime(alert.createdAt, locale)} · {alert.ruleCode}</small></div><div className="alert-actions">{alert.status === 'OPEN' && <button className="button button-secondary button-small" type="button" onClick={() => action(alert, 'acknowledge')}><Check size={14} />{t('history.acknowledge')}</button>}{alert.status !== 'RESOLVED' && <button className="button button-primary button-small" type="button" onClick={() => action(alert, 'resolve')}>{t('history.resolve')}</button>}</div></article>)}</div><Pagination page={data.page.number} totalPages={data.page.totalPages} totalElements={data.page.totalElements} onChange={setPage} /></> : <EmptyState title={t('history.noAlerts')} description={t('history.noAlertsDescription')} />}
  </section>
}

function AlertRules({ createRequested, onCreateHandled }: { createRequested: boolean; onCreateHandled: () => void }) {
  const { t } = useTranslation()
  const [rules, setRules] = useState<AlertRule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<AlertRule | null>(null)
  const [creating, setCreating] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()
  useEffect(() => { if (createRequested) { setCreating(true); onCreateHandled() } }, [createRequested, onCreateHandled])
  useEffect(() => { let active = true; setLoading(true); apiFetch<AlertRule[]>('/api/v1/alert-rules').then((response) => { if (active) { setRules(response); setError('') } }).catch((cause) => { if (active) setError(errorMessage(cause)) }).finally(() => { if (active) setLoading(false) }); return () => { active = false } }, [refresh])
  const deactivate = async (rule: AlertRule) => { try { await apiFetch(`/api/v1/alert-rules/${rule.id}`, { method: 'DELETE' }); notify(t('history.ruleDeactivated')); setRefresh((value) => value + 1) } catch (cause) { notify(errorMessage(cause), 'error') } }
  return <section className="panel table-panel">{error && <div className="inline-error">{error}</div>}{loading ? <LoadingState /> : rules.length ? <div className="table-scroll"><table><thead><tr><th>{t('field.code')}</th><th>{t('field.name')}</th><th>{t('history.trigger')}</th><th>{t('history.severityLabel')}</th><th>{t('field.status')}</th><th>{t('common.actions')}</th></tr></thead><tbody>{rules.map((rule) => <tr key={rule.id}><td><strong className="document-number">{rule.code}</strong></td><td>{rule.name}</td><td><strong>{rule.eventType}</strong><small>{rule.conditionField ? `${rule.conditionField} ${rule.conditionOperator ?? ''} ${rule.conditionValue ?? ''}` : t('history.noCondition')}</small></td><td><StatusBadge tone={severityTone(rule.severity)}>{t(`history.severity.${rule.severity}`)}</StatusBadge></td><td><StatusBadge tone={rule.active ? 'success' : 'neutral'}>{rule.active ? t('common.active') : t('common.inactive')}</StatusBadge></td><td><div className="row-actions"><button className="icon-button" type="button" onClick={() => setEditing(rule)} aria-label={t('history.editRule', { name: rule.name })}><Pencil size={15} /></button>{rule.active && <button className="button button-secondary button-small" type="button" onClick={() => deactivate(rule)}>{t('history.deactivate')}</button>}</div></td></tr>)}</tbody></table></div> : <EmptyState title={t('history.noRules')} description={t('history.noRulesDescription')} action={<button className="button button-secondary" type="button" onClick={() => setCreating(true)}>{t('history.newRule')}</button>} />}
    <Modal open={creating || editing !== null} title={editing ? t('history.editRuleTitle') : t('history.newRule')} description={t('history.ruleDescription')} onClose={() => { setCreating(false); setEditing(null) }} size="large"><AlertRuleForm rule={editing} onCancel={() => { setCreating(false); setEditing(null) }} onSaved={() => { setCreating(false); setEditing(null); setRefresh((value) => value + 1); notify(t('history.ruleSaved')) }} /></Modal>
  </section>
}

function AlertRuleForm({ rule, onCancel, onSaved }: { rule: AlertRule | null; onCancel: () => void; onSaved: () => void }) {
  const { language, t } = useTranslation()
  const [form, setForm] = useState<AlertRuleInput>(() => rule ? { ...rule } : {
    code: '', name: '', eventType: 'API_MUTATION', action: '', resourceType: '', conditionField: '',
    conditionOperator: null, conditionValue: '', severity: 'WARNING',
    titleTemplate: '{{action}} · {{resourceType}}',
    messageTemplate: language === 'es'
      ? '{{actorName}} realizó {{action}} sobre {{resourceId}}'
      : '{{actorName}} performed {{action}} on {{resourceId}}',
    cooldownMinutes: 0, deliveryChannel: 'IN_APP', active: true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const hasCondition = Boolean(form.conditionField)
  const needsValue = hasCondition && form.conditionOperator !== 'EXISTS' && form.conditionOperator !== 'NOT_EXISTS'
  const submit = async (event: FormEvent) => { event.preventDefault(); setSaving(true); setError(''); const payload = { ...form, action: form.action || null, resourceType: form.resourceType || null, conditionField: form.conditionField || null, conditionOperator: form.conditionField ? form.conditionOperator : null, conditionValue: needsValue ? form.conditionValue || null : null }; try { await apiFetch(rule ? `/api/v1/alert-rules/${rule.id}` : '/api/v1/alert-rules', { method: rule ? 'PUT' : 'POST', body: JSON.stringify(payload) }); onSaved() } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) } }
  return <form onSubmit={submit}><div className="form-grid"><Field label={t('field.code')} htmlFor="alert-code" required><input id="alert-code" value={form.code} disabled={Boolean(rule)} onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })} required /></Field><Field label={t('field.name')} htmlFor="alert-name" required><input id="alert-name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required /></Field><Field label={t('history.eventType')} htmlFor="alert-event" required><input id="alert-event" value={form.eventType} onChange={(event) => setForm({ ...form, eventType: event.target.value })} required /></Field><Field label={t('history.action')} htmlFor="alert-action"><input id="alert-action" value={form.action ?? ''} onChange={(event) => setForm({ ...form, action: event.target.value })} /></Field><Field label={t('history.resourceType')} htmlFor="alert-resource"><input id="alert-resource" value={form.resourceType ?? ''} onChange={(event) => setForm({ ...form, resourceType: event.target.value })} /></Field><Field label={t('history.severityLabel')} htmlFor="alert-severity" required><select id="alert-severity" value={form.severity} onChange={(event) => setForm({ ...form, severity: event.target.value as AlertSeverity })}><option value="INFO">{t('history.severity.INFO')}</option><option value="WARNING">{t('history.severity.WARNING')}</option><option value="CRITICAL">{t('history.severity.CRITICAL')}</option></select></Field><Field label={t('history.channel')} htmlFor="alert-channel" required><select id="alert-channel" value={form.deliveryChannel} onChange={(event) => setForm({ ...form, deliveryChannel: event.target.value as 'IN_APP' })}><option value="IN_APP">{t('history.channel.IN_APP')}</option></select></Field><Field label={t('history.conditionField')} htmlFor="alert-field"><input id="alert-field" placeholder="statusCode" value={form.conditionField ?? ''} onChange={(event) => setForm({ ...form, conditionField: event.target.value, conditionOperator: event.target.value ? form.conditionOperator ?? 'EQUALS' : null })} /></Field><Field label={t('history.operator')} htmlFor="alert-operator"><select id="alert-operator" disabled={!hasCondition} value={form.conditionOperator ?? ''} onChange={(event) => setForm({ ...form, conditionOperator: event.target.value as AlertConditionOperator })}><option value="">—</option>{conditionOperators.map((operator) => <option key={operator} value={operator}>{t(`history.operator.${operator}`)}</option>)}</select></Field><Field label={t('history.conditionValue')} htmlFor="alert-value"><input id="alert-value" disabled={!needsValue} value={form.conditionValue ?? ''} onChange={(event) => setForm({ ...form, conditionValue: event.target.value })} /></Field><Field label={t('history.cooldown')} htmlFor="alert-cooldown"><input id="alert-cooldown" type="number" min="0" max="525600" value={form.cooldownMinutes} onChange={(event) => setForm({ ...form, cooldownMinutes: Number(event.target.value) })} /></Field><Field label={t('history.titleTemplate')} htmlFor="alert-title" required wide><input id="alert-title" value={form.titleTemplate} onChange={(event) => setForm({ ...form, titleTemplate: event.target.value })} required /></Field><Field label={t('history.messageTemplate')} htmlFor="alert-message" required wide><textarea id="alert-message" rows={3} value={form.messageTemplate} onChange={(event) => setForm({ ...form, messageTemplate: event.target.value })} required /></Field><Field label={t('field.status')} htmlFor="alert-active"><label className="switch-row" htmlFor="alert-active"><input id="alert-active" type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span>{t('history.ruleActive')}</span></label></Field></div>{error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} /></form>
}

function outcomeTone(outcome: AuditOutcome): BadgeTone { return outcome === 'SUCCESS' ? 'success' : outcome === 'DENIED' ? 'warning' : 'danger' }
function severityTone(severity: AlertSeverity): BadgeTone { return severity === 'CRITICAL' ? 'danger' : severity === 'WARNING' ? 'warning' : 'info' }
