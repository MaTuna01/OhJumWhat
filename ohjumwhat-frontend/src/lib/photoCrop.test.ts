import { describe, expect, it } from 'vitest'
import { clampCrop, imageBox, initialCrop, MAX_ZOOM, panBy, sourceRect, zoomTo } from './photoCrop.ts'

describe('sourceRect', () => {
  it('정사각형 사진은 처음에 사진 전체를 쓴다', () => {
    expect(sourceRect(800, 800, initialCrop)).toEqual({ sx: 0, sy: 0, size: 800 })
  })

  it('가로 사진은 처음에 가운데 정사각형을 쓴다', () => {
    expect(sourceRect(2000, 1000, initialCrop)).toEqual({ sx: 500, sy: 0, size: 1000 })
  })

  it('세로 사진은 처음에 가운데 정사각형을 쓴다', () => {
    expect(sourceRect(1000, 2000, initialCrop)).toEqual({ sx: 0, sy: 500, size: 1000 })
  })

  it('2배로 확대하면 가운데의 절반 크기를 쓴다', () => {
    expect(sourceRect(800, 800, { zoom: 2, x: 0, y: 0 })).toEqual({ sx: 200, sy: 200, size: 400 })
  })

  it('가로 사진을 오른쪽 끝까지 옮기면 왼쪽 끝 정사각형을 쓴다', () => {
    const crop = panBy(2000, 1000, initialCrop, 10, 0)
    expect(crop.x).toBe(0.5)
    expect(sourceRect(2000, 1000, crop)).toEqual({ sx: 0, sy: 0, size: 1000 })
  })
})

describe('clampCrop·panBy', () => {
  it('정사각형 사진은 확대하지 않으면 옮길 수 없다', () => {
    expect(panBy(800, 800, initialCrop, 0.3, -0.3)).toEqual(initialCrop)
  })

  it('세로 사진은 위아래로만 옮길 수 있다', () => {
    expect(panBy(1000, 2000, initialCrop, 0.3, 0.3)).toEqual({ zoom: 1, x: 0, y: 0.3 })
  })

  it('확대 배율은 1~3배로 제한한다', () => {
    expect(clampCrop(800, 800, { zoom: 0.5, x: 0, y: 0 }).zoom).toBe(1)
    expect(clampCrop(800, 800, { zoom: 9, x: 0, y: 0 }).zoom).toBe(MAX_ZOOM)
  })

  it('어디로 옮기고 확대해도 원본 사진 밖을 쓰지 않는다', () => {
    for (const [w, h] of [[800, 800], [2000, 1000], [600, 1500]]) {
      for (const zoom of [1, 1.7, 3]) {
        for (const d of [-5, -0.2, 0.2, 5]) {
          const crop = panBy(w, h, zoomTo(w, h, initialCrop, zoom), d, -d)
          const { sx, sy, size } = sourceRect(w, h, crop)
          expect(sx).toBeGreaterThanOrEqual(-1e-9)
          expect(sy).toBeGreaterThanOrEqual(-1e-9)
          expect(sx + size).toBeLessThanOrEqual(w + 1e-9)
          expect(sy + size).toBeLessThanOrEqual(h + 1e-9)
        }
      }
    }
  })
})

describe('zoomTo', () => {
  it('가운데에 보이던 곳을 그대로 두고 확대한다', () => {
    const moved = panBy(2000, 1000, initialCrop, 0.25, 0)
    const zoomed = zoomTo(2000, 1000, moved, 2)
    const center = (c: typeof moved) => {
      const r = sourceRect(2000, 1000, c)
      return r.sx + r.size / 2
    }
    expect(center(zoomed)).toBeCloseTo(center(moved))
  })

  it('축소하면 빈 곳이 생기지 않게 위치를 다시 맞춘다', () => {
    const zoomedIn = panBy(800, 800, zoomTo(800, 800, initialCrop, 3), 10, 10)
    expect(zoomedIn).toEqual({ zoom: 3, x: 1, y: 1 })
    expect(zoomTo(800, 800, zoomedIn, 1)).toEqual(initialCrop)
  })
})

describe('imageBox', () => {
  it('가로 사진은 처음에 뷰포트보다 넓게, 가운데에 그린다', () => {
    expect(imageBox(2000, 1000, initialCrop)).toEqual({ left: -0.5, top: 0, width: 2, height: 1 })
  })
})
