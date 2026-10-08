import { describe, expect, it } from 'vitest'
import {
  hasDateToken,
  insertDateToken,
  renderScheduleName,
  scheduleNameLength,
  scheduleNameParts,
  scheduleNameText,
  TODAY_TOKEN,
} from './scheduleName.ts'

describe('renderScheduleName', () => {
  it('날짜 토큰을 그날 날짜로 바꾼다', () => {
    expect(renderScheduleName('${오늘날짜} 오점왓?!', '2026-10-08')).toBe('2026-10-08 오점왓?!')
    expect(renderScheduleName('점심 ${오늘날짜} / ${오늘날짜}', '2026-10-08')).toBe('점심 2026-10-08 / 2026-10-08')
    expect(renderScheduleName('점심', '2026-10-08')).toBe('점심')
  })

  it('오타 토큰은 글자로 둔다', () => {
    expect(renderScheduleName('${오늘 날짜} ${날짜}', '2026-10-08')).toBe('${오늘 날짜} ${날짜}')
    expect(hasDateToken('${오늘 날짜}')).toBe(false)
  })
})

describe('scheduleNameLength', () => {
  it('토큰을 10자로 센다', () => {
    expect(scheduleNameLength('점심')).toBe(2)
    expect(scheduleNameLength('${오늘날짜} 오점왓?!')).toBe(16)
    expect(scheduleNameLength('${오늘날짜}${오늘날짜}')).toBe(20)
    expect(scheduleNameLength('${오늘 날짜}')).toBe('${오늘 날짜}'.length)
  })
})

describe('scheduleNameParts', () => {
  it('글자와 토큰 조각으로 나눈다', () => {
    expect(scheduleNameParts('${오늘날짜} 오점왓?!')).toEqual([{ kind: 'today' }, { kind: 'text', text: ' 오점왓?!' }])
    expect(scheduleNameParts('점심')).toEqual([{ kind: 'text', text: '점심' }])
    expect(scheduleNameParts('a${오늘날짜}${오늘날짜}')).toEqual([{ kind: 'text', text: 'a' }, { kind: 'today' }, { kind: 'today' }])
  })
})

describe('scheduleNameText', () => {
  it('토큰을 [오늘 날짜]로 보여준다', () => {
    expect(scheduleNameText('${오늘날짜} 오점왓?!')).toBe('[오늘 날짜] 오점왓?!')
  })
})

describe('insertDateToken', () => {
  it('커서 자리에 넣고 커서를 토큰 뒤로 옮긴다', () => {
    expect(insertDateToken(' 오점왓', 0, 0)).toEqual({ value: `${TODAY_TOKEN} 오점왓`, cursor: TODAY_TOKEN.length })
    expect(insertDateToken('', 0, 0)).toEqual({ value: TODAY_TOKEN, cursor: TODAY_TOKEN.length })
  })

  it('고른 글자를 바꾼다', () => {
    expect(insertDateToken('점심 메뉴', 3, 5)).toEqual({ value: `점심 ${TODAY_TOKEN}`, cursor: 3 + TODAY_TOKEN.length })
  })

  it('앞뒤가 글자면 띄어 쓴다', () => {
    expect(insertDateToken('점심', 2, 2)).toEqual({ value: `점심 ${TODAY_TOKEN}`, cursor: 3 + TODAY_TOKEN.length })
    expect(insertDateToken('오점왓', 0, 0)).toEqual({ value: `${TODAY_TOKEN} 오점왓`, cursor: TODAY_TOKEN.length })
  })

  it('토큰 한가운데에서는 그 토큰 뒤에 넣는다', () => {
    const value = `${TODAY_TOKEN} 점심`
    expect(insertDateToken(value, 3, 3).value).toBe(`${TODAY_TOKEN} ${TODAY_TOKEN} 점심`)
  })
})
