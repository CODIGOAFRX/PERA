export interface PreviewLine {
  quantity: string | number
  unitPrice: string | number
  discountPercentage: string | number
  taxPercentage: string | number
}

export function calculateDocumentPreview(lines: PreviewLine[]) {
  const round = (value: number, digits: number) => {
    const scale = 10 ** digits
    return Math.round((value + Number.EPSILON) * scale) / scale
  }
  const amounts = lines.reduce((result, line) => {
    const gross = Number(line.quantity) * Number(line.unitPrice)
    const discount = round(gross * Number(line.discountPercentage) / 100, 8)
    const net = round(gross - discount, 4)
    const tax = round(net * Number(line.taxPercentage) / 100, 4)
    return { net: result.net + Math.round(net * 10000), tax: result.tax + Math.round(tax * 10000) }
  }, { net: 0, tax: 0 })
  const net = round(amounts.net / 10000, 2)
  const tax = round(amounts.tax / 10000, 2)
  return { net, tax, total: round(net + tax, 2) }
}
