import { useState } from 'react'
import { PageLoader, PageMessage, Section } from '../components/PageState.tsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.ts'
import { useNow } from '../hooks/useNow.ts'
import { useOrgId } from '../hooks/useOrgId.ts'
import { formatEatenDay } from '../lib/time.ts'
import { columnsClass } from '../lib/ui.ts'
import { useOrganization } from '../queries/orgs.ts'
import { type MenuStat, useMenuRecommendations } from '../queries/polls.ts'
import { useMenuStats } from '../queries/stats.ts'

const periods = [
  { days: 30, label: '최근 30일' },
  { days: null, label: '전체' },
] as const

/**
 * 조직 통계(Figma 08·D08). 마감된 투표에서 참여자가 있었던 메뉴를 "먹은 메뉴"로 센다.
 * 데스크톱은 순위를 본문에, 요약·추천을 오른쪽 사이드에 둔다.
 */
export default function OrgStatsPage() {
  const orgId = useOrgId()
  const { data: org } = useOrganization(orgId)
  useDocumentTitle('통계', org?.name)
  const [days, setDays] = useState<number | null>(30)
  const stats = useMenuStats(orgId, days)
  const recommendations = useMenuRecommendations(orgId, true)
  const now = useNow(60_000)

  return (
    <div className="space-y-4">
      <div className="flex gap-2" role="group" aria-label="기간">
        {periods.map((p) => {
          const active = p.days === days
          return (
            <button
              key={p.label}
              type="button"
              aria-pressed={active}
              onClick={() => setDays(p.days)}
              className={`rounded-full border px-3 py-1.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-brand ${
                active ? 'border-border-brand bg-bg-brand-soft text-text-brand' : 'border-border-strong bg-bg-surface text-text-secondary hover:bg-bg-subtle'
              }`}
            >
              {p.label}
            </button>
          )
        })}
      </div>

      {stats.isError ? (
        <PageMessage title="통계를 불러오지 못했어요">{stats.error.message}</PageMessage>
      ) : stats.isPending ? (
        <PageLoader />
      ) : (
        <div className={`flex flex-col gap-4 lg:grid-rows-[auto_auto_1fr] ${columnsClass}`}>
          <div className="grid grid-cols-2 gap-3 lg:col-start-2 lg:row-start-1">
            <Stat label="마감된 투표" value={`${stats.data.pollCount}번`} />
            <Stat label="먹은 메뉴" value={`${stats.data.menus.length}가지`} />
          </div>

          <Section title="자주 먹은 메뉴" className="lg:col-start-1 lg:row-span-3 lg:row-start-1">
            {stats.data.menus.length === 0 ? (
              <p className="py-6 text-center text-sm text-text-tertiary">
                {days == null ? '아직 먹은 메뉴가 없어요.' : `최근 ${days}일 동안 먹은 메뉴가 없어요.`}
                <br />
                투표가 마감되면 참여한 메뉴가 여기에 쌓여요.
              </p>
            ) : (
              <ol className="space-y-4">
                {stats.data.menus.map((menu, i) => (
                  <RankRow key={menu.name} rank={i + 1} menu={menu} max={stats.data.menus[0].times} now={now} />
                ))}
              </ol>
            )}
          </Section>

          {recommendations.data && recommendations.data.length > 0 && (
            <section className="space-y-3 rounded-2xl bg-bg-brand-soft p-4 lg:col-start-2 lg:row-start-2" aria-label="추천 메뉴">
              <div>
                <h2 className="text-sm font-bold">오늘은 이거 어때요?</h2>
                <p className="text-xs text-text-secondary">자주 먹었지만 최근 7일 동안 안 먹은 메뉴예요</p>
              </div>
              <ul className="flex flex-wrap gap-1.5">
                {recommendations.data.slice(0, 6).map((m) => (
                  <li key={m.name} className="flex items-baseline gap-1 rounded-full border border-border-brand bg-bg-surface px-2.5 py-1">
                    <span className="text-sm font-medium">{m.name}</span>
                    <span className="text-xs text-text-tertiary">{m.times}번</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <p className="text-xs text-text-placeholder lg:col-start-2 lg:row-start-3">
            마감된 투표에서 참여자가 있었던 메뉴를 세요. 띄어쓰기가 달라도 같은 메뉴로 묶어요.
          </p>
        </div>
      )}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-border-default bg-bg-surface p-4">
      <p className="text-xs text-text-tertiary">{label}</p>
      <p className="mt-1 text-2xl font-bold tracking-tight">{value}</p>
    </div>
  )
}

function RankRow({ rank, menu, max, now }: { rank: number; menu: MenuStat; max: number; now: number }) {
  return (
    <li className="flex gap-3">
      <span className={`w-4 shrink-0 text-center text-sm leading-6 font-bold ${rank === 1 ? 'text-text-brand' : 'text-text-tertiary'}`}>{rank}</span>
      <div className="min-w-0 flex-1 space-y-1.5">
        <p className="flex items-baseline gap-2">
          <span className="truncate font-bold">{menu.name}</span>
          <span className="shrink-0 text-sm font-bold text-text-brand">{menu.times}번</span>
        </p>
        <p className="text-xs text-text-tertiary">
          연인원 {menu.people}명 · 마지막 {formatEatenDay(menu.lastEatenOn, now)}
        </p>
        <div className="h-1.5 overflow-hidden rounded-full bg-bg-muted" aria-hidden>
          <div className="h-full rounded-full bg-bg-brand" style={{ width: `${(menu.times / max) * 100}%` }} />
        </div>
      </div>
    </li>
  )
}
