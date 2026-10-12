// 요청 래퍼. 세션 쿠키는 VU의 쿠키 저장소에 넣고(응답의 XSRF-TOKEN 쿠키도 저장된다), GET이 아닌 요청에는 X-XSRF-TOKEN 헤더를 붙인다.
// 모든 요청에 경로 템플릿 이름(tags.name)을 붙여 k6가 URL마다 시계열을 쪼개지 않게 하고, 이름마다 Trend를 둬 p95를 요약에 남긴다.
import http from 'k6/http'
import { check } from 'k6'
import { Counter, Trend } from 'k6/metrics'

export const BASE_URL = (__ENV.BASE_URL || 'https://localhost').replace(/\/$/, '')
export const WS_URL = BASE_URL.replace(/^http/, 'ws')

/** 경로 템플릿. 키는 Trend 이름(rt_<key>)이 된다. */
export const ROUTES = {
  me: 'GET /api/me',
  config: 'GET /api/config',
  org: 'GET /api/orgs/{o}',
  today: 'GET /api/orgs/{o}/polls/today',
  history: 'GET /api/orgs/{o}/polls/history',
  detail: 'GET /api/orgs/{o}/polls/{p}',
  menuNames: 'GET /api/orgs/{o}/menu-names',
  members: 'GET /api/orgs/{o}/members',
  ranking: 'GET /api/orgs/{o}/ranking',
  vote: 'PUT /api/polls/{p}/vote',
  unvote: 'DELETE /api/polls/{p}/vote',
  addOption: 'POST /api/polls/{p}/options',
  close: 'POST /api/polls/{p}/close',
  messages: 'GET /api/polls/{p}/messages',
  sendMessage: 'POST /api/polls/{p}/messages',
  sendPhoto: 'POST /api/polls/{p}/messages/photo',
  markRead: 'PUT /api/polls/{p}/messages/read',
  lettersUnread: 'GET /api/letters/unread',
  guestbookAlerts: 'GET /api/guestbook/alerts',
  sanctionAlerts: 'GET /api/sanctions/alerts',
  version: 'GET /version.json',
  profilePhoto: 'POST /api/me/photo',
}

const trends = Object.fromEntries(Object.keys(ROUTES).map((key) => [key, new Trend(`rt_${key}`, true)]))

/** 기대한 4xx·503(마감 409, 속도 제한 429, 사진 세마포어 503)은 실패가 아니라 따로 센다. */
export const expectedErrors = new Counter('expected_errors')
export const unexpectedErrors = new Counter('unexpected_errors')

export function paths(member) {
  const o = member.orgId
  const p = member.pollId
  return {
    me: '/api/me',
    config: '/api/config',
    org: `/api/orgs/${o}`,
    today: `/api/orgs/${o}/polls/today`,
    history: `/api/orgs/${o}/polls/history?page=0`,
    detail: `/api/orgs/${o}/polls/${p}`,
    menuNames: (q) => `/api/orgs/${o}/menu-names?q=${encodeURIComponent(q)}`,
    members: `/api/orgs/${o}/members`,
    ranking: `/api/orgs/${o}/ranking?period=week`,
    vote: `/api/polls/${p}/vote`,
    unvote: `/api/polls/${p}/vote`,
    addOption: `/api/polls/${p}/options`,
    close: `/api/polls/${p}/close`,
    messages: `/api/polls/${p}/messages`,
    sendMessage: `/api/polls/${p}/messages`,
    sendPhoto: `/api/polls/${p}/messages/photo`,
    markRead: `/api/polls/${p}/messages/read`,
    lettersUnread: '/api/letters/unread',
    guestbookAlerts: '/api/guestbook/alerts',
    sanctionAlerts: '/api/sanctions/alerts',
    version: '/version.json',
    profilePhoto: '/api/me/photo',
  }
}

/** 이 VU의 쿠키 저장소에 SESSION을 넣는다. 반복(iteration)마다 불러도 된다(같은 값을 덮어쓴다). */
export function login(member) {
  http.cookieJar().set(BASE_URL, 'SESSION', member.cookie, { path: '/' })
}

/** 저장소의 XSRF-TOKEN(서버가 매 응답에 내려준다). 아직 없으면 임의 값(서버는 쿠키와 헤더가 같기만 하면 받는다). */
function xsrfToken() {
  const cookies = http.cookieJar().cookiesForURL(BASE_URL)
  const token = cookies['XSRF-TOKEN'] && cookies['XSRF-TOKEN'][0]
  if (token) {
    return token
  }
  const fresh = `${__VU}-${Date.now()}-${Math.random().toString(16).slice(2)}`
  http.cookieJar().set(BASE_URL, 'XSRF-TOKEN', fresh, { path: '/' })
  return fresh
}

function record(key, res, okStatuses, expected) {
  trends[key].add(res.timings.duration, { status: String(res.status) })
  const ok = okStatuses.includes(res.status)
  if (!ok) {
    if (expected.includes(res.status)) {
      expectedErrors.add(1, { name: ROUTES[key], status: String(res.status) })
    } else {
      unexpectedErrors.add(1, { name: ROUTES[key], status: String(res.status) })
    }
  }
  check(res, { [`${ROUTES[key]} ok`]: () => ok || expected.includes(res.status) })
  return res
}

/**
 * @param key ROUTES 키
 * @param path 실제 경로
 * @param opts { ok: 성공으로 볼 상태 코드, expected: 기대한 오류 코드(실패로 세지 않음), body, timeout }
 */
export function get(key, path, opts = {}) {
  const res = http.get(BASE_URL + path, {
    tags: { name: ROUTES[key] },
    timeout: opts.timeout || '30s',
    responseCallback: http.expectedStatuses(...(opts.ok || [200]), ...(opts.expected || [])),
  })
  return record(key, res, opts.ok || [200], opts.expected || [])
}

export function send(method, key, path, body, opts = {}) {
  const isJson = body !== undefined && !(body instanceof Object && Object.values(body).some((v) => v && v.data))
  const res = http.request(method, BASE_URL + path, isJson && body !== undefined ? JSON.stringify(body) : body, {
    headers: Object.assign({ 'X-XSRF-TOKEN': xsrfToken() }, isJson && body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    tags: { name: ROUTES[key] },
    timeout: opts.timeout || '30s',
    responseCallback: http.expectedStatuses(...(opts.ok || [200]), ...(opts.expected || [])),
  })
  return record(key, res, opts.ok || [200], opts.expected || [])
}

export const put = (key, path, body, opts) => send('PUT', key, path, body, opts)
export const post = (key, path, body, opts) => send('POST', key, path, body, opts)
export const del = (key, path, opts) => send('DELETE', key, path, undefined, opts)

/** 여러 GET을 한꺼번에(탭 포커스 버스트). 배열의 각 항목은 [key, path]. */
export function batchGet(items) {
  const responses = http.batch(
    items.map(([key, path]) => ({
      method: 'GET',
      url: BASE_URL + path,
      params: { tags: { name: ROUTES[key] }, timeout: '30s', responseCallback: http.expectedStatuses(200) },
    })),
  )
  responses.forEach((res, i) => record(items[i][0], res, [200], []))
  return responses
}

export function pick(array) {
  return array[Math.floor(Math.random() * array.length)]
}

/** 평균 ± 흔들림(ms) */
export function jitter(ms, spread) {
  return ms + (Math.random() * 2 - 1) * spread
}
