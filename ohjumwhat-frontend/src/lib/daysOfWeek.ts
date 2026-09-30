// 정기 투표 요일 비트마스크: 월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64 (백엔드 PollSchedule과 같다)
export const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'] as const

export const WEEKDAYS = 0b0011111
export const WEEKEND = 0b1100000
export const EVERY_DAY = 0b1111111

/** 비트마스크 → 요일 인덱스(월=0 … 일=6) */
export function daysFromMask(mask: number): number[] {
  return DAY_LABELS.map((_, i) => i).filter((i) => (mask & (1 << i)) !== 0)
}

export function toggleDay(mask: number, index: number): number {
  return mask ^ (1 << index)
}

/** "평일", "주말", "매일", 그 밖에는 "월·수·금" */
export function daysLabel(mask: number): string {
  if (mask === WEEKDAYS) return '평일'
  if (mask === WEEKEND) return '주말'
  if (mask === EVERY_DAY) return '매일'
  return daysFromMask(mask)
    .map((i) => DAY_LABELS[i])
    .join('·')
}
