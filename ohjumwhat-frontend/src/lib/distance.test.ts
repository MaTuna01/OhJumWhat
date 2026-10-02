import { describe, expect, it } from 'vitest'
import { distanceLabel, distanceMeters, formatDistance, walkMinutes } from './distance.ts'

describe('distanceMeters', () => {
  it('두 지점의 직선거리를 미터로 구한다', () => {
    // 위도 0.001도 ≈ 111m
    expect(distanceMeters({ lat: 37.5, lng: 127.0 }, { lat: 37.501, lng: 127.0 })).toBeCloseTo(111.2, 0)
    expect(distanceMeters({ lat: 37.5, lng: 127.0 }, { lat: 37.5, lng: 127.0 })).toBe(0)
  })
})

describe('formatDistance', () => {
  it('1km 미만은 10m 단위, 그 이상은 0.1km 단위로 보여준다', () => {
    expect(formatDistance(347)).toBe('350m')
    expect(formatDistance(3)).toBe('10m')
    expect(formatDistance(994)).toBe('990m')
    expect(formatDistance(996)).toBe('1km')
    expect(formatDistance(1234)).toBe('1.2km')
    expect(formatDistance(2000)).toBe('2km')
  })
})

describe('walkMinutes', () => {
  it('직선거리의 1.3배를 분당 67m로 걷는다고 보고 최소 1분이다', () => {
    expect(walkMinutes(350)).toBe(7)
    expect(walkMinutes(250)).toBe(5)
    expect(walkMinutes(10)).toBe(1)
    expect(walkMinutes(1200)).toBe(23)
  })
})

describe('distanceLabel', () => {
  it('거리와 도보 시간을 함께 보여주고, 걸어가기 먼 곳(60분 넘게)은 거리만 보여준다', () => {
    expect(distanceLabel(250)).toBe('250m · 도보 약 5분')
    expect(distanceLabel(3000)).toBe('3km · 도보 약 58분')
    expect(distanceLabel(8300)).toBe('8.3km')
  })
})
