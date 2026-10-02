import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import { type LatLng, distanceMeters } from '../lib/distance.ts'
import type { PollOption } from './polls.ts'

/** 회사 위치(조직 설정의 회사 주소를 볼 때마다 좌표로 바꾼 값) */
export type PlaceCenter = LatLng & { name: string | null }

/** 메뉴(optionId)에 붙인 식당의 위치 */
export type PlaceSpot = LatLng & { optionId: number }

/** 지도에 올릴 위치. 좌표는 약관상 저장하지 않으므로 서버가 볼 때마다 찾는다. */
export type Places = { center: PlaceCenter | null; places: PlaceSpot[] }

/** 위치를 찾을 수 있는 메뉴: 식당 링크와 주소가 있어야 한다 */
export function placeOptionIds(options: PollOption[]): number[] {
  return options.filter((o) => o.link && o.placeAddress).map((o) => o.id)
}

/** 메뉴별 회사에서의 거리(m). 회사 위치를 모르면 비어 있다. */
export function distancesByOption(places: Places | undefined): Map<number, number> {
  const center = places?.center
  if (!center) return new Map()
  return new Map(places.places.map((spot) => [spot.optionId, distanceMeters(center, spot)]))
}

/**
 * 지도에 올릴 회사·식당 위치(GET /api/orgs/{orgId}/places).
 * 키에 회사 주소와 메뉴의 식당(링크·주소)을 넣어, 3초 폴링으로 투표 객체가 새로 와도 식당이 그대로면 다시 부르지 않는다.
 * optionIds가 비면 회사 위치만 받는다(조직 설정 미리보기).
 */
export function usePlaces(orgId: number, officeAddress: string | null, options: PollOption[], enabled = true) {
  const ids = placeOptionIds(options)
  const signature = options
    .filter((o) => ids.includes(o.id))
    .map((o) => `${o.id}|${o.link}|${o.placeAddress}`)
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
