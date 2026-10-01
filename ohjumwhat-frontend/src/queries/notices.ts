import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'

/** RELEASE = 업데이트(배포 때 자동 게시), NOTE = 개발자 노트(관리자 작성) */
export type NoticeKind = 'RELEASE' | 'NOTE'

export type Notice = {
  id: number
  kind: NoticeKind
  /** 업데이트 글의 버전(예: 1.5.0). 개발자 노트는 null */
  version: string | null
  title: string
  /** 일반 텍스트: 빈 줄 = 문단, "- " = 목록, http(s) 주소 = 링크 */
  body: string
  publishedAt: string
  /** 불러온 시점 기준으로 안 읽은 공지(NEW) */
  unread: boolean
}

export type NoticePage = { notices: Notice[]; hasMore: boolean }

export type UnreadNotices = {
  count: number
  /** 안 읽은 공지 중 가장 최근 것. 없으면 null */
  latest: Pick<Notice, 'id' | 'kind' | 'version' | 'title'> | null
}

export const noticeKeys = {
  list: ['notices', 'list'] as const,
  unread: ['notices', 'unread'] as const,
}

/**
 * 새 소식(최신순 10개씩). NEW 배지는 연 시점 기준이라 화면에 있는 동안은 창에 다시 들어와도 다시 불러오지 않고,
 * 화면을 떠나면 버려서 다음에 들어올 때 새로 받는다.
 */
export function useNotices() {
  return useInfiniteQuery({
    queryKey: noticeKeys.list,
    queryFn: ({ pageParam }) => api<NoticePage>(`/api/notices?page=${pageParam}`),
    initialPageParam: 0,
    getNextPageParam: (last, pages) => (last.hasMore ? pages.length : undefined),
    refetchOnWindowFocus: false,
    gcTime: 0,
  })
}

/** 안 읽은 공지(상단 바 점, 조직 홈 배너). 창에 다시 들어오면 다시 불러온다. */
export function useUnreadNotices() {
  return useQuery({
    queryKey: noticeKeys.unread,
    queryFn: () => api<UnreadNotices>('/api/notices/unread'),
    staleTime: 60_000,
  })
}

/** 「새 소식」을 봤다(화면을 열거나 배너를 닫음). 점과 배너는 응답을 기다리지 않고 바로 없앤다. */
export function useMarkNoticesSeen() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/notices/seen', { method: 'POST' }),
    onMutate: async () => {
      // 이미 나가 있던 요청의 늦은 응답이 점을 되살리지 않게 먼저 멈춘다.
      await queryClient.cancelQueries({ queryKey: noticeKeys.unread })
      queryClient.setQueryData<UnreadNotices>(noticeKeys.unread, { count: 0, latest: null })
    },
    onError: () => queryClient.invalidateQueries({ queryKey: noticeKeys.unread }),
  })
}
