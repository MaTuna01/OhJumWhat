import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../queries/chat.ts'
import { chatLength, chatSocketUrl, mergeChatPage, mergeMessages, parseChatPush, reconnectDelay, unseenCount } from './chat.ts'

const kim = { userId: 1, name: '김철수', profileImageUrl: null }
const lee = { userId: 2, name: '이영희', profileImageUrl: null }
const message = (id: number, patch: Partial<ChatMessage> = {}): ChatMessage => ({
  id,
  author: lee,
  body: `메시지 ${id}`,
  createdAt: '2026-09-30T02:00:00Z',
  editedAt: null,
  deleted: false,
  ...patch,
})

describe('mergeMessages', () => {
  it('ID 순으로 합치고 같은 메시지는 고친 것·지운 것을 남긴다', () => {
    const merged = mergeMessages(
      [message(1), message(3, { body: '고침', editedAt: '2026-09-30T02:05:00.123Z' })],
      [message(2), message(3), message(1, { body: null, deleted: true })],
    )
    expect(merged.map((m) => m.id)).toEqual([1, 2, 3])
    expect(merged[0]).toMatchObject({ deleted: true, body: null })
    expect(merged[2].body).toBe('고침')
  })

  it('더 늦게 고친 쪽이 최신이다(소수점 초가 있어도)', () => {
    const early = message(1, { body: '처음 고침', editedAt: '2026-09-30T02:05:00Z' })
    const later = message(1, { body: '다시 고침', editedAt: '2026-09-30T02:05:00.5Z' })
    expect(mergeMessages([later], [early])[0].body).toBe('다시 고침')
    expect(mergeMessages([early], [later])[0].body).toBe('다시 고침')
  })
})

describe('mergeChatPage', () => {
  it('최신 페이지를 다시 받아도 이전 메시지와 WebSocket으로 받은 메시지를 지우지 않는다', () => {
    const old = { messages: [message(1), message(2), message(5)], hasMore: false }
    const fresh = { messages: [message(2), message(3), message(4)], hasMore: true }
    const merged = mergeChatPage(old, fresh)
    expect(merged.messages.map((m) => m.id)).toEqual([1, 2, 3, 4, 5])
    // 더 오래된 것까지 받아 둔 쪽의 hasMore
    expect(merged.hasMore).toBe(false)
    expect(mergeChatPage(undefined, fresh)).toBe(fresh)
  })
})

describe('parseChatPush', () => {
  it('WebSocket 글을 읽고 모르는 형식은 버린다', () => {
    expect(parseChatPush(JSON.stringify({ type: 'created', message: message(1) }))?.message.id).toBe(1)
    expect(parseChatPush('{"type":"other","message":{"id":1}}')).toBeNull()
    expect(parseChatPush('not json')).toBeNull()
    expect(parseChatPush(new ArrayBuffer(1))).toBeNull()
  })
})

describe('reconnectDelay', () => {
  it('1초부터 두 배씩 늘리고 30초를 넘지 않는다', () => {
    expect([0, 1, 2, 3, 4, 5, 10].map(reconnectDelay)).toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000])
  })
})

describe('unseenCount', () => {
  it('본 뒤에 온 남의 메시지만 센다(내 글·지운 글 제외)', () => {
    const messages = [message(1), message(2, { author: kim }), message(3), message(4, { deleted: true, body: null })]
    expect(unseenCount(messages, 1, kim.userId)).toBe(1)
    expect(unseenCount(messages, 0, kim.userId)).toBe(2)
  })
})

describe('chatLength·chatSocketUrl', () => {
  it('글자 수와 연결 주소', () => {
    expect(chatLength('  안녕 😋 ')).toBe(4)
    expect(chatSocketUrl(7, { protocol: 'https:', host: 'www.ohjumwhat.cloud' })).toBe('wss://www.ohjumwhat.cloud/api/polls/7/ws')
    expect(chatSocketUrl(7, { protocol: 'http:', host: 'localhost:5173' })).toBe('ws://localhost:5173/api/polls/7/ws')
  })
})
