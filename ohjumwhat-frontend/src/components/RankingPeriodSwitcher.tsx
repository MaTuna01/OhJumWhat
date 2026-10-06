import type { PeriodLabel, RankingPeriod } from '../lib/ranking.ts'

type Props = {
  period: RankingPeriod
  label: PeriodLabel
  canPrevious: boolean
  canNext: boolean
  onPeriodChange: (period: RankingPeriod) => void
  onPrevious: () => void
  onNext: () => void
  className?: string
}

const periods: { value: RankingPeriod; label: string }[] = [
  { value: 'WEEK', label: '주간' },
  { value: 'MONTH', label: '월간' },
]

const navClass =
  'flex size-8 shrink-0 items-center justify-center rounded-full border border-border-default bg-bg-surface text-sm font-bold text-text-secondary hover:bg-bg-subtle focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand disabled:cursor-not-allowed disabled:text-text-placeholder disabled:hover:bg-bg-surface'

/**
 * Figma PeriodSwitcher: 위는 주간/월간(MbtiPicker와 같은 채움 스타일), 아래는 ‹ 기간 ›.
 * 지금 기간이면 ›, 12개월 전이면 ‹를 누를 수 없다.
 */
export default function RankingPeriodSwitcher({ period, label, canPrevious, canNext, onPeriodChange, onPrevious, onNext, className = '' }: Props) {
  return (
    <div className={`space-y-3 ${className}`}>
      <div className="grid grid-cols-2 gap-1.5" role="group" aria-label="랭킹 기간">
        {periods.map((p) => {
          const active = p.value === period
          return (
            <button
              key={p.value}
              type="button"
              aria-pressed={active}
              onClick={() => onPeriodChange(p.value)}
              className={`h-9 rounded-lg text-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand ${
                active ? 'bg-bg-brand font-bold text-text-on-brand' : 'bg-bg-muted font-medium text-text-secondary hover:bg-bg-subtle'
              }`}
            >
              {p.label}
            </button>
          )
        })}
      </div>
      <div className="flex items-center justify-between gap-3">
        <button type="button" onClick={onPrevious} disabled={!canPrevious} aria-label="이전 기간" className={navClass}>
          ‹
        </button>
        <p className="flex min-w-0 flex-col items-center text-center" aria-live="polite">
          <span className="text-sm font-bold">{label.title}</span>
          <span className="text-xs text-text-tertiary">{label.subtitle}</span>
        </p>
        <button type="button" onClick={onNext} disabled={!canNext} aria-label="다음 기간" className={navClass}>
          ›
        </button>
      </div>
    </div>
  )
}
