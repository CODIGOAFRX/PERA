import type { PreviewLine } from './document'

const round2 = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100

/**
 * Previsualiza los totales de un documento de compra igual que los calcula el servidor: base de
 * cada línea al céntimo y cuota sobre la suma de bases de cada tipo impositivo.
 */
export function calculatePurchasePreview(lines: PreviewLine[]) {
  const baseByRate = new Map<number, number>()
  for (const line of lines) {
    const gross = Number(line.quantity) * Number(line.unitPrice)
    const net = round2(gross - gross * Number(line.discountPercentage) / 100)
    const rate = Number(line.taxPercentage)
    baseByRate.set(rate, round2((baseByRate.get(rate) ?? 0) + (Number.isFinite(net) ? net : 0)))
  }
  let net = 0
  let tax = 0
  for (const [rate, base] of baseByRate) {
    net = round2(net + base)
    tax = round2(tax + round2(base * rate / 100))
  }
  return { net, tax, total: round2(net + tax) }
}
