import { CalendarRange } from 'lucide-react'
import { useState } from 'react'

export type PeriodMode = 'all' | 'month' | 'quarter' | 'year' | 'custom'

const pad = (value: number) => String(value).padStart(2, '0')
const lastDay = (year: number, month: number) => new Date(year, month, 0).getDate()

/** Fechas de inicio y fin (incluidas) del periodo elegido. */
export function periodRange(mode: PeriodMode, year: number, month: number, quarter: number): { fromDate: string; toDate: string } | null {
  if (mode === 'month') return { fromDate: `${year}-${pad(month)}-01`, toDate: `${year}-${pad(month)}-${pad(lastDay(year, month))}` }
  if (mode === 'quarter') {
    const first = (quarter - 1) * 3 + 1
    return { fromDate: `${year}-${pad(first)}-01`, toDate: `${year}-${pad(first + 2)}-${pad(lastDay(year, first + 2))}` }
  }
  if (mode === 'year') return { fromDate: `${year}-01-01`, toDate: `${year}-12-31` }
  if (mode === 'all') return { fromDate: '', toDate: '' }
  return null
}

const MONTHS: Array<[string, string]> = [['Enero', 'January'], ['Febrero', 'February'], ['Marzo', 'March'], ['Abril', 'April'], ['Mayo', 'May'], ['Junio', 'June'], ['Julio', 'July'], ['Agosto', 'August'], ['Septiembre', 'September'], ['Octubre', 'October'], ['Noviembre', 'November'], ['Diciembre', 'December']]

/**
 * Atajos de periodo para los listados: mes, trimestre o año natural. «Fechas a medida» deja los
 * campos Desde/Hasta para elegirlas a mano.
 */
export function ReportPeriodPicker({ language, onChange }: { language: 'es' | 'en'; onChange: (range: { fromDate: string; toDate: string }, label: string) => void }) {
  const c = (es: string, en: string) => language === 'es' ? es : en
  const today = new Date()
  const [mode, setMode] = useState<PeriodMode>('all')
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [quarter, setQuarter] = useState(Math.floor(today.getMonth() / 3) + 1)
  const years = Array.from({ length: 6 }, (_, index) => today.getFullYear() - index)

  const apply = (nextMode: PeriodMode, nextYear = year, nextMonth = month, nextQuarter = quarter) => {
    setMode(nextMode); setYear(nextYear); setMonth(nextMonth); setQuarter(nextQuarter)
    const range = periodRange(nextMode, nextYear, nextMonth, nextQuarter)
    if (!range) return
    const label = nextMode === 'month' ? `${MONTHS[nextMonth - 1][language === 'es' ? 0 : 1]} ${nextYear}`
      : nextMode === 'quarter' ? `${c('Trimestre', 'Quarter')} ${nextQuarter} · ${nextYear}`
      : nextMode === 'year' ? `${c('Año', 'Year')} ${nextYear}` : ''
    onChange(range, label)
  }

  return <div className="report-period field-wide" role="group" aria-label={c('Periodo del listado', 'Report period')}>
    <span className="report-period-title"><CalendarRange size={16} />{c('Periodo', 'Period')}</span>
    <div className="report-period-modes">
      {([['all', c('Todo', 'All')], ['month', c('Mensual', 'Monthly')], ['quarter', c('Trimestral', 'Quarterly')], ['year', c('Anual', 'Yearly')], ['custom', c('Fechas a medida', 'Custom dates')]] as const).map(([value, text]) =>
        <button key={value} type="button" className={mode === value ? 'active' : ''} aria-pressed={mode === value} onClick={() => apply(value)}>{text}</button>)}
    </div>
    {(mode === 'month' || mode === 'quarter' || mode === 'year') && <div className="report-period-values">
      <select aria-label={c('Año', 'Year')} value={year} onChange={(event) => apply(mode, Number(event.target.value))}>{years.map((value) => <option key={value} value={value}>{value}</option>)}</select>
      {mode === 'month' && <select aria-label={c('Mes', 'Month')} value={month} onChange={(event) => apply(mode, year, Number(event.target.value))}>{MONTHS.map((names, index) => <option key={names[0]} value={index + 1}>{names[language === 'es' ? 0 : 1]}</option>)}</select>}
      {mode === 'quarter' && <select aria-label={c('Trimestre', 'Quarter')} value={quarter} onChange={(event) => apply(mode, year, month, Number(event.target.value))}>{[1, 2, 3, 4].map((value) => <option key={value} value={value}>{c(`${value}.º trimestre`, `Q${value}`)}</option>)}</select>}
    </div>}
  </div>
}
