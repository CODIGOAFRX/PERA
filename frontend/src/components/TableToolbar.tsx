import { Search, X } from 'lucide-react'
import { useTranslation } from '../i18n/I18nProvider'

export function TableToolbar({ value, onChange, placeholder, children, hideSearch = false }: { value: string; onChange: (value: string) => void; placeholder: string; children?: React.ReactNode; hideSearch?: boolean }) {
  const { t } = useTranslation()
  return <div className={`table-toolbar ${hideSearch ? 'toolbar-filter-only' : ''}`}>{!hideSearch && <div className="search-box"><Search size={17} /><input type="search" value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} aria-label={placeholder} />{value && <button type="button" onClick={() => onChange('')} aria-label={t('common.clearSearch')}><X size={15} /></button>}</div>}{children && <div className="toolbar-actions">{children}</div>}</div>
}
