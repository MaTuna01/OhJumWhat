import { type FormEvent, useState } from 'react'
import { distanceLabel } from '../lib/distance.ts'
import { naverPlaceSearchUrl } from '../lib/place.ts'
import { inputClass } from '../lib/ui.ts'
import type { Organization } from '../queries/orgs.ts'
import { type FoundPlace, usePlaceSearch } from '../queries/places.ts'
import Button from './Button.tsx'
import NaverMap, { type MapMarker } from './NaverMap.tsx'

/** 검색어가 비었을 때 둘러보기 분류(누르면 그 말로 찾는다) */
const CATEGORIES = ['한식', '중식', '일식', '양식', '분식', '아시아음식', '패스트푸드']

type Props = {
  org: Organization
  /** 네이버 지도 키. 없으면 목록만 보여준다. */
  keyId: string | null
  /** 처음 검색어(메뉴 이름). 비면 근처 음식점을 둘러본다. */
  initialQuery: string
  selectedId: string | null
  /** 식당을 고르면(목록이나 지도 마커). query는 그 식당을 찾은 검색어 */
  onSelect: (place: FoundPlace, query: string) => void
}

/**
 * 근처에서 찾기(Figma 05-F): 회사 주소 기준 반경 안 음식점을 가까운 순으로 보여준다(카카오 로컬, 15개씩, 45개까지).
 * 검색어가 비면 분류 칩으로 둘러본다. 목록과 지도 마커는 번호로 이어지고, 누르면 고른다.
 * 결과는 화면에만 보여주고, 고른 식당은 장소 ID와 검색어만 저장한다.
 */
export default function PlaceFinder({ org, keyId, initialQuery, selectedId, onSelect }: Props) {
  const [input, setInput] = useState(initialQuery)
  const [query, setQuery] = useState(initialQuery.trim())
  const search = usePlaceSearch(org.id, org.officeAddress, org.searchRadius, query, true)
  const places = search.data?.pages.flatMap((page) => page.places) ?? []
  const center = search.data?.pages[0]?.center

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setQuery(input.trim())
  }

  const choose = (word: string) => {
    setInput(word)
    setQuery(word)
  }

  const markers: MapMarker[] = places.map((place, i) => ({
    id: place.kakaoPlaceId,
    lat: place.lat,
    lng: place.lng,
    label: `${i + 1} ${place.name}`,
    tone: place.kakaoPlaceId === selectedId ? 'brand' : 'default',
  }))
  if (center) markers.unshift({ id: 'office', lat: center.lat, lng: center.lng, label: '회사', tone: 'office' })

  return (
    <div className="space-y-3">
      <form onSubmit={submit} className="flex gap-2">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          maxLength={50}
          placeholder="메뉴나 식당 이름(비우면 근처 음식점)"
          aria-label="식당 검색어"
          className={inputClass}
        />
        <Button type="submit" variant="secondary" className="shrink-0">
          찾기
        </Button>
      </form>

      {!query && (
        <div className="flex flex-wrap gap-1.5" aria-label="분류로 둘러보기">
          {CATEGORIES.map((word) => (
            <button
              key={word}
              type="button"
              onClick={() => choose(word)}
              className="rounded-full border border-border-default bg-bg-surface px-3 py-1 text-xs font-medium text-text-secondary hover:border-border-strong focus-visible:outline-2 focus-visible:outline-border-brand"
            >
              {word}
            </button>
          ))}
        </div>
      )}

      {keyId && places.length > 0 && (
        <NaverMap
          keyId={keyId}
          markers={markers}
          onSelect={(id) => {
            const place = places.find((p) => p.kakaoPlaceId === id)
            if (place) onSelect(place, query)
          }}
          className="h-44"
          label="근처 식당 지도"
        />
      )}

      {search.isPending ? (
        <div className="space-y-1.5" aria-hidden>
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-14 animate-pulse rounded-xl bg-bg-muted" />
          ))}
        </div>
      ) : search.isError ? (
        <p role="alert" className="rounded-lg bg-bg-danger-soft px-3 py-2 text-sm text-text-danger">
          {search.error.message}
        </p>
      ) : places.length === 0 ? (
        <p className="rounded-xl bg-bg-muted px-4 py-6 text-center text-sm text-text-tertiary">
          {query ? `회사 근처에서 「${query}」 식당을 찾지 못했어요.` : '회사 근처에서 음식점을 찾지 못했어요.'}
        </p>
      ) : (
        <ul className="max-h-72 space-y-1.5 overflow-y-auto" aria-label="근처 식당">
          {places.map((place, i) => {
            const selected = place.kakaoPlaceId === selectedId
            return (
              <li key={place.kakaoPlaceId}>
                <div
                  className={`flex items-center gap-2.5 rounded-xl border px-3 py-2.5 ${
                    selected ? 'border-border-brand bg-bg-brand-soft' : 'border-border-default bg-bg-surface hover:border-border-strong'
                  }`}
                >
                  <button
                    type="button"
                    aria-pressed={selected}
                    onClick={() => onSelect(place, query)}
                    className="flex min-w-0 flex-1 items-center gap-2.5 text-left focus-visible:outline-2 focus-visible:outline-border-brand"
                  >
                    <span
                      className={`flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full px-1.5 text-xs font-bold ${
                        selected ? 'bg-bg-brand text-text-on-brand' : 'bg-bg-muted text-text-secondary'
                      }`}
                    >
                      {i + 1}
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-bold">{place.name}</span>
                      <span className="block truncate text-xs text-text-tertiary">
                        {[place.category, place.distance != null ? distanceLabel(place.distance) : null].filter(Boolean).join(' · ')}
                      </span>
                    </span>
                  </button>
                  <a
                    href={naverPlaceSearchUrl(place.name, place.roadAddress)}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={`${place.name} 네이버 지도에서 보기`}
                    className="shrink-0 text-xs font-medium text-text-secondary hover:text-text-primary hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
                  >
                    네이버 지도 ↗
                  </a>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {search.hasNextPage && (
        <div className="text-center">
          <button
            type="button"
            onClick={() => search.fetchNextPage()}
            disabled={search.isFetchingNextPage}
            className="text-sm font-medium text-text-secondary hover:text-text-primary disabled:opacity-50"
          >
            {search.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
          </button>
        </div>
      )}

      <p className="text-xs text-text-placeholder">검색 결과 제공: 카카오 · 거리는 회사에서 직선거리예요</p>
    </div>
  )
}
