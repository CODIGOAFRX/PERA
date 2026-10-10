import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { localIsoDate } from '../lib/date'
import { formatDateTime, formatNumber } from '../lib/format'
import { reasonLabel } from '../lib/stockReasons'
import type { FlatPage, StockMovement, Warehouse } from '../types/api'
import { EmptyState, LoadingState } from './DataState'
import { Field } from './Form'
import { StatusBadge } from './StatusBadge'
import { TableCaption } from './TableCaption'

const INBOUND = new Set(['PURCHASE_RECEIPT', 'ADJUSTMENT_IN', 'TRANSFER_IN', 'SALES_RETURN'])
const LOSS_REASONS = new Set(['DAMAGED', 'EXPIRED', 'SPOILED', 'LOST', 'INTERNAL_USE'])
const LIMIT = 1000

/** Origen legible de un movimiento, para agrupar lo que ha pasado en el día. */
export function movementOrigin(movement: Pick<StockMovement, 'sourceType' | 'type'>, language: 'es' | 'en') {
  const c = (es: string, en: string) => language === 'es' ? es : en
  switch (movement.sourceType) {
    case 'PURCHASE_DOCUMENT': return c('Compras', 'Purchases')
    case 'SALES_DOCUMENT': return c('Ventas (albaranes y facturas)', 'Sales (delivery notes and invoices)')
    case 'POS_TICKET': return c('TPV', 'POS')
    case 'SCAN': return c('Almacén móvil', 'Mobile warehouse')
    case 'TRANSFER': return c('Traspasos', 'Transfers')
    default: return c('Ajustes manuales', 'Manual adjustments')
  }
}

/**
 * Actividad de un día en el almacén: entradas, salidas, mermas por motivo y quién ha hecho cada
 * cosa. Es lo que consulta el responsable sin bajar al almacén.
 */
export function InventoryActivity({ warehouses }: { warehouses: Warehouse[] }) {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [day, setDay] = useState(localIsoDate())
  const [warehouseId, setWarehouseId] = useState('')
  const [movements, setMovements] = useState<StockMovement[] | null>(null)
  const [total, setTotal] = useState(0)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setMovements(null); setError('')
    const start = new Date(`${day}T00:00:00`)
    const end = new Date(start)
    end.setDate(start.getDate() + 1)
    const params = new URLSearchParams({ occurredFrom: start.toISOString(), occurredTo: end.toISOString(), size: String(LIMIT), sort: 'occurredAt,desc' })
    if (warehouseId) params.set('warehouseId', warehouseId)
    apiFetch<FlatPage<StockMovement>>(`/api/v1/stock-movements?${params}`)
      .then((page) => { if (active) { setMovements(page.content); setTotal(page.totalElements) } })
      .catch((cause) => { if (active) { setMovements([]); setError(errorMessage(cause)) } })
    return () => { active = false }
  }, [day, warehouseId])

  const summary = useMemo(() => {
    const list = movements ?? []
    const add = (map: Map<string, { count: number; units: number }>, key: string, units: number) => {
      const current = map.get(key) ?? { count: 0, units: 0 }
      map.set(key, { count: current.count + 1, units: current.units + units })
    }
    const origins = new Map<string, { count: number; units: number }>()
    const losses = new Map<string, { count: number; units: number }>()
    const people = new Map<string, { in: number; out: number }>()
    let inUnits = 0, outUnits = 0, inCount = 0, outCount = 0
    for (const movement of list) {
      const inbound = INBOUND.has(movement.type)
      const quantity = Number(movement.quantity)
      if (inbound) { inUnits += quantity; inCount += 1 } else { outUnits += quantity; outCount += 1 }
      add(origins, movementOrigin(movement, language), quantity)
      if (!inbound && movement.reasonCode && LOSS_REASONS.has(movement.reasonCode)) add(losses, reasonLabel(movement.reasonCode, language), quantity)
      const who = movement.performedByName || c('Automático', 'Automatic')
      const person = people.get(who) ?? { in: 0, out: 0 }
      if (inbound) person.in += 1; else person.out += 1
      people.set(who, person)
    }
    return { inUnits, outUnits, inCount, outCount, origins: [...origins.entries()], losses: [...losses.entries()], people: [...people.entries()] }
  }, [movements, language])

  const warehouseCode = (id: string) => warehouses.find((item) => item.id === id)?.code ?? '—'

  return <section className="panel inventory-activity" aria-label={c('Actividad del día', 'Daily activity')}>
    <div className="inventory-activity-toolbar">
      <Field label={c('Día', 'Day')} htmlFor="activity-day"><input id="activity-day" type="date" value={day} max={localIsoDate()} onChange={(event) => event.target.value && setDay(event.target.value)} /></Field>
      <Field label={c('Almacén', 'Warehouse')} htmlFor="activity-warehouse"><select id="activity-warehouse" value={warehouseId} onChange={(event) => setWarehouseId(event.target.value)}><option value="">{c('Todos', 'All')}</option>{warehouses.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></Field>
    </div>
    {error && <div className="inline-error" role="alert">{error}</div>}
    {movements === null ? <LoadingState /> : movements.length === 0 ? <EmptyState title={c('Sin movimientos ese día', 'No movements that day')} description={c('Las entradas, salidas, ventas del TPV y escaneos del móvil aparecen aquí.', 'Entries, exits, POS sales and mobile scans appear here.')} /> : <>
      <div className="metric-grid">
        <article className="metric-card"><span className="metric-label">{c('Entradas', 'Entries')}</span><strong>{formatNumber(summary.inUnits, locale, 3)}</strong><span className="metric-hint">{summary.inCount} {c('movimientos', 'movements')}</span></article>
        <article className="metric-card"><span className="metric-label">{c('Salidas', 'Exits')}</span><strong>{formatNumber(summary.outUnits, locale, 3)}</strong><span className="metric-hint">{summary.outCount} {c('movimientos', 'movements')}</span></article>
        <article className="metric-card"><span className="metric-label">{c('Mermas', 'Losses')}</span><strong>{formatNumber(summary.losses.reduce((sum, [, value]) => sum + value.units, 0), locale, 3)}</strong><span className="metric-hint">{c('Roturas, caducados, pérdidas…', 'Breakage, expiry, losses…')}</span></article>
        <article className="metric-card"><span className="metric-label">{c('Personas', 'People')}</span><strong>{summary.people.filter(([name]) => name !== c('Automático', 'Automatic')).length}</strong><span className="metric-hint">{c('Han registrado movimientos', 'Recorded movements')}</span></article>
      </div>
      <div className="inventory-activity-grid">
        <div className="inventory-activity-card"><h3>{c('Por origen', 'By origin')}</h3><ul>{summary.origins.map(([name, value]) => <li key={name}><span>{name}</span><b>{value.count} · {formatNumber(value.units, locale, 3)} {c('uds.', 'units')}</b></li>)}</ul></div>
        <div className="inventory-activity-card"><h3>{c('Mermas por motivo', 'Losses by reason')}</h3>{summary.losses.length === 0 ? <p className="pos-muted">{c('Ninguna.', 'None.')}</p> : <ul>{summary.losses.map(([name, value]) => <li key={name}><span>{name}</span><b>{formatNumber(value.units, locale, 3)} {c('uds.', 'units')}</b></li>)}</ul>}</div>
        <div className="inventory-activity-card"><h3>{c('Por persona', 'By person')}</h3><ul>{summary.people.map(([name, value]) => <li key={name}><span>{name}</span><b>+{value.in} / −{value.out}</b></li>)}</ul></div>
      </div>
      {total > LIMIT && <p className="pos-muted">{c(`Se muestran los ${LIMIT} movimientos más recientes de ${total}.`, `Showing the ${LIMIT} most recent of ${total} movements.`)}</p>}
      <div className="table-scroll"><table>
        <TableCaption es="Movimientos del día" en="Movements of the day" />
        <thead><tr><th>{c('Hora', 'Time')}</th><th>{c('Producto', 'Product')}</th><th>{c('Almacén', 'Warehouse')}</th><th className="align-right">{c('Cantidad', 'Quantity')}</th><th>{c('Origen', 'Origin')}</th><th>{c('Motivo / documento', 'Reason / document')}</th><th>{c('Quién', 'Who')}</th></tr></thead>
        <tbody>{movements.slice(0, 200).map((movement) => {
          const inbound = INBOUND.has(movement.type)
          return <tr key={movement.id}>
            <td>{formatDateTime(movement.occurredAt, locale)}</td>
            <td><strong>{movement.productName}</strong><small>{movement.productCode}</small></td>
            <td>{warehouseCode(movement.warehouseId)}</td>
            <td className="align-right"><StatusBadge tone={inbound ? 'success' : 'warning'}>{inbound ? '+' : '−'}{formatNumber(movement.quantity, locale, 3)}</StatusBadge></td>
            <td>{movementOrigin(movement, language)}</td>
            <td>{movement.reasonCode ? reasonLabel(movement.reasonCode, language) : movement.sourceNumber || movement.note || '—'}{movement.reasonCode && movement.note && <small>{movement.note}</small>}</td>
            <td>{movement.performedByName || <span className="pos-muted">{c('Automático', 'Automatic')}</span>}</td>
          </tr>
        })}</tbody>
      </table></div>
    </>}
  </section>
}
