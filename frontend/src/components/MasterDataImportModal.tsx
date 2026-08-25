import { Download, FileSpreadsheet, Upload } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { apiDownload, apiFetch, errorMessage } from '../lib/api'
import { useTranslation } from '../i18n/I18nProvider'
import { Modal } from './Modal'

interface ImportRowError {
  row: number
  message: string
}

interface ImportResult {
  imported: number
  blankRows: number
  failed: number
  errors: ImportRowError[]
}

interface MasterDataImportModalProps {
  open: boolean
  entityName: string
  basePath: '/api/v1/customers' | '/api/v1/suppliers' | '/api/v1/products'
  onClose: () => void
  onImported: (count: number) => void
}

export function MasterDataImportModal({ open, entityName, basePath, onClose, onImported }: MasterDataImportModalProps) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const inputId = useId()
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState('')
  const [working, setWorking] = useState(false)

  useEffect(() => {
    if (!open) return
    setFile(null)
    setResult(null)
    setError('')
  }, [open])

  const downloadTemplate = async () => {
    setWorking(true)
    setError('')
    try {
      const { blob, filename } = await apiDownload(`${basePath}/import-template`)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setWorking(false)
    }
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!file) return
    setWorking(true)
    setError('')
    setResult(null)
    const body = new FormData()
    body.append('file', file)
    try {
      const response = await apiFetch<ImportResult>(`${basePath}/import`, { method: 'POST', body })
      setResult(response)
      if (response.imported > 0) onImported(response.imported)
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setWorking(false)
    }
  }

  return <Modal
    open={open}
    title={c(`Importar ${entityName}`, `Import ${entityName}`)}
    description={c('Carga muchos registros de una vez sin perder el control de cada fila.', 'Upload many records at once while keeping row-level feedback.')}
    onClose={onClose}
    size="large"
  >
    <form onSubmit={submit} className="import-workflow">
      <section className="import-step">
        <span className="import-step-number">1</span>
        <div>
          <h3>{c('Descarga la plantilla base', 'Download the base template')}</h3>
          <p>{c('Puedes dejar en blanco cualquier dato opcional. Código y nombre son los únicos campos imprescindibles para crear cada registro.', 'You may leave any optional field blank. Code and name are the only fields required to create each record.')}</p>
          <button className="button button-secondary" type="button" onClick={downloadTemplate} disabled={working}>
            <Download size={17} />{c('Descargar Excel base', 'Download Excel template')}
          </button>
        </div>
      </section>

      <section className="import-step">
        <span className="import-step-number">2</span>
        <div className="import-upload-wrap">
          <h3>{c('Selecciona el archivo completado', 'Select the completed file')}</h3>
          <p>{c('Se admiten Excel y CSV de hasta 5 MiB. Las filas totalmente vacías se ignoran sin producir errores.', 'Excel and CSV files up to 5 MiB are accepted. Completely empty rows are ignored without errors.')}</p>
          <label className={`import-file-picker${file ? ' has-file' : ''}`} htmlFor={inputId}>
            <FileSpreadsheet size={24} />
            <span><strong>{file?.name ?? c('Elegir Excel o CSV', 'Choose Excel or CSV')}</strong>{file && <small>{formatBytes(file.size)}</small>}</span>
            <input id={inputId} type="file" accept=".xlsx,.xls,.csv" onChange={(event) => { setFile(event.target.files?.[0] ?? null); setResult(null); setError('') }} />
          </label>
        </div>
      </section>

      {error && <div className="form-error" role="alert">{error}</div>}
      {result && <section className={`import-result${result.failed > 0 ? ' has-errors' : ''}`} aria-live="polite">
        <strong>{result.imported > 0
          ? c(`${result.imported} registros importados`, `${result.imported} records imported`)
          : c('No había registros para importar', 'There were no records to import')}</strong>
        <p>{c(`${result.blankRows} filas vacías ignoradas · ${result.failed} filas pendientes de corregir.`, `${result.blankRows} empty rows ignored · ${result.failed} rows need correction.`)}</p>
        {result.errors.length > 0 && <div className="import-error-list">
          {result.errors.map((item) => <div key={`${item.row}-${item.message}`}><span>{c(`Fila ${item.row}`, `Row ${item.row}`)}</span><p>{item.message}</p></div>)}
        </div>}
      </section>}

      <footer className="form-actions">
        <button className="button button-ghost" type="button" onClick={onClose}>{c('Cerrar', 'Close')}</button>
        <button className="button button-primary" type="submit" disabled={!file || working}>
          <Upload size={17} />{working ? c('Importando…', 'Importing…') : c('Importar archivo', 'Import file')}
        </button>
      </footer>
    </form>
  </Modal>
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
