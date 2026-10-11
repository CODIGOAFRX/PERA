import { MapPin } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import type { StockLevel } from '../types/api'
import { Field, FormActions } from './Form'

/** Datos del artículo y del almacén necesarios para guardar dónde está. */
export interface LocationTarget {
  warehouseId: string
  productId: string
  productCode: string
  productName: string
  unitOfMeasure: string
}

/**
 * Guarda la ubicación de un artículo en un almacén. Si aún no tiene existencias allí se crea su
 * ficha con cero unidades, para poder decir dónde irá antes de que llegue.
 */
export function saveStockLocation(target: LocationTarget, location: string) {
  return apiFetch<StockLevel>('/api/v1/stock-levels/location', {
    method: 'PUT',
    body: JSON.stringify({ ...target, location: location.trim() || null }),
  })
}

/** Ejemplos para quien no sabe qué escribir: cada empresa organiza su almacén a su manera. */
export const LOCATION_EXAMPLES = ['Pasillo 3 · Estante B', 'Cámara frigorífica 2', 'Zona de palés · Calle 4']

export function StockLocationForm({ level, suggestions, onCancel, onSaved }: {
  level: StockLevel
  suggestions: string[]
  onCancel: () => void
  onSaved: (level: StockLevel) => void
}) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const listId = useId()
  const [value, setValue] = useState(level.location ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const options = [...new Set([...suggestions, ...LOCATION_EXAMPLES])].sort((left, right) => left.localeCompare(right, 'es'))
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true); setError('')
    try {
      onSaved(await saveStockLocation({ warehouseId: level.warehouseId, productId: level.productId, productCode: level.productCode, productName: level.productName, unitOfMeasure: level.unitOfMeasure }, value))
    } catch (cause) { setError(errorMessage(cause)) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <div className="form-grid">
      <Field label={c('Ubicación', 'Location')} htmlFor="stock-location" wide hint={c('Pasillo, estantería, zona o cámara: lo que use tu almacén. Déjalo vacío para quitarla.', 'Aisle, shelf, zone or cold room: whatever your warehouse uses. Leave it empty to remove it.')}>
        <div className="input-prefix"><MapPin size={16} /><input id="stock-location" data-autofocus value={value} maxLength={120} list={listId} onChange={(event) => setValue(event.target.value)} placeholder={LOCATION_EXAMPLES[0]} autoComplete="off" /></div>
      </Field>
      <datalist id={listId}>{options.map((option) => <option key={option} value={option} />)}</datalist>
    </div>
    {error && <div className="form-error" role="alert">{error}</div>}
    <FormActions onCancel={onCancel} saving={saving} submitLabel={c('Guardar ubicación', 'Save location')} />
  </form>
}
