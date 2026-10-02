import { useEffect, useRef, useState } from 'react'
import type { ChatConnection } from '../hooks/usePollChatSocket.ts'
import { unseenCount } from '../lib/chat.ts'
import { useChatMessages } from '../queries/chat.ts'
import { useMe } from '../queries/me.ts'
import ChatPanel from './ChatPanel.tsx'

type Props = { pollId: number; chatClosesAt: string; connection: ChatConnection }

/**
 * Figma 05-C·05-C2: 모바일 투표 상세의 하단 고정 「💬 채팅」 버튼과 아래에서 올라오는 채팅 시트.
 * 시트가 닫혀 있는 동안 받은 남의 메시지 수를 「새 메시지 N」으로 보여주고, 열면 지운다(처음 받은 목록은 새 메시지로 치지 않는다).
 */
export default function ChatSheet({ pollId, chatClosesAt, connection }: Props) {
  const { data: me } = useMe()
  const { data } = useChatMessages(pollId)
  const ref = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)
  const [seenId, setSeenId] = useState<number | null>(null)
  const latestId = data?.messages.at(-1)?.id ?? 0
  if (data && (seenId === null || (open && seenId < latestId))) setSeenId(latestId)
  const unseen = !open && data && seenId !== null ? unseenCount(data.messages, seenId, me?.id) : 0

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <>
      <div className="pointer-events-none fixed inset-x-0 bottom-4 z-20 flex justify-center px-4 lg:hidden">
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-haspopup="dialog"
          className="pointer-events-auto inline-flex items-center gap-1.5 rounded-full bg-bg-brand px-5 py-3 text-sm font-bold text-text-on-brand shadow-lg hover:bg-bg-brand-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
        >
          <span aria-hidden>💬</span> 채팅
          {unseen > 0 && <span className="rounded-full bg-bg-surface px-1.5 text-xs text-text-brand">새 메시지 {unseen}</span>}
        </button>
      </div>
      <dialog
        ref={ref}
        // React는 안쪽 <dialog>(삭제 확인)의 close 이벤트도 여기로 올려 보내므로 시트 자신이 닫힐 때만 받는다.
        onClose={(e) => {
          if (e.target === e.currentTarget) setOpen(false)
        }}
        onClick={(e) => {
          if (e.target === ref.current) setOpen(false)
        }}
        aria-label="채팅"
        className="mx-auto mt-auto mb-0 h-[85dvh] max-h-none w-full max-w-3xl rounded-t-2xl bg-bg-surface p-0 text-text-primary shadow-xl backdrop:bg-bg-scrim"
      >
        {open && (
          <div className="flex h-full flex-col">
            <div className="relative flex shrink-0 items-center justify-end px-3 pt-2 pb-1">
              <span aria-hidden className="absolute left-1/2 top-2 h-1 w-10 -translate-x-1/2 rounded-full bg-border-strong" />
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="채팅 닫기"
                className="rounded-lg p-1.5 text-lg leading-none font-bold text-text-tertiary hover:bg-bg-subtle hover:text-text-secondary focus-visible:outline-2 focus-visible:outline-border-brand"
              >
                ✕
              </button>
            </div>
            <ChatPanel pollId={pollId} chatClosesAt={chatClosesAt} connection={connection} variant="sheet" />
          </div>
        )}
      </dialog>
    </>
  )
}
