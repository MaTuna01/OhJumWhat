/** 메뉴 지도 링크의 도메인: "https://www.naver.me/x" → "naver.me". 주소가 아니면 원문 */
export function linkHost(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}
