import { describe, expect, it } from 'vitest'
import { linkHost } from './link.ts'

describe('linkHost', () => {
  it('도메인만 꺼내고 www.는 뺀다', () => {
    expect(linkHost('https://naver.me/5abcDEF')).toBe('naver.me')
    expect(linkHost('https://www.google.com/maps/place/x')).toBe('google.com')
    expect(linkHost('HTTP://Map.Kakao.com/?itemId=1')).toBe('map.kakao.com')
  })
  it('주소가 아니면 그대로', () => {
    expect(linkHost('naver')).toBe('naver')
  })
})
