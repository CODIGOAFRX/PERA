import { Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import { useTranslation } from '../i18n/I18nProvider'
import { apiFetch } from '../lib/api'
import { useRouter } from '../routing/Router'
import { appRoutes, isRouteAllowed } from '../routing/routes'

/**
 * Buscador de toda la aplicación (Ctrl+K o Cmd+K desde cualquier pantalla): pantallas, opciones de
 * configuración y datos (clientes, proveedores, artículos y documentos de venta).
 */
interface Result {
  key: string
  group: string
  title: string
  subtitle?: string
  to: string
}

/** Opciones que viven dentro de una pantalla: se buscan por lo que el usuario escribiría. */
const options: { words: string; title: [string, string]; to: string; routeId: string }[] = [
  { words: 'certificado aeat factura_e.ini conexion verifactu remision hacienda', title: ['Certificado y conexión con la AEAT', 'AEAT certificate and connection'], to: '/conexiones', routeId: 'connections' },
  { words: 'correo smtp email enviar facturas por correo', title: ['Correo de la empresa (SMTP)', 'Company email (SMTP)'], to: '/conexiones', routeId: 'connections' },
  { words: 'b2brouter factura electronica facturae', title: ['B2Brouter · factura electrónica', 'B2Brouter · e-invoicing'], to: '/conexiones', routeId: 'connections' },
  { words: 'empresa razon social nif cif logo direccion datos de la empresa', title: ['Datos de la empresa (razón social, NIF, logo)', 'Company data (legal name, tax ID, logo)'], to: '/configuracion', routeId: 'settings' },
  { words: 'verifactu emisor entorno pruebas produccion configuracion fiscal', title: ['Configuración de Veri*Factu', 'Veri*Factu settings'], to: '/configuracion', routeId: 'settings' },
  { words: 'numeracion series contador numero de factura', title: ['Series de documentos (numeración, a qué pasan, impresión)', 'Document series'], to: '/configuracion', routeId: 'settings' },
  { words: 'formas de pago vencimientos plazos recibos dias', title: ['Formas de pago', 'Payment methods'], to: '/finanzas', routeId: 'finance' },
  { words: 'usuarios contraseña permisos roles acceso', title: ['Usuarios, contraseñas y permisos', 'Users, passwords and permissions'], to: '/usuarios', routeId: 'users' },
  { words: 'comerciales comision vendedor', title: ['Comerciales y comisiones', 'Salespeople and commissions'], to: '/comisiones', routeId: 'commissions' },
  { words: 'remesas recibos cobros caja arqueo', title: ['Cartera: recibos, remesas y caja', 'Collections: receipts, remittances and cash'], to: '/cartera', routeId: 'collections' },
  { words: 'tarifas precios iva impuestos grupos familias', title: ['Maestros: tarifas, impuestos y grupos', 'Master data: price lists, taxes and groups'], to: '/maestros', routeId: 'catalogConfiguration' },
  { words: 'existencias stock almacen inventario traspaso ubicacion', title: ['Almacén y existencias', 'Warehouse and stock'], to: '/almacen', routeId: 'inventory' },
  { words: 'historial auditoria quien cambio', title: ['Historial de cambios', 'Change history'], to: '/historial', routeId: 'history' },
]

/** Sin acentos ni mayúsculas, para que «razon» encuentre «razón». */
const fold = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

type Page<T> = { content: T[] }

export function GlobalSearch() {
  const { t, language } = useTranslation()
  const c = (es: string, en: string) => (language === 'es' ? es : en)
  const { identity } = useAuth()
  const { navigate } = useRouter()
  const roles = useMemo(() => identity?.roles ?? [], [identity?.roles])
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [data, setData] = useState<Result[]>([])
  const [searching, setSearching] = useState(false)
  const [selected, setSelected] = useState(0)
  const input = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const onKey = (event: globalThis.KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setOpen(true)
      }
    }
    const onOpen = () => setOpen(true)
    window.addEventListener('keydown', onKey)
    window.addEventListener('pera:open-search', onOpen)
    return () => { window.removeEventListener('keydown', onKey); window.removeEventListener('pera:open-search', onOpen) }
  }, [])

  useEffect(() => {
    if (open) { setQuery(''); setData([]); setSelected(0); window.setTimeout(() => input.current?.focus(), 0) }
  }, [open])

  const allowed = useCallback((routeId: string) => {
    const route = appRoutes.find((item) => item.id === routeId)
    return route ? isRouteAllowed(route, roles) : false
  }, [roles])

  // Pantallas y opciones: se buscan aquí mismo, sin ir al servidor.
  const local = useMemo<Result[]>(() => {
    const term = fold(query.trim())
    if (!term) return []
    const screens = appRoutes.filter((route) => isRouteAllowed(route, roles) && fold(t(route.navigation.labelKey)).includes(term))
      .map((route) => ({ key: `screen-${route.id}`, group: c('Pantallas', 'Screens'), title: t(route.navigation.labelKey), to: route.path }))
    const settings = options.filter((option) => allowed(option.routeId) && term.split(/\s+/).every((word) => fold(`${option.words} ${option.title[0]}`).includes(word)))
      .map((option) => ({ key: `option-${option.title[0]}`, group: c('Opciones', 'Options'), title: option.title[language === 'es' ? 0 : 1], to: option.to }))
    return [...screens, ...settings]
  }, [query, roles, t, allowed, language])

  // Datos: a partir de dos letras y con una pausa, para no preguntar al servidor en cada tecla.
  useEffect(() => {
    const term = query.trim()
    if (term.length < 2) { setData([]); setSearching(false); return }
    let active = true
    setSearching(true)
    const timer = window.setTimeout(() => {
      const q = encodeURIComponent(term)
      const sources: Promise<Result[]>[] = []
      if (allowed('customers')) sources.push(apiFetch<Page<{ id: string; code: string; legalName: string; taxId?: string | null }>>(`/api/v1/customers?query=${q}&size=5`)
        .then((page) => page.content.map((item) => ({ key: `customer-${item.id}`, group: c('Clientes', 'Customers'), title: item.legalName, subtitle: [item.code, item.taxId].filter(Boolean).join(' · '), to: `/clientes?q=${encodeURIComponent(item.code)}` }))))
      if (allowed('suppliers')) sources.push(apiFetch<Page<{ id: string; code: string; legalName: string; taxId?: string | null }>>(`/api/v1/suppliers?query=${q}&size=5`)
        .then((page) => page.content.map((item) => ({ key: `supplier-${item.id}`, group: c('Proveedores', 'Suppliers'), title: item.legalName, subtitle: [item.code, item.taxId].filter(Boolean).join(' · '), to: `/proveedores?q=${encodeURIComponent(item.code)}` }))))
      if (allowed('catalog')) sources.push(apiFetch<Page<{ id: string; code: string; name: string }>>(`/api/v1/products?query=${q}&size=5`)
        .then((page) => page.content.map((item) => ({ key: `product-${item.id}`, group: c('Artículos', 'Products'), title: item.name, subtitle: item.code, to: `/catalogo?q=${encodeURIComponent(item.code)}` }))))
      if (allowed('sales')) sources.push(apiFetch<Page<{ id: string; number: string; customerName: string }>>(`/api/v1/documents?q=${q}&size=5`)
        .then((page) => page.content.map((item) => ({ key: `document-${item.id}`, group: c('Documentos de venta', 'Sales documents'), title: item.number, subtitle: item.customerName, to: `/ventas?q=${encodeURIComponent(item.number)}` }))))
      // Si una fuente falla (sin permiso, servicio caído) se enseñan las demás.
      void Promise.allSettled(sources).then((results) => {
        if (!active) return
        setData(results.flatMap((result) => result.status === 'fulfilled' ? result.value : []))
        setSearching(false)
      })
    }, 250)
    return () => { active = false; window.clearTimeout(timer) }
  }, [query, allowed, language])

  const results = useMemo(() => [...local, ...data], [local, data])
  useEffect(() => { setSelected(0) }, [results.length])

  const go = (result: Result) => { setOpen(false); navigate(result.to) }
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') { event.preventDefault(); setSelected((index) => Math.min(index + 1, results.length - 1)) }
    else if (event.key === 'ArrowUp') { event.preventDefault(); setSelected((index) => Math.max(index - 1, 0)) }
    else if (event.key === 'Enter' && results[selected]) { event.preventDefault(); go(results[selected]) }
    else if (event.key === 'Escape') { event.preventDefault(); setOpen(false) }
  }

  if (!open) return null
  let lastGroup = ''
  return <div className="global-search-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setOpen(false) }}>
    <div className="global-search" role="dialog" aria-modal="true" aria-label={c('Buscar en PERA', 'Search PERA')}>
      <label className="global-search-input">
        <Search size={18} aria-hidden="true" />
        <input ref={input} value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={onKeyDown}
          placeholder={c('Busca pantallas, opciones, clientes, artículos, facturas…', 'Search screens, options, customers, products, invoices…')}
          aria-label={c('Buscar en PERA', 'Search PERA')} aria-controls="global-search-results" />
        <kbd>Esc</kbd>
      </label>
      <ul id="global-search-results" className="global-search-results" role="listbox">
        {results.map((result, index) => {
          const header = result.group !== lastGroup ? result.group : ''
          lastGroup = result.group
          return <li key={result.key} role="presentation">
            {header && <span className="global-search-group">{header}</span>}
            <button type="button" role="option" aria-selected={index === selected} className={index === selected ? 'selected' : ''}
              onMouseEnter={() => setSelected(index)} onClick={() => go(result)}>
              <strong>{result.title}</strong>{result.subtitle && <small>{result.subtitle}</small>}
            </button>
          </li>
        })}
      </ul>
      <p className="global-search-status" role="status">
        {!query.trim() ? c('Escribe lo que buscas. Ctrl+K lo abre desde cualquier pantalla.', 'Type what you are looking for. Ctrl+K opens it from any screen.')
          : searching ? c('Buscando…', 'Searching…')
          : results.length === 0 ? c('No se ha encontrado nada.', 'Nothing found.') : ''}
      </p>
    </div>
  </div>
}
