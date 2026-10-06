import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { startGuestbookCooldown } from '../hooks/useGuestbookCooldown.ts'
import { ApiError, api } from '../lib/api.ts'
import { reportReason } from '../lib/letters.ts'
import type { Person } from './polls.ts'

/** 방명록 글 한 줄(보는 사람 기준). 서버 GuestbookEntryResponse */
export type GuestbookEntry = {
  id: number
  /** 쓴 사람. 강제 탈퇴했으면 null(「탈퇴한 사용자」) */
  author: Person | null
  /** 본문. 관리자가 제한한 글이면 null(누구에게도 보내지 않는다) */
  body: string | null
  createdAt: string
  /** 관리자가 제한한 글(「관리자에 의해 제한된 게시글입니다」) */
  restricted: boolean
  /** 내가 쓴 글 */
  mine: boolean
  /** 지울 수 있다(쓴 사람 또는 주인) */
  canDelete: boolean
  /** 신고할 수 있다(주인이고, 아직 신고하지 않았고, 제한되지 않은 글) */
  canReport: boolean
  /** 주인이 이미 신고했다(「신고함」). 주인이 아니면 언제나 false */
  reported: boolean
}

/** 방명록 한 쪽(최신순 10개). 쪽이 끝을 넘으면 entries만 비고 totalPages·totalCount는 실제 값이다. */
export type GuestbookPage = {
  entries: GuestbookEntry[]
  page: number
  /** 글이 없으면 0 */
  totalPages: number
  totalCount: number
  /** 보는 사람이 방명록 주인인지(주인은 자기 방명록에 쓸 수 없다) */
  owner: boolean
  /** 주인일 때만, 마지막으로 본 시각(없으면 null = 모두 새 글). 주인이 아니면 null */
  seenAt: string | null
}

/** 관리자가 제한한 내 글(경고 안내). body는 쓴 사람 본인에게만 보여주는 원문이다. */
export type GuestbookWarning = {
  entryId: number
  /** 그 글이 있는 방명록의 주인(강제 탈퇴했으면 null) */
  owner: Person | null
  body: string
  writtenAt: string
  restrictedAt: string
}

export type GuestbookAlerts = {
  /** 내 방명록에서 아직 보지 않은 글 수(상단 바 점, 프로필 메뉴 배지) */
  newEntryCount: number
  /** 아직 확인하지 않은 경고(제한된 순서) */
  warnings: GuestbookWarning[]
}

export const guestbookKeys = {
  all: ['guestbook'] as const,
  owner: (ownerId: number) => ['guestbook', 'owner', ownerId] as const,
  page: (ownerId: number, page: number) => ['guestbook', 'owner', ownerId, page] as const,
  alerts: ['guestbook', 'alerts'] as const,
}

/**
 * 한 사람의 방명록 한 쪽. 쪽을 넘기는 동안에는 앞 쪽을 그대로 보여준다(깜빡이지 않게).
 * 다만 다른 사람의 방명록으로 바뀌면 앞 사람의 글이 비치지 않게 이전 값을 쓰지 않는다.
 */
export function useGuestbook(ownerId: number, page: number, enabled = true) {
  return useQuery({
    queryKey: guestbookKeys.page(ownerId, page),
    queryFn: () => api<GuestbookPage>(`/api/guestbook/users/${ownerId}?page=${page}`),
    enabled,
    placeholderData: (previous, previousQuery) => (previousQuery?.queryKey[2] === ownerId ? previous : undefined),
  })
}

/**
 * 남기기. 응답(내가 보는 0쪽)을 바로 0쪽에 넣고 나머지 쪽은 다시 받는다(한 칸씩 밀린다).
 * 성공하거나 서버가 도배로 거절하면(429) 앱 전체의 5초 대기를 시작한다.
 */
export function useWriteGuestbook(ownerId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => api<GuestbookPage>(`/api/guestbook/users/${ownerId}`, { method: 'POST', body: { body } }),
    onSuccess: (page) => {
      startGuestbookCooldown()
      queryClient.setQueryData(guestbookKeys.page(ownerId, 0), page)
      queryClient.invalidateQueries({ queryKey: guestbookKeys.owner(ownerId), predicate: (query) => query.queryKey[3] !== 0 })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 429) startGuestbookCooldown()
    },
  })
}

/** 지우기(쓴 사람 또는 주인). 그 사람의 방명록과 새 글 수를 다시 받는다. */
export function useDeleteGuestbookEntry(ownerId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (entryId: number) => api<void>(`/api/guestbook/entries/${entryId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: guestbookKeys.owner(ownerId) })
      queryClient.invalidateQueries({ queryKey: guestbookKeys.alerts })
    },
    // 다른 사람이 먼저 지운 글(404)이면 목록을 다시 받아 그 글을 없앤다.
    onError: (error) => {
      if (error instanceof ApiError && error.status === 404) queryClient.invalidateQueries({ queryKey: guestbookKeys.owner(ownerId) })
    },
  })
}

/** 신고(주인만, 사유는 선택). 신고한 글은 「신고함」으로 바뀐다. */
export function useReportGuestbookEntry(ownerId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ entryId, reason }: { entryId: number; reason: string }) =>
      api<void>(`/api/guestbook/entries/${entryId}/report`, { method: 'POST', body: { reason: reportReason(reason) } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: guestbookKeys.owner(ownerId) })
      queryClient.invalidateQueries({ queryKey: guestbookKeys.alerts })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 404) queryClient.invalidateQueries({ queryKey: guestbookKeys.owner(ownerId) })
    },
  })
}

/**
 * 새 방명록 수와 글 제한 경고. 실시간 연결이 없어서 60초마다, 그리고 창에 다시 들어오면 새로 받는다
 * (숨긴 탭에서는 쉰다).
 */
export function useGuestbookAlerts() {
  return useQuery({
    queryKey: guestbookKeys.alerts,
    queryFn: () => api<GuestbookAlerts>('/api/guestbook/alerts'),
    refetchInterval: 60_000,
    staleTime: 30_000,
  })
}

/**
 * 내 방명록을 봤다(until = 화면에 보인 가장 최근 글의 시각). 점·배지는 응답을 기다리지 않고 바로 없애고,
 * 받아 둔 내 방명록의 seenAt도 맞춰서 다음에 열 때 이미 본 글이 NEW로 보이지 않게 한다.
 */
export function useMarkGuestbookSeen() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (until: string) => api<void>('/api/guestbook/seen', { method: 'POST', body: { until } }),
    onMutate: async (until) => {
      // 이미 나가 있던 요청의 늦은 응답이 점을 되살리지 않게 먼저 멈춘다.
      await queryClient.cancelQueries({ queryKey: guestbookKeys.alerts })
      queryClient.setQueryData<GuestbookAlerts>(guestbookKeys.alerts, (data) => (data ? { ...data, newEntryCount: 0 } : data))
      queryClient.setQueriesData<GuestbookPage>({ queryKey: ['guestbook', 'owner'] }, (data) =>
        data?.owner && (data.seenAt === null || Date.parse(data.seenAt) < Date.parse(until)) ? { ...data, seenAt: until } : data,
      )
    },
    onError: () => queryClient.invalidateQueries({ queryKey: guestbookKeys.all }),
    // 요청이 서버에 반영되기 전에 받은 옛 응답이 점을 되살렸을 수 있어서, 끝나면 서버 값으로 다시 맞춘다.
    onSettled: () => queryClient.invalidateQueries({ queryKey: guestbookKeys.alerts }),
  })
}

/** 글 제한 경고를 확인했다(until = 보여준 경고 중 가장 최근 제한 시각). 안내 창이 다시 뜨지 않게 바로 지운다. */
export function useAckGuestbookWarnings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (until: string) => api<void>('/api/guestbook/warnings/ack', { method: 'POST', body: { until } }),
    onMutate: async (until) => {
      await queryClient.cancelQueries({ queryKey: guestbookKeys.alerts })
      queryClient.setQueryData<GuestbookAlerts>(guestbookKeys.alerts, (data) =>
        data ? { ...data, warnings: data.warnings.filter((w) => Date.parse(w.restrictedAt) > Date.parse(until)) } : data,
      )
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: guestbookKeys.alerts }),
  })
}
