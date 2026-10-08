import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../queries/chat.ts'
import {
  arrivals,
  chatLength,
  chatSocketUrl,
  dividerAnchor,
  firstLine,
  previewText,
  mayHaveOlderUnread,
  mergeChatPage,
  mergeMessages,
  parseChatPush,
  reconnectDelay,
  unreadCount,
  unreadLabel,
} from './chat.ts'

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

describe('mergeMessages · 사진', () => {
  const photo = { width: 1600, height: 1200, expired: false, url: '/api/chat-photos/a.jpg', thumbnailUrl: '/api/chat-photos/a_t.jpg' }
  const expired = { width: 1600, height: 1200, expired: true, url: null, thumbnailUrl: null }

  it('다시 받은 「보관 기간 지남」이 먼저 받은 사진에 묻히지 않는다', () => {
    const fresh = message(1, { body: null, photo })
    const old = message(1, { body: null, photo: expired })
    expect(mergeMessages([fresh], [old])[0].photo?.expired).toBe(true)
    expect(mergeMessages([old], [fresh])[0].photo?.expired).toBe(true)
  })

  it('지운 사진이 가장 최신이다', () => {
    const removed = message(1, { body: null, photo: null, deleted: true })
    expect(mergeMessages([message(1, { body: null, photo: expired })], [removed])[0].deleted).toBe(true)
  })

  it('미리보기는 사진이면 「📷 사진」', () => {
    expect(previewText(message(1, { body: null, photo }))).toBe('📷 사진')
    expect(previewText(message(2, { body: '첫 줄\n둘째 줄' }))).toBe('첫 줄')
  })
})

describe('mergeChatPage', () => {
  it('최신 페이지를 다시 받아도 이전 메시지와 WebSocket으로 받은 메시지를 지우지 않는다', () => {
    const old = { messages: [message(1), message(2), message(5)], hasMore: false, lastReadId: 0 }
    const fresh = { messages: [message(2), message(3), message(4)], hasMore: true, lastReadId: 0 }
    const merged = mergeChatPage(old, fresh)
    expect(merged.messages.map((m) => m.id)).toEqual([1, 2, 3, 4, 5])
    // 더 오래된 것까지 받아 둔 쪽의 hasMore
    expect(merged.hasMore).toBe(false)
    expect(mergeChatPage(undefined, fresh)).toBe(fresh)
  })

  it('읽은 위치는 큰 쪽을 남긴다(화면에서 먼저 올린 값·다른 기기에서 읽은 값)', () => {
    const page = (lastReadId: number) => ({ messages: [message(1), message(2)], hasMore: false, lastReadId })
    expect(mergeChatPage(page(2), page(1)).lastReadId).toBe(2)
    expect(mergeChatPage(page(1), page(2)).lastReadId).toBe(2)
    // 예전 형식(읽은 위치 없음)이 섞여도 NaN이 되지 않는다
    expect(mergeChatPage({ messages: [], hasMore: false } as never, page(1)).lastReadId).toBe(1)
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

describe('unreadCount', () => {
  it('읽은 위치 뒤의 남의 메시지만 센다(내 글·지운 글 제외, 탈퇴한 사용자의 글은 센다)', () => {
    const messages = [message(1), message(2, { author: kim }), message(3), message(4, { deleted: true, body: null }), message(5, { author: null })]
    expect(unreadCount(messages, 1, kim.userId)).toBe(2)
    expect(unreadCount(messages, 0, kim.userId)).toBe(3)
    expect(unreadCount(messages, 5, kim.userId)).toBe(0)
  })

  it('내 정보를 모르면 세지 않는다(내 글을 남의 글로 세지 않게)', () => {
    expect(unreadCount([message(1)], 0, undefined)).toBe(0)
  })
})

describe('mayHaveOlderUnread·unreadLabel', () => {
  it('받은 것보다 앞부터 안 읽었으면 더 있을 수 있다', () => {
    expect(mayHaveOlderUnread({ messages: [message(51), message(52)], hasMore: true, lastReadId: 10 })).toBe(true)
    expect(mayHaveOlderUnread({ messages: [message(51), message(52)], hasMore: true, lastReadId: 51 })).toBe(false)
    expect(mayHaveOlderUnread({ messages: [message(51), message(52)], hasMore: false, lastReadId: 0 })).toBe(false)
  })

  it('배지 글자', () => {
    expect(unreadLabel(0)).toBe('')
    expect(unreadLabel(3)).toBe('3')
    expect(unreadLabel(99)).toBe('99')
    expect(unreadLabel(100)).toBe('99+')
    expect(unreadLabel(50, true)).toBe('50+')
    expect(unreadLabel(120, true)).toBe('99+')
  })
})

describe('dividerAnchor', () => {
  it('보이기 시작할 때 안 읽었던 범위의 첫 남의 메시지(내 글·지운 글은 건너뛴다)', () => {
    const messages = [message(1), message(2, { author: kim }), message(3, { deleted: true, body: null }), message(4), message(5)]
    expect(dividerAnchor(messages, 1, 5, kim.userId, false)).toBe(4)
    expect(dividerAnchor(messages, 0, 5, kim.userId, false)).toBe(1)
  })

  it('보는 동안 온 메시지(upTo 뒤)에는 긋지 않는다', () => {
    expect(dividerAnchor([message(1), message(2)], 1, 1, kim.userId, false)).toBeNull()
  })

  it('안 읽은 첫 메시지를 아직 받지 않았으면(이전 메시지) 긋지 않는다', () => {
    const messages = [message(51), message(52)]
    expect(dividerAnchor(messages, 10, 52, kim.userId, true)).toBeNull()
    expect(dividerAnchor(messages, 51, 52, kim.userId, true)).toBe(52)
  })
})

describe('arrivals·firstLine', () => {
  it('afterId 뒤에 온 남의 메시지만(내 글·지운 글·이전 메시지 제외)', () => {
    const messages = [message(1), message(2), message(3, { author: kim }), message(4, { deleted: true, body: null }), message(5)]
    expect(arrivals(messages, 2, kim.userId).map((m) => m.id)).toEqual([5])
  })

  it('미리보기는 빈 줄을 건너뛴 첫 줄', () => {
    expect(firstLine('\n  김치찌개 집 웨이팅 길대요  \n둘째 줄')).toBe('김치찌개 집 웨이팅 길대요')
    expect(firstLine(null)).toBe('')
  })
})

describe('chatLength·chatSocketUrl', () => {
  it('글자 수와 연결 주소', () => {
    expect(chatLength('  안녕 😋 ')).toBe(4)
    expect(chatSocketUrl(7, { protocol: 'https:', host: 'www.ohjumwhat.cloud' })).toBe('wss://www.ohjumwhat.cloud/api/polls/7/ws')
    expect(chatSocketUrl(7, { protocol: 'http:', host: 'localhost:5173' })).toBe('ws://localhost:5173/api/polls/7/ws')
  })
})
