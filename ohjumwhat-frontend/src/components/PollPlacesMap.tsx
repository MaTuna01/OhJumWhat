import { useState } from 'react'
import { distanceLabel } from '../lib/distance.ts'
import { serviceLabel } from '../lib/link.ts'
import type { Places } from '../queries/places.ts'
import type { PollOption } from '../queries/polls.ts'
import Modal from './Modal.tsx'
import NaverMap, { type MapMarker } from './NaverMap.tsx'

type Props = {
  keyId: string
  /** 투표 제목(큰 지도 모달 제목) */
  title: string
  /** 조직 이름: 조직 위치 핀 이름표(장소 이름이 없을 때) */
  orgName: string
  places: Places
  /** 지도에 올릴 메뉴(진행 중: 모든 메뉴, 마감: 확정 팀) */
  options: PollOption[]
  myOptionId: number | null
  /** 메뉴별 조직 위치에서의 거리(m) */
  distances: Map<number, number>
  /** 카카오 식당을 다시 찾은 이름과 네이버 지도 링크 */
  resolved: Map<number, { name: string; link: string }>
  /** 모바일: 접었다 펼 수 있다(펼칠 때만 지도를 불러온다). 데스크톱 사이드에서는 항상 펼친다. */
  collapsible: boolean
  defaultOpen: boolean
  /** 메뉴 마커를 누르면(카드로 이동) */
  onSelect: (optionId: number) => void
}

/**
 * 투표 상세의 지도(Figma 05-G·D05-G): 조직 위치(검정 핀, 장소 이름이나 조직 이름)와 메뉴별 식당 「메뉴 · N명」(내 메뉴는 주황 핀).
 * 「⤢ 크게 보기」는 큰 지도와 식당 목록을 모달로 연다(05-G2·D05-G2). 위치를 찾은 식당이 없으면 그리지 않는다.
 */
export default function PollPlacesMap({ keyId, title, orgName, places, options, myOptionId, distances, resolved, collapsible, defaultOpen, onSelect }: Props) {
  const [open, setOpen] = useState(defaultOpen)
  const [expanded, setExpanded] = useState(false)
  const byId = new Map(options.map((o) => [o.id, o]))
  const spots = places.places.filter((spot) => byId.has(spot.optionId))
  if (spots.length === 0) return null

  const markers = (activeId: number | null = null): MapMarker[] => {
    const list: MapMarker[] = spots.map((spot) => {
      const option = byId.get(spot.optionId)!
      return {
        id: String(option.id),
        lat: spot.lat,
        lng: spot.lng,
        label: `${option.name} · ${option.voters.length}명`,
        tone: option.id === myOptionId ? 'brand' : 'default',
        active: option.id === activeId,
      }
    })
    if (places.center) list.unshift({ id: 'office', lat: places.center.lat, lng: places.center.lng, label: places.center.name ?? orgName, tone: 'office' })
    return list
  }

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
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setExpanded(true)}
            className="rounded-lg border border-border-default bg-bg-surface px-2 py-0.5 text-xs font-medium text-text-secondary hover:border-border-strong hover:text-text-primary focus-visible:outline-2 focus-visible:outline-border-brand"
          >
            ⤢ 크게 보기
          </button>
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
      </div>
      <NaverMap
        keyId={keyId}
        markers={markers()}
        onSelect={(id) => id !== 'office' && onSelect(Number(id))}
        className="h-56 lg:h-60"
        label="조직 위치와 메뉴별 식당 위치"
      />
      <p className="text-xs text-text-tertiary">마커를 누르면 그 메뉴 카드로 이동해요</p>

      <Modal open={expanded} onClose={() => setExpanded(false)} title={`${title} 지도 · 식당 ${spots.length}곳`} size="lg" closable>
        <BigMap
          keyId={keyId}
          markers={markers}
          options={spots.map((spot) => byId.get(spot.optionId)!)}
          myOptionId={myOptionId}
          distances={distances}
          resolved={resolved}
        />
      </Modal>
    </section>
  )
}

type BigMapProps = {
  keyId: string
  markers: (activeId: number | null) => MapMarker[]
  options: PollOption[]
  myOptionId: number | null
  distances: Map<number, number>
  resolved: Map<number, { name: string; link: string }>
}

/** 큰 지도(Figma 05-G2·D05-G2): 지도와 식당 목록. 목록을 누르면 지도가 그 식당으로 옮겨 가고, 핀을 누르면 목록에서 강조된다. */
function BigMap({ keyId, markers, options, myOptionId, distances, resolved }: BigMapProps) {
  const [activeId, setActiveId] = useState<number | null>(null)

  const select = (optionId: number) => {
    setActiveId(optionId)
    document.getElementById(`map-row-${optionId}`)?.scrollIntoView({ block: 'nearest' })
  }

  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:gap-4">
      <NaverMap
        keyId={keyId}
        markers={markers(activeId)}
        focusId={activeId == null ? null : String(activeId)}
        onSelect={(id) => id !== 'office' && select(Number(id))}
        className="h-[55vh] lg:h-[34rem] lg:flex-1"
        label="조직 위치와 메뉴별 식당 위치(크게)"
      />
      <ul className="max-h-[25vh] space-y-1.5 overflow-y-auto lg:max-h-[34rem] lg:w-72 lg:shrink-0" aria-label="메뉴별 식당">
        {options.map((option) => {
          const place = resolved.get(option.id)?.name ?? option.placeName
          const link = resolved.get(option.id)?.link ?? option.link
          const distance = distances.get(option.id)
          const active = option.id === activeId
          const mine = option.id === myOptionId
          return (
            <li key={option.id} id={`map-row-${option.id}`}>
              <div
                className={`flex items-center gap-2.5 rounded-xl border px-3 py-2.5 ${
                  active || mine ? 'border-border-brand bg-bg-brand-soft' : 'border-border-default bg-bg-surface hover:border-border-strong'
                } ${active ? 'ring-1 ring-border-brand' : ''}`}
              >
                <button
                  type="button"
                  aria-pressed={active}
                  onClick={() => setActiveId(option.id)}
                  className="min-w-0 flex-1 text-left focus-visible:outline-2 focus-visible:outline-border-brand"
                >
                  <span className="block truncate text-sm font-bold">
                    {option.name} · {option.voters.length}명
                  </span>
                  <span className="block truncate text-xs text-text-tertiary">
                    {[place, distance != null ? distanceLabel(distance) : null].filter(Boolean).join(' · ')}
                  </span>
                </button>
                {link && (
                  <a
                    href={link}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={`${place ?? option.name} 지도에서 보기`}
                    className="shrink-0 text-xs font-medium text-text-secondary hover:text-text-primary hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
                  >
                    {serviceLabel(link)} ↗
                  </a>
                )}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
