import { describe, expect, it } from 'vitest'
import { renderLanding } from './render.tsx'

describe('renderLanding', () => {
  const html = renderLanding()

  it('검색 로봇이 읽을 본문을 HTML로 그린다', () => {
    expect(html).toContain('<h1')
    expect(html).toContain('오늘 점심 뭐 먹지?')
    expect(html.match(/<h2/g)).toHaveLength(3)
  })

  it('로그인 버튼은 서버의 구글 로그인 주소로 간다', () => {
    expect(html).toContain('href="/oauth2/authorization/google"')
    expect(html).toContain('href="/login"')
  })

  it('브라우저에서 React를 붙이지 않으므로 스크립트가 없다', () => {
    expect(html).not.toContain('<script')
  })
})
