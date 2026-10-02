import { useState } from 'react'
import type { Places } from '../queries/places.ts'
import type { PollOption } from '../queries/polls.ts'
import NaverMap, { type MapMarker } from './NaverMap.tsx'

type Props = {
  keyId: string
  places: Places
  /** 지도에 올릴 메뉴(진행 중: 모든 메뉴, 마감: 확정 팀) */
  options: PollOption[]
  myOptionId: number | null
  /** 모바일: 접었다 펼 수 있다(펼칠 때만 지도를 불러온다). 데스크톱 사이드에서는 항상 펼친다. */
  collapsible: boolean
  defaultOpen: boolean
  /** 메뉴 마커를 누르면 */
  onSelect: (optionId: number) => void
}

/**
 * 투표 상세의 지도(Figma 05-G·D05-G): 회사(검정)와 메뉴별 식당 「메뉴 · N명」(내 메뉴는 주황).
 * 위치를 찾은 식당이 없으면 그리지 않는다.
 */
export default function PollPlacesMap({ keyId, places, options, myOptionId, collapsible, defaultOpen, onSelect }: Props) {
  const [open, setOpen] = useState(defaultOpen)
  const byId = new Map(options.map((o) => [o.id, o]))
  const spots = places.places.filter((spot) => byId.has(spot.optionId))
  if (spots.length === 0) return null

  const markers: MapMarker[] = spots.map((spot) => {
    const option = byId.get(spot.optionId)!
    return {
      id: String(option.id),
      lat: spot.lat,
      lng: spot.lng,
      label: `${option.name} · ${option.voters.length}명`,
      tone: option.id === myOptionId ? 'brand' : 'default',
    }
  })
  if (places.center) markers.unshift({ id: 'office', lat: places.center.lat, lng: places.center.lng, label: '회사', tone: 'office' })

  if (collapsible && !open) {
    return (
      <button
        type="button"
        aria-expanded={false}
        onClick={() => setOpen(true)}
        className="flex w-full items-center justify-between rounded-2xl border border-border-default bg-bg-surface px-4 py-3 text-left hover:bg-bg-subtle focus-visible:outline-2 focus-visible:outline-border-brand"
      >
        <span className="text-sm font-bold">지도로 보기 · 식당 {spots.length}곳</span>
        <span className="text-xs font-medium text-text-tertiary">펼치기</span>
      </button>
    )
  }

  return (
    <section aria-label="식당 지도" className="space-y-2.5 rounded-2xl border border-border-default bg-bg-surface p-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-bold">지도 · 식당 {spots.length}곳</h2>
        {collapsible && (
          <button
            type="button"
            aria-expanded
            onClick={() => setOpen(false)}
            className="text-xs font-medium text-text-tertiary hover:text-text-secondary focus-visible:outline-2 focus-visible:outline-border-brand"
          >
            접기
          </button>
        )}
      </div>
      <NaverMap
        keyId={keyId}
        markers={markers}
        onSelect={(id) => id !== 'office' && onSelect(Number(id))}
        className="h-56 lg:h-60"
        label="회사와 메뉴별 식당 위치"
      />
      <p className="text-xs text-text-tertiary">마커를 누르면 그 메뉴 카드로 이동해요</p>
    </section>
  )
}
