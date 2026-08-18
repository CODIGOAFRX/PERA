export function PearBrandMark({ className = '' }: { className?: string }) {
  return (
    <span className={`brand-mark ${className}`} aria-hidden="true">
      <svg viewBox="0 0 48 48" focusable="false">
        <path d="M25.2 14.1c-1.1 5.2-4 7.1-7.3 10.2-3 2.8-4.8 6.1-4.1 10.3.8 5.4 5.4 8.4 10.5 8.4 5.5 0 10-3.4 10.6-8.8.5-4.3-1.7-7.4-4.7-10.2-3-2.8-4.4-5.3-5-9.9Z" />
        <path d="M24.8 15.2c-.2-4.6 1.8-7.9 5.7-9.8" />
        <path d="M29.1 7.2c3.5-1.6 6.8-.7 8.4 1.1-2.4 3.1-5.8 4-9.4 2.7" />
        <path d="M18.5 31.9c.2-2.4 1.5-4.4 3.5-6.1" className="pear-shine" />
      </svg>
    </span>
  )
}
