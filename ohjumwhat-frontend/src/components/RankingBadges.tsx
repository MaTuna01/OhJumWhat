/** Figma AdoptedBadge: 마감 결과에서 채택된 메뉴 카드에 붙는다(투표 하나에 하나). */
export function AdoptedBadge() {
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-bg-brand-muted px-2 py-0.5 text-xs font-medium text-text-brand-strong">
      <span aria-hidden>👑</span>
      채택
    </span>
  )
}

/**
 * Figma MakerBadge: 지난달 월간 1위(공동이면 모두)에게 이번 달 동안 붙는 「이달의 메뉴 메이커」.
 * Full은 멤버 프로필 모달, Compact(🏅만)는 멤버 목록의 이름 옆이다.
 */
export function MakerBadge({ compact = false }: { compact?: boolean }) {
  if (compact) {
    return (
      <span title="이달의 메뉴 메이커" className="inline-flex shrink-0 items-center rounded-full bg-bg-warning-soft px-1.5 py-0.5 text-xs">
        <span aria-hidden>🏅</span>
        <span className="sr-only">이달의 메뉴 메이커</span>
      </span>
    )
  }
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-bg-warning-soft px-2 py-0.5 text-xs font-medium text-text-warning">
      <span aria-hidden>🏅</span>
      이달의 메뉴 메이커
    </span>
  )
}
