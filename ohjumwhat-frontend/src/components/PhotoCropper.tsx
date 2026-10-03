import { type KeyboardEvent, type PointerEvent, useEffect, useRef, useState } from 'react'
import { type Crop, imageBox, initialCrop, MAX_ZOOM, MIN_ZOOM, panBy, sourceRect, zoomTo } from '../lib/photoCrop.ts'
import Button from './Button.tsx'

/** 보내는 사진의 한 변(px). 서버가 다시 256px로 줄인다. */
const OUTPUT_SIZE = 512

export type CroppedPhoto = { blob: Blob; dataUrl: string }

type Props = {
  image: HTMLImageElement
  onBack: () => void
  onApply: (photo: CroppedPhoto) => void
  onPickOther: () => void
}

/**
 * 사진 맞추기(Figma 03-M3). 원 안에 보일 곳을 끌어서 옮기고 슬라이더(또는 휠·+/− 키)로 확대한다.
 * 적용하면 정사각형 512px JPEG로 그린다. 미리보기는 data: 주소로 만든다(CSP가 blob: 이미지를 막는다).
 */
export default function PhotoCropper({ image, onBack, onApply, onPickOther }: Props) {
  const width = image.naturalWidth
  const height = image.naturalHeight
  const [crop, setCrop] = useState<Crop>(initialCrop)
  const [error, setError] = useState<string | null>(null)
  const viewport = useRef<HTMLDivElement>(null)
  const drag = useRef<{ id: number; x: number; y: number } | null>(null)

  // React의 onWheel은 passive라 페이지 스크롤을 막지 못해서 직접 등록한다.
  useEffect(() => {
    const el = viewport.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      setCrop((c) => zoomTo(width, height, c, c.zoom * Math.exp(-e.deltaY * 0.002)))
    }
    el.addEventListener('wheel', onWheel, { passive: false })
    return () => el.removeEventListener('wheel', onWheel)
  }, [width, height])

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    drag.current = { id: e.pointerId, x: e.clientX, y: e.clientY }
  }
  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const last = drag.current
    if (!last || last.id !== e.pointerId) return
    const size = e.currentTarget.clientWidth
    setCrop((c) => panBy(width, height, c, (e.clientX - last.x) / size, (e.clientY - last.y) / size))
    drag.current = { id: e.pointerId, x: e.clientX, y: e.clientY }
  }
  const endDrag = () => {
    drag.current = null
  }
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const step = 0.02
    // 사진을 화살표 방향으로 옮긴다(끌 때와 같다).
    const moves: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }
    if (e.key in moves) {
      e.preventDefault()
      const [dx, dy] = moves[e.key]
      setCrop((c) => panBy(width, height, c, dx, dy))
    } else if (e.key === '+' || e.key === '=') {
      setCrop((c) => zoomTo(width, height, c, c.zoom + 0.1))
    } else if (e.key === '-') {
      setCrop((c) => zoomTo(width, height, c, c.zoom - 0.1))
    }
  }

  const apply = () => {
    const canvas = document.createElement('canvas')
    canvas.width = OUTPUT_SIZE
    canvas.height = OUTPUT_SIZE
    const context = canvas.getContext('2d')
    if (!context) {
      setError('이 브라우저에서는 사진을 맞출 수 없어요.')
      return
    }
    // JPEG에는 투명도가 없으므로 흰 바탕에 그린다.
    context.fillStyle = '#fff'
    context.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
    context.imageSmoothingQuality = 'high'
    const { sx, sy, size } = sourceRect(width, height, crop)
    context.drawImage(image, sx, sy, size, size, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE)
    const dataUrl = canvas.toDataURL('image/jpeg', 0.9)
    canvas.toBlob(
      (blob) => {
        if (blob) onApply({ blob, dataUrl })
        else setError('사진을 만들지 못했어요. 다른 사진을 골라 주세요.')
      },
      'image/jpeg',
      0.9,
    )
  }

  const box = imageBox(width, height, crop)
  const zoomBy = (delta: number) => setCrop((c) => zoomTo(width, height, c, c.zoom + delta))

  return (
    <div className="space-y-4">
      <div className="flex flex-col items-center gap-3">
        <div
          ref={viewport}
          role="group"
          tabIndex={0}
          aria-label="프로필 사진 위치. 끌거나 화살표 키로 옮기고, + − 키로 확대해요."
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
          onKeyDown={onKeyDown}
          className="relative size-64 max-w-full cursor-grab touch-none overflow-hidden rounded-xl bg-bg-muted select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand active:cursor-grabbing"
        >
          <img
            src={image.src}
            alt=""
            draggable={false}
            className="pointer-events-none absolute max-w-none"
            style={{ left: `${box.left * 100}%`, top: `${box.top * 100}%`, width: `${box.width * 100}%`, height: `${box.height * 100}%` }}
          />
          {/* 원 밖은 어둡게: 원의 바깥 그림자(ring)를 넓게 깔고 뷰포트가 잘라 낸다. */}
          <div aria-hidden className="pointer-events-none absolute inset-2 rounded-full border-2 border-bg-surface ring-[256px] ring-bg-scrim" />
        </div>
        <div className="flex w-full items-center gap-3">
          <button
            type="button"
            onClick={() => zoomBy(-0.25)}
            aria-label="축소"
            className="rounded-lg px-1.5 text-lg leading-6 font-medium text-text-secondary hover:bg-bg-muted"
          >
            −
          </button>
          <input
            type="range"
            min={MIN_ZOOM}
            max={MAX_ZOOM}
            step={0.01}
            value={crop.zoom}
            onChange={(e) => setCrop((c) => zoomTo(width, height, c, Number(e.target.value)))}
            aria-label="확대 배율"
            className="min-w-0 flex-1 accent-bg-brand"
          />
          <button
            type="button"
            onClick={() => zoomBy(0.25)}
            aria-label="확대"
            className="rounded-lg px-1.5 text-lg leading-6 font-medium text-text-secondary hover:bg-bg-muted"
          >
            +
          </button>
        </div>
        <p className="text-xs text-text-tertiary">끌어서 위치를 옮기고, 슬라이더로 크기를 맞춰요.</p>
      </div>
      {error && (
        <p role="alert" className="text-sm text-text-danger">
          {error}
        </p>
      )}
      <div className="flex items-center justify-between gap-2 pt-2">
        <button type="button" onClick={onPickOther} className="text-sm font-medium text-text-secondary hover:underline">
          다른 사진 고르기
        </button>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onBack}>
            뒤로
          </Button>
          <Button onClick={apply}>적용</Button>
        </div>
      </div>
    </div>
  )
}
