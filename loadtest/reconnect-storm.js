// 재연결 폭풍: 앱 재시작 직후 모든 화면이 같은 순간 다시 연결한다(프론트 백오프에 지터가 없다). 연결마다 GET /messages가 따라온다.
// 앱을 재시작한 직후(docker compose restart app, /healthz UP 확인) 실행한다.
//   k6 run loadtest/reconnect-storm.js -e VUS=300 -e SPREAD=1s   (SPREAD=10s로 분산 변형)
import { sleep } from 'k6'
import { memberFor } from './lib/seed.js'
import { login, paths, get } from './lib/http.js'
import { openChat } from './lib/ws.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const VUS = Number(__ENV.VUS || 300)
const SPREAD = __ENV.SPREAD || '1s'
const HOLD_MS = Number(__ENV.HOLD_SEC || 60) * 1000

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios: {
    storm: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: SPREAD, target: VUS },
        { duration: `${HOLD_MS / 1000 + 5}s`, target: VUS },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: { ws_connecting: ['p(95)<2000'], ws_handshake_failed: ['count<1'], rt_messages: ['p(95)<1500'] },
}

export default function () {
  const member = memberFor(__VU)
  const p = paths(member)
  login(member)
  openChat(member, {
    ttlMs: HOLD_MS,
    onOpen: () => get('messages', p.messages),
    intervals: [{ ms: 3000, fn: () => get('detail', p.detail) }],
  })
  sleep(1)
}

export const handleSummary = summaryFor('reconnect-storm')
