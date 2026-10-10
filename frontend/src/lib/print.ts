/**
 * Impresión aislada de tickets y etiquetas.
 *
 * Se copia el contenido a un iframe con su propio tamaño de página (@page) y sus estilos, y se
 * imprime desde ahí. Así la página de la aplicación no se ve afectada y el tamaño del papel
 * (rollo de 80 mm, etiqueta de 50 × 25 mm, A4…) es exactamente el elegido. Con una impresora de
 * tickets o de etiquetas instalada en el equipo, basta con elegirla en el cuadro de impresión.
 */
export function printHtml(html: string, css: string, title = 'PERA ERP') {
  const frame = document.createElement('iframe')
  frame.setAttribute('aria-hidden', 'true')
  frame.style.position = 'fixed'
  frame.style.right = '0'
  frame.style.bottom = '0'
  frame.style.width = '0'
  frame.style.height = '0'
  frame.style.border = '0'
  document.body.appendChild(frame)
  const doc = frame.contentDocument
  const win = frame.contentWindow
  if (!doc || !win) { frame.remove(); return }
  doc.open()
  doc.write(`<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title><style>${css}</style></head><body>${html}</body></html>`)
  doc.close()
  const cleanup = () => window.setTimeout(() => frame.remove(), 500)
  const run = () => {
    try {
      win.focus()
      win.addEventListener('afterprint', cleanup, { once: true })
      win.print()
    } finally {
      // Algunos navegadores no avisan de afterprint: se retira igualmente pasado un minuto.
      window.setTimeout(() => frame.isConnected && frame.remove(), 60_000)
    }
  }
  const images = Array.from(doc.images)
  if (images.length === 0) { window.setTimeout(run, 50); return }
  let pending = images.length
  const done = () => { pending -= 1; if (pending <= 0) run() }
  images.forEach((image) => { if (image.complete) done(); else { image.onload = done; image.onerror = done } })
}

export function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char] ?? char))
}

/** Logo de la empresa como data URL, para que el iframe de impresión lo tenga sin pedirlo otra vez. */
export function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}
