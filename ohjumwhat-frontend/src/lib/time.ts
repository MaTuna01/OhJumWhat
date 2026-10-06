// 시간 표시는 사용자 기기의 시간대와 관계없이 한국 시간으로 한다(서비스 기준 시간).
const TIME_ZONE = 'Asia/Seoul'

const clockFormat = new Intl.DateTimeFormat('ko-KR', { timeZone: TIME_ZONE, hour: 'numeric', minute: '2-digit' })

/** "오전 11:50" */
export function formatClock(iso: string): string {
  return clockFormat.format(new Date(iso))
}

/** 한국 시간 "HH:mm" → "오전 11:00" (정기 투표 시각 표시) */
export function formatHhmm(hhmm: string): string {
  return formatClock(`2026-01-01T${hhmm}:00+09:00`)
}

/** "오전 11:00 ~ 11:50", 오전·오후가 바뀌면 "오전 11:30 ~ 오후 12:10" */
export function formatTimeRange(open: string, close: string): string {
  const [a, b] = [formatHhmm(open), formatHhmm(close)]
  const period = (t: string) => t.split(' ')[0]
  return `${a} ~ ${period(a) === period(b) ? b.slice(period(b).length + 1) : b}`
}

/** 마감까지 남은 시간: "32분 남음", "1시간 5분 남음", "1분 안에 마감". 이미 지났으면 null */
export function formatRemaining(closesAt: string, now: number): string | null {
  const ms = new Date(closesAt).getTime() - now
  if (ms <= 0) return null
  const minutes = Math.floor(ms / 60_000)
  if (minutes < 1) return '1분 안에 마감'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}분 남음`
  return m === 0 ? `${h}시간 남음` : `${h}시간 ${m}분 남음`
}

/** 한국 시간 "HH:mm" */
export function toKstHhmm(date: Date): string {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: TIME_ZONE, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(date)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00'
  return `${get('hour')}:${get('minute')}`
}

/** 투표 만들기의 기본 마감 시간: 지금부터 30분 뒤를 10분 단위로 올림(한국 시간). 자정을 넘기면 23:50 */
export function defaultCloseTime(now: number): string {
  const step = 10 * 60_000
  const target = Math.ceil((now + 30 * 60_000) / step) * step
  const hhmm = toKstHhmm(new Date(target))
  return hhmm < toKstHhmm(new Date(now)) ? '23:50' : hhmm
}

const dayKeyFormat = new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })
const monthDayFormat = new Intl.DateTimeFormat('ko-KR', { timeZone: TIME_ZONE, month: 'long', day: 'numeric' })
const fullDateFormat = new Intl.DateTimeFormat('ko-KR', { timeZone: TIME_ZONE, year: 'numeric', month: 'long', day: 'numeric' })

/** 한국 날짜 "YYYY-MM-DD". 서버의 LocalDate("2026-09-30")나 시각(ISO)을 모두 받는다. */
export function kstDayKey(value: string | number): string {
  return dayKeyFormat.format(new Date(value))
}

/** "2026년 9월 28일" */
export function formatDate(value: string): string {
  return fullDateFormat.format(new Date(value))
}

/** 날짜를 짧게: "오늘", "어제", 올해면 "9월 28일", 그 전이면 "2025년 9월 28일" (관리자 콘솔 목록) */
export function formatDay(value: string, now: number): string {
  const day = kstDayKey(value)
  if (day === kstDayKey(now)) return '오늘'
  if (day === kstDayKey(now - 86_400_000)) return '어제'
  return day.slice(0, 4) === kstDayKey(now).slice(0, 4) ? monthDayFormat.format(new Date(value)) : formatDate(value)
}

/** "오늘 오전 9:12", "어제 오후 3:00", "9월 28일 오전 9:12" */
export function formatDayTime(iso: string, now: number): string {
  return `${formatDay(iso, now)} ${formatClock(iso)}`
}

/** 최근 활동: "방금", "5분 전", "3시간 전", 하루가 지나면 formatDayTime */
export function formatAgo(iso: string, now: number): string {
  const minutes = Math.floor((now - new Date(iso).getTime()) / 60_000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}시간 전`
  return formatDayTime(iso, now)
}

/** 한국 날짜("YYYY-MM-DD")가 오늘(한국)로부터 며칠 전인지. 오늘이면 0 */
export function daysAgo(day: string, now: number): number {
  const utc = (key: string) => Date.UTC(Number(key.slice(0, 4)), Number(key.slice(5, 7)) - 1, Number(key.slice(8, 10)))
  return Math.round((utc(kstDayKey(now)) - utc(day)) / 86_400_000)
}

/** 먹은 날 표시: "오늘", "어제", 30일 안이면 "3일 전", 그보다 오래면 formatDay("9월 8일") */
export function formatEatenDay(day: string, now: number): string {
  const days = daysAgo(day, now)
  if (days <= 0) return '오늘'
  if (days === 1) return '어제'
  if (days <= 30) return `${days}일 전`
  return formatDay(day, now)
}

const weekdayFormat = new Intl.DateTimeFormat('ko-KR', { timeZone: TIME_ZONE, weekday: 'short' })

/** 지난 투표 날짜: "오늘", "어제", 그 전은 요일을 붙여 "9월 28일 (일)" */
export function formatPollDay(day: string, now: number): string {
  const label = formatDay(day, now)
  return daysAgo(day, now) <= 1 ? label : `${label} (${weekdayFormat.format(new Date(day))})`
}

/** "9월 28일" (결과 복사 글머리) */
export function formatMonthDay(value: string): string {
  return monthDayFormat.format(new Date(value))
}
