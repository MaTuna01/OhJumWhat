import { describe, expect, it } from 'vitest'
import type { Me } from '../queries/me.ts'
import type { PollDetail } from '../queries/polls.ts'
import { applyVote, confirmedTeams } from './pollDetail.ts'

const me: Me = { id: 1, name: '김철수', nickname: null, googleName: '김철수', email: 'kim@example.com', profileImageUrl: null, googleProfileImageUrl: null, customPhoto: false, lastVisitedOrgId: 1, admin: false }
const lee = { userId: 2, name: '이영희', profileImageUrl: null }
const base: PollDetail = {
  id: 10,
  organizationId: 1,
  title: '점심',
  status: 'OPEN',
  opensAt: '2026-09-30T02:00:00Z',
  closesAt: '2026-09-30T02:50:00Z',
  scheduled: false,
  memberCount: 2,
  options: [
    { id: 100, name: '김치찌개', link: null, placeName: null, placeAddress: null, kakaoPlaceId: null, placeQuery: null, createdBy: lee, voters: [lee], mine: false, deletable: false },
    { id: 101, name: '쌀국수', link: 'https://naver.me/x', placeName: null, placeAddress: null, kakaoPlaceId: null, placeQuery: null, createdBy: { userId: 1, name: '김철수', profileImageUrl: null }, voters: [], mine: true, deletable: true },
  ],
  myResponse: 'NONE',
  myOptionId: null,
  passed: [],
  nonRespondents: [{ userId: 1, name: '김철수', profileImageUrl: null }],
  soloOptionIds: [100],
}

describe('applyVote', () => {
  it('메뉴에 참여하면 명단에 들어가고 미응답에서 빠진다', () => {
    const next = applyVote(base, me, 100)
    expect(next.options[0].voters.map((v) => v.name)).toEqual(['이영희', '김철수'])
    expect(next.myResponse).toBe('OPTION')
    expect(next.myOptionId).toBe(100)
    expect(next.nonRespondents).toEqual([])
    expect(next.soloOptionIds).toEqual([])
  })

  it('다른 메뉴로 옮기면 이전 명단에서 빠지고, 내 메뉴에 참여하면 삭제할 수 없게 된다', () => {
    const next = applyVote(applyVote(base, me, 100), me, 101)
    expect(next.options[0].voters.map((v) => v.name)).toEqual(['이영희'])
    expect(next.options[1].voters.map((v) => v.name)).toEqual(['김철수'])
    expect(next.options[1].deletable).toBe(false)
    expect(next.soloOptionIds).toEqual([100, 101])
  })

  it('패스하면 모든 명단에서 빠지고 패스 목록에 들어간다', () => {
    const next = applyVote(applyVote(base, me, 101), me, null)
    expect(next.options[1].voters).toEqual([])
    expect(next.options[1].deletable).toBe(true)
    expect(next.passed.map((p) => p.name)).toEqual(['김철수'])
    expect(next.myResponse).toBe('PASS')
    expect(next.myOptionId).toBeNull()
  })
})

describe('confirmedTeams', () => {
  it('참여자가 있는 메뉴만 인원 많은 순으로', () => {
    const detail = applyVote(base, me, 100)
    expect(confirmedTeams(detail).map((o) => o.name)).toEqual(['김치찌개'])
  })
})
