import type { ChatMessage } from '../queries/chat.ts'

/** 한 번에 고를 수 있는 사진 수. 장마다 메시지 하나로 차례로 보낸다. */
export const CHAT_PHOTO_MAX_FILES = 5

/** 보내는 사진의 긴 변(px). 서버가 원본(1600px)·썸네일(480px)로 다시 그린다. */
export const CHAT_PHOTO_SIZE = 1600

/** 보내는 동안 말풍선에 보여줄 미리보기의 긴 변(px) */
const PREVIEW_SIZE = 480

/** 고를 수 있는 원본 파일 크기. 브라우저가 줄여 보내므로 넉넉하게 둔다. */
export const CHAT_PHOTO_FILE_MAX_BYTES = 30 * 1024 * 1024

/** 말풍선 사진의 최대 크기(CSS px): 데스크톱 사이드 카드·관리자 콘솔과 모바일 채팅 시트 */
export const BUBBLE_MAX = { side: { width: 200, height: 240 }, sheet: { width: 240, height: 320 } } as const

export type Size = { width: number; height: number }

/** 비율을 지키며 max 안에 들어가게 줄인다(키우지 않는다). 한 변이 0이 되지 않게 1px은 남긴다. */
export function fitWithin(width: number, height: number, maxWidth: number, maxHeight: number): Size {
  const ratio = Math.min(1, maxWidth / width, maxHeight / height)
  return { width: Math.max(1, Math.round(width * ratio)), height: Math.max(1, Math.round(height * ratio)) }
}

/**
 * 말풍선 사진의 크기. 너무 길쭉한 사진은 1:2~2:1로 잘라(object-cover) 보여주고, 작은 사진도 너무 작지 않게 긴 변을 최대에 맞춘다.
 * 사진이 뜨기 전에 이 크기로 자리를 잡아, 늦게 떠도 아래로 따라 내려가기와 읽음 판정이 어긋나지 않는다.
 */
export function bubbleSize(width: number, height: number, max: Size): Size {
  const ratio = Math.min(2, Math.max(0.5, width / height))
  const scale = Math.min(max.width / ratio, max.height)
  return { width: Math.round(scale * ratio), height: Math.round(scale) }
}

/** 고른 파일 중 보낼 것(앞에서 5장)과 넘친 수. 사진이 아닌 파일·너무 큰 파일은 오류로 따로 센다. */
export function pickPhotos(files: File[]): { photos: File[]; skipped: number; overflow: number } {
  const usable = files.filter((f) => f.type === '' || f.type.startsWith('image/'))
  const sized = usable.filter((f) => f.size <= CHAT_PHOTO_FILE_MAX_BYTES)
  return {
    photos: sized.slice(0, CHAT_PHOTO_MAX_FILES),
    skipped: files.length - sized.length,
    overflow: Math.max(0, sized.length - CHAT_PHOTO_MAX_FILES),
  }
}

/** 파일을 고른 뒤 알려줄 말(없으면 null) */
export function pickNotice({ skipped, overflow }: { skipped: number; overflow: number }): string | null {
  if (overflow > 0) return `한 번에 ${CHAT_PHOTO_MAX_FILES}장까지 보낼 수 있어요. 앞의 ${CHAT_PHOTO_MAX_FILES}장만 보낼게요.`
  if (skipped > 0) return '사진이 아니거나 너무 큰 파일(30MB 넘게)은 빼고 보낼게요.'
  return null
}

/** 뷰어에서 넘겨 볼 사진 하나 */
export type ViewerPhoto = Size & {
  id: number
  url: string
  thumbnailUrl: string
  author: string
  authorImageUrl: string | null
  createdAt: string
}

/** 뷰어에서 넘겨 볼 사진: 받아 둔 메시지 중 지우지 않았고 기간이 지나지 않은 사진(보낸 순) */
export function viewerPhotos(messages: ChatMessage[]): ViewerPhoto[] {
  return messages.flatMap((m) =>
    !m.deleted && m.photo && !m.photo.expired && m.photo.url && m.photo.thumbnailUrl
      ? [
          {
            id: m.id,
            url: m.photo.url,
            thumbnailUrl: m.photo.thumbnailUrl,
            width: m.photo.width,
            height: m.photo.height,
            author: m.author?.name ?? '탈퇴한 사용자',
            authorImageUrl: m.author?.profileImageUrl ?? null,
            createdAt: m.createdAt,
          },
        ]
      : [],
  )
}

export type PreparedPhoto = { blob: Blob; previewUrl: string } & Size

/**
 * 사진 파일을 긴 변 1600px JPEG로 줄인다(흰 바탕, 브라우저가 EXIF 방향을 반영한다). 미리보기는 data: 주소다(CSP가 blob: 이미지를 막는다).
 * 브라우저가 열지 못하는 형식(데스크톱 Chrome의 HEIC 등)이면 실패한다. 줄이지 못한 원본은 보내지 않는다(서버는 EXIF 방향을 모른다).
 */
export async function preparePhoto(file: File): Promise<PreparedPhoto> {
  const source = await decode(file)
  try {
    const size = fitWithin(source.width, source.height, CHAT_PHOTO_SIZE, CHAT_PHOTO_SIZE)
    const canvas = draw(source.image, size)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85))
    if (!blob) throw new Error('사진을 줄이지 못했어요')
    const previewSize = fitWithin(size.width, size.height, PREVIEW_SIZE, PREVIEW_SIZE)
    const previewUrl = draw(canvas, previewSize).toDataURL('image/jpeg', 0.8)
    return { blob, previewUrl, ...size }
  } finally {
    source.close()
  }
}

type Decoded = { image: CanvasImageSource; width: number; height: number; close: () => void }

/** createImageBitmap은 주소를 만들지 않아 CSP와 상관없다. 없는 브라우저는 data: 주소로 연다. */
async function decode(file: File): Promise<Decoded> {
  if (typeof createImageBitmap === 'function') {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
      return { image: bitmap, width: bitmap.width, height: bitmap.height, close: () => bitmap.close() }
    } catch {
      // 아래에서 다시 시도한다(옛 Safari는 옵션을 모른다).
    }
  }
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
  const image = new Image()
  image.src = dataUrl
  await image.decode()
  return { image, width: image.naturalWidth, height: image.naturalHeight, close: () => {} }
}

function draw(source: CanvasImageSource, { width, height }: Size): HTMLCanvasElement {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) throw new Error('사진을 그리지 못했어요')
  context.fillStyle = '#fff'
  context.fillRect(0, 0, width, height)
  context.imageSmoothingQuality = 'high'
  context.drawImage(source, 0, 0, width, height)
  return canvas
}
