import { gapToFirst } from '../lib/ranking.ts'
import type { Ranking } from '../queries/ranking.ts'
import Avatar from './Avatar.tsx'

type Props = {
  ranking: Ranking
  /** 기간 이름(이번 주, 2026년 9월 …) */
  periodName: string
  me: { name: string; profileImageUrl: string | null }
  className?: string
}

/**
 * Figma MyRankCard: 내 순위. 모바일은 화면 아래에 떠 있고(fixed, 쓰는 쪽이 위치를 준다), 데스크톱은 사이드 맨 위 카드다.
 * Ranked: 「N위 · M회 채택」 + 1위까지 남은 횟수. Unranked: 이 기간에 채택이 없을 때.
 */
export default function MyRankCard({ ranking, periodName, me, className = '' }: Props) {
  const { rank, count } = ranking.me
  return (
    <section
      aria-label="내 순위"
      className={`flex items-center gap-3 rounded-2xl border border-border-brand bg-bg-surface px-4 py-3 shadow-lg ${className}`}
    >
      <Avatar name={me.name} imageUrl={me.profileImageUrl} />
      <div className="min-w-0 flex-1">
        <p className="text-xs text-text-tertiary">내 순위 · {periodName}</p>
        {rank == null ? (
          <>
            <p className="text-sm font-bold">아직 채택된 메뉴가 없어요</p>
            <p className="text-xs text-text-tertiary">메뉴를 제안해 보세요. 가장 많이 고른 메뉴가 채택돼요</p>
          </>
        ) : (
          <p className="font-bold">
            {rank}위 · {count}회 채택
          </p>
        )}
      </div>
      {rank != null && ranking.entries.length > 0 && (
        <p className="shrink-0 text-xs font-medium text-text-brand-strong">{gapToFirst(rank, count, ranking.entries[0].count)}</p>
      )}
    </section>
  )
}
