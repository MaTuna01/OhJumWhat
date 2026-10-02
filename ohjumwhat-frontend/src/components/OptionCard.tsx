import type { KeyboardEvent } from 'react'
import { distanceLabel } from '../lib/distance.ts'
import { withJosa } from '../lib/josa.ts'
import { serviceLabel } from '../lib/link.ts'
import type { PollOption } from '../queries/polls.ts'
import Badge from './Badge.tsx'
import PersonChip from './PersonChip.tsx'

type Props = {
  option: PollOption
  meId: number
  /** 마감 후 결과 모드: 누를 수 없고 확정 인원을 보여준다. */
  result?: boolean
  selected?: boolean
  onSelect?: () => void
  onDelete?: () => void
  /** 내가 추가한 메뉴에서 「식당 고치기」·「＋ 식당 달기」를 누르면(진행 중일 때만) */
  onEditLink?: () => void
  disabled?: boolean
  /** 회사에서 식당까지 직선거리(m). 회사·식당 위치를 찾았을 때만 */
  distance?: number
  /** 지도에서 마커를 눌러 이 카드로 왔을 때 잠깐 강조한다. */
  highlighted?: boolean
}

/**
 * Figma OptionCard. 카드 전체를 누르면 그 메뉴에 참여한다(한 사람은 한 메뉴만).
 * 상태: 기본 / 내 선택(오렌지 테두리) / 혼자(배지) / 비어 있음(내가 추가했으면 삭제) / 결과
 * 식당이 있으면 「식당 이름 · 네이버 지도 ↗」(이름이 없으면 「지도 · 서비스 ↗」, Figma OptionCard Link=true, 05-L)를 보여주고,
 * 위치를 찾았으면 회사에서의 거리·도보 시간(Figma Distance=true, 05-G)을 함께 보여준다.
 * 지도 마커에서 찾아올 수 있게 id="option-{id}"를 둔다.
 */
export default function OptionCard({ option, meId, result, selected, onSelect, onDelete, onEditLink, disabled, distance, highlighted }: Props) {
  const count = option.voters.length
  const solo = count === 1
  const interactive = !result && !disabled
  const canEditLink = !result && option.mine && onEditLink != null
  const creator = option.mine ? '내가 추가' : `${withJosa(option.createdBy?.name ?? '탈퇴한 사용자', '이/가')} 추가`

  const onKeyDown = (e: KeyboardEvent) => {
    if (interactive && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault()
      onSelect?.()
    }
  }

  return (
    <div
      id={`option-${option.id}`}
      role={result ? undefined : 'button'}
      tabIndex={interactive ? 0 : undefined}
      aria-pressed={result ? undefined : selected}
      aria-label={result ? undefined : `${option.name} ${count}명${selected ? ', 참여 중' : ', 누르면 참여'}${solo ? ', 혼자예요' : ''}`}
      aria-disabled={!result && disabled ? true : undefined}
      onClick={interactive ? onSelect : undefined}
      onKeyDown={onKeyDown}
      className={`flex scroll-mt-24 flex-col gap-3 rounded-2xl border p-4 text-left transition-[colors,box-shadow] ${
        highlighted && !selected ? 'ring-2 ring-border-brand-soft' : ''
      } ${
        selected
          ? 'border-border-brand bg-bg-brand-soft ring-1 ring-border-brand'
          : 'border-border-default bg-bg-surface'
      } ${interactive ? 'cursor-pointer hover:border-border-brand-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand' : ''} ${
        !result && disabled ? 'opacity-60' : ''
      }`}
    >
      <div className="flex items-center gap-2">
        <div className="min-w-0 flex-1">
          <p className="flex items-baseline gap-2">
            <span className="truncate font-bold">{option.name}</span>
            <span className={`shrink-0 text-sm font-bold ${selected ? 'text-text-brand' : 'text-text-tertiary'}`}>{count}명</span>
          </p>
          <p className="mt-0.5 text-xs text-text-tertiary">{result ? '확정 팀' : creator}</p>
          {(option.link || canEditLink) && (
            <div className="mt-1.5 flex flex-wrap items-center gap-2">
              {option.link && (
                <a
                  href={option.link}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={(e) => e.stopPropagation()}
                  onKeyDown={(e) => e.stopPropagation()}
                  aria-label={`${option.placeName ?? option.name} 식당 지도 열기 (${serviceLabel(option.link)})`}
                  className="inline-flex max-w-full items-center rounded-full border border-border-default bg-bg-surface px-2 py-0.5 text-xs font-medium text-text-secondary hover:border-border-strong hover:text-text-primary focus-visible:outline-2 focus-visible:outline-border-brand"
                >
                  <span className="truncate">
                    {option.placeName ?? '지도'} · {serviceLabel(option.link)} ↗
                  </span>
                </a>
              )}
              {option.link && distance != null && <span className="text-xs text-text-tertiary">{distanceLabel(distance)}</span>}
              {canEditLink && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation()
                    onEditLink?.()
                  }}
                  onKeyDown={(e) => e.stopPropagation()}
                  className="text-xs font-medium text-text-brand hover:underline focus-visible:outline-2 focus-visible:outline-border-brand"
                >
                  {option.link ? '식당 고치기' : '＋ 식당 달기'}
                </button>
              )}
            </div>
          )}
        </div>
        {result ? (
          <Badge tone={solo ? 'warning' : 'success'}>{solo ? '혼자 가요' : `확정 ${count}명`}</Badge>
        ) : (
          <>
            {solo && <Badge tone="warning">혼자예요</Badge>}
            {option.deletable && onDelete && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation()
                  onDelete()
                }}
                onKeyDown={(e) => e.stopPropagation()}
                className="shrink-0 rounded-md px-1.5 py-0.5 text-sm font-medium text-text-danger hover:bg-bg-danger-soft focus-visible:outline-2 focus-visible:outline-border-brand"
              >
                삭제
              </button>
            )}
            <span
              aria-hidden
              className={`flex size-6 shrink-0 items-center justify-center rounded-full text-sm font-bold ${
                selected ? 'bg-bg-brand text-text-on-brand' : 'border-2 border-border-strong bg-bg-surface'
              }`}
            >
              {selected && '✓'}
            </span>
          </>
        )}
      </div>
      {count > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {option.voters.map((v) => (
            <PersonChip key={v.userId} person={v} isMe={v.userId === meId} />
          ))}
        </div>
      ) : (
        <p className="text-xs text-text-placeholder">
          아직 아무도 없어요{option.deletable && ' · 참여자가 없어서 삭제할 수 있어요'}
        </p>
      )}
    </div>
  )
}
