import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import type { MenuStat } from './polls.ts'

/** 조직 통계(GET /api/orgs/:orgId/menu-stats). days가 null이면 전체 기간 */
export type MenuStats = {
  days: number | null
  from: string | null
  /** 기간 안에 마감된 투표 수 */
  pollCount: number
  /** 먹은 메뉴(많이 먹은 순, 최대 30개) */
  menus: MenuStat[]
}

export function useMenuStats(orgId: number, days: number | null) {
  return useQuery({
    queryKey: ['orgs', orgId, 'menu-stats', days] as const,
    queryFn: () => api<MenuStats>(`/api/orgs/${orgId}/menu-stats${days == null ? '' : `?days=${days}`}`),
    staleTime: 60_000,
  })
}
