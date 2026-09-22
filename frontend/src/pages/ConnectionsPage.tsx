import { Cable, CheckCircle2, Mail, Unplug } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { PageHeader } from '../components/PageHeader'
import { Field } from '../components/Form'
import { LoadingState } from '../components/DataState'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'

type Connection = { configured: boolean; encryptionReady: boolean; host: string; port: number; username: string; senderEmail: string; senderName: string; security: string; enabled: boolean; autoInvoices: boolean; verifiedAt: string | null; passwordStored: boolean }
export function ConnectionsPage() {
  const { language } = useTranslation()
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState<Connection | null>(null)
  const [provider, setProvider] = useState<'smtp' | 'gmail'>('smtp')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => { let active = true; apiFetch<Connection>('/api/v1/connections/email').then(v => { if (active) { setForm(v); setProvider(v.host.toLowerCase() === 'smtp.gmail.com' ? 'gmail' : 'smtp') } }).catch(e => { if (active) setError(errorMessage(e)) }); return () => { active = false } }, [])
  function change<K extends keyof Connection>(key: K, value: Connection[K]) {
    if (!form) return
    setDirty(true)
    const credentialsChanged = !['enabled', 'autoInvoices', 'senderName'].includes(key)
    setForm({ ...form, [key]: value, ...(credentialsChanged ? { verifiedAt: null, enabled: false } : {}) })
    setNotice('')
  }
  function selectProvider(next: 'smtp' | 'gmail') {
    if (!form || next === provider) return
    setProvider(next); setPassword(''); setDirty(true); setNotice(''); setError('')
    setForm({ ...form, host: next === 'gmail' ? 'smtp.gmail.com' : '', port: 587, security: 'STARTTLS',
      senderEmail: next === 'gmail' ? form.username : form.senderEmail,
      verifiedAt: null, enabled: false, passwordStored: false })
  }
  function gmailAddress(value: string) {
    if (!form) return
    setDirty(true); setNotice('')
    setForm({ ...form, username: value, senderEmail: value, verifiedAt: null, enabled: false })
  }
  async function action(kind: 'save' | 'test' | 'disconnect') {
    if (!form) return
    const secret = provider === 'gmail' ? password.replace(/\s/g, '') : password
    if (kind === 'save' && provider === 'gmail' && secret && secret.length !== 16) {
      setError(c('La contraseña de aplicación de Google tiene 16 caracteres. Usa la creada para PERA, no la contraseña habitual de Gmail.', 'A Google app password has 16 characters. Use the one created for PERA, not your regular Gmail password.'))
      return
    }
    setBusy(true); setError(''); setNotice('')
    try {
      const result = await apiFetch<Connection>(`/api/v1/connections/email${kind === 'test' ? '/test' : ''}`, { method: kind === 'test' ? 'POST' : kind === 'disconnect' ? 'DELETE' : 'PUT', ...(kind === 'save' ? { body: JSON.stringify({ ...form, password: secret || null }) } : {}) })
      setForm(result); setPassword(''); setDirty(false)
      if (kind === 'disconnect') setProvider('smtp')
      setNotice(kind === 'test' ? c('Conexión y autenticación correctas. Ya puedes activar los envíos.', 'Connection and authentication succeeded. You can now enable sending.') : kind === 'disconnect' ? c('Correo desconectado. Credencial eliminada.', 'Email disconnected. Credential removed.') : c('Configuración guardada.', 'Settings saved.'))
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }
  return <div className="page-stack">
    <PageHeader eyebrow={c('Tu empresa, conectada', 'Your connected company')} title={c('Conexiones', 'Connections')} description={c('Conecta tu correo para enviar documentos desde PERA con tu propio remitente.', 'Connect your email to send documents from PERA using your own sender.')} icon={Cable} />
    {error && <div className="inline-error" role="alert">{error}</div>}
    {!form ? !error && <LoadingState /> : <form className="panel connection-panel" onSubmit={(event: FormEvent) => { event.preventDefault(); void action('save') }}>
      <div className="connection-heading"><Mail size={23} /><div><h2>{provider === 'gmail' ? 'Gmail' : c('Correo de empresa', 'Company email')}</h2><p>{form.enabled ? c('Envíos activados', 'Sending enabled') : c('SMTP · Configuración guiada', 'SMTP · Guided setup')}</p></div>{form.enabled && <CheckCircle2 size={20} />}</div>
      {!form.encryptionReady && <div className="inline-error">{c('El servidor necesita configurar su clave de cifrado antes de guardar contraseñas.', 'The server needs its encryption key configured before passwords can be saved.')}</div>}
      <div className="connection-provider"><Field label={c('¿Qué correo utilizas?', 'Which email do you use?')} htmlFor="email-provider"><select id="email-provider" value={provider} disabled={busy} onChange={e => selectProvider(e.target.value as 'smtp' | 'gmail')}><option value="smtp">{c('Correo de empresa / otro proveedor', 'Company email / other provider')}</option><option value="gmail">Gmail / Google Workspace</option></select></Field></div>
      <section className="connection-step"><span className="step-number">1</span><div><h3>{provider === 'gmail' ? c('Prepara tu Gmail', 'Prepare your Gmail') : c('Prepara tu cuenta', 'Prepare your account')}</h3>
        {provider === 'gmail' ? <>
          <p>{c('Puedes usar tu cuenta personal de Gmail o el correo de tu dominio alojado en Google Workspace. PERA ya conoce el servidor y la seguridad de Google.', 'Use your personal Gmail account or a custom-domain mailbox hosted on Google Workspace. PERA already knows Google’s server and security settings.')}</p>
          <ol className="connection-guide">
            <li><a href="https://myaccount.google.com/signinoptions/two-step-verification" target="_blank" rel="noopener noreferrer">{c('Activa la verificación en dos pasos en Google.', 'Enable two-step verification in Google.')}</a></li>
            <li><a href="https://myaccount.google.com/apppasswords" target="_blank" rel="noopener noreferrer">{c('Abre Contraseñas de aplicación', 'Open App passwords')}</a>{c(' y crea una llamada «PERA ERP».', ' and create one named “PERA ERP”.')}</li>
            <li>{c('Copia los 16 caracteres y pégalos abajo. Usa esa contraseña de aplicación, no tu contraseña habitual de Gmail.', 'Copy the 16 characters and paste them below. Use this app password, not your regular Gmail password.')}</li>
          </ol>
          <p>{c('¿No aparece Contraseñas de aplicación? Algunas cuentas de empresa, cuentas con Protección Avanzada o con verificación solo mediante llaves de seguridad no la permiten. Consulta al administrador o revisa la ayuda de Google.', 'App passwords missing? Some managed accounts, Advanced Protection accounts or security-key-only verification setups do not allow them. Ask your administrator or check Google’s help.')} <a href="https://support.google.com/accounts/answer/185833" target="_blank" rel="noopener noreferrer">{c('Ayuda de Google', 'Google help')}</a></p>
          <p>{c('Esta conexión utiliza una contraseña de aplicación. No es el acceso con el botón «Iniciar sesión con Google».', 'This connection uses an app password. It is not the “Sign in with Google” flow.')}</p>
        </> : <><p>{c('Pide a quien gestiona tu correo el servidor SMTP, puerto, usuario y contraseña. El dominio de tu web no determina esos datos: tener pedrogomez.dev no significa que el servidor sea smtp.pedrogomez.dev.', 'Ask your email provider for the SMTP server, port, username and password. Your website domain does not determine these settings: owning pedrogomez.dev does not mean the server is smtp.pedrogomez.dev.')}</p><p>{c('Si tu correo funciona con Google Workspace, elige Gmail arriba. Para otros proveedores, copia los datos de su panel de correo. El remitente debe estar autorizado en esa cuenta.', 'If your email uses Google Workspace, choose Gmail above. For other providers, copy the settings from their email control panel. The sender must be authorized for that account.')}</p></>}
      </div></section>
      <section className="connection-step"><span className="step-number">2</span><div><h3>{c('Introduce los datos del correo', 'Enter your email settings')}</h3><div className="form-grid">
        {provider === 'smtp' && <>
        <Field label={c('Servidor SMTP', 'SMTP server')} htmlFor="smtp-host" required><input id="smtp-host" value={form.host} onChange={e => change('host', e.target.value)} placeholder="smtp.tuempresa.com" maxLength={253} required /></Field>
        <Field label={c('Seguridad', 'Security')} htmlFor="smtp-security"><select id="smtp-security" value={form.security} onChange={e => { change('security', e.target.value) }}><option value="STARTTLS">STARTTLS</option><option value="TLS">TLS</option></select></Field>
        <Field label={c('Puerto', 'Port')} htmlFor="smtp-port" required><input id="smtp-port" type="number" min={1} max={65535} value={form.port} onChange={e => change('port', Number(e.target.value))} required /><small>{c('Habitualmente 587 para STARTTLS o 465 para TLS.', 'Usually 587 for STARTTLS or 465 for TLS.')}</small></Field>
        </>}
        <Field label={provider === 'gmail' ? c('Tu dirección de Gmail o Google Workspace', 'Your Gmail or Google Workspace address') : c('Usuario SMTP', 'SMTP username')} htmlFor="smtp-user" required><input id="smtp-user" type={provider === 'gmail' ? 'email' : 'text'} value={form.username} onChange={e => provider === 'gmail' ? gmailAddress(e.target.value) : change('username', e.target.value)} autoComplete="off" maxLength={254} required /></Field>
        <Field label={provider === 'gmail' ? c('Contraseña de aplicación de Google', 'Google app password') : c('Contraseña SMTP', 'SMTP password')} htmlFor="smtp-password" required={!form.passwordStored}><input id="smtp-password" type="password" value={password} autoComplete="new-password" maxLength={1000} onChange={e => { setPassword(e.target.value); setDirty(true); setForm({ ...form, enabled: false, verifiedAt: null }) }} required={!form.passwordStored} /><small>{form.passwordStored ? c('Guardada de forma cifrada. Déjala vacía para conservarla.', 'Stored encrypted. Leave blank to keep it.') : c('Se guardará cifrada en el servidor.', 'It will be stored encrypted on the server.')}</small></Field>
        <Field label={c('Nombre del remitente', 'Sender name')} htmlFor="smtp-name" required><input id="smtp-name" value={form.senderName} onChange={e => change('senderName', e.target.value)} maxLength={180} required /></Field>
        {provider === 'smtp' && <Field label={c('Correo del remitente', 'Sender email')} htmlFor="smtp-from" required><input id="smtp-from" type="email" value={form.senderEmail} onChange={e => change('senderEmail', e.target.value)} maxLength={254} required /></Field>}
      </div>{provider === 'gmail' && <p>{c('Conexión de Google:', 'Google connection:')} {form.host} · {form.port} · {form.security}{' · '}{form.senderEmail}</p>}<button className="button button-secondary" type="submit" disabled={busy || !form.encryptionReady}>{c('Guardar configuración', 'Save settings')}</button></div></section>
      <section className="connection-step"><span className="step-number">3</span><div><h3>{c('Comprueba la conexión', 'Check the connection')}</h3><p>{c('Verifica la conexión segura y las credenciales. Esta comprobación no envía ningún correo.', 'Verify the secure connection and credentials. This check does not send email.')}</p><button className="button button-secondary" type="button" disabled={busy || !form.configured || dirty} onClick={() => void action('test')}>{c('Comprobar conexión', 'Check connection')}</button>{form.verifiedAt && <p className="connection-success">{c('Conexión comprobada', 'Connection checked')} ✓</p>}</div></section>
      <section className="connection-step"><span className="step-number">4</span><div><h3>{c('Elige cómo enviar', 'Choose how to send')}</h3>
        <label className="switch-row"><input type="checkbox" checked={form.enabled} disabled={!form.verifiedAt || busy} onChange={e => change('enabled', e.target.checked)} /><span>{c('Activar envíos desde esta empresa', 'Enable sending for this company')}</span></label>
        <label className="switch-row"><input type="checkbox" checked={form.autoInvoices} disabled={!form.verifiedAt || busy} onChange={e => change('autoInvoices', e.target.checked)} /><span>{c('Enviar automáticamente las facturas al expedirlas', 'Automatically email invoices when issued')}</span></label>
        <p>{c('Incluye facturas ordinarias y rectificativas. Se adjunta el PDF imprimible con el QR de Veri*Factu cuando corresponda. Los borradores no se envían. Los presupuestos se envían desde su detalle.', 'Includes regular and credit invoices. The printable PDF includes the Veri*Factu QR when applicable. Drafts are not sent. Quotes are sent from their detail view.')}</p>
        <button className="button button-primary" type="submit" disabled={busy || !form.verifiedAt}>{c('Guardar preferencias', 'Save preferences')}</button>
      </div></section>
      {notice && <p role="status" className="connection-success">{notice}</p>}
      {form.configured && <button type="button" className="button button-ghost" disabled={busy} onClick={() => void action('disconnect')}><Unplug size={16} />{c('Desconectar correo', 'Disconnect email')}</button>}
    </form>}
  </div>
}
