import { type RefObject, useEffect, useRef, useState } from 'react'
import { arrivals, dividerAnchor, mayHaveOlderUnread, unreadCount, unreadLabel } from '../lib/chat.ts'
import { useChatMessages, useMarkChatRead } from '../queries/chat.ts'
import { useMe } from '../queries/me.ts'
import { useDocumentVisible } from './useDocumentVisible.ts'
import { useOrgId } from './useOrgId.ts'

const FRESH_MS = 2500

const NONE: ReadonlySet<number> = new Set()

export type ChatReading = {
  /** 목록 끝(sticky)에 붙인다. 이것이 화면에 보이면 채팅 목록이 보이는 것이다 */
  sentinelRef: RefObject<HTMLLIElement | null>
  /** 목록 끝이 화면 안에 있는지(처음 재기 전에는 null) */
  inView: boolean | null
  unread: number
  /** 안 읽은 수 글자(「3」·「99+」·「50+」, 없으면 빈 글자) */
  unreadLabel: string
  /** 「여기부터 새 메시지」를 그을 메시지 ID */
  dividerId: number | null
  /** 보고 있는 동안 새로 와서 잠깐 강조할 메시지 */
  freshIds: ReadonlySet<number>
  /** 위로 올려 읽는 중에 새 메시지가 왔다(「새 메시지 N ↓」) */
  showNewPill: boolean
  /** 목록의 onScroll에서 부른다(맨 아래에 닿으면 읽음) */
  onScroll: () => void
  /** 「새 메시지 N ↓」: 맨 아래로 */
  scrollToBottom: () => void
  /** 떠 있는 「💬 새 메시지」: 채팅 카드로 스크롤하고 목록은 맨 아래로 */
  reveal: () => void
}

/**
 * 채팅 목록을 보고 있는지 재고 읽은 위치를 올린다(Figma 05-C5·05-C6·D05-C2).
 * 목록 끝이 화면 안이고 탭이 보이고 목록이 맨 아래에 붙어 있을 때만 읽은 것으로 한다(위로 올려 읽는 중에 온 메시지는 안 읽음으로 남는다).
 * 보이기 시작하는 순간(시트를 열거나 카드가 화면에 들어올 때) 안 읽었던 범위에 구분선을 고정하고, 보고 있는 동안 온 메시지는 잠깐 강조한다.
 * atBottom은 ChatPanel이 스크롤할 때 재는 값이다. covered: 채팅 위를 가리는 창(사진 뷰어)이 열려 있으면 보이지 않는 것으로 한다
 * (IntersectionObserver는 가려진 것을 모른다).
 */
export function useChatReading(
  pollId: number,
  listRef: RefObject<HTMLUListElement | null>,
  atBottom: RefObject<boolean>,
  covered = false,
): ChatReading {
  const orgId = useOrgId()
  const { data: me } = useMe()
  const { data: page } = useChatMessages(pollId)
  const docVisible = useDocumentVisible()
  const markRead = useMarkChatRead(pollId, orgId)
  const sentinelRef = useRef<HTMLLIElement>(null)
  const [inView, setInView] = useState<boolean | null>(null)
  const [bottom, setBottom] = useState(true)
  const [mark, setMark] = useState<{ after: number; upTo: number } | null>(null)
  const [freshIds, setFreshIds] = useState<ReadonlySet<number>>(NONE)
  const wasVisible = useRef(false)
  const seenNewest = useRef<number | null>(null)
  const freshTimer = useRef<ReturnType<typeof setTimeout>>(undefined)

  const messages = page?.messages ?? []
  const newestId = messages.at(-1)?.id ?? 0
  const lastReadId = page?.lastReadId ?? 0
  const unread = page ? unreadCount(messages, lastReadId, me?.id) : 0
  const visible = inView === true && docVisible && !covered

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel) return
    const observer = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting))
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [])

  // 보이기 시작하면 안 읽었던 범위에 구분선을 고정하고, 보고 있고 맨 아래면 읽은 위치를 올린다.
  useEffect(() => {
    if (!page) return
    if (visible && !wasVisible.current) setMark(unread > 0 ? { after: lastReadId, upTo: newestId } : null)
    wasVisible.current = visible
    if (visible && atBottom.current && newestId > 0) markRead(newestId)
  }, [page, visible, unread, lastReadId, newestId, markRead, atBottom])

  // 보고 있는 동안 온 남의 메시지는 잠깐 강조한다(처음 받은 목록·이전 메시지는 아니다).
  useEffect(() => {
    if (!page) return
    const previous = seenNewest.current
    seenNewest.current = newestId
    if (previous === null || !visible) return
    const fresh = arrivals(page.messages, previous, me?.id)
    if (fresh.length === 0) return
    setFreshIds(new Set(fresh.map((m) => m.id)))
    clearTimeout(freshTimer.current)
    freshTimer.current = setTimeout(() => setFreshIds(NONE), FRESH_MS)
  }, [page, newestId, visible, me?.id])

  useEffect(() => () => clearTimeout(freshTimer.current), [])

  // 구분선은 내가 그 뒤에 글을 쓰면 지운다.
  const replied = mark != null && messages.some((m) => m.id > mark.upTo && m.author != null && m.author.userId === me?.id)
  const dividerId = mark && !replied ? dividerAnchor(messages, mark.after, mark.upTo, me?.id, page?.hasMore ?? false) : null

  const onScroll = () => {
    setBottom(atBottom.current)
    if (visible && atBottom.current && newestId > 0) markRead(newestId)
  }

  const scrollToBottom = () => {
    const list = listRef.current
    list?.scrollTo({ top: list.scrollHeight, behavior: 'smooth' })
  }

  // 목록을 맨 아래로 옮기면 scroll 이벤트로 ChatPanel이 atBottom을 다시 잰다(카드가 화면에 들어오면 읽음).
  const reveal = () => {
    const list = listRef.current
    if (!list) return
    list.scrollTop = list.scrollHeight
    list.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  return {
    sentinelRef,
    inView,
    unread,
    unreadLabel: page ? unreadLabel(unread, mayHaveOlderUnread(page)) : '',
    dividerId,
    freshIds,
    showNewPill: visible && !bottom && unread > 0,
    onScroll,
    scrollToBottom,
    reveal,
  }
}
