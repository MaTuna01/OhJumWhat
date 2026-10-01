import { describe, expect, it } from 'vitest'
import type { Person, PollDetail, PollOption } from '../queries/polls.ts'
import { resultText } from './share.ts'

const person = (userId: number, name: string): Person => ({ userId, name, profileImageUrl: null })
const option = (id: number, name: string, voters: Person[], link: string | null = null): PollOption => ({
  id,
  name,
  link,
  createdBy: null,
  voters,
  mine: false,
  deletable: false,
})

const base: PollDetail = {
  id: 3,
  organizationId: 1,
  title: '점심',
  status: 'CLOSED',
  opensAt: '2026-09-30T02:00:00Z',
  closesAt: '2026-09-30T02:50:00Z',
  scheduled: true,
  memberCount: 6,
  options: [],
  myResponse: 'NONE',
  myOptionId: null,
  passed: [],
  nonRespondents: [],
  soloOptionIds: [],
}

describe('resultText', () => {
  it('확정 팀을 인원 많은 순으로, 지도 링크·패스·미응답·주소를 붙인다', () => {
    const poll: PollDetail = {
      ...base,
      options: [
        option(10, '김치찌개', [person(4, '박민수')]),
        option(11, '돈까스', [person(1, '김오점'), person(2, '김철수'), person(3, '정하늘')], 'https://naver.me/abc'),
        option(12, '냉면', []),
      ],
      passed: [person(5, '한가람')],
      nonRespondents: [person(6, '윤서준')],
    }
    expect(resultText(poll, 'https://www.ohjumwhat.cloud/orgs/1/polls/3')).toBe(
      [
        '[오점왓] 9월 30일 점심 결과 · 2팀',
        '오전 11:50 마감',
        '· 돈까스 3명: 김오점, 김철수, 정하늘',
        '  지도 https://naver.me/abc',
        '· 김치찌개 1명: 박민수',
        '패스: 한가람',
        '응답 안 함: 윤서준',
        'https://www.ohjumwhat.cloud/orgs/1/polls/3',
      ].join('\n'),
    )
  })

  it('참여한 메뉴가 없으면 그렇게 적는다', () => {
    expect(resultText(base, 'u').split('\n')[0]).toBe('[오점왓] 9월 30일 점심 결과 · 참여한 메뉴 없음')
  })
})
