import { describe, expect, it } from 'vitest'
import { daysAgo, defaultCloseTime, formatAgo, formatClock, formatDate, formatDay, formatDayTime, formatEatenDay, formatHhmm, formatRemaining, formatTimeRange, toKstHhmm } from './time.ts'

// 2026-09-30 11:00 (한국 시간) = 02:00Z
const at = (kst: string) => new Date(`2026-09-30T${kst}:00+09:00`).getTime()

describe('formatClock', () => {
  it('한국 시간으로 오전/오후를 붙여 보여준다', () => {
    expect(formatClock('2026-09-30T02:50:00Z')).toBe('오전 11:50')
    expect(formatClock('2026-09-30T09:05:00Z')).toBe('오후 6:05')
  })
})

describe('formatHhmm', () => {
  it('HH:mm을 오전/오후 표기로', () => {
    expect(formatHhmm('11:00')).toBe('오전 11:00')
    expect(formatHhmm('17:40')).toBe('오후 5:40')
  })
})

describe('formatTimeRange', () => {
  it('오전·오후가 같으면 뒤쪽은 생략한다', () => {
    expect(formatTimeRange('11:00', '11:50')).toBe('오전 11:00 ~ 11:50')
    expect(formatTimeRange('11:30', '12:10')).toBe('오전 11:30 ~ 오후 12:10')
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

describe('formatDay', () => {
  const now = at('11:00')
  it('오늘·어제·올해·그 전을 구분한다(한국 날짜 기준)', () => {
    expect(formatDay('2026-09-30T00:30:00+09:00', now)).toBe('오늘')
    expect(formatDay('2026-09-29T15:30:00Z', now)).toBe('오늘') // 9/30 00:30 KST
    expect(formatDay('2026-09-29', now)).toBe('어제')
    expect(formatDay('2026-09-28T03:00:00Z', now)).toBe('9월 28일')
    expect(formatDay('2025-12-31', now)).toBe('2025년 12월 31일')
  })
})

describe('formatDate / formatDayTime / formatAgo', () => {
  const now = at('11:00')
  it('관리자 콘솔의 날짜·시각 표기', () => {
    expect(formatDate('2026-09-28T03:00:00Z')).toBe('2026년 9월 28일')
    expect(formatDayTime('2026-09-30T00:12:00Z', now)).toBe('오늘 오전 9:12')
    expect(formatAgo('2026-09-30T01:59:40Z', now)).toBe('방금')
    expect(formatAgo('2026-09-30T01:55:00Z', now)).toBe('5분 전')
    expect(formatAgo('2026-09-29T23:00:00Z', now)).toBe('3시간 전')
    expect(formatAgo('2026-09-28T06:00:00Z', now)).toBe('9월 28일 오후 3:00')
  })
})

describe('formatEatenDay', () => {
  it('오늘·어제·N일 전, 30일이 넘으면 날짜', () => {
    expect(daysAgo('2026-09-30', at('00:10'))).toBe(0)
    expect(formatEatenDay('2026-09-30', at('23:50'))).toBe('오늘')
    expect(formatEatenDay('2026-09-29', at('11:00'))).toBe('어제')
    expect(formatEatenDay('2026-09-18', at('11:00'))).toBe('12일 전')
    expect(formatEatenDay('2026-08-01', at('11:00'))).toBe('8월 1일')
    expect(formatEatenDay('2025-12-24', at('11:00'))).toBe('2025년 12월 24일')
  })
})
