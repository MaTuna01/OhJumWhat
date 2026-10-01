import { describe, expect, it } from 'vitest'
import { naverSearchUrl, parseShareText, placeInput } from './place.ts'

describe('parseShareText', () => {
  it('네이버 지도 공유 글에서 머리말·링크·주소를 빼고 식당 이름을 꺼낸다', () => {
    expect(parseShareText('[네이버 지도]\n할매집\n서울 강남구 테헤란로 1\nhttps://naver.me/5abcDEF')).toEqual({ name: '할매집' })
    expect(parseShareText('[네이버지도]\r\n  할매집  강남점 \r\nnaver.me/5abcDEF')).toEqual({ name: '할매집 강남점' })
  })

  it('한 줄짜리 공유 글은 링크를 뺀 나머지가 이름이다', () => {
    expect(parseShareText('[카카오맵] 김밥천국 https://kko.to/AbC123')).toEqual({ name: '김밥천국' })
  })

  it('링크만 붙였으면 이름이 없다', () => {
    expect(parseShareText('https://naver.me/5abcDEF')).toEqual({ name: null })
    expect(parseShareText('map.naver.com/p/entry/place/1868364770')).toEqual({ name: null })
    expect(parseShareText('  ')).toEqual({ name: null })
  })

  it('이름은 100자까지 자른다', () => {
    expect(parseShareText(`[네이버 지도]\n${'가'.repeat(120)}\nnaver.me/x`).name).toHaveLength(100)
  })
})

describe('naverSearchUrl', () => {
  it('검색 지역과 검색어를 붙여 네이버 지도 검색 주소를 만든다', () => {
    expect(naverSearchUrl('역삼동', '김치찌개')).toBe(`https://map.naver.com/p/search/${encodeURIComponent('역삼동 김치찌개')}`)
  })

  it('검색 지역이 없으면 검색어만, 빗금은 공백으로 바꾼다', () => {
    expect(naverSearchUrl(null, ' 짜장/짬뽕 ')).toBe(`https://map.naver.com/p/search/${encodeURIComponent('짜장 짬뽕')}`)
    expect(naverSearchUrl('  ', '국밥')).toBe(`https://map.naver.com/p/search/${encodeURIComponent('국밥')}`)
  })
})

describe('placeInput', () => {
  it('링크가 비면 식당 없음, 이름은 앞뒤 공백을 지운다', () => {
    expect(placeInput({ link: ' https://naver.me/abcd1234 ', name: ' 할매집 ' })).toEqual({ link: 'https://naver.me/abcd1234', placeName: '할매집' })
    expect(placeInput({ link: 'https://naver.me/abcd1234', name: '  ' })).toEqual({ link: 'https://naver.me/abcd1234', placeName: null })
    expect(placeInput({ link: ' ', name: '할매집' })).toEqual({ link: null, placeName: null })
  })
})
