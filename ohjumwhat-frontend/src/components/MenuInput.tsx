import { type FormEvent, useCallback, useEffect, useId, useRef, useState } from 'react'
import { useDismiss } from '../hooks/useDismiss.ts'
import { useNow } from '../hooks/useNow.ts'
import { distanceLabel } from '../lib/distance.ts'
import { NO_PLACE, type PlaceInput } from '../lib/place.ts'
import { daysAgo, formatEatenDay } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { useClientConfig } from '../queries/config.ts'
import { useOrganization } from '../queries/orgs.ts'
import { usePlaces } from '../queries/places.ts'
import { type LastPlace, useMenuNames, useMenuRecommendations } from '../queries/polls.ts'
import Button from './Button.tsx'
import PlaceModal, { type PickedPlace } from './PlaceModal.tsx'

type Props = {
  orgId: number
  /** 이미 이 투표에 있는 메뉴(자동완성·추천에서 뺀다) */
  existing: string[]
  pending: boolean
  onAdd: (name: string, place: PlaceInput) => Promise<unknown>
}

type Item = { name: string; caption: string; lastPlace: LastPlace | null }

/**
 * 메뉴 추가 입력창(Figma 05·05-R·05-R2).
 * - 비운 채 누르면 "오늘은 이거 어때요?"(자주 먹었지만 최근 7일 안에는 먹지 않은 메뉴)와 「근처 식당 둘러보기 →」를 보여준다.
 * - 입력하면 같은 조직에서 전에 나온 메뉴를 자동완성하고, 마지막으로 먹은 날을 함께 보여준다.
 * - 같은 메뉴에 지난번 붙인 식당이 있으면 「지난번: 할매집」을 보여주고, 고르면 그 식당까지 붙여 추가한다.
 * - 「＋ 식당」으로 식당 찾기 모달(05-F)을 열어 근처 식당을 고르거나 링크를 붙인다. 고른 식당은 입력 아래 칩으로 보여준다.
 */
export default function MenuInput({ orgId, existing, pending, onAdd }: Props) {
  const [value, setValue] = useState('')
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [picked, setPicked] = useState<PickedPlace | null>(null)
  const [finder, setFinder] = useState<'closed' | 'menu' | 'browse'>('closed')
  const ref = useRef<HTMLDivElement>(null)
  const listId = useId()
  const close = useCallback(() => setOpen(false), [])
  useDismiss(ref, open, close)
  const now = useNow(60_000)
  const { data: config } = useClientConfig()
  const { data: org } = useOrganization(orgId)
  const canBrowse = Boolean(config?.placeSearch && org?.officeAddress)

  // 입력이 멈추고 200ms 뒤에 검색한다.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(value.trim()), 200)
    return () => clearTimeout(timer)
  }, [value])

  const recommending = value.trim().length === 0
  const names = useMenuNames(orgId, query, open && query.length > 0)
  const recommendations = useMenuRecommendations(orgId, open && recommending)
  const items: Item[] = recommending
    ? (recommendations.data ?? [])
        .filter((m) => !existing.includes(m.name))
        .slice(0, 5)
        .map((m) => ({ name: m.name, caption: `${m.times}번 먹었어요 · ${formatEatenDay(m.lastEatenOn, now)}`, lastPlace: m.lastPlace }))
    : (names.data ?? [])
        .filter((s) => !existing.includes(s.name))
        .slice(0, 6)
        .map((s) => ({ name: s.name, caption: s.lastEatenOn ? eatenCaption(s.lastEatenOn, now) : '전에 나온 메뉴', lastPlace: s.lastPlace }))
  const showList = open && (recommending || query.length > 0) && (items.length > 0 || (recommending && canBrowse))

  // 카카오 식당은 이름을 저장하지 않으므로, 보이는 항목의 지난번 식당 이름을 받아 온다.
  const kakaoRefs = items
    .map((item) => item.lastPlace)
    .filter((p): p is LastPlace => p?.kakaoPlaceId != null)
    .map((p) => ({ ...p, id: p.optionId }))
  const lastPlaceSpots = usePlaces(orgId, org?.officeAddress ?? null, kakaoRefs, showList && kakaoRefs.length > 0 && Boolean(config?.placeSearch))
  const lastPlaceName = (place: LastPlace) =>
    place.kakaoPlaceId ? lastPlaceSpots.data?.places.find((s) => s.optionId === place.optionId)?.name : place.placeName

  const submit = async (name: string, lastPlace?: LastPlace | null) => {
    const trimmed = name.trim()
    if (!trimmed || pending) return
    // 직접 고른 식당이 있으면 그 식당, 없으면 지난번 식당을 붙인다.
    const place = picked?.input ?? (lastPlace ? lastPlaceInput(lastPlace) : NO_PLACE)
    try {
      await onAdd(trimmed, place)
      setValue('')
      setQuery('')
      setOpen(false)
      setActive(-1)
      setPicked(null)
    } catch {
      // 오류 문구는 투표 화면이 보여준다. 입력값은 그대로 둔다.
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (showList && active >= 0) submit(items[active].name, items[active].lastPlace)
    else submit(value)
  }

  return (
    <div ref={ref}>
      <form onSubmit={onSubmit} className="space-y-1.5">
        <div className="relative flex gap-2">
          <input
            value={value}
            onChange={(e) => {
              setValue(e.target.value)
              setOpen(true)
              setActive(-1)
            }}
            onFocus={() => setOpen(true)}
            onClick={() => setOpen(true)}
            onKeyDown={(e) => {
              if (!showList || items.length === 0) return
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActive((i) => (i + 1) % items.length)
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActive((i) => (i <= 0 ? items.length - 1 : i - 1))
              }
            }}
            maxLength={50}
            placeholder="먹고 싶은 메뉴를 추가하세요"
            aria-label="메뉴 이름"
            role="combobox"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
            className={inputClass}
          />
          <Button type="submit" className="shrink-0" disabled={!value.trim() || pending}>
            추가
          </Button>
          {showList && (
            <div className="absolute top-full right-0 left-0 z-10 mt-1.5 overflow-hidden rounded-xl border border-border-default bg-bg-surface py-1 shadow-lg">
              {recommending && items.length > 0 && (
                <p className="px-3 pt-1.5 pb-1 text-xs font-medium text-text-tertiary">오늘은 이거 어때요? · 최근 7일 동안 안 먹은 메뉴</p>
              )}
              <ul id={listId} role="listbox" aria-label={recommending ? '추천 메뉴' : '전에 나온 메뉴'}>
                {items.map((item, i) => (
                  <li
                    key={item.name}
                    id={`${listId}-${i}`}
                    role="option"
                    aria-selected={i === active}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => submit(item.name, item.lastPlace)}
                    className={`flex cursor-pointer items-center justify-between gap-3 px-3 py-2 text-sm ${i === active ? 'bg-bg-muted' : 'hover:bg-bg-subtle'}`}
                  >
                    <span className="truncate">{item.name}</span>
                    <span className="shrink-0 text-xs text-text-placeholder">
                      {item.lastPlace && !picked ? `지난번: ${lastPlaceName(item.lastPlace) ?? '식당'} · ${item.caption}` : item.caption}
                    </span>
                  </li>
                ))}
              </ul>
              {recommending && canBrowse && (
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => {
                    setOpen(false)
                    setFinder('browse')
                  }}
                  className="mt-1 w-full border-t border-border-default px-3 py-2.5 text-left text-sm font-medium text-text-brand hover:bg-bg-subtle"
                >
                  근처 식당 둘러보기 →
                </button>
              )}
            </div>
          )}
        </div>

        {picked ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-border-brand bg-bg-brand-soft py-0.5 pr-1.5 pl-2.5 text-xs font-medium text-text-brand-strong">
              <span className="truncate">{[picked.name ?? '식당', picked.distance != null ? distanceLabel(picked.distance) : null].filter(Boolean).join(' · ')}</span>
              <button
                type="button"
                onClick={() => setPicked(null)}
                aria-label="고른 식당 빼기"
                className="rounded-full px-1 font-bold text-text-brand hover:bg-bg-brand-muted focus-visible:outline-2 focus-visible:outline-border-brand"
              >
                ✕
              </button>
            </span>
            <button type="button" onClick={() => setFinder('menu')} className="text-xs font-medium text-text-tertiary hover:text-text-secondary">
              식당 바꾸기
            </button>
          </div>
        ) : (
          <button type="button" onClick={() => setFinder('menu')} className="text-xs font-medium text-text-tertiary hover:text-text-secondary">
            ＋ 식당
          </button>
        )}
      </form>

      <PlaceModal
        orgId={orgId}
        open={finder !== 'closed'}
        title={finder === 'browse' ? '근처 식당' : value.trim() ? `${value.trim()} 식당` : '식당 붙이기'}
        menuName={finder === 'browse' ? '' : value.trim()}
        confirmLabel="이 식당으로"
        onConfirm={(place) => {
          setPicked(place)
          setFinder('closed')
        }}
        onClose={() => setFinder('closed')}
      />
    </div>
  )
}

/** 지난번 식당을 그대로 붙인다(카카오 식당은 장소 ID와 검색어만). */
function lastPlaceInput(place: LastPlace): PlaceInput {
  return place.kakaoPlaceId
    ? { ...NO_PLACE, kakaoPlaceId: place.kakaoPlaceId, placeQuery: place.placeQuery }
    : { ...NO_PLACE, link: place.link, placeName: place.placeName, placeAddress: place.placeAddress }
}

/** 자동완성에서 마지막으로 먹은 날: "오늘 먹었어요", "3일 전에 먹었어요", "8월 1일에 먹었어요" */
function eatenCaption(day: string, now: number): string {
  const days = daysAgo(day, now)
  if (days <= 0) return '오늘 먹었어요'
  if (days === 1) return '어제 먹었어요'
  return `${formatEatenDay(day, now)}에 먹었어요`
}
