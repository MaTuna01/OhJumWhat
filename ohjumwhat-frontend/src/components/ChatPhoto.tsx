import { useState } from 'react'
import { bubbleSize, type Size } from '../lib/chatPhoto.ts'
import type { ChatPhoto as Photo } from '../queries/chat.ts'

type Props = {
  photo: Photo
  /** 말풍선 최대 크기(BUBBLE_MAX.side·sheet) */
  max: Size
  /** 누르면 뷰어로 크게 본다. 없으면 누를 수 없다 */
  onOpen?: () => void
  alt: string
}

/**
 * Figma ChatPhoto(Loaded·Expired·Failed): 채팅의 사진 말풍선. 썸네일을 정한 크기(bubbleSize)로 보여주고 누르면 뷰어(PhotoViewer)를 연다.
 * 사진이 뜨기 전·기간이 지난 뒤·불러오지 못했을 때도 같은 크기로 자리를 잡는다(아래로 따라 내려가기와 읽음 판정이 어긋나지 않게).
 */
export default function ChatPhoto({ photo, max, onOpen, alt }: Props) {
  const size = bubbleSize(photo.width, photo.height, max)
  const [failed, setFailed] = useState(false)
  const style = { width: size.width, height: size.height }

  if (photo.expired || !photo.thumbnailUrl || failed) {
    return (
      <div
        style={style}
        className="flex max-w-full flex-col items-center justify-center gap-1 rounded-xl border border-border-default bg-bg-subtle px-3 text-center text-xs text-text-placeholder"
      >
        <span aria-hidden className="text-xl">
          🖼
        </span>
        {photo.expired ? '보관 기간이 지난 사진이에요' : '사진을 불러올 수 없어요'}
      </div>
    )
  }
  const image = (
    <img
      src={photo.thumbnailUrl}
      alt={alt}
      width={size.width}
      height={size.height}
      loading="lazy"
      decoding="async"
      onError={() => setFailed(true)}
      className="h-full w-full bg-bg-muted object-cover"
    />
  )
  if (!onOpen) {
    return (
      <div style={style} className="max-w-full overflow-hidden rounded-xl">
        {image}
      </div>
    )
  }
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-label={`${alt} 크게 보기`}
      aria-haspopup="dialog"
      style={style}
      className="relative block max-w-full overflow-hidden rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand"
    >
      {image}
      <span aria-hidden className="absolute right-2 bottom-2 rounded-full bg-bg-scrim px-1.5 py-0.5 text-xs font-medium text-text-on-viewer">
        ⤢
      </span>
    </button>
  )
}

/** Figma ChatPhoto(Sending): 보내는 중인 사진(브라우저에서 만든 미리보기 data: 주소) */
export function SendingPhoto({ previewUrl, width, height, max }: { previewUrl: string; max: Size } & Size) {
  const size = bubbleSize(width, height, max)
  return (
    <div style={{ width: size.width, height: size.height }} className="relative max-w-full overflow-hidden rounded-xl">
      <img src={previewUrl} alt="" className="h-full w-full object-cover" />
      <span className="absolute inset-0 flex items-center justify-center bg-bg-scrim text-xs font-medium text-text-on-viewer">보내는 중…</span>
    </div>
  )
}
