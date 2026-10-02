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
}

const TONE_CLASS: Record<MapMarker['tone'], string> = {
  office: 'bg-bg-inverse text-text-on-brand',
  brand: 'bg-bg-brand text-text-on-brand',
  default: 'border border-border-default bg-bg-surface text-text-primary',
}

/** 마커 내용: 지점 위에 가운데 맞춘 알약 모양(Figma 05-G Marker) */
function markerElement(marker: MapMarker): HTMLElement {
  const el = document.createElement('div')
  el.className = `-translate-x-1/2 -translate-y-full cursor-pointer whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-bold shadow-md ${TONE_CLASS[marker.tone]}`
  el.textContent = marker.label
  return el
}

/**
 * 네이버 지도(Figma 05-G·07-L 지도). 마커가 모두 보이게 맞추고, 마커가 바뀌면 다시 맞춘다.
 * <dialog>나 접힌 영역에서는 처음에 크기가 0이라, 크기가 생긴 뒤에 지도를 만들고 크기가 바뀌면 다시 맞춘다.
 */
export default function NaverMap({ keyId, markers, onSelect, className = 'h-56', label }: Props) {
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
  const signature = markers.map((m) => `${m.id}|${m.lat}|${m.lng}|${m.label}|${m.tone}`).join(',')
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
        zIndex: marker.tone === 'default' ? 10 : 20,
        title: marker.label,
      })
      naver.maps.Event.addListener(overlay, 'click', () => onSelectRef.current?.(marker.id))
      return overlay
    })
    if (markers.length === 1) {
      map.setCenter(new naver.maps.LatLng(markers[0].lat, markers[0].lng))
      map.setZoom(16)
    } else if (markers.length > 1) {
      const bounds = new naver.maps.LatLngBounds(
        new naver.maps.LatLng(markers[0].lat, markers[0].lng),
        new naver.maps.LatLng(markers[0].lat, markers[0].lng),
      )
      markers.forEach((m) => bounds.extend(new naver.maps.LatLng(m.lat, m.lng)))
      map.fitBounds(bounds, { top: 48, right: 48, bottom: 24, left: 48 })
    }
  }, [ready, signature])

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
