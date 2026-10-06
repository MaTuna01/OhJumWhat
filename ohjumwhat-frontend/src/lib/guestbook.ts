import type { GuestbookEntry, GuestbookWarning } from '../queries/guestbook.ts'

/** 방명록 글 최대 글자 수(서버 GuestbookService와 같다). 한 줄이다. */
export const GUESTBOOK_MAX = 100

/** 한 쪽에 보이는 글 수(서버와 같다) */
export const GUESTBOOK_PAGE_SIZE = 10

/** 도배 방지: 한 사람이 모든 방명록을 합쳐 이만큼에 한 번 쓴다(서버 429와 같다). */
export const GUESTBOOK_COOLDOWN_MS = 5_000

/** 관리자가 제한한 글 대신 모두에게 보여주는 문구 */
export const RESTRICTED_TEXT = '관리자에 의해 제한된 게시글입니다'

/** 앞뒤 공백을 뺀 글자(코드 포인트) 수(이모지도 한 글자) */
export function guestbookLength(text: string): number {
  return [...text.trim()].length
}

/** 다시 쓸 수 있을 때까지 남은 초(올림, 0 이상) */
export function cooldownSecondsLeft(until: number, now: number): number {
  return Math.max(0, Math.ceil((until - now) / 1000))
}

/** 쪽 넘기기 가운데 글자: 0쪽부터 세는 쪽 번호를 「1 / 3」으로 */
export function pagerLabel(page: number, totalPages: number): string {
  return `${page + 1} / ${totalPages}`
}

/** 주인이 아직 보지 않은 글(NEW): 마지막으로 본 시각 뒤에 쓰였다. 본 적이 없으면(null) 모두 새 글이다. */
export function isNewEntry(createdAt: string, seenAt: string | null): boolean {
  return seenAt === null || Date.parse(createdAt) > Date.parse(seenAt)
}

/** 가장 최근 글의 시각(「봤음」으로 보낼 값). 글이 없으면 null */
export function newestCreatedAt(entries: Pick<GuestbookEntry, 'createdAt'>[]): string | null {
  return latest(entries.map((entry) => entry.createdAt))
}

/** 경고 중 가장 최근에 제한된 시각(「확인」으로 보낼 값). 경고가 없으면 null */
export function latestRestrictedAt(warnings: Pick<GuestbookWarning, 'restrictedAt'>[]): string | null {
  return latest(warnings.map((warning) => warning.restrictedAt))
}

function latest(values: string[]): string | null {
  let found: string | null = null
  for (const value of values) {
    if (found === null || Date.parse(value) > Date.parse(found)) found = value
  }
  return found
}

/** 새 방명록 수 글자(99+). 없으면 빈 글자 */
export function newGuestbookLabel(count: number): string {
  if (count <= 0) return ''
  return count > 99 ? '99+' : String(count)
}

/**
 * 「방명록」 옆에 따로 보이는 글 수(Figma ProfileTabs 「수 보이기」, 마이페이지 제목).
 * 글이 없거나 아직 모르면 null이라 「방명록」만 보인다.
 */
export function guestbookTabLabel(total: number | undefined): string | null {
  return total ? String(total) : null
}

/** 쓴 사람 이름: 강제 탈퇴했으면 「탈퇴한 사용자」 */
export function authorLabel(entry: Pick<GuestbookEntry, 'author'>): string {
  return entry.author?.name ?? '탈퇴한 사용자'
}
