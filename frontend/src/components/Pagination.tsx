import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, onChange }: PaginationProps) {
  if (totalPages <= 1) return <div className="pagination-summary">{totalElements} registros</div>
  return (
    <div className="pagination">
      <span>{totalElements} registros · Página {page + 1} de {totalPages}</span>
      <div>
        <button type="button" className="icon-button" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Página anterior"><ChevronLeft size={18} /></button>
        <button type="button" className="icon-button" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label="Página siguiente"><ChevronRight size={18} /></button>
      </div>
    </div>
  )
}
