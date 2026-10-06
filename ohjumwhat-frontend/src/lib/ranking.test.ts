import { describe, expect, it } from 'vitest'
import { addDays, adoptedDayText, earliestDay, gapToFirst, hasNext, hasPrevious, lastMonthDay, medalOf, parseRankingView, periodLabel, periodRange, rangeText, rankingSearch } from './ranking.ts'

// 오늘은 2026-10-07(수)
const today = '2026-10-07'

describe('periodRange', () => {
  it('주간은 월요일부터 일요일까지다', () => {
    expect(periodRange('WEEK', '2026-10-07')).toEqual({ from: '2026-10-05', to: '2026-10-11' })
    expect(periodRange('WEEK', '2026-10-05')).toEqual({ from: '2026-10-05', to: '2026-10-11' })
    expect(periodRange('WEEK', '2026-10-04')).toEqual({ from: '2026-09-28', to: '2026-10-04' })
    expect(periodRange('WEEK', '2027-01-01')).toEqual({ from: '2026-12-28', to: '2027-01-03' })
  })
  it('월간은 1일부터 말일까지다', () => {
    expect(periodRange('MONTH', '2026-10-07')).toEqual({ from: '2026-10-01', to: '2026-10-31' })
    expect(periodRange('MONTH', '2028-02-10')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
  })
})

describe('지난 기록 범위', () => {
  it('이번 달 1일의 12개월 전이 들어 있는 기간까지 본다', () => {
    expect(earliestDay(today)).toBe('2025-10-01')
    expect(earliestDay('2026-01-31')).toBe('2025-01-01')
    expect(hasPrevious(periodRange('MONTH', '2025-11-15'), today)).toBe(true)
    expect(hasPrevious(periodRange('MONTH', '2025-10-15'), today)).toBe(false)
    expect(hasPrevious(periodRange('WEEK', '2025-10-06'), today)).toBe(true)
    expect(hasPrevious(periodRange('WEEK', '2025-10-01'), today)).toBe(false)
  })
  it('지금 기간이면 다음이 없다', () => {
    expect(hasNext(periodRange('WEEK', today), today)).toBe(false)
    expect(hasNext(periodRange('WEEK', '2026-10-04'), today)).toBe(true)
    expect(hasNext(periodRange('MONTH', today), today)).toBe(false)
  })
  it('날짜를 더하고 지난달 1일을 찾는다', () => {
    expect(addDays('2026-10-01', -1)).toBe('2026-09-30')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(lastMonthDay(today)).toBe('2026-09-01')
    expect(lastMonthDay('2026-01-15')).toBe('2025-12-01')
  })
})

describe('periodLabel', () => {
  it('이번 주·지난주는 이름과 날짜, 그 전은 날짜와 몇 주 전인지', () => {
    expect(periodLabel('WEEK', periodRange('WEEK', today), today)).toEqual({
      name: '이번 주',
      title: '이번 주',
      subtitle: '10월 5일 – 10월 11일',
      cardTitle: '이번 주 메뉴 메이커',
      summary: '이번 주 · 10월 5일 – 10월 11일',
    })
    expect(periodLabel('WEEK', periodRange('WEEK', '2026-10-01'), today)).toMatchObject({ name: '지난주', title: '지난주', subtitle: '9월 28일 – 10월 4일' })
    expect(periodLabel('WEEK', periodRange('WEEK', '2026-09-22'), today)).toMatchObject({
      name: '2주 전',
      title: '9월 21일 – 9월 27일',
      subtitle: '2주 전',
      cardTitle: '2주 전 메뉴 메이커',
    })
  })
  it('이번 달이 아니면 연·월로 부른다', () => {
    expect(periodLabel('MONTH', periodRange('MONTH', today), today)).toMatchObject({ title: '이번 달', subtitle: '10월 1일 – 10월 31일', cardTitle: '이번 달 메뉴 메이커' })
    expect(periodLabel('MONTH', periodRange('MONTH', '2026-09-01'), today)).toEqual({
      name: '2026년 9월',
      title: '2026년 9월',
      subtitle: '지난달',
      cardTitle: '9월 메뉴 메이커',
      summary: '2026년 9월',
    })
    expect(periodLabel('MONTH', periodRange('MONTH', '2025-12-01'), today)).toMatchObject({ subtitle: '10개월 전', cardTitle: '2025년 12월 메뉴 메이커' })
  })
  it('채택된 날은 요일을 붙인다', () => {
    expect(adoptedDayText('2026-10-01', today)).toBe('10월 1일 (목)')
    expect(adoptedDayText('2025-11-03', today)).toBe('2025년 11월 3일 (월)')
  })
  it('올해가 아니거나 해를 넘기면 연도를 붙인다', () => {
    expect(rangeText({ from: '2025-12-29', to: '2026-01-04' }, today)).toBe('2025년 12월 29일 – 2026년 1월 4일')
    expect(rangeText({ from: '2025-11-03', to: '2025-11-09' }, today)).toBe('2025년 11월 3일 – 11월 9일')
  })
})

describe('메달과 순위', () => {
  it('TOP3만 메달과 칭호가 있다', () => {
    expect(medalOf(1)).toEqual({ emoji: '🥇', title: '명예 셰프' })
    expect(medalOf(3)?.title).toBe('미식가')
    expect(medalOf(4)).toBeNull()
  })
  it('1위까지 남은 횟수, 1위면 지금 1위', () => {
    expect(gapToFirst(4, 1, 3)).toBe('1위까지 2회')
    expect(gapToFirst(1, 3, 3)).toBe('지금 1위예요')
  })
})

describe('랭킹 화면 주소', () => {
  it('지난 기간은 그 기간을, 지금 기간이면 date 없이 지금으로 읽는다', () => {
    expect(parseRankingView('month', '2026-09-15', today)).toEqual({ period: 'MONTH', range: { from: '2026-09-01', to: '2026-09-30' }, current: false })
    expect(parseRankingView(null, '2026-10-04', today)).toEqual({ period: 'WEEK', range: { from: '2026-09-28', to: '2026-10-04' }, current: false })
    expect(parseRankingView(null, '2026-10-06', today)).toEqual({ period: 'WEEK', range: { from: '2026-10-05', to: '2026-10-11' }, current: true })
    expect(parseRankingView('week', null, today).current).toBe(true)
  })
  it('없는 날짜, 앞으로의 날짜, 12개월보다 앞이면 지금 기간이다', () => {
    for (const date of ['2025-13-15', '2025-00-15', '2026-02-30', '10/07', '2026-10-08', '2025-09-30']) {
      expect(parseRankingView('month', date, today)).toEqual({ period: 'MONTH', range: periodRange('MONTH', today), current: true })
    }
    expect(parseRankingView('week', '2025-09-29', today).current).toBe(false)
  })
  it('지금 기간이면 주소에서 날짜를 뺀다', () => {
    expect(rankingSearch('MONTH', '2026-09-30', today)).toEqual({ period: 'month', date: '2026-09-01' })
    expect(rankingSearch('WEEK', '2026-10-12', today)).toEqual({})
    expect(rankingSearch('MONTH', today, today)).toEqual({ period: 'month' })
  })
})
