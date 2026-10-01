import { describe, expect, it } from 'vitest'
import { linkHost, serviceLabel } from './link.ts'

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

describe('serviceLabel', () => {
  it('지도 서비스 이름으로 보여준다', () => {
    expect(serviceLabel('https://map.naver.com/p/entry/place/1868364770')).toBe('네이버 지도')
    expect(serviceLabel('https://m.place.naver.com/restaurant/1/home')).toBe('네이버 지도')
    expect(serviceLabel('https://kko.to/AbC123')).toBe('카카오맵')
    expect(serviceLabel('https://place.map.kakao.com/123')).toBe('카카오맵')
    expect(serviceLabel('https://www.google.com/maps/place/x')).toBe('구글 지도')
    expect(serviceLabel('https://maps.app.goo.gl/abc')).toBe('구글 지도')
  })
  it('장소로 확인하지 못한 naver.me는 "네이버", 그 밖에는 도메인', () => {
    expect(serviceLabel('https://naver.me/5abcDEF')).toBe('네이버')
    expect(serviceLabel('https://www.google.com/search?q=x')).toBe('google.com')
    expect(serviceLabel('https://example.com/a')).toBe('example.com')
  })
})
