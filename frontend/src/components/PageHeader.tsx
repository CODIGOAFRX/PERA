import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

interface PageHeaderProps {
  eyebrow?: string
  title: string
  description: string
  icon?: LucideIcon
  actions?: ReactNode
}

export function PageHeader({ eyebrow, title, description, icon: Icon, actions }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="page-title-wrap">
        {Icon && <span className="page-icon"><Icon size={20} /></span>}
        <div>
          {eyebrow && <span className="eyebrow">{eyebrow}</span>}
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}
