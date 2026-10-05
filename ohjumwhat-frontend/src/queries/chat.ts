import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef } from 'react'
import { api } from '../lib/api.ts'
import { mergeChatPage, mergeMessages } from '../lib/chat.ts'
import { type Person, pollKeys } from './polls.ts'

/**
 * 투표 채팅 메시지. 같은 투표를 보는 모두에게 WebSocket으로도 오므로 "내 글인지"는 author로 판단한다.
 * author가 null이면 강제 탈퇴로 삭제된 회원("탈퇴한 사용자"), 지운 메시지는 body가 null이다.
 */
export type ChatMessage = {
  id: number
  author: Person | null
  body: string | null
  createdAt: string
  /** 마지막으로 고친 시각. 같은 메시지를 여러 번 받으면 더 늦은 쪽이 최신이다 */
  editedAt: string | null
  deleted: boolean
}

/** 오래된 → 최신. hasMore: 이보다 오래된 메시지가 더 있다. lastReadId: 내가 마지막으로 본 메시지 ID(없으면 0) */
export type ChatPage = { messages: ChatMessage[]; hasMore: boolean; lastReadId: number }

/** WebSocket으로 받는 글 */
export type ChatPush = { type: 'created' | 'updated' | 'deleted'; message: ChatMessage }

export const chatKeys = {
  poll: (pollId: number) => ['chat', pollId] as const,
}

const messagesUrl = (pollId: number) => `/api/polls/${pollId}/messages`

/**
 * 최신 메시지 50개. 다시 받으면(창이 다시 보일 때·다시 연결됐을 때) 이미 받은 메시지(이전 메시지·WebSocket)와 합친다.
 * 합칠 때 캐시의 그 순간 값과 합치므로(structuralSharing) 응답을 기다리는 사이에 WebSocket으로 받은 메시지를 잃지 않는다.
 */
export function useChatMessages(pollId: number) {
  return useQuery({
    queryKey: chatKeys.poll(pollId),
    queryFn: () => api<ChatPage>(messagesUrl(pollId)),
    structuralSharing: (old, fresh) => mergeChatPage(old as ChatPage | undefined, fresh as ChatPage),
  })
}

/** 받은 메시지(내가 보낸 응답·WebSocket)를 캐시에 합친다. 아직 목록을 받기 전이면 받을 때 함께 온다. */
export function upsertMessages(queryClient: QueryClient, pollId: number, messages: ChatMessage[]) {
  queryClient.setQueryData<ChatPage>(chatKeys.poll(pollId), (page) =>
    page ? { ...page, messages: mergeMessages(page.messages, messages) } : page,
  )
}

/** 「이전 메시지 더 보기」: 가장 오래된 메시지보다 앞의 50개를 붙인다. */
export function useLoadOlderMessages(pollId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (before: number) => api<ChatPage>(`${messagesUrl(pollId)}?before=${before}`),
    onSuccess: (older) =>
      queryClient.setQueryData<ChatPage>(chatKeys.poll(pollId), (page) =>
        page ? { ...page, messages: mergeMessages(page.messages, older.messages), hasMore: older.hasMore } : page,
      ),
  })
}

function useChatMutation<T>(pollId: number, request: (arg: T) => Promise<ChatMessage>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (message) => upsertMessages(queryClient, pollId, [message]),
  })
}

/** 보내면 서버가 내 메시지까지 읽은 것으로 하므로 화면의 읽은 위치도 올린다. */
export function useSendMessage(pollId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: string) => api<ChatMessage>(messagesUrl(pollId), { method: 'POST', body: { body } }),
    onSuccess: (message) => {
      upsertMessages(queryClient, pollId, [message])
      raiseLastRead(queryClient, pollId, message.id)
    },
  })
}

export function useEditMessage(pollId: number) {
  return useChatMutation(pollId, ({ id, body }: { id: number; body: string }) =>
    api<ChatMessage>(`${messagesUrl(pollId)}/${id}`, { method: 'PUT', body: { body } }),
  )
}

export function useDeleteMessage(pollId: number) {
  return useChatMutation(pollId, (id: number) => api<ChatMessage>(`${messagesUrl(pollId)}/${id}`, { method: 'DELETE' }))
}

/**
 * 화면의 읽은 위치를 올린다(내려가지 않는다). 다시 받은 목록과는 mergeChatPage가 큰 쪽으로 합친다.
 * 바뀌지 않으면 캐시를 건드리지 않는다(setQueryData는 같은 값도 structuralSharing으로 새 객체를 만들어 다시 그리게 한다).
 */
function raiseLastRead(queryClient: QueryClient, pollId: number, messageId: number) {
  queryClient.setQueryData<ChatPage>(chatKeys.poll(pollId), (page) =>
    page && messageId > page.lastReadId ? { ...page, lastReadId: messageId } : undefined,
  )
}

/**
 * 「이 메시지까지 봤다」. 화면(안 읽은 수)은 바로 바꾸고, 서버에는 1초에 한 번 가장 뒤의 위치만 보낸다.
 * 화면을 떠나거나(시트를 닫거나) 탭을 숨기면 기다리지 않고 바로 보낸다(keepalive). 저장되면 조직 홈의 투표 카드를 다시 받는다.
 * 실패하면(투표가 지워졌거나 로그아웃) 그냥 둔다. 다음에 더 뒤를 볼 때나 채팅을 다시 열 때 다시 보낸다.
 */
export function useMarkChatRead(pollId: number, orgId: number): (messageId: number) => void {
  const queryClient = useQueryClient()
  const state = useRef<{ sent: number; target: number; timer?: ReturnType<typeof setTimeout> }>({ sent: 0, target: 0 })

  const flush = useCallback(
    (keepalive = false) => {
      const s = state.current
      clearTimeout(s.timer)
      s.timer = undefined
      if (s.target <= s.sent) return
      const previous = s.sent
      const lastReadId = s.target
      s.sent = lastReadId
      api(`${messagesUrl(pollId)}/read`, { method: 'PUT', body: { lastReadId }, keepalive })
        .then(() => queryClient.invalidateQueries({ queryKey: pollKeys.today(orgId) }))
        .catch(() => {
          if (s.sent === lastReadId) s.sent = previous
        })
    },
    [pollId, orgId, queryClient],
  )

  useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === 'hidden') flush(true)
    }
    document.addEventListener('visibilitychange', onHide)
    return () => {
      document.removeEventListener('visibilitychange', onHide)
      flush(true)
    }
  }, [flush])

  return useCallback(
    (messageId: number) => {
      raiseLastRead(queryClient, pollId, messageId)
      const s = state.current
      if (messageId <= Math.max(s.sent, s.target)) return
      s.target = messageId
      s.timer ??= setTimeout(() => flush(), 1000)
    },
    [queryClient, pollId, flush],
  )
}
