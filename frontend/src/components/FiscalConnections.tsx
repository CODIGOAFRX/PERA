import { AlertTriangle, CheckCircle2, FileKey, Network } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import { formatDate } from '../lib/format'
import { Field } from './Form'
import { StatusBadge } from './StatusBadge'

type Provider = 'AEAT' | 'B2B'
type CertificateInfo = { holder: string; personalTaxId: string | null; entityTaxId: string | null; issuer: string; validUntil: string; expired: boolean }
type View = { provider: string; configured: boolean; encryptionReady: boolean; account: string; enabled: boolean; environment: string; certificate?: CertificateInfo | null; issuerMatches?: boolean | null }

/** Días de antelación con los que se avisa de la caducidad del certificado. */
const EXPIRY_WARNING_DAYS = 30

function daysUntil(isoDate: string) {
  return Math.floor((new Date(`${isoDate}T23:59:59`).getTime() - Date.now()) / 86_400_000)
}

/** El servidor rechaza certificados más grandes; se avisa antes de leerlos para no subirlos en vano. */
const MAX_CERTIFICATE_BYTES = 2_000_000

export function FiscalConnections() {
  return <><FiscalConnection provider="AEAT" /><FiscalConnection provider="B2B" /></>
}

/** Lee el P12/PFX como Base64 sin el prefijo `data:`; así viaja en el JSON de la configuración. */
function readAsBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).split(',')[1] ?? '')
    reader.onerror = () => reject(reader.error ?? new Error('read failed'))
    reader.readAsDataURL(file)
  })
}

/**
 * Credenciales de pruebas de una empresa para la AEAT (preproducción) o el sandbox de B2Brouter.
 *
 * Los secretos solo viajan al guardar y el servidor nunca los devuelve. «Comprobar» exige que lo
 * visible esté guardado, porque verifica la configuración almacenada y no la del formulario.
 */
function FiscalConnection({ provider }: { provider: Provider }) {
  const { language } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const id = useId()
  const fileInput = useRef<HTMLInputElement>(null)
  const [view, setView] = useState<View | null>(null)
  const [password, setPassword] = useState('')
  const [certificate, setCertificate] = useState('')
  const [reading, setReading] = useState(false)
  const [apiKey, setApiKey] = useState('')
  const [busy, setBusy] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let active = true
    apiFetch<View>(`/api/v1/connections/fiscal/${provider}`)
      .then((value) => { if (active) setView(value) })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
    return () => { active = false }
  }, [provider])

  function edit(next: Partial<View>) {
    if (!view) return
    setView({ ...view, ...next }); setDirty(true); setNotice('')
  }

  async function selectCertificate(file?: File) {
    setCertificate(''); setDirty(true); setNotice(''); setError('')
    if (!file) return
    if (file.size > MAX_CERTIFICATE_BYTES) {
      setError(c('El certificado no puede superar 2 MB.', 'The certificate cannot exceed 2 MB.'))
      if (fileInput.current) fileInput.current.value = ''
      return
    }
    setReading(true)
    try { setCertificate(await readAsBase64(file)) }
    catch { setError(c('No se pudo leer el certificado.', 'The certificate could not be read.')) }
    finally { setReading(false) }
  }

  async function save() {
    if (!view) return
    if (certificate && !password) {
      setError(c('Introduce la contraseña del certificado seleccionado.', 'Enter the password of the selected certificate.'))
      return
    }
    setBusy(true); setError(''); setNotice('')
    try {
      setView(await apiFetch<View>('/api/v1/connections/fiscal', { method: 'PUT', body: JSON.stringify({ provider, account: provider === 'AEAT' ? view.account : view.account.trim(), enabled: view.enabled, certificate: certificate || null, password, apiKey: apiKey || null }) }))
      setCertificate(''); setPassword(''); setApiKey(''); setDirty(false)
      if (fileInput.current) fileInput.current.value = ''
      setNotice(c('Configuración de pruebas guardada.', 'Test configuration saved.'))
    } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }

  async function check() {
    setBusy(true); setError(''); setNotice('')
    try {
      const result = await apiFetch<{ message: string }>(`/api/v1/connections/fiscal/${provider}/test`, { method: 'POST' })
      setNotice(result.message)
    } catch (cause) { setError(errorMessage(cause)) } finally { setBusy(false) }
  }

  const aeat = provider === 'AEAT'
  const Icon = aeat ? FileKey : Network
  return <form className="panel connection-panel fiscal-connection" aria-busy={busy || reading} onSubmit={(event: FormEvent) => { event.preventDefault(); void save() }}>
    <div className="connection-heading">
      <Icon size={23} aria-hidden="true" />
      <div>
        <h2>{aeat ? c('VeriFactu · AEAT de pruebas', 'VeriFactu · AEAT test environment') : 'B2Brouter · Sandbox'}</h2>
        <p>{aeat ? c('Remisión directa a la preproducción de la AEAT', 'Direct submission to AEAT pre-production') : c('Factura electrónica en el entorno de pruebas', 'E-invoicing in the test environment')}</p>
      </div>
      <StatusBadge tone={view?.configured ? (view.enabled ? 'success' : 'info') : 'neutral'}>
        {!view?.configured ? c('Sin configurar', 'Not configured') : view.enabled ? c('Envíos activos', 'Sending enabled') : c('Guardada, envíos desactivados', 'Saved, sending disabled')}
      </StatusBadge>
    </div>
    <p className="fiscal-connection-intro">{aeat
      ? c('Remite desde el detalle de una factura el registro fiscal generado por PERA. El certificado se guarda cifrado. La comprobación del certificado es local: no confirma que la AEAT acepte tu representación ni tus registros.', 'Submit the fiscal record generated by PERA from an invoice detail view. The certificate is stored encrypted. The certificate check is local: it does not confirm that AEAT accepts your representation or records.')
      : c('Envía facturas electrónicas ordinarias F1 al sandbox. VeriFactu sigue conectado directamente con la AEAT; B2Brouter no lo sustituye.', 'Send ordinary F1 e-invoices to the sandbox. VeriFactu stays directly connected to AEAT; B2Brouter does not replace it.')}</p>
    {!aeat && <p className="fiscal-connection-intro">{c('Crea una cuenta en ', 'Create an account at ')}<a href="https://app.b2brouter.net" target="_blank" rel="noopener noreferrer">B2Brouter</a>{c(', activa el modo sandbox y genera una clave ', ', enable sandbox mode and generate a ')}<code>test_</code>{c('. Configura el contacto de pruebas y desactiva los informes fiscales automáticos. ', ' key. Set up the test contact and disable automatic tax reports. ')}<a href="https://docs.b2brouter.net/en/developers/testing/sandbox/" target="_blank" rel="noopener noreferrer">{c('Guía del sandbox', 'Sandbox guide')}</a>.</p>}
    {error && <p className="inline-error" role="alert">{error}</p>}
    {!view && !error && <p className="fiscal-connection-intro">{c('Cargando la configuración…', 'Loading configuration…')}</p>}
    {aeat && view?.certificate && <CertificateSummary info={view.certificate} issuerMatches={view.issuerMatches ?? null} c={c} locale={language === 'es' ? 'es-ES' : 'en-GB'} />}
    {view && <fieldset className="fiscal-connection-fields" disabled={busy}>
      <legend className="sr-only">{aeat ? c('Credenciales de la AEAT', 'AEAT credentials') : c('Credenciales de B2Brouter', 'B2Brouter credentials')}</legend>
      {!view.encryptionReady && <p className="inline-error" role="alert">{c('El servidor necesita configurar su clave de cifrado antes de guardar credenciales.', 'The server needs its encryption key configured before credentials can be saved.')}</p>}
      <div className="form-grid">
        {aeat ? <>
          <Field label={c('Tipo de certificado', 'Certificate type')} htmlFor={`${id}-type`}>
            <select id={`${id}-type`} value={view.account || 'CERTIFICATE'} onChange={(event) => edit({ account: event.target.value })}>
              <option value="CERTIFICATE">{c('Certificado personal / representante', 'Personal / representative certificate')}</option>
              <option value="SEAL">{c('Sello de entidad', 'Entity seal')}</option>
            </select>
          </Field>
          <Field label={c('Certificado P12/PFX', 'P12/PFX certificate')} htmlFor={`${id}-file`} required={!view.configured} hint={view.configured ? c('Ya hay uno guardado. Selecciona otro solo para sustituirlo.', 'One is already stored. Select another only to replace it.') : c('Archivo con clave privada, máximo 2 MB.', 'File with its private key, 2 MB maximum.')}>
            <input ref={fileInput} id={`${id}-file`} type="file" accept=".p12,.pfx,application/x-pkcs12" onChange={(event) => void selectCertificate(event.target.files?.[0])} />
          </Field>
          <Field label={c('Contraseña del certificado', 'Certificate password')} htmlFor={`${id}-password`} required={Boolean(certificate)} hint={c('Solo se envía al guardar; no se muestra de nuevo.', 'Only sent when saving; it is never shown again.')}>
            <input id={`${id}-password`} type="password" autoComplete="new-password" value={password} onChange={(event) => { setPassword(event.target.value); setDirty(true); setNotice('') }} />
          </Field>
        </> : <>
          <Field label={c('Identificador de la cuenta', 'Account ID')} htmlFor={`${id}-account`} required>
            <input id={`${id}-account`} required value={view.account} maxLength={80} pattern="[A-Za-z0-9_-]+" onChange={(event) => edit({ account: event.target.value })} />
          </Field>
          <Field label={c('Clave API sandbox', 'Sandbox API key')} htmlFor={`${id}-key`} required={!view.configured} hint={view.configured ? c('Guardada de forma cifrada. Déjala vacía para conservarla.', 'Stored encrypted. Leave blank to keep it.') : c('Solo se aceptan claves de pruebas que empiezan por test_.', 'Only test keys starting with test_ are accepted.')}>
            <input id={`${id}-key`} type="password" autoComplete="new-password" value={apiKey} placeholder="test_…" onChange={(event) => { setApiKey(event.target.value); setDirty(true); setNotice('') }} />
          </Field>
        </>}
      </div>
      <label className="switch-row"><input type="checkbox" checked={view.enabled} onChange={(event) => edit({ enabled: event.target.checked })} /><span>{c('Permitir envíos manuales de pruebas', 'Allow manual test submissions')}</span></label>
      <div className="form-actions">
        <button className="button button-primary" disabled={!view.encryptionReady || reading || !dirty} type="submit">{busy ? c('Guardando…', 'Saving…') : c('Guardar conexión', 'Save connection')}</button>
        <button className="button button-ghost" type="button" disabled={!view.configured || dirty} title={dirty ? c('Guarda los cambios antes de comprobar.', 'Save your changes before checking.') : undefined} onClick={() => void check()}>{aeat ? c('Comprobar certificado', 'Check certificate') : c('Comprobar cuenta', 'Check account')}</button>
      </div>
    </fieldset>}
    {notice && <p className="connection-success" role="status"><CheckCircle2 size={15} aria-hidden="true" /> {notice}</p>}
  </form>
}

function CertificateSummary({ info, issuerMatches, c, locale }: { info: CertificateInfo; issuerMatches: boolean | null; c: (es: string, en: string) => string; locale: string }) {
  const remaining = daysUntil(info.validUntil)
  const taxIds = [info.personalTaxId, info.entityTaxId].filter(Boolean).join(' · ')
  const warnings: string[] = []
  if (info.expired || remaining < 0) warnings.push(c('El certificado está caducado. Renuévalo en la FNMT y carga la copia nueva.', 'The certificate has expired. Renew it and upload the new copy.'))
  else if (remaining <= EXPIRY_WARNING_DAYS) warnings.push(c(`El certificado caduca en ${remaining} días. Renuévalo antes para no interrumpir los envíos.`, `The certificate expires in ${remaining} days. Renew it in advance to avoid interrupting submissions.`))
  if (issuerMatches === false) warnings.push(c('El NIF del certificado no coincide con el emisor configurado en VeriFactu. La AEAT solo lo aceptará si el titular está apoderado para ese emisor.', 'The certificate tax ID does not match the issuer configured in VeriFactu. AEAT will only accept it if the holder is authorised for that issuer.'))
  return <div className="certificate-summary">
    <dl>
      <div><dt>{c('Titular', 'Holder')}</dt><dd>{info.holder || '—'}</dd></div>
      <div><dt>NIF</dt><dd>{taxIds || '—'}</dd></div>
      <div><dt>{c('Emisor', 'Issuer')}</dt><dd>{info.issuer || '—'}</dd></div>
      <div><dt>{c('Válido hasta', 'Valid until')}</dt><dd>{formatDate(info.validUntil, locale)}</dd></div>
    </dl>
    {warnings.map((warning) => <p key={warning} className="certificate-warning" role="alert"><AlertTriangle size={15} aria-hidden="true" /> {warning}</p>)}
  </div>
}
