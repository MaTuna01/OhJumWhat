import { useState } from 'react'
import { useLocation } from 'react-router'
import { latestRestrictedAt } from '../lib/guestbook.ts'
import { formatMonthDay } from '../lib/time.ts'
import { type GuestbookWarning, useAckGuestbookWarnings, useGuestbookAlerts } from '../queries/guestbook.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

/**
 * 글 제한 경고 안내(Figma 07-M4). 관리자가 내 방명록 글을 「글 제한」하면 다음에 서비스를 열 때(어느 화면이든) 한 번 보여준다.
 * 처음 받았을 때와 화면을 옮길 때만 확인해서, 보는 도중에 폴링으로 받은 경고는 다음 화면에서 띄운다.
 * 여러 글이 제한됐으면 가장 최근 것을 보여주고, 닫으면(「확인」·Esc) 보여준 경고까지 확인한 것으로 보낸다.
 */
export default function GuestbookWarningDialog() {
  const location = useLocation()
  const alerts = useGuestbookAlerts()
  const { mutate: ack } = useAckGuestbookWarnings()
  const [checkedKey, setCheckedKey] = useState<string | null>(null)
  // 띄울 때의 경고 목록(띄운 뒤에 폴링으로 늘어난 경고는 확인하지 않은 것으로 남긴다)
  const [shown, setShown] = useState<GuestbookWarning[] | null>(null)

  if (alerts.data && checkedKey !== location.key) {
    setCheckedKey(location.key)
    if (shown === null && alerts.data.warnings.length > 0) setShown(alerts.data.warnings)
  }

  const until = shown ? latestRestrictedAt(shown) : null
  const warning = shown?.find((w) => w.restrictedAt === until) ?? null

  const close = () => {
    if (until) ack(until)
    setShown(null)
  }

  return (
    <Modal open={warning !== null} onClose={close} title="방명록 글이 제한됐어요">
      {warning && (
        <div className="space-y-4">
          <p className="text-sm leading-relaxed text-text-secondary">
            내가 남긴 방명록 글이 신고되어 관리자가 제한했어요. 다른 사람이 불쾌할 수 있는 글은 남기지 말아 주세요.
          </p>
          <blockquote className="border-l-[3px] border-border-strong px-3 py-1.5">
            <p className="text-xs font-medium text-text-tertiary">
              {warning.owner ? `${warning.owner.name}님 방명록` : '탈퇴한 사용자의 방명록'} · {formatMonthDay(warning.writtenAt)}
            </p>
            <p className="text-sm break-words text-text-secondary">{warning.body}</p>
          </blockquote>
          <Button onClick={close} className="w-full">
            확인
          </Button>
        </div>
      )}
    </Modal>
  )
}
