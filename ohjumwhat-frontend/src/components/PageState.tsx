import type { ReactNode } from 'react'

export function PageLoader() {
  return (
    <div className="flex justify-center py-16" role="status" aria-label="불러오는 중">
      <div className="size-7 animate-spin rounded-full border-4 border-border-default border-t-border-brand" />
    </div>
  )
}

export function PageMessage({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <p className="font-bold">{title}</p>
      {children && <div className="text-sm text-text-tertiary">{children}</div>}
    </div>
  )
}

export function Section({ title, action, className = '', children }: { title: string; action?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <section className={`rounded-2xl border border-border-default bg-bg-surface p-5 ${className}`}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-bold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}
