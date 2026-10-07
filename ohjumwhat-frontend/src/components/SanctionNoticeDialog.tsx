import { useState } from 'react'
import { useNow } from '../hooks/useNow.ts'
import {
  isActiveSanction,
  noticeDescription,
  noticePeriodText,
  noticeTitle,
  REASON_LABELS,
  RESET_NOTICES,
  restrictionLabels,
  type SanctionNotice,
  sortResets,
} from '../lib/sanctions.ts'
import { useMe } from '../queries/me.ts'
import Badge from './Badge.tsx'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  /** 띄울 안내들(오래된 순, null이면 닫힘). 고르는 것은 AppNoticeDialogs가 한다 */
  notices: SanctionNotice[] | null
  /**
   * 닫을 때. seenIds = 본 안내들, finished = 마지막 안내에서 「확인」을 눌렀다
   * (Esc·바깥으로 닫았으면 거기까지 본 것으로 하고 남은 안내는 다음 화면에서 띄운다).
   */
  onClose: (seenIds: number[], finished: boolean) => void
}

/**
 * Figma SanctionNotice(00-S): 관리자가 내게 건 제재(제한·프로필 초기화·경고)를 다음에 서비스를 열 때 한 번 알린다.
 * 여러 건이면 「1 / 2」와 「다음」으로 넘기고 마지막에 「확인」. 이미 풀린 제재(해제·기간 끝남)도 「이용이 제한됐었어요」로 알린다.
 */
export default function SanctionNoticeDialog({ notices, onClose }: Props) {
  const [index, setIndex] = useState(0)
  const { data: me } = useMe()
  const now = useNow(60_000)
  const notice = notices?.[index] ?? null
  const total = notices?.length ?? 0
  const last = index >= total - 1
  // 이미 풀린 제재를 알릴 때 「지금은 모든 기능을 쓸 수 있어요」를 붙일지
  const restrictedNow = (me?.sanctions ?? []).some((s) => isActiveSanction(s, now))

  const close = (finished: boolean) => {
    if (notices) onClose(notices.slice(0, finished ? total : index + 1).map((n) => n.id), finished)
    setIndex(0)
  }

  return (
    <Modal
      open={notice !== null}
      onClose={() => close(false)}
      title={notice ? noticeTitle(notice) : ''}
      aside={
        total > 1 && (
          <p className="mt-1 shrink-0 text-xs text-text-tertiary tabular-nums">
            {index + 1} / {total}
          </p>
        )
      }
    >
      {notice && (
        <div className="space-y-4">
          <p className="text-sm leading-relaxed text-text-secondary">{noticeDescription(notice, restrictedNow)}</p>
          {(notice.restrictions.length > 0 || notice.resets.length > 0) && (
            <div className="space-y-2.5 rounded-xl bg-bg-muted px-3.5 py-3">
              {notice.restrictions.length > 0 && (
                <div>
                  <p className="flex flex-wrap items-center gap-2 text-sm font-bold">
                    {restrictionLabels(notice.restrictions)}
                    {notice.status === 'LIFTED' && <Badge tone="neutral">해제됨</Badge>}
                    {notice.status === 'EXPIRED' && <Badge tone="neutral">기간 끝남</Badge>}
                  </p>
                  <p className="mt-0.5 text-xs text-text-secondary">{noticePeriodText(notice)}</p>
                </div>
              )}
              {sortResets(notice.resets).map((reset) => (
                <div key={reset}>
                  <p className="text-sm font-bold">{RESET_NOTICES[reset].label}</p>
                  <p className="mt-0.5 text-xs text-text-secondary">{RESET_NOTICES[reset].description}</p>
                </div>
              ))}
            </div>
          )}
          <div className="space-y-2">
            <p className="text-xs text-text-tertiary">사유 · {REASON_LABELS[notice.reason]}</p>
            {notice.note && (
              <blockquote className="border-l-[3px] border-border-strong px-3 py-1.5">
                <p className="text-xs font-medium text-text-tertiary">관리자 설명</p>
                <p className="text-sm break-words whitespace-pre-wrap text-text-secondary">{notice.note}</p>
              </blockquote>
            )}
          </div>
          <Button onClick={() => (last ? close(true) : setIndex(index + 1))} className="w-full">
            {last ? '확인' : '다음'}
          </Button>
        </div>
      )}
    </Modal>
  )
}
