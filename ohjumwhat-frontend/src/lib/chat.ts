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
 */
export function mergeChatPage(old: ChatPage | undefined, fresh: ChatPage): ChatPage {
  if (!old) return fresh
  const messages = mergeMessages(old.messages, fresh.messages)
  const oldOldest = old.messages[0]?.id ?? Infinity
  const freshOldest = fresh.messages[0]?.id ?? Infinity
  return { messages, hasMore: oldOldest < freshOldest ? old.hasMore : fresh.hasMore }
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

/** 채팅 시트가 닫혀 있는 동안 받은 남의 새 메시지 수(seenId보다 뒤, 지우지 않은 것) */
export function unseenCount(messages: ChatMessage[], seenId: number, meId: number | undefined): number {
  return messages.filter((m) => m.id > seenId && !m.deleted && m.author?.userId !== meId).length
}

/** 같은 출처의 채팅 연결 주소(https면 wss) */
export function chatSocketUrl(pollId: number, location: Pick<Location, 'protocol' | 'host'> = window.location): string {
  return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/api/polls/${pollId}/ws`
}
