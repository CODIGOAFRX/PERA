import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { DocumentPrintCenter } from './DocumentPrintCenter'
import { I18nProvider } from '../i18n/I18nProvider'
const { api, download } = vi.hoisted(() => ({ api: vi.fn(), download: vi.fn() }))
vi.mock('../lib/api', () => ({ apiFetch: api, apiDownload: download, errorMessage: (e: Error) => e.message }))
const invoice = { id: 'invoice-1', number: 'FAC-2026-000003', type: 'INVOICE', status: 'CONFIRMED', customerName: 'Cliente', issueDate: '2026-09-22', totalAmount: 847, currency: 'EUR' }
beforeEach(() => {
  vi.clearAllMocks(); localStorage.clear(); Object.defineProperty(navigator, 'pdfViewerEnabled', {configurable:true, value:true})
  api.mockResolvedValue({ content: [invoice], page: {totalPages: 1} })
  download.mockResolvedValue({ blob: new Blob(['%PDF-example'], {type:'application/pdf'}), filename: 'Factura-FAC-2026-000003.pdf' })
  URL.createObjectURL = vi.fn(() => 'blob:invoice-pdf')
  URL.revokeObjectURL = vi.fn()
})
it('uses the backend PDF for preview, download and printing instead of printing the ERP', async () => {
  const print = vi.spyOn(window, 'print').mockImplementation(() => {})
  const view = render(<I18nProvider><DocumentPrintCenter /></I18nProvider>)
  fireEvent.click(await screen.findByRole('button', {name: 'Ver PDF completo'}))
  const link = await screen.findByRole('link', {name:'Abrir PDF para imprimir'})
  expect(download).toHaveBeenCalledWith('/api/v1/documents/invoice-1/invoice.pdf')
  expect(link).toHaveAttribute('href','blob:invoice-pdf')
  expect(screen.getByRole('link',{name:'Descargar PDF'})).toHaveAttribute('download','Factura-FAC-2026-000003.pdf')
  expect(screen.getByLabelText('PDF del documento')).toHaveAttribute('data','blob:invoice-pdf')
  expect(print).not.toHaveBeenCalled()
  view.unmount(); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:invoice-pdf')
  print.mockRestore()
})
it('searches by the actual API q parameter and supports credit invoices', async () => {
  render(<I18nProvider><DocumentPrintCenter /></I18nProvider>)
  await screen.findByText(invoice.number)
  fireEvent.change(screen.getByLabelText('Número o cliente'), {target: {value: invoice.number}})
  fireEvent.click(screen.getByRole('button',{name:'Buscar'}))
  await waitFor(() => expect(api).toHaveBeenLastCalledWith(expect.stringContaining('q=FAC-2026-000003')))
  fireEvent.change(screen.getByLabelText('Documento'), {target: {value:'RECTIFYING_INVOICE'}})
  await waitFor(() => expect(api).toHaveBeenLastCalledWith(expect.stringContaining('type=RECTIFYING_INVOICE')))
})
it('shows download failures and prevents printing invoice drafts', async () => {
  api.mockResolvedValue({content:[invoice,{...invoice,id:'draft',number:'DRAFT',status:'DRAFT'}],page:{totalPages:1}})
  download.mockRejectedValue(new Error('No se pudo generar el PDF'))
  render(<I18nProvider><DocumentPrintCenter /></I18nProvider>)
  expect(await screen.findByRole('button',{name:'Expide la factura primero'})).toBeDisabled()
  fireEvent.click(screen.getByRole('button',{name:'Ver PDF completo'}))
  expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo generar el PDF')
  expect(screen.queryByRole('link',{name:'Descargar PDF'})).not.toBeInTheDocument()
})
