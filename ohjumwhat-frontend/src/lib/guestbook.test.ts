import { describe, expect, it } from 'vitest'
import {
  authorLabel,
  cooldownSecondsLeft,
  GUESTBOOK_COOLDOWN_MS,
  guestbookLength,
  guestbookTabLabel,
  isNewEntry,
  latestRestrictedAt,
  newestCreatedAt,
  newGuestbookLabel,
  pagerLabel,
} from './guestbook.ts'

describe('guestbookLength', () => {
  it('앞뒤 공백을 빼고 글자(코드 포인트) 수로 센다', () => {
    expect(guestbookLength('  국밥 맛있었어요  ')).toBe(8)
    expect(guestbookLength('😋'.repeat(100))).toBe(100)
    expect(guestbookLength('   ')).toBe(0)
  })
})

describe('cooldownSecondsLeft', () => {
  const start = 1_000_000

  it('남은 시간을 초로 올려 센다', () => {
    const until = start + GUESTBOOK_COOLDOWN_MS
    expect(cooldownSecondsLeft(until, start)).toBe(5)
    expect(cooldownSecondsLeft(until, start + 1)).toBe(5)
    expect(cooldownSecondsLeft(until, start + 1000)).toBe(4)
    expect(cooldownSecondsLeft(until, start + 4999)).toBe(1)
  })

  it('지나면 0이고 음수가 되지 않는다', () => {
    const until = start + GUESTBOOK_COOLDOWN_MS
    expect(cooldownSecondsLeft(until, until)).toBe(0)
    expect(cooldownSecondsLeft(until, until + 10_000)).toBe(0)
    expect(cooldownSecondsLeft(0, start)).toBe(0)
  })
})

describe('pagerLabel', () => {
  it('0쪽부터 세는 번호를 「1 / 3」으로', () => {
    expect(pagerLabel(0, 3)).toBe('1 / 3')
    expect(pagerLabel(2, 3)).toBe('3 / 3')
  })
})

describe('isNewEntry', () => {
  it('마지막으로 본 시각 뒤에 쓴 글만 새 글이다', () => {
    expect(isNewEntry('2026-10-06T04:20:00Z', '2026-10-06T04:00:00Z')).toBe(true)
    expect(isNewEntry('2026-10-06T04:00:00Z', '2026-10-06T04:00:00Z')).toBe(false)
    expect(isNewEntry('2026-10-05T04:00:00Z', '2026-10-06T04:00:00Z')).toBe(false)
  })

  it('본 적이 없으면 모두 새 글이다', () => {
    expect(isNewEntry('2020-01-01T00:00:00Z', null)).toBe(true)
  })

  it('소수점 자리 수가 달라도 시각으로 비교한다', () => {
    expect(isNewEntry('2026-10-06T04:00:00.5Z', '2026-10-06T04:00:00.123456Z')).toBe(true)
    expect(isNewEntry('2026-10-06T04:00:00Z', '2026-10-06T04:00:00.000Z')).toBe(false)
  })
})

describe('newestCreatedAt · latestRestrictedAt', () => {
  it('가장 최근 시각을 고른다(순서와 상관없이)', () => {
    expect(newestCreatedAt([{ createdAt: '2026-10-05T00:00:00Z' }, { createdAt: '2026-10-06T00:00:00Z' }, { createdAt: '2026-10-04T00:00:00Z' }])).toBe(
      '2026-10-06T00:00:00Z',
    )
    expect(latestRestrictedAt([{ restrictedAt: '2026-10-05T01:00:00Z' }, { restrictedAt: '2026-10-05T02:00:00Z' }])).toBe('2026-10-05T02:00:00Z')
  })

  it('비었으면 null', () => {
    expect(newestCreatedAt([])).toBeNull()
    expect(latestRestrictedAt([])).toBeNull()
  })
})

describe('newGuestbookLabel', () => {
  it('없으면 빈 글자, 99개가 넘으면 99+', () => {
    expect(newGuestbookLabel(0)).toBe('')
    expect(newGuestbookLabel(3)).toBe('3')
    expect(newGuestbookLabel(99)).toBe('99')
    expect(newGuestbookLabel(100)).toBe('99+')
  })
})

describe('guestbookTabLabel', () => {
  it('글이 없거나 모르면 null(「방명록」만), 있으면 글 수', () => {
    expect(guestbookTabLabel(undefined)).toBeNull()
    expect(guestbookTabLabel(0)).toBeNull()
    expect(guestbookTabLabel(12)).toBe('12')
  })
})

describe('authorLabel', () => {
  it('강제 탈퇴한 사람은 「탈퇴한 사용자」', () => {
    expect(authorLabel({ author: { userId: 2, name: '이영희', profileImageUrl: null } })).toBe('이영희')
    expect(authorLabel({ author: null })).toBe('탈퇴한 사용자')
  })
})
