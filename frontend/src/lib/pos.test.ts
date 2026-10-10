import { describe, expect, it } from 'vitest'
import { periodRange } from '../components/ReportPeriodPicker'
import { cartTotals, countDenominations, money, quickCashOptions, retailPrice, type CartLine } from './pos'
import { createScanBuffer } from './scanner'

const line = (quantity: number, basePrice: number, taxRate: number, discountPercent = 0, unitPrice: number | null = null): CartLine => ({
  key: Math.random().toString(36), quantity, unitPrice, discountPercent,
  product: { id: Math.random().toString(36), code: 'P', name: 'Producto', unitOfMeasure: 'UNIT', basePrice, taxRate },
})

describe('cálculos del TPV', () => {
  it('redondea como el servidor (mitad hacia arriba)', () => {
    expect(money(3.135)).toBe(3.14)
    expect(money(1.005)).toBe(1.01)
    expect(retailPrice(0.8264, 21)).toBe(1)
    expect(retailPrice(2.5, 10)).toBe(2.75)
  })

  it('aplica descuentos de línea y de ticket igual que PosCalculator', () => {
    const totals = cartTotals([line(3, 1, 21, 0, 1.21), line(2, 2.5, 10, 10), line(1, 0.99, 4, 0, 0.99)], 5)
    expect(totals.lineTotals).toEqual([3.45, 4.7, 0.94])
    expect(totals.totalAmount).toBe(9.09)
    expect(totals.grossAmount).toBe(10.12)
    expect(totals.discountAmount).toBe(1.03)
    const general = totals.taxes.find((tax) => tax.rate === 21)!
    expect(general.base).toBe(2.85)
    expect(general.tax).toBe(0.6)
    expect(money(totals.taxes.reduce((sum, tax) => sum + tax.base + tax.tax, 0))).toBe(9.09)
  })

  it('propone el importe exacto y los billetes siguientes para cobrar en efectivo', () => {
    expect(quickCashOptions(7.35)).toEqual([7.35, 8, 10, 20, 50, 100])
    expect(quickCashOptions(0)).toEqual([])
  })

  it('cuenta el cajón por billetes y monedas', () => {
    expect(countDenominations({ '50': 2, '20': 1, '0.5': 3, '0.01': 7 })).toBe(121.57)
  })
})

describe('lector de códigos por teclado', () => {
  it('reconoce un código escrito muy deprisa y terminado en Intro', () => {
    const read: string[] = []
    const buffer = createScanBuffer((code) => read.push(code))
    let at = 1000
    for (const key of '2000000000015') { buffer.key(key, at); at += 8 }
    expect(buffer.key('Enter', at)).toBe(true)
    expect(read).toEqual(['2000000000015'])
  })

  it('no confunde a una persona tecleando despacio con un lector', () => {
    const read: string[] = []
    const buffer = createScanBuffer((code) => read.push(code))
    let at = 1000
    for (const key of 'hola') { buffer.key(key, at); at += 400 }
    buffer.key('Enter', at)
    expect(read).toEqual([])
  })
})

describe('periodos de los listados', () => {
  it('calcula mes, trimestre y año naturales', () => {
    expect(periodRange('month', 2028, 2, 1)).toEqual({ fromDate: '2028-02-01', toDate: '2028-02-29' })
    expect(periodRange('quarter', 2026, 1, 3)).toEqual({ fromDate: '2026-07-01', toDate: '2026-09-30' })
    expect(periodRange('year', 2026, 1, 1)).toEqual({ fromDate: '2026-01-01', toDate: '2026-12-31' })
    expect(periodRange('all', 2026, 1, 1)).toEqual({ fromDate: '', toDate: '' })
    expect(periodRange('custom', 2026, 1, 1)).toBeNull()
  })
})
