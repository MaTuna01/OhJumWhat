import { describe, expect, it } from 'vitest'
import { linkify, parseNoticeBody } from './noticeBody.ts'

const text = (value: string) => ({ type: 'text' as const, text: value })
const link = (href: string) => ({ type: 'link' as const, href, text: href })

describe('parseNoticeBody', () => {
  it('빈 줄로 문단을 나누고 문단 안의 줄바꿈은 유지한다', () => {
    expect(parseNoticeBody('첫 줄\n둘째 줄\n\n\n새 문단')).toEqual([
      { type: 'paragraph', lines: [[text('첫 줄')], [text('둘째 줄')]] },
      { type: 'paragraph', lines: [[text('새 문단')]] },
    ])
  })

  it('"- "로 시작하는 줄은 목록이고, 문단과 섞을 수 있다', () => {
    expect(parseNoticeBody('바뀐 점\n- 별명\n-  지난 투표 \r\n- \n\n끝')).toEqual([
      { type: 'paragraph', lines: [[text('바뀐 점')]] },
      { type: 'list', items: [[text('별명')], [text('지난 투표')]] },
      { type: 'paragraph', lines: [[text('끝')]] },
    ])
  })

  it('"-" 뒤에 공백이 없으면 목록이 아니다', () => {
    expect(parseNoticeBody('-5분 정도')).toEqual([{ type: 'paragraph', lines: [[text('-5분 정도')]] }])
  })

  it('HTML은 해석하지 않고 글자 그대로 둔다', () => {
    expect(parseNoticeBody('<b>굵게</b>')).toEqual([{ type: 'paragraph', lines: [[text('<b>굵게</b>')]] }])
  })
})

describe('linkify', () => {
  it('http/https 주소를 링크로 만든다', () => {
    expect(linkify('의견은 https://github.com/MaTuna01/OhJumWhat 에 남겨 주세요')).toEqual([
      text('의견은 '),
      link('https://github.com/MaTuna01/OhJumWhat'),
      text(' 에 남겨 주세요'),
    ])
  })

  it('한글이 붙으면 그 앞에서 끊는다', () => {
    expect(linkify('https://www.ohjumwhat.cloud에서 확인하세요')).toEqual([
      link('https://www.ohjumwhat.cloud'),
      text('에서 확인하세요'),
    ])
  })

  it('문장 끝 문장부호와 짝 없는 닫는 괄호는 주소에서 뺀다', () => {
    expect(linkify('주소(https://a.com/b).')).toEqual([text('주소('), link('https://a.com/b'), text(').')])
    expect(linkify('https://ko.wikipedia.org/wiki/A_(B)!')).toEqual([link('https://ko.wikipedia.org/wiki/A_(B)'), text('!')])
  })

  it('http/https가 아니거나 주소가 비어 있으면 링크가 아니다', () => {
    expect(linkify('javascript:alert(1)')).toEqual([text('javascript:alert(1)')])
    expect(linkify('https://')).toEqual([text('https://')])
  })
})
