// 사진 동시 처리: 채팅 사진은 서버가 동시에 2장만 다시 그리고(세마포어, 2초 대기 뒤 503), 프로필 사진은 제한이 없다.
//   k6 run loadtest/photo.js -e VUS=10 -e PHOTOS=2 -e MODE=chat|profile|both
// VU마다 다른 회원이라 속도 제한(10분 20장)에 걸리지 않는다. 힙·GC는 actuator 스냅샷(collect.sh)으로 본다.
import http from 'k6/http'
import { sleep } from 'k6'
import { memberFor } from './lib/seed.js'
import { login, paths, post } from './lib/http.js'
import { summaryFor, TREND_STATS } from './lib/summary.js'

const VUS = Number(__ENV.VUS || 10)
const PHOTOS = Number(__ENV.PHOTOS || 2)
const MODE = __ENV.MODE || 'chat'
const chatPhoto = open('./fixtures/chat-photo.jpg', 'b')
const profilePhoto = open('./fixtures/profile.jpg', 'b')

const scenarios = {}
if (MODE === 'chat' || MODE === 'both') {
  scenarios.chat = { executor: 'per-vu-iterations', exec: 'chat', vus: VUS, iterations: PHOTOS, maxDuration: '5m' }
}
if (MODE === 'profile' || MODE === 'both') {
  scenarios.profile = { executor: 'per-vu-iterations', exec: 'profile', vus: VUS, iterations: PHOTOS, maxDuration: '5m', startTime: MODE === 'both' ? '1m' : '0s' }
}

export const options = {
  insecureSkipTLSVerify: true,
  summaryTrendStats: TREND_STATS,
  scenarios,
  thresholds: { unexpected_errors: ['count<1'], rt_sendPhoto: ['p(95)<5000'], rt_profilePhoto: ['p(95)<5000'] },
}

export function chat() {
  const member = memberFor(__VU)
  login(member)
  post('sendPhoto', paths(member).sendPhoto, { photo: http.file(chatPhoto, 'photo.jpg', 'image/jpeg') }, { ok: [200, 201], expected: [503, 429, 409], timeout: '60s' })
  sleep(0.5)
}

export function profile() {
  const member = memberFor(__VU)
  login(member)
  post('profilePhoto', paths(member).profilePhoto, { photo: http.file(profilePhoto, 'photo.jpg', 'image/jpeg') }, { ok: [200], timeout: '60s' })
  sleep(0.5)
}

export const handleSummary = summaryFor('photo')
