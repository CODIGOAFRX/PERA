import { describe, expect, it } from 'vitest'
import { calculateDocumentPreview } from './document'

describe('calculateDocumentPreview', () => {
  it('rounds the reported delivery note to payable cents', () => {
    expect(calculateDocumentPreview([{ quantity: 2, unitPrice: 27.95, discountPercentage: 0, taxPercentage: 21 }]))
      .toEqual({ net: 55.9, tax: 11.74, total: 67.64 })
  })

  it('adds rounded net and tax instead of rounding an inconsistent total', () => {
    expect(calculateDocumentPreview([{ quantity: 3, unitPrice: 19.99, discountPercentage: 10, taxPercentage: 21 }]))
      .toEqual({ net: 53.97, tax: 11.33, total: 65.3 })
  })

  it('matches the backend discount and tax formula', () => {
    const result = calculateDocumentPreview([{ quantity: 2, unitPrice: 50, discountPercentage: 10, taxPercentage: 21 }])
    expect(result.net).toBeCloseTo(90)
    expect(result.tax).toBeCloseTo(18.9)
    expect(result.total).toBeCloseTo(108.9)
  })

  it('adds several independent lines', () => {
    const result = calculateDocumentPreview([
      { quantity: 1, unitPrice: 100, discountPercentage: 0, taxPercentage: 21 },
      { quantity: 2, unitPrice: 20, discountPercentage: 50, taxPercentage: 10 },
    ])
    expect(result.total).toBeCloseTo(143)
  })
})
