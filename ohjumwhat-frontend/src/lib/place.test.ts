import { describe, expect, it } from 'vitest'
import { kakaoPlaceInput, naverPlaceSearchUrl, naverSearchUrl, parseShareText, placeInput } from './place.ts'

describe('parseShareText', () => {
  it('네이버 지도 공유 글에서 머리말·링크를 빼고 식당 이름과 주소를 꺼낸다', () => {
    expect(parseShareText('[네이버 지도]\n할매집\n서울 강남구 테헤란로 1\nhttps://naver.me/5abcDEF')).toEqual({
      name: '할매집',
      address: '서울 강남구 테헤란로 1',
    })
    expect(parseShareText('[네이버지도]\r\n  할매집  강남점 \r\n경기 성남시  분당구 판교역로 1\r\nnaver.me/5abcDEF')).toEqual({
      name: '할매집 강남점',
      address: '경기 성남시 분당구 판교역로 1',
    })
  })

  it('주소처럼 보이는 줄이 없으면 주소는 없다', () => {
    expect(parseShareText('[네이버지도]\n할매집\n맛있는 집\nnaver.me/5abcDEF')).toEqual({ name: '할매집', address: null })
  })

  it('한 줄짜리 공유 글은 링크를 뺀 나머지가 이름이다', () => {
    expect(parseShareText('[카카오맵] 김밥천국 https://kko.to/AbC123')).toEqual({ name: '김밥천국', address: null })
  })

  it('링크만 붙였으면 이름·주소가 없다', () => {
    expect(parseShareText('https://naver.me/5abcDEF')).toEqual({ name: null, address: null })
    expect(parseShareText('map.naver.com/p/entry/place/1868364770')).toEqual({ name: null, address: null })
    expect(parseShareText('  ')).toEqual({ name: null, address: null })
  })

  it('이름은 100자, 주소는 200자까지 자른다', () => {
    expect(parseShareText(`[네이버 지도]\n${'가'.repeat(120)}\nnaver.me/x`).name).toHaveLength(100)
    expect(parseShareText(`[네이버 지도]\n할매집\n서울 ${'가'.repeat(250)}\nnaver.me/x`).address).toHaveLength(200)
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
  it('링크가 비면 식당 없음, 이름·주소는 앞뒤 공백을 지운다', () => {
    expect(placeInput({ link: ' https://naver.me/abcd1234 ', name: ' 할매집 ', address: ' 서울 강남구 테헤란로 1 ' })).toEqual({
      link: 'https://naver.me/abcd1234',
      placeName: '할매집',
      placeAddress: '서울 강남구 테헤란로 1',
      kakaoPlaceId: null,
      placeQuery: null,
    })
    expect(placeInput({ link: 'https://naver.me/abcd1234', name: '  ', address: '' })).toMatchObject({ placeName: null, placeAddress: null })
    expect(placeInput({ link: ' ', name: '할매집', address: '서울' })).toEqual({
      link: null,
      placeName: null,
      placeAddress: null,
      kakaoPlaceId: null,
      placeQuery: null,
    })
  })
})

describe('kakaoPlaceInput', () => {
  it('카카오 식당은 장소 ID와 검색어만 보낸다(검색어가 비면 둘러보기)', () => {
    expect(kakaoPlaceInput('1001', ' 김치찌개 ')).toEqual({ link: null, placeName: null, placeAddress: null, kakaoPlaceId: '1001', placeQuery: '김치찌개' })
    expect(kakaoPlaceInput('1001', ' ').placeQuery).toBeNull()
  })
})

describe('naverPlaceSearchUrl', () => {
  it('이름과 도로명 주소로 네이버 지도 검색 주소를 만든다', () => {
    expect(naverPlaceSearchUrl('할매집', '서울 강남구 테헤란로 10')).toBe(
      `https://map.naver.com/p/search/${encodeURIComponent('할매집 서울 강남구 테헤란로 10')}`,
    )
    expect(naverPlaceSearchUrl('할매집', null)).toBe(`https://map.naver.com/p/search/${encodeURIComponent('할매집')}`)
  })
})
