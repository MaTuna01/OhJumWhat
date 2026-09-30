import { describe, expect, it } from 'vitest'
import { EVERY_DAY, WEEKDAYS, WEEKEND, daysFromMask, daysLabel, toggleDay } from './daysOfWeek.ts'

describe('daysOfWeek', () => {
  it('평일은 31, 주말은 96, 매일은 127 (백엔드와 같은 비트마스크)', () => {
    expect([WEEKDAYS, WEEKEND, EVERY_DAY]).toEqual([31, 96, 127])
  })

  it('비트마스크를 요일 인덱스로 바꾼다', () => {
    expect(daysFromMask(1 + 4 + 16)).toEqual([0, 2, 4])
    expect(daysFromMask(64)).toEqual([6])
  })

  it('요일을 켜고 끈다', () => {
    expect(toggleDay(WEEKDAYS, 4)).toBe(15)
    expect(toggleDay(15, 4)).toBe(WEEKDAYS)
  })

  it('자주 쓰는 조합은 이름으로, 나머지는 요일을 이어서 보여준다', () => {
    expect(daysLabel(31)).toBe('평일')
    expect(daysLabel(96)).toBe('주말')
    expect(daysLabel(127)).toBe('매일')
    expect(daysLabel(1 + 4 + 16)).toBe('월·수·금')
    expect(daysLabel(16)).toBe('금')
  })
})
