import { formatPollDay, kstDayKey } from './time.ts'

/** 투표가 열린 날(한국)이 오늘인지. poll_date는 opens_at의 한국 날짜와 같다 */
export function openedToday(opensAt: string, now: number): boolean {
  return kstDayKey(opensAt) === kstDayKey(now)
}

/** Figma 05b-D: 마감된 투표 상세의 제목 위 날짜 줄. "오늘 마감된 투표" / "지난 투표 · 9월 28일 (일)" */
export function closedPollDayLabel(opensAt: string, now: number): string {
  return openedToday(opensAt, now) ? '오늘 마감된 투표' : `지난 투표 · ${formatPollDay(kstDayKey(opensAt), now)}`
}

/**
 * 오늘 투표를 진행 중과 마감으로 나눈다. 같은 상태끼리는 서버 순서(열린 시각 순)를 지킨다.
 * 서버 상태는 15초마다 받으므로 마감 시각이 지난 투표는 바로 마감으로 옮기고,
 * 자정을 넘겨 다시 받기 전까지 남아 있는 어제 투표는 뺀다(마감 시각은 늘 투표 날짜 안이다).
 */
export function splitTodayPolls<T extends { status: 'OPEN' | 'CLOSED'; closesAt: string }>(polls: T[], now: number): { open: T[]; closed: T[] } {
  const today = polls.filter((p) => kstDayKey(p.closesAt) === kstDayKey(now))
  const isOpen = (p: T) => p.status === 'OPEN' && Date.parse(p.closesAt) > now
  return { open: today.filter(isOpen), closed: today.filter((p) => !isOpen(p)) }
}
