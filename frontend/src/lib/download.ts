/**
 * Guarda un blob como fichero desde el navegador.
 *
 * El enlace se añade al documento porque algunos navegadores ignoran el clic en un enlace suelto, y
 * la URL se revoca con retraso: revocarla en el mismo instante puede cancelar la descarga.
 */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
}
