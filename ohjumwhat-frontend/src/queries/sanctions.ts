import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import type { SanctionInput, SanctionNotice, SanctionRow } from '../lib/sanctions.ts'
import { useAdminMutation } from './admin.ts'

/** 아직 확인하지 않은 안내. (신고 결과가 더해지면 키가 늘어난다. 없는 키는 빈 배열로 본다) */
export type SanctionAlerts = {
  sanctions?: SanctionNotice[]
}

export const sanctionKeys = {
  alerts: ['sanctions', 'alerts'] as const,
  /** 관리자 「제재」 탭: active = 진행 중만, all = 최근 100건 */
  admin: (status: 'active' | 'all') => ['admin', 'sanctions', status] as const,
  /** 관리자 회원 상세의 제재 이력 */
  user: (userId: number) => ['admin', 'user-sanctions', userId] as const,
}

/**
 * 아직 확인하지 않은 제재 안내(AppNoticeDialogs가 띄운다). 실시간 연결이 없어서 60초마다, 그리고 창에 다시 들어오면 새로 받는다.
 * 제재 푸시가 오거나 막힌 요청이 423을 받으면 바로 다시 받는다(lib/push.ts, main.tsx).
 */
export function useSanctionAlerts() {
  return useQuery({
    queryKey: sanctionKeys.alerts,
    queryFn: () => api<SanctionAlerts>('/api/sanctions/alerts'),
    refetchInterval: 60_000,
    staleTime: 30_000,
  })
}

/** 안내를 확인했다. 다시 뜨지 않게 캐시에서 바로 지우고, 끝나면 서버 값으로 맞춘다. */
export function useMarkSanctionsSeen() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => api<void>('/api/sanctions/seen', { method: 'POST', body: { ids } }),
    onMutate: async (ids) => {
      await queryClient.cancelQueries({ queryKey: sanctionKeys.alerts })
      queryClient.setQueryData<SanctionAlerts>(sanctionKeys.alerts, (data) =>
        data ? { ...data, sanctions: (data.sanctions ?? []).filter((s) => !ids.includes(s.id)) } : data,
      )
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: sanctionKeys.alerts }),
  })
}

/** 관리자 「제재」 탭(최신순 최대 100건) */
export function useAdminSanctions(status: 'active' | 'all') {
  return useQuery({
    queryKey: sanctionKeys.admin(status),
    queryFn: () => api<SanctionRow[]>(`/api/admin/sanctions?status=${status}`),
    placeholderData: keepPreviousData,
  })
}

/** 관리자 회원 상세의 제재 이력(최신순) */
export function useUserSanctions(userId: number) {
  return useQuery({
    queryKey: sanctionKeys.user(userId),
    queryFn: () => api<SanctionRow[]>(`/api/admin/users/${userId}/sanctions`),
    enabled: Number.isInteger(userId),
  })
}

/** 제재하기(제한·초기화·경고). 본인에게 안내 창과 푸시가 간다. */
export const useApplySanction = (userId: number) =>
  useAdminMutation((input: SanctionInput) => api<SanctionRow>(`/api/admin/users/${userId}/sanctions`, { method: 'POST', body: input }))

/** 진행 중인 제재 해제. 함께 한 프로필 초기화는 되돌리지 않고, 본인에게 따로 알리지 않는다. */
export const useLiftSanction = () =>
  useAdminMutation((sanctionId: number) => api<SanctionRow>(`/api/admin/sanctions/${sanctionId}/lift`, { method: 'POST' }))
