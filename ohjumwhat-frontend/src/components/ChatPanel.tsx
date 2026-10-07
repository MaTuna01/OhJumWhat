import { type FormEvent, type KeyboardEvent, useLayoutEffect, useRef, useState } from 'react'
import { useChatReading } from '../hooks/useChatReading.ts'
import type { ChatConnection } from '../hooks/usePollChatSocket.ts'
import { useNow } from '../hooks/useNow.ts'
import { useRestriction } from '../hooks/useRestriction.ts'
import { CHAT_MAX, chatLength } from '../lib/chat.ts'
import { formatClock } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { type ChatMessage, useChatMessages, useDeleteMessage, useEditMessage, useLoadOlderMessages, useSendMessage } from '../queries/chat.ts'
import { useMe } from '../queries/me.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import { ChatJumpButton, ListEnd, UnreadDivider } from './ChatUnread.tsx'
import ConfirmDialog from './ConfirmDialog.tsx'
import MessageBody from './MessageBody.tsx'
import { ProfileButton } from './ProfileViewer.tsx'
import RestrictionNotice from './RestrictionNotice.tsx'

type Props = {
  pollId: number
  /** 채팅이 닫히는 시각(마감 1시간 뒤). 그 뒤에는 읽기만 한다 */
  chatClosesAt: string
  connection: ChatConnection
  /** side: 데스크톱 사이드 열의 카드(D05-C), sheet: 모바일 채팅 시트 안(05-C2), 높이를 채운다 */
  variant: 'side' | 'sheet'
}

/**
 * Figma ChatPanel(05-C2·05-C3·D05-C): 투표 채팅. 보내기·고치기·지우기는 REST, 받기는 WebSocket(usePollChatSocket)이다.
 * 남의 글은 왼쪽(아바타·이름), 내 글은 오른쪽(채팅이 열려 있을 때 고치기·삭제), 지운 글은 「삭제된 메시지예요」.
 * 아래에 붙어 있으면 새 메시지가 올 때 따라 내려간다.
 * 관리자가 채팅을 제한했으면(05-X) 입력창 대신 RestrictionNotice를 두고 내 글의 「고치기」를 숨긴다(「삭제」는 그대로).
 * 안 읽은 메시지(useChatReading): 열 때 「여기부터 새 메시지」, 위로 올려 읽는 중이면 「새 메시지 N ↓」, 데스크톱 카드가 화면 밖이면 떠 있는 버튼.
 */
export default function ChatPanel({ pollId, chatClosesAt, connection, variant }: Props) {
  const { data: me } = useMe()
  const now = useNow(10_000)
  const open = now < Date.parse(chatClosesAt)
  const blocked = useRestriction('CHAT')
  const chat = useChatMessages(pollId)
  const loadOlder = useLoadOlderMessages(pollId)
  const send = useSendMessage(pollId)
  const edit = useEditMessage(pollId)
  const remove = useDeleteMessage(pollId)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [removing, setRemoving] = useState<ChatMessage | null>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const atBottom = useRef(true)
  const reading = useChatReading(pollId, listRef, atBottom)
  const messages = chat.data?.messages ?? []
  const lastId = messages.at(-1)?.id

  // 아래에 붙어 있었으면 새 메시지를 따라 내려간다(위로 올려 읽는 중이면 그대로 둔다).
  useLayoutEffect(() => {
    const list = listRef.current
    if (list && atBottom.current) list.scrollTop = list.scrollHeight
  }, [lastId])

  const closeRemove = () => {
    remove.reset()
    setRemoving(null)
  }

  return (
    <section
      aria-label="채팅"
      className={variant === 'side' ? 'space-y-3 rounded-2xl border border-border-default bg-bg-surface p-4' : 'flex min-h-0 flex-1 flex-col gap-3 px-4 pb-4'}
    >
      <div className="flex flex-wrap items-baseline gap-x-2">
        <h2 className="font-bold">채팅</h2>
        <p className="text-xs text-text-tertiary">
          {open ? `${formatClock(chatClosesAt)}까지 열려 있어요` : `${formatClock(chatClosesAt)}에 닫혔어요 · 읽기 전용`}
          {open && connection === 'connecting' && ' · 연결 중…'}
        </p>
      </div>

      <ul
        ref={listRef}
        onScroll={(e) => {
          const list = e.currentTarget
          atBottom.current = list.scrollHeight - list.scrollTop - list.clientHeight < 80
          reading.onScroll()
        }}
        className={`space-y-3 overflow-y-auto overscroll-contain ${variant === 'side' ? 'max-h-96' : 'min-h-0 flex-1'}`}
        aria-live="polite"
      >
        {chat.data?.hasMore && (
          <li>
            <button
              type="button"
              disabled={loadOlder.isPending}
              onClick={() => messages[0] && loadOlder.mutate(messages[0].id)}
              className="text-xs font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand disabled:opacity-50"
            >
              이전 메시지 더 보기
            </button>
          </li>
        )}
        {chat.isPending && <li className="text-xs text-text-tertiary">채팅을 불러오는 중이에요</li>}
        {chat.isError && (
          <li role="alert" className="text-xs text-text-danger">
            {chat.error.message}
          </li>
        )}
        {chat.isSuccess && messages.length === 0 && (
          <li className="text-xs text-text-tertiary">{open ? '아직 메시지가 없어요. 메뉴를 먼저 의논해 보세요' : '메시지가 없어요'}</li>
        )}
        {messages.map((message) => {
          const mine = message.author != null && message.author.userId === me?.id
          return (
            <li key={message.id} className={`rounded-xl transition-colors duration-700 ${reading.freshIds.has(message.id) ? 'bg-bg-brand-soft' : ''}`}>
              {reading.dividerId === message.id && <UnreadDivider />}
              {open && !blocked && editingId === message.id ? (
                <MessageForm
                  initial={message.body ?? ''}
                  label="메시지 고치기"
                  submitLabel="저장"
                  pending={edit.isPending}
                  error={edit.error?.message}
                  onCancel={() => {
                    edit.reset()
                    setEditingId(null)
                  }}
                  onSubmit={(body) => edit.mutate({ id: message.id, body }, { onSuccess: () => setEditingId(null) })}
                />
              ) : (
                <MessageItem
                  message={message}
                  mine={mine}
                  onEdit={open && !blocked && mine && !message.deleted ? () => setEditingId(message.id) : undefined}
                  onDelete={open && mine && !message.deleted ? () => setRemoving(message) : undefined}
                />
              )}
            </li>
          )
        })}
        <ListEnd sentinelRef={reading.sentinelRef} newLabel={reading.showNewPill ? reading.unreadLabel : ''} onJump={reading.scrollToBottom} />
      </ul>

      {open && blocked ? (
        <RestrictionNotice type="CHAT" restriction={blocked} />
      ) : open ? (
        <MessageForm
          label="채팅 메시지"
          placeholder={`메시지 보내기 (${CHAT_MAX}자)`}
          submitLabel="보내기"
          primary
          pending={send.isPending}
          error={send.error?.message}
          onSubmit={(body, clear) => {
            atBottom.current = true
            send.mutate(body, { onSuccess: clear })
          }}
          hint="Enter로 보내고 Shift+Enter로 줄을 바꿔요"
        />
      ) : (
        <p className="rounded-lg bg-bg-subtle px-3 py-2 text-xs text-text-tertiary">채팅은 마감 1시간 뒤에 닫혀요. 지난 대화는 읽기만 할 수 있어요</p>
      )}

      <ConfirmDialog
        open={removing != null}
        onClose={closeRemove}
        onConfirm={() => removing && remove.mutate(removing.id, { onSuccess: closeRemove })}
        title="메시지를 삭제할까요?"
        confirmLabel="삭제"
        danger
        pending={remove.isPending}
        error={remove.error?.message}
      >
        <p className="break-words whitespace-pre-wrap">「{removing?.body}」</p>
        <p className="mt-2">채팅에는 「삭제된 메시지예요」로 남아요.</p>
      </ConfirmDialog>
      {variant === 'side' && <ChatJumpButton pollId={pollId} active={reading.inView === false} onClick={reading.reveal} />}
    </section>
  )
}

function MessageItem({ message, mine, onEdit, onDelete }: { message: ChatMessage; mine: boolean; onEdit?: () => void; onDelete?: () => void }) {
  const name = message.author?.name ?? '탈퇴한 사용자'
  const time = (
    <span className="text-text-tertiary">
      {formatClock(message.createdAt)}
      {message.editedAt && !message.deleted && ' · 수정됨'}
    </span>
  )
  const bubble = (
    <p
      className={`max-w-full rounded-xl px-3 py-2 text-sm break-words ${
        message.deleted
          ? 'bg-bg-subtle text-text-placeholder'
          : mine
            ? 'bg-bg-brand-soft text-text-primary'
            : 'border border-border-default bg-bg-surface text-text-primary'
      }`}
    >
      {message.deleted || message.body == null ? '삭제된 메시지예요' : <MessageBody body={message.body} />}
    </p>
  )

  if (mine) {
    return (
      <div className="flex flex-col items-end gap-1 pl-8">
        <div className="flex items-baseline gap-1.5 text-xs">
          {time}
          {onEdit && (
            <button type="button" onClick={onEdit} className="font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
              고치기
            </button>
          )}
          {onDelete && (
            <button type="button" onClick={onDelete} className="font-medium text-text-danger hover:underline focus-visible:outline-2 focus-visible:outline-border-brand">
              삭제
            </button>
          )}
        </div>
        {bubble}
      </div>
    )
  }
  return (
    <div className="flex gap-2 pr-8">
      <ProfileButton person={message.author} focusable={false} className="h-fit shrink-0 rounded-full">
        <Avatar name={name} imageUrl={message.author?.profileImageUrl} size="sm" />
      </ProfileButton>
      <div className="flex min-w-0 flex-col items-start gap-1">
        <div className="flex items-baseline gap-1.5 text-xs">
          <ProfileButton person={message.author} className="rounded font-medium text-text-primary hover:underline">
            {name}
          </ProfileButton>
          {time}
        </div>
        {bubble}
      </div>
    </div>
  )
}

type FormProps = {
  initial?: string
  label: string
  placeholder?: string
  submitLabel: string
  primary?: boolean
  pending: boolean
  error?: string
  hint?: string
  /** clear: 입력창을 비운다(보낸 뒤) */
  onSubmit: (body: string, clear: () => void) => void
  onCancel?: () => void
}

function MessageForm({ initial = '', label, placeholder, submitLabel, primary, pending, error, hint, onSubmit, onCancel }: FormProps) {
  const [value, setValue] = useState(initial)
  const length = chatLength(value)
  const over = length > CHAT_MAX
  const submit = (e?: FormEvent) => {
    e?.preventDefault()
    if (length === 0 || over || pending) return
    onSubmit(value.trim(), () => setValue(''))
  }
  // Enter로 보내고 Shift+Enter로 줄을 바꾼다. 한글 조합 중의 Enter는 글자를 확정하는 것이라 보내지 않는다.
  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <form onSubmit={submit} className="space-y-1.5">
      <div className="flex items-end gap-2">
        <textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={onKeyDown}
          rows={1}
          placeholder={placeholder}
          aria-label={label}
          className={`${inputClass} max-h-32 resize-none field-sizing-content`}
        />
        <Button type="submit" variant={primary ? 'primary' : 'secondary'} className="shrink-0" disabled={length === 0 || over || pending}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button variant="ghost" className="shrink-0" onClick={onCancel}>
            취소
          </Button>
        )}
      </div>
      {length > CHAT_MAX - 30 && (
        <p className={`text-right text-xs ${over ? 'text-text-danger' : 'text-text-tertiary'}`}>
          {length}/{CHAT_MAX}
        </p>
      )}
      {error && (
        <p role="alert" className="text-xs text-text-danger">
          {error}
        </p>
      )}
      {hint && <p className="hidden text-xs text-text-tertiary lg:block">{hint}</p>}
    </form>
  )
}
