import { useEffect, useRef, useState } from 'react'
import { useChatUnread } from '../hooks/useChatUnread.ts'
import type { ChatConnection } from '../hooks/usePollChatSocket.ts'
import ChatPanel from './ChatPanel.tsx'
import { ChatButton } from './ChatUnread.tsx'

type Props = { pollId: number; chatClosesAt: string; connection: ChatConnection }

/**
 * Figma 05-C·05-C4·05-C2: 모바일 투표 상세의 하단 고정 「💬 채팅」 버튼과 아래에서 올라오는 채팅 시트.
 * 버튼 오른쪽 위에 안 읽은 메시지 수(빨간 배지, 읽은 위치는 서버에 있어 다시 와도 남는다)를 두고,
 * 시트가 닫혀 있는 동안 남의 새 메시지가 오면 버튼 위에 4초 동안 미리보기를 띄운다. 읽음은 시트 안의 ChatPanel이 올린다.
 */
export default function ChatSheet({ pollId, chatClosesAt, connection }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)
  const { count, label, preview } = useChatUnread(pollId, !open)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <>
      <ChatButton
        ariaLabel={count > 0 ? `채팅, 안 읽은 메시지 ${label}개` : '채팅'}
        unreadLabel={label}
        preview={preview}
        onClick={() => setOpen(true)}
        opensDialog
        className="inset-x-0 bottom-4 flex items-center px-4 lg:hidden"
      >
        <span aria-hidden>💬</span> 채팅
      </ChatButton>
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
