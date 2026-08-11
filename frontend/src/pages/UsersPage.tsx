import { KeyRound, Pencil, Plus, ShieldCheck, UserCog, UsersRound } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { EmptyState, LoadingState } from '../components/DataState'
import { Field, FormActions } from '../components/Form'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../components/Toast'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch, errorMessage } from '../lib/api'
import type { ManagedUser, RoleProfile } from '../types/api'

type EditingUser = ManagedUser | 'new' | null

export function UsersPage() {
  const { language } = useTranslation()
  const { identity } = useAuth()
  const c = useCallback((es: string, en: string) => language === 'es' ? es : en, [language])
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [roles, setRoles] = useState<RoleProfile[]>([])
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<EditingUser>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const { notify } = useToast()

  useEffect(() => {
    let active = true
    setLoading(true)
    Promise.all([apiFetch<ManagedUser[]>('/api/v1/users'), apiFetch<RoleProfile[]>('/api/v1/roles')])
      .then(([nextUsers, nextRoles]) => { if (active) { setUsers(nextUsers); setRoles(nextRoles); setError('') } })
      .catch((cause) => { if (active) setError(errorMessage(cause)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [refresh])

  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase(language)
    if (!normalized) return users
    return users.filter((user) => [user.username, user.displayName, user.email ?? '', ...user.roles]
      .some((value) => value.toLocaleLowerCase(language).includes(normalized)))
  }, [language, query, users])

  const saved = () => {
    setEditing(null)
    setRefresh((value) => value + 1)
    notify(c('Usuario guardado correctamente.', 'User saved successfully.'))
  }

  return (
    <div className="page-stack">
      <PageHeader eyebrow={c('Administración', 'Administration')} title={c('Usuarios y accesos', 'Users and access')} description={c('Define quién puede ver y modificar cada área de la empresa.', 'Define who can view and change each area of the company.')} icon={UserCog} actions={<button className="button button-primary" type="button" onClick={() => setEditing('new')}><Plus size={17} />{c('Nuevo usuario', 'New user')}</button>} />

      <section className="role-overview" aria-label={c('Perfiles disponibles', 'Available profiles')}>
        {roles.map((role) => <RoleSummary key={role.code} role={role} language={language} />)}
      </section>

      <section className="panel table-panel">
        <div className="table-toolbar user-toolbar">
          <div><strong>{c('Equipo', 'Team')}</strong><small>{c(`${users.length} cuentas en esta empresa`, `${users.length} accounts in this company`)}</small></div>
          <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={c('Buscar por nombre, usuario o perfil', 'Search by name, username or profile')} aria-label={c('Buscar usuarios', 'Search users')} />
        </div>
        {error && <div className="inline-error">{error}</div>}
        {loading ? <LoadingState /> : filtered.length > 0 ? (
          <div className="table-scroll"><table><thead><tr><th>{c('Persona', 'Person')}</th><th>{c('Usuario', 'Username')}</th><th>{c('Perfil', 'Profile')}</th><th>{c('Estado', 'Status')}</th><th><span className="sr-only">{c('Acciones', 'Actions')}</span></th></tr></thead><tbody>
            {filtered.map((user) => <tr key={user.id}><td><strong>{user.displayName}</strong><small>{user.email || '—'}</small></td><td><span className="code-cell">{user.username}</span></td><td><div className="role-cell">{user.roles.map((role) => <span key={role}>{roleLabel(role, language)}</span>)}</div></td><td><StatusBadge tone={user.active ? 'success' : 'neutral'}>{user.active ? c('Activo', 'Active') : c('Inactivo', 'Inactive')}</StatusBadge></td><td><button className="icon-button" type="button" disabled={user.id === identity?.id} onClick={() => setEditing(user)} aria-label={c(`Editar ${user.displayName}`, `Edit ${user.displayName}`)} title={user.id === identity?.id ? c('Tu cuenta no se modifica durante la sesión activa', 'Your account cannot be changed during its active session') : undefined}><Pencil size={16} /></button></td></tr>)}
          </tbody></table></div>
        ) : <EmptyState title={c('No hay usuarios', 'No users found')} description={query ? c('Prueba con otra búsqueda.', 'Try another search.') : c('Crea la primera cuenta del equipo.', 'Create the first team account.')} />}
      </section>

      <Modal open={editing !== null} title={editing === 'new' ? c('Nuevo usuario', 'New user') : c('Editar usuario', 'Edit user')} description={c('Asigna un perfil preparado para su responsabilidad real.', 'Assign a profile designed for the person’s actual responsibility.')} onClose={() => setEditing(null)} size="large">
        {editing && <UserForm key={editing === 'new' ? 'new' : editing.id} user={editing === 'new' ? null : editing} roles={roles} language={language} onCancel={() => setEditing(null)} onSaved={saved} />}
      </Modal>
    </div>
  )
}

function RoleSummary({ role, language }: { role: RoleProfile; language: 'es' | 'en' }) {
  const Icon = role.code === 'OWNER' ? ShieldCheck : role.code === 'ADMIN' ? KeyRound : UsersRound
  return <article className={`role-summary role-${role.code.toLowerCase()}`}><span><Icon size={18} /></span><div><strong>{roleLabel(role.code, language)}</strong><p>{roleDescription(role.code, language)}</p><small>{role.permissions.length} {language === 'es' ? 'permisos' : 'permissions'}</small></div></article>
}

function UserForm({ user, roles, language, onCancel, onSaved }: { user: ManagedUser | null; roles: RoleProfile[]; language: 'es' | 'en'; onCancel: () => void; onSaved: () => void }) {
  const c = (es: string, en: string) => language === 'es' ? es : en
  const [form, setForm] = useState({
    username: user?.username ?? '', displayName: user?.displayName ?? '', email: user?.email ?? '', password: '',
    role: user?.roles[0] ?? roles.find((role) => role.code === 'ADMIN')?.code ?? roles[0]?.code ?? '', active: user?.active ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (name: string, value: string | boolean) => setForm((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const payload = user
        ? { displayName: form.displayName.trim(), email: form.email.trim() || null, password: form.password || null, roleCodes: [form.role], active: form.active }
        : { username: form.username.trim(), displayName: form.displayName.trim(), email: form.email.trim() || null, password: form.password, roleCodes: [form.role] }
      await apiFetch<ManagedUser>(user ? `/api/v1/users/${user.id}` : '/api/v1/users', { method: user ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      onSaved()
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSaving(false)
    }
  }

  return <form onSubmit={submit}>
    <div className="form-grid">
      <Field label={c('Nombre visible', 'Display name')} htmlFor="user-display-name" required><input id="user-display-name" value={form.displayName} onChange={(event) => update('displayName', event.target.value)} maxLength={160} required /></Field>
      <Field label={c('Correo electrónico', 'Email address')} htmlFor="user-email"><input id="user-email" type="email" value={form.email} onChange={(event) => update('email', event.target.value)} maxLength={180} /></Field>
      <Field label={c('Nombre de usuario', 'Username')} htmlFor="user-username" required><input id="user-username" value={form.username} onChange={(event) => update('username', event.target.value)} maxLength={80} disabled={Boolean(user)} required /></Field>
      <Field label={user ? c('Nueva contraseña (opcional)', 'New password (optional)') : c('Contraseña', 'Password')} htmlFor="user-password" required={!user}><input id="user-password" type="password" autoComplete="new-password" value={form.password} onChange={(event) => update('password', event.target.value)} minLength={10} maxLength={100} required={!user} /></Field>
      <Field label={c('Perfil de acceso', 'Access profile')} htmlFor="user-role" wide required><div className="role-picker" id="user-role">{roles.map((role) => <label key={role.code} className={form.role === role.code ? 'selected' : ''}><input type="radio" name="role" value={role.code} checked={form.role === role.code} onChange={(event) => update('role', event.target.value)} /><span><strong>{roleLabel(role.code, language)}</strong><small>{roleDescription(role.code, language)}</small></span></label>)}</div></Field>
      {user && <Field label={c('Estado', 'Status')} htmlFor="user-active" wide><label className="switch-row" htmlFor="user-active"><input id="user-active" type="checkbox" checked={form.active} onChange={(event) => update('active', event.target.checked)} /><span>{c('Permitir que inicie sesión', 'Allow this user to sign in')}</span></label></Field>}
    </div>
    {error && <div className="form-error" role="alert">{error}</div>}
    <FormActions onCancel={onCancel} saving={saving} submitLabel={user ? c('Guardar cambios', 'Save changes') : c('Crear usuario', 'Create user')} />
  </form>
}

function roleLabel(code: string, language: 'es' | 'en') {
  const labels: Record<string, [string, string]> = {
    OWNER: ['Propietario', 'Owner'], ADMIN: ['Administrador', 'Administrator'], ECONOMY: ['Economía', 'Economy'],
    LOGISTICS: ['Logística y procesos', 'Logistics and workflows'], CATALOG: ['Catálogo y maestros', 'Catalogue and master data'],
  }
  return labels[code]?.[language === 'es' ? 0 : 1] ?? code
}

function roleDescription(code: string, language: 'es' | 'en') {
  const descriptions: Record<string, [string, string]> = {
    OWNER: ['Acceso total y control de propietarios.', 'Full access and owner-level control.'],
    ADMIN: ['Acceso total y gestión de usuarios.', 'Full access and user management.'],
    ECONOMY: ['Clientes, presupuestos, ventas y finanzas.', 'Customers, quotes, sales and finance.'],
    LOGISTICS: ['Proveedores, rutas, expediciones y procesos.', 'Suppliers, routes, shipments and workflows.'],
    CATALOG: ['Productos, precios, impuestos y embalajes.', 'Products, pricing, taxes and packaging.'],
  }
  return descriptions[code]?.[language === 'es' ? 0 : 1] ?? code
}
