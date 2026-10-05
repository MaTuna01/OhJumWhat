import { useEffect, useRef, useState } from 'react'
import { arrivals, mayHaveOlderUnread, unreadCount, unreadLabel } from '../lib/chat.ts'
import { type ChatMessage, useChatMessages } from '../queries/chat.ts'
import { useMe } from '../queries/me.ts'

const PREVIEW_MS = 4000

/**
 * 채팅을 안 보는 동안(모바일 시트가 닫혀 있거나 데스크톱 채팅 카드가 화면 밖) 버튼에 보여줄 안 읽은 수와 미리보기.
 * notify일 때 남의 새 메시지가 오면 가장 최근 것을 4초 동안 미리보기로 준다. 처음 받은 목록은 미리보기하지 않는다.
 * 미리보기는 ID만 들고 그릴 때 캐시에서 찾는다(그사이 지워지면 숨기고, 고치면 고친 글을 보여준다).
 */
export function useChatUnread(pollId: number, notify: boolean): { count: number; label: string; preview: ChatMessage | null } {
  const { data: me } = useMe()
  const { data: page } = useChatMessages(pollId)
  const [previewId, setPreviewId] = useState<number | null>(null)
  const seenNewest = useRef<number | null>(null)
  const messages = page?.messages ?? []
  const newestId = messages.at(-1)?.id ?? 0
  const lastReadId = page?.lastReadId ?? 0

  useEffect(() => {
    if (!page) return
    const previous = seenNewest.current
    seenNewest.current = newestId
    if (previous === null || !notify) return
    const latest = arrivals(page.messages, Math.max(previous, page.lastReadId), me?.id).at(-1)
    if (latest) setPreviewId(latest.id)
  }, [page, newestId, notify, me?.id])

  useEffect(() => {
    if (previewId === null) return
    const timer = setTimeout(() => setPreviewId(null), PREVIEW_MS)
    return () => clearTimeout(timer)
  }, [previewId])

  const count = page ? unreadCount(messages, lastReadId, me?.id) : 0
  const preview = notify && previewId !== null && previewId > lastReadId ? (messages.find((m) => m.id === previewId && !m.deleted) ?? null) : null
  return { count, label: page ? unreadLabel(count, mayHaveOlderUnread(page)) : '', preview }
}
