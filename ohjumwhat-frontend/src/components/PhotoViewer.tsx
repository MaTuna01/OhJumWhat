import { type KeyboardEvent, type PointerEvent, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ViewerPhoto } from '../lib/chatPhoto.ts'
import { type Box, fitBox, initialView, isDoubleTap, panBy, swipeStep, type Tap, toggleZoom, zoomAt, type ZoomView } from '../lib/photoZoom.ts'
import { formatClock } from '../lib/time.ts'
import Avatar from './Avatar.tsx'

type Props = {
  /** 넘겨 볼 사진(보낸 순, lib/chatPhoto.ts viewerPhotos) */
  photos: ViewerPhoto[]
  /** 보고 있는 사진의 메시지 ID(null이면 닫혀 있다) */
  openId: number | null
  /** 다른 사진으로 넘기거나(ID) 닫는다(null) */
  onChange: (id: number | null) => void
}

/**
 * Figma PhotoViewer(05-I·05-I2·D05-I): 채팅 사진을 화면 전체로 크게 본다. 네이티브 <dialog>라 채팅 시트 위에서도 열리고 Esc로 닫힌다.
 * 같은 채팅의 다른 사진으로 ‹ ›·←→·옆으로 밀기로 넘기고, 보는 중에 사진이 지워지면 옆 사진으로 옮기거나(없으면) 닫는다.
 */
export default function PhotoViewer({ photos, openId, onChange }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const lastIndex = useRef(0)
  const open = openId != null
  const index = photos.findIndex((p) => p.id === openId)
  const photo = index >= 0 ? photos[index] : null

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  // 보던 사진이 지워졌으면(WebSocket) 그 자리의 사진으로, 남은 사진이 없으면 닫는다.
  useEffect(() => {
    if (!open) return
    if (index >= 0) {
      lastIndex.current = index
      return
    }
    onChange(photos.length > 0 ? photos[Math.min(lastIndex.current, photos.length - 1)].id : null)
  }, [open, index, photos, onChange])

  const go = (step: number) => {
    const next = photos[index + step]
    if (next) onChange(next.id)
  }

  return (
    <dialog
      ref={ref}
      // 채팅 시트(<dialog>) 안에 있어도 자기 자신이 닫힐 때만 받는다.
      onClose={(e) => {
        if (e.target === e.currentTarget) onChange(null)
      }}
      aria-label="사진 크게 보기"
      className="m-0 h-dvh max-h-none w-screen max-w-none overflow-hidden bg-bg-viewer p-0 text-text-on-viewer backdrop:bg-bg-viewer"
    >
      {open && photo && (
        <ViewerBody
          key={photo.id}
          photo={photo}
          position={`${index + 1} / ${photos.length}`}
          onPrev={index > 0 ? () => go(-1) : undefined}
          onNext={index < photos.length - 1 ? () => go(1) : undefined}
          onClose={() => onChange(null)}
        />
      )}
    </dialog>
  )
}

type BodyProps = {
  photo: ViewerPhoto
  /** 「2 / 5」 */
  position: string
  onPrev?: () => void
  onNext?: () => void
  onClose: () => void
}

type Gesture = {
  startX: number
  startY: number
  lastX: number
  lastY: number
  moved: boolean
  /** 누른 곳이 사진이었는지(빈 곳을 누르면 닫는다) */
  onImage: boolean
  pinch?: { distance: number; scale: number }
  pinched: boolean
}

/** 사진 하나를 보는 화면. 사진이 바뀌면 key로 새로 그려 배율이 처음으로 돌아간다. */
function ViewerBody({ photo, position, onPrev, onNext, onClose }: BodyProps) {
  const rootRef = useRef<HTMLDivElement>(null)
  const stageRef = useRef<HTMLDivElement>(null)
  const [stage, setStage] = useState<Box | null>(null)
  const [view, setView] = useState<ZoomView>(initialView)
  const [swipeX, setSwipeX] = useState(0)
  const [failed, setFailed] = useState(false)
  const pointers = useRef(new Map<number, { x: number; y: number }>())
  const gesture = useRef<Gesture | null>(null)
  const lastTap = useRef<Tap | null>(null)
  // 빈 곳을 누르면 닫는다. pointerup에서 바로 닫으면 뒤따르는 click이 아래 채팅 시트(사진·배경)에 떨어지므로 click에서 닫는다.
  const closeOnClick = useRef(false)
  const viewRef = useRef(view)
  const fit = stage ? fitBox(photo.width, photo.height, stage) : null
  // 휠·Safari 제스처 리스너는 한 번만 걸고 최신 크기는 ref로 읽는다.
  const sizes = useRef<{ fit: Box; stage: Box } | null>(null)
  const fitWidth = fit?.width
  const fitHeight = fit?.height
  useLayoutEffect(() => {
    viewRef.current = view
  }, [view])
  useLayoutEffect(() => {
    sizes.current = stage && fitWidth != null && fitHeight != null ? { fit: { width: fitWidth, height: fitHeight }, stage } : null
  }, [stage, fitWidth, fitHeight])

  // 키(←→·+−0)를 받도록 본문에 포커스를 둔다. 사진(포커스를 받지 않는 곳)을 눌러도 포커스가 본문에 머문다.
  useEffect(() => {
    rootRef.current?.focus({ preventScroll: true })
  }, [])

  useEffect(() => {
    const el = stageRef.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => setStage({ width: entry.contentRect.width, height: entry.contentRect.height }))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  // React의 onWheel은 passive라 페이지 확대·스크롤을 막지 못해서 직접 건다. 트랙패드 핀치(Chrome)는 ctrlKey가 붙은 휠로 온다.
  // Safari 트랙패드 핀치는 gesture* 이벤트로 오므로 막고 같은 확대로 바꾼다.
  useEffect(() => {
    const el = stageRef.current
    if (!el) return
    const local = (clientX: number, clientY: number) => {
      const rect = el.getBoundingClientRect()
      return { x: clientX - rect.left - rect.width / 2, y: clientY - rect.top - rect.height / 2 }
    }
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const s = sizes.current
      if (!s) return
      const factor = Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.002))
      setView((v) => zoomAt(v, v.scale * factor, local(e.clientX, e.clientY), s.fit, s.stage))
    }
    let gestureStart = 1
    let gestureView = initialView
    type SafariGesture = Event & { scale: number; clientX: number; clientY: number }
    // iOS는 손가락 핀치에도 gesture*를 보낸다. 그때는 포인터 이벤트가 이미 핀치를 맡으므로 페이지 확대만 막는다.
    // iOS의 GestureEvent에는 좌표가 없어서 그때는 가운데를 기준으로 한다.
    const onGestureStart = (e: Event) => {
      e.preventDefault()
      gestureStart = (e as SafariGesture).scale || 1
      gestureView = viewRef.current
    }
    const onGestureChange = (e: Event) => {
      e.preventDefault()
      const s = sizes.current
      const g = e as SafariGesture
      if (!s || pointers.current.size > 0 || !Number.isFinite(g.scale)) return
      const point = Number.isFinite(g.clientX) && Number.isFinite(g.clientY) ? local(g.clientX, g.clientY) : { x: 0, y: 0 }
      setView(zoomAt(gestureView, gestureView.scale * (g.scale / gestureStart), point, s.fit, s.stage))
    }
    el.addEventListener('wheel', onWheel, { passive: false })
    el.addEventListener('gesturestart', onGestureStart)
    el.addEventListener('gesturechange', onGestureChange)
    return () => {
      el.removeEventListener('wheel', onWheel)
      el.removeEventListener('gesturestart', onGestureStart)
      el.removeEventListener('gesturechange', onGestureChange)
    }
  }, [])

  const local = (clientX: number, clientY: number) => {
    const rect = stageRef.current!.getBoundingClientRect()
    return { x: clientX - rect.left - rect.width / 2, y: clientY - rect.top - rect.height / 2 }
  }

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    closeOnClick.current = false
    e.currentTarget.setPointerCapture(e.pointerId)
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY })
    if (pointers.current.size === 1) {
      gesture.current = {
        startX: e.clientX,
        startY: e.clientY,
        lastX: e.clientX,
        lastY: e.clientY,
        moved: false,
        onImage: e.target instanceof HTMLImageElement,
        pinched: false,
      }
    } else if (pointers.current.size === 2 && gesture.current) {
      const [a, b] = [...pointers.current.values()]
      gesture.current.pinch = { distance: Math.hypot(a.x - b.x, a.y - b.y), scale: view.scale }
      gesture.current.moved = true
      gesture.current.pinched = true
      setSwipeX(0)
    }
  }

  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    const g = gesture.current
    if (!g || !pointers.current.has(e.pointerId) || !fit || !stage) return
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY })
    if (g.pinch && pointers.current.size >= 2) {
      const [a, b] = [...pointers.current.values()]
      const center = local((a.x + b.x) / 2, (a.y + b.y) / 2)
      const scale = g.pinch.scale * (Math.hypot(a.x - b.x, a.y - b.y) / g.pinch.distance)
      setView((v) => zoomAt(v, scale, center, fit, stage))
      return
    }
    const dx = e.clientX - g.lastX
    const dy = e.clientY - g.lastY
    g.lastX = e.clientX
    g.lastY = e.clientY
    if (Math.hypot(e.clientX - g.startX, e.clientY - g.startY) > 8) g.moved = true
    if (view.scale > 1) setView((v) => panBy(v, dx, dy, fit, stage))
    else if (!g.pinched && (onPrev || onNext)) setSwipeX(e.clientX - g.startX)
  }

  const onPointerUp = (e: PointerEvent<HTMLDivElement>) => {
    if (!pointers.current.delete(e.pointerId)) return
    const g = gesture.current
    if (!g) return
    if (pointers.current.size > 0) {
      // 핀치에서 한 손가락을 떼면 남은 손가락으로 이어서 옮긴다.
      const [rest] = [...pointers.current.values()]
      g.pinch = undefined
      g.lastX = rest.x
      g.lastY = rest.y
      return
    }
    gesture.current = null
    setSwipeX(0)
    if (!g.moved) {
      const tap = { time: e.timeStamp, x: e.clientX, y: e.clientY }
      if (!g.onImage) {
        closeOnClick.current = view.scale === 1
        return
      }
      if (isDoubleTap(lastTap.current, tap) && fit && stage) {
        lastTap.current = null
        setView((v) => toggleZoom(v, local(e.clientX, e.clientY), fit, stage))
      } else {
        lastTap.current = tap
      }
      return
    }
    if (view.scale === 1 && !g.pinched) {
      const step = swipeStep(e.clientX - g.startX, e.clientY - g.startY)
      if (step < 0) onPrev?.()
      if (step > 0) onNext?.()
    }
  }

  const onPointerCancel = (e: PointerEvent<HTMLDivElement>) => {
    pointers.current.delete(e.pointerId)
    if (pointers.current.size === 0) {
      gesture.current = null
      setSwipeX(0)
    }
  }

  const zoomBy = (factor: number) => {
    if (fit && stage) setView((v) => zoomAt(v, v.scale * factor, { x: 0, y: 0 }, fit, stage))
  }

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const actions: Record<string, () => void> = {
      ArrowLeft: () => onPrev?.(),
      ArrowRight: () => onNext?.(),
      '+': () => zoomBy(1.5),
      '=': () => zoomBy(1.5),
      '-': () => zoomBy(1 / 1.5),
      '0': () => setView(initialView),
    }
    const action = actions[e.key]
    if (!action) return
    e.preventDefault()
    action()
  }

  const zoomed = view.scale > 1

  return (
    <div ref={rootRef} tabIndex={-1} className="flex h-full flex-col outline-none" onKeyDown={onKeyDown}>
      <div className="flex shrink-0 items-center gap-2.5 py-3 pr-3 pl-4">
        <Avatar name={photo.author} imageUrl={photo.authorImageUrl} size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{photo.author}</p>
          <p className="text-xs text-text-on-viewer-muted">
            {formatClock(photo.createdAt)} · {position}
          </p>
        </div>
        <a
          href={photo.url}
          target="_blank"
          rel="noopener noreferrer"
          className="shrink-0 rounded-full bg-bg-viewer-control px-3 py-1.5 text-xs font-medium hover:opacity-80 focus-visible:outline-2 focus-visible:outline-text-on-viewer"
        >
          원본 ↗
        </a>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-bg-viewer-control text-base leading-none font-bold hover:opacity-80 focus-visible:outline-2 focus-visible:outline-text-on-viewer"
        >
          ✕
        </button>
      </div>

      <div
        ref={stageRef}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
        onClick={() => {
          if (!closeOnClick.current) return
          closeOnClick.current = false
          onClose()
        }}
        className={`relative min-h-0 flex-1 touch-none overflow-hidden select-none ${zoomed ? 'cursor-grab active:cursor-grabbing' : ''}`}
      >
        {fit && (
          <div
            className="absolute top-1/2 left-1/2"
            style={{
              width: fit.width,
              height: fit.height,
              transform: `translate(-50%, -50%) translate(${view.x + swipeX}px, ${view.y}px) scale(${view.scale})`,
            }}
          >
            {/* 원본이 뜨기 전까지는 이미 받은 썸네일을 크게 보여준다. */}
            <img src={photo.thumbnailUrl} alt="" draggable={false} className="absolute inset-0 h-full w-full" />
            {failed ? (
              <p className="absolute inset-0 flex items-center justify-center bg-bg-viewer text-sm text-text-on-viewer-muted">사진을 불러올 수 없어요</p>
            ) : (
              <img
                src={photo.url}
                alt={`${photo.author}님이 보낸 사진`}
                draggable={false}
                onError={() => setFailed(true)}
                className="absolute inset-0 h-full w-full"
              />
            )}
          </div>
        )}
        {zoomed && (
          <span className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-full bg-bg-viewer px-3 py-1 text-xs font-medium">
            {Math.round(view.scale * 100)}%
          </span>
        )}
        {onPrev && <NavButton side="left" label="이전 사진" onClick={onPrev} />}
        {onNext && <NavButton side="right" label="다음 사진" onClick={onNext} />}
      </div>

      <p className="shrink-0 px-4 pt-4 pb-5 text-center text-xs text-text-on-viewer-muted">
        <span className="lg:hidden">{zoomed ? '끌어서 옮기기 · 두 번 눌러 원래 크기' : '두 번 눌러 확대 · 옆으로 밀어 다음 사진'}</span>
        <span className="hidden lg:inline">{zoomed ? '끌어서 옮기기 · 더블클릭하면 원래 크기 · 0 원래대로' : '더블클릭·휠로 확대 · ← → 다른 사진 · Esc 닫기'}</span>
      </p>
    </div>
  )
}

function NavButton({ side, label, onClick }: { side: 'left' | 'right'; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      // 무대의 끌기·두 번 누르기로 넘어가지 않게 한다.
      onPointerDown={(e) => e.stopPropagation()}
      onPointerUp={(e) => e.stopPropagation()}
      onClick={onClick}
      aria-label={label}
      className={`absolute top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-full bg-bg-viewer-control text-2xl leading-none font-bold hover:opacity-80 focus-visible:outline-2 focus-visible:outline-text-on-viewer ${side === 'left' ? 'left-2' : 'right-2'}`}
    >
      {side === 'left' ? '‹' : '›'}
    </button>
  )
}
