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

/**
 * Figma 「와이어프레임 · 데스크톱」의 2단: lg(1024px) 이상에서 본문 + 오른쪽 사이드(320px).
 * 좁은 화면에서는 DOM 순서대로 쌓이므로, 모바일 간격(`flex flex-col gap-*`)은 쓰는 쪽에서 붙인다.
 */
export const columnsClass = 'lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start lg:gap-x-8'
