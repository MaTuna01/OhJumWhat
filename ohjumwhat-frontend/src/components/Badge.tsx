import type { ReactNode } from 'react'

export type BadgeTone = 'brand' | 'warning' | 'neutral' | 'success' | 'danger'

const tones: Record<BadgeTone, string> = {
  brand: 'bg-bg-brand-muted text-text-brand-strong',
  warning: 'bg-bg-warning-soft text-text-warning',
  neutral: 'bg-bg-muted text-text-tertiary',
  success: 'bg-bg-success-soft text-text-success',
  danger: 'bg-bg-danger-soft text-text-danger',
}

/** Figma Badge. 색만으로 뜻을 전하지 않도록 항상 글자를 함께 쓴다. */
export default function Badge({ tone, children }: { tone: BadgeTone; children: ReactNode }) {
  return <span className={`inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>
}
