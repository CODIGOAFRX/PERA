/**
 * Lectura de códigos de barras y QR desde la cámara o una foto.
 *
 * Usa el lector del propio navegador (BarcodeDetector, presente en Chrome para Android) cuando
 * existe y, si no, la librería ZXing, que solo se descarga la primera vez que hace falta.
 */

type Decoder = (canvas: HTMLCanvasElement) => Promise<string | null>

const WANTED_FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'code_93', 'itf', 'codabar', 'qr_code', 'data_matrix']

interface NativeDetector { detect(source: CanvasImageSource): Promise<Array<{ rawValue: string }>> }
interface NativeDetectorClass {
  new (options?: { formats?: string[] }): NativeDetector
  getSupportedFormats?: () => Promise<string[]>
}

let decoderPromise: Promise<Decoder> | null = null

export function getDecoder(): Promise<Decoder> {
  decoderPromise ??= createDecoder()
  return decoderPromise
}

async function createDecoder(): Promise<Decoder> {
  const Native = (window as unknown as { BarcodeDetector?: NativeDetectorClass }).BarcodeDetector
  if (Native) {
    try {
      const supported = (await Native.getSupportedFormats?.()) ?? WANTED_FORMATS
      const formats = WANTED_FORMATS.filter((format) => supported.includes(format))
      if (formats.length > 0) {
        const detector = new Native({ formats })
        return async (canvas) => {
          const found = await detector.detect(canvas)
          return found.find((item) => item.rawValue)?.rawValue ?? null
        }
      }
    } catch {
      // Si el lector del navegador falla se usa ZXing.
    }
  }
  return zxingDecoder()
}

async function zxingDecoder(): Promise<Decoder> {
  const zxing = await import('@zxing/library')
  const hints = new Map()
  hints.set(zxing.DecodeHintType.POSSIBLE_FORMATS, [
    zxing.BarcodeFormat.EAN_13, zxing.BarcodeFormat.EAN_8, zxing.BarcodeFormat.UPC_A, zxing.BarcodeFormat.UPC_E,
    zxing.BarcodeFormat.CODE_128, zxing.BarcodeFormat.CODE_39, zxing.BarcodeFormat.CODE_93, zxing.BarcodeFormat.ITF,
    zxing.BarcodeFormat.QR_CODE, zxing.BarcodeFormat.DATA_MATRIX,
  ])
  hints.set(zxing.DecodeHintType.TRY_HARDER, true)
  const reader = new zxing.MultiFormatReader()
  reader.setHints(hints)
  return async (canvas) => {
    const context = canvas.getContext('2d', { willReadFrequently: true })
    if (!context || canvas.width === 0 || canvas.height === 0) return null
    const { data, width, height } = context.getImageData(0, 0, canvas.width, canvas.height)
    const luminance = new Uint8ClampedArray(width * height)
    for (let pixel = 0, offset = 0; pixel < luminance.length; pixel += 1, offset += 4) {
      luminance[pixel] = (data[offset] * 299 + data[offset + 1] * 587 + data[offset + 2] * 114) / 1000
    }
    try {
      const bitmap = new zxing.BinaryBitmap(new zxing.HybridBinarizer(new zxing.RGBLuminanceSource(luminance, width, height)))
      return reader.decodeWithState(bitmap).getText()
    } catch {
      return null
    } finally {
      reader.reset()
    }
  }
}

/** Dibuja una imagen (foto del móvil) reducida en un lienzo y la descodifica. */
export async function decodeImageFile(file: Blob): Promise<string | null> {
  const url = URL.createObjectURL(file)
  try {
    const image = await new Promise<HTMLImageElement>((resolve, reject) => {
      const element = new Image()
      element.onload = () => resolve(element)
      element.onerror = () => reject(new Error('No se pudo abrir la foto.'))
      element.src = url
    })
    const decoder = await getDecoder()
    // Las fotos del móvil son enormes: se prueba a varios tamaños, que es lo que más acierta.
    for (const maxSide of [1280, 1800, 900]) {
      const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(image.naturalWidth * scale)
      canvas.height = Math.round(image.naturalHeight * scale)
      canvas.getContext('2d')?.drawImage(image, 0, 0, canvas.width, canvas.height)
      const result = await decoder(canvas)
      if (result) return result
    }
    return null
  } finally {
    URL.revokeObjectURL(url)
  }
}

export function liveCameraAvailable() {
  return typeof window !== 'undefined' && window.isSecureContext && Boolean(navigator.mediaDevices?.getUserMedia)
}
