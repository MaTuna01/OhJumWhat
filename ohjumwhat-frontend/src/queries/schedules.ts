import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'

export type Schedule = {
  id: number
  name: string
  /** 월=1 … 일=64 비트마스크 */
  daysOfWeek: number
  /** 한국 시간 "HH:mm" */
  openTime: string
  closeTime: string
}

export type ScheduleInput = Omit<Schedule, 'id'>

export const scheduleKeys = {
  list: (orgId: number) => ['orgs', orgId, 'schedules'] as const,
}

export function useSchedules(orgId: number) {
  return useQuery({
    queryKey: scheduleKeys.list(orgId),
    queryFn: () => api<Schedule[]>(`/api/orgs/${orgId}/schedules`),
    staleTime: 30_000,
  })
}

function useScheduleMutation<T>(orgId: number, request: (arg: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: scheduleKeys.list(orgId) })
      // 규칙을 추가·수정하면 스케줄러가 곧 오늘 투표를 열 수 있으므로 오늘 목록도 새로 불러온다.
      queryClient.invalidateQueries({ queryKey: ['orgs', orgId, 'polls', 'today'] })
    },
  })
}

export function useCreateSchedule(orgId: number) {
  return useScheduleMutation(orgId, (body: ScheduleInput) =>
    api<Schedule>(`/api/orgs/${orgId}/schedules`, { method: 'POST', body }),
  )
}

export function useUpdateSchedule(orgId: number) {
  return useScheduleMutation(orgId, ({ id, ...body }: Schedule) =>
    api<Schedule>(`/api/orgs/${orgId}/schedules/${id}`, { method: 'PUT', body }),
  )
}

export function useDeleteSchedule(orgId: number) {
  return useScheduleMutation(orgId, (id: number) => api<void>(`/api/orgs/${orgId}/schedules/${id}`, { method: 'DELETE' }))
}
