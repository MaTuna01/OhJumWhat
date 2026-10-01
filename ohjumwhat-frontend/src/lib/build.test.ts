import { describe, expect, it } from 'vitest'
import { isOutdated, parseBuild } from './build.ts'

describe('parseBuild', () => {
  it('빌드 ID와 버전을 읽는다', () => {
    expect(parseBuild({ buildId: 'abc', version: '1.6.0' })).toEqual({ buildId: 'abc', version: '1.6.0' })
  })

  it('모양이 이상한 응답은 null', () => {
    expect(parseBuild(null)).toBeNull()
    expect(parseBuild('<!doctype html>')).toBeNull()
    expect(parseBuild({})).toBeNull()
    expect(parseBuild({ buildId: '', version: '1.6.0' })).toBeNull()
    expect(parseBuild({ buildId: 123, version: '1.6.0' })).toBeNull()
    expect(parseBuild({ buildId: 'abc' })).toBeNull()
    expect(parseBuild({ buildId: 'abc', version: '' })).toBeNull()
  })
})

describe('isOutdated', () => {
  it('빌드 ID가 다르면 새 버전이다', () => {
    expect(isOutdated('abc', { buildId: 'def', version: '1.6.0' })).toBe(true)
  })

  it('빌드 ID가 같으면 그대로다', () => {
    expect(isOutdated('abc', { buildId: 'abc', version: '1.6.0' })).toBe(false)
  })

  it('배포된 빌드를 못 읽었으면 그대로로 본다', () => {
    expect(isOutdated('abc', null)).toBe(false)
    expect(isOutdated('abc', undefined)).toBe(false)
  })
})
