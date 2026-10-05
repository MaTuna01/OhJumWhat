import type { InfiniteData } from '@tanstack/react-query'
import type { Letter, LetterPage } from '../queries/letters.ts'

/** 쪽지 본문 최대 글자 수(서버 LetterService와 같다) */
export const LETTER_MAX = 500

/** 신고 사유 최대 글자 수 */
export const REPORT_REASON_MAX = 100

/** 글자 수는 서버처럼 코드 포인트로 센다(이모지도 한 글자). 앞뒤 공백과 \r은 서버가 지운다. */
export function letterLength(body: string): number {
  return [...body.replace(/\r\n/g, '\n').trim()].length
}

/** 상대 이름: 숨겼으면 「익명」, 탈퇴했으면 「탈퇴한 사용자」 */
export function counterpartLabel(letter: Pick<Letter, 'counterpart' | 'counterpartHidden'>): string {
  if (letter.counterpartHidden) return '익명'
  return letter.counterpart?.name ?? '탈퇴한 사용자'
}

/** 조직 이름(없어졌으면 「삭제된 조직」) */
export function organizationLabel(letter: Pick<Letter, 'organization'>): string {
  return letter.organization?.name ?? '삭제된 조직'
}

/** 쪽지함 캐시에서 한 통을 바꾼다(없으면 그대로). */
export function updateLetterInPages(
  data: InfiniteData<LetterPage> | undefined,
  letterId: number,
  update: (letter: Letter) => Letter,
): InfiniteData<LetterPage> | undefined {
  if (!data) return data
  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      letters: page.letters.map((letter) => (letter.id === letterId ? update(letter) : letter)),
    })),
  }
}

/** 받은 쪽지 탭의 안 읽은 수 라벨(99+) */
export function unreadLetterLabel(count: number): string {
  if (count <= 0) return ''
  return count > 99 ? '99+' : String(count)
}
