import { useId, useState } from 'react'
import { useLetterComposer } from '../hooks/useLetterComposer.ts'
import { useNow } from '../hooks/useNow.ts'
import { ApiError } from '../lib/api.ts'
import { authorLabel } from '../lib/guestbook.ts'
import { REPORT_REASON_MAX, reportReason } from '../lib/letters.ts'
import { formatDayTime } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { type GuestbookEntry, useReportGuestbookEntry } from '../queries/guestbook.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  ownerId: number
  /** 신고할 글(없으면 닫힘) */
  entry: GuestbookEntry | null
  onClose: () => void
}

/** 방명록 신고(Figma 07-M2, 방명록 주인만): 사유(선택, 100자). 쪽지 신고에서 「보낸 사람 차단하기」를 뺀 모양이다. */
export default function GuestbookReportDialog({ ownerId, entry, onClose }: Props) {
  return (
    <Modal open={entry !== null} onClose={onClose} title="방명록 신고" closable>
      {entry && <ReportForm key={entry.id} ownerId={ownerId} entry={entry} onClose={onClose} />}
    </Modal>
  )
}

/** 모달을 열 때마다 새로 그려져 사유가 처음 상태로 돌아간다. */
function ReportForm({ ownerId, entry, onClose }: { ownerId: number; entry: GuestbookEntry; onClose: () => void }) {
  const report = useReportGuestbookEntry(ownerId)
  const { notify } = useLetterComposer()
  const [reason, setReason] = useState('')
  const reasonId = useId()
  // 사유는 한 줄로 보낸다(줄바꿈은 공백으로 합친다).
  const length = [...(reportReason(reason) ?? '')].length
  const now = useNow(60_000)

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault()
        report.mutate(
          { entryId: entry.id, reason },
          {
            onSuccess: () => {
              onClose()
              notify('신고했어요')
            },
          },
        )
      }}
    >
      <blockquote className="border-l-[3px] border-border-strong px-3 py-1.5">
        <p className="text-xs font-medium text-text-tertiary">
          {authorLabel(entry)} · {formatDayTime(entry.createdAt, now)}
        </p>
        <p className="line-clamp-3 text-sm break-words text-text-secondary">{entry.body}</p>
      </blockquote>
      <div>
        <label htmlFor={reasonId} className="text-sm font-medium">
          신고 사유 (선택)
        </label>
        <textarea
          id={reasonId}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder="예: 기분 나쁜 표현이 있어요"
          aria-invalid={length > REPORT_REASON_MAX || undefined}
          className={`${inputClass} mt-1.5 resize-none`}
        />
        <p className={`mt-1 text-right text-xs ${length > REPORT_REASON_MAX ? 'text-text-danger' : 'text-text-tertiary'}`}>
          {length}/{REPORT_REASON_MAX}
        </p>
      </div>
      <p className="rounded-xl bg-bg-subtle px-3.5 py-3 text-xs text-text-secondary">관리자가 확인한 뒤 글을 제한할 수 있어요. 글이 제한되면 작성자에게 내 방명록의 글이 제한됐다는 안내가 가요.</p>
      {report.error && (
        <p role="alert" className="text-sm text-text-danger">
          {report.error instanceof ApiError ? report.error.message : '신고하지 못했어요. 연결을 확인하고 다시 시도해 주세요.'}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button type="submit" variant="danger" disabled={report.isPending || length > REPORT_REASON_MAX}>
          신고하기
        </Button>
      </div>
    </form>
  )
}
