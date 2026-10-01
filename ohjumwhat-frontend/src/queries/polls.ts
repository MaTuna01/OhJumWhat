import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api.ts'
import { applyVote } from '../lib/pollDetail.ts'
import type { Me } from './me.ts'
import { orgKeys } from './orgs.ts'

export type PollStatus = 'OPEN' | 'CLOSED'
export type MyResponse = 'NONE' | 'PASS' | 'OPTION'

export type Person = { userId: number; name: string; profileImageUrl: string | null }

export type PollOption = {
  id: number
  name: string
  /** 식당 지도 링크(http/https). 없으면 null */
  link: string | null
  /** 추가한 사람. 강제 탈퇴로 삭제된 회원이면 null("탈퇴한 사용자") */
  createdBy: Person | null
  voters: Person[]
  mine: boolean
  deletable: boolean
}

export type PollDetail = {
  id: number
  organizationId: number
  title: string
  status: PollStatus
  opensAt: string
  closesAt: string
  scheduled: boolean
  memberCount: number
  options: PollOption[]
  myResponse: MyResponse
  myOptionId: number | null
  passed: Person[]
  nonRespondents: Person[]
  soloOptionIds: number[]
}

export type PollSummary = {
  id: number
  title: string
  status: PollStatus
  closesAt: string
  memberCount: number
  respondedCount: number
  passCount: number
  optionCount: number
  teamCount: number
  myResponse: MyResponse
  myOptionName: string | null
}

/** 지난 투표 한 줄. teams는 참여자가 있는 메뉴(인원 많은 순) */
export type PollHistoryItem = {
  id: number
  title: string
  pollDate: string
  closesAt: string
  respondedCount: number
  passCount: number
  teams: { name: string; count: number }[]
  myResponse: MyResponse
  myOptionName: string | null
}

export type PollHistoryPage = { polls: PollHistoryItem[]; hasMore: boolean }

/** 메뉴 자동완성. lastEatenOn은 마지막으로 먹은 날(한국 날짜), 먹은 적이 없으면 null */
export type MenuSuggestion = { name: string; lastEatenOn: string | null }

/** 먹은 메뉴(통계·추천). times = 먹은 투표 수, people = 참여 인원 합계 */
export type MenuStat = { name: string; times: number; people: number; lastEatenOn: string }

export const pollKeys = {
  today: (orgId: number) => ['orgs', orgId, 'polls', 'today'] as const,
  detail: (pollId: number) => ['polls', pollId] as const,
  menuNames: (orgId: number, q: string) => ['orgs', orgId, 'menu-names', q] as const,
  recommendations: (orgId: number) => ['orgs', orgId, 'menu-recommendations'] as const,
  history: (orgId: number) => ['orgs', orgId, 'polls', 'history'] as const,
}

/** 진행 중인 투표는 3초마다 다시 불러온다(백그라운드 탭에서는 멈춘다). */
const POLL_INTERVAL = 3000

export function useTodayPolls(orgId: number) {
  return useQuery({
    queryKey: pollKeys.today(orgId),
    queryFn: () => api<PollSummary[]>(`/api/orgs/${orgId}/polls/today`),
    refetchInterval: (query) => (query.state.data?.some((p) => p.status === 'OPEN') ? 15_000 : false),
  })
}

/** 지난 투표(오늘 이전, 최신순 10개씩). 「더 보기」로 다음 페이지를 붙인다. */
export function usePollHistory(orgId: number) {
  return useInfiniteQuery({
    queryKey: pollKeys.history(orgId),
    queryFn: ({ pageParam }) => api<PollHistoryPage>(`/api/orgs/${orgId}/polls/history?page=${pageParam}`),
    initialPageParam: 0,
    getNextPageParam: (last, pages) => (last.hasMore ? pages.length : undefined),
    staleTime: 60_000,
  })
}

export function usePollDetail(orgId: number, pollId: number) {
  return useQuery({
    queryKey: pollKeys.detail(pollId),
    queryFn: () => api<PollDetail>(`/api/orgs/${orgId}/polls/${pollId}`),
    enabled: Number.isInteger(orgId) && Number.isInteger(pollId),
    // 다른 멤버가 투표를 삭제하면 404가 오므로 그때는 폴링을 멈춘다.
    refetchInterval: (query) =>
      query.state.data?.status === 'OPEN' && !isNotFound(query.state.error) ? POLL_INTERVAL : false,
    refetchIntervalInBackground: false,
  })
}

function isNotFound(error: unknown) {
  return error instanceof ApiError && error.status === 404
}

export function useMenuNames(orgId: number, q: string, enabled: boolean) {
  return useQuery({
    queryKey: pollKeys.menuNames(orgId, q),
    queryFn: () => api<MenuSuggestion[]>(`/api/orgs/${orgId}/menu-names?q=${encodeURIComponent(q)}`),
    enabled,
    staleTime: 30_000,
  })
}

/** 오늘은 이거 어때요? 자주 먹었지만 최근 7일 안에는 먹지 않은 메뉴 */
export function useMenuRecommendations(orgId: number, enabled: boolean) {
  return useQuery({
    queryKey: pollKeys.recommendations(orgId),
    queryFn: () => api<MenuStat[]>(`/api/orgs/${orgId}/menu-recommendations`),
    enabled,
    staleTime: 60_000,
  })
}

export function useCreatePoll(orgId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { title: string; closesAt: string }) =>
      api<PollDetail>(`/api/orgs/${orgId}/polls`, { method: 'POST', body }),
    onSuccess: (poll) => {
      queryClient.setQueryData(pollKeys.detail(poll.id), poll)
      queryClient.invalidateQueries({ queryKey: pollKeys.today(orgId) })
      queryClient.invalidateQueries({ queryKey: orgKeys.mine })
    },
  })
}

/**
 * 메뉴 추가·삭제·참여, 투표 수정·마감은 모두 최신 투표 상세를 돌려주므로 바로 캐시에 넣는다.
 * 마감처럼 마이페이지의 "진행 중인 투표" 표시가 바뀌는 작업은 refreshMyOrgs로 내 조직 목록도 다시 불러온다.
 */
function usePollMutation<T>(pollId: number, orgId: number, request: (arg: T) => Promise<PollDetail>, refreshMyOrgs = false) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (detail) => {
      queryClient.setQueryData(pollKeys.detail(pollId), detail)
      queryClient.invalidateQueries({ queryKey: pollKeys.today(orgId) })
      if (refreshMyOrgs) queryClient.invalidateQueries({ queryKey: orgKeys.mine })
    },
  })
}

export function useUpdatePoll(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, (body: { title: string; closesAt: string }) =>
    api<PollDetail>(`/api/polls/${pollId}`, { method: 'PUT', body }),
  )
}

/** 조기 마감: 응답으로 마감된 상세가 오면 화면이 바로 결과 모드로 바뀐다. */
export function useClosePoll(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, () => api<PollDetail>(`/api/polls/${pollId}/close`, { method: 'POST' }), true)
}

export function useDeletePoll(orgId: number, pollId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(`/api/polls/${pollId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: pollKeys.today(orgId) })
      queryClient.invalidateQueries({ queryKey: orgKeys.mine })
    },
  })
}

export function useAddOption(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, (body: { name: string; link: string | null }) =>
    api<PollDetail>(`/api/polls/${pollId}/options`, { method: 'POST', body }),
  )
}

/** 식당 지도 링크 달기·고치기. link가 null이면 지운다. */
export function useChangeLink(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, ({ optionId, link }: { optionId: number; link: string | null }) =>
    api<PollDetail>(`/api/polls/${pollId}/options/${optionId}/link`, { method: 'PUT', body: { link } }),
  )
}

export function useDeleteOption(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, (optionId: number) =>
    api<PollDetail>(`/api/polls/${pollId}/options/${optionId}`, { method: 'DELETE' }),
  )
}

/** 참여·패스는 누르는 즉시 화면에 반영하고(낙관적 업데이트), 실패하면 되돌린다. */
export function useVote(orgId: number, pollId: number, me: Me | undefined) {
  const queryClient = useQueryClient()
  const key = pollKeys.detail(pollId)
  return useMutation({
    mutationFn: (optionId: number | null) =>
      api<PollDetail>(`/api/polls/${pollId}/vote`, { method: 'PUT', body: { optionId } }),
    onMutate: async (optionId) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<PollDetail>(key)
      if (previous && me) {
        queryClient.setQueryData(key, applyVote(previous, me, optionId))
      }
      return { previous }
    },
    onError: (_error, _optionId, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous)
    },
    onSuccess: (detail) => {
      queryClient.setQueryData(key, detail)
      queryClient.invalidateQueries({ queryKey: pollKeys.today(orgId) })
    },
  })
}
