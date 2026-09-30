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
