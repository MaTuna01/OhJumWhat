import { describe, expect, it } from 'vitest'
import { isOutdated } from './build.ts'

describe('isOutdated', () => {
  it('빌드 ID가 다르면 새 버전이다', () => {
    expect(isOutdated('abc', { buildId: 'def', version: '1.5.0' })).toBe(true)
  })

  it('빌드 ID가 같으면 그대로다', () => {
    expect(isOutdated('abc', { buildId: 'abc', version: '1.5.0' })).toBe(false)
  })

  it('모양이 이상한 응답은 새 버전으로 보지 않는다', () => {
    expect(isOutdated('abc', null)).toBe(false)
    expect(isOutdated('abc', '<!doctype html>')).toBe(false)
    expect(isOutdated('abc', {})).toBe(false)
    expect(isOutdated('abc', { buildId: '' })).toBe(false)
    expect(isOutdated('abc', { buildId: 123 })).toBe(false)
  })
})
