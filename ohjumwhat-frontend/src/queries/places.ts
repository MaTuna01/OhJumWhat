import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import { type LatLng, distanceMeters } from '../lib/distance.ts'

/** 회사 위치(조직 설정의 회사 주소를 볼 때마다 좌표로 바꾼 값) */
export type PlaceCenter = LatLng & { name: string | null }

/**
 * 메뉴(optionId)에 붙인 식당의 위치. 카카오 식당은 다시 찾은 이름·분류·도로명 주소가 함께 온다
 * (링크로 붙인 식당은 null, 저장한 placeName을 쓴다).
 */
export type PlaceSpot = LatLng & {
  optionId: number
  name: string | null
  category: string | null
  roadAddress: string | null
}

/** 지도에 올릴 위치. 좌표·카카오 결과는 약관상 저장하지 않으므로 서버가 볼 때마다 찾는다. */
export type Places = { center: PlaceCenter | null; places: PlaceSpot[] }

/** 위치를 찾을 메뉴(투표 메뉴 또는 지난번 식당을 붙였던 메뉴) */
export type PlaceRef = {
  id: number
  link: string | null
  placeAddress: string | null
  kakaoPlaceId: string | null
  placeQuery: string | null
}

/** 근처 식당 찾기 결과(카카오, 저장하지 않고 화면에만 보여준다) */
export type FoundPlace = LatLng & {
  kakaoPlaceId: string
  name: string
  category: string | null
  roadAddress: string | null
  /** 회사에서의 직선거리(m). 모르면 null */
  distance: number | null
}

export type PlaceSearchPage = { center: PlaceCenter; places: FoundPlace[]; hasMore: boolean }

/** 위치를 찾을 수 있는 메뉴: 링크로 붙이고 주소가 있거나, 근처 식당 찾기로 고른 카카오 식당 */
export function placeOptionIds(refs: PlaceRef[]): number[] {
  return refs.filter((r) => r.link && (r.placeAddress || r.kakaoPlaceId)).map((r) => r.id)
}

/** 메뉴별 회사에서의 거리(m). 회사 위치를 모르면 비어 있다. */
export function distancesByOption(places: Places | undefined): Map<number, number> {
  const center = places?.center
  if (!center) return new Map()
  return new Map(places.places.map((spot) => [spot.optionId, distanceMeters(center, spot)]))
}

/**
 * 지도에 올릴 회사·식당 위치(GET /api/orgs/{orgId}/places).
 * 키에 회사 주소와 메뉴의 식당(링크·주소·카카오 ID·검색어)을 넣어, 3초 폴링으로 투표 객체가 새로 와도 식당이 그대로면 다시 부르지 않는다.
 * 위치를 찾을 메뉴가 없으면 회사 위치만 받는다(조직 설정 미리보기).
 */
export function usePlaces(orgId: number, officeAddress: string | null, refs: PlaceRef[], enabled = true) {
  const ids = placeOptionIds(refs)
  const signature = refs
    .filter((r) => ids.includes(r.id))
    .map((r) => `${r.id}|${r.link}|${r.placeAddress}|${r.kakaoPlaceId}|${r.placeQuery}`)
    .join(',')
  return useQuery({
    queryKey: ['orgs', orgId, 'places', officeAddress, signature],
    queryFn: () => {
      const query = ids.length ? `?optionIds=${ids.join(',')}` : ''
      return api<Places>(`/api/orgs/${orgId}/places${query}`)
    },
    enabled,
    staleTime: Infinity,
    placeholderData: keepPreviousData,
  })
}

/**
 * 근처 식당 찾기(GET /api/orgs/{orgId}/places/search): 회사 주소 기준 반경 안 음식점을 가까운 순으로 15개씩, 45개까지.
 * 검색어가 비면 근처 음식점을 둘러본다. 결과는 화면에 보여줄 때만 쓴다.
 */
export function usePlaceSearch(orgId: number, officeAddress: string | null, searchRadius: number, q: string, enabled: boolean) {
  return useInfiniteQuery({
    queryKey: ['orgs', orgId, 'places', 'search', officeAddress, searchRadius, q],
    queryFn: ({ pageParam }) =>
      api<PlaceSearchPage>(`/api/orgs/${orgId}/places/search?q=${encodeURIComponent(q)}&page=${pageParam}`),
    initialPageParam: 1,
    getNextPageParam: (last, pages) => (last.hasMore ? pages.length + 1 : undefined),
    enabled,
    staleTime: 60_000,
  })
}
