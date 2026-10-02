import { useEffect, useRef, useState } from 'react'
import { loadNaverMaps } from '../lib/naverMaps.ts'

export type MapMarker = {
  id: string
  lat: number
  lng: number
  /** 마커 글자(예: "김치찌개 · 4명"). textContent로만 넣는다(메뉴 이름은 사용자 입력이라 HTML로 넣지 않는다). */
  label: string
  /** office: 회사(검정), brand: 내가 고른 메뉴·선택한 식당(주황), default: 그 밖 */
  tone: 'office' | 'brand' | 'default'
  /** 핀 머리에 넣을 짧은 글자(식당 찾기 목록 번호). 없으면 흰 점 */
  badge?: string
  /** 목록에서 고른 마커: 이름표를 강조하고 맨 위에 그린다. */
  active?: boolean
  /** 이름표를 숨긴다(번호로 목록과 이어지는 식당 찾기에서 몰려 있는 핀이 서로 가리지 않게). title로는 남는다. */
  hideLabel?: boolean
}

type Props = {
  keyId: string
  markers: MapMarker[]
  /** 마커를 누르면 */
  onSelect?: (id: string) => void
  /** 지도 영역 크기(높이 포함) */
  className?: string
  /** 스크린 리더용 지도 설명 */
  label: string
  /** 이 마커로 지도를 옮긴다(목록에서 고를 때). */
  focusId?: string | null
}

const SVG_NS = 'http://www.w3.org/2000/svg'

/** 물방울 핀(24×32): 끝(12, 32)이 정확한 위치다. */
const PIN_PATH = 'M12 0C5.4 0 0 5.3 0 11.9 0 20.8 12 32 12 32s12-11.2 12-20.1C24 5.3 18.6 0 12 0z'

const PIN_CLASS: Record<MapMarker['tone'], string> = {
  office: 'fill-bg-inverse',
  brand: 'fill-bg-brand',
  default: 'fill-text-secondary',
}

const LABEL_CLASS: Record<MapMarker['tone'], string> = {
  office: 'bg-bg-inverse text-text-on-brand',
  brand: 'bg-bg-brand text-text-on-brand',
  default: 'border border-border-default bg-bg-surface text-text-primary',
}

/**
 * 마커 내용(Figma 디자인 시스템 MapPin): 핀 끝이 정확한 위치이고, 이름표는 핀 오른쪽에 둔다.
 * 사용자 입력(메뉴 이름)이 들어가므로 HTML 문자열 없이 노드를 만들고 글자는 textContent로만 넣는다.
 * 운영 CSP가 style 속성을 막지 않도록 모양은 클래스로만 정한다.
 */
function markerElement(marker: MapMarker): HTMLElement {
  const root = document.createElement('div')
  root.className = 'relative h-8 w-6 -translate-x-1/2 -translate-y-full cursor-pointer'

  const pin = document.createElementNS(SVG_NS, 'svg')
  pin.setAttribute('viewBox', '0 0 24 32')
  pin.setAttribute('class', 'absolute inset-0 size-full overflow-visible drop-shadow-md')
  const path = document.createElementNS(SVG_NS, 'path')
  path.setAttribute('d', PIN_PATH)
  path.setAttribute('class', `${PIN_CLASS[marker.tone]} stroke-bg-surface`)
  path.setAttribute('stroke-width', '1.5')
  pin.appendChild(path)
  if (marker.badge) {
    const badge = document.createElementNS(SVG_NS, 'text')
    badge.setAttribute('x', '12')
    badge.setAttribute('y', '16')
    badge.setAttribute('text-anchor', 'middle')
    badge.setAttribute('class', 'fill-bg-surface text-[11px] font-bold')
    badge.textContent = marker.badge
    pin.appendChild(badge)
  } else {
    const dot = document.createElementNS(SVG_NS, 'circle')
    dot.setAttribute('cx', '12')
    dot.setAttribute('cy', '12')
    dot.setAttribute('r', '4.5')
    dot.setAttribute('class', 'fill-bg-surface')
    pin.appendChild(dot)
  }
  root.appendChild(pin)

  if (!marker.hideLabel) {
    const label = document.createElement('span')
    label.className = `absolute top-0 left-[22px] whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-bold shadow-sm ${LABEL_CLASS[marker.tone]} ${
      marker.active ? 'ring-2 ring-border-brand ring-offset-1' : ''
    }`
    label.textContent = marker.label
    root.appendChild(label)
  }
  return root
}

/**
 * 네이버 지도(Figma 05-G·07-L 지도). 마커가 모두 보이게 맞추고, 마커가 바뀌면 다시 맞춘다.
 * <dialog>나 접힌 영역에서는 처음에 크기가 0이라, 크기가 생긴 뒤에 지도를 만들고 크기가 바뀌면 다시 맞춘다.
 */
export default function NaverMap({ keyId, markers, onSelect, className = 'h-56', label, focusId }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<naver.maps.Map | null>(null)
  const overlaysRef = useRef<naver.maps.Marker[]>([])
  const onSelectRef = useRef(onSelect)
  const markersRef = useRef(markers)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    onSelectRef.current = onSelect
    markersRef.current = markers
  })

  // 스크립트를 불러오고, 컨테이너에 크기가 생기면 지도를 만든다.
  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    let cancelled = false
    let observer: ResizeObserver | null = null
    loadNaverMaps(keyId)
      .then((maps) => {
        if (cancelled) return
        observer = new ResizeObserver(() => {
          if (el.clientWidth === 0 || el.clientHeight === 0) return
          if (mapRef.current) {
            mapRef.current.autoResize()
            return
          }
          mapRef.current = new maps.Map(el, {
            zoom: 16,
            scaleControl: false,
            mapDataControl: false,
            logoControlOptions: { position: maps.Position.BOTTOM_LEFT },
          })
          setReady(true)
        })
        observer.observe(el)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
      observer?.disconnect()
      overlaysRef.current.forEach((m) => m.setMap(null))
      overlaysRef.current = []
      mapRef.current?.destroy()
      mapRef.current = null
    }
  }, [keyId])

  // 마커를 다시 그리고 모두 보이게 맞춘다. 마커는 내용(signature)으로 비교한다(폴링으로 배열만 새로 와도 다시 그리지 않는다).
  const signature = markers.map((m) => `${m.id}|${m.lat}|${m.lng}|${m.label}|${m.tone}|${m.badge}|${m.active}|${m.hideLabel}`).join(',')
  // 지도를 다시 맞추는 것은 마커가 바뀔 때만이다(강조만 바뀌면 보던 자리를 그대로 둔다).
  const placement = markers.map((m) => `${m.id}|${m.lat}|${m.lng}`).join(',')
  useEffect(() => {
    const map = mapRef.current
    const markers = markersRef.current
    if (!ready || !map) return
    overlaysRef.current.forEach((m) => m.setMap(null))
    overlaysRef.current = markers.map((marker) => {
      const overlay = new naver.maps.Marker({
        map,
        position: new naver.maps.LatLng(marker.lat, marker.lng),
        icon: { content: markerElement(marker), anchor: new naver.maps.Point(0, 0) },
        // 식당을 회사 위에 그린다(회사 바로 옆 식당 글자가 가리지 않게). 내 메뉴·고른 식당, 목록에서 고른 마커가 위
        zIndex: marker.active ? 30 : marker.tone === 'office' ? 5 : marker.tone === 'brand' ? 20 : 10,
        title: marker.label,
      })
      naver.maps.Event.addListener(overlay, 'click', () => onSelectRef.current?.(marker.id))
      return overlay
    })
  }, [ready, signature])

  useEffect(() => {
    const map = mapRef.current
    const markers = markersRef.current
    if (!ready || !map) return
    if (markers.length === 1) {
      map.setCenter(new naver.maps.LatLng(markers[0].lat, markers[0].lng))
      map.setZoom(16)
    } else if (markers.length > 1) {
      const bounds = new naver.maps.LatLngBounds(
        new naver.maps.LatLng(markers[0].lat, markers[0].lng),
        new naver.maps.LatLng(markers[0].lat, markers[0].lng),
      )
      markers.forEach((m) => bounds.extend(new naver.maps.LatLng(m.lat, m.lng)))
      // 핀이 위로, 이름표가 오른쪽으로 나오므로 그쪽 여백을 더 둔다.
      map.fitBounds(bounds, { top: 56, right: 96, bottom: 24, left: 40 })
    }
  }, [ready, placement])

  // 목록에서 고른 마커로 옮긴다.
  useEffect(() => {
    const map = mapRef.current
    const marker = markersRef.current.find((m) => m.id === focusId)
    if (!ready || !map || !marker) return
    map.morph(new naver.maps.LatLng(marker.lat, marker.lng), Math.max(map.getZoom(), 16))
  }, [ready, focusId])

  return (
    <div className={`relative overflow-hidden rounded-xl bg-bg-muted ${className}`}>
      <div ref={containerRef} role="region" aria-label={label} className="size-full" />
      {failed && (
        <p className="absolute inset-0 flex items-center justify-center px-4 text-center text-sm text-text-tertiary">
          지도를 불러오지 못했어요
        </p>
      )}
    </div>
  )
}
