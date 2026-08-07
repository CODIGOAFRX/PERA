import { ArrowRight, BarChart3, Check, Eye, EyeOff, Leaf, LockKeyhole, ShieldCheck, UserRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../lib/api'
import type { CompanyOption } from '../types/api'

export function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('ChangeMe123!')
  const [showPassword, setShowPassword] = useState(false)
  const [companies, setCompanies] = useState<CompanyOption[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent, companyId?: string) => {
    event.preventDefault()
    setLoading(true)
    setError('')
    try {
      const response = await login(username.trim(), password, companyId)
      if (response.companySelectionRequired) setCompanies(response.companies)
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-panel">
        <div className="login-brand brand">
          <span className="brand-mark">P</span>
          <span><strong>PERA</strong><small>ERP</small></span>
        </div>
        <div className="login-copy">
          <span className="eyebrow">Gestión sencilla para pymes</span>
          <h1>Todo el negocio,<br />mucho más claro.</h1>
          <p>Clientes, catálogo, ventas y cobros en un espacio ordenado, rápido y pensado para trabajar.</p>
        </div>
        <div className="login-benefits">
          <span><ShieldCheck size={18} /><span><strong>Datos protegidos</strong><small>Acceso por empresa y permisos</small></span></span>
          <span><BarChart3 size={18} /><span><strong>Información útil</strong><small>El estado del negocio a primera vista</small></span></span>
          <span><Leaf size={18} /><span><strong>Sin ruido</strong><small>Solo lo necesario para avanzar</small></span></span>
        </div>
        <small className="login-version">PERA ERP · versión inicial 0.1</small>
      </section>

      <section className="login-form-section">
        <div className="login-form-card">
          {companies.length === 0 ? (
            <>
              <div className="form-heading">
                <span className="secure-dot"><LockKeyhole size={18} /></span>
                <div><h2>Bienvenido de nuevo</h2><p>Introduce tus datos para continuar.</p></div>
              </div>
              <form onSubmit={submit}>
                <label className="field-label" htmlFor="username">Usuario</label>
                <div className="input-with-icon"><UserRound size={18} /><input id="username" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required /></div>
                <label className="field-label" htmlFor="password">Contraseña</label>
                <div className="input-with-icon password-input"><LockKeyhole size={18} /><input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button></div>
                {error && <div className="form-error" role="alert">{error}</div>}
                <button className="button button-primary button-full" type="submit" disabled={loading}>{loading ? 'Comprobando…' : 'Entrar'}<ArrowRight size={18} /></button>
              </form>
              <div className="demo-hint"><Check size={16} /><span><strong>Entorno local</strong> Las credenciales de demostración ya están preparadas.</span></div>
            </>
          ) : (
            <>
              <div className="form-heading"><span className="secure-dot"><Leaf size={18} /></span><div><h2>Elige una empresa</h2><p>Tu usuario tiene acceso a varios espacios.</p></div></div>
              <div className="company-options">
                {companies.map((company) => <button key={company.id} type="button" disabled={loading} onClick={(event) => submit(event, company.id)}><span className="company-avatar">{company.code.slice(0, 2)}</span><span><strong>{company.name}</strong><small>{company.code}</small></span><ArrowRight size={18} /></button>)}
              </div>
              {error && <div className="form-error" role="alert">{error}</div>}
            </>
          )}
        </div>
      </section>
    </main>
  )
}
