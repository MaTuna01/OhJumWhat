import type { Person, PollDetail } from '../queries/polls.ts'
import { confirmedTeams } from './pollDetail.ts'
import { formatClock, formatMonthDay } from './time.ts'

const names = (people: Person[]) => people.map((p) => p.name).join(', ')

/**
 * 마감 결과를 메신저에 붙일 글로 만든다(Figma 05b-S).
 * 예) [오점왓] 9월 30일 점심 결과 · 2팀
 *     · 돈까스 3명: 김오점, 김철수, 정하늘
 *       지도 https://naver.me/…
 *     패스: 한가람
 *     응답 안 함: 윤서준
 *     https://www.ohjumwhat.cloud/orgs/1/polls/3
 */
export function resultText(poll: PollDetail, url: string): string {
  const teams = confirmedTeams(poll)
  const lines = [
    `[오점왓] ${formatMonthDay(poll.closesAt)} ${poll.title} 결과 · ${teams.length > 0 ? `${teams.length}팀` : '참여한 메뉴 없음'}`,
    `${formatClock(poll.closesAt)} 마감`,
  ]
  for (const team of teams) {
    lines.push(`· ${team.name} ${team.voters.length}명: ${names(team.voters)}`)
    if (team.link) lines.push(`  지도 ${team.link}`)
  }
  if (poll.passed.length > 0) lines.push(`패스: ${names(poll.passed)}`)
  if (poll.nonRespondents.length > 0) lines.push(`응답 안 함: ${names(poll.nonRespondents)}`)
  lines.push(url)
  return lines.join('\n')
}

/** 클립보드에 복사한다. 권한이 없거나 오래된 브라우저면 숨긴 입력창으로 한 번 더 시도한다. */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    try {
      return document.execCommand('copy')
    } catch {
      return false
    } finally {
      area.remove()
    }
  }
}
