import { formatMonthDay, formatMonthDayTime } from './time.ts'

// 이용 제한(제재, 이슈 #114). 유효 제한 규칙은 서버 SanctionGuard와 같아서 함께 고친다.

/** 제한할 기능(서버 Restriction). SUSPEND(활동 정지)는 투표 참여 말고 모두 막고, 혼자만 걸린다. */
export type Restriction = 'SUSPEND' | 'POLL' | 'CHAT' | 'LETTER' | 'GUESTBOOK' | 'PROFILE'

/** 프로필 초기화(서버 ProfileReset) */
export type ProfileReset = 'NICKNAME' | 'PHOTO' | 'INTRO' | 'DETAILS'

/** 제재 사유(서버 SanctionReason) */
export type SanctionReason = 'PROFILE' | 'ABUSE' | 'SPAM' | 'ETC'

/**
 * 서버가 계산한 상태: ACTIVE(제한 중), EXPIRED(기간 끝남), LIFTED(해제됨),
 * WARNING(제한·초기화 없이 경고만), RESET_ONLY(제한 없이 초기화만)
 */
export type SanctionStatus = 'ACTIVE' | 'EXPIRED' | 'LIFTED' | 'WARNING' | 'RESET_ONLY'

/** 내 활성 제재(GET /api/me의 sanctions, 오래된 순). 끝났거나 해제된 제재는 오지 않는다. */
export type ActiveSanction = {
  id: number
  restrictions: Restriction[]
  /** 끝나는 시각. null이면 관리자가 해제할 때까지 */
  endsAt: string | null
  reason: SanctionReason
  /** 관리자 설명(본인에게 보인다). 없으면 null */
  note: string | null
  createdAt: string
}

/** 아직 확인하지 않은 제재 안내(GET /api/sanctions/alerts) */
export type SanctionNotice = {
  id: number
  restrictions: Restriction[]
  resets: ProfileReset[]
  reason: SanctionReason
  note: string | null
  createdAt: string
  endsAt: string | null
  /** 관리자가 해제한 시각(해제 전이면 null) */
  liftedAt: string | null
  status: SanctionStatus
}

/** 관리자 콘솔의 제재 한 건(회원 상세·「제재」 탭) */
export type SanctionRow = {
  id: number
  userId: number
  /** 별명, 없으면 구글 이름 */
  userName: string
  userEmail: string
  userProfileImageUrl: string | null
  restrictions: Restriction[]
  resets: ProfileReset[]
  reason: SanctionReason
  note: string | null
  createdAt: string
  endsAt: string | null
  liftedAt: string | null
  /** 건 관리자(관리자가 지워졌으면 null) */
  createdByName: string | null
  /** 해제한 관리자(해제 전이거나 관리자가 지워졌으면 null) */
  liftedByName: string | null
  status: SanctionStatus
}

/** 제재하기 요청(POST /api/admin/users/{userId}/sanctions) */
export type SanctionInput = {
  restrictions: Restriction[]
  resets: ProfileReset[]
  /** 1·3·7·30일. null이면 해제할 때까지(제한이 없으면 null이어야 한다) */
  days: number | null
  reason: SanctionReason
  note: string | null
}

/** 화면에 보여주는 순서 */
export const RESTRICTIONS: readonly Restriction[] = ['SUSPEND', 'POLL', 'CHAT', 'LETTER', 'GUESTBOOK', 'PROFILE']
export const PROFILE_RESETS: readonly ProfileReset[] = ['NICKNAME', 'PHOTO', 'INTRO', 'DETAILS']
export const SANCTION_REASONS: readonly SanctionReason[] = ['PROFILE', 'ABUSE', 'SPAM', 'ETC']
/** 고를 수 있는 기간(일) */
export const SANCTION_DAYS: readonly number[] = [1, 3, 7, 30]

/** 관리자 설명 최대 글자(코드 포인트) 수 */
export const SANCTION_NOTE_MAX = 200

export const RESTRICTION_LABELS: Record<Restriction, string> = {
  SUSPEND: '활동 정지',
  POLL: '투표 제한',
  CHAT: '채팅 금지',
  LETTER: '쪽지 금지',
  GUESTBOOK: '방명록 쓰기 금지',
  PROFILE: '프로필 수정 잠금',
}

export const RESET_LABELS: Record<ProfileReset, string> = {
  NICKNAME: '별명',
  PHOTO: '사진',
  INTRO: '소개',
  DETAILS: '상세 프로필',
}

export const REASON_LABELS: Record<SanctionReason, string> = {
  PROFILE: '부적절한 프로필',
  ABUSE: '욕설·비방',
  SPAM: '도배',
  ETC: '기타',
}

export const STATUS_LABELS: Record<SanctionStatus, string> = {
  ACTIVE: '진행 중',
  EXPIRED: '기간 끝남',
  LIFTED: '해제됨',
  WARNING: '경고',
  RESET_ONLY: '초기화',
}

/** 본인 안내 창의 초기화 항목: 무엇이 어떻게 바뀌었는지 */
export const RESET_NOTICES: Record<ProfileReset, { label: string; description: string }> = {
  NICKNAME: { label: '별명', description: '구글 이름으로 돌아갔어요' },
  PHOTO: { label: '사진', description: '구글 사진으로 돌아갔어요' },
  INTRO: { label: '한줄 소개·좋아하는 음식', description: '비웠어요' },
  DETAILS: { label: '상세 프로필', description: '비웠어요' },
}

const DAY_MS = 86_400_000

/** 제재의 기간 부분(관리자 콘솔의 해제 정보까지 받을 수 있게 liftedAt은 선택) */
type Restricting = { restrictions: readonly Restriction[]; endsAt: string | null; liftedAt?: string | null }

/** 지금 걸려 있는 제재: 제한이 있고, 해제되지 않았고, 끝나지 않았다(끝나는 시각이 없으면 해제할 때까지). */
export function isActiveSanction(sanction: Restricting, now: number): boolean {
  return sanction.restrictions.length > 0 && !sanction.liftedAt && (sanction.endsAt === null || Date.parse(sanction.endsAt) > now)
}

/** 기능 하나에 걸린 제한: 끝나는 시각(null = 해제할 때까지)과 활동 정지 때문인지 */
export type EffectiveRestriction = { endsAt: string | null; suspended: boolean }

/**
 * 기능 type이 지금 막혔는지(서버 SanctionGuard와 같은 규칙). 활성 제재 중 type이나 SUSPEND가 든 것이 하나라도 있으면 막힌다.
 * 끝나는 시각은 그 제재들 중 가장 늦은 것이고, 하나라도 해제할 때까지(null)면 null이다. 막히지 않았으면 null
 */
export function restrictionOf(sanctions: readonly Restricting[], type: Restriction, now: number): EffectiveRestriction | null {
  const blocking = sanctions.filter((s) => isActiveSanction(s, now) && (s.restrictions.includes(type) || s.restrictions.includes('SUSPEND')))
  if (blocking.length === 0) return null
  return {
    endsAt: latestEnd(blocking),
    suspended: blocking.some((s) => s.restrictions.includes('SUSPEND')),
  }
}

/** 가장 늦게 끝나는 시각. 하나라도 해제할 때까지(null)면 null */
function latestEnd(sanctions: readonly Restricting[]): string | null {
  let latest: string | null = null
  for (const s of sanctions) {
    if (s.endsAt === null) return null
    if (latest === null || Date.parse(s.endsAt) > Date.parse(latest)) latest = s.endsAt
  }
  return latest
}

/**
 * 지금 걸린 제한을 종류별로(보여주는 순서): 그 종류가 든 활성 제재 중 가장 늦게 끝나는 시각.
 * 마이페이지 「이용 제한」 카드와 관리자 회원 상세의 「지금: …」 한 줄에 쓴다.
 */
export function activeRestrictions(sanctions: readonly Restricting[], now: number): { type: Restriction; endsAt: string | null }[] {
  const active = sanctions.filter((s) => isActiveSanction(s, now))
  return RESTRICTIONS.flatMap((type) => {
    const having = active.filter((s) => s.restrictions.includes(type))
    return having.length > 0 ? [{ type, endsAt: latestEnd(having) }] : []
  })
}

/** 제한 목록을 보여주는 순서대로 */
export function sortRestrictions(restrictions: readonly Restriction[]): Restriction[] {
  return RESTRICTIONS.filter((r) => restrictions.includes(r))
}

/** 초기화 목록을 보여주는 순서대로 */
export function sortResets(resets: readonly ProfileReset[]): ProfileReset[] {
  return PROFILE_RESETS.filter((r) => resets.includes(r))
}

/** 본인에게 보여주는 끝: 「10월 14일 오후 6:00까지」, 없으면 「관리자가 해제할 때까지」 */
export function untilText(endsAt: string | null): string {
  return endsAt ? `${formatMonthDayTime(endsAt)}까지` : '관리자가 해제할 때까지'
}

/** 목록·요약에 쓰는 짧은 끝: 「10월 14일 오후 6:00까지」, 없으면 「해제할 때까지」 */
export function endText(endsAt: string | null): string {
  return endsAt ? `${formatMonthDayTime(endsAt)}까지` : '해제할 때까지'
}

/** 제재 기간(일). 서버는 정한 날 수만큼 정확히 더하므로 반올림하면 그 날 수다. */
export function periodDays(createdAt: string, endsAt: string): number {
  return Math.round((Date.parse(endsAt) - Date.parse(createdAt)) / DAY_MS)
}

/** 끝 + 기간: 「10월 14일 오후 6:00까지 (7일)」, 무기한이면 「해제할 때까지」 */
export function endWithDays(createdAt: string, endsAt: string | null): string {
  return endsAt ? `${endText(endsAt)} (${periodDays(createdAt, endsAt)}일)` : endText(null)
}

const NOTICE_TITLES: Record<Restriction, string> = {
  SUSPEND: '활동이 정지된 상태예요',
  POLL: '메뉴 올리기와 댓글이 제한돼 있어요',
  CHAT: '채팅이 제한돼 있어요',
  LETTER: '쪽지 보내기가 제한돼 있어요',
  GUESTBOOK: '방명록 쓰기가 제한돼 있어요',
  PROFILE: '프로필 수정이 잠겨 있어요',
}

/** 막힌 자리의 안내(Figma RestrictionNotice): 제목과 「끝 · 할 수 있는 것」 설명 */
export function restrictionNotice(type: Restriction, restriction: EffectiveRestriction): { title: string; description: string } {
  const title = restriction.suspended ? NOTICE_TITLES.SUSPEND : NOTICE_TITLES[type]
  const detail = restriction.suspended
    ? '투표 참여만 할 수 있어요'
    : type === 'POLL'
      ? '올라온 메뉴를 고르거나 패스하는 건 그대로 할 수 있어요'
      : type === 'PROFILE'
        ? '별명·사진·소개·상세 프로필을 바꿀 수 없어요'
        : '자세한 내용은 마이페이지에서 볼 수 있어요'
  return { title, description: `${untilText(restriction.endsAt)} · ${detail}` }
}

/** 제한 요약: 「채팅 금지 · 쪽지 금지 · 10월 14일 오후 6:00까지 (7일)」. 제한이 없으면 null */
export function restrictionSummary(restrictions: readonly Restriction[], createdAt: string, endsAt: string | null): string | null {
  if (restrictions.length === 0) return null
  return [...sortRestrictions(restrictions).map((r) => RESTRICTION_LABELS[r]), endWithDays(createdAt, endsAt)].join(' · ')
}

/** 초기화 요약: 「별명·사진 초기화」. 초기화가 없으면 null */
export function resetSummary(resets: readonly ProfileReset[]): string | null {
  if (resets.length === 0) return null
  return `${sortResets(resets)
    .map((r) => RESET_LABELS[r])
    .join('·')} 초기화`
}

/** 한 건의 내용(관리자 목록): 「채팅 금지 · 쪽지 금지 · 별명 초기화」, 둘 다 없으면 「경고만 보냄」 */
export function contentText(restrictions: readonly Restriction[], resets: readonly ProfileReset[]): string {
  const parts = sortRestrictions(restrictions).map((r) => RESTRICTION_LABELS[r])
  const reset = resetSummary(resets)
  if (reset) parts.push(reset)
  return parts.length > 0 ? parts.join(' · ') : '경고만 보냄'
}

/** 제한 이름만: 「채팅 금지 · 쪽지 금지」 */
export function restrictionLabels(restrictions: readonly Restriction[]): string {
  return sortRestrictions(restrictions)
    .map((r) => RESTRICTION_LABELS[r])
    .join(' · ')
}

/**
 * 관리자 목록의 기간 줄: 「10월 7일 오후 2:30 ~ 10월 14일 오후 2:30 (7일)」, 무기한 「… ~ 해제할 때까지」,
 * 해제했으면 「… → 10월 9일 해제(박관리)」, 제한이 없으면 건 시각만
 */
export function periodText(row: Pick<SanctionRow, 'restrictions' | 'createdAt' | 'endsAt' | 'liftedAt' | 'liftedByName'>): string {
  const start = formatMonthDayTime(row.createdAt)
  if (row.restrictions.length === 0) return start
  const end = row.endsAt ? `${formatMonthDayTime(row.endsAt)} (${periodDays(row.createdAt, row.endsAt)}일)` : '해제할 때까지'
  const lifted = row.liftedAt ? ` → ${formatMonthDay(row.liftedAt)} 해제${row.liftedByName ? `(${row.liftedByName})` : ''}` : ''
  return `${start} ~ ${end}${lifted}`
}

/** 사유 줄: 「욕설·비방 — 관리자 설명」 */
export function reasonText(reason: SanctionReason, note: string | null): string {
  return note ? `${REASON_LABELS[reason]} — ${note}` : REASON_LABELS[reason]
}

/**
 * 관리자 회원 상세의 「지금: …」: 같은 시각에 끝나는 제한끼리 묶는다.
 * 「채팅 금지 · 쪽지 금지 (10월 14일 오후 2:30까지)」, 걸린 제한이 없으면 「제한 없음」
 */
export function currentRestrictionsText(sanctions: readonly Restricting[], now: number): string {
  const groups: { endsAt: string | null; labels: string[] }[] = []
  for (const { type, endsAt } of activeRestrictions(sanctions, now)) {
    const group = groups.find((g) => g.endsAt === endsAt)
    if (group) group.labels.push(RESTRICTION_LABELS[type])
    else groups.push({ endsAt, labels: [RESTRICTION_LABELS[type]] })
  }
  if (groups.length === 0) return '제한 없음'
  return groups.map((g) => `${g.labels.join(' · ')} (${endText(g.endsAt)})`).join(', ')
}

/** 고른 제한 정리(서버와 같다): 활동 정지가 있으면 그것만, 아니면 보여주는 순서대로 */
export function normalizeRestrictions(restrictions: readonly Restriction[]): Restriction[] {
  return restrictions.includes('SUSPEND') ? ['SUSPEND'] : sortRestrictions(restrictions)
}

/** 본인 안내 창(Figma SanctionNotice)의 제목 */
export function noticeTitle(notice: Pick<SanctionNotice, 'restrictions' | 'resets' | 'status'>): string {
  if (notice.status === 'LIFTED' || notice.status === 'EXPIRED') return '이용이 제한됐었어요'
  if (notice.restrictions.includes('SUSPEND')) return '활동이 정지됐어요'
  if (notice.restrictions.length > 0) return '이용이 제한됐어요'
  if (notice.resets.length > 0) return '프로필이 초기화됐어요'
  return '관리자의 경고가 도착했어요'
}

/**
 * 본인 안내 창의 설명. restrictedNow: 지금 걸려 있는 다른 제한이 있다
 * (이미 풀린 제재를 알릴 때 「지금은 모든 기능을 쓸 수 있어요」를 붙일지).
 */
export function noticeDescription(notice: Pick<SanctionNotice, 'restrictions' | 'resets' | 'status' | 'endsAt'>, restrictedNow: boolean): string {
  const free = restrictedNow ? '' : ' 지금은 모든 기능을 쓸 수 있어요.'
  if (notice.status === 'LIFTED') return `관리자가 이 제한을 이미 풀었어요.${free}`
  if (notice.status === 'EXPIRED') return `기간이 끝나 이 제한은 이미 풀렸어요.${free}`
  if (notice.restrictions.includes('SUSPEND')) {
    return '정지된 동안에는 올라온 투표에 참여만 할 수 있어요. 메뉴 올리기·댓글·채팅·쪽지·방명록·프로필 수정은 할 수 없어요.'
  }
  if (notice.restrictions.length > 0) {
    return notice.endsAt
      ? '관리자가 아래 기능을 제한했어요. 기간이 끝나면 자동으로 풀려요. 투표 참여는 그대로 할 수 있어요.'
      : '관리자가 아래 기능을 제한했어요. 관리자가 해제할 때까지 이어져요. 투표 참여는 그대로 할 수 있어요.'
  }
  if (notice.resets.length > 0) return '관리자가 프로필 일부를 비웠어요. 다시 적을 때는 다른 멤버가 불편하지 않은 내용으로 적어 주세요.'
  return '이번에는 이용을 제한하지 않았어요. 같은 일이 반복되면 이용이 제한될 수 있어요.'
}

/** 본인 안내 창의 제한 줄 아래: 「10월 14일 오후 6:00까지 (7일)」, 해제했으면 「… → 10월 9일 해제」 */
export function noticePeriodText(notice: Pick<SanctionNotice, 'createdAt' | 'endsAt' | 'liftedAt'>): string {
  const lifted = notice.liftedAt ? ` → ${formatMonthDay(notice.liftedAt)} 해제` : ''
  return `${endWithDays(notice.createdAt, notice.endsAt)}${lifted}`
}

// 사람 신고(프로필 모달 「이 사람 신고하기」, 이슈 #115). 분류는 제재 사유(SanctionReason)를 그대로 쓴다.

/** 사람 신고 처리 결과(서버 ProfileReportResolution): ACTIONED = 제재(경고 포함), DISMISSED = 문제 없음, WITHDRAWN = 강제 탈퇴 */
export type ProfileReportResolution = 'ACTIONED' | 'DISMISSED' | 'WITHDRAWN'

export const RESOLUTION_LABELS: Record<ProfileReportResolution, string> = {
  ACTIONED: '조치함',
  DISMISSED: '문제 없음',
  WITHDRAWN: '탈퇴 처리',
}

/** 조치 내용의 사본(처리한 순간 제재에 실제로 저장된 값). 나중에 해제·탈퇴돼도 바뀌지 않는다. */
export type ReportAction = {
  restrictions: Restriction[]
  resets: ProfileReset[]
  /** 끝나는 시각(제한이 없거나 해제할 때까지면 null) */
  endsAt: string | null
}

/** 신고한 사람이 받는 처리 결과(GET /api/sanctions/alerts의 reportResults, 처리 시각 오래된 순). 관리자 설명은 오지 않는다. */
export type ReportResult = ReportAction & {
  id: number
  /** 신고할 때 이름 */
  targetName: string
  reportedAt: string
  reason: SanctionReason
  resolution: ProfileReportResolution
  resolvedAt: string
}

/** 신고할 때(또는 지금)의 프로필. 관리자가 둘을 나란히 비교한다(사진은 저장하지 않는다). */
export type ProfileSnapshot = {
  name: string
  bio: string | null
  foodTags: string[]
  hobbies: string[]
  jobTitle: string | null
}

/** 조치한 제한·초기화가 있는지(없으면 경고만) */
function hasAction(action: Pick<ReportAction, 'restrictions' | 'resets'>): boolean {
  return action.restrictions.length > 0 || action.resets.length > 0
}

/**
 * 결과 창(Figma ReportResult)의 조치 항목(ACTIONED만): 「채팅 금지 · 쪽지 금지 · 7일」(무기한이면 「· 해제할 때까지」),
 * 「별명·사진 초기화」, 둘 다 없으면 「경고 (이용은 제한하지 않았어요)」. 일수는 처리 시각부터 끝나는 시각까지를 반올림한다.
 */
export function reportResultItems(result: Pick<ReportResult, 'resolution' | 'restrictions' | 'resets' | 'endsAt' | 'resolvedAt'>): string[] {
  if (result.resolution !== 'ACTIONED') return []
  const items: string[] = []
  if (result.restrictions.length > 0) {
    const period = result.endsAt ? `${periodDays(result.resolvedAt, result.endsAt)}일` : '해제할 때까지'
    items.push([...sortRestrictions(result.restrictions).map((r) => RESTRICTION_LABELS[r]), period].join(' · '))
  }
  const reset = resetSummary(result.resets)
  if (reset) items.push(reset)
  return items.length > 0 ? items : ['경고 (이용은 제한하지 않았어요)']
}

/** 결과 창의 설명 */
export function reportResultDescription(result: Pick<ReportResult, 'resolution' | 'restrictions' | 'resets'>): string {
  switch (result.resolution) {
    case 'ACTIONED':
      return hasAction(result) ? '관리자가 신고 내용을 확인하고 아래와 같이 조치했어요.' : '관리자가 신고 내용을 확인하고 경고를 보냈어요.'
    case 'DISMISSED':
      return '관리자가 신고 내용을 확인했지만, 이번에는 조치하지 않았어요.'
    case 'WITHDRAWN':
      return '관리자가 신고 내용을 확인하고 이 사람을 탈퇴 처리했어요.'
  }
}

/** 결과 창 아래의 한 줄 */
export function reportResultCaption(resolution: ProfileReportResolution): string {
  return resolution === 'DISMISSED' ? '신고해 주셔서 고마워요. 같은 일이 계속되면 다시 신고해 주세요.' : '신고한 사실과 신고한 사람은 상대에게 알리지 않아요.'
}

/** 관리자 신고 목록의 처리 줄 앞부분: 「조치함 — 채팅 금지 · 7일 · 별명 초기화」, 「조치함 — 경고만 보냄」, 「문제 없음」, 「탈퇴 처리」 */
export function reportOutcome(resolution: ProfileReportResolution, result: ReportAction | null, resolvedAt: string): string {
  if (resolution !== 'ACTIONED') return RESOLUTION_LABELS[resolution]
  const action = result ?? { restrictions: [], resets: [], endsAt: null }
  const summary = hasAction(action) ? reportResultItems({ resolution, resolvedAt, ...action }).join(' · ') : '경고만 보냄'
  return `${RESOLUTION_LABELS.ACTIONED} — ${summary}`
}

const SNAPSHOT_LABELS = { bio: '한줄 소개', foodTags: '좋아하는 음식', hobbies: '취미', jobTitle: '직급' } as const

type SnapshotField = keyof typeof SNAPSHOT_LABELS

const SNAPSHOT_FIELDS = Object.keys(SNAPSHOT_LABELS) as SnapshotField[]

/** 항목 하나의 글(비었으면 null): 소개는 「」로 감싸고, 음식·취미는 쉼표로 잇는다. */
function fieldText(profile: ProfileSnapshot, field: SnapshotField): string | null {
  switch (field) {
    case 'bio':
      return profile.bio ? `「${profile.bio}」` : null
    case 'foodTags':
    case 'hobbies':
      return profile[field].length > 0 ? profile[field].join(', ') : null
    case 'jobTitle':
      return profile.jobTitle || null
  }
}

/** 이름과 채운 항목들: 「이름 「…」 · 한줄 소개 「…」 · 좋아하는 음식 … · 취미 … · 직급 …」 */
function profileParts(profile: ProfileSnapshot, emptied?: ProfileSnapshot): string[] {
  const parts = [`이름 「${profile.name}」`]
  for (const field of SNAPSHOT_FIELDS) {
    const text = fieldText(profile, field)
    if (text) parts.push(`${SNAPSHOT_LABELS[field]} ${text}`)
    // 신고할 때 있던 항목을 지웠으면 「한줄 소개 없음」으로 남긴다.
    else if (emptied && fieldText(emptied, field)) parts.push(`${SNAPSHOT_LABELS[field]} 없음`)
  }
  return parts
}

/** 관리자 신고 목록의 「신고할 때 — 이름 「…」 · 한줄 소개 「…」 · …」(빈 항목은 뺀다) */
export function snapshotText(snapshot: ProfileSnapshot): string {
  return `신고할 때 — ${profileParts(snapshot).join(' · ')}`
}

/**
 * 관리자 신고 목록의 「지금 — …」: 신고할 때와 같으면 「지금 — 같음」, 탈퇴했으면 「지금 — 탈퇴한 사용자」.
 * 신고할 때 있던 항목을 지웠으면 「한줄 소개 없음」처럼 남기고, 둘 다 빈 항목은 뺀다.
 */
export function currentProfileText(snapshot: ProfileSnapshot, current: ProfileSnapshot | null): string {
  if (!current) return '지금 — 탈퇴한 사용자'
  const same = current.name === snapshot.name && SNAPSHOT_FIELDS.every((field) => fieldText(current, field) === fieldText(snapshot, field))
  return same ? '지금 — 같음' : `지금 — ${profileParts(current, snapshot).join(' · ')}`
}
