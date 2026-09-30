import { describe, expect, it } from 'vitest'
import { defaultCloseTime, formatClock, formatRemaining, toKstHhmm } from './time.ts'

// 2026-09-30 11:00 (한국 시간) = 02:00Z
const at = (kst: string) => new Date(`2026-09-30T${kst}:00+09:00`).getTime()

describe('formatClock', () => {
  it('한국 시간으로 오전/오후를 붙여 보여준다', () => {
    expect(formatClock('2026-09-30T02:50:00Z')).toBe('오전 11:50')
    expect(formatClock('2026-09-30T09:05:00Z')).toBe('오후 6:05')
  })
})

describe('formatRemaining', () => {
  const closesAt = '2026-09-30T02:50:00Z'
  it('남은 분·시간을 보여준다', () => {
    expect(formatRemaining(closesAt, at('11:18'))).toBe('32분 남음')
    expect(formatRemaining(closesAt, at('10:45'))).toBe('1시간 5분 남음')
    expect(formatRemaining(closesAt, at('10:50'))).toBe('1시간 남음')
    expect(formatRemaining(closesAt, at('11:49') + 30_000)).toBe('1분 안에 마감')
  })
  it('마감이 지났으면 null', () => {
    expect(formatRemaining(closesAt, at('11:50'))).toBeNull()
  })
})

describe('defaultCloseTime', () => {
  it('30분 뒤를 10분 단위로 올린다', () => {
    expect(toKstHhmm(new Date(at('11:00')))).toBe('11:00')
    expect(defaultCloseTime(at('11:00'))).toBe('11:30')
    expect(defaultCloseTime(at('11:03'))).toBe('11:40')
  })
  it('자정을 넘기면 23:50', () => {
    expect(defaultCloseTime(new Date('2026-09-30T23:45:00+09:00').getTime())).toBe('23:50')
  })
})
