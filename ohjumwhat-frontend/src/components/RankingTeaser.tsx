import { Link } from 'react-router'
import { useNow } from '../hooks/useNow.ts'
import { medalOf, periodRange } from '../lib/ranking.ts'
import { kstDayKey } from '../lib/time.ts'
import { type RankingEntry, useRanking } from '../queries/ranking.ts'
import Avatar from './Avatar.tsx'
import { Section } from './PageState.tsx'

const TOP = 3
const EMPTY = '아직 채택된 메뉴가 없어요. 투표가 마감되면 가장 많이 고른 메뉴를 제안한 사람이 올라가요.'

/** 메달 색: 1위 text-warning, 그 밖은 text-secondary(Figma 04-R·D04-R) */
function Medal({ rank }: { rank: number }) {
  return (
    <span aria-hidden className={`shrink-0 text-sm ${rank === 1 ? 'text-text-warning' : 'text-text-secondary'}`}>
      {medalOf(rank)?.emoji}
    </span>
  )
}

function useWeekTop(orgId: number) {
  const now = useNow(60_000)
  const ranking = useRanking(orgId, 'WEEK', periodRange('WEEK', kstDayKey(now)).from, true)
  return ranking.data?.entries.slice(0, TOP)
}

/** Figma 04-R 조직 홈(모바일): 오늘 투표 아래 이번 주 TOP3 띠와 「랭킹 보기 ›」 */
export function RankingHint({ orgId }: { orgId: number }) {
  const top = useWeekTop(orgId)
  if (!top) return null
  return (
    <section className="space-y-3 rounded-2xl border border-border-default bg-bg-surface p-3.5 lg:hidden" aria-label="이번 주 메뉴 메이커">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-bold">
          <span aria-hidden>🏆 </span>이번 주 메뉴 메이커
        </h2>
        <Link to={`/orgs/${orgId}/ranking`} className="shrink-0 text-xs font-medium text-text-brand hover:underline">
          랭킹 보기 ›
        </Link>
      </div>
      {top.length === 0 ? (
        <p className="text-xs text-text-tertiary">{EMPTY}</p>
      ) : (
        <ol className="grid grid-cols-3 gap-1.5">
          {top.map((entry) => (
            <li key={entry.user.userId} className="flex min-w-0 items-center gap-1 rounded-xl bg-bg-subtle px-1.5 py-2">
              <Medal rank={entry.rank} />
              {/* 390px보다 좁으면 세 글자 이름도 잘려서 사진을 뺀다. */}
              <span className="flex shrink-0 max-[389px]:hidden">
                <Avatar name={entry.user.name} imageUrl={entry.user.profileImageUrl} size="sm" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">{entry.user.name}</span>
                <span className="block text-xs text-text-tertiary">{entry.count}회</span>
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

/** Figma D04-R 조직 홈(데스크톱 사이드): 이번 주 TOP3와 대표 메뉴, 「랭킹 전체 보기 ›」 */
export function RankingCard({ orgId }: { orgId: number }) {
  const top = useWeekTop(orgId)
  if (!top) return null
  return (
    <Section title="이번 주 메뉴 메이커">
      {top.length === 0 ? (
        <p className="text-sm text-text-tertiary">{EMPTY}</p>
      ) : (
        <ol className="space-y-3">
          {top.map((entry) => (
            <TopRow key={entry.user.userId} entry={entry} />
          ))}
        </ol>
      )}
      <Link to={`/orgs/${orgId}/ranking`} className="mt-3 inline-block text-xs font-medium text-text-brand hover:underline">
        랭킹 전체 보기 ›
      </Link>
    </Section>
  )
}

function TopRow({ entry }: { entry: RankingEntry }) {
  return (
    <li className="flex items-center gap-2.5">
      <Medal rank={entry.rank} />
      <Avatar name={entry.user.name} imageUrl={entry.user.profileImageUrl} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium">{entry.user.name}</span>
        <span className="block truncate text-xs text-text-tertiary">대표 메뉴 {entry.topMenu}</span>
      </span>
      <span className="shrink-0 text-sm font-bold">{entry.count}회</span>
    </li>
  )
}
