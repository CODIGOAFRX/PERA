import { CircleUserRound, LockKeyhole, LogOut, MonitorSmartphone, RefreshCw, Store, Unlock } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { EmptyState, LoadingState } from '../components/DataState'
import { LanguageSelector } from '../components/LanguageSelector'
import { PearBrandMark } from '../components/PearBrandMark'
import { OpenSessionDialog } from '../components/pos/PosDialogs'
import { PosTerminal } from '../components/pos/PosTerminal'
import { useToast } from '../components/Toast'
import { useCompanyProfile } from '../hooks/useCompanyProfile'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatCurrency, formatDateTime } from '../lib/format'
import type { PosRegister, PosSessionSummary } from '../lib/pos'
import { Link } from '../routing/Router'

const LAST_REGISTER_KEY = 'pera.pos.lastRegister'

/**
 * TPV. Primero se elige la caja (abierta o por abrir) y después se vende en ella. Un cajero
 * entra directamente aquí al iniciar sesión.
 */
export function PosPage() {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { identity, company, logout, hasRole } = useAuth()
  const { notify } = useToast()
  const profile = useCompanyProfile()
  const [registers, setRegisters] = useState<PosRegister[] | null>(null)
  const [error, setError] = useState('')
  const [active, setActive] = useState<{ register: PosRegister; summary: PosSessionSummary } | null>(null)
  const [opening, setOpening] = useState<PosRegister | null>(null)
  const [entering, setEntering] = useState('')
  const cashierOnly = (identity?.roles ?? []).every((role) => role.toUpperCase() === 'CASHIER')

  const load = useCallback(async () => {
    setError('')
    try { setRegisters(await apiFetch<PosRegister[]>('/api/v1/pos/registers')) } catch (cause) { setError(errorMessage(cause)); setRegisters([]) }
  }, [])
  useEffect(() => { void load() }, [load])

  const enter = async (register: PosRegister) => {
    if (!register.openSession) { setOpening(register); return }
    setEntering(register.id)
    try {
      const summary = await apiFetch<PosSessionSummary>(`/api/v1/pos/sessions/${register.openSession.id}/summary`)
      try { localStorage.setItem(LAST_REGISTER_KEY, register.id) } catch { /* preferencia solo en memoria */ }
      setActive({ register, summary })
    } catch (cause) { notify(errorMessage(cause), 'error') } finally { setEntering('') }
  }
  const open = async (amount: number) => {
    if (!opening) return
    const summary = await apiFetch<PosSessionSummary>(`/api/v1/pos/registers/${opening.id}/open`, { method: 'POST', body: JSON.stringify({ openingAmount: amount }) })
    try { localStorage.setItem(LAST_REGISTER_KEY, opening.id) } catch { /* preferencia solo en memoria */ }
    notify(c(`${opening.name} abierta.`, `${opening.name} opened.`))
    setActive({ register: opening, summary })
    setOpening(null)
  }
  const backToRegisters = () => { setActive(null); setRegisters(null); void load() }

  if (active) return <PosTerminal key={active.summary.sessionId} register={active.register} initialSummary={active.summary} company={profile} language={language} locale={locale} onExit={backToRegisters} onClosed={backToRegisters} />

  let lastRegister = ''
  try { lastRegister = localStorage.getItem(LAST_REGISTER_KEY) ?? '' } catch { lastRegister = '' }
  const longDate = new Date().toLocaleDateString(locale, { weekday: 'long', day: 'numeric', month: 'long' })
  const today = longDate.charAt(0).toUpperCase() + longDate.slice(1)

  return <div className="pos-picker">
    <header className="pos-picker-header">
      <div className="pos-brand"><PearBrandMark /><span><strong>PERA</strong><small>TPV</small></span></div>
      <div className="pos-picker-user">
        {!cashierOnly && <Link className="button button-ghost button-small" to="/">{c('Volver a PERA ERP', 'Back to PERA ERP')}</Link>}
        <LanguageSelector compact />
        <span><CircleUserRound size={18} />{identity?.displayName}</span>
        <button type="button" className="button button-ghost button-small" onClick={logout}><LogOut size={16} />{c('Salir', 'Sign out')}</button>
      </div>
    </header>
    <main className="pos-picker-main">
      <div className="pos-picker-intro">
        <span className="eyebrow">{company?.name ?? profile.displayName} · {today}</span>
        <h1>{c(`Hola, ${identity?.displayName?.split(/[ ·]/)[0] ?? ''}`, `Hello, ${identity?.displayName?.split(/[ ·]/)[0] ?? ''}`)}</h1>
        <p>{c('Elige la caja en la que vas a trabajar. Si está cerrada, la abres con el fondo de cambio.', 'Choose the till you will work on. If it is closed, open it with the change float.')}</p>
      </div>
      {error && <div className="inline-error" role="alert">{error}</div>}
      {registers === null ? <LoadingState /> : registers.length === 0 ? <EmptyState title={c('No hay cajas activas', 'There are no active tills')} description={hasRole('ADMIN') || hasRole('OWNER') ? c('Crea una caja en Cartera y caja → Caja.', 'Create a till in Collections and cash → Cash.') : c('Pide a un administrador que cree una caja.', 'Ask an administrator to create a till.')} /> :
        <div className="pos-register-grid">{registers.map((register) => {
          const session = register.openSession
          return <button key={register.id} type="button" className={`pos-register-card ${session ? 'open' : 'closed'} ${register.id === lastRegister ? 'last' : ''}`} onClick={() => void enter(register)} disabled={entering === register.id}>
            <span className="pos-register-icon">{session ? <Unlock size={26} /> : <LockKeyhole size={26} />}</span>
            <span className="pos-register-name"><strong>{register.name}</strong><small>{register.code}{register.ownerName ? ` · ${register.ownerName}` : ''}</small></span>
            {session ? <span className="pos-register-facts">
              <span><small>{c('Abierta desde', 'Open since')}</small><b>{formatDateTime(session.openedAt, locale)}</b></span>
              <span><small>{c('Ventas del turno', 'Shift sales')}</small><b>{formatCurrency(session.salesTotal, profile.baseCurrency, locale)}</b></span>
              <span><small>{c('Tickets', 'Tickets')}</small><b>{session.ticketCount}</b></span>
              <span><small>{c('Efectivo en caja', 'Cash in drawer')}</small><b>{formatCurrency(session.cashBalance, profile.baseCurrency, locale)}</b></span>
            </span> : <span className="pos-register-facts closed"><span><small>{c('Cerrada', 'Closed')}</small><b>{register.lastClosedAt ? c(`Último cierre ${formatDateTime(register.lastClosedAt, locale)}`, `Last closed ${formatDateTime(register.lastClosedAt, locale)}`) : c('Sin turnos todavía', 'No shifts yet')}</b></span></span>}
            <span className="pos-register-action">{entering === register.id ? <RefreshCw className="spin" size={18} /> : session ? <MonitorSmartphone size={18} /> : <Store size={18} />}{session ? c('Entrar a vender', 'Start selling') : c('Abrir caja', 'Open till')}</span>
          </button>
        })}</div>}
      <p className="pos-picker-help">{c('Atajos: F2 buscar · F4 cobrar · F6 tickets · F7 devolución · F8 movimientos de caja · F9 aparcar. Funciona con lector de códigos USB o Bluetooth y con la cámara.', 'Shortcuts: F2 search · F4 charge · F6 tickets · F7 refund · F8 cash movements · F9 park. Works with USB or Bluetooth barcode scanners and with the camera.')}</p>
    </main>
    <OpenSessionDialog open={opening !== null} registerName={opening?.name ?? ''} currency={profile.baseCurrency} locale={locale} language={language} onClose={() => setOpening(null)} onOpen={open} />
  </div>
}
