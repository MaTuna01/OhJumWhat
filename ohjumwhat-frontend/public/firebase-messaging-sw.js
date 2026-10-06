// @ts-check
// 오점왓 웹 푸시 서비스 워커. Firebase SDK 없이 FCM이 보낸 data 메시지만 처리한다(빌드하지 않고 그대로 배포).
// 캐시하지 않으므로 fetch 핸들러는 두지 않는다. 형식 검사는 tsconfig.sw.json(tsc -b), 동작 테스트는 src/lib/pushWorker.test.ts

const sw = /** @type {ServiceWorkerGlobalScope} */ (/** @type {unknown} */ (self))

const ICON = '/icons/icon-192.png'
const BADGE = '/icons/badge-96.png'
// 깨진 메시지여도 알림은 띄운다(iOS는 알림 없는 푸시가 쌓이면 구독을 끊는다).
const FALLBACK = { kind: '', title: '오점왓에 새 소식이 있어요', url: '/', tag: 'ohjumwhat' }

/**
 * 같은 출처의 경로만 받는다. 글자로만 보면 URL 해석이 지우는 탭·줄바꿈("/\t/evil.example" → //evil.example)을
 * 놓치므로 실제로 해석한 출처를 비교한다.
 * @param {unknown} url
 * @returns {string | null}
 */
function internalPath(url) {
  if (typeof url !== 'string' || !url) return null
  const origin = sw.location.origin
  try {
    const target = new URL(url, origin)
    return target.origin === origin ? target.pathname + target.search + target.hash : null
  } catch {
    return null
  }
}

/**
 * FCM 웹 푸시 본문({ data: { kind, title, url, tag }, from, … })에서 알림 내용을 꺼낸다.
 * @param {PushMessageData | null} data
 */
function readPush(data) {
  /** @type {unknown} */
  let body = null
  try {
    body = data ? data.json() : null
  } catch {
    // JSON이 아니면 기본 문구로 알린다.
  }
  const fields = body && typeof body === 'object' && 'data' in body ? body.data : null
  if (!fields || typeof fields !== 'object') return FALLBACK
  /** @param {string} key */
  const text = (key) => {
    const value = /** @type {Record<string, unknown>} */ (fields)[key]
    return typeof value === 'string' && value.trim() ? value : null
  }
  return {
    kind: text('kind') ?? '',
    title: text('title') ?? FALLBACK.title,
    url: internalPath(text('url')) ?? FALLBACK.url,
    tag: text('tag') ?? FALLBACK.tag,
  }
}

// Chromium은 보이는 창이 포커스돼 있으면 알림 없이 받아도 된다. WebKit(Safari)·Firefox는 언제나 알림을 띄워야 한다.
function isChromium() {
  return 'userAgentData' in sw.navigator || /\b(Chrome|Chromium)\//.test(sw.navigator.userAgent)
}

function windows() {
  return sw.clients.matchAll({ type: 'window', includeUncontrolled: true })
}

/** @param {PushMessageData | null} data */
async function onPush(data) {
  const push = readPush(data)
  const clients = await windows()
  // 열린 창은 방명록·쪽지를 바로 다시 받는다.
  if (push.kind) {
    for (const client of clients) client.postMessage({ type: 'ohjumwhat:push', kind: push.kind })
  }
  if (isChromium() && clients.some((client) => client.focused)) return
  // 같은 종류(tag)는 하나로 묶되, 새로 올 때마다 다시 울린다. renotify는 크롬이 지원하고 TS 타입에는 없다(tag가 늘 있어 안전하다).
  /** @type {NotificationOptions & { renotify: boolean }} */
  const options = { tag: push.tag, renotify: true, icon: ICON, badge: BADGE, data: { url: push.url } }
  await sw.registration.showNotification(push.title, options)
}

/** @param {unknown} data */
async function onClick(data) {
  const url = internalPath(data && typeof data === 'object' && 'url' in data ? data.url : null)
  if (url === null) return
  const clients = await windows()
  const client = clients.find((c) => c.focused) ?? clients[0]
  if (client) {
    try {
      await client.focus()
    } catch {
      // 포커스하지 못해도 화면은 옮긴다.
    }
    client.postMessage({ type: 'ohjumwhat:navigate', url })
    return
  }
  await sw.clients.openWindow(new URL(url, sw.location.origin).href)
}

sw.addEventListener('install', () => {
  // 고친 서비스 워커를 열린 창이 모두 닫힐 때까지 기다리지 않고 바로 쓴다(캐시가 없어 섞일 것이 없다).
  sw.skipWaiting()
})

sw.addEventListener('push', (event) => {
  event.waitUntil(onPush(event.data))
})

sw.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil(onClick(event.notification.data))
})
