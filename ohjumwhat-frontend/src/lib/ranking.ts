// 메뉴 메이커 랭킹의 기간 계산과 표시. 기간 규칙은 서버 ranking/RankingPeriod와 같아서 함께 고친다.
// 날짜는 모두 한국 날짜 "YYYY-MM-DD" 문자열로 다룬다(문자열 비교 = 날짜 비교).

export type RankingPeriod = 'WEEK' | 'MONTH'

/** 지난 기록은 이번 달 1일의 12개월 전부터 볼 수 있다. */
export const HISTORY_MONTHS = 12

export type PeriodRange = { from: string; to: string }

const DAY_MS = 86_400_000

function toUtc(day: string): number {
  return Date.UTC(Number(day.slice(0, 4)), Number(day.slice(5, 7)) - 1, Number(day.slice(8, 10)))
}

function fromUtc(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10)
}

export function addDays(day: string, days: number): string {
  return fromUtc(toUtc(day) + days * DAY_MS)
}

/** 이 날짜가 들어 있는 기간: 주간은 월~일, 월간은 1일~말일 */
export function periodRange(period: RankingPeriod, day: string): PeriodRange {
  if (period === 'WEEK') {
    const fromMonday = (new Date(toUtc(day)).getUTCDay() + 6) % 7
    const from = addDays(day, -fromMonday)
    return { from, to: addDays(from, 6) }
  }
  const year = Number(day.slice(0, 4))
  const month = Number(day.slice(5, 7))
  return { from: `${day.slice(0, 7)}-01`, to: fromUtc(Date.UTC(year, month, 0)) }
}

/** 볼 수 있는 가장 이른 날짜(이번 달 1일의 12개월 전). 이 날짜가 들어 있는 기간까지 볼 수 있다. */
export function earliestDay(today: string): string {
  return fromUtc(Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)) - 1 - HISTORY_MONTHS, 1))
}

export function hasPrevious(range: PeriodRange, today: string): boolean {
  return addDays(range.from, -1) >= earliestDay(today)
}

export function hasNext(range: PeriodRange, today: string): boolean {
  return range.to < today
}

/** 지난달 1일(「이달의 메뉴 메이커」는 지난달 월간 1위다) */
export function lastMonthDay(today: string): string {
  return periodRange('MONTH', addDays(periodRange('MONTH', today).from, -1)).from
}

function monthDay(day: string, withYear: boolean): string {
  const text = `${Number(day.slice(5, 7))}월 ${Number(day.slice(8, 10))}일`
  return withYear ? `${day.slice(0, 4)}년 ${text}` : text
}

/** "9월 28일 – 10월 4일". 올해가 아니면 연도를 붙인다. */
export function rangeText(range: PeriodRange, today: string): string {
  const crossYear = range.from.slice(0, 4) !== range.to.slice(0, 4)
  const fromYear = crossYear || range.from.slice(0, 4) !== today.slice(0, 4)
  return `${monthDay(range.from, fromYear)} – ${monthDay(range.to, crossYear)}`
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** 채택된 날: 「10월 1일 (목)」. 올해가 아니면 연도를 붙인다. */
export function adoptedDayText(day: string, today: string): string {
  return `${monthDay(day, day.slice(0, 4) !== today.slice(0, 4))} (${WEEKDAYS[new Date(toUtc(day)).getUTCDay()]})`
}

export type PeriodLabel = {
  /** 기간 이름: 이번 주·지난주·3주 전 / 이번 달·2026년 9월 */
  name: string
  /** 기간 고르기의 두 줄(Figma PeriodSwitcher) */
  title: string
  subtitle: string
  /** 포디움 카드 제목: 「이번 주 메뉴 메이커」, 「9월 메뉴 메이커」 */
  cardTitle: string
  /** 채택 기록 모달의 기간: 「이번 주 · 9월 28일 – 10월 4일」, 「2026년 9월」 */
  summary: string
}

export function periodLabel(period: RankingPeriod, range: PeriodRange, today: string): PeriodLabel {
  const dates = rangeText(range, today)
  if (period === 'WEEK') {
    const weeksAgo = Math.round((toUtc(periodRange('WEEK', today).from) - toUtc(range.from)) / (7 * DAY_MS))
    const name = weeksAgo === 0 ? '이번 주' : weeksAgo === 1 ? '지난주' : `${weeksAgo}주 전`
    return {
      name,
      title: weeksAgo <= 1 ? name : dates,
      subtitle: weeksAgo <= 1 ? dates : name,
      cardTitle: `${name} 메뉴 메이커`,
      summary: `${name} · ${dates}`,
    }
  }
  const year = Number(range.from.slice(0, 4))
  const month = Number(range.from.slice(5, 7))
  const monthsAgo = Number(today.slice(0, 4)) * 12 + Number(today.slice(5, 7)) - (year * 12 + month)
  if (monthsAgo === 0) {
    return { name: '이번 달', title: '이번 달', subtitle: dates, cardTitle: '이번 달 메뉴 메이커', summary: `이번 달 · ${dates}` }
  }
  const name = `${year}년 ${month}월`
  const shortMonth = String(year) === today.slice(0, 4) ? `${month}월` : name
  return {
    name,
    title: name,
    subtitle: monthsAgo === 1 ? '지난달' : `${monthsAgo}개월 전`,
    cardTitle: `${shortMonth} 메뉴 메이커`,
    summary: name,
  }
}

/** TOP3 메달과 칭호(같은 횟수면 같은 순위라 같은 메달) */
export function medalOf(rank: number): { emoji: string; title: string } | null {
  if (rank === 1) return { emoji: '🥇', title: '명예 셰프' }
  if (rank === 2) return { emoji: '🥈', title: '기획왕' }
  if (rank === 3) return { emoji: '🥉', title: '미식가' }
  return null
}

/** 내 순위 카드 오른쪽: 「1위까지 2회」, 1위면 「지금 1위예요」 */
export function gapToFirst(myRank: number, myCount: number, firstCount: number): string {
  return myRank === 1 ? '지금 1위예요' : `1위까지 ${firstCount - myCount}회`
}

export type RankingView = {
  period: RankingPeriod
  range: PeriodRange
  /** 지금 기간이면 서버가 오늘로 기간을 정한다(요청에 date를 넣지 않는다). */
  current: boolean
}

/**
 * 랭킹 화면 주소(?period=month&date=YYYY-MM-DD)를 읽는다. 날짜가 없거나, 없는 날짜거나, 앞으로의 날짜거나,
 * 12개월 전보다 앞이면 지금 기간을 보여준다.
 */
export function parseRankingView(periodParam: string | null, dateParam: string | null, today: string): RankingView {
  const period: RankingPeriod = periodParam === 'month' ? 'MONTH' : 'WEEK'
  const valid = dateParam != null && /^\d{4}-\d{2}-\d{2}$/.test(dateParam) && addDays(dateParam, 0) === dateParam && dateParam <= today
  const range = valid ? periodRange(period, dateParam) : null
  if (range == null || range.to < earliestDay(today) || range.to >= today) {
    return { period, range: periodRange(period, today), current: true }
  }
  return { period, range, current: false }
}

/** 이 날짜가 들어 있는 기간의 주소. 지금 기간이면 날짜를 비운다(내일이 되면 새 기간을 보여주게). */
export function rankingSearch(period: RankingPeriod, day: string, today: string): Record<string, string> {
  const search: Record<string, string> = {}
  if (period === 'MONTH') search.period = 'month'
  const range = periodRange(period, day)
  if (range.to < today) search.date = range.from
  return search
}
