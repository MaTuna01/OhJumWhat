import { parseChatPush, reconnectDelay } from './chat.ts'
import type { ChatPush } from '../queries/chat.ts'

/** 채팅 받기 연결 상태. closed: 채팅이 닫혔거나 탭이 숨겨져 연결하지 않는다 */
export type ChatConnection = 'connecting' | 'open' | 'closed'

/** 서버가 다시 연결하지 말라고 끊는 코드(ChatHub): 4001 채팅이 닫힘, 4003 더 볼 수 없음(투표 삭제·조직 탈퇴·로그아웃) */
export const FINAL_CLOSE_CODES = [4001, 4003]

/**
 * 서버는 30초마다 연결 확인 신호({"type":"ping"})를 보낸다. 이만큼 아무것도 받지 못하면 반쯤 끊긴 연결로 보고 다시 연결한다
 * (네트워크가 바뀌거나 프록시가 한쪽만 끊으면 브라우저가 끊긴 줄 모른다).
 */
export const SILENCE_LIMIT_MS = 75_000

const WATCH_INTERVAL_MS = 15_000

const OPEN = 1

/** WebSocket에서 쓰는 부분만(테스트에서 가짜로 바꾼다) */
export type SocketLike = {
  readonly readyState: number
  onopen: (() => void) | null
  onmessage: ((event: { data: unknown }) => void) | null
  onclose: ((event: { code: number }) => void) | null
  close(code?: number): void
}

type Options = {
  url: string
  onState: (state: ChatConnection) => void
  /** 연결될 때마다(끊긴 동안 놓친 메시지를 목록을 다시 받아 채운다) */
  onOpen: () => void
  onPush: (push: ChatPush) => void
  /** 서버가 다시 연결하지 말라고 끊었을 때(4001·4003) */
  onFinalClose: () => void
  createSocket?: (url: string) => SocketLike
}

/**
 * 투표 채팅 받기 연결(받기 전용). 끊기면 1초부터 두 배씩(최대 30초) 기다렸다 다시 연결하고,
 * 열려 있다는데 75초 동안 아무것도 받지 못하면 버리고 새로 연결한다. 돌려준 함수를 부르면 끊고 멈춘다.
 */
export function openChatSocket({ url, onState, onOpen, onPush, onFinalClose, createSocket = (u) => new WebSocket(u) as unknown as SocketLike }: Options): () => void {
  let socket: SocketLike | null = null
  let retry: ReturnType<typeof setTimeout> | undefined
  let attempt = 0
  let stopped = false
  let lastHeardAt = Date.now()

  const reconnect = () => {
    onState('connecting')
    retry = setTimeout(connect, reconnectDelay(attempt++))
  }

  const connect = () => {
    lastHeardAt = Date.now()
    const current = createSocket(url)
    socket = current
    current.onopen = () => {
      attempt = 0
      lastHeardAt = Date.now()
      onState('open')
      onOpen()
    }
    current.onmessage = (event) => {
      lastHeardAt = Date.now()
      const push = parseChatPush(event.data)
      if (push) onPush(push)
    }
    current.onclose = (event) => {
      socket = null
      if (stopped) return
      if (FINAL_CLOSE_CODES.includes(event.code)) {
        onState('closed')
        onFinalClose()
        return
      }
      reconnect()
    }
  }

  // 반쯤 끊긴 연결 감시: 열려 있다는데 오래 조용하면 버리고 새로 연결한다.
  const watchdog = setInterval(() => {
    if (socket?.readyState === OPEN && Date.now() - lastHeardAt > SILENCE_LIMIT_MS) {
      const dead = socket
      dead.onclose = null
      dead.close()
      socket = null
      reconnect()
    }
  }, WATCH_INTERVAL_MS)

  connect()
  return () => {
    stopped = true
    clearTimeout(retry)
    clearInterval(watchdog)
    socket?.close(1000)
  }
}
