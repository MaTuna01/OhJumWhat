import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../queries/chat.ts'
import { BUBBLE_MAX, bubbleSize, CHAT_PHOTO_FILE_MAX_BYTES, fitWithin, pickNotice, pickPhotos, viewerPhotos } from './chatPhoto.ts'

const file = (name: string, type = 'image/jpeg') => new File([new Uint8Array(1)], name, { type })

function sized(name: string, size: number, type = 'image/jpeg'): File {
  const f = file(name, type)
  Object.defineProperty(f, 'size', { value: size })
  return f
}

describe('fitWithin', () => {
  it('비율을 지키며 줄이고 키우지 않는다', () => {
    expect(fitWithin(4000, 3000, 1600, 1600)).toEqual({ width: 1600, height: 1200 })
    expect(fitWithin(1000, 3000, 1600, 1600)).toEqual({ width: 533, height: 1600 })
    expect(fitWithin(300, 200, 1600, 1600)).toEqual({ width: 300, height: 200 })
  })

  it('한 변이 0이 되지 않는다', () => {
    expect(fitWithin(5000, 1, 1600, 1600)).toEqual({ width: 1600, height: 1 })
  })
})

describe('bubbleSize', () => {
  it('사이드 카드는 200×240 안, 시트는 240×320 안이다', () => {
    expect(bubbleSize(1600, 1200, BUBBLE_MAX.side)).toEqual({ width: 200, height: 150 })
    expect(bubbleSize(1200, 1600, BUBBLE_MAX.side)).toEqual({ width: 180, height: 240 })
    expect(bubbleSize(1200, 1600, BUBBLE_MAX.sheet)).toEqual({ width: 240, height: 320 })
    expect(bubbleSize(1000, 1000, BUBBLE_MAX.sheet)).toEqual({ width: 240, height: 240 })
  })

  it('너무 길쭉한 사진은 1:2~2:1로 자른다', () => {
    expect(bubbleSize(1600, 200, BUBBLE_MAX.sheet)).toEqual({ width: 240, height: 120 })
    expect(bubbleSize(200, 1600, BUBBLE_MAX.sheet)).toEqual({ width: 160, height: 320 })
  })
})

describe('pickPhotos·pickNotice', () => {
  it('앞에서 5장만 보내고 넘친 수를 알린다', () => {
    const files = Array.from({ length: 7 }, (_, i) => file(`${i}.jpg`))
    const picked = pickPhotos(files)
    expect(picked.photos.map((f) => f.name)).toEqual(['0.jpg', '1.jpg', '2.jpg', '3.jpg', '4.jpg'])
    expect(picked.overflow).toBe(2)
    expect(pickNotice(picked)).toBe('한 번에 5장까지 보낼 수 있어요. 앞의 5장만 보낼게요.')
  })

  it('사진이 아니거나 너무 큰 파일은 빼고, 형식을 모르는 파일(.heic)은 열어 본다', () => {
    const picked = pickPhotos([file('a.pdf', 'application/pdf'), sized('big.jpg', CHAT_PHOTO_FILE_MAX_BYTES + 1), file('b.heic', ''), file('c.png', 'image/png')])
    expect(picked.photos.map((f) => f.name)).toEqual(['b.heic', 'c.png'])
    expect(picked.skipped).toBe(2)
    expect(pickNotice(picked)).toBe('사진이 아니거나 너무 큰 파일(30MB 넘게)은 빼고 보낼게요.')
    expect(pickNotice(pickPhotos([file('a.jpg')]))).toBeNull()
  })
})

describe('viewerPhotos', () => {
  const photo = (id: number, patch: Partial<NonNullable<ChatMessage['photo']>> = {}) => ({
    width: 1600,
    height: 1200,
    expired: false,
    url: `/api/chat-photos/${id}.jpg`,
    thumbnailUrl: `/api/chat-photos/${id}_t.jpg`,
    ...patch,
  })
  const message = (id: number, patch: Partial<ChatMessage> = {}): ChatMessage => ({
    id,
    author: { userId: 1, name: '김철수', profileImageUrl: null },
    body: null,
    photo: photo(id),
    createdAt: '2026-09-30T02:00:00Z',
    editedAt: null,
    deleted: false,
    ...patch,
  })

  it('지우지 않았고 기간이 지나지 않은 사진만 보낸 순으로', () => {
    const photos = viewerPhotos([
      message(1),
      message(2, { body: '글', photo: null }),
      message(3, { photo: photo(3, { expired: true, url: null, thumbnailUrl: null }) }),
      message(4, { deleted: true, photo: null }),
      message(5, { author: null }),
    ])
    expect(photos.map((p) => [p.id, p.author])).toEqual([
      [1, '김철수'],
      [5, '탈퇴한 사용자'],
    ])
  })
})
