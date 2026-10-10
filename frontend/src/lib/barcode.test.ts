import { describe, expect, it } from 'vitest'
import { code128Values, eanCheckDigit, encodeBarcode, encodeCode128, encodeEan13, internalEan13, isValidEan13 } from './barcode'

describe('códigos de barras', () => {
  it('calcula y comprueba el dígito de control EAN', () => {
    expect(eanCheckDigit('400638133393')).toBe(1)
    expect(isValidEan13('4006381333931')).toBe(true)
    expect(isValidEan13('4006381333932')).toBe(false)
    expect(isValidEan13('12345')).toBe(false)
    expect(eanCheckDigit('9638507')).toBe(4)
  })

  it('genera códigos internos de tienda válidos con prefijo 2', () => {
    const code = internalEan13(42)
    expect(code).toHaveLength(13)
    expect(code.startsWith('20')).toBe(true)
    expect(isValidEan13(code)).toBe(true)
  })

  it('dibuja un EAN-13 de 95 módulos con sus guardas', () => {
    const encoded = encodeEan13('4006381333931')
    expect(encoded.modules).toHaveLength(95)
    expect(encoded.modules.startsWith('101')).toBe(true)
    expect(encoded.modules.slice(45, 50)).toBe('01010')
    expect(encoded.modules.endsWith('101')).toBe(true)
  })

  it('usa Code 128 B con su suma de control para textos', () => {
    // Start B (104) y la suma ponderada de «Wikipedia» módulo 103 = 88.
    const values = code128Values('Wikipedia')
    expect(values[0]).toBe(104)
    expect(values[values.length - 2]).toBe(88)
    expect(values[values.length - 1]).toBe(106)
  })

  it('agrupa las cifras de dos en dos (subconjunto C) cuando compensa', () => {
    expect(code128Values('123456').slice(0, 4)).toEqual([105, 12, 34, 56])
    const mixed = code128Values('T2026-CAJA1-000001')
    expect(mixed[0]).toBe(104)
    expect(mixed).toContain(99)
    // Cada símbolo ocupa 11 módulos y la parada 13.
    expect(encodeCode128('T2026-CAJA1-000001').modules.length % 11).toBe(2)
  })

  it('elige EAN-13 solo si el número es válido', () => {
    expect(encodeBarcode('4006381333931').symbology).toBe('ean13')
    expect(encodeBarcode('4006381333932').symbology).toBe('code128')
    expect(encodeBarcode('SARD-120').symbology).toBe('code128')
  })
})
