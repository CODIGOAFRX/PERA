import { expect, it } from 'vitest'
import { calculatePurchasePreview } from './purchase'

const line = (quantity: number, unitPrice: number, taxPercentage: number, discountPercentage = 0) => ({ quantity, unitPrice, discountPercentage, taxPercentage })

it('calculates the tax on the joint base of each rate, like the server', () => {
  // Line by line the tax would be 0.01 × 3 = 0.03; on the joint base 0.09 × 21 % = 0.02.
  expect(calculatePurchasePreview([line(1, 0.03, 21), line(1, 0.03, 21), line(1, 0.03, 21)])).toEqual({ net: 0.09, tax: 0.02, total: 0.11 })
})

it('keeps each tax rate in its own group and applies the discount before rounding', () => {
  expect(calculatePurchasePreview([line(2, 27.95, 21), line(1, 10, 10), line(1, 5, 0)])).toEqual({ net: 70.9, tax: 12.74, total: 83.64 })
  expect(calculatePurchasePreview([line(2, 27.95, 21, 10)])).toEqual({ net: 50.31, tax: 10.57, total: 60.88 })
})
