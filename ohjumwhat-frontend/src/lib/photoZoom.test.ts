import { describe, expect, it } from 'vitest'
import { clampView, DOUBLE_TAP_SCALE, fitBox, initialView, isDoubleTap, MAX_SCALE, panBy, swipeStep, toggleZoom, zoomAt } from './photoZoom.ts'

const stage = { width: 400, height: 800 }
// 1600×1200 사진을 400×800 무대에 맞추면 400×300
const fit = fitBox(1600, 1200, stage)

describe('fitBox', () => {
  it('무대에 맞추고 원본보다 키우지 않는다', () => {
    expect(fit).toEqual({ width: 400, height: 300 })
    expect(fitBox(200, 100, stage)).toEqual({ width: 200, height: 100 })
  })
})

describe('clampView·panBy', () => {
  it('1배에서는 옮길 수 없다', () => {
    expect(panBy(initialView, 50, 50, fit, stage)).toEqual(initialView)
  })

  it('확대하면 사진 가장자리가 무대 안으로 들어오지 않을 만큼만 옮긴다', () => {
    // 2배면 800×600: 가로로는 ±200, 세로로는 무대(800)보다 작아 0
    expect(panBy({ scale: 2, x: 0, y: 0 }, 500, 500, fit, stage)).toEqual({ scale: 2, x: 200, y: 0 })
  })

  it('NaN이 들어오면 처음으로 돌린다', () => {
    expect(clampView({ scale: 2, x: Number.NaN, y: 0 }, fit, stage)).toEqual(initialView)
  })

  it('배율은 1~4배다', () => {
    expect(clampView({ scale: 0.3, x: 0, y: 0 }, fit, stage).scale).toBe(1)
    expect(clampView({ scale: 9, x: 0, y: 0 }, fit, stage).scale).toBe(MAX_SCALE)
  })
})

describe('zoomAt·toggleZoom', () => {
  it('가리킨 곳이 그대로 있게 확대한다', () => {
    const point = { x: 100, y: 50 }
    const view = zoomAt(initialView, 2, point, fit, stage)
    expect(view.scale).toBe(2)
    // 무대의 (100, 50)에 있던 사진 자리(사진 좌표 (100, 50))가 확대 뒤에도 (100, 50)에 있다(세로는 무대보다 작아 가운데로).
    expect(view.x + 2 * 100).toBe(100)
    expect(view.y).toBeCloseTo(0)
  })

  it('두 번 누르면 2.5배, 다시 누르면 원래대로', () => {
    const zoomed = toggleZoom(initialView, { x: 0, y: 0 }, fit, stage)
    expect(zoomed.scale).toBe(DOUBLE_TAP_SCALE)
    expect(toggleZoom(zoomed, { x: 0, y: 0 }, fit, stage)).toEqual(initialView)
  })
})

describe('swipeStep·isDoubleTap', () => {
  it('옆으로 충분히 밀면 넘긴다(오른쪽으로 밀면 이전)', () => {
    expect(swipeStep(80, 10)).toBe(-1)
    expect(swipeStep(-80, 10)).toBe(1)
    expect(swipeStep(40, 0)).toBe(0)
    expect(swipeStep(80, 70)).toBe(0)
  })

  it('300ms·20px 안의 두 번째 탭', () => {
    const first = { time: 1000, x: 10, y: 10 }
    expect(isDoubleTap(first, { time: 1200, x: 15, y: 12 })).toBe(true)
    expect(isDoubleTap(first, { time: 1400, x: 15, y: 12 })).toBe(false)
    expect(isDoubleTap(first, { time: 1200, x: 60, y: 10 })).toBe(false)
    expect(isDoubleTap(null, first)).toBe(false)
  })
})
