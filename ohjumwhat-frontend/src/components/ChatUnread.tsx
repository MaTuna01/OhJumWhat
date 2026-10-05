import type { ReactNode, Ref } from 'react'
import { useChatUnread } from '../hooks/useChatUnread.ts'
import { firstLine } from '../lib/chat.ts'
import type { ChatMessage } from '../queries/chat.ts'

/** Figma UnreadBadge: 안 읽은 수(빨간 원). 버튼·카드 위에서 떨어져 보이게 바깥에 흰 테두리를 두른다. */
export function UnreadBadge({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={`inline-flex h-5 min-w-5 items-center justify-center gap-0.5 rounded-full bg-bg-danger px-1.5 text-xs font-medium whitespace-nowrap text-text-on-brand ring-2 ring-bg-surface ${className}`}
    >
      {children}
    </span>
  )
}

/** Figma ChatPreview: 채팅을 안 보는 중에 온 남의 메시지(이름 · 첫 줄). 누르면 채팅을 연다. */
function ChatPreview({ message, onClick }: { message: ChatMessage; onClick: () => void }) {
  return (
    <div role="status" className="pointer-events-auto max-w-[300px] min-w-0">
      <button
        type="button"
        onClick={onClick}
        className="flex w-full items-center gap-2 rounded-xl border border-border-default bg-bg-surface px-3.5 py-2.5 text-left text-sm shadow-lg hover:bg-bg-subtle focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
      >
        <span className="shrink-0 font-bold text-text-primary">{message.author?.name ?? '탈퇴한 사용자'}</span>
        <span className="truncate text-text-secondary">{firstLine(message.body)}</span>
      </button>
    </div>
  )
}

type ChatButtonProps = {
  /** 버튼 글자(「💬 채팅」·「💬 새 메시지」) */
  children: ReactNode
  ariaLabel: string
  unreadLabel: string
  preview: ChatMessage | null
  onClick: () => void
  /** 자리: 모바일은 아래 가운데, 데스크톱은 오른쪽 아래 */
  className: string
  opensDialog?: boolean
}

/** Figma ChatButton(Default·Unread·Arrival): 떠 있는 채팅 버튼. 오른쪽 위에 안 읽은 수, 위에 도착한 메시지 미리보기. */
export function ChatButton({ children, ariaLabel, unreadLabel, preview, onClick, className, opensDialog }: ChatButtonProps) {
  return (
    <div className={`pointer-events-none fixed z-20 flex-col gap-2.5 ${className}`}>
      {preview && <ChatPreview message={preview} onClick={onClick} />}
      <button
        type="button"
        onClick={onClick}
        aria-label={ariaLabel}
        aria-haspopup={opensDialog ? 'dialog' : undefined}
        className="pointer-events-auto relative inline-flex items-center gap-1.5 rounded-full bg-bg-brand px-5 py-3 text-sm font-bold text-text-on-brand shadow-lg hover:bg-bg-brand-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
      >
        {children}
        {unreadLabel && <UnreadBadge className="absolute -top-1.5 -right-1.5">{unreadLabel}</UnreadBadge>}
      </button>
    </div>
  )
}

/**
 * Figma D05-C2: 데스크톱에서 채팅 카드가 스크롤로 화면 밖에 있고 안 읽은 메시지가 있으면 오른쪽 아래에 띄운다.
 * 누르면 채팅 카드로 간다. 미리보기를 놓치지 않게 화면 밖이 아니어도 마운트해 둔다(active일 때만 그린다).
 */
export function ChatJumpButton({ pollId, active, onClick }: { pollId: number; active: boolean; onClick: () => void }) {
  const { count, label, preview } = useChatUnread(pollId, active)
  if (!active || count === 0) return null
  return (
    <ChatButton
      ariaLabel={`안 읽은 메시지 ${label}개, 채팅으로 이동`}
      unreadLabel={label}
      preview={preview}
      onClick={onClick}
      className="right-6 bottom-6 hidden items-end lg:flex"
    >
      <span aria-hidden>💬</span> 새 메시지
    </ChatButton>
  )
}

/** Figma UnreadDivider: 채팅을 열었을 때 안 읽은 첫 메시지 위 */
export function UnreadDivider() {
  return (
    <div role="separator" aria-label="여기부터 새 메시지" className="mb-3 flex items-center gap-2 text-xs font-medium text-text-brand">
      <span className="h-px flex-1 bg-border-brand-soft" />
      여기부터 새 메시지
      <span className="h-px flex-1 bg-border-brand-soft" />
    </div>
  )
}

/**
 * 채팅 목록의 끝. 목록이 화면에 보이는지 재는 표시(sticky라 위로 올려 읽는 중에도 보이는 영역의 맨 아래에 있다)이고,
 * 위로 올려 읽는 중에 새 메시지가 오면 그 위에 Figma NewMessagesPill 「새 메시지 N ↓」를 띄운다.
 */
export function ListEnd({ sentinelRef, newLabel, onJump }: { sentinelRef: Ref<HTMLLIElement>; newLabel: string; onJump: () => void }) {
  return (
    <li ref={sentinelRef} className="pointer-events-none sticky bottom-0 h-px">
      {newLabel && (
        <button
          type="button"
          onClick={onJump}
          className="pointer-events-auto absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-bg-brand px-3 py-1.5 text-xs font-medium whitespace-nowrap text-text-on-brand shadow-lg hover:bg-bg-brand-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
        >
          새 메시지 {newLabel} ↓
        </button>
      )}
    </li>
  )
}
