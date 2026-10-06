import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import AdoptionModal from '../components/AdoptionModal.tsx'
import MyRankCard from '../components/MyRankCard.tsx'
import { PageLoader, PageMessage, Section } from '../components/PageState.tsx'
import RankingList from '../components/RankingList.tsx'
import RankingPeriodSwitcher from '../components/RankingPeriodSwitcher.tsx'
import RankingPodium from '../components/RankingPodium.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useNow } from '../hooks/useNow.ts'
import { useOrgId } from '../hooks/useOrgId.ts'
import { withJosa } from '../lib/josa.ts'
import { addDays, hasNext, hasPrevious, type PeriodLabel, parseRankingView, periodLabel, periodRange, type RankingPeriod, rankingSearch } from '../lib/ranking.ts'
import { kstDayKey } from '../lib/time.ts'
import { buttonClass, columnsClass } from '../lib/ui.ts'
import { useMe } from '../queries/me.ts'
import { useOrganization } from '../queries/orgs.ts'
import { type Ranking, type RankingEntry, useRanking } from '../queries/ranking.ts'

const RULES = [
  '투표가 마감되면 메뉴를 고른 사람이 가장 많은 메뉴를 제안한 사람이 1회 채택돼요.',
  '동점이면 먼저 제안한 메뉴예요.',
  '메뉴를 고른 사람이 조직 인원의 30%보다 적으면 세지 않아요.',
  '같은 횟수는 같은 순위예요.',
  '지난달 1위는 이번 달 동안 🏅 이달의 메뉴 메이커 배지를 달아요.',
]

/**
 * 메뉴 메이커 랭킹(Figma 11·11b·11c·D11). 주간/월간과 ‹ 기간 ›(12개월 전까지)은 주소(?period=month&date=)에 둔다.
 * 모바일은 내 순위를 화면 아래에 띄우고, 데스크톱은 사이드 맨 위에 둔다.
 */
export default function OrgRankingPage() {
  const orgId = useOrgId()
  const { data: org } = useOrganization(orgId)
  useDocumentTitle('랭킹', org?.name)
  const { data: me } = useMe()
  const now = useNow(60_000)
  const today = kstDayKey(now)
  const [params, setParams] = useSearchParams()
  const [selected, setSelected] = useState<RankingEntry | null>(null)

  const { period, range: shown, current } = parseRankingView(params.get('period'), params.get('date'), today)
  const label = periodLabel(period, shown, today)
  const ranking = useRanking(orgId, period, shown.from, current)

  // 주간/월간을 바꾸면 보고 있던 기간의 첫날이 들어 있는 주·달로 간다.
  const go = (nextPeriod: RankingPeriod, day: string) => setParams(rankingSearch(nextPeriod, day, today), { replace: true })

  return (
    <div className="space-y-5 pb-32 lg:pb-0">
      <RankingPeriodSwitcher
        className="lg:max-w-[22.375rem]"
        period={period}
        label={label}
        canPrevious={hasPrevious(shown, today)}
        canNext={hasNext(shown, today)}
        onPeriodChange={(p) => p !== period && go(p, shown.from)}
        onPrevious={() => go(period, addDays(shown.from, -1))}
        onNext={() => go(period, addDays(shown.to, 1))}
      />

      {ranking.isError ? (
        <PageMessage title="랭킹을 불러오지 못했어요">{ranking.error.message}</PageMessage>
      ) : ranking.isPending ? (
        <PageLoader />
      ) : (
        <div className={`flex flex-col gap-4 ${columnsClass}`}>
          <div className="min-w-0 space-y-4">
            {ranking.data.entries.length === 0 ? (
              <EmptyRanking orgId={orgId} label={label} adoptedPollCount={ranking.data.adoptedPollCount} />
            ) : (
              <>
                <RankingPodium
                  title={label.cardTitle}
                  closedPollCount={ranking.data.closedPollCount}
                  adoptedPollCount={ranking.data.adoptedPollCount}
                  entries={ranking.data.entries}
                  onSelect={setSelected}
                  notice={period === 'MONTH' && <MakerNotice ranking={ranking.data} today={today} />}
                />
                <RankingList entries={ranking.data.entries.slice(3)} meId={me?.id} onSelect={setSelected} />
              </>
            )}
            <p className="text-xs text-text-placeholder lg:hidden">
              투표가 마감되면 메뉴를 고른 사람이 가장 많은 메뉴(동점이면 먼저 제안한 메뉴)를 제안한 사람이 1회 채택돼요. 메뉴를 고른 사람이 조직
              인원의 30%보다 적은 투표는 세지 않아요. 같은 횟수는 같은 순위예요.
            </p>
          </div>

          <aside className="hidden space-y-4 lg:block">
            {me && <MyRankCard ranking={ranking.data} periodName={label.name} me={me} />}
            <Section title="이렇게 세요">
              <ul className="space-y-2 text-sm text-text-secondary">
                {RULES.map((rule) => (
                  <li key={rule} className="flex gap-1.5">
                    <span aria-hidden>·</span>
                    <span>{rule}</span>
                  </li>
                ))}
              </ul>
            </Section>
          </aside>

          {me && (
            <MyRankCard
              ranking={ranking.data}
              periodName={label.name}
              me={me}
              className="fixed inset-x-4 bottom-4 z-10 mx-auto max-w-[46rem] lg:hidden"
            />
          )}
        </div>
      )}

      <AdoptionModal entry={selected} label={label} today={today} onClose={() => setSelected(null)} />
    </div>
  )
}

/** Figma 11c: 이 기간에 채택된 메뉴가 없을 때 */
function EmptyRanking({ orgId, label, adoptedPollCount }: { orgId: number; label: PeriodLabel; adoptedPollCount: number }) {
  // 채택은 있었지만 제안한 사람이 모두 조직을 떠났거나 탈퇴했으면 순위가 비어 있다.
  if (adoptedPollCount > 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border-strong bg-bg-surface px-4 py-10 text-center">
        <span aria-hidden className="text-3xl">
          🏆
        </span>
        <p className="font-bold">{withJosa(label.name, '은/는')} 순위에 오른 멤버가 없어요</p>
        <p className="text-sm text-text-secondary">채택된 메뉴 {adoptedPollCount}번을 제안한 사람이 모두 조직을 떠났어요.</p>
      </div>
    )
  }
  return (
    <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-border-strong bg-bg-surface px-4 py-10 text-center">
      <span aria-hidden className="text-3xl">
        🏆
      </span>
      <p className="font-bold">{withJosa(label.name, '은/는')} 아직 채택된 메뉴가 없어요</p>
      <p className="text-sm text-text-secondary">
        투표가 마감되면 가장 많은 사람이 고른 메뉴를 제안한 사람이 채택돼요. 오늘 투표에 먹고 싶은 메뉴를 먼저 올려 보세요.
      </p>
      <Link to={`/orgs/${orgId}`} className={buttonClass('secondary', 'mt-2')}>
        오늘 투표 보기
      </Link>
    </div>
  )
}

/**
 * Figma 11b: 월간 1위(공동이면 모두)가 「이달의 메뉴 메이커」. 지난달이면 이번 달 동안 배지가 붙는다고 알려주고,
 * 그보다 앞의 달은 누가 메이커였는지만 알려준다. 이번 달은 아직 정해지지 않아 보여주지 않는다.
 */
function MakerNotice({ ranking, today }: { ranking: Ranking; today: string }) {
  const makers = ranking.entries.filter((e) => e.rank === 1).map((e) => e.user.name)
  const thisMonth = periodRange('MONTH', today)
  if (makers.length === 0 || ranking.to >= thisMonth.from) return null
  const month = `${Number(ranking.from.slice(5, 7))}월`
  const lastMonth = addDays(thisMonth.from, -1) === ranking.to
  return (
    <p className="rounded-xl bg-bg-warning-soft px-3 py-2.5 text-xs text-text-warning">
      <span aria-hidden>🏅 </span>
      {makers.join(', ')} 님이 {month}의 메뉴 메이커{lastMonth ? '예요' : '였어요'}.
      {lastMonth && ` ${Number(today.slice(5, 7))}월 한 달 동안 프로필과 멤버 목록에 「이달의 메뉴 메이커」 배지가 붙어요.`}
    </p>
  )
}
