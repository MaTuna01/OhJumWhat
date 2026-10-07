import { describe, expect, it } from 'vitest'
import type { Me } from '../queries/me.ts'
import type { PollDetail } from '../queries/polls.ts'
import { applyVote, confirmedTeams } from './pollDetail.ts'

const me: Me = { id: 1, name: '김철수', nickname: null, googleName: '김철수', email: 'kim@example.com', profileImageUrl: null, googleProfileImageUrl: null, customPhoto: false, bio: null, foodTags: [], details: null, lastVisitedOrgId: 1, admin: false, sanctions: [] }
const lee = { userId: 2, name: '이영희', profileImageUrl: null }
const base: PollDetail = {
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
    { id: 100, name: '김치찌개', link: null, placeName: null, placeAddress: null, kakaoPlaceId: null, placeQuery: null, createdBy: lee, voters: [lee], mine: false, deletable: false, commentCount: 0 },
    { id: 101, name: '쌀국수', link: 'https://naver.me/x', placeName: null, placeAddress: null, kakaoPlaceId: null, placeQuery: null, createdBy: { userId: 1, name: '김철수', profileImageUrl: null }, voters: [], mine: true, deletable: true, commentCount: 2 },
  ],
  myResponse: 'NONE',
  myOptionId: null,
  passed: [],
  nonRespondents: [{ userId: 1, name: '김철수', profileImageUrl: null }],
  soloOptionIds: [100],
  adoption: null,
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
    const next = applyVote(applyVote(base, me, 101), me, 'PASS')
    expect(next.options[1].voters).toEqual([])
    expect(next.options[1].deletable).toBe(true)
    expect(next.passed.map((p) => p.name)).toEqual(['김철수'])
    expect(next.myResponse).toBe('PASS')
    expect(next.myOptionId).toBeNull()
  })

  it('참여를 취소하면 명단에서 빠지고 처음처럼 미응답으로 돌아간다', () => {
    const next = applyVote(applyVote(base, me, 101), me, 'NONE')
    expect(next.options[1].voters).toEqual([])
    expect(next.options[1].deletable).toBe(true)
    expect(next.soloOptionIds).toEqual([100])
    expect(next.nonRespondents.map((p) => p.name)).toEqual(['김철수'])
    expect(next.passed).toEqual([])
    expect(next.myResponse).toBe('NONE')
    expect(next.myOptionId).toBeNull()
  })

  it('패스를 취소하면 패스 목록에서 빠지고 미응답으로 돌아간다', () => {
    const next = applyVote(applyVote(base, me, 'PASS'), me, 'NONE')
    expect(next.passed).toEqual([])
    expect(next.nonRespondents.map((p) => p.name)).toEqual(['김철수'])
    expect(next.myResponse).toBe('NONE')
  })

  it('미응답에서 취소해도 내가 미응답에 한 번만 있다', () => {
    const next = applyVote(base, me, 'NONE')
    expect(next.nonRespondents.map((p) => p.name)).toEqual(['김철수'])
    expect(next.options[0].voters.map((v) => v.name)).toEqual(['이영희'])
  })
})

describe('confirmedTeams', () => {
  it('참여자가 있는 메뉴만 인원 많은 순으로', () => {
    const detail = applyVote(base, me, 100)
    expect(confirmedTeams(detail).map((o) => o.name)).toEqual(['김치찌개'])
  })
})
