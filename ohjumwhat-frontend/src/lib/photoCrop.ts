/**
 * 「사진 맞추기」(Figma 03-M3) 계산. 정사각형 뷰포트의 한 변을 1로 놓고 계산하므로 화면 크기와 관계없다.
 * - zoom 1: 사진의 짧은 변이 뷰포트를 꽉 채운다(더 줄이면 빈 곳이 생기므로 1이 최소).
 * - x, y: 뷰포트 가운데에서 사진 가운데까지의 거리(뷰포트 단위). 사진이 뷰포트를 늘 덮도록 제한한다.
 */
export type Crop = { zoom: number; x: number; y: number }

export const MIN_ZOOM = 1
export const MAX_ZOOM = 3

export const initialCrop: Crop = { zoom: MIN_ZOOM, x: 0, y: 0 }

/** 화면에 그린 사진의 크기(뷰포트 단위) */
function displaySize(width: number, height: number, zoom: number) {
  const short = Math.min(width, height)
  return { width: (width / short) * zoom, height: (height / short) * zoom }
}

function clampValue(value: number, limit: number) {
  const clamped = Math.min(limit, Math.max(-limit, value))
  return clamped === 0 ? 0 : clamped // -0을 0으로
}

/** 사진이 뷰포트를 늘 덮도록 위치를 제한한다. */
export function clampCrop(width: number, height: number, crop: Crop): Crop {
  const zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, crop.zoom))
  const size = displaySize(width, height, zoom)
  return { zoom, x: clampValue(crop.x, (size.width - 1) / 2), y: clampValue(crop.y, (size.height - 1) / 2) }
}

/** 뷰포트 가운데를 기준으로 확대·축소한다(가운데에 보이던 곳이 그대로 가운데에 남는다). */
export function zoomTo(width: number, height: number, crop: Crop, zoom: number): Crop {
  const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom))
  const ratio = next / crop.zoom
  return clampCrop(width, height, { zoom: next, x: crop.x * ratio, y: crop.y * ratio })
}

/** 끌어서 옮긴다. dx, dy는 뷰포트 단위(픽셀 ÷ 뷰포트 한 변). */
export function panBy(width: number, height: number, crop: Crop, dx: number, dy: number): Crop {
  return clampCrop(width, height, { ...crop, x: crop.x + dx, y: crop.y + dy })
}

/** 화면에 그릴 사진의 위치·크기(뷰포트 단위, CSS 백분율로 쓴다) */
export function imageBox(width: number, height: number, crop: Crop) {
  const size = displaySize(width, height, crop.zoom)
  return {
    left: 0.5 + crop.x - size.width / 2,
    top: 0.5 + crop.y - size.height / 2,
    width: size.width,
    height: size.height,
  }
}

/** 뷰포트에 보이는 부분을 원본 사진 좌표(px)로. 캔버스 drawImage의 원본 영역이다. */
export function sourceRect(width: number, height: number, crop: Crop) {
  const size = Math.min(width, height) / crop.zoom
  return { sx: width / 2 - (0.5 + crop.x) * size, sy: height / 2 - (0.5 + crop.y) * size, size }
}
