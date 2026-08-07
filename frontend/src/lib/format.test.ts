import { describe, expect, it } from 'vitest'
import { documentTypeLabel, formatCurrency, formatDate, unitLabel } from './format'

describe('format helpers', () => {
  it('formats monetary values for Spanish users', () => {
    expect(formatCurrency(1234.5).replace('.', '')).toContain('1234,50')
    expect(formatCurrency(null)).toContain('0,00')
  })

  it('formats ISO dates without timezone drift', () => {
    expect(formatDate('2026-08-07')).toMatch(/07.*ago.*2026/i)
    expect(formatDate(null)).toBe('—')
  })

  it('provides business labels instead of technical enum values', () => {
    expect(documentTypeLabel.DELIVERY_NOTE).toBe('Albarán')
    expect(unitLabel.SQUARE_METER).toBe('Metro cuadrado')
  })
})
