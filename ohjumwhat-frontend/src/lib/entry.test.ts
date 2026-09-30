import { describe, expect, it } from 'vitest'
import { resolveEntryPath } from './entry.ts'

describe('resolveEntryPath', () => {
  it('기억해 둔 경로(초대 링크)가 있으면 그곳으로 간다', () => {
    expect(resolveEntryPath('/invite/abc', 3)).toBe('/invite/abc')
  })

  it('기억해 둔 경로가 없으면 최근 들어간 조직으로 간다', () => {
    expect(resolveEntryPath(null, 3)).toBe('/orgs/3')
  })

  it('속한 조직도 없으면 마이페이지로 간다', () => {
    expect(resolveEntryPath(null, null)).toBe('/me')
  })

  it('외부 주소나 루트·로그인 경로는 무시한다', () => {
    expect(resolveEntryPath('//evil.example.com', 3)).toBe('/orgs/3')
    expect(resolveEntryPath('https://evil.example.com', null)).toBe('/me')
    expect(resolveEntryPath('/', 3)).toBe('/orgs/3')
    expect(resolveEntryPath('/login?error', 3)).toBe('/orgs/3')
  })
})
