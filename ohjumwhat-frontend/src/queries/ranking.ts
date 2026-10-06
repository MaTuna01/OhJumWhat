import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef } from 'react'
import { useNow } from '../hooks/useNow.ts'
import { api } from '../lib/api.ts'
import { lastMonthDay, type RankingPeriod } from '../lib/ranking.ts'
import { kstDayKey } from '../lib/time.ts'
import type { Person, PollStatus } from './polls.ts'

/** 채택된 메뉴(이름별로 묶음). lastAdoptedOn은 마지막으로 채택된 투표 날짜(한국) */
export type AdoptedMenu = { name: string; count: number; lastAdoptedOn: string }

export type RankingEntry = {
  /** 같은 횟수는 같은 순위(1, 1, 3) */
  rank: number
  user: Person
  count: number
  /** 대표 채택 메뉴(menus의 첫 번째) */
  topMenu: string
  /** 횟수 많은 순(같으면 최근 순) */
  menus: AdoptedMenu[]
}

/** 메뉴 메이커 랭킹(GET /api/orgs/:orgId/ranking) */
export type Ranking = {
  period: RankingPeriod
  from: string
  to: string
  hasPrevious: boolean
  hasNext: boolean
  /** 기간 안에 마감된 투표 수 */
  closedPollCount: number
  /** 그중 채택된 메뉴가 있는 투표 수 */
  adoptedPollCount: number
  /** 1회 이상 채택된 지금 멤버(순위 순) */
  entries: RankingEntry[]
  /** 내 순위(이 기간에 채택이 없으면 rank는 null) */
  me: { rank: number | null; count: number }
}

export const rankingKeys = {
  all: (orgId: number) => ['orgs', orgId, 'ranking'] as const,
  period: (orgId: number, period: RankingPeriod, from: string) => ['orgs', orgId, 'ranking', period, from] as const,
}

/**
 * 기간의 첫날(from)로 구분한다. 진행 중인 투표가 마감되면 바뀌므로 1분 동안만 그대로 쓴다.
 * 지금 기간(current)은 날짜를 보내지 않고 서버의 오늘로 정한다. 기기 시계가 빨라 자정 직후에 「앞으로의 날짜」(400)를
 * 보내지 않게 하려는 것이다.
 */
export function useRanking(orgId: number, period: RankingPeriod, from: string, current = false) {
  return useQuery({
    queryKey: rankingKeys.period(orgId, period, from),
    queryFn: () => api<Ranking>(`/api/orgs/${orgId}/ranking?period=${period.toLowerCase()}${current ? '' : `&date=${from}`}`),
    staleTime: 60_000,
  })
}

/** 보고 있던 투표가 마감되면(지금 마감·마감 시각) 그 조직의 랭킹을 다시 받게 한다(방금 채택이 랭킹에 바로 보이게). */
export function useRefreshRankingOnClose(orgId: number, status: PollStatus | undefined) {
  const queryClient = useQueryClient()
  const previous = useRef(status)
  useEffect(() => {
    if (previous.current === 'OPEN' && status === 'CLOSED') queryClient.invalidateQueries({ queryKey: rankingKeys.all(orgId) })
    previous.current = status
  }, [orgId, status, queryClient])
}

/** 「이달의 메뉴 메이커」: 지난달 월간 1위(공동이면 모두)의 userId */
export function useMonthlyMakers(orgId: number): ReadonlySet<number> {
  // 달이 바뀌면 지난달도 바뀐다. 1분마다 오늘을 다시 본다.
  const now = useNow(60_000)
  const { data } = useRanking(orgId, 'MONTH', lastMonthDay(kstDayKey(now)))
  return useMemo(() => new Set(data?.entries.filter((e) => e.rank === 1).map((e) => e.user.userId)), [data])
}
