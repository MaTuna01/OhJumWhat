import type { InfiniteData } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import type { Letter, LetterPage } from '../queries/letters.ts'
import { counterpartLabel, letterLength, organizationLabel, unreadLetterLabel, updateLetterInPages } from './letters.ts'

const letter: Letter = {
  id: 1,
  box: 'RECEIVED',
  organization: { id: 1, name: '개발팀' },
  counterpart: { userId: 2, name: '이서연', profileImageUrl: null },
  counterpartHidden: false,
  anonymous: false,
  body: '안녕',
  createdAt: '2026-10-05T03:00:00Z',
  readAt: null,
  replyTo: null,
  canReply: true,
  replyAnonymous: false,
  reported: false,
}

describe('counterpartLabel', () => {
  it('익명이면 이름 대신 「익명」, 탈퇴했으면 「탈퇴한 사용자」', () => {
    expect(counterpartLabel(letter)).toBe('이서연')
    expect(counterpartLabel({ counterpart: null, counterpartHidden: true })).toBe('익명')
    expect(counterpartLabel({ counterpart: null, counterpartHidden: false })).toBe('탈퇴한 사용자')
  })

  it('조직이 없어졌으면 「삭제된 조직」', () => {
    expect(organizationLabel(letter)).toBe('개발팀')
    expect(organizationLabel({ organization: null })).toBe('삭제된 조직')
  })
})

describe('letterLength', () => {
  it('앞뒤 공백을 빼고 코드 포인트로 센다', () => {
    expect(letterLength('  안녕\r\n하세요  ')).toBe(6)
    expect(letterLength('😀😀')).toBe(2)
  })
})

describe('updateLetterInPages', () => {
  it('한 통만 바꾸고 나머지는 그대로 둔다', () => {
    const other = { ...letter, id: 2 }
    const data: InfiniteData<LetterPage> = { pages: [{ letters: [letter, other], hasMore: false }], pageParams: [0] }
    const next = updateLetterInPages(data, 1, (l) => ({ ...l, readAt: 'now' }))
    expect(next?.pages[0].letters[0].readAt).toBe('now')
    expect(next?.pages[0].letters[1]).toBe(other)
    expect(updateLetterInPages(undefined, 1, (l) => l)).toBeUndefined()
  })
})

describe('unreadLetterLabel', () => {
  it('99개가 넘으면 99+', () => {
    expect(unreadLetterLabel(0)).toBe('')
    expect(unreadLetterLabel(3)).toBe('3')
    expect(unreadLetterLabel(120)).toBe('99+')
  })
})
