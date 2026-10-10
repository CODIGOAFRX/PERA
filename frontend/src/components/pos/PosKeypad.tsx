import { Delete } from 'lucide-react'

/**
 * Teclado numérico grande para pantallas táctiles. Escribe sobre un texto (no un número) para
 * que «0,» o «12,5» se puedan teclear sin que se pierda la coma.
 */
export function PosKeypad({ value, onChange, onEnter, decimals = 2, enterLabel, language }: {
  value: string
  onChange: (value: string) => void
  onEnter?: () => void
  decimals?: number
  enterLabel?: string
  language: 'es' | 'en'
}) {
  const press = (key: string) => {
    if (key === 'C') { onChange(''); return }
    if (key === '⌫') { onChange(value.slice(0, -1)); return }
    if (key === ',') {
      if (decimals === 0 || value.includes(',')) return
      onChange(value ? `${value},` : '0,')
      return
    }
    const [, fraction] = value.split(',')
    if (fraction !== undefined && fraction.length >= decimals) return
    onChange(value === '0' ? key : `${value}${key}`)
  }
  const keys = ['7', '8', '9', '4', '5', '6', '1', '2', '3', ',', '0', '⌫']
  return <div className="pos-keypad" role="group" aria-label={language === 'es' ? 'Teclado numérico' : 'Numeric keypad'}>
    {keys.map((key) => <button key={key} type="button" onClick={() => press(key)} disabled={key === ',' && decimals === 0} aria-label={key === '⌫' ? (language === 'es' ? 'Borrar' : 'Backspace') : key}>{key === '⌫' ? <Delete size={20} /> : key}</button>)}
    <button type="button" className="pos-keypad-clear" onClick={() => press('C')}>C</button>
    {onEnter && <button type="button" className="pos-keypad-enter" onClick={onEnter}>{enterLabel ?? 'OK'}</button>}
  </div>
}

/** «12,5» → 12.5; texto vacío o no válido → null. */
export function parseKeypad(value: string): number | null {
  const text = value.trim()
  // Con coma, los puntos son de miles («1.250,5»); sin coma, el punto es el decimal del teclado físico.
  const normalized = text.includes(',') ? text.replace(/\./g, '').replace(',', '.') : text
  if (!normalized) return null
  const number = Number(normalized)
  return Number.isFinite(number) ? number : null
}

/** 12.5 → «12,5» para editar con el teclado. */
export function toKeypad(value: number | null | undefined, decimals = 2): string {
  if (value == null || !Number.isFinite(value)) return ''
  return String(Number(value.toFixed(decimals))).replace('.', ',')
}
