import type { KeyboardEvent } from 'react'
import { commentToggleLabel, commentsPanelId } from '../lib/comments.ts'
import { distanceLabel } from '../lib/distance.ts'
import { withJosa } from '../lib/josa.ts'
import { serviceLabel } from '../lib/link.ts'
import type { PollOption } from '../queries/polls.ts'
import Badge from './Badge.tsx'
import PersonChip from './PersonChip.tsx'
import { AdoptedBadge } from './RankingBadges.tsx'

type Props = {
  option: PollOption
  meId: number
  /** 마감 후 결과 모드: 누를 수 없고 확정 인원을 보여준다. */
  result?: boolean
  /** 결과 모드에서 채택된 메뉴(메뉴 메이커 랭킹): 「👑 채택」과 제안한 사람을 보여준다(Figma 05b-W). */
  adopted?: boolean
  selected?: boolean
  onSelect?: () => void
  onDelete?: () => void
  /** 내가 추가한 메뉴에서 「식당 고치기」·「＋ 식당 달기」를 누르면(진행 중일 때만) */
  onEditLink?: () => void
  disabled?: boolean
  /** 조직 위치에서 식당까지 직선거리(m). 조직·식당 위치를 찾았을 때만 */
  distance?: number
  /** 카카오 식당을 다시 찾은 이름과 그 이름으로 만든 「네이버 지도 ↗」 링크(저장하지 않는 값). 못 찾았으면 저장한 카카오 링크를 쓴다. */
  resolved?: { name: string; link: string }
  /** 지도에서 마커를 눌러 이 카드로 왔을 때 잠깐 강조한다. */
  highlighted?: boolean
  /** 댓글이 펼쳐져 있는지. 펼친 댓글(OptionComments)은 쓰는 쪽이 카드 바로 아래에 그린다. */
  commentsOpen?: boolean
  /** 「💬 댓글 N」을 누르면(없으면 버튼을 숨긴다) */
  onToggleComments?: () => void
}

/**
 * Figma OptionCard. 카드 전체를 누르면 그 메뉴에 참여한다(한 사람은 한 메뉴만). 내가 고른 카드를 다시 누르면 참여를 취소한다(미응답).
 * 상태: 기본 / 내 선택(오렌지 테두리) / 혼자(배지) / 비어 있음(내가 추가했으면 삭제) / 결과(채택된 메뉴는 「👑 채택」)
 * 식당이 있으면 「식당 이름 · 네이버 지도 ↗」(이름이 없으면 「지도 · 서비스 ↗」, Figma OptionCard Link=true, 05-L)를 보여주고,
 * 위치를 찾았으면 조직 위치에서의 거리·도보 시간(Figma Distance=true, 05-G)을 함께 보여준다.
 * 카드 아래 「💬 댓글 N」(Figma Comments=true, 05-K)을 누르면 댓글을 펼친다. 마감된 투표에서 댓글이 없으면 버튼을 숨긴다.
 * 지도 마커에서 찾아올 수 있게 id="option-{id}"를 둔다.
 */
export default function OptionCard({ option, meId, result, adopted, selected, onSelect, onDelete, onEditLink, disabled, distance, resolved, highlighted, commentsOpen, onToggleComments }: Props) {
  const count = option.voters.length
  const solo = count === 1
  const interactive = !result && !disabled
  const canEditLink = !result && option.mine && onEditLink != null
  const creator = option.mine ? '내가 추가' : `${withJosa(option.createdBy?.name ?? '탈퇴한 사용자', '이/가')} 추가`
  const proposer = option.mine ? '내가' : (option.createdBy?.name ?? '탈퇴한 사용자')
  const placeLink = resolved?.link ?? option.link
  const placeLabel = resolved?.name ?? option.placeName ?? '지도'
  const commentLabel = onToggleComments ? commentToggleLabel(option.commentCount, result === true) : null

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
      aria-label={result ? undefined : `${option.name} ${count}명${selected ? ', 참여 중, 누르면 취소' : ', 누르면 참여'}${solo ? ', 혼자예요' : ''}`}
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
            {result && adopted && (
              <span className="self-center">
                <AdoptedBadge />
              </span>
            )}
          </p>
          <p className="mt-0.5 text-xs text-text-tertiary">{result ? (adopted ? `확정 팀 · ${proposer} 제안` : '확정 팀') : creator}</p>
          {(option.link || canEditLink) && (
            <div className="mt-1.5 flex flex-wrap items-center gap-2">
              {placeLink && (
                <a
                  href={placeLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={(e) => e.stopPropagation()}
                  onKeyDown={(e) => e.stopPropagation()}
                  aria-label={`${resolved?.name ?? option.placeName ?? option.name} 식당 지도 열기 (${serviceLabel(placeLink)})`}
                  className="inline-flex max-w-full items-center rounded-full border border-border-default bg-bg-surface px-2 py-0.5 text-xs font-medium text-text-secondary hover:border-border-strong hover:text-text-primary focus-visible:outline-2 focus-visible:outline-border-brand"
                >
                  <span className="truncate">
                    {placeLabel} · {serviceLabel(placeLink)} ↗
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
      {commentLabel && (
        <div>
          <button
            type="button"
            aria-expanded={commentsOpen === true}
            aria-controls={commentsOpen ? commentsPanelId(option.id) : undefined}
            aria-label={`${option.name} ${commentLabel}${commentsOpen ? ' 접기' : ''}`}
            onClick={(e) => {
              e.stopPropagation()
              onToggleComments?.()
            }}
            onKeyDown={(e) => e.stopPropagation()}
            className="inline-flex items-center gap-1 rounded-full border border-border-default bg-bg-surface px-2.5 py-1 text-xs font-medium text-text-secondary hover:border-border-strong hover:text-text-primary focus-visible:outline-2 focus-visible:outline-border-brand"
          >
            <span aria-hidden>💬</span>
            {commentLabel}
            <span aria-hidden>{commentsOpen ? '▴' : '▾'}</span>
          </button>
        </div>
      )}
    </div>
  )
}
