/**
 * 사진 뷰어(PhotoViewer)의 확대·이동 계산. 사진은 화면(무대)에 맞춘 크기(fit)로 가운데에 두고,
 * 그 위에 배율(scale)과 가운데에서 옮긴 거리(x, y, CSS px)를 얹는다. 점은 모두 무대 가운데 기준 좌표다.
 */
export type ZoomView = { scale: number; x: number; y: number }

export type Box = { width: number; height: number }

export const MIN_SCALE = 1

export const MAX_SCALE = 4

/** 두 번 눌렀을 때 배율 */
export const DOUBLE_TAP_SCALE = 2.5

export const initialView: ZoomView = { scale: 1, x: 0, y: 0 }

/** 사진을 무대에 맞춘 크기(contain). 원본보다 키우지 않는다. */
export function fitBox(width: number, height: number, stage: Box): Box {
  const ratio = Math.min(1, stage.width / width, stage.height / height)
  return { width: width * ratio, height: height * ratio }
}

/** 배율은 1~4배, 사진이 무대보다 크면 가장자리가 무대 안으로 들어오지 않을 만큼만 옮긴다. */
export function clampView(view: ZoomView, fit: Box, stage: Box): ZoomView {
  const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, view.scale))
  const maxX = Math.max(0, (fit.width * scale - stage.width) / 2)
  const maxY = Math.max(0, (fit.height * scale - stage.height) / 2)
  return { scale, x: clamp(view.x, maxX), y: clamp(view.y, maxY) }
}

/** point(무대 가운데 기준) 아래의 사진 자리가 그대로 있게 배율을 바꾼다(휠·핀치·두 번 누르기). */
export function zoomAt(view: ZoomView, scale: number, point: { x: number; y: number }, fit: Box, stage: Box): ZoomView {
  const next = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale))
  const ratio = next / view.scale
  return clampView({ scale: next, x: point.x - (point.x - view.x) * ratio, y: point.y - (point.y - view.y) * ratio }, fit, stage)
}

export function panBy(view: ZoomView, dx: number, dy: number, fit: Box, stage: Box): ZoomView {
  return clampView({ ...view, x: view.x + dx, y: view.y + dy }, fit, stage)
}

/** 두 번 누르기: 1배면 누른 곳을 2.5배로, 확대 중이면 원래대로 */
export function toggleZoom(view: ZoomView, point: { x: number; y: number }, fit: Box, stage: Box): ZoomView {
  return view.scale > MIN_SCALE ? initialView : zoomAt(view, DOUBLE_TAP_SCALE, point, fit, stage)
}

/** 1배에서 옆으로 민 거리로 넘길 방향: 오른쪽으로 밀면 이전(-1), 왼쪽으로 밀면 다음(1). 짧거나 세로로 밀면 0 */
export function swipeStep(dx: number, dy: number, threshold = 60): -1 | 0 | 1 {
  if (Math.abs(dx) < threshold || Math.abs(dx) < Math.abs(dy) * 1.5) return 0
  return dx > 0 ? -1 : 1
}

export type Tap = { time: number; x: number; y: number }

/** 앞의 탭과 300ms·20px 안이면 두 번 누른 것이다. */
export function isDoubleTap(previous: Tap | null, tap: Tap): boolean {
  return previous != null && tap.time - previous.time < 300 && Math.hypot(tap.x - previous.x, tap.y - previous.y) < 20
}

function clamp(value: number, max: number): number {
  return Math.min(max, Math.max(-max, value))
}
