import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api.ts'
import { mergeChatPage, mergeMessages } from '../lib/chat.ts'
import type { Person } from './polls.ts'

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

/** 오래된 → 최신. hasMore: 이보다 오래된 메시지가 더 있다 */
export type ChatPage = { messages: ChatMessage[]; hasMore: boolean }

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
        page ? { messages: mergeMessages(page.messages, older.messages), hasMore: older.hasMore } : page,
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

export function useSendMessage(pollId: number) {
  return useChatMutation(pollId, (body: string) => api<ChatMessage>(messagesUrl(pollId), { method: 'POST', body: { body } }))
}

export function useEditMessage(pollId: number) {
  return useChatMutation(pollId, ({ id, body }: { id: number; body: string }) =>
    api<ChatMessage>(`${messagesUrl(pollId)}/${id}`, { method: 'PUT', body: { body } }),
  )
}

export function useDeleteMessage(pollId: number) {
  return useChatMutation(pollId, (id: number) => api<ChatMessage>(`${messagesUrl(pollId)}/${id}`, { method: 'DELETE' }))
}
