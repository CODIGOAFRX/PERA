import { CalendarClock, Landmark, Plus, ReceiptText, Trash2, WalletCards } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../components/Toast'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDate, paymentStatusLabel } from '../lib/format'
import type { CommercialDocument, DueDate, PageResponse, PaymentMethod, PaymentMethodInput } from '../types/api'

export function FinancePage() {
  const [methods, setMethods] = useState<PaymentMethod[]>([])
  const [invoices, setInvoices] = useState<CommercialDocument[]>([])
  const [selected, setSelected] = useState<CommercialDocument | null>(null)
  const [dueDates, setDueDates] = useState<DueDate[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingDue, setLoadingDue] = useState(false)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => {
    setLoading(true)
    Promise.all([
      apiFetch<PaymentMethod[]>('/api/v1/payment-methods'),
      apiFetch<PageResponse<CommercialDocument>>('/api/v1/documents?type=INVOICE&size=50&sort=issueDate,desc'),
    ]).then(([paymentMethods, page]) => { setMethods(paymentMethods); setInvoices(page.content); setError('') })
      .catch((cause) => setError(errorMessage(cause))).finally(() => setLoading(false))
  }, [refresh])

  const selectInvoice = async (invoice: CommercialDocument) => {
    setSelected(invoice); setLoadingDue(true); setDueDates([])
    try { setDueDates(await apiFetch<DueDate[]>(`/api/v1/due-dates?documentId=${invoice.id}`)) }
    catch (cause) { notify(errorMessage(cause), 'error') } finally { setLoadingDue(false) }
  }

  const generateDueDates = async () => {
    if (!selected?.paymentMethodId) return
    setLoadingDue(true)
    try {
      const generated = await apiFetch<DueDate[]>('/api/v1/due-dates/generate', { method: 'POST', body: JSON.stringify({ documentId: selected.id, paymentMethodId: selected.paymentMethodId, issueDate: selected.issueDate, totalAmount: selected.totalAmount }) })
      setDueDates(generated); notify('Vencimientos generados correctamente.')
    } catch (cause) { notify(errorMessage(cause), 'error') } finally { setLoadingDue(false) }
  }

  if (loading) return <div className="page-stack"><PageHeader eyebrow="Tesorería" title="Finanzas" description="Formas de pago y vencimientos." icon={Landmark} /><LoadingState /></div>
  return <div className="page-stack">
    <PageHeader eyebrow="Tesorería" title="Finanzas" description="Configura formas de pago y controla los próximos cobros." icon={Landmark} actions={<button className="button button-primary" type="button" onClick={() => setCreating(true)}><Plus size={17} />Nueva forma de pago</button>} />
    {error && <div className="inline-error">{error}</div>}
    <section className="finance-overview">
      <div className="panel payment-method-panel">
        <div className="panel-heading"><div><span className="eyebrow">Configuración</span><h2>Formas de pago</h2></div><StatusBadge tone="info">{methods.length} activas</StatusBadge></div>
        {methods.length === 0 ? <EmptyState title="Sin formas de pago" description="Crea una para poder repartir vencimientos." /> : <div className="payment-method-list">{methods.map((method) => <div className="payment-method-card" key={method.id}><span className="row-icon"><WalletCards size={18} /></span><div><strong>{method.name}</strong><small>{method.code}</small></div><div className="rule-chips">{method.rules.map((rule) => <span key={rule.installment}>{rule.percentage}% · {rule.dueDays === 0 ? 'al contado' : `${rule.dueDays} días`}</span>)}</div></div>)}</div>}
      </div>
      <div className="panel finance-kpi"><span className="metric-icon"><ReceiptText size={21} /></span><small>Facturas registradas</small><strong>{invoices.length}</strong><p>{formatCurrency(invoices.reduce((total, invoice) => total + Number(invoice.totalAmount), 0))} en el listado reciente</p></div>
    </section>

    <section className="finance-workspace">
      <div className="panel invoice-list-panel">
        <div className="panel-heading"><div><span className="eyebrow">Cartera</span><h2>Facturas</h2></div></div>
        {invoices.length === 0 ? <EmptyState title="No hay facturas" description="Las facturas confirmadas aparecerán aquí." /> : <div className="invoice-select-list">{invoices.map((invoice) => <button type="button" key={invoice.id} className={selected?.id === invoice.id ? 'selected' : ''} onClick={() => selectInvoice(invoice)}><span><strong>{invoice.number}</strong><small>{invoice.customerName} · {formatDate(invoice.issueDate)}</small></span><span><StatusBadge tone={invoice.paymentStatus === 'PAID' ? 'success' : 'warning'}>{paymentStatusLabel[invoice.paymentStatus]}</StatusBadge><strong>{formatCurrency(invoice.totalAmount)}</strong></span></button>)}</div>}
      </div>
      <div className="panel due-date-panel">
        <div className="panel-heading"><div><span className="eyebrow">Planificación</span><h2>Vencimientos</h2></div>{selected && <span className="code-cell">{selected.number}</span>}</div>
        {!selected ? <EmptyState title="Selecciona una factura" description="Consulta o genera su calendario de cobro." /> : loadingDue ? <LoadingState label="Consultando vencimientos…" /> : dueDates.length > 0 ? <div className="due-date-list">{dueDates.map((dueDate) => <div key={dueDate.id || dueDate.installment}><span className="due-icon"><CalendarClock size={18} /></span><span><strong>Plazo {dueDate.installment}</strong><small>{formatDate(dueDate.dueDate)}</small></span><StatusBadge tone={dueDate.status === 'PAID' ? 'success' : 'warning'}>{dueDate.status === 'PAID' ? 'Pagado' : 'Pendiente'}</StatusBadge><strong>{formatCurrency(dueDate.amount)}</strong></div>)}<div className="due-total"><span>Total planificado</span><strong>{formatCurrency(dueDates.reduce((total, dueDate) => total + Number(dueDate.amount), 0))}</strong></div></div> : <div className="generate-due"><span className="empty-icon"><CalendarClock size={22} /></span><h3>Sin vencimientos generados</h3>{selected.paymentMethodId ? <><p>Se aplicará la forma de pago asignada a la factura.</p><button className="button button-primary" type="button" onClick={generateDueDates}>Generar vencimientos</button></> : <p>Esta factura no tiene una forma de pago asignada. Crea otra factura seleccionando una para generar su calendario.</p>}</div>}
      </div>
    </section>
    <Modal open={creating} title="Nueva forma de pago" description="Los porcentajes de todos los plazos deben sumar 100 %." onClose={() => setCreating(false)}><PaymentMethodForm onCancel={() => setCreating(false)} onSaved={() => { setCreating(false); setRefresh((value) => value + 1); notify('Forma de pago creada correctamente.') }} /></Modal>
  </div>
}

function PaymentMethodForm({ onCancel, onSaved }: { onCancel: () => void; onSaved: () => void }) {
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [rules, setRules] = useState([{ dueDays: '0', percentage: '100' }])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const total = rules.reduce((sum, rule) => sum + Number(rule.percentage || 0), 0)
  const updateRule = (index: number, field: 'dueDays' | 'percentage', value: string) => setRules((current) => current.map((rule, ruleIndex) => ruleIndex === index ? { ...rule, [field]: value } : rule))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (Math.abs(total - 100) > 0.0001) { setError('Los porcentajes deben sumar exactamente 100 %.'); return }
    setSaving(true); setError('')
    const payload: PaymentMethodInput = { code: code.trim(), name: name.trim(), rules: rules.map((rule) => ({ dueDays: Number(rule.dueDays), percentage: Number(rule.percentage) })) }
    try { await apiFetch<PaymentMethod>('/api/v1/payment-methods', { method: 'POST', body: JSON.stringify(payload) }); onSaved() }
    catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form onSubmit={submit}><div className="form-grid"><Field label="Código" htmlFor="payment-code" required><input id="payment-code" value={code} onChange={(event) => setCode(event.target.value)} maxLength={40} required /></Field><Field label="Nombre" htmlFor="payment-name" required><input id="payment-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={160} required /></Field></div>
    <div className="document-lines-heading"><div><span className="eyebrow">Reparto</span><h3>Plazos</h3></div><button className="button button-secondary button-small" type="button" onClick={() => setRules((current) => [...current, { dueDays: '30', percentage: '0' }])}><Plus size={15} />Añadir plazo</button></div>
    <div className="payment-rule-editor">{rules.map((rule, index) => <div key={index}><span className="rule-number">{index + 1}</span><Field label="Días desde emisión" htmlFor={`rule-days-${index}`}><input id={`rule-days-${index}`} type="number" min="0" value={rule.dueDays} onChange={(event) => updateRule(index, 'dueDays', event.target.value)} /></Field><Field label="Porcentaje" htmlFor={`rule-percentage-${index}`}><div className="suffix-input"><input id={`rule-percentage-${index}`} type="number" min="0.0001" max="100" step="0.0001" value={rule.percentage} onChange={(event) => updateRule(index, 'percentage', event.target.value)} /><span>%</span></div></Field><button className="icon-button" type="button" disabled={rules.length === 1} onClick={() => setRules((current) => current.filter((_, ruleIndex) => ruleIndex !== index))} aria-label={`Eliminar plazo ${index + 1}`}><Trash2 size={16} /></button></div>)}</div>
    <div className={`percentage-total ${Math.abs(total - 100) < 0.0001 ? 'valid' : 'invalid'}`}><span>Total asignado</span><strong>{total.toLocaleString('es-ES')} %</strong></div>
    {error && <div className="form-error" role="alert">{error}</div>}<FormActions onCancel={onCancel} saving={saving} submitLabel="Crear forma de pago" />
  </form>
}
