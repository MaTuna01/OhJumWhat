import { describe, expect, it } from 'vitest'
import {
  type ActiveSanction,
  activeRestrictions,
  contentText,
  currentRestrictionsText,
  endText,
  isActiveSanction,
  normalizeRestrictions,
  noticeDescription,
  noticePeriodText,
  noticeTitle,
  periodDays,
  periodText,
  reasonText,
  resetSummary,
  type Restriction,
  restrictionNotice,
  restrictionOf,
  restrictionSummary,
  type SanctionNotice,
  untilText,
} from './sanctions.ts'

// 2026-10-07 14:30 (한국 시간) = 05:30Z
const NOW = Date.parse('2026-10-07T05:30:00Z')
const kst = (iso: string) => new Date(`${iso}+09:00`).toISOString()

let nextId = 1
function sanction(restrictions: Restriction[], endsAt: string | null, extra: Partial<ActiveSanction> = {}): ActiveSanction {
  return { id: nextId++, restrictions, endsAt, reason: 'ABUSE', note: null, createdAt: kst('2026-10-07T14:30:00'), ...extra }
}

describe('restrictionOf(유효 제한 규칙, 서버 SanctionGuard와 같다)', () => {
  it('그 기능이 든 활성 제재가 있으면 막힌다', () => {
    const chat = sanction(['CHAT', 'LETTER'], kst('2026-10-14T18:00:00'))
    expect(restrictionOf([chat], 'CHAT', NOW)).toEqual({ endsAt: kst('2026-10-14T18:00:00'), suspended: false })
    expect(restrictionOf([chat], 'LETTER', NOW)).not.toBeNull()
    expect(restrictionOf([chat], 'POLL', NOW)).toBeNull()
    expect(restrictionOf([chat], 'SUSPEND', NOW)).toBeNull()
    expect(restrictionOf([], 'CHAT', NOW)).toBeNull()
  })

  it('활동 정지는 모든 기능을 막고 suspended로 알린다', () => {
    const suspend = sanction(['SUSPEND'], kst('2026-10-10T14:30:00'))
    for (const type of ['POLL', 'CHAT', 'LETTER', 'GUESTBOOK', 'PROFILE', 'SUSPEND'] as const) {
      expect(restrictionOf([suspend], type, NOW)).toEqual({ endsAt: kst('2026-10-10T14:30:00'), suspended: true })
    }
  })

  it('겹치면 가장 늦게 끝나는 시각, 하나라도 해제할 때까지면 null(무기한)이 이긴다', () => {
    const short = sanction(['CHAT'], kst('2026-10-08T14:30:00'))
    const long = sanction(['CHAT'], kst('2026-11-06T14:30:00'))
    const forever = sanction(['CHAT'], null)
    expect(restrictionOf([short, long], 'CHAT', NOW)?.endsAt).toBe(kst('2026-11-06T14:30:00'))
    expect(restrictionOf([long, short], 'CHAT', NOW)?.endsAt).toBe(kst('2026-11-06T14:30:00'))
    expect(restrictionOf([short, forever, long], 'CHAT', NOW)?.endsAt).toBeNull()
  })

  it('활동 정지와 그 기능의 제한이 겹치면 늦은 쪽까지, 활동 정지가 있으면 suspended', () => {
    const suspend = sanction(['SUSPEND'], kst('2026-10-08T14:30:00'))
    const chat = sanction(['CHAT'], kst('2026-10-14T14:30:00'))
    expect(restrictionOf([suspend, chat], 'CHAT', NOW)).toEqual({ endsAt: kst('2026-10-14T14:30:00'), suspended: true })
    // 채팅 제한이 없는 기능은 활동 정지만 본다.
    expect(restrictionOf([suspend, chat], 'LETTER', NOW)).toEqual({ endsAt: kst('2026-10-08T14:30:00'), suspended: true })
  })

  it('끝난 제재(끝나는 시각 ≤ 지금)는 막지 않는다', () => {
    const ends = kst('2026-10-07T14:31:00')
    const chat = sanction(['CHAT'], ends)
    expect(restrictionOf([chat], 'CHAT', Date.parse(ends) - 60_000)).not.toBeNull()
    expect(restrictionOf([chat], 'CHAT', Date.parse(ends))).toBeNull()
    expect(restrictionOf([chat], 'CHAT', Date.parse(ends) + 1)).toBeNull()
  })

  it('끝난 제재는 겹침 계산에서도 빠진다(무기한이어도 해제됐으면 빠진다)', () => {
    const expired = sanction(['CHAT'], kst('2026-10-07T14:00:00'))
    const lifted = { restrictions: ['CHAT'] as Restriction[], endsAt: null, liftedAt: kst('2026-10-07T14:00:00') }
    const active = sanction(['CHAT'], kst('2026-10-09T14:30:00'))
    expect(restrictionOf([expired, lifted, active], 'CHAT', NOW)).toEqual({ endsAt: kst('2026-10-09T14:30:00'), suspended: false })
    expect(restrictionOf([lifted], 'CHAT', NOW)).toBeNull()
  })

  it('제한이 없는 제재(경고·초기화만)는 막지 않는다', () => {
    expect(isActiveSanction(sanction([], null), NOW)).toBe(false)
    expect(restrictionOf([sanction([], null)], 'CHAT', NOW)).toBeNull()
  })
})

describe('activeRestrictions', () => {
  it('종류별로 보여주는 순서대로, 그 종류가 든 제재 중 가장 늦게 끝나는 시각', () => {
    const a = sanction(['PROFILE', 'CHAT'], kst('2026-10-14T18:00:00'))
    const b = sanction(['CHAT'], null)
    const c = sanction(['LETTER'], kst('2026-10-01T18:00:00'))
    expect(activeRestrictions([a, b, c], NOW)).toEqual([
      { type: 'CHAT', endsAt: null },
      { type: 'PROFILE', endsAt: kst('2026-10-14T18:00:00') },
    ])
  })
})

describe('시각 문구', () => {
  it('본인 안내는 「관리자가 해제할 때까지」, 목록은 「해제할 때까지」', () => {
    expect(untilText(kst('2026-10-14T18:00:00'))).toBe('10월 14일 오후 6:00까지')
    expect(untilText(null)).toBe('관리자가 해제할 때까지')
    expect(endText(kst('2026-10-14T18:00:00'))).toBe('10월 14일 오후 6:00까지')
    expect(endText(null)).toBe('해제할 때까지')
  })

  it('기간(일)', () => {
    expect(periodDays(kst('2026-10-07T14:30:00'), kst('2026-10-14T14:30:00'))).toBe(7)
    expect(periodDays(kst('2026-10-07T14:30:00'), kst('2026-11-06T14:30:00'))).toBe(30)
  })
})

describe('restrictionNotice(막힌 자리 안내)', () => {
  const until = kst('2026-10-14T18:00:00')

  it('종류별 제목과 할 수 있는 것', () => {
    expect(restrictionNotice('CHAT', { endsAt: until, suspended: false })).toEqual({
      title: '채팅이 제한돼 있어요',
      description: '10월 14일 오후 6:00까지 · 자세한 내용은 마이페이지에서 볼 수 있어요',
    })
    expect(restrictionNotice('LETTER', { endsAt: null, suspended: false })).toEqual({
      title: '쪽지 보내기가 제한돼 있어요',
      description: '관리자가 해제할 때까지 · 자세한 내용은 마이페이지에서 볼 수 있어요',
    })
    expect(restrictionNotice('GUESTBOOK', { endsAt: until, suspended: false }).title).toBe('방명록 쓰기가 제한돼 있어요')
    expect(restrictionNotice('POLL', { endsAt: until, suspended: false })).toEqual({
      title: '메뉴 올리기와 댓글이 제한돼 있어요',
      description: '10월 14일 오후 6:00까지 · 올라온 메뉴를 고르거나 패스하는 건 그대로 할 수 있어요',
    })
    expect(restrictionNotice('PROFILE', { endsAt: null, suspended: false })).toEqual({
      title: '프로필 수정이 잠겨 있어요',
      description: '관리자가 해제할 때까지 · 별명·사진·소개·상세 프로필을 바꿀 수 없어요',
    })
  })

  it('활동 정지 때문이면 어느 자리든 「활동이 정지된 상태예요」', () => {
    for (const type of ['CHAT', 'POLL', 'PROFILE', 'SUSPEND'] as const) {
      expect(restrictionNotice(type, { endsAt: until, suspended: true })).toEqual({
        title: '활동이 정지된 상태예요',
        description: '10월 14일 오후 6:00까지 · 투표 참여만 할 수 있어요',
      })
    }
  })
})

describe('요약 문구', () => {
  const created = kst('2026-10-07T18:00:00')

  it('제한 요약은 보여주는 순서대로 이름 · 끝 (기간)', () => {
    expect(restrictionSummary(['LETTER', 'CHAT'], created, kst('2026-10-14T18:00:00'))).toBe('채팅 금지 · 쪽지 금지 · 10월 14일 오후 6:00까지 (7일)')
    expect(restrictionSummary(['PROFILE'], created, null)).toBe('프로필 수정 잠금 · 해제할 때까지')
    expect(restrictionSummary([], created, null)).toBeNull()
  })

  it('초기화 요약', () => {
    expect(resetSummary(['PHOTO', 'NICKNAME'])).toBe('별명·사진 초기화')
    expect(resetSummary(['DETAILS', 'INTRO'])).toBe('소개·상세 프로필 초기화')
    expect(resetSummary([])).toBeNull()
  })

  it('관리자 목록의 내용 한 줄', () => {
    expect(contentText(['LETTER', 'CHAT'], ['NICKNAME'])).toBe('채팅 금지 · 쪽지 금지 · 별명 초기화')
    expect(contentText([], ['NICKNAME', 'INTRO'])).toBe('별명·소개 초기화')
    expect(contentText([], [])).toBe('경고만 보냄')
  })

  it('관리자 목록의 기간 줄', () => {
    const base = { liftedAt: null, liftedByName: null }
    expect(periodText({ ...base, restrictions: ['CHAT'], createdAt: kst('2026-10-07T14:30:00'), endsAt: kst('2026-10-14T14:30:00') })).toBe(
      '10월 7일 오후 2:30 ~ 10월 14일 오후 2:30 (7일)',
    )
    expect(
      periodText({ restrictions: ['PROFILE'], createdAt: kst('2026-09-01T15:00:00'), endsAt: null, liftedAt: kst('2026-09-05T10:00:00'), liftedByName: '박관리' }),
    ).toBe('9월 1일 오후 3:00 ~ 해제할 때까지 → 9월 5일 해제(박관리)')
    expect(periodText({ ...base, restrictions: [], createdAt: kst('2026-08-28T13:10:00'), endsAt: null })).toBe('8월 28일 오후 1:10')
    expect(reasonText('SPAM', '같은 메뉴를 반복해서 올렸어요.')).toBe('도배 — 같은 메뉴를 반복해서 올렸어요.')
    expect(reasonText('PROFILE', null)).toBe('부적절한 프로필')
  })

  it('관리자 회원 상세의 「지금」: 같은 시각에 끝나는 제한끼리 묶는다', () => {
    const ends = kst('2026-10-14T14:30:00')
    expect(currentRestrictionsText([sanction(['CHAT', 'LETTER'], ends)], NOW)).toBe('채팅 금지 · 쪽지 금지 (10월 14일 오후 2:30까지)')
    expect(currentRestrictionsText([sanction(['CHAT'], ends), sanction(['PROFILE'], null)], NOW)).toBe(
      '채팅 금지 (10월 14일 오후 2:30까지), 프로필 수정 잠금 (해제할 때까지)',
    )
    expect(currentRestrictionsText([sanction(['CHAT'], kst('2026-10-01T00:00:00'))], NOW)).toBe('제한 없음')
  })

  it('고른 제한 정리: 활동 정지가 있으면 그것만', () => {
    expect(normalizeRestrictions(['PROFILE', 'CHAT'])).toEqual(['CHAT', 'PROFILE'])
    expect(normalizeRestrictions(['CHAT', 'SUSPEND', 'LETTER'])).toEqual(['SUSPEND'])
  })
})

describe('본인 안내 창(SanctionNotice)', () => {
  const notice = (extra: Partial<SanctionNotice>): SanctionNotice => ({
    id: 1,
    restrictions: [],
    resets: [],
    reason: 'ABUSE',
    note: null,
    createdAt: kst('2026-10-07T18:00:00'),
    endsAt: null,
    liftedAt: null,
    status: 'WARNING',
    ...extra,
  })

  it('상태·내용별 제목', () => {
    expect(noticeTitle(notice({ restrictions: ['CHAT'], endsAt: kst('2026-10-14T18:00:00'), status: 'ACTIVE' }))).toBe('이용이 제한됐어요')
    expect(noticeTitle(notice({ restrictions: ['SUSPEND'], status: 'ACTIVE' }))).toBe('활동이 정지됐어요')
    expect(noticeTitle(notice({ resets: ['NICKNAME'], status: 'RESET_ONLY' }))).toBe('프로필이 초기화됐어요')
    expect(noticeTitle(notice({}))).toBe('관리자의 경고가 도착했어요')
    expect(noticeTitle(notice({ restrictions: ['CHAT'], status: 'LIFTED', liftedAt: kst('2026-10-09T10:00:00') }))).toBe('이용이 제한됐었어요')
    expect(noticeTitle(notice({ restrictions: ['SUSPEND'], status: 'EXPIRED' }))).toBe('이용이 제한됐었어요')
  })

  it('이미 풀린 제재는 다른 제한이 없을 때만 「지금은 모든 기능을 쓸 수 있어요」', () => {
    const lifted = notice({ restrictions: ['CHAT'], status: 'LIFTED', liftedAt: kst('2026-10-09T10:00:00') })
    expect(noticeDescription(lifted, false)).toBe('관리자가 이 제한을 이미 풀었어요. 지금은 모든 기능을 쓸 수 있어요.')
    expect(noticeDescription(lifted, true)).toBe('관리자가 이 제한을 이미 풀었어요.')
    expect(noticeDescription(notice({ restrictions: ['CHAT'], status: 'ACTIVE', endsAt: kst('2026-10-14T18:00:00') }), true)).toBe(
      '관리자가 아래 기능을 제한했어요. 기간이 끝나면 자동으로 풀려요. 투표 참여는 그대로 할 수 있어요.',
    )
    expect(noticeDescription(notice({}), false)).toBe('이번에는 이용을 제한하지 않았어요. 같은 일이 반복되면 이용이 제한될 수 있어요.')
  })

  it('제한 줄의 기간과 해제', () => {
    expect(noticePeriodText(notice({ endsAt: kst('2026-10-14T18:00:00') }))).toBe('10월 14일 오후 6:00까지 (7일)')
    expect(noticePeriodText(notice({ endsAt: kst('2026-10-14T18:00:00'), liftedAt: kst('2026-10-09T10:00:00') }))).toBe(
      '10월 14일 오후 6:00까지 (7일) → 10월 9일 해제',
    )
    expect(noticePeriodText(notice({ endsAt: null }))).toBe('해제할 때까지')
  })
})
