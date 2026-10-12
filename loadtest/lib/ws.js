// 투표 채팅 받기 연결(/api/polls/{p}/ws). 받기 전용이라 보내지 않는다. 받은 글 수는 k6 기본 지표 ws_msgs_received가 센다(서버 ping 포함).
// k6의 ws.connect는 연결이 닫힐 때까지 VU를 붙들고, 콜백 안에서 http 요청을 보낼 수 있어 「소켓을 든 채 폴링하는 화면」을 VU 하나로 흉내 낸다.
import ws from 'k6/ws'
import { check } from 'k6'
import { Counter } from 'k6/metrics'
import { WS_URL } from './http.js'

export const wsHandshakeFailed = new Counter('ws_handshake_failed')
export const wsClosed = new Counter('ws_closed')

/**
 * @param member 시드 멤버
 * @param opts { ttlMs: 연결 유지 시간, onOpen(socket), onMessage(socket, data), intervals: [{ms, fn(socket)}] }
 * @returns ws.connect 결과(status 101이면 성공)
 */
export function openChat(member, opts = {}) {
  const url = `${WS_URL}/api/polls/${member.pollId}/ws`
  const res = ws.connect(
    url,
    { headers: { Cookie: `SESSION=${member.cookie}` }, tags: { name: 'WS /api/polls/{p}/ws' } },
    (socket) => {
      socket.on('open', () => {
        if (opts.onOpen) {
          opts.onOpen(socket)
        }
        ;(opts.intervals || []).forEach(({ ms, fn }) => socket.setInterval(() => fn(socket), ms))
        if (opts.ttlMs) {
          socket.setTimeout(() => socket.close(), opts.ttlMs)
        }
      })
      socket.on('message', (data) => {
        if (opts.onMessage) {
          opts.onMessage(socket, data)
        }
      })
      socket.on('close', (code) => wsClosed.add(1, { code: String(code) }))
      socket.on('error', (e) => {
        if (e && e.error() !== 'websocket: close sent') {
          console.warn(`ws error vu=${__VU}: ${e.error()}`)
        }
      })
    },
  )
  const ok = check(res, { 'ws 101': (r) => r && r.status === 101 })
  if (!ok) {
    wsHandshakeFailed.add(1, { status: String(res && res.status) })
  }
  return res
}
