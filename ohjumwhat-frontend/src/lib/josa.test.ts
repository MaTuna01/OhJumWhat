import { describe, expect, it } from 'vitest'
import { josa, withJosa } from './josa.ts'

describe('josa', () => {
  it('끝 글자의 받침으로 이/가를 고른다', () => {
    expect(josa('마찬영', '이/가')).toBe('이')
    expect(josa('김민수', '이/가')).toBe('가')
    expect(josa('탈퇴한 사용자', '이/가')).toBe('가')
  })

  it('을/를·은/는·과/와도 같은 규칙을 쓴다', () => {
    expect([josa('개발팀', '을/를'), josa('개발팀', '은/는'), josa('개발팀', '과/와')]).toEqual(['을', '은', '과'])
    expect([josa('스터디', '을/를'), josa('스터디', '은/는'), josa('스터디', '과/와')]).toEqual(['를', '는', '와'])
  })

  it('으로/로는 ㄹ받침이면 로를 쓴다', () => {
    expect(josa('서울', '으로/로')).toBe('로')
    expect(josa('부산', '으로/로')).toBe('으로')
    expect(josa('대구', '으로/로')).toBe('로')
  })

  it('숫자는 읽는 소리로 고른다', () => {
    expect(josa('개발자1', '이/가')).toBe('이')
    expect(josa('개발자1', '으로/로')).toBe('로')
    expect(josa('2', '이/가')).toBe('가')
    expect(josa('팀3', '이/가')).toBe('이')
    expect(josa('팀3', '으로/로')).toBe('으로')
    expect(josa('10', '이/가')).toBe('이')
    expect(josa('7', '으로/로')).toBe('로')
  })

  it('한글·숫자로 끝나지 않으면 병기한다', () => {
    expect(josa('Kiki', '이/가')).toBe('이(가)')
    expect(josa('민수😀', '이/가')).toBe('이(가)')
    expect(josa('민수(개발)', '이/가')).toBe('이(가)')
    expect(josa('ㅋㅋ', '을/를')).toBe('을(를)')
    expect(josa('', '으로/로')).toBe('(으)로')
  })

  it('끝의 공백은 보지 않는다', () => {
    expect(josa('마찬영 ', '이/가')).toBe('이')
  })
})

describe('withJosa', () => {
  it('말 뒤에 조사를 붙인다', () => {
    expect(withJosa('마찬영', '이/가')).toBe('마찬영이')
    expect(withJosa('Kiki', '이/가')).toBe('Kiki이(가)')
  })
})
