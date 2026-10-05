import type { ChatMessage, ChatPage, ChatPush } from '../queries/chat.ts'

/** 메시지 최대 글자 수. 서버(ChatService)처럼 글자(코드 포인트) 수로 센다. */
export const CHAT_MAX = 300

/** 앞뒤 공백을 뺀 글자 수(이모지도 한 글자) */
export function chatLength(text: string): number {
  return [...text.trim()].length
}

/** 같은 메시지를 여러 번 받으면(목록·WebSocket·내 요청의 응답) 지운 것, 그다음 늦게 고친 것이 최신이다. */
function latest(a: ChatMessage, b: ChatMessage): ChatMessage {
  if (a.deleted !== b.deleted) return a.deleted ? a : b
  const edited = (m: ChatMessage) => (m.editedAt ? Date.parse(m.editedAt) : 0)
  return edited(b) > edited(a) ? b : a
}

/** 메시지 목록 합치기: ID로 겹치는 것은 최신으로, 순서는 ID(보낸 순) */
export function mergeMessages(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const byId = new Map(current.map((m) => [m.id, m]))
  for (const message of incoming) {
    const previous = byId.get(message.id)
    byId.set(message.id, previous ? latest(previous, message) : message)
  }
  return [...byId.values()].sort((a, b) => a.id - b.id)
}

/**
 * 최신 페이지를 다시 받았을 때 이미 있던 목록과 합친다.
 * 「이전 메시지 더 보기」로 더 오래된 것까지 받아 둔 상태면 그쪽의 hasMore를 쓴다.
 * 읽은 위치는 뒤로 가지 않는다(화면에서 먼저 올린 값과 다른 기기에서 읽은 서버 값 중 큰 쪽).
 */
export function mergeChatPage(old: ChatPage | undefined, fresh: ChatPage): ChatPage {
  if (!old) return fresh
  const messages = mergeMessages(old.messages, fresh.messages)
  const oldOldest = old.messages[0]?.id ?? Infinity
  const freshOldest = fresh.messages[0]?.id ?? Infinity
  return {
    messages,
    hasMore: oldOldest < freshOldest ? old.hasMore : fresh.hasMore,
    lastReadId: Math.max(old.lastReadId ?? 0, fresh.lastReadId ?? 0),
  }
}

/** WebSocket으로 받은 글을 읽는다. 모르는 형식이면 null */
export function parseChatPush(data: unknown): ChatPush | null {
  if (typeof data !== 'string') return null
  try {
    const push = JSON.parse(data) as Partial<ChatPush>
    if ((push.type === 'created' || push.type === 'updated' || push.type === 'deleted') && typeof push.message?.id === 'number') {
      return push as ChatPush
    }
  } catch {
    // 무시
  }
  return null
}

/** 다시 연결하기까지 기다리는 시간: 1초부터 두 배씩, 최대 30초 */
export function reconnectDelay(attempt: number): number {
  return Math.min(30_000, 1000 * 2 ** attempt)
}

/**
 * 안 읽은 메시지인지: 남이 쓴(탈퇴한 사용자의 글도 남의 글) 지우지 않은, 읽은 위치보다 뒤의 메시지.
 * 서버(ChatReadRepository.countUnread)와 같은 규칙이라 바꿀 때는 함께 고친다. 내 정보를 아직 모르면 세지 않는다.
 */
function isUnread(message: ChatMessage, lastReadId: number, meId: number | undefined): boolean {
  return meId !== undefined && message.id > lastReadId && !message.deleted && message.author?.userId !== meId
}

/** 받아 둔 메시지 중 안 읽은 수 */
export function unreadCount(messages: ChatMessage[], lastReadId: number, meId: number | undefined): number {
  return messages.filter((m) => isUnread(m, lastReadId, meId)).length
}

/** 받아 둔 것보다 오래된 안 읽은 메시지가 더 있을 수 있다(최신 50개만 받았고 그 앞부터 안 읽었다) */
export function mayHaveOlderUnread(page: ChatPage): boolean {
  return page.hasMore && (page.messages[0]?.id ?? 0) > page.lastReadId
}

/** 배지 글자: 없으면 빈 글자, 99개가 넘으면 「99+」, 더 있을 수 있으면 「50+」 */
export function unreadLabel(count: number, more = false): string {
  if (count <= 0) return ''
  if (count > 99) return '99+'
  return more ? `${count}+` : String(count)
}

/**
 * 「여기부터 새 메시지」를 그을 메시지 ID. 채팅이 보이기 시작한 순간 안 읽었던 범위(after, upTo]의 첫 메시지이고,
 * 그 뒤에 온 메시지는 구분선 대신 잠깐 강조한다. 그 범위의 첫 메시지를 아직 받지 않았으면(이전 메시지) 긋지 않는다.
 */
export function dividerAnchor(messages: ChatMessage[], after: number, upTo: number, meId: number | undefined, hasMore: boolean): number | null {
  if (hasMore && (messages[0]?.id ?? 0) > after) return null
  return messages.find((m) => m.id <= upTo && isUnread(m, after, meId))?.id ?? null
}

/** afterId 뒤에 새로 온 남의 메시지(미리보기·강조). 이전 메시지 더 보기·고친 글·내 글은 빠진다 */
export function arrivals(messages: ChatMessage[], afterId: number, meId: number | undefined): ChatMessage[] {
  return messages.filter((m) => isUnread(m, afterId, meId))
}

/** 미리보기에 쓸 첫 줄(빈 줄은 건너뛴다) */
export function firstLine(body: string | null): string {
  return body?.split('\n').find((line) => line.trim())?.trim() ?? ''
}

/** 같은 출처의 채팅 연결 주소(https면 wss) */
export function chatSocketUrl(pollId: number, location: Pick<Location, 'protocol' | 'host'> = window.location): string {
  return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/api/polls/${pollId}/ws`
}
