import type { ReactNode } from 'react'

export function PageLoader() {
  return (
    <div className="flex justify-center py-16" role="status" aria-label="불러오는 중">
      <div className="size-7 animate-spin rounded-full border-4 border-stone-200 border-t-orange-500" />
    </div>
  )
}

export function PageMessage({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <p className="font-semibold">{title}</p>
      {children && <div className="text-sm text-stone-500">{children}</div>}
    </div>
  )
}

export function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-stone-200 bg-white p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}
