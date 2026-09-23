const pad = (value: number) => String(value).padStart(2, '0')

/**
 * Fecha del calendario local en formato AAAA-MM-DD, la que esperan los `<input type="date">`.
 *
 * `toISOString()` devuelve la fecha en UTC: en España, entre medianoche y la 01:00/02:00, daría el
 * día anterior y una factura emitida a esas horas saldría con fecha de ayer.
 */
export function localIsoDate(date: Date = new Date(), offsetDays = 0) {
  const shifted = new Date(date.getFullYear(), date.getMonth(), date.getDate() + offsetDays)
  return `${shifted.getFullYear()}-${pad(shifted.getMonth() + 1)}-${pad(shifted.getDate())}`
}

/** Fecha y hora local en formato AAAA-MM-DDTHH:mm, la que esperan los `<input type="datetime-local">`. */
export function localIsoDateTime(date: Date = new Date()) {
  return `${localIsoDate(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
