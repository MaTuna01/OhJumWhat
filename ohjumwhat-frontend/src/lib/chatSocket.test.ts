import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { type ChatConnection, SILENCE_LIMIT_MS, type SocketLike, openChatSocket } from './chatSocket.ts'

class FakeSocket implements SocketLike {
  readyState = 0
  onopen: (() => void) | null = null
  onmessage: ((event: { data: unknown }) => void) | null = null
  onclose: ((event: { code: number }) => void) | null = null
  closedWith: number | undefined

  open() {
    this.readyState = 1
    this.onopen?.()
  }

  receive(data: string) {
    this.onmessage?.({ data })
  }

  /** 서버나 네트워크가 끊었다 */
  drop(code: number) {
    this.readyState = 3
    this.onclose?.({ code })
  }

  close(code?: number) {
    this.closedWith = code ?? 1005
    this.readyState = 3
  }
}

describe('openChatSocket', () => {
  let sockets: FakeSocket[]
  let states: ChatConnection[]
  let opens: number
  let finals: number
  let pushes: unknown[]
  let stop: () => void

  beforeEach(() => {
    vi.useFakeTimers()
    sockets = []
    states = []
    opens = 0
    finals = 0
    pushes = []
    stop = openChatSocket({
      url: 'ws://localhost/api/polls/1/ws',
      createSocket: () => {
        const socket = new FakeSocket()
        sockets.push(socket)
        return socket
      },
      onState: (state) => states.push(state),
      onOpen: () => opens++,
      onPush: (push) => pushes.push(push),
      onFinalClose: () => finals++,
    })
  })

  afterEach(() => {
    stop()
    vi.useRealTimers()
  })

  it('연결되면 열림을 알리고 받은 메시지를 넘긴다(연결 확인 신호는 넘기지 않는다)', () => {
    sockets[0].open()
    sockets[0].receive('{"type":"ping"}')
    sockets[0].receive(JSON.stringify({ type: 'created', message: { id: 3 } }))
    expect(states).toEqual(['open'])
    expect(opens).toBe(1)
    expect(pushes).toHaveLength(1)
  })

  it('끊기면 1초, 2초… 뒤에 다시 연결하고 다시 열리면 목록을 다시 받는다', () => {
    sockets[0].open()
    sockets[0].drop(1006)
    expect(states.at(-1)).toBe('connecting')
    vi.advanceTimersByTime(999)
    expect(sockets).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(sockets).toHaveLength(2)

    sockets[1].drop(1006) // 서버가 아직 안 떴다
    vi.advanceTimersByTime(1999)
    expect(sockets).toHaveLength(2)
    vi.advanceTimersByTime(1)
    expect(sockets).toHaveLength(3)

    sockets[2].open()
    expect(states.at(-1)).toBe('open')
    expect(opens).toBe(2)
  })

  it('채팅이 닫혔거나(4001) 더 볼 수 없으면(4003) 다시 연결하지 않는다', () => {
    sockets[0].open()
    sockets[0].drop(4001)
    vi.advanceTimersByTime(60_000)
    expect(sockets).toHaveLength(1)
    expect(states.at(-1)).toBe('closed')
    expect(finals).toBe(1)
  })

  it('열려 있다는데 75초 넘게 아무것도 받지 못하면 버리고 새로 연결한다', () => {
    sockets[0].open()
    vi.advanceTimersByTime(30_000)
    sockets[0].receive('{"type":"ping"}')
    vi.advanceTimersByTime(SILENCE_LIMIT_MS)
    expect(sockets).toHaveLength(1)

    vi.advanceTimersByTime(15_000) // 마지막 신호 뒤 90초
    expect(sockets[0].closedWith).toBeDefined()
    expect(states.at(-1)).toBe('connecting')
    vi.advanceTimersByTime(1000)
    expect(sockets).toHaveLength(2)
  })

  it('멈추면 연결을 닫고 다시 연결하지 않는다', () => {
    sockets[0].open()
    stop()
    expect(sockets[0].closedWith).toBe(1000)
    sockets[0].drop(1000)
    vi.advanceTimersByTime(120_000)
    expect(sockets).toHaveLength(1)
  })
})
