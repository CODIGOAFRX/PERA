import { describe, expect, it } from 'vitest'
import { formatCurrency, formatDate, formatDateTime, formatNumber } from './format'

describe('format helpers', () => {
  it('formats the requested currency and locale', () => {
    expect(formatCurrency(1234.5, 'EUR', 'es-ES').replace('.', '')).toContain('1234,50')
    expect(formatCurrency(1234.5, 'USD', 'en-GB')).toContain('US$1,234.50')
    expect(formatCurrency(null, 'GBP', 'en-GB')).toContain('£0.00')
  })

  it('formats ISO dates without timezone drift in both languages', () => {
    expect(formatDate('2026-08-07', 'es-ES')).toMatch(/07.*ago.*2026/i)
    expect(formatDate('2026-08-07', 'en-GB')).toMatch(/07.*Aug.*2026/i)
    expect(formatDate(null, 'en-GB')).toBe('—')
  })

  it('formats decimal values with the requested precision', () => {
    expect(formatNumber(1234.5678, 'es-ES', 4)).toBe('1234,5678')
    expect(formatNumber(1234.5, 'en-GB')).toBe('1,234.5')
  })

  it('formats timestamps in the requested locale', () => {
    expect(formatDateTime('2026-08-07T12:34:00Z', 'en-GB')).toMatch(/07.*Aug.*2026/i)
    expect(formatDateTime(null, 'en-GB')).toBe('—')
  })
})
