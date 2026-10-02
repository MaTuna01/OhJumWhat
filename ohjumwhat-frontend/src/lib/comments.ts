import type { PollDetail } from '../queries/polls.ts'

/** 댓글 최대 글자 수. 서버(MenuCommentService)처럼 글자(코드 포인트) 수로 센다. */
export const COMMENT_MAX = 200

/** 메뉴 카드의 댓글 버튼이 가리키는 펼친 댓글 영역의 id */
export const commentsPanelId = (optionId: number) => `comments-${optionId}`

/** 앞뒤 공백을 뺀 글자 수(이모지도 한 글자) */
export function commentLength(text: string): number {
  return [...text.trim()].length
}

/**
 * 카드의 댓글 버튼 글자. 댓글이 있으면 「댓글 N」, 없으면 「댓글 달기」.
 * 마감된 투표는 읽기만 하므로 댓글이 없으면 null(버튼을 숨긴다).
 */
export function commentToggleLabel(count: number, readOnly: boolean): string | null {
  if (count > 0) return `댓글 ${count}`
  return readOnly ? null : '댓글 달기'
}

/** 댓글을 쓰거나 지운 뒤 투표 상세의 댓글 수를 맞춘다(다음 폴링을 기다리지 않게). */
export function withCommentCount(detail: PollDetail, optionId: number, count: number): PollDetail {
  return {
    ...detail,
    options: detail.options.map((option) => (option.id === optionId ? { ...option, commentCount: count } : option)),
  }
}
