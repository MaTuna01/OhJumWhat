// 탭 포커스 버스트: 창으로 돌아올 때 staleTime 0인 쿼리 9개가 한꺼번에 나간다. N명이 동시에 그러면 Hikari pending·Tomcat busy가 어떻게 되는지 본다.
//   k6 run loadtest/focus-burst.js -e VUS=300 -e ROUNDS=3
import { sleep } from 'k6'
import { memberFor } from './lib/seed.js'
import { login, paths, batchGet } from './lib/http.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const VUS = Number(__ENV.VUS || 100)
const ROUNDS = Number(__ENV.ROUNDS || 3)

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios: { burst: { executor: 'per-vu-iterations', vus: VUS, iterations: ROUNDS, maxDuration: '5m' } },
  thresholds: { http_req_failed: ['rate<0.01'], rt_detail: ['p(95)<1500'], rt_today: ['p(95)<1500'] },
}

export default function () {
  const member = memberFor(__VU)
  const p = paths(member)
  login(member)
  batchGet([
    ['me', p.me],
    ['org', p.org],
    ['today', p.today],
    ['detail', p.detail],
    ['messages', p.messages],
    ['lettersUnread', p.lettersUnread],
    ['guestbookAlerts', p.guestbookAlerts],
    ['sanctionAlerts', p.sanctionAlerts],
    ['version', p.version],
  ])
  sleep(10)
}

export const handleSummary = summaryFor('focus-burst')
