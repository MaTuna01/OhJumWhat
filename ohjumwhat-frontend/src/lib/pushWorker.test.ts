import { describe, expect, it } from 'vitest'
// 배포되는 서비스 워커 파일을 그대로 읽어 실행한다(빌드하지 않고 public/에서 복사된다).
import source from '../../public/firebase-messaging-sw.js?raw'

const ORIGIN = 'https://www.ohjumwhat.cloud'
const CHROME = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
const SAFARI = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1'
const FIREFOX = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:143.0) Gecko/20100101 Firefox/143.0'

type FakeWindow = { focused: boolean; messages: unknown[]; focusCalls: number; postMessage: (message: unknown) => void; focus: () => Promise<FakeWindow> }
type Listener = (event: unknown) => void

function fakeWindow(focused: boolean): FakeWindow {
  const win: FakeWindow = {
    focused,
    messages: [],
    focusCalls: 0,
    postMessage: (message) => win.messages.push(message),
    focus: async () => {
      win.focusCalls++
      return win
    },
  }
  return win
}

function startWorker({ userAgent, chromiumHints = false, windows = [] }: { userAgent: string; chromiumHints?: boolean; windows?: FakeWindow[] }) {
  const listeners = new Map<string, Listener>()
  const shown: { title: string; options: NotificationOptions }[] = []
  const opened: string[] = []
  const self = {
    location: { origin: ORIGIN },
    navigator: chromiumHints ? { userAgent, userAgentData: { brands: [] } } : { userAgent },
    addEventListener: (type: string, listener: Listener) => listeners.set(type, listener),
    skipWaiting: async () => {},
    registration: {
      showNotification: async (title: string, options: NotificationOptions) => {
        shown.push({ title, options })
      },
    },
    clients: {
      matchAll: async () => windows,
      openWindow: async (url: string) => {
        opened.push(url)
        return null
      },
    },
  }
  new Function('self', source)(self)

  async function dispatch(type: string, event: object) {
    let done: Promise<unknown> = Promise.resolve()
    listeners.get(type)?.({ ...event, waitUntil: (promise: Promise<unknown>) => (done = promise) })
    await done
  }

  return {
    shown,
    opened,
    listeners,
    push: (payload: unknown) => dispatch('push', { data: { json: () => (typeof payload === 'string' ? JSON.parse(payload) : payload) } }),
    pushRaw: (data: unknown) => dispatch('push', { data }),
    click: (data: unknown) => {
      let closed = false
      return dispatch('notificationclick', { notification: { data, close: () => (closed = true) } }).then(() => closed)
    },
  }
}

const letter = {
  data: { kind: 'LETTER', title: '김오점님이 쪽지를 보냈어요', url: '/letters', tag: 'ohjumwhat-letter' },
  from: '1234567890',
  fcmMessageId: 'abc',
}

describe('firebase-messaging-sw.js', () => {
  it('push·notificationclick만 듣고 fetch 핸들러는 없다(캐시하지 않는다)', () => {
    const worker = startWorker({ userAgent: CHROME })
    expect([...worker.listeners.keys()].sort()).toEqual(['install', 'notificationclick', 'push'])
  })

  it('열린 창이 없으면 알림을 띄운다', async () => {
    const worker = startWorker({ userAgent: CHROME, chromiumHints: true })
    await worker.push(letter)
    expect(worker.shown).toEqual([
      {
        title: '김오점님이 쪽지를 보냈어요',
        options: { tag: 'ohjumwhat-letter', renotify: true, icon: '/icons/icon-192.png', badge: '/icons/badge-96.png', data: { url: '/letters' } },
      },
    ])
  })

  it('Chromium에서 포커스된 창이 있으면 알림을 생략하고 창에만 알린다', async () => {
    const focused = fakeWindow(true)
    const background = fakeWindow(false)
    const worker = startWorker({ userAgent: CHROME, chromiumHints: true, windows: [background, focused] })
    await worker.push(letter)
    expect(worker.shown).toEqual([])
    expect(focused.messages).toEqual([{ type: 'ohjumwhat:push', kind: 'LETTER' }])
    expect(background.messages).toEqual([{ type: 'ohjumwhat:push', kind: 'LETTER' }])
  })

  it('Chromium이어도 포커스된 창이 없으면 알림을 띄운다', async () => {
    const worker = startWorker({ userAgent: CHROME, windows: [fakeWindow(false)] })
    await worker.push(letter)
    expect(worker.shown).toHaveLength(1)
  })

  it('WebKit·Firefox는 포커스된 창이 있어도 알림을 띄운다(iOS는 알림 없는 푸시가 쌓이면 구독을 끊는다)', async () => {
    for (const userAgent of [SAFARI, FIREFOX]) {
      const focused = fakeWindow(true)
      const worker = startWorker({ userAgent, windows: [focused] })
      await worker.push(letter)
      expect(worker.shown).toHaveLength(1)
      expect(focused.messages).toEqual([{ type: 'ohjumwhat:push', kind: 'LETTER' }])
    }
  })

  it('깨진 payload도 기본 문구로 알린다', async () => {
    const fallback = {
      title: '오점왓에 새 소식이 있어요',
      options: { tag: 'ohjumwhat', renotify: true, icon: '/icons/icon-192.png', badge: '/icons/badge-96.png', data: { url: '/' } },
    }
    for (const data of [null, { json: () => JSON.parse('not json') }, { json: () => ({ from: 'x' }) }, { json: () => ({ data: 'x' }) }]) {
      const focused = fakeWindow(true)
      const worker = startWorker({ userAgent: SAFARI, windows: [focused] })
      await worker.pushRaw(data)
      expect(worker.shown).toEqual([fallback])
      // 종류를 모르면 창에 다시 받으라고 알리지 않는다.
      expect(focused.messages).toEqual([])
    }
  })

  it('빠진 값은 기본값으로, 다른 출처 url은 / 로 바꾼다', async () => {
    const worker = startWorker({ userAgent: SAFARI })
    await worker.push({ data: { kind: 'GUESTBOOK', title: '이영희님이 방명록을 남겼어요', url: 'https://evil.example/' } })
    await worker.push({ data: { kind: 'GUESTBOOK', title: '', url: '//evil.example/me' } })
    await worker.push({ data: { kind: 'LETTER', title: '익명 쪽지가 왔어요', url: '/\t/evil.example' } })
    expect(worker.shown.map((n) => [n.title, n.options.tag, n.options.data])).toEqual([
      ['이영희님이 방명록을 남겼어요', 'ohjumwhat', { url: '/' }],
      ['오점왓에 새 소식이 있어요', 'ohjumwhat', { url: '/' }],
      ['익명 쪽지가 왔어요', 'ohjumwhat', { url: '/' }],
    ])
  })

  it('알림을 누르면 닫고, 열린 창이 있으면 그 창을 포커스해서 옮긴다', async () => {
    const background = fakeWindow(false)
    const worker = startWorker({ userAgent: CHROME, windows: [background] })
    const closed = await worker.click({ url: '/me#guestbook' })
    expect(closed).toBe(true)
    expect(background.focusCalls).toBe(1)
    expect(background.messages).toEqual([{ type: 'ohjumwhat:navigate', url: '/me#guestbook' }])
    expect(worker.opened).toEqual([])
  })

  it('포커스된 창이 있으면 그 창을 옮긴다', async () => {
    const other = fakeWindow(false)
    const focused = fakeWindow(true)
    const worker = startWorker({ userAgent: CHROME, windows: [other, focused] })
    await worker.click({ url: '/letters' })
    expect(focused.messages).toEqual([{ type: 'ohjumwhat:navigate', url: '/letters' }])
    expect(other.messages).toEqual([])
  })

  it('열린 창이 없으면 새 창으로 연다', async () => {
    const worker = startWorker({ userAgent: SAFARI })
    await worker.click({ url: '/letters' })
    expect(worker.opened).toEqual([`${ORIGIN}/letters`])
  })

  it('다른 출처 url은 열지 않는다', async () => {
    const urls = ['https://evil.example', 'https://evil.example/', '//evil.example', '//evil.example/letters', '/\t/evil.example', '/\n/evil.example', '/\\evil.example', 'javascript:alert(1)', 42, undefined]
    for (const url of urls) {
      const win = fakeWindow(false)
      const worker = startWorker({ userAgent: CHROME, windows: [win] })
      const closed = await worker.click({ url })
      expect(closed).toBe(true)
      expect(worker.opened).toEqual([])
      expect(win.messages).toEqual([])
    }
    const worker = startWorker({ userAgent: CHROME })
    await worker.click(null)
    expect(worker.opened).toEqual([])
  })
})
