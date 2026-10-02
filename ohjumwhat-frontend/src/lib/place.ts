const NAME_MAX_LENGTH = 100
const ADDRESS_MAX_LENGTH = 200

/** 식당 입력값(링크·이름·주소). 링크가 비면 식당 없음 */
export type PlaceValue = { link: string; name: string; address: string }

export const EMPTY_PLACE: PlaceValue = { link: '', name: '', address: '' }

export type PlaceInput = { link: string | null; placeName: string | null; placeAddress: string | null }

/** 서버에 보낼 값: 링크가 비면 식당 없음(이름·주소도 보내지 않는다) */
export function placeInput(value: PlaceValue): PlaceInput {
  const link = value.link.trim()
  return link
    ? { link, placeName: value.name.trim() || null, placeAddress: value.address.trim() || null }
    : { link: null, placeName: null, placeAddress: null }
}

// 지도 앱 공유 글의 머리말: [네이버 지도], [네이버지도], [카카오맵], [NAVER Map] …
const SHARE_HEADER = /^\s*\[[^\]\n]{1,20}\]\s*/
// 링크로 보이는 토큰(스킴이 없어도): https://…, naver.me/…, map.naver.com/…, kko.to/…
const URL_TOKEN = /(?:https?:\/\/\S+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)+\/\S*)/gi

// 주소 줄: 시·도 이름으로 시작한다(예: "서울 강남구 테헤란로 1", "경기도 성남시 …").
const ADDRESS_LINE = /^(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|충청|전북|전남|전라|경북|경남|경상|제주)/

/**
 * 지도 앱 공유 글에서 식당 이름과 주소를 꺼낸다(입력칸 미리 채우기용, 서버는 사용자가 고친 값을 저장한다).
 * 예) "[네이버 지도]\n할매집\n서울 강남구 …\nnaver.me/abc" → { name: "할매집", address: "서울 강남구 …" },
 * "[카카오맵] 김밥천국 https://kko.to/x" → { name: "김밥천국", address: null }. 링크만 붙였으면 둘 다 null
 */
export function parseShareText(text: string): { name: string | null; address: string | null } {
  const lines = text
    .replace(SHARE_HEADER, '')
    .split(/\r?\n/)
    .map((line) => line.replace(URL_TOKEN, ' ').replace(/\s+/g, ' ').trim())
    .filter(Boolean)
  const [first, ...rest] = lines
  if (!first) return { name: null, address: null }
  const address = rest.find((line) => ADDRESS_LINE.test(line)) ?? null
  return {
    name: [...first].slice(0, NAME_MAX_LENGTH).join(''),
    address: address && [...address].slice(0, ADDRESS_MAX_LENGTH).join(''),
  }
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
