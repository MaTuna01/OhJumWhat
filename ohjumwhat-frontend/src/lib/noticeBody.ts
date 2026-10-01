/** 새 소식 본문 한 조각: 글자 또는 링크 */
export type NoticeSegment = { type: 'text'; text: string } | { type: 'link'; href: string; text: string }

export type NoticeLine = NoticeSegment[]

/** 문단(줄바꿈 유지) 또는 목록 */
export type NoticeBlock = { type: 'paragraph'; lines: NoticeLine[] } | { type: 'list'; items: NoticeLine[] }

// 주소에 쓰는 ASCII 문자만 잡는다. 한글이나 공백이 나오면 끊는다("https://a.com에서" → https://a.com).
const URL_PATTERN = /https?:\/\/[A-Za-z0-9\-._~:/?#[\]@!$&'()*+,;=%]+/g
const TRAILING_PUNCTUATION = '.,!?:;\''

const count = (text: string, char: string) => text.split(char).length - 1

/** 문장 끝 문장부호와 짝 없는 닫는 괄호는 주소에서 뺀다("(https://a.com)." → https://a.com). */
function trimUrl(url: string): string {
  let end = url.length
  while (end > 0) {
    const last = url[end - 1]
    const head = url.slice(0, end)
    if (TRAILING_PUNCTUATION.includes(last) || (last === ')' && count(head, ')') > count(head, '('))) {
      end -= 1
    } else {
      break
    }
  }
  return url.slice(0, end)
}

/** 한 줄을 글자와 링크(http/https만)로 나눈다. */
export function linkify(line: string): NoticeLine {
  const segments: NoticeSegment[] = []
  let last = 0
  for (const match of line.matchAll(URL_PATTERN)) {
    const href = trimUrl(match[0])
    if (!/^https?:\/\/[^/]/.test(href)) continue
    if (match.index > last) segments.push({ type: 'text', text: line.slice(last, match.index) })
    segments.push({ type: 'link', href, text: href })
    last = match.index + href.length
  }
  if (last < line.length) segments.push({ type: 'text', text: line.slice(last) })
  return segments
}

/**
 * 새 소식 본문(일반 텍스트)을 나눈다. 빈 줄은 문단을 나누고, "- "로 시작하는 줄은 목록이 된다.
 * HTML이나 마크다운은 해석하지 않는다(글자 그대로 보여준다).
 */
export function parseNoticeBody(body: string): NoticeBlock[] {
  const blocks: NoticeBlock[] = []
  let current: NoticeBlock | null = null
  for (const raw of body.replace(/\r\n?/g, '\n').split('\n')) {
    const line = raw.trim()
    if (line === '') {
      current = null
      continue
    }
    // "- 내용"은 목록. 내용 없이 "-"만 있는 줄은 건너뛴다.
    const item = /^-(?:\s+(.*))?$/.exec(line)
    if (item) {
      const content = (item[1] ?? '').trim()
      if (content === '') continue
      if (current?.type !== 'list') {
        current = { type: 'list', items: [] }
        blocks.push(current)
      }
      current.items.push(linkify(content))
    } else {
      if (current?.type !== 'paragraph') {
        current = { type: 'paragraph', lines: [] }
        blocks.push(current)
      }
      current.lines.push(linkify(line))
    }
  }
  return blocks
}
