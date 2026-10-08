import { describe, expect, it } from 'vitest'
import { closedPollDayLabel, openedToday, splitTodayPolls } from './pollStatus.ts'

// 2026-09-30(수) 13:00 한국 시간
const now = new Date('2026-09-30T13:00:00+09:00').getTime()

describe('closedPollDayLabel', () => {
  it('오늘 열린 투표는 「오늘 마감된 투표」', () => {
    expect(closedPollDayLabel('2026-09-30T02:00:00Z', now)).toBe('오늘 마감된 투표')
    expect(openedToday('2026-09-30T02:00:00Z', now)).toBe(true)
  })

  it('그 전 투표는 「지난 투표 · 날짜」', () => {
    expect(closedPollDayLabel('2026-09-29T02:00:00Z', now)).toBe('지난 투표 · 어제')
    expect(closedPollDayLabel('2026-09-26T08:00:00Z', now)).toBe('지난 투표 · 9월 26일 (토)')
  })

  it('한국 날짜로 판단한다(UTC로는 전날인 아침 투표)', () => {
    // 2026-09-30 08:00 KST = 2026-09-29T23:00Z
    expect(openedToday('2026-09-29T23:00:00Z', now)).toBe(true)
  })
})

describe('splitTodayPolls', () => {
  const poll = (id: number, status: 'OPEN' | 'CLOSED', closesAt: string) => ({ id, status, closesAt })

  it('진행 중과 마감으로 나누고 순서를 지킨다', () => {
    const polls = [
      poll(1, 'CLOSED', '2026-09-30T02:50:00Z'),
      poll(2, 'OPEN', '2026-09-30T08:00:00Z'),
      poll(3, 'CLOSED', '2026-09-30T03:00:00Z'),
      poll(4, 'OPEN', '2026-09-30T09:00:00Z'),
    ]
    const { open, closed } = splitTodayPolls(polls, now)
    expect(open.map((p) => p.id)).toEqual([2, 4])
    expect(closed.map((p) => p.id)).toEqual([1, 3])
  })

  it('서버가 아직 진행 중이라고 해도 마감 시각이 지났으면 마감으로 본다', () => {
    // now = 13:00 KST = 04:00Z
    const { open, closed } = splitTodayPolls([poll(1, 'OPEN', '2026-09-30T03:59:00Z')], now)
    expect(open).toEqual([])
    expect(closed.map((p) => p.id)).toEqual([1])
  })

  it('자정을 넘겨 남아 있는 어제 투표는 뺀다', () => {
    const { open, closed } = splitTodayPolls([poll(1, 'CLOSED', '2026-09-29T02:50:00Z')], now)
    expect(open).toEqual([])
    expect(closed).toEqual([])
  })
})
