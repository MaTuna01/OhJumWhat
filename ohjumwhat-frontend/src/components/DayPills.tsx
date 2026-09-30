import { DAY_LABELS, daysFromMask, toggleDay } from '../lib/daysOfWeek.ts'

type Props = {
  mask: number
  /** 있으면 요일을 눌러 켜고 끌 수 있다(규칙 편집). 없으면 보기 전용(규칙 카드). */
  onChange?: (mask: number) => void
  size?: 'sm' | 'lg'
}

/** Figma 06 요일 표시: 켜진 요일은 오렌지 원, 꺼진 요일은 흰 원 */
export default function DayPills({ mask, onChange, size = 'sm' }: Props) {
  const on = new Set(daysFromMask(mask))
  // 편집용(lg)은 7칸 그리드로 폭에 맞춰 줄어든다(모바일 모달에서도 넘치지 않게).
  const box = size === 'lg' ? 'aspect-square w-full text-sm font-medium' : 'size-7 text-xs font-medium'
  const layout = size === 'lg' ? 'grid w-full max-w-80 grid-cols-7 gap-1.5' : 'flex gap-1'
  return (
    <div className={layout} role={onChange ? 'group' : undefined} aria-label={onChange ? '요일 선택' : undefined}>
      {DAY_LABELS.map((label, i) => {
        const active = on.has(i)
        const style = active ? 'bg-bg-brand text-text-on-brand' : 'border border-border-default bg-bg-surface text-text-placeholder'
        return onChange ? (
          <button
            key={label}
            type="button"
            aria-pressed={active}
            aria-label={`${label}요일`}
            onClick={() => onChange(toggleDay(mask, i))}
            className={`${box} ${style} flex shrink-0 items-center justify-center rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand`}
          >
            {label}
          </button>
        ) : (
          <span key={label} aria-hidden className={`${box} ${style} flex shrink-0 items-center justify-center rounded-full`}>
            {label}
          </span>
        )
      })}
    </div>
  )
}
