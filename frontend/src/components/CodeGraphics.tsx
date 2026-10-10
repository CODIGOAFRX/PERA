import QRCode from 'qrcode'
import { useEffect, useMemo, useState } from 'react'
import { encodeBarcode, type BarcodeSymbology } from '../lib/barcode'

/**
 * Código de barras en SVG: se imprime nítido a cualquier tamaño, también en impresoras de
 * etiquetas térmicas de 203 ppp.
 */
export function BarcodeSvg({ value, symbology = 'auto', height = 48, showText = true, className, fontSize = 11 }: {
  value: string
  symbology?: BarcodeSymbology | 'auto'
  height?: number
  showText?: boolean
  className?: string
  fontSize?: number
}) {
  const encoded = useMemo(() => {
    try { return encodeBarcode(value, symbology) } catch { return null }
  }, [value, symbology])
  if (!encoded) return <span className={className}>{value}</span>
  const quiet = encoded.symbology === 'ean13' ? 9 : 10
  const width = encoded.modules.length + quiet * 2
  const textSpace = showText ? fontSize + 3 : 0
  const bars: Array<{ x: number; w: number; guard: boolean }> = []
  for (let index = 0; index < encoded.modules.length; index += 1) {
    if (encoded.modules[index] !== '1') continue
    const last = bars[bars.length - 1]
    const guard = encoded.guards.has(index)
    if (last && last.x + last.w === index + quiet && last.guard === guard) last.w += 1
    else bars.push({ x: index + quiet, w: 1, guard })
  }
  const barHeight = height
  const total = barHeight + textSpace
  return <svg className={className} viewBox={`0 0 ${width} ${total}`} preserveAspectRatio="none" role="img" aria-label={encoded.text} shapeRendering="crispEdges">
    <rect width={width} height={total} fill="#fff" />
    {bars.map((bar) => <rect key={bar.x} x={bar.x} y={0} width={bar.w} height={bar.guard && showText ? barHeight + textSpace * 0.55 : barHeight} fill="#000" />)}
    {showText && <text x={width / 2} y={total - 1} textAnchor="middle" fontSize={fontSize} fontFamily="'OCR-B', 'Courier New', monospace" fill="#000">{encoded.text}</text>}
  </svg>
}

/** Código QR en SVG. El contenido es el código del artículo, que es lo que buscan el TPV y el almacén. */
export function QrSvg({ value, className, label }: { value: string; className?: string; label?: string }) {
  const [svg, setSvg] = useState('')
  useEffect(() => {
    let active = true
    QRCode.toString(value || ' ', { type: 'svg', margin: 0, errorCorrectionLevel: 'M' })
      .then((markup) => { if (active) setSvg(markup) })
      .catch(() => { if (active) setSvg('') })
    return () => { active = false }
  }, [value])
  return <span className={className} role="img" aria-label={label ?? value} dangerouslySetInnerHTML={{ __html: svg }} />
}
