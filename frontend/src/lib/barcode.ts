/**
 * Códigos de barras para etiquetas y tickets, sin dependencias.
 *
 * EAN-13 para los artículos que ya lo tienen (lo que llevan impreso los productos de fábrica) y
 * Code 128 para todo lo demás: códigos internos, números de ticket o códigos con letras. Ambos los
 * leen todas las pistolas USB/Bluetooth y la cámara del móvil.
 */

export type BarcodeSymbology = 'ean13' | 'code128'

export interface BarcodeModules {
  symbology: BarcodeSymbology
  /** '1' barra, '0' espacio; un carácter por módulo. */
  modules: string
  /** Módulos que bajan más en EAN-13 (guardas), para dibujarlos como en los productos. */
  guards: Set<number>
  /** Texto legible bajo las barras. */
  text: string
}

const CODE128_PATTERNS = [
  '212222', '222122', '222221', '121223', '121322', '131222', '122213', '122312', '132212', '221213',
  '221312', '231212', '112232', '122132', '122231', '113222', '123122', '123221', '223211', '221132',
  '221231', '213212', '223112', '312131', '311222', '321122', '321221', '312212', '322112', '322211',
  '212123', '212321', '232121', '111323', '131123', '131321', '112313', '132113', '132311', '211313',
  '231113', '231311', '112133', '112331', '132131', '113123', '113321', '133121', '313121', '211331',
  '231131', '213113', '213311', '213131', '311123', '311321', '331121', '312113', '312311', '332111',
  '314111', '221411', '431111', '111224', '111422', '121124', '121421', '141122', '141221', '112214',
  '112412', '122114', '122411', '142112', '142211', '241211', '221114', '413111', '241112', '134111',
  '111242', '121142', '121241', '114212', '124112', '124211', '411212', '421112', '421211', '212141',
  '214121', '412121', '111143', '111341', '131141', '114113', '114311', '411113', '411311', '113141',
  '114131', '311141', '411131', '211412', '211214', '211232', '2331112',
]
const START_B = 104
const START_C = 105
const CODE_B = 100
const CODE_C = 99
const STOP = 106

const EAN_L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011']
const EAN_G = ['0100111', '0110011', '0011011', '0100001', '0011101', '0111001', '0000101', '0010001', '0001001', '0010111']
const EAN_R = ['1110010', '1100110', '1101100', '1000010', '1011100', '1001110', '1010000', '1000100', '1001000', '1110100']
const EAN_PARITY = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLL', 'LGLGLG', 'LGLGGL', 'LGGLGL']

/** Dígito de control EAN/UPC de los dígitos dados (12 para EAN-13, 7 para EAN-8). */
export function eanCheckDigit(digits: string): number {
  let sum = 0
  for (let index = 0; index < digits.length; index += 1) {
    const weight = (digits.length - index) % 2 === 1 ? 3 : 1
    sum += Number(digits[index]) * weight
  }
  return (10 - (sum % 10)) % 10
}

export function isValidEan13(code: string): boolean {
  return /^\d{13}$/.test(code) && eanCheckDigit(code.slice(0, 12)) === Number(code[12])
}

/**
 * Código EAN-13 interno de la empresa (prefijo 2, reservado para uso en tienda): sirve para
 * etiquetar artículos que no traen código de fábrica sin chocar con códigos de otros fabricantes.
 */
export function internalEan13(sequence: number, prefix = '20'): string {
  const body = `${prefix}${String(Math.max(0, Math.floor(sequence))).padStart(12 - prefix.length, '0')}`.slice(0, 12)
  return `${body}${eanCheckDigit(body)}`
}

export function encodeEan13(code: string): BarcodeModules {
  if (!isValidEan13(code)) throw new Error(`«${code}» no es un EAN-13 válido.`)
  const parity = EAN_PARITY[Number(code[0])]
  let modules = '101'
  for (let index = 1; index <= 6; index += 1) {
    const digit = Number(code[index])
    modules += parity[index - 1] === 'L' ? EAN_L[digit] : EAN_G[digit]
  }
  modules += '01010'
  for (let index = 7; index <= 12; index += 1) modules += EAN_R[Number(code[index])]
  modules += '101'
  const guards = new Set<number>([0, 1, 2, 45, 46, 47, 48, 49, 92, 93, 94])
  return { symbology: 'ean13', modules, guards, text: code }
}

/** Valores Code 128 del texto: subconjunto C para tramos largos de cifras, B para el resto. */
export function code128Values(text: string): number[] {
  const printable = [...text].map((char) => (char.charCodeAt(0) >= 32 && char.charCodeAt(0) <= 126 ? char : '?')).join('')
  if (!printable) throw new Error('No hay nada que codificar.')
  const values: number[] = []
  let index = 0
  let subset: 'B' | 'C' | null = null
  const digitRun = (from: number) => { let end = from; while (end < printable.length && /\d/.test(printable[end])) end += 1; return end - from }
  const pushB = () => {
    if (subset !== 'B') { values.push(subset === null ? START_B : CODE_B); subset = 'B' }
    values.push(printable.charCodeAt(index) - 32)
    index += 1
  }
  while (index < printable.length) {
    let run = digitRun(index)
    // Compensa cambiar a C (dos cifras por símbolo) a partir de 4 cifras seguidas. Si son impares,
    // la primera va en B para que el resto forme parejas.
    if (run >= 4) {
      if (run % 2 === 1) { pushB(); run -= 1 }
      if (subset !== 'C') { values.push(subset === null ? START_C : CODE_C); subset = 'C' }
      for (let pair = 0; pair < run / 2; pair += 1) { values.push(Number(printable.slice(index, index + 2))); index += 2 }
      continue
    }
    pushB()
  }
  let checksum = values[0]
  for (let position = 1; position < values.length; position += 1) checksum += values[position] * position
  values.push(checksum % 103, STOP)
  return values
}

export function encodeCode128(text: string): BarcodeModules {
  let modules = ''
  for (const value of code128Values(text)) {
    const pattern = CODE128_PATTERNS[value]
    for (let index = 0; index < pattern.length; index += 1) modules += (index % 2 === 0 ? '1' : '0').repeat(Number(pattern[index]))
  }
  return { symbology: 'code128', modules, guards: new Set(), text }
}

/** EAN-13 si el valor es uno válido; si no, Code 128. */
export function encodeBarcode(value: string, preferred: BarcodeSymbology | 'auto' = 'auto'): BarcodeModules {
  const trimmed = value.trim()
  if ((preferred === 'auto' || preferred === 'ean13') && isValidEan13(trimmed)) return encodeEan13(trimmed)
  return encodeCode128(trimmed)
}
