import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useTranslation } from '../i18n/I18nProvider'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, onChange }: PaginationProps) {
  const { t } = useTranslation()
  if (totalPages <= 1) return <div className="pagination-summary">{t('common.records', { count: totalElements })}</div>
  return (
    <div className="pagination">
      <span>{t('common.pagination', { count: totalElements, page: page + 1, totalPages })}</span>
      <div>
        <button type="button" className="icon-button" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label={t('common.previousPage')}><ChevronLeft size={18} /></button>
        <button type="button" className="icon-button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label={t('common.nextPage')}><ChevronRight size={18} /></button>
      </div>
    </div>
  )
}
