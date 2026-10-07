import { type FormEvent, useId, useState } from 'react'
import { useNow } from '../hooks/useNow.ts'
import {
  normalizeRestrictions,
  type ProfileReset,
  PROFILE_RESETS,
  REASON_LABELS,
  resetSummary,
  type Restriction,
  RESTRICTIONS,
  restrictionSummary,
  SANCTION_DAYS,
  SANCTION_NOTE_MAX,
  SANCTION_REASONS,
  type SanctionReason,
  type SanctionRow,
  sortResets,
} from '../lib/sanctions.ts'
import { formatMonthDayTime } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { type AdminUserDetail, useAdminUser } from '../queries/admin.ts'
import { useApplySanction } from '../queries/sanctions.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  /** 제재할 회원 */
  userId: number
  open: boolean
  onClose: () => void
  /** 처음 고른 사유(신고에서 열 때) */
  initialReason?: SanctionReason
  /** 제재가 들어간 뒤(모달은 쓰는 쪽이 닫는다) */
  onApplied: (row: SanctionRow) => void
}

/**
 * Figma A03-M5 「제재하기」: 회원 상세·신고 탭에서 연다. 제한할 기능·기간·프로필 초기화·사유를 한 번에 고르고,
 * 고른 내용과 사유·설명이 본인에게 안내된다. 제한도 초기화도 고르지 않으면 「경고 보내기」가 된다.
 */
export default function SanctionModal({ userId, open, onClose, initialReason, onApplied }: Props) {
  return (
    <Modal open={open} onClose={onClose} title="제재하기">
      {/* 열려 있을 때만 그려져서 열 때마다 처음 상태로 시작한다. */}
      <SanctionForm userId={userId} initialReason={initialReason} onCancel={onClose} onApplied={onApplied} />
    </Modal>
  )
}

const RESTRICTION_OPTIONS: Record<Restriction, string> = {
  SUSPEND: '활동 정지 — 투표 참여만 할 수 있어요',
  POLL: '투표 제한 — 메뉴·댓글·투표 만들기·관리',
  CHAT: '채팅 금지',
  LETTER: '쪽지 금지',
  GUESTBOOK: '방명록 쓰기 금지',
  PROFILE: '프로필 수정 잠금',
}

const RESET_OPTIONS: Record<ProfileReset, { label: string; empty: string }> = {
  NICKNAME: { label: '별명 — 구글 이름으로', empty: '별명 없음' },
  PHOTO: { label: '사진 — 구글 사진으로', empty: '올린 사진 없음' },
  INTRO: { label: '소개 — 한줄 소개·좋아하는 음식', empty: '소개 비었음' },
  DETAILS: { label: '상세 프로필 — 다섯 항목 모두', empty: '상세 프로필 없음' },
}

/** 지금 비어 있어서 초기화할 것이 없는 항목 */
function emptyReset(detail: AdminUserDetail, reset: ProfileReset): boolean {
  switch (reset) {
    case 'NICKNAME':
      return detail.nickname === null
    case 'PHOTO':
      return !detail.user.customPhoto
    case 'INTRO':
      return detail.bio === null && detail.foodTags.length === 0
    case 'DETAILS':
      return detail.details === null
  }
}

const DAY_MS = 86_400_000

type FormProps = { userId: number; initialReason?: SanctionReason; onCancel: () => void; onApplied: (row: SanctionRow) => void }

function SanctionForm({ userId, initialReason, onCancel, onApplied }: FormProps) {
  const detail = useAdminUser(userId)
  const apply = useApplySanction(userId)
  const now = useNow(60_000)
  const [picked, setPicked] = useState<Restriction[]>([])
  const [days, setDays] = useState<number | null>(7)
  const [resets, setResets] = useState<ProfileReset[]>([])
  const [reason, setReason] = useState<SanctionReason | ''>(initialReason ?? '')
  const [note, setNote] = useState('')
  const noteId = useId()

  const restrictions = normalizeRestrictions(picked)
  const suspend = restrictions.includes('SUSPEND')
  // 비어 있게 된 항목(다른 관리자가 먼저 지운 경우)은 고른 것에서 뺀다.
  const pickedResets = detail.data ? sortResets(resets.filter((r) => !emptyReset(detail.data, r))) : []
  const noteLength = [...note.trim()].length
  const noteOver = noteLength > SANCTION_NOTE_MAX
  const warningOnly = restrictions.length === 0 && pickedResets.length === 0
  const canSubmit = detail.isSuccess && reason !== '' && !noteOver && !apply.isPending

  // 미리보기는 지금 건다고 보고 계산한다(실제 시각은 서버가 정한다).
  const startIso = new Date(now).toISOString()
  const endsAt = restrictions.length > 0 && days !== null ? new Date(now + days * DAY_MS).toISOString() : null
  const previewFirst = restrictionSummary(restrictions, startIso, endsAt) ?? (pickedResets.length === 0 ? '경고만 보내요' : null)
  const previewSecond = [resetSummary(pickedResets), reason ? `사유: ${REASON_LABELS[reason]}` : null].filter(Boolean).join(' · ')

  const toggleRestriction = (r: Restriction, on: boolean) => {
    // 활동 정지는 나머지를 모두 막으므로 켜면 다른 제한을 비운다.
    if (r === 'SUSPEND') setPicked(on ? ['SUSPEND'] : [])
    else setPicked((prev) => (on ? [...prev, r] : prev.filter((x) => x !== r)))
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    // mutate의 onSuccess는 그사이 모달을 닫아(취소·Esc) 이 폼이 사라지면 불리지 않는다. 그러면 신고 탭에서 제재는 들어갔는데
    // 쪽지 신고가 처리 전으로 남으므로, 폼과 상관없이 끝나는 mutateAsync로 받는다(실패는 apply.error로 보여준다).
    apply
      .mutateAsync({
        restrictions,
        resets: pickedResets,
        days: restrictions.length > 0 ? days : null,
        reason,
        note: note.trim() || null,
      })
      .then(onApplied, () => {})
  }

  if (detail.isPending) return <p className="py-6 text-center text-sm text-text-tertiary">불러오는 중…</p>
  if (detail.isError) {
    return (
      <p role="alert" className="text-sm text-text-danger">
        {detail.error.message}
      </p>
    )
  }
  const user = detail.data.user

  return (
    <form onSubmit={submit} className="space-y-5">
      <div className="-mt-2 text-sm">
        <p className="truncate text-text-secondary">
          {user.name} · {user.email}
        </p>
        <p className="text-text-tertiary">고른 내용과 사유·설명이 본인에게 안내돼요.</p>
      </div>

      <fieldset>
        <legend className="text-sm font-bold">제한할 기능</legend>
        <div className="mt-2 space-y-2">
          {RESTRICTIONS.map((r) => (
            <label key={r} className={`flex items-center gap-2 text-sm ${suspend && r !== 'SUSPEND' ? 'opacity-50' : 'cursor-pointer'}`}>
              <input
                type="checkbox"
                checked={restrictions.includes(r)}
                disabled={suspend && r !== 'SUSPEND'}
                onChange={(e) => toggleRestriction(r, e.target.checked)}
                className="size-4 accent-bg-brand"
              />
              {RESTRICTION_OPTIONS[r]}
            </label>
          ))}
        </div>
        <p className="mt-2 text-xs text-text-tertiary">활동 정지를 고르면 나머지 제한이 모두 들어가요. 투표 참여(고르기·패스·취소)는 어떤 제재로도 막지 않아요.</p>
      </fieldset>

      {restrictions.length > 0 && (
        <fieldset>
          <legend className="text-sm font-bold">기간</legend>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {[...SANCTION_DAYS, null].map((value) => {
              const selected = days === value
              return (
                <label
                  key={value ?? 'until-lifted'}
                  className={`flex h-9 cursor-pointer items-center justify-center rounded-lg px-3.5 text-sm transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-border-brand ${
                    selected ? 'bg-bg-brand font-bold text-text-on-brand' : 'bg-bg-muted font-medium text-text-secondary hover:bg-bg-subtle'
                  }`}
                >
                  <input type="radio" name="sanction-days" checked={selected} onChange={() => setDays(value)} className="sr-only" />
                  {value === null ? '해제할 때까지' : `${value}일`}
                </label>
              )
            })}
          </div>
          <p className="mt-2 text-xs text-text-tertiary">
            {endsAt ? `${formatMonthDayTime(endsAt)}에 자동으로 풀려요.` : '관리자가 해제할 때까지 이어져요.'}
          </p>
        </fieldset>
      )}

      <fieldset>
        <legend className="text-sm font-bold">프로필 초기화</legend>
        <div className="mt-2 space-y-2">
          {PROFILE_RESETS.map((r) => {
            const empty = emptyReset(detail.data, r)
            return (
              <label key={r} className={`flex items-center gap-2 text-sm ${empty ? 'opacity-50' : 'cursor-pointer'}`}>
                <input
                  type="checkbox"
                  checked={pickedResets.includes(r)}
                  disabled={empty}
                  onChange={(e) => setResets((prev) => (e.target.checked ? [...prev, r] : prev.filter((x) => x !== r)))}
                  className="size-4 accent-bg-brand"
                />
                {RESET_OPTIONS[r].label}
                {empty && <span className="text-xs text-text-tertiary">· {RESET_OPTIONS[r].empty}</span>}
              </label>
            )
          })}
        </div>
        <p className="mt-2 text-xs text-text-tertiary">해제해도 되돌리지 않아요. 비어 있는 항목은 고를 수 없어요.</p>
      </fieldset>

      <div className="space-y-2">
        <label htmlFor="sanction-reason" className="text-sm font-bold">
          사유
        </label>
        <div className="relative">
          <select
            id="sanction-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value as SanctionReason)}
            required
            className={`${inputClass} appearance-none pr-9 ${reason ? '' : 'text-text-placeholder'}`}
          >
            <option value="" disabled>
              사유를 골라 주세요
            </option>
            {SANCTION_REASONS.map((r) => (
              <option key={r} value={r} className="text-text-primary">
                {REASON_LABELS[r]}
              </option>
            ))}
          </select>
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 fill-none stroke-icon-muted stroke-2"
          >
            <path d="m6 9 6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={3}
          placeholder="관리자 설명(선택)"
          aria-label="관리자 설명"
          aria-invalid={noteOver || undefined}
          aria-describedby={noteId}
          className={`${inputClass} resize-none`}
        />
        <p id={noteId} className="flex gap-2 text-xs text-text-tertiary">
          <span className="min-w-0 flex-1">관리자 설명(선택, {SANCTION_NOTE_MAX}자)은 본인에게 보여요. 신고한 사람에게는 보이지 않아요.</span>
          <span className={`shrink-0 tabular-nums ${noteOver ? 'text-text-danger' : ''}`}>
            {noteLength}/{SANCTION_NOTE_MAX}
          </span>
        </p>
      </div>

      <div className="rounded-xl bg-bg-muted px-3.5 py-3">
        <p className="text-xs text-text-tertiary">본인에게 이렇게 안내돼요</p>
        {previewFirst && <p className="mt-1 text-sm font-bold">{previewFirst}</p>}
        {previewSecond && <p className="mt-0.5 text-sm font-bold">{previewSecond}</p>}
      </div>

      {apply.error && (
        <p role="alert" className="text-sm text-text-danger">
          {apply.error.message}
        </p>
      )}
      <div className="flex justify-end gap-2 border-t border-border-default pt-4">
        <Button variant="secondary" onClick={onCancel}>
          취소
        </Button>
        <Button type="submit" variant="danger" disabled={!canSubmit}>
          {warningOnly ? '경고 보내기' : '제재하기'}
        </Button>
      </div>
    </form>
  )
}
