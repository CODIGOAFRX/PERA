import { BellRing, BookOpenCheck, CircleDollarSign, Landmark, Plus, Search, Trash2 } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../components/Toast'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDate } from '../lib/format'
import { useTranslation } from '../i18n/I18nProvider'

type AccountingTab = 'pending' | 'entries' | 'accounts'

interface Account {
  id: string
  code: string
  name: string
  kind: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE'
  systemDefined: boolean
}

interface SuggestedLine {
  accountId: string
  accountCode: string
  accountName: string
  debit: number
  credit: number
}

interface InboxItem {
  id: string
  sourceType: string
  sourceId: string
  sourceNumber: string
  sourceDate: string
  sourceStatus: string
  readyToPost: boolean
  counterpartyCode: string | null
  counterpartyName: string
  currency: string
  netAmount: number
  taxAmount: number
  totalAmount: number
  suggestedLines: SuggestedLine[]
}

interface JournalLine {
  accountId: string
  accountCode: string
  accountName: string
  description: string | null
  debit: number
  credit: number
}

interface JournalEntry {
  id: string
  entryDate: string
  description: string
  status: 'POSTED'
  sourceType: string
  sourceNumber: string | null
  total: number
  lines: JournalLine[]
}

export function AccountingPage() {
  const { language, locale } = useTranslation()
  const c = useCallback((es: string, en: string) => language === 'es' ? es : en, [language])
  const [tab, setTab] = useState<AccountingTab>('pending')
  const [accounts, setAccounts] = useState<Account[]>([])
  const [pending, setPending] = useState<InboxItem[]>([])
  const [entries, setEntries] = useState<JournalEntry[]>([])
  const [accountQuery, setAccountQuery] = useState('')
  const [editing, setEditing] = useState<InboxItem | 'manual' | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    const load = async () => {
      const accountData = await apiFetch<Account[]>('/api/v1/accounting/accounts')
      const [inboxData, entryData] = await Promise.all([
        apiFetch<InboxItem[]>('/api/v1/accounting/inbox'),
        apiFetch<JournalEntry[]>('/api/v1/accounting/entries'),
      ])
      if (active) { setAccounts(accountData); setPending(inboxData); setEntries(entryData) }
    }
    load().catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [refresh])

  const saved = () => {
    setEditing(null)
    setRefresh((value) => value + 1)
    window.dispatchEvent(new Event('pera:accounting-updated'))
    notify(c('Asiento contabilizado y cuadrado.', 'Journal entry posted and balanced.'))
  }
  const visibleAccounts = useMemo(() => {
    const query = normalize(accountQuery)
    return accounts.filter((account) => !query || normalize(`${account.code} ${account.name}`).includes(query))
  }, [accountQuery, accounts])

  return <div className="page-stack accounting-page">
    <PageHeader eyebrow={c('Economía', 'Economy')} title={c('Contabilidad clara', 'Clear accounting')}
      description={c('Convierte la actividad de la empresa en asientos comprensibles, equilibrados y trazables.', 'Turn company activity into understandable, balanced and traceable journal entries.')}
      icon={BookOpenCheck} actions={<button className="button button-primary" type="button" onClick={() => setEditing('manual')}><Plus size={17} />{c('Asiento manual', 'Manual entry')}</button>} />

    <section className="accounting-overview" aria-label={c('Resumen contable', 'Accounting overview')}>
      <article><span className="accounting-metric-icon pending"><BellRing size={19} /></span><div><small>{c('Pendientes de revisar', 'Pending review')}</small><strong>{pending.length}</strong><p>{c('Facturas y movimientos sin asiento', 'Invoices and movements without an entry')}</p></div></article>
      <article><span className="accounting-metric-icon posted"><BookOpenCheck size={19} /></span><div><small>{c('Asientos recientes', 'Recent entries')}</small><strong>{entries.length}</strong><p>{c('Últimos 100 movimientos contabilizados', 'Latest 100 posted movements')}</p></div></article>
      <article><span className="accounting-metric-icon accounts"><Landmark size={19} /></span><div><small>{c('Cuentas disponibles', 'Available accounts')}</small><strong>{accounts.length}</strong><p>{c('Busca por nombre o por número', 'Search by name or number')}</p></div></article>
    </section>

    <div className="workspace-tabs" role="tablist" aria-label={c('Secciones de contabilidad', 'Accounting sections')}>
      <button type="button" className={tab === 'pending' ? 'active' : ''} onClick={() => setTab('pending')}><BellRing size={15} />{c('Pendientes', 'Pending')} {pending.length > 0 && <span className="tab-count">{pending.length}</span>}</button>
      <button type="button" className={tab === 'entries' ? 'active' : ''} onClick={() => setTab('entries')}><BookOpenCheck size={15} />{c('Libro diario', 'Journal')}</button>
      <button type="button" className={tab === 'accounts' ? 'active' : ''} onClick={() => setTab('accounts')}><Landmark size={15} />{c('Plan de cuentas', 'Chart of accounts')}</button>
    </div>

    {error && <div className="inline-error">{error}</div>}
    {loading ? <LoadingState label={c('Preparando tu contabilidad…', 'Preparing your accounting…')} /> : <>
      {tab === 'pending' && <section className="panel accounting-inbox">
        <header className="panel-heading"><div><h2>{c('Por contabilizar', 'To be posted')}</h2><p>{c('PERA recoge aquí la actividad que necesita reflejo contable.', 'PERA gathers activity that needs an accounting entry here.')}</p></div></header>
        {pending.length === 0 ? <EmptyState title={c('Todo está al día', 'Everything is up to date')} description={c('No hay facturas pendientes de contabilizar.', 'There are no invoices waiting to be posted.')} />
          : pending.map((item) => <article className="accounting-inbox-card" key={item.id}>
            <span className="accounting-source-icon"><CircleDollarSign size={21} /></span>
            <div className="accounting-source-copy">
              <div><strong>{item.sourceNumber}</strong><StatusBadge tone={item.readyToPost ? 'success' : 'warning'}>{item.readyToPost ? c('Lista', 'Ready') : c('Borrador', 'Draft')}</StatusBadge></div>
              <p>{item.counterpartyCode && <span>{item.counterpartyCode} · </span>}{item.counterpartyName}</p>
              <small>{formatDate(item.sourceDate, locale)} · {item.sourceType === 'RECTIFYING_INVOICE' ? c('Factura rectificativa', 'Credit note') : c('Factura de venta', 'Sales invoice')}</small>
            </div>
            <div className="accounting-source-amount"><small>{c('Total', 'Total')}</small><strong>{formatCurrency(item.totalAmount, item.currency, locale)}</strong><span>{c('Base', 'Net')} {formatCurrency(item.netAmount, item.currency, locale)} · IVA {formatCurrency(item.taxAmount, item.currency, locale)}</span></div>
            <button className="button button-secondary" type="button" onClick={() => setEditing(item)} disabled={!item.readyToPost}>{item.readyToPost ? c('Preparar asiento', 'Prepare entry') : c('Confirma en Ventas', 'Confirm in Sales')}</button>
          </article>)}
      </section>}

      {tab === 'entries' && <section className="panel table-panel">
        {entries.length === 0 ? <EmptyState title={c('Todavía no hay asientos', 'No journal entries yet')} description={c('Los asientos confirmados aparecerán aquí.', 'Posted journal entries will appear here.')} />
          : <div className="table-scroll"><table><thead><tr><th>{c('Fecha', 'Date')}</th><th>{c('Concepto', 'Description')}</th><th>{c('Origen', 'Source')}</th><th className="align-right">{c('Debe = Haber', 'Debit = Credit')}</th><th>{c('Estado', 'Status')}</th></tr></thead><tbody>{entries.map((entry) => <tr key={entry.id}><td>{formatDate(entry.entryDate, locale)}</td><td><strong>{entry.description}</strong><small>{entry.lines.length} {c('líneas', 'lines')}</small></td><td>{entry.sourceNumber ?? c('Asiento manual', 'Manual entry')}</td><td className="align-right"><strong>{formatCurrency(entry.total, 'EUR', locale)}</strong></td><td><StatusBadge tone="success">{c('Contabilizado', 'Posted')}</StatusBadge></td></tr>)}</tbody></table></div>}
      </section>}

      {tab === 'accounts' && <section className="panel table-panel">
        <div className="table-toolbar"><label className="search-control"><Search size={16} /><input value={accountQuery} onChange={(event) => setAccountQuery(event.target.value)} placeholder={c('Prueba “caja”, “banco” o 700…', 'Try “cash”, “bank” or 700…')} /></label></div>
        <div className="table-scroll"><table><thead><tr><th>{c('Número', 'Number')}</th><th>{c('Cuenta', 'Account')}</th><th>{c('Naturaleza', 'Type')}</th><th>{c('Origen', 'Origin')}</th></tr></thead><tbody>{visibleAccounts.map((account) => <tr key={account.id}><td><strong className="code-cell">{account.code}</strong></td><td>{account.name}</td><td>{accountKind(account.kind, language)}</td><td>{account.systemDefined ? c('Base PERA', 'PERA default') : c('Personalizada', 'Custom')}</td></tr>)}</tbody></table></div>
      </section>}
    </>}

    <Modal open={editing !== null} title={editing === 'manual' ? c('Nuevo asiento manual', 'New manual entry') : c('Contabilizar factura', 'Post invoice')}
      description={c('Debe y Haber se muestran separados. PERA solo permite guardar cuando ambos lados cuadran.', 'Debit and Credit are shown separately. PERA only posts when both sides balance.')}
      onClose={() => setEditing(null)} size="large">
      {editing && <JournalEntryForm key={editing === 'manual' ? 'manual' : editing.id} source={editing === 'manual' ? null : editing} accounts={accounts} onCancel={() => setEditing(null)} onSaved={saved} />}
    </Modal>
  </div>
}

interface EditableLine {
  key: number
  accountId: string
  accountText: string
  description: string
  debit: string
  credit: string
}

function JournalEntryForm({ source, accounts, onCancel, onSaved }: { source: InboxItem | null; accounts: Account[]; onCancel: () => void; onSaved: () => void }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [nextKey, setNextKey] = useState(10)
  const initialLines = source?.suggestedLines.map((line, index) => ({ key: index, accountId: line.accountId,
    accountText: `${line.accountCode} · ${line.accountName}`, description: '', debit: String(line.debit || ''), credit: String(line.credit || '') }))
    ?? [blankLine(0), blankLine(1)]
  const [entryDate, setEntryDate] = useState(source?.sourceDate ?? new Date().toISOString().slice(0, 10))
  const [description, setDescription] = useState(source ? `${source.sourceNumber} · ${source.counterpartyName}` : '')
  const [lines, setLines] = useState<EditableLine[]>(initialLines)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const debitTotal = lines.reduce((total, line) => total + Number(line.debit || 0), 0)
  const creditTotal = lines.reduce((total, line) => total + Number(line.credit || 0), 0)
  const balanced = debitTotal > 0 && Math.abs(debitTotal - creditTotal) < 0.00005

  const updateLine = (key: number, changes: Partial<EditableLine>) => setLines((current) => current.map((line) => line.key === key ? { ...line, ...changes } : line))
  const chooseAccount = (key: number, text: string) => {
    const normalized = normalize(text)
    const exact = accounts.find((account) => normalize(`${account.code} · ${account.name}`) === normalized
      || normalize(account.code) === normalized || normalize(account.name) === normalized)
    const matches = exact ? [exact] : accounts.filter((account) => normalize(`${account.code} ${account.name}`).includes(normalized))
    const account = matches.length === 1 ? matches[0] : undefined
    updateLine(key, { accountText: text, accountId: account?.id ?? '' })
  }
  const setSide = (key: number, side: 'debit' | 'credit', value: string) => updateLine(key,
    side === 'debit' ? { debit: value, credit: value ? '' : lines.find((line) => line.key === key)?.credit ?? '' }
      : { credit: value, debit: value ? '' : lines.find((line) => line.key === key)?.debit ?? '' })
  const addLine = () => { setLines((current) => [...current, blankLine(nextKey)]); setNextKey((value) => value + 1) }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await apiFetch(source ? `/api/v1/accounting/inbox/${source.id}/post` : '/api/v1/accounting/entries', {
        method: 'POST', body: JSON.stringify({ entryDate, description: description.trim(), lines: lines.map((line) => ({
          accountId: line.accountId, description: line.description.trim() || null,
          debit: Number(line.debit || 0), credit: Number(line.credit || 0),
        })) }),
      })
      onSaved()
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }

  return <form className="journal-form" onSubmit={submit}>
    <div className="journal-head">
      <Field label={c('Fecha del asiento', 'Entry date')} htmlFor="journal-date" required><input id="journal-date" type="date" value={entryDate} onChange={(event) => setEntryDate(event.target.value)} required /></Field>
      <Field label={c('Concepto', 'Description')} htmlFor="journal-description" required><input id="journal-description" value={description} maxLength={300} onChange={(event) => setDescription(event.target.value)} placeholder={c('¿Qué ha ocurrido?', 'What happened?')} required /></Field>
    </div>
    <datalist id="account-options">{accounts.map((account) => <option key={account.id} value={`${account.code} · ${account.name}`} />)}</datalist>
    <div className="journal-guide"><span className="debit">{c('DEBE', 'DEBIT')}<small>{c('Lo que entra o se recibe', 'What comes in or is received')}</small></span><span className="credit">{c('HABER', 'CREDIT')}<small>{c('De dónde sale o cómo se financia', 'Where it comes from or how it is funded')}</small></span></div>
    <div className="journal-lines">
      {lines.map((line, index) => <div className="journal-line" key={line.key}>
        <span className="journal-line-number">{index + 1}</span>
        <label><span>{c('Cuenta', 'Account')}</span><input list="account-options" value={line.accountText} onChange={(event) => chooseAccount(line.key, event.target.value)} placeholder={c('Escribe 70, caja, banco o ventas…', 'Type 70, cash, bank or sales…')} required={!line.accountId} className={line.accountText && !line.accountId ? 'invalid' : ''} /></label>
        <label className="debit-field"><span>{c('Debe', 'Debit')}</span><input type="number" min="0" step="0.0001" value={line.debit} onChange={(event) => setSide(line.key, 'debit', event.target.value)} placeholder="0,00" /></label>
        <label className="credit-field"><span>{c('Haber', 'Credit')}</span><input type="number" min="0" step="0.0001" value={line.credit} onChange={(event) => setSide(line.key, 'credit', event.target.value)} placeholder="0,00" /></label>
        <button className="icon-button" type="button" disabled={lines.length <= 2} onClick={() => setLines((current) => current.filter((candidate) => candidate.key !== line.key))} aria-label={c('Eliminar línea', 'Remove line')}><Trash2 size={15} /></button>
      </div>)}
      <button className="button button-ghost journal-add-line" type="button" onClick={addLine}><Plus size={15} />{c('Añadir línea', 'Add line')}</button>
    </div>
    <section className={`journal-balance ${balanced ? 'balanced' : 'unbalanced'}`} aria-live="polite">
      <div><small>{c('Total Debe', 'Total Debit')}</small><strong>{formatCurrency(debitTotal, source?.currency ?? 'EUR', locale)}</strong></div>
      <span>{balanced ? c('Cuadrado', 'Balanced') : c('Falta cuadrar', 'Not balanced')}</span>
      <div><small>{c('Total Haber', 'Total Credit')}</small><strong>{formatCurrency(creditTotal, source?.currency ?? 'EUR', locale)}</strong></div>
    </section>
    {error && <div className="form-error" role="alert">{error}</div>}
    <footer className="form-actions"><button className="button button-ghost" type="button" onClick={onCancel}>{c('Cancelar', 'Cancel')}</button><button className="button button-primary" type="submit" disabled={saving || !balanced || lines.some((line) => !line.accountId)}>{saving ? c('Contabilizando…', 'Posting…') : c('Contabilizar asiento', 'Post entry')}</button></footer>
  </form>
}

function blankLine(key: number): EditableLine { return { key, accountId: '', accountText: '', description: '', debit: '', credit: '' } }
function normalize(value: string) { return value.normalize('NFD').replace(/\p{Diacritic}/gu, '').trim().toLowerCase() }
function accountKind(kind: Account['kind'], language: 'es' | 'en') {
  const labels: Record<Account['kind'], [string, string]> = { ASSET: ['Activo', 'Asset'], LIABILITY: ['Pasivo', 'Liability'], EQUITY: ['Patrimonio neto', 'Equity'], INCOME: ['Ingreso', 'Income'], EXPENSE: ['Gasto', 'Expense'] }
  return labels[kind][language === 'es' ? 0 : 1]
}
