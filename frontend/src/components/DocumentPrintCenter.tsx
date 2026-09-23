import { Download, ExternalLink, Search } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { apiDownload, apiFetch, errorMessage } from '../lib/api'
import { useTranslation } from '../i18n/I18nProvider'
import { formatCurrency, formatDate } from '../lib/format'
import { Field } from './Form'
import { EmptyState, LoadingState } from './DataState'
import type { CommercialDocument, PageResponse } from '../types/api'
import { TableCaption } from './TableCaption'

/** The server PDF is the printable document; the application page is never printed here. */
export function DocumentPrintCenter() {
  const { language, locale } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [type, setType] = useState('INVOICE')
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<CommercialDocument> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [pdf, setPdf] = useState<{ url: string; filename: string; number: string } | null>(null)
  const request = useRef(0)
  useEffect(() => () => { request.current++ }, [])
  useEffect(() => () => { if (pdf) URL.revokeObjectURL(pdf.url) }, [pdf])
  useEffect(() => {
    let active = true
    setLoading(true); setError(''); setData(null)
    const params = new URLSearchParams({ type, q: search, page: String(page), size: '20' })
    apiFetch<PageResponse<CommercialDocument>>(`/api/v1/documents?${params}`)
      .then(value => { if (active) setData(value) })
      .catch(e => { if (active) setError(errorMessage(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [type, search, page])
  async function prepare(document: CommercialDocument) {
    const current = ++request.current
    setPending(document.id); setError(''); setPdf(null)
    try {
      const result = await apiDownload(`/api/v1/documents/${document.id}/invoice.pdf`)
      if (current !== request.current) return
      setPdf({ url: URL.createObjectURL(result.blob), filename: result.filename, number: document.number })
    } catch (e) { if (current === request.current) setError(errorMessage(e)) }
    finally { if (current === request.current) setPending(null) }
  }
  function filter(event: FormEvent) { event.preventDefault(); setSearch(query.trim()); setPage(0) }
  return <section className="panel document-print-center">
    <h2>{c('Documentos completos para el cliente', 'Complete customer documents')}</h2>
    <p>{c('Selecciona una factura o presupuesto para obtener su PDF con emisor, cliente, líneas, impuestos y total. Incluye el QR de Veri*Factu cuando corresponde.', 'Select an invoice or quote to get its PDF with issuer, customer, lines, taxes and total. Includes the Veri*Factu QR when applicable.')}</p>
    <form className="document-print-filters" onSubmit={filter}>
      <Field label={c('Documento', 'Document')} htmlFor="print-document-type"><select id="print-document-type" value={type} onChange={e => { setType(e.target.value); setPage(0) }}><option value="INVOICE">{c('Facturas', 'Invoices')}</option><option value="RECTIFYING_INVOICE">{c('Facturas rectificativas', 'Credit invoices')}</option><option value="QUOTE">{c('Presupuestos', 'Quotes')}</option></select></Field>
      <Field label={c('Número o cliente', 'Number or customer')} htmlFor="print-document-query"><input id="print-document-query" value={query} onChange={e => setQuery(e.target.value)} placeholder="FAC-2026-000003" /></Field>
      <button className="button button-secondary" type="submit"><Search size={16}/>{c('Buscar', 'Search')}</button>
    </form>
    {error && <div className="inline-error" role="alert">{error}</div>}
    {loading ? <LoadingState /> : !data?.content.length ? <EmptyState description={c('Prueba otro número, cliente o tipo de documento.', 'Try another number, customer or document type.')} title={c('No hay documentos con estos filtros', 'No documents match these filters')} /> : <>
      <div className="table-scroll"><table className="data-table"><TableCaption es="Documentos para imprimir" en="Documents to print" /><thead><tr><th>{c('Número', 'Number')}</th><th>{c('Cliente', 'Customer')}</th><th>{c('Fecha', 'Date')}</th><th>{c('Total', 'Total')}</th><th>{c('Documento completo', 'Complete document')}</th></tr></thead><tbody>{data.content.map(item => <tr key={item.id}>
        <td>{item.number}{item.status === 'DRAFT' && <small className="document-draft-label">{c('Borrador', 'Draft')}</small>}</td><td>{item.customerName}</td><td>{formatDate(item.issueDate, locale)}</td><td>{formatCurrency(item.totalAmount, item.currency, locale)}</td>
        <td><button className="button button-secondary button-small" disabled={pending !== null || (item.type !== 'QUOTE' && item.status === 'DRAFT')} onClick={() => void prepare(item)}>{item.type !== 'QUOTE' && item.status === 'DRAFT' ? c('Expide la factura primero', 'Issue the invoice first') : pending === item.id ? c('Preparando…', 'Preparing…') : c('Ver PDF completo', 'View full PDF')}</button></td>
      </tr>)}</tbody></table></div>
      <div className="document-print-actions"><button className="button button-ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>{c('Anterior', 'Previous')}</button><span>{page + 1} / {data.page.totalPages}</span><button className="button button-ghost" disabled={page + 1 >= data.page.totalPages} onClick={() => setPage(page + 1)}>{c('Siguiente', 'Next')}</button></div>
    </>}
    {pdf && <section className="document-pdf-preview" aria-label={c('Vista previa del documento completo', 'Complete document preview')}>
      <div className="document-print-actions"><h3>{pdf.number}</h3><a className="button button-primary" href={pdf.url} target="_blank" rel="noopener noreferrer"><ExternalLink size={16}/>{c('Abrir PDF para imprimir', 'Open PDF to print')}</a><a className="button button-secondary" href={pdf.url} download={pdf.filename}><Download size={16}/>{c('Descargar PDF', 'Download PDF')}</a></div>
      <p>{c('Imprime desde el visor del PDF. La descarga contiene el documento completo, sin menús ni controles de PERA.', 'Print from the PDF viewer. The download contains the complete document, without PERA menus or controls.')}</p>
      {navigator.pdfViewerEnabled ? <object data={pdf.url} type="application/pdf" aria-label={c('PDF del documento', 'Document PDF')}><p>{c('Tu navegador no muestra PDF incrustados. Usa Abrir PDF para imprimir o Descargar PDF.', 'Your browser cannot display embedded PDFs. Use Open PDF to print or Download PDF.')}</p></object> : <p>{c('Abre o descarga el PDF para verlo en tu lector de documentos.', 'Open or download the PDF to view it in your document reader.')}</p>}
    </section>}
  </section>
}
