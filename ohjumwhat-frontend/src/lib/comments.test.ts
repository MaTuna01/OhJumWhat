import { describe, expect, it } from 'vitest'
import type { PollDetail } from '../queries/polls.ts'
import { commentLength, commentToggleLabel, withCommentCount } from './comments.ts'

describe('commentLength', () => {
  it('앞뒤 공백을 빼고 글자(코드 포인트) 수로 센다', () => {
    expect(commentLength('  웨이팅 길어요  ')).toBe(7)
    expect(commentLength('😋😋')).toBe(2)
    expect(commentLength('   ')).toBe(0)
  })
})

describe('commentToggleLabel', () => {
  it('댓글이 있으면 개수, 없으면 진행 중에만 「댓글 달기」', () => {
    expect(commentToggleLabel(2, false)).toBe('댓글 2')
    expect(commentToggleLabel(2, true)).toBe('댓글 2')
    expect(commentToggleLabel(0, false)).toBe('댓글 달기')
    expect(commentToggleLabel(0, true)).toBeNull()
  })
})

describe('withCommentCount', () => {
  const option = { link: null, placeName: null, placeAddress: null, kakaoPlaceId: null, placeQuery: null, createdBy: null, voters: [], mine: false, deletable: false }
  const detail: PollDetail = {
    id: 10,
    organizationId: 1,
    title: '점심',
    status: 'OPEN',
    opensAt: '2026-09-30T02:00:00Z',
    closesAt: '2026-09-30T02:50:00Z',
    chatClosesAt: '2026-09-30T03:50:00Z',
    scheduled: false,
    memberCount: 2,
    options: [
      { ...option, id: 100, name: '김치찌개', commentCount: 1 },
      { ...option, id: 101, name: '돈가스', commentCount: 0 },
    ],
    myResponse: 'NONE',
    myOptionId: null,
    passed: [],
    nonRespondents: [],
    soloOptionIds: [],
  }

  it('그 메뉴의 댓글 수만 바꾼다', () => {
    const next = withCommentCount(detail, 101, 3)
    expect(next.options.map((o) => o.commentCount)).toEqual([1, 3])
    expect(detail.options[1].commentCount).toBe(0)
  })
})
