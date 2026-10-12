// 세션 동시 쓰기: 로그인한 요청마다 spring_session.last_access_time을 UPDATE한다. 한 세션(한 사람의 탭 여러 개)으로 동시 요청을 보내
// 같은 행 잠금 대기가 생기는지 본다. DB 쪽은 collect.sh의 pg_stat_activity 샘플과 log_lock_waits로 본다.
//   k6 run loadtest/session-burst.js -e VUS=20 -e DURATION=60s
import { sleep } from 'k6'
import { members } from './lib/seed.js'
import { login, paths, get } from './lib/http.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const VUS = Number(__ENV.VUS || 20)
const DURATION = __ENV.DURATION || '60s'

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios: { oneSession: { executor: 'constant-vus', vus: VUS, duration: DURATION } },
  thresholds: { http_req_failed: ['rate<0.01'], rt_detail: ['p(95)<500'], rt_today: ['p(95)<500'] },
}

export default function () {
  const member = members[0]
  const p = paths(member)
  login(member)
  get('today', p.today)
  get('detail', p.detail)
  sleep(0.2)
}

export const handleSummary = summaryFor('session-burst')
