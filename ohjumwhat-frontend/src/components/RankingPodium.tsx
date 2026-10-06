import type { ReactNode } from 'react'
import { medalOf } from '../lib/ranking.ts'
import type { RankingEntry } from '../queries/ranking.ts'
import Avatar from './Avatar.tsx'

type Props = {
  /** 「이번 주 메뉴 메이커」 */
  title: string
  closedPollCount: number
  adoptedPollCount: number
  /** 순위 순 전체 목록(앞의 세 사람을 올린다) */
  entries: RankingEntry[]
  onSelect: (entry: RankingEntry) => void
  /** 포디움 아래 안내(지난달 「이달의 메뉴 메이커」) */
  notice?: ReactNode
}

/**
 * 앞의 세 사람을 2위 · 1위 · 3위 순서로 보이게 하는 칸 위치. 목록(DOM)은 순위 순서라 스크린리더는 1위부터 읽는다.
 * 같은 순위가 여럿이면 보여주는 순서(먼저 도달한 사람)대로 칸을 채운다.
 */
const SLOT_ORDER = ['order-2', 'order-1', 'order-3']

/** 순위(메달)별 칸 색. 새 토큰 없이 1위 warning-soft, 2위 muted, 3위 brand-muted */
const tones: Record<number, { box: string; title: string }> = {
  1: { box: 'bg-bg-warning-soft pt-6', title: 'text-text-warning' },
  2: { box: 'bg-bg-muted pt-3.5', title: 'text-text-secondary' },
  3: { box: 'bg-bg-brand-muted pt-3.5', title: 'text-text-brand-strong' },
}

/** Figma 「메뉴 메이커」 카드: TOP3 포디움(2위 · 1위 · 3위로 보이고 1위가 가장 높다). 칸을 누르면 채택 기록 모달(11-M)을 연다. */
export default function RankingPodium({ title, closedPollCount, adoptedPollCount, entries, onSelect, notice }: Props) {
  return (
    <section className="space-y-4 rounded-2xl border border-border-default bg-bg-surface px-4 pt-5 pb-4" aria-label={title}>
      <div className="flex items-center justify-between gap-3 px-1">
        <h2 className="font-bold">{title}</h2>
        <p className="shrink-0 text-xs text-text-tertiary">
          마감 {closedPollCount}번 · 채택 {adoptedPollCount}번
        </p>
      </div>
      <ol className="flex items-end gap-2">
        {SLOT_ORDER.map((order, i) => {
          const entry = entries[i]
          // 세 사람이 안 되면 빈 칸을 남겨 1위가 가운데에 오게 한다.
          return entry ? (
            <li key={entry.user.userId} className={`min-w-0 flex-1 ${order}`}>
              <PodiumItem entry={entry} onSelect={() => onSelect(entry)} />
            </li>
          ) : (
            <li key={`empty-${order}`} aria-hidden className={`flex-1 ${order}`} />
          )
        })}
      </ol>
      {notice}
    </section>
  )
}

function PodiumItem({ entry, onSelect }: { entry: RankingEntry; onSelect: () => void }) {
  const medal = medalOf(entry.rank)
  const tone = tones[entry.rank] ?? tones[3]
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`${entry.rank}위 ${entry.user.name}, ${entry.count}회 채택, 채택 기록 보기`}
      className={`flex w-full flex-col items-center gap-1 rounded-2xl px-2 pb-3.5 text-center transition-[filter] hover:brightness-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand ${tone.box}`}
    >
      <span aria-hidden className="text-[22px] leading-[26px]">
        {medal?.emoji}
      </span>
      <Avatar name={entry.user.name} imageUrl={entry.user.profileImageUrl} size="lg" />
      <span className="w-full truncate text-sm font-bold">{entry.user.name}</span>
      <span className={`text-xs font-medium ${tone.title}`}>{medal?.title}</span>
      <span className="text-lg font-bold tracking-tight">{entry.count}회</span>
      <span className="w-full truncate text-xs text-text-tertiary">{entry.topMenu}</span>
    </button>
  )
}
