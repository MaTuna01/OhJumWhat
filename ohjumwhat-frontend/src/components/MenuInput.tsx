import { type FormEvent, useCallback, useEffect, useId, useRef, useState } from 'react'
import { useDismiss } from '../hooks/useDismiss.ts'
import { inputClass } from '../lib/ui.ts'
import { useMenuNames } from '../queries/polls.ts'
import Button from './Button.tsx'

type Props = {
  orgId: number
  /** 이미 이 투표에 있는 메뉴(자동완성에서 뺀다) */
  existing: string[]
  pending: boolean
  onAdd: (name: string) => Promise<unknown>
}

/** 메뉴 추가 입력창. 같은 조직에서 전에 나온 메뉴를 자동완성으로 보여준다. */
export default function MenuInput({ orgId, existing, pending, onAdd }: Props) {
  const [value, setValue] = useState('')
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const ref = useRef<HTMLDivElement>(null)
  const listId = useId()
  const close = useCallback(() => setOpen(false), [])
  useDismiss(ref, open, close)

  // 입력이 멈추고 200ms 뒤에 검색한다.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(value.trim()), 200)
    return () => clearTimeout(timer)
  }, [value])

  const names = useMenuNames(orgId, query, open && query.length > 0)
  const suggestions = (names.data ?? []).filter((n) => !existing.includes(n)).slice(0, 6)
  const showList = open && query.length > 0 && suggestions.length > 0

  const submit = async (name: string) => {
    const trimmed = name.trim()
    if (!trimmed || pending) return
    try {
      await onAdd(trimmed)
      setValue('')
      setQuery('')
      setOpen(false)
      setActive(-1)
    } catch {
      // 오류 문구는 투표 화면이 보여준다. 입력값은 그대로 둔다.
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    submit(showList && active >= 0 ? suggestions[active] : value)
  }

  return (
    <div ref={ref} className="relative">
      <form onSubmit={onSubmit} className="flex gap-2">
        <input
          value={value}
          onChange={(e) => {
            setValue(e.target.value)
            setOpen(true)
            setActive(-1)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={(e) => {
            if (!showList) return
            if (e.key === 'ArrowDown') {
              e.preventDefault()
              setActive((i) => (i + 1) % suggestions.length)
            } else if (e.key === 'ArrowUp') {
              e.preventDefault()
              setActive((i) => (i <= 0 ? suggestions.length - 1 : i - 1))
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
      </form>
      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute top-full right-0 left-0 z-10 mt-1.5 overflow-hidden rounded-xl border border-border-default bg-bg-surface py-1 shadow-lg"
        >
          {suggestions.map((name, i) => (
            <li
              key={name}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => submit(name)}
              className={`flex cursor-pointer items-center justify-between px-3 py-2 text-sm ${i === active ? 'bg-bg-muted' : 'hover:bg-bg-subtle'}`}
            >
              {name}
              <span className="text-xs text-text-placeholder">전에 나온 메뉴</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
