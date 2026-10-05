import type { Me } from '../queries/me.ts'
import type { Person, PollDetail } from '../queries/polls.ts'

/** 내 응답 바꾸기: 메뉴에 참여(optionId), 오늘은 패스, 응답 취소(미응답으로) */
export type VoteChoice = number | 'PASS' | 'NONE'

/**
 * 참여·패스·취소를 서버 응답 전에 화면에 먼저 반영한 투표 상세.
 * 서버의 PollService.detail과 같은 규칙으로 명단·패스·미응답·1인 메뉴·삭제 가능 여부를 다시 계산한다.
 */
export function applyVote(detail: PollDetail, me: Me, choice: VoteChoice): PollDetail {
  const person: Person = { userId: me.id, name: me.name, profileImageUrl: me.profileImageUrl }
  const without = (people: Person[]) => people.filter((p) => p.userId !== me.id)
  const optionId = typeof choice === 'number' ? choice : null

  const options = detail.options.map((option) => {
    const voters = option.id === optionId ? [...without(option.voters), person] : without(option.voters)
    return { ...option, voters, deletable: option.mine && voters.length === 0 && detail.status === 'OPEN' }
  })
  return {
    ...detail,
    options,
    myResponse: choice === 'NONE' ? 'NONE' : choice === 'PASS' ? 'PASS' : 'OPTION',
    myOptionId: optionId,
    passed: choice === 'PASS' ? [...without(detail.passed), person] : without(detail.passed),
    // 서버는 미응답을 가입 순서로 준다. 취소하면 일단 끝에 두고, 응답이 오면 서버 순서로 맞춰진다.
    nonRespondents: choice === 'NONE' ? [...without(detail.nonRespondents), person] : without(detail.nonRespondents),
    soloOptionIds: options.filter((o) => o.voters.length === 1).map((o) => o.id),
  }
}

/** 마감 후 결과: 참여자가 있는 메뉴를 인원 많은 순으로(같으면 먼저 올라온 순) */
export function confirmedTeams(detail: PollDetail) {
  return detail.options
    .filter((o) => o.voters.length > 0)
    .sort((a, b) => b.voters.length - a.voters.length || a.id - b.id)
}
