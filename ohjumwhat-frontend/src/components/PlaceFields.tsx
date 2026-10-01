import { type ClipboardEvent, useId } from 'react'
import { type PlaceValue, naverSearchUrl, parseShareText } from '../lib/place.ts'
import { buttonClass, inputClass } from '../lib/ui.ts'

type Props = {
  value: PlaceValue
  onChange: (value: PlaceValue) => void
  /** 「네이버 지도에서 찾기」 검색어(메뉴 이름 등). 비면 버튼을 막는다. */
  searchQuery: string
  /** 조직 검색 지역(예: 역삼동). 검색어 앞에 붙인다. */
  area: string | null
  /** 이름 입력칸 이름(기본 "식당 이름") */
  nameLabel?: string
  /** 링크 입력칸 이름(기본 "식당 지도 링크") */
  linkLabel?: string
}

/**
 * 식당 붙이기 입력(Figma 05-L·05-M4·07-L): 「네이버 지도에서 찾기 ↗」, 공유 링크, 식당 이름(선택).
 * 공유 글을 붙이면 식당 이름을 미리 채운다. naver.me 링크는 서버가 장소 정식 링크로 바꾼다.
 */
export default function PlaceFields({ value, onChange, searchQuery, area, nameLabel = '식당 이름', linkLabel = '식당 지도 링크' }: Props) {
  const id = useId()
  const query = searchQuery.trim()
  const searchLabel = [area?.trim(), query].filter(Boolean).join(' ')

  // 이름을 직접 고치지 않았으면(비었거나 이전 공유 글에서 채운 그대로면) 새 공유 글의 이름으로 바꾼다.
  const nameFor = (shareText: string) => {
    const previous = parseShareText(value.link).name ?? ''
    return !value.name || value.name === previous ? (parseShareText(shareText).name ?? '') : value.name
  }

  const changeLink = (link: string) => onChange({ link, name: nameFor(link) })

  // 한 줄 입력칸은 붙여 넣은 글의 줄바꿈을 지워 "이름주소링크"가 붙어 버린다.
  // 여러 줄 공유 글은 줄바꿈이 있는 원문으로 이름을 꺼내고, 칸에는 줄바꿈을 공백으로 바꿔 넣는다.
  const onPaste = (e: ClipboardEvent<HTMLInputElement>) => {
    const text = e.clipboardData.getData('text')
    if (!/[\r\n]/.test(text)) return
    e.preventDefault()
    onChange({ link: text.replace(/\s*[\r\n]+\s*/g, ' ').trim(), name: nameFor(text) })
  }

  return (
    <div className="space-y-2">
      {query ? (
        <a href={naverSearchUrl(area, query)} target="_blank" rel="noopener noreferrer" className={buttonClass('secondary', 'w-full')}>
          <span className="truncate">네이버 지도에서 「{searchLabel}」 찾기 ↗</span>
        </a>
      ) : (
        <span aria-disabled="true" className={buttonClass('secondary', 'w-full cursor-not-allowed opacity-50')}>
          메뉴 이름을 먼저 입력하세요
        </span>
      )}
      <input
        value={value.link}
        onChange={(e) => changeLink(e.target.value)}
        onPaste={onPaste}
        maxLength={1000}
        placeholder="네이버 지도 공유 링크를 붙여 넣으세요"
        aria-label={linkLabel}
        className={inputClass}
      />
      {value.link.trim() && (
        <div>
          <label htmlFor={`${id}-name`} className="sr-only">
            {nameLabel}(선택)
          </label>
          <input
            id={`${id}-name`}
            value={value.name}
            onChange={(e) => onChange({ ...value, name: e.target.value })}
            maxLength={100}
            placeholder={`${nameLabel}(선택)`}
            className={inputClass}
          />
        </div>
      )}
      <p className="text-xs text-text-tertiary">
        네이버 지도에서 찾아 「공유 → 링크 복사」 후 붙여 넣으세요. 이름은 공유 글에서 채워요. 다른 지도 링크도 돼요.
      </p>
    </div>
  )
}
