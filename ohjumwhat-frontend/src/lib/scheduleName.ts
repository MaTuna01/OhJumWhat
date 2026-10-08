/**
 * 정기 투표 규칙 이름의 날짜 토큰. 이름에 `${오늘날짜}`를 넣으면 투표가 열릴 때 그날의 한국 날짜("2026-10-08")로 바뀐다.
 * 토큰은 이것 하나뿐이고 오타(`${오늘 날짜}`, `${날짜}`)는 글자로 둔다.
 * 서버 schedule/ScheduleName과 규칙이 같아서 함께 고친다.
 */
export const TODAY_TOKEN = '${오늘날짜}'

/** 이름 길이 한도. 토큰은 바뀐 날짜의 길이(10자)로 센다. */
export const SCHEDULE_NAME_MAX = 50

const DATE_LENGTH = '2026-10-08'.length

/** 그날 열리는 투표의 제목. day는 한국 날짜 "YYYY-MM-DD"(lib/time.ts kstDayKey) */
export function renderScheduleName(name: string, day: string): string {
  return name.split(TODAY_TOKEN).join(day)
}

/** 투표 제목이 됐을 때의 길이 */
export function scheduleNameLength(name: string): number {
  const tokens = name.split(TODAY_TOKEN).length - 1
  return name.length + tokens * (DATE_LENGTH - TODAY_TOKEN.length)
}

export function hasDateToken(name: string): boolean {
  return name.includes(TODAY_TOKEN)
}

export type ScheduleNamePart = { kind: 'text'; text: string } | { kind: 'today' }

/** 화면에 그릴 조각: 글자와 날짜 토큰(「오늘 날짜」 칩) */
export function scheduleNameParts(name: string): ScheduleNamePart[] {
  const parts: ScheduleNamePart[] = []
  name.split(TODAY_TOKEN).forEach((text, i) => {
    if (i > 0) parts.push({ kind: 'today' })
    if (text) parts.push({ kind: 'text', text })
  })
  return parts
}

/** 글만 들어가는 곳(확인 창 제목, 한 줄 요약)의 규칙 이름: 토큰을 「[오늘 날짜]」로 */
export function scheduleNameText(name: string): string {
  return name.split(TODAY_TOKEN).join('[오늘 날짜]')
}

/**
 * value의 [start, end) 자리에 토큰을 넣는다. 넣은 뒤의 값과 커서 위치(토큰 바로 뒤).
 * 자리가 토큰에 걸치면 그 토큰 뒤로 옮기고(토큰을 쪼개지 않게), 앞뒤가 글자면 띄어 쓴다(「점심2026-10-08」이 되지 않게).
 */
export function insertDateToken(value: string, start: number, end: number): { value: string; cursor: number } {
  for (let i = value.indexOf(TODAY_TOKEN); i >= 0; i = value.indexOf(TODAY_TOKEN, i + 1)) {
    const tokenEnd = i + TODAY_TOKEN.length
    if (start < tokenEnd && end > i) {
      start = Math.max(start, tokenEnd)
      end = Math.max(end, tokenEnd)
    }
  }
  const before = value.slice(0, start)
  const after = value.slice(end)
  const head = before && !/\s$/.test(before) ? ' ' : ''
  const tail = after && !/^\s/.test(after) ? ' ' : ''
  return { value: before + head + TODAY_TOKEN + tail + after, cursor: before.length + head.length + TODAY_TOKEN.length }
}
