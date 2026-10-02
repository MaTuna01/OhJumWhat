export type LatLng = { lat: number; lng: number }

const EARTH_RADIUS_M = 6_371_000

/** 두 지점의 직선거리(m, haversine) */
export function distanceMeters(a: LatLng, b: LatLng): number {
  const rad = (deg: number) => (deg * Math.PI) / 180
  const dLat = rad(b.lat - a.lat)
  const dLng = rad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)))
}

/** 거리 표시: 1km 미만은 10m 단위("350m"), 그 이상은 0.1km 단위("1.2km", "2km") */
export function formatDistance(meters: number): string {
  if (meters < 995) return `${Math.max(10, Math.round(meters / 10) * 10)}m`
  return `${Number((meters / 1000).toFixed(1))}km`
}

/** 도보 시간(분): 직선거리에 길 돌아가는 몫(1.3배)을 더하고 분당 67m(시속 4km)로 나눈다. 최소 1분 */
export function walkMinutes(meters: number): number {
  return Math.max(1, Math.round((meters * 1.3) / 67))
}

/** 카드·목록의 거리 표시: "350m · 도보 약 5분" */
export function distanceLabel(meters: number): string {
  return `${formatDistance(meters)} · 도보 약 ${walkMinutes(meters)}분`
}
