import { latestRestrictedAt } from '../lib/guestbook.ts'
import { formatMonthDay } from '../lib/time.ts'
import type { GuestbookWarning } from '../queries/guestbook.ts'
import Button from './Button.tsx'
import Modal from './Modal.tsx'

type Props = {
  /** 띄울 경고들(null이면 닫힘). 고르는 것은 AppNoticeDialogs가 한다 */
  warnings: GuestbookWarning[] | null
  /**
   * 닫을 때. until = 보여준 경고 중 가장 최근 제한 시각(이때까지 확인한 것으로 보낸다),
   * finished = 「확인」을 눌렀다(Esc·바깥으로 닫았으면 false: 다음 안내는 다음 화면에서 고른다)
   */
  onClose: (until: string | null, finished: boolean) => void
}

/**
 * 글 제한 경고 안내(Figma 07-M4). 관리자가 내 방명록 글을 「글 제한」하면 다음에 서비스를 열 때(어느 화면이든) 한 번 보여준다.
 * 여러 글이 제한됐으면 가장 최근 것을 보여주고, 닫으면 보여준 경고까지 확인한 것으로 보낸다.
 */
export default function GuestbookWarningDialog({ warnings, onClose }: Props) {
  const until = warnings ? latestRestrictedAt(warnings) : null
  const warning = warnings?.find((w) => w.restrictedAt === until) ?? null
  return (
    <Modal open={warning !== null} onClose={() => onClose(until, false)} title="방명록 글이 제한됐어요">
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
          <Button onClick={() => onClose(until, true)} className="w-full">
            확인
          </Button>
        </div>
      )}
    </Modal>
  )
}
