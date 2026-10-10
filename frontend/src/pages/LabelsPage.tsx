import { Barcode, Camera, Minus, Plus, Printer, QrCode, RotateCcw, Search, Tags, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { CameraScanner } from '../components/CameraScanner'
import { BarcodeSvg, QrSvg } from '../components/CodeGraphics'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field } from '../components/Form'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../components/Toast'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { useCompanyProfile, type CompanyProfile } from '../hooks/useCompanyProfile'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { isValidEan13 } from '../lib/barcode'
import { formatCurrency } from '../lib/format'
import { retailPrice } from '../lib/pos'
import { printHtml } from '../lib/print'
import { scanFeedback, useHardwareScanner } from '../lib/scanner'
import { useRouter } from '../routing/Router'
import type { PageResponse, Product } from '../types/api'

type LabelProduct = Product & { barcode?: string | null }
type CodeKind = 'barcode' | 'qr' | 'both'
type Layout = 'roll' | 'sheet'
type Align = 'left' | 'center'

export interface LabelDesign {
  size: string
  layout: Layout
  code: CodeKind
  showLogo: boolean
  showCompany: boolean
  showName: boolean
  showPrice: boolean
  showCode: boolean
  showCodeText: boolean
  border: boolean
  nameSize: number
  align: Align
}

interface LabelSize { id: string; width: number; height: number; label: [string, string]; columns?: number; rows?: number }

/** Tamaños de etiqueta habituales: rollos de impresoras térmicas (Zebra, Brother, Dymo) y hojas A4 adhesivas. */
export const LABEL_SIZES: LabelSize[] = [
  { id: '50x25', width: 50, height: 25, label: ['50 × 25 mm · rollo (Zebra, TSC)', '50 × 25 mm · roll (Zebra, TSC)'] },
  { id: '58x40', width: 58, height: 40, label: ['58 × 40 mm · rollo', '58 × 40 mm · roll'] },
  { id: '62x29', width: 62, height: 29, label: ['62 × 29 mm · Brother QL', '62 × 29 mm · Brother QL'] },
  { id: '54x25', width: 54, height: 25, label: ['54 × 25 mm · Dymo LabelWriter', '54 × 25 mm · Dymo LabelWriter'] },
  { id: '100x50', width: 100, height: 50, label: ['100 × 50 mm · cajas y palés', '100 × 50 mm · boxes and pallets'] },
  { id: '100x150', width: 100, height: 150, label: ['100 × 150 mm · palé / envío', '100 × 150 mm · pallet / shipping'] },
  { id: 'a4-65', width: 38.1, height: 21.2, columns: 5, rows: 13, label: ['A4 · 65 etiquetas 38,1 × 21,2 mm', 'A4 · 65 labels 38.1 × 21.2 mm'] },
  { id: 'a4-24', width: 70, height: 37, columns: 3, rows: 8, label: ['A4 · 24 etiquetas 70 × 37 mm', 'A4 · 24 labels 70 × 37 mm'] },
]

const DESIGN_KEY = 'pera.labels.design'
const defaultDesign: LabelDesign = { size: '50x25', layout: 'roll', code: 'barcode', showLogo: false, showCompany: false, showName: true, showPrice: true, showCode: true, showCodeText: true, border: false, nameSize: 9, align: 'center' }

function readDesign(): LabelDesign {
  try { return { ...defaultDesign, ...(JSON.parse(localStorage.getItem(DESIGN_KEY) ?? '{}') as Partial<LabelDesign>) } } catch { return defaultDesign }
}

/** Código que lleva la etiqueta: el de barras del artículo o, si no tiene, su código interno. */
export function labelCode(product: Pick<LabelProduct, 'code' | 'barcode'>) {
  return product.barcode?.trim() || product.code
}

const LABEL_CSS = (size: LabelSize, layout: Layout) => `
@page { size: ${layout === 'sheet' ? '210mm 297mm' : `${size.width}mm ${size.height}mm`}; margin: ${layout === 'sheet' ? '10mm 6mm' : '0'}; }
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: #fff; }
body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
${LABEL_STYLES}
.label-sheet.sheet { display: grid; grid-template-columns: repeat(${size.columns ?? 1}, ${size.width}mm); grid-auto-rows: ${size.height}mm; gap: 0 2mm; justify-content: center; }
.label-sheet.roll .label { page-break-after: always; break-after: page; }
`

/** Estilos de la etiqueta, iguales en la vista previa y al imprimir. */
const LABEL_STYLES = `
.label { position: relative; overflow: hidden; background: #fff; color: #000; display: flex; flex-direction: column; justify-content: center; gap: 0.6mm; padding: 1.4mm 2mm; font-family: Arial, Helvetica, sans-serif; }
.label.bordered { outline: 0.3mm solid #000; outline-offset: -0.6mm; }
.label.align-center { text-align: center; align-items: center; }
.label.align-left { text-align: left; align-items: stretch; }
.label .l-head { display: flex; align-items: center; gap: 1.2mm; width: 100%; justify-content: inherit; }
.label.align-center .l-head { justify-content: center; }
.label .l-logo { max-height: 5.5mm; max-width: 16mm; object-fit: contain; }
.label .l-company { font-size: 6pt; font-weight: 700; text-transform: uppercase; letter-spacing: .03em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.label .l-name { font-weight: 700; line-height: 1.12; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; width: 100%; }
.label .l-body { display: flex; align-items: center; gap: 1.5mm; width: 100%; min-height: 0; flex: 1 1 auto; justify-content: center; }
.label .l-codes { display: flex; align-items: center; gap: 1.5mm; flex: 1 1 auto; min-width: 0; height: 100%; justify-content: center; }
.label .l-barcode { width: 100%; height: 100%; max-height: 14mm; display: block; }
.label .l-qr { height: 100%; max-height: 16mm; aspect-ratio: 1; display: block; flex: 0 0 auto; }
.label .l-qr svg { width: 100%; height: 100%; display: block; }
.label .l-foot { display: flex; justify-content: space-between; align-items: baseline; width: 100%; gap: 2mm; line-height: 1; }
.label .l-head, .label .l-name, .label .l-foot { flex: 0 0 auto; }
.label .l-body { flex: 1 1 0; min-height: 4mm; }
.label .l-codes svg { min-height: 0; }
.label.small { gap: 0.4mm; padding: 1mm 1.6mm; }
.label.small .l-logo { max-height: 3.6mm; }
.label.small .l-company { font-size: 5pt; }
.label.small .l-price { font-size: 9pt; }
.label.small .l-code { font-size: 5.5pt; }
.label.align-center .l-foot { justify-content: center; }
.label .l-code { font-size: 6.5pt; font-family: 'Courier New', monospace; }
.label .l-price { font-size: 11pt; font-weight: 800; white-space: nowrap; }
`

export function LabelsPage() {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const { notify } = useToast()
  const { search } = useRouter()
  const company = useCompanyProfile()
  const [design, setDesign] = useState<LabelDesign>(readDesign)
  const [query, setQuery] = useState('')
  const debounced = useDebouncedValue(query.trim())
  const [results, setResults] = useState<LabelProduct[] | null>(null)
  const [selection, setSelection] = useState<Array<{ product: LabelProduct; copies: number }>>([])
  const [camera, setCamera] = useState(false)
  const preview = useRef<HTMLDivElement>(null)
  const size = LABEL_SIZES.find((item) => item.id === design.size) ?? LABEL_SIZES[0]

  useEffect(() => { try { localStorage.setItem(DESIGN_KEY, JSON.stringify(design)) } catch { /* diseño solo en memoria */ } }, [design])
  useEffect(() => {
    let active = true
    setResults(null)
    apiFetch<PageResponse<LabelProduct>>(`/api/v1/products?size=30&sort=name,asc${debounced ? `&query=${encodeURIComponent(debounced)}` : ''}`)
      .then((page) => { if (active) setResults(page.content.filter((item) => item.active)) })
      .catch((cause) => { if (active) { setResults([]); notify(errorMessage(cause), 'error') } })
    return () => { active = false }
  }, [debounced, notify])

  // /etiquetas?producto=<id> llega desde el catálogo con el artículo ya elegido.
  useEffect(() => {
    const id = new URLSearchParams(search).get('producto')
    if (!id) return
    apiFetch<LabelProduct>(`/api/v1/products/${encodeURIComponent(id)}`).then((product) => add(product)).catch(() => undefined)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search])

  const add = (product: LabelProduct, copies = 1) => setSelection((current) => {
    const existing = current.find((item) => item.product.id === product.id)
    return existing ? current.map((item) => item === existing ? { ...item, copies: item.copies + copies } : item) : [...current, { product, copies }]
  })
  const lookup = async (code: string) => {
    try { add(await apiFetch<LabelProduct>(`/api/v1/products/lookup?code=${encodeURIComponent(code.trim())}`)); scanFeedback(true) }
    catch (cause) { scanFeedback(false); notify(errorMessage(cause), 'error') }
  }
  useHardwareScanner((code) => void lookup(code), { enabled: !camera })

  const labels = useMemo(() => selection.flatMap((item) => Array.from({ length: Math.max(0, Math.min(500, item.copies)) }, () => item.product)), [selection])
  const set = <K extends keyof LabelDesign>(key: K, value: LabelDesign[K]) => setDesign((current) => ({ ...current, [key]: value }))
  const print = () => {
    if (!preview.current || labels.length === 0) return
    printHtml(preview.current.innerHTML, LABEL_CSS(size, design.layout), c('Etiquetas', 'Labels'))
  }
  const sheetCapacity = (size.columns ?? 1) * (size.rows ?? 1)

  return <div className="page-stack labels-page">
    <PageHeader eyebrow={c('Almacén y catálogo', 'Inventory and catalogue')} title={c('Etiquetas', 'Labels')} description={c('Diseña e imprime etiquetas con código de barras o QR para tus artículos, en impresora de etiquetas o en hojas A4.', 'Design and print barcode or QR labels for your products, on a label printer or A4 sheets.')} icon={Tags} actions={<button className="button button-primary" type="button" onClick={print} disabled={labels.length === 0}><Printer size={17} />{c(`Imprimir ${labels.length} etiquetas`, `Print ${labels.length} labels`)}</button>} />

    <div className="labels-layout">
      <section className="panel labels-products" aria-labelledby="labels-products-title">
        <div className="panel-heading"><h2 id="labels-products-title">{c('1. Artículos', '1. Products')}</h2><span className="pos-muted">{c('Busca, escanea o toca para añadir.', 'Search, scan or tap to add.')}</span></div>
        <div className="labels-search">
          <div className="input-prefix"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && query.trim()) { event.preventDefault(); void lookup(query) } }} placeholder={c('Nombre, código o código de barras', 'Name, code or barcode')} aria-label={c('Buscar artículo', 'Search product')} /></div>
          <button type="button" className="button button-ghost" onClick={() => setCamera(true)} aria-label={c('Escanear con la cámara', 'Scan with camera')}><Camera size={17} /></button>
        </div>
        {results === null ? <LoadingState /> : results.length === 0 ? <p className="pos-muted">{c('No hay artículos que coincidan.', 'No matching products.')}</p> :
          <ul className="labels-results">{results.map((product) => <li key={product.id}><button type="button" onClick={() => add(product)}><span><strong>{product.name}</strong><small>{product.code}{product.barcode ? ` · ${product.barcode}` : ''}</small></span><Plus size={16} /></button></li>)}</ul>}
        <h3 className="pos-subtitle">{c('Para imprimir', 'To print')}</h3>
        {selection.length === 0 ? <p className="pos-muted">{c('Todavía no has elegido artículos.', 'No products chosen yet.')}</p> :
          <ul className="labels-selection">{selection.map((item) => <li key={item.product.id}>
            <span><strong>{item.product.name}</strong><small>{labelCode(item.product)}</small></span>
            <div className="pos-stepper">
              <button type="button" aria-label={c('Una menos', 'One less')} onClick={() => setSelection((current) => current.map((entry) => entry === item ? { ...entry, copies: Math.max(1, entry.copies - 1) } : entry))}><Minus size={14} /></button>
              <input aria-label={c(`Copias de ${item.product.name}`, `Copies of ${item.product.name}`)} inputMode="numeric" value={item.copies} onChange={(event) => setSelection((current) => current.map((entry) => entry === item ? { ...entry, copies: Math.max(1, Math.min(500, Math.floor(Number(event.target.value) || 1))) } : entry))} />
              <button type="button" aria-label={c('Una más', 'One more')} onClick={() => setSelection((current) => current.map((entry) => entry === item ? { ...entry, copies: Math.min(500, entry.copies + 1) } : entry))}><Plus size={14} /></button>
            </div>
            <button type="button" className="icon-button" onClick={() => setSelection((current) => current.filter((entry) => entry !== item))} aria-label={c('Quitar', 'Remove')}><Trash2 size={15} /></button>
          </li>)}</ul>}
      </section>

      <section className="panel labels-design" aria-labelledby="labels-design-title">
        <div className="panel-heading"><h2 id="labels-design-title">{c('2. Diseño', '2. Design')}</h2><button type="button" className="button button-ghost button-small" onClick={() => setDesign(defaultDesign)}><RotateCcw size={14} />{c('Restablecer', 'Reset')}</button></div>
        <div className="form-grid">
          <Field label={c('Tamaño de etiqueta', 'Label size')} htmlFor="label-size" wide><select id="label-size" value={design.size} onChange={(event) => { const next = LABEL_SIZES.find((item) => item.id === event.target.value)!; setDesign((current) => ({ ...current, size: next.id, layout: next.columns ? 'sheet' : 'roll' })) }}>{LABEL_SIZES.map((item) => <option key={item.id} value={item.id}>{item.label[language === 'es' ? 0 : 1]}</option>)}</select></Field>
          <fieldset className="labels-code-kind field-wide"><legend>{c('Código', 'Code')}</legend>
            {([['barcode', Barcode, c('Código de barras', 'Barcode')], ['qr', QrCode, 'QR'], ['both', Tags, c('Los dos', 'Both')]] as const).map(([value, Icon, text]) => <button key={value} type="button" className={design.code === value ? 'active' : ''} aria-pressed={design.code === value} onClick={() => set('code', value)}><Icon size={18} />{text}</button>)}
          </fieldset>
          <Field label={c('Alineación', 'Alignment')} htmlFor="label-align"><select id="label-align" value={design.align} onChange={(event) => set('align', event.target.value as Align)}><option value="center">{c('Centrada', 'Centred')}</option><option value="left">{c('A la izquierda', 'Left')}</option></select></Field>
          <Field label={c('Tamaño del nombre', 'Name size')} htmlFor="label-name-size"><input id="label-name-size" type="range" min={6} max={20} value={design.nameSize} onChange={(event) => set('nameSize', Number(event.target.value))} /></Field>
        </div>
        <div className="labels-toggles">
          {([['showLogo', c('Logo de la empresa', 'Company logo')], ['showCompany', c('Nombre de la empresa', 'Company name')], ['showName', c('Nombre del artículo', 'Product name')], ['showPrice', c('Precio (PVP con IVA)', 'Price (incl. VAT)')], ['showCode', c('Referencia del artículo', 'Product reference')], ['showCodeText', c('Número bajo las barras', 'Number under the bars')], ['border', c('Borde', 'Border')]] as const).map(([key, text]) =>
            <label key={key} className="switch-row"><input type="checkbox" checked={design[key]} onChange={(event) => set(key, event.target.checked)} disabled={key === 'showLogo' && !company.logo} /><span>{text}{key === 'showLogo' && !company.logo ? ` · ${c('sube el logo en Configuración', 'upload it in Settings')}` : ''}</span></label>)}
        </div>
        <p className="pos-muted labels-hint">{design.layout === 'roll' ? c('Elige tu impresora de etiquetas en el cuadro de impresión, con márgenes «Ninguno» y escala 100 %. Cada etiqueta sale en su propia página.', 'Choose your label printer in the print dialog, with margins “None” and 100 % scale. Each label prints on its own page.') : c(`Hoja A4 de ${sheetCapacity} etiquetas. Imprime al 100 %, sin ajustar a la página.`, `A4 sheet of ${sheetCapacity} labels. Print at 100 %, without fit to page.`)}</p>
      </section>
    </div>

    <section className="panel labels-preview-panel" aria-labelledby="labels-preview-title">
      <div className="panel-heading"><h2 id="labels-preview-title">{c('3. Vista previa', '3. Preview')}</h2><span className="pos-muted">{labels.length} {c('etiquetas', 'labels')}{design.layout === 'sheet' && labels.length > 0 ? ` · ${Math.ceil(labels.length / sheetCapacity)} ${c('hojas', 'sheets')}` : ''}</span></div>
      {labels.length === 0 ? <EmptyState title={c('Elige artículos para ver sus etiquetas', 'Choose products to see their labels')} description={c('Puedes escanear el código con la pistola o la cámara para añadirlos.', 'You can scan the code with a scanner or the camera to add them.')} /> :
        <div className="labels-preview">
          <style>{LABEL_STYLES}</style>
          <div ref={preview}><div className={`label-sheet ${design.layout}`} style={design.layout === 'sheet' ? { display: 'grid', gridTemplateColumns: `repeat(${size.columns}, ${size.width}mm)`, gridAutoRows: `${size.height}mm`, gap: '0 2mm' } : undefined}>
            {labels.slice(0, design.layout === 'sheet' ? 260 : 120).map((product, index) => <LabelView key={`${product.id}-${index}`} product={product} design={design} size={size} company={company} locale={locale} />)}
          </div></div>
          {labels.length > (design.layout === 'sheet' ? 260 : 120) && <p className="pos-muted">{c('La vista previa muestra las primeras; se imprimen todas.', 'The preview shows the first ones; all of them print.')}</p>}
        </div>}
    </section>
    <CameraScanner open={camera} onClose={() => setCamera(false)} onDetected={(code) => { setCamera(false); void lookup(code) }} title={c('Escanear artículo', 'Scan product')} />
  </div>
}

export function LabelView({ product, design, size, company, locale }: { product: LabelProduct; design: LabelDesign; size: LabelSize; company: CompanyProfile; locale: string }) {
  const code = labelCode(product)
  const small = size.height < 30
  const showHead = (design.showLogo && company.logo) || design.showCompany
  const qrOnly = design.code === 'qr'
  return <div className={`label align-${design.align} ${design.border ? 'bordered' : ''} ${small ? 'small' : ''}`} style={{ width: `${size.width}mm`, height: `${size.height}mm` }}>
    {showHead && <div className="l-head">{design.showLogo && company.logo && <img className="l-logo" src={company.logo} alt="" />}{design.showCompany && <span className="l-company">{company.displayName}</span>}</div>}
    {design.showName && <div className="l-name" style={{ fontSize: `${design.nameSize}pt` }}>{product.name}</div>}
    <div className="l-body">
      <div className="l-codes">
        {design.code !== 'qr' && <BarcodeSvg className="l-barcode" value={code} symbology={isValidEan13(code) ? 'ean13' : 'code128'} height={small ? 30 : 42} showText={design.showCodeText} fontSize={small ? 9 : 10} />}
        {design.code !== 'barcode' && <QrSvg className="l-qr" value={code} />}
        {qrOnly && (design.showPrice || design.showCode) && <div className="l-qr-side">{design.showPrice && <div className="l-price">{formatCurrency(retailPrice(product.basePrice, product.taxRate), company.baseCurrency, locale)}</div>}{design.showCode && <div className="l-code">{product.code}</div>}</div>}
      </div>
    </div>
    {!qrOnly && (design.showCode || design.showPrice) && <div className="l-foot">{design.showCode && <span className="l-code">{product.code}</span>}{design.showPrice && <span className="l-price">{formatCurrency(retailPrice(product.basePrice, product.taxRate), company.baseCurrency, locale)}</span>}</div>}
  </div>
}
