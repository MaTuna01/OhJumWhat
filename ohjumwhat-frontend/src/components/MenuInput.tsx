import { type FormEvent, useCallback, useEffect, useId, useRef, useState } from 'react'
import { useDismiss } from '../hooks/useDismiss.ts'
import { useNow } from '../hooks/useNow.ts'
import { EMPTY_PLACE, placeInput } from '../lib/place.ts'
import { daysAgo, formatEatenDay } from '../lib/time.ts'
import { inputClass } from '../lib/ui.ts'
import { useMenuNames, useMenuRecommendations } from '../queries/polls.ts'
import Button from './Button.tsx'
import PlaceFields from './PlaceFields.tsx'

type Props = {
  orgId: number
  /** 조직 검색 지역(「네이버 지도에서 찾기」 검색어 앞에 붙인다) */
  area: string | null
  /** 이미 이 투표에 있는 메뉴(자동완성·추천에서 뺀다) */
  existing: string[]
  pending: boolean
  onAdd: (name: string, link: string | null, placeName: string | null) => Promise<unknown>
}

type Item = { name: string; caption: string }

/**
 * 메뉴 추가 입력창(Figma 05·05-L·05-R).
 * - 비운 채 누르면 "오늘은 이거 어때요?"(자주 먹었지만 최근 7일 안에는 먹지 않은 메뉴)를 보여준다.
 * - 입력하면 같은 조직에서 전에 나온 메뉴를 자동완성하고, 마지막으로 먹은 날을 함께 보여준다.
 * - 「＋ 식당」으로 식당 패널(Figma 05-L)을 열어 네이버 지도에서 찾고 공유 링크를 붙일 수 있다(선택).
 */
export default function MenuInput({ orgId, area, existing, pending, onAdd }: Props) {
  const [value, setValue] = useState('')
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [placeOpen, setPlaceOpen] = useState(false)
  const [place, setPlace] = useState(EMPTY_PLACE)
  const ref = useRef<HTMLDivElement>(null)
  const listId = useId()
  const close = useCallback(() => setOpen(false), [])
  useDismiss(ref, open, close)
  const now = useNow(60_000)

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
        .map((m) => ({ name: m.name, caption: `${m.times}번 먹었어요 · ${formatEatenDay(m.lastEatenOn, now)}` }))
    : (names.data ?? [])
        .filter((s) => !existing.includes(s.name))
        .slice(0, 6)
        .map((s) => ({ name: s.name, caption: s.lastEatenOn ? eatenCaption(s.lastEatenOn, now) : '전에 나온 메뉴' }))
  const showList = open && (recommending || query.length > 0) && items.length > 0

  const submit = async (name: string) => {
    const trimmed = name.trim()
    if (!trimmed || pending) return
    const { link, placeName } = placeOpen ? placeInput(place) : placeInput(EMPTY_PLACE)
    try {
      await onAdd(trimmed, link, placeName)
      setValue('')
      setQuery('')
      setOpen(false)
      setActive(-1)
      setPlace(EMPTY_PLACE)
      setPlaceOpen(false)
    } catch {
      // 오류 문구는 투표 화면이 보여준다. 입력값은 그대로 둔다.
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    submit(showList && active >= 0 ? items[active].name : value)
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
              if (!showList) return
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
              {recommending && <p className="px-3 pt-1.5 pb-1 text-xs font-medium text-text-tertiary">오늘은 이거 어때요? · 최근 7일 동안 안 먹은 메뉴</p>}
              <ul id={listId} role="listbox" aria-label={recommending ? '추천 메뉴' : '전에 나온 메뉴'}>
                {items.map((item, i) => (
                  <li
                    key={item.name}
                    id={`${listId}-${i}`}
                    role="option"
                    aria-selected={i === active}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => submit(item.name)}
                    className={`flex cursor-pointer items-center justify-between gap-3 px-3 py-2 text-sm ${i === active ? 'bg-bg-muted' : 'hover:bg-bg-subtle'}`}
                  >
                    <span className="truncate">{item.name}</span>
                    <span className="shrink-0 text-xs text-text-placeholder">{item.caption}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {placeOpen ? (
          <div className="space-y-2 rounded-xl border border-border-default bg-bg-subtle p-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-medium">식당(선택)</p>
              <button
                type="button"
                onClick={() => {
                  setPlaceOpen(false)
                  setPlace(EMPTY_PLACE)
                }}
                className="shrink-0 text-xs font-medium text-text-tertiary hover:text-text-secondary"
              >
                식당 빼기
              </button>
            </div>
            <PlaceFields value={place} onChange={setPlace} searchQuery={value} area={area} />
          </div>
        ) : (
          <button type="button" onClick={() => setPlaceOpen(true)} className="text-xs font-medium text-text-tertiary hover:text-text-secondary">
            ＋ 식당
          </button>
        )}
      </form>
    </div>
  )
}

/** 자동완성에서 마지막으로 먹은 날: "오늘 먹었어요", "3일 전에 먹었어요", "8월 1일에 먹었어요" */
function eatenCaption(day: string, now: number): string {
  const days = daysAgo(day, now)
  if (days <= 0) return '오늘 먹었어요'
  if (days === 1) return '어제 먹었어요'
  return `${formatEatenDay(day, now)}에 먹었어요`
}
