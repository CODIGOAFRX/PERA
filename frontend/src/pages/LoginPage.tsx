import { ArrowRight, BarChart3, Check, Eye, EyeOff, Leaf, LockKeyhole, ShieldCheck, UserRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { LanguageSelector } from '../components/LanguageSelector'
import { PearBrandMark } from '../components/PearBrandMark'
import { useTranslation } from '../i18n/I18nProvider'
import { errorMessage } from '../lib/api'
import type { CompanyOption } from '../types/api'

export function LoginPage() {
  const { login } = useAuth()
  const { t } = useTranslation()
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
      <div className="login-language"><LanguageSelector compact /></div>
      <section className="login-panel">
        <div className="login-brand brand">
          <PearBrandMark />
          <span><strong>PERA</strong><small>ERP</small></span>
        </div>
        <div className="login-copy">
          <span className="eyebrow">{t('login.eyebrow')}</span>
          <h1>{t('login.titleLine1')}<br />{t('login.titleLine2')}</h1>
          <p>{t('login.description')}</p>
        </div>
        <div className="login-benefits">
          <span><ShieldCheck size={18} /><span><strong>{t('login.protected.title')}</strong><small>{t('login.protected.description')}</small></span></span>
          <span><BarChart3 size={18} /><span><strong>{t('login.insight.title')}</strong><small>{t('login.insight.description')}</small></span></span>
          <span><Leaf size={18} /><span><strong>{t('login.simple.title')}</strong><small>{t('login.simple.description')}</small></span></span>
        </div>
        <small className="login-version">{t('login.version')}</small>
      </section>

      <section className="login-form-section">
        <div className="login-form-card">
          {companies.length === 0 ? (
            <>
              <div className="form-heading">
                <span className="secure-dot"><LockKeyhole size={18} /></span>
                <div><h2>{t('login.welcome')}</h2><p>{t('login.instructions')}</p></div>
              </div>
              <form onSubmit={submit}>
                <label className="field-label" htmlFor="username">{t('login.username')}</label>
                <div className="input-with-icon"><UserRound size={18} /><input id="username" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required /></div>
                <label className="field-label" htmlFor="password">{t('login.password')}</label>
                <div className="input-with-icon password-input"><LockKeyhole size={18} /><input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? t('login.hidePassword') : t('login.showPassword')}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button></div>
                {error && <div className="form-error" role="alert">{error}</div>}
                <button className="button button-primary button-full" type="submit" disabled={loading}>{loading ? t('login.checking') : t('login.submit')}<ArrowRight size={18} /></button>
              </form>
              <div className="demo-hint"><Check size={16} /><span><strong>{t('login.local.title')}</strong> {t('login.local.description')}</span></div>
            </>
          ) : (
            <>
              <div className="form-heading"><span className="secure-dot"><Leaf size={18} /></span><div><h2>{t('login.chooseCompany')}</h2><p>{t('login.chooseCompanyDescription')}</p></div></div>
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
