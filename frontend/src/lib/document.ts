export interface PreviewLine {
  quantity: string | number
  unitPrice: string | number
  discountPercentage: string | number
  taxPercentage: string | number
}

export function calculateDocumentPreview(lines: PreviewLine[]) {
  return lines.reduce((result, line) => {
    const gross = Number(line.quantity) * Number(line.unitPrice)
    const net = gross * (1 - Number(line.discountPercentage) / 100)
    const tax = net * Number(line.taxPercentage) / 100
    return { net: result.net + net, tax: result.tax + tax, total: result.total + net + tax }
  }, { net: 0, tax: 0, total: 0 })
}
