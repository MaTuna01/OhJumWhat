/** 메뉴 지도 링크의 도메인: "https://www.naver.me/x" → "naver.me". 주소가 아니면 원문 */
export function linkHost(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

const KAKAO_MAP_HOSTS = new Set(['map.kakao.com', 'place.map.kakao.com', 'kko.to'])

/**
 * 지도 링크를 어느 서비스인지로 보여준다: "네이버 지도", "카카오맵", "구글 지도", 그 밖에는 도메인.
 * naver.me는 블로그·카페 등에도 쓰는 단축 주소라 장소로 확인하지 못했으면 "네이버"로 둔다(장소면 서버가 map.naver.com으로 바꾼다).
 */
export function serviceLabel(url: string): string {
  const host = linkHost(url).toLowerCase()
  let path = ''
  try {
    path = new URL(url).pathname
  } catch {
    // 주소가 아니면 도메인(원문)을 그대로 보여준다.
  }
  if (host === 'map.naver.com' || host.endsWith('place.naver.com')) return '네이버 지도'
  if (host === 'naver.me') return '네이버'
  if (KAKAO_MAP_HOSTS.has(host)) return '카카오맵'
  if (host === 'maps.app.goo.gl' || host === 'maps.google.com' || (/^google\.[a-z.]+$/.test(host) && path.startsWith('/maps'))) {
    return '구글 지도'
  }
  return linkHost(url)
}
