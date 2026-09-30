import type { ReactNode } from 'react'

export function FullPageLoader() {
  return (
    <div className="flex min-h-dvh items-center justify-center" role="status" aria-label="불러오는 중">
      <div className="size-8 animate-spin rounded-full border-4 border-border-default border-t-border-brand" />
    </div>
  )
}

export function FullPageMessage({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 px-4 text-center">
      <p className="text-lg font-bold">{title}</p>
      {children}
    </div>
  )
}
