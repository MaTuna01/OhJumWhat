import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from '../lib/api.ts'
import type { PlaceInput } from '../lib/place.ts'
import { applyVote, type VoteChoice } from '../lib/pollDetail.ts'
import type { Me } from './me.ts'
import { orgKeys } from './orgs.ts'

export type PollStatus = 'OPEN' | 'CLOSED'
export type MyResponse = 'NONE' | 'PASS' | 'OPTION'

export type Person = { userId: number; name: string; profileImageUrl: string | null }

export type PollOption = {
  id: number
  name: string
  /** 식당 지도 링크(http/https, 네이버 장소면 정식 링크). 없으면 null */
  link: string | null
  /** 식당 이름(링크가 있을 때만). 없으면 null */
  placeName: string | null
  /** 식당 주소(링크가 있을 때만, 공유 글의 주소 줄). 지도 위치는 usePlaces로 따로 받는다. 없으면 null */
  placeAddress: string | null
  /** 근처 식당 찾기로 고른 카카오 장소 ID(이때 link는 카카오 장소 링크, 이름·위치는 usePlaces로 받는다). 없으면 null */
  kakaoPlaceId: string | null
  /** 카카오 식당을 찾을 때 친 검색어. 없으면 null(둘러보기) */
  placeQuery: string | null
  /** 추가한 사람. 강제 탈퇴로 삭제된 회원이면 null("탈퇴한 사용자") */
  createdBy: Person | null
  voters: Person[]
  mine: boolean
  deletable: boolean
  /** 댓글 수(진행 중에는 폴링으로 갱신). 목록은 queries/comments.ts로 펼칠 때 받는다 */
  commentCount: number
}

export type PollDetail = {
  id: number
  organizationId: number
  title: string
  status: PollStatus
  opensAt: string
  closesAt: string
  /** 채팅이 닫히는 시각(마감 1시간 뒤). 그 뒤에는 읽기만 한다 */
  chatClosesAt: string
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

/** 같은 이름의 메뉴에 지난번 붙인 식당(카카오 식당의 이름은 optionId로 usePlaces에서 받는다) */
export type LastPlace = {
  optionId: number
  link: string
  placeName: string | null
  placeAddress: string | null
  kakaoPlaceId: string | null
  placeQuery: string | null
}

/** 메뉴 자동완성. lastEatenOn은 마지막으로 먹은 날(한국 날짜), 먹은 적이 없으면 null */
export type MenuSuggestion = { name: string; lastEatenOn: string | null; lastPlace: LastPlace | null }

/** 먹은 메뉴(통계·추천). times = 먹은 투표 수, people = 참여 인원 합계 */
export type MenuStat = { name: string; times: number; people: number; lastEatenOn: string }

/** 「오늘은 이거 어때요?」 추천: 먹은 메뉴 + 지난번 붙인 식당 */
export type MenuRecommendation = MenuStat & { lastPlace: LastPlace | null }

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
    queryFn: () => api<MenuRecommendation[]>(`/api/orgs/${orgId}/menu-recommendations`),
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

/** 메뉴 추가. 식당(링크·이름·주소 또는 카카오 식당)은 선택이고, naver.me 공유 링크는 서버가 장소 정식 링크로 바꾼다. */
export function useAddOption(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, (body: { name: string } & PlaceInput) =>
    api<PollDetail>(`/api/polls/${pollId}/options`, { method: 'POST', body }),
  )
}

/** 식당 달기·고치기. link와 kakaoPlaceId가 모두 null이면 식당을 뺀다. */
export function useChangePlace(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, ({ optionId, ...place }: { optionId: number } & PlaceInput) =>
    api<PollDetail>(`/api/polls/${pollId}/options/${optionId}/link`, { method: 'PUT', body: place }),
  )
}

export function useDeleteOption(orgId: number, pollId: number) {
  return usePollMutation(pollId, orgId, (optionId: number) =>
    api<PollDetail>(`/api/polls/${pollId}/options/${optionId}`, { method: 'DELETE' }),
  )
}

/**
 * 참여·패스·취소(미응답으로)는 누르는 즉시 화면에 반영하고(낙관적 업데이트), 실패하면 되돌린다.
 * 같은 투표의 요청은 scope로 묶어 보낸 순서대로 처리한다(빠르게 두 번 누르면 참여 → 취소 순서가 지켜진다).
 */
export function useVote(orgId: number, pollId: number, me: Me | undefined) {
  const queryClient = useQueryClient()
  const key = pollKeys.detail(pollId)
  const mutationKey = ['polls', pollId, 'vote']
  return useMutation({
    mutationKey,
    scope: { id: `vote-${pollId}` },
    mutationFn: (choice: VoteChoice) =>
      choice === 'NONE'
        ? api<PollDetail>(`/api/polls/${pollId}/vote`, { method: 'DELETE' })
        : api<PollDetail>(`/api/polls/${pollId}/vote`, { method: 'PUT', body: { optionId: choice === 'PASS' ? null : choice } }),
    onMutate: async (choice) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<PollDetail>(key)
      if (previous && me) {
        queryClient.setQueryData(key, applyVote(previous, me, choice))
      }
      return { previous }
    },
    onError: (_error, _choice, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous)
    },
    onSuccess: (detail) => {
      // 뒤에 기다리는 요청이 있으면 그 요청의 낙관적 화면을 덮지 않는다(마지막 응답이 넣는다).
      if (queryClient.isMutating({ mutationKey }) <= 1) queryClient.setQueryData(key, detail)
      queryClient.invalidateQueries({ queryKey: pollKeys.today(orgId) })
    },
  })
}
