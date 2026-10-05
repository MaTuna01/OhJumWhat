import { type InfiniteData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import { updateLetterInPages } from '../lib/letters.ts'
import type { Person } from './polls.ts'

export type LetterBox = 'RECEIVED' | 'SENT'

/** 쪽지(보는 사람 기준). 서버 LetterResponse */
export type Letter = {
  id: number
  box: LetterBox
  /** 어느 조직에서 보냈는지. 조직이 없어졌으면 null(「삭제된 조직」) */
  organization: { id: number; name: string } | null
  /** 상대(받은 쪽지는 보낸 사람, 보낸 쪽지는 받는 사람). 숨겼거나(counterpartHidden) 탈퇴했으면 null */
  counterpart: Person | null
  /** 상대를 「익명」으로 보여준다 */
  counterpartHidden: boolean
  /** 받은 쪽지: 익명으로 왔는지, 보낸 쪽지: 내가 익명으로 보냈는지 */
  anonymous: boolean
  body: string
  createdAt: string
  /** 받은 사람이 처음 읽은 시각(안 읽었으면 null) */
  readAt: string | null
  /** 답장이면 원래 쪽지와 그 첫 줄 */
  replyTo: { id: number; preview: string | null } | null
  /** 받은 쪽지에 답장할 수 있는지 */
  canReply: boolean
  /** 받은 쪽지에 답장하면 익명으로만 간다(내가 익명으로 보낸 쪽지에 온 답장) */
  replyAnonymous: boolean
  /** 받은 쪽지를 내가 신고했는지 */
  reported: boolean
}

export type LetterPage = { letters: Letter[]; hasMore: boolean }

/** 차단한 사람. 익명 쪽지에서 차단했으면 person 없이 「익명 쪽지를 보낸 사람」 */
export type LetterBlock = { id: number; anonymous: boolean; person: Person | null; preview: string | null; createdAt: string }

export const letterKeys = {
  all: ['letters'] as const,
  box: (box: LetterBox) => ['letters', 'box', box] as const,
  unread: ['letters', 'unread'] as const,
  blocks: ['letters', 'blocks'] as const,
}

/** 쪽지함(최신순 20통씩, 「더 보기」로 이어 받는다). */
export function useLetters(box: LetterBox) {
  return useInfiniteQuery({
    queryKey: letterKeys.box(box),
    queryFn: ({ pageParam }) =>
      api<LetterPage>(`/api/letters?box=${box.toLowerCase()}${pageParam ? `&before=${pageParam}` : ''}`),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasMore ? last.letters[last.letters.length - 1]?.id : undefined),
  })
}

/**
 * 안 읽은 쪽지 수(상단 바 배지). 실시간 연결이 없어서 30초마다, 그리고 창에 다시 들어오면 새로 받는다
 * (숨긴 탭에서는 쉰다).
 */
export function useUnreadLetters() {
  return useQuery({
    queryKey: letterKeys.unread,
    queryFn: () => api<{ count: number }>('/api/letters/unread'),
    refetchInterval: 30_000,
    staleTime: 15_000,
  })
}

export type SendLetter =
  | { kind: 'new'; organizationId: number; recipientId: number; body: string; anonymous: boolean }
  | { kind: 'reply'; letterId: number; body: string; anonymous: boolean }

/** 보내기·답장. 보낸 쪽지함을 다시 받는다. */
export function useSendLetter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (letter: SendLetter) =>
      letter.kind === 'new'
        ? api<Letter>('/api/letters', {
            method: 'POST',
            body: { organizationId: letter.organizationId, recipientId: letter.recipientId, body: letter.body, anonymous: letter.anonymous },
          })
        : api<Letter>(`/api/letters/${letter.letterId}/reply`, {
            method: 'POST',
            body: { body: letter.body, anonymous: letter.anonymous },
          }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: letterKeys.box('SENT') }),
  })
}

/** 받은 쪽지를 열었다: 응답을 기다리지 않고 읽음으로 바꾸고 배지를 줄인다. */
export function useMarkLetterRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (letterId: number) => api<void>(`/api/letters/${letterId}/read`, { method: 'PUT' }),
    onMutate: async (letterId) => {
      await queryClient.cancelQueries({ queryKey: letterKeys.unread })
      queryClient.setQueryData<InfiniteData<LetterPage>>(letterKeys.box('RECEIVED'), (data) =>
        updateLetterInPages(data, letterId, (l) => (l.readAt ? l : { ...l, readAt: new Date().toISOString() })),
      )
      queryClient.setQueryData<{ count: number }>(letterKeys.unread, (data) =>
        data ? { count: Math.max(0, data.count - 1) } : data,
      )
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: letterKeys.unread }),
  })
}

/** 내 쪽지함에서만 지운다. */
export function useDeleteLetter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (letterId: number) => api<void>(`/api/letters/${letterId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: letterKeys.all }),
  })
}

/** 보낸 사람 차단(그 쪽지의 익명 여부 범위로). 받은 쪽지함에서 그 사람의 쪽지가 사라진다. */
export function useBlockLetterSender() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (letterId: number) => api<void>(`/api/letters/${letterId}/block`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: letterKeys.all }),
  })
}

/** 신고(사유는 선택). block이면 보낸 사람도 차단한다. */
export function useReportLetter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ letterId, reason, block }: { letterId: number; reason: string; block: boolean }) =>
      api<void>(`/api/letters/${letterId}/report`, { method: 'POST', body: { reason: reason.trim() || null, block } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: letterKeys.all }),
  })
}

export function useLetterBlocks(enabled: boolean) {
  return useQuery({
    queryKey: letterKeys.blocks,
    queryFn: () => api<LetterBlock[]>('/api/letters/blocks'),
    enabled,
  })
}

/** 차단 풀기: 차단하기 전에 받은 쪽지가 다시 보인다. */
export function useUnblockLetterSender() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (blockId: number) => api<void>(`/api/letters/blocks/${blockId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: letterKeys.all }),
  })
}
