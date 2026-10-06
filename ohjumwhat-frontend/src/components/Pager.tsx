import { pagerLabel } from '../lib/guestbook.ts'

type Props = {
  /** 0쪽부터 센다 */
  page: number
  totalPages: number
  onChange: (page: number) => void
  /** 다음 쪽을 받는 중에는 두 번 넘기지 않게 막는다 */
  busy?: boolean
}

const arrowClass =
  'grid size-8 place-items-center rounded-lg text-lg leading-none text-text-primary hover:bg-bg-subtle focus-visible:outline-2 focus-visible:outline-border-brand disabled:cursor-not-allowed disabled:text-text-placeholder disabled:hover:bg-transparent'

/** Figma Pager(First·Middle·Last): 「‹ 1 / 3 ›」. 첫 쪽·끝 쪽에서 화살표를 막고, 1쪽뿐이면 그리지 않는다. */
export default function Pager({ page, totalPages, onChange, busy = false }: Props) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="쪽 넘기기" className="flex items-center justify-center gap-3">
      <button type="button" aria-label="이전 페이지" disabled={page <= 0 || busy} onClick={() => onChange(page - 1)} className={arrowClass}>
        ‹
      </button>
      <span aria-live="polite" className="min-w-12 text-center text-sm text-text-secondary tabular-nums">
        {pagerLabel(page, totalPages)}
      </span>
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={page >= totalPages - 1 || busy}
        onClick={() => onChange(page + 1)}
        className={arrowClass}
      >
        ›
      </button>
    </nav>
  )
}
