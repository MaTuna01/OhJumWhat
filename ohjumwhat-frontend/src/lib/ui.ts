export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-bg-brand text-text-on-brand hover:bg-bg-brand-hover',
  secondary: 'border border-border-strong bg-bg-surface text-text-primary hover:bg-bg-subtle',
  danger: 'bg-bg-danger text-text-on-brand hover:bg-bg-danger/90',
  ghost: 'text-text-secondary hover:bg-bg-muted',
}

/** 버튼 스타일. 버튼 모양이 필요한 Link에도 쓴다. */
export function buttonClass(variant: ButtonVariant = 'primary', className = '') {
  return `inline-flex items-center justify-center gap-1.5 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`
}

export const inputClass =
  'w-full rounded-lg border border-border-strong bg-bg-surface px-3 py-2 text-sm placeholder:text-text-placeholder focus:border-border-brand focus:outline-none focus:ring-2 focus:ring-border-brand/20'
