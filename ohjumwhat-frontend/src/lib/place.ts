const NAME_MAX_LENGTH = 100

// 지도 앱 공유 글의 머리말: [네이버 지도], [네이버지도], [카카오맵], [NAVER Map] …
const SHARE_HEADER = /^\s*\[[^\]\n]{1,20}\]\s*/
// 링크로 보이는 토큰(스킴이 없어도): https://…, naver.me/…, map.naver.com/…, kko.to/…
const URL_TOKEN = /(?:https?:\/\/\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)+\/\S*)/gi

/**
 * 지도 앱 공유 글에서 식당 이름을 꺼낸다(입력칸 미리 채우기용, 서버는 사용자가 고친 이름을 저장한다).
 * 예) "[네이버 지도]\n할매집\n서울 강남구 …\nnaver.me/abc" → "할매집", "[카카오맵] 김밥천국 https://kko.to/x" → "김밥천국"
 * 링크만 붙였으면 null
 */
export function parseShareText(text: string): { name: string | null } {
  const lines = text.replace(SHARE_HEADER, '').split(/\r?\n/)
  for (const line of lines) {
    const name = line.replace(URL_TOKEN, ' ').replace(/\s+/g, ' ').trim()
    if (name) return { name: [...name].slice(0, NAME_MAX_LENGTH).join('') }
  }
  return { name: null }
}

/** 「네이버 지도에서 찾기」: 검색 지역 + 검색어로 네이버 지도 검색을 연다(모바일은 네이버 지도 앱으로 열린다). */
export function naverSearchUrl(area: string | null | undefined, query: string): string {
  const q = [area, query]
    .filter((part): part is string => Boolean(part?.trim()))
    .join(' ')
    .replace(/\//g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return `https://map.naver.com/p/search/${encodeURIComponent(q)}`
}
