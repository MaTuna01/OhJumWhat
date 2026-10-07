import { useId, useState } from 'react'
import { useLetterComposer } from '../hooks/useLetterComposer.ts'
import { ApiError } from '../lib/api.ts'
import { REPORT_REASON_MAX, reportReason } from '../lib/letters.ts'
import { REASON_LABELS, SANCTION_REASONS, type SanctionReason } from '../lib/sanctions.ts'
import { inputClass } from '../lib/ui.ts'
import type { Person } from '../queries/polls.ts'
import { useReportUser } from '../queries/sanctions.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  /** 신고할 사람(없으면 닫힘) */
  person: Person | null
  /** 「{조직 이름} 멤버」로 보여줄 조직 이름(아직 모르면 null) */
  orgName: string | null
  onClose: () => void
}

/**
 * 사람 신고(Figma 07-M5): 멤버 프로필의 「이 사람 신고하기」에서 프로필 모달 위에 연다(닫아도 프로필은 그대로).
 * 무엇이 문제인지 하나(필수)와 설명(선택, 100자, 한 줄)을 보낸다. 신고한 사실과 신고한 사람은 상대에게 알리지 않는다.
 */
export default function ReportUserDialog({ person, orgName, onClose }: Props) {
  return (
    <Modal open={person !== null} onClose={onClose} title="신고" closable>
      {person && <ReportForm key={person.userId} person={person} orgName={orgName} onClose={onClose} />}
    </Modal>
  )
}

/** 모달을 열 때마다 새로 그려져 고른 분류와 설명이 처음 상태로 돌아간다. */
function ReportForm({ person, orgName, onClose }: { person: Person; orgName: string | null; onClose: () => void }) {
  const report = useReportUser(person.userId)
  const { notify } = useLetterComposer()
  const [reason, setReason] = useState<SanctionReason | null>(null)
  const [detail, setDetail] = useState('')
  const reasonLabelId = useId()
  const detailId = useId()
  // 설명은 한 줄로 보낸다(줄바꿈은 공백으로 합친다).
  const length = [...(reportReason(detail) ?? '')].length
  const over = length > REPORT_REASON_MAX

  return (
    <form
      // 프로필 모달의 가운데 정렬 영역 안에 그려져서(DOM 안쪽의 <dialog>) 정렬을 이어받지 않게 왼쪽으로 둔다.
      className="space-y-4 text-left"
      onSubmit={(e) => {
        e.preventDefault()
        if (!reason || over || report.isPending) return
        report.mutate(
          { reason, detail },
          {
            onSuccess: () => {
              onClose()
              notify('신고했어요')
            },
          },
        )
      }}
    >
      <div className="flex items-center gap-2.5 rounded-xl bg-bg-muted px-3 py-2.5">
        <Avatar name={person.name} imageUrl={person.profileImageUrl} />
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{person.name}</p>
          {orgName && <p className="truncate text-xs text-text-tertiary">{orgName} 멤버</p>}
        </div>
      </div>
      <div>
        <p id={reasonLabelId} className="text-sm font-medium">
          무엇이 문제인가요?
        </p>
        {/* Figma ChoicePill: 하나만 고른다. 네이티브 라디오(sr-only)라 화살표 키로도 고른다. */}
        <div role="radiogroup" aria-labelledby={reasonLabelId} aria-required="true" className="mt-2 flex flex-wrap gap-2">
          {SANCTION_REASONS.map((value) => {
            const selected = reason === value
            return (
              <label
                key={value}
                className={`flex h-9 cursor-pointer items-center justify-center rounded-lg px-3 text-sm transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-border-brand ${
                  selected ? 'bg-bg-brand font-bold text-text-on-brand' : 'bg-bg-muted font-medium text-text-secondary hover:bg-bg-subtle'
                }`}
              >
                <input type="radio" name={`${reasonLabelId}-reason`} value={value} checked={selected} onChange={() => setReason(value)} className="sr-only" />
                {REASON_LABELS[value]}
              </label>
            )
          })}
        </div>
      </div>
      <div>
        <label htmlFor={detailId} className="text-sm font-medium">
          설명 (선택)
        </label>
        <textarea
          id={detailId}
          value={detail}
          onChange={(e) => setDetail(e.target.value)}
          rows={3}
          placeholder="예: 기분 나쁜 표현이 있어요"
          aria-invalid={over || undefined}
          className={`${inputClass} mt-1.5 resize-none`}
        />
        <p className={`mt-1 text-right text-xs tabular-nums ${over ? 'text-text-danger' : 'text-text-tertiary'}`}>
          {length}/{REPORT_REASON_MAX}
        </p>
      </div>
      <p className="rounded-lg bg-bg-subtle px-3 py-2.5 text-xs font-medium text-text-secondary">
        관리자가 확인한 뒤 결과를 알려 드려요. 신고한 사실과 신고한 사람은 상대에게 알리지 않아요.
      </p>
      {report.error && (
        <p role="alert" className="text-sm text-text-danger">
          {report.error instanceof ApiError ? report.error.message : '신고하지 못했어요. 연결을 확인하고 다시 시도해 주세요.'}
        </p>
      )}
      <div className="flex justify-end gap-2">
        {/* 보내는 중에 닫으면 성공 콜백(「신고했어요」)이 불리지 않아, 끝날 때까지 「취소」를 끈다. */}
        <Button variant="secondary" disabled={report.isPending} onClick={onClose}>
          취소
        </Button>
        <Button type="submit" variant="danger" disabled={reason === null || over || report.isPending}>
          신고하기
        </Button>
      </div>
    </form>
  )
}
