import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { chatSocketUrl } from '../lib/chat.ts'
import { type ChatConnection, openChatSocket } from '../lib/chatSocket.ts'
import { chatKeys, upsertMessages } from '../queries/chat.ts'
import { pollKeys } from '../queries/polls.ts'
import { useDocumentVisible } from './useDocumentVisible.ts'
import { useNow } from './useNow.ts'

export type { ChatConnection }

/**
 * 투표 채팅 받기(WebSocket, 받기 전용). 보내기·고치기·지우기는 REST로 한다.
 * 채팅이 열려 있고(마감 1시간 뒤까지) 탭이 보일 때만 연결한다. 다시 연결·반쯤 끊긴 연결 감시는 openChatSocket이 맡고,
 * 연결될 때마다 목록을 다시 받아 끊긴 동안(배포·숨긴 탭) 놓친 메시지를 채운다.
 */
export function usePollChatSocket(pollId: number, chatClosesAt: string | undefined): ChatConnection {
  const queryClient = useQueryClient()
  const visible = useDocumentVisible()
  const now = useNow(30_000)
  const chatOpen = chatClosesAt != null && now < Date.parse(chatClosesAt)
  const active = chatOpen && visible
  const [state, setState] = useState<ChatConnection>('connecting')

  useEffect(() => {
    if (!active) return
    return openChatSocket({
      url: chatSocketUrl(pollId),
      onState: setState,
      onOpen: () => queryClient.invalidateQueries({ queryKey: chatKeys.poll(pollId) }),
      onPush: (push) => upsertMessages(queryClient, pollId, [push.message]),
      // 채팅이 닫혔거나 더 볼 수 없다: 투표 상세를 다시 받아 화면을 맞춘다(404면 「찾을 수 없어요」).
      onFinalClose: () => queryClient.invalidateQueries({ queryKey: pollKeys.detail(pollId) }),
    })
  }, [pollId, active, queryClient])

  return active ? state : 'closed'
}
