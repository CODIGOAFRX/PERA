import { describe, expect, it } from 'vitest'
import { calculateDocumentPreview } from './document'

describe('calculateDocumentPreview', () => {
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
