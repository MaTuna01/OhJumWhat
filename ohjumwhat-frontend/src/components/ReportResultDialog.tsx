import { useState } from 'react'
import { REASON_LABELS, type ReportResult, reportResultCaption, reportResultDescription, reportResultItems } from '../lib/sanctions.ts'
import { formatMonthDay } from '../lib/time.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  /** 띄울 결과들(처리 시각 오래된 순, null이면 닫힘). 고르는 것은 AppNoticeDialogs가 한다 */
  results: ReportResult[] | null
  /**
   * 닫을 때. seenIds = 본 결과들, finished = 마지막 결과에서 「확인」을 눌렀다
   * (Esc·바깥으로 닫았으면 거기까지 본 것으로 하고 남은 결과는 다음 화면에서 띄운다).
   */
  onClose: (seenIds: number[], finished: boolean) => void
}

/**
 * Figma ReportResult(00-R): 내가 프로필 모달에서 신고한 사람을 관리자가 처리하면 다음에 서비스를 열 때 한 번 알린다.
 * 조치 내용(제한·기간·초기화 / 경고 / 문제 없음 / 탈퇴 처리)만 보여주고 관리자 설명은 보여주지 않는다.
 * 결과는 처리한 순간의 사본이라 나중에 해제·탈퇴돼도 그대로다. 여러 건이면 「1 / 2」와 「다음」으로 넘긴다(SanctionNoticeDialog와 같다).
 */
export default function ReportResultDialog({ results, onClose }: Props) {
  const [index, setIndex] = useState(0)
  const result = results?.[index] ?? null
  const total = results?.length ?? 0
  const last = index >= total - 1
  const items = result ? reportResultItems(result) : []

  const close = (finished: boolean) => {
    if (results) onClose(results.slice(0, finished ? total : index + 1).map((r) => r.id), finished)
    setIndex(0)
  }

  return (
    <Modal
      open={result !== null}
      onClose={() => close(false)}
      title="신고 처리 결과"
      aside={
        total > 1 && (
          <p className="mt-1 shrink-0 text-xs text-text-tertiary tabular-nums">
            {index + 1} / {total}
          </p>
        )
      }
    >
      {result && (
        <div className="space-y-3">
          <div className="flex items-center gap-2.5">
            {/* 사진은 저장하지 않아 신고할 때 이름의 첫 글자로 보여준다. */}
            <Avatar name={result.targetName} />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">
                {result.targetName}
                {result.resolution === 'WITHDRAWN' && ' (탈퇴)'}
              </p>
              <p className="text-xs text-text-tertiary">
                {formatMonthDay(result.reportedAt)} 신고 · {REASON_LABELS[result.reason]}
              </p>
            </div>
          </div>
          <p className="text-sm leading-relaxed text-text-secondary">{reportResultDescription(result)}</p>
          {items.length > 0 && (
            <ul className="space-y-1.5 rounded-xl bg-bg-muted px-3.5 py-3 text-sm font-medium">
              {items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          )}
          <p className="text-xs text-text-tertiary">{reportResultCaption(result.resolution)}</p>
          <Button onClick={() => (last ? close(true) : setIndex(index + 1))} className="w-full">
            {last ? '확인' : '다음'}
          </Button>
        </div>
      )}
    </Modal>
  )
}
