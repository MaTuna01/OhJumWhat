import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PushWebConfig } from '../queries/config.ts'

// Firebase SDK·서버 API·브라우저 서비스 워커를 흉내 내고, 일어난 일을 h.events에 순서대로 남긴다.
const h = vi.hoisted(() => ({
  events: [] as string[],
  fid: 'fid_AAAAAAAAAAAAAAAA',
  onRegistered: null as ((fid: string) => void) | null,
  onUnregistered: null as ((fid: string) => void) | null,
  registerOptions: undefined as unknown,
  /** unregister: 성공(onUnregistered 호출)·실패(FCM 오류, 핸들러 안 부름)·끝나지 않음 */
  unregisterMode: 'ok' as 'ok' | 'fail' | 'hang',
  /** 서버 응답을 늦춘다(ms, 0이면 바로) */
  apiDelay: 0,
  apiFail: false,
}))

vi.mock('firebase/app', () => ({
  getApps: () => [],
  initializeApp: (options: unknown) => ({ options }),
}))

// @firebase/messaging 12.19의 동작: register는 onRegistered가 없으면 실패하고 부를 때마다 FID를 알린다.
// unregister는 FCM에서 지우지 못하면 onUnregistered를 부르지 않고 실패한다.
vi.mock('firebase/messaging', () => ({
  isSupported: async () => true,
  getMessaging: (app: unknown) => ({ app }),
  onRegistered: (_messaging: unknown, handler: (fid: string) => void) => {
    h.events.push('onRegistered')
    h.onRegistered = handler
    return () => {}
  },
  onUnregistered: (_messaging: unknown, handler: (fid: string) => void) => {
    h.events.push('onUnregistered')
    h.onUnregistered = handler
    return () => {}
  },
  register: async (_messaging: unknown, options: unknown) => {
    h.events.push('register')
    h.registerOptions = options
    if (!h.onRegistered) throw new Error('messaging/invalid-on-registered-handler')
    h.onRegistered(h.fid)
  },
  unregister: async () => {
    h.events.push('unregister')
    if (h.unregisterMode === 'hang') return new Promise<void>(() => {})
    if (h.unregisterMode === 'fail') throw new Error('messaging/fid-unregister-failed')
    h.onUnregistered?.(h.fid)
  },
}))

vi.mock('./api.ts', () => ({
  api: async (path: string, { method = 'GET' }: { method?: string } = {}) => {
    h.events.push(`${method} ${path}`)
    if (h.apiDelay) await new Promise((resolve) => setTimeout(resolve, h.apiDelay))
    h.events.push(`done ${method} ${path}`)
    if (h.apiFail) throw new Error('서버 오류')
    return method === 'PUT' ? { created: true } : undefined
  },
}))

const base64url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
const keyBytes = Uint8Array.from({ length: 65 }, (_, i) => (i * 37 + 4) % 256)
const oldKeyBytes = Uint8Array.from({ length: 65 }, (_, i) => (i * 11 + 9) % 256)
const config: PushWebConfig = { apiKey: 'api-key', projectId: 'project', appId: '1:2:web:3', messagingSenderId: '2', vapidKey: base64url(keyBytes) }
const DEVICE = `/api/push/devices/${'fid_AAAAAAAAAAAAAAAA'}`

/**
 * 브라우저 흉내. registered: 서비스 워커가 이미 켜져 있다, subscription: 지금 키로 만든 구독·없음·옛 키로 만든 구독
 */
function fakeBrowser({ registered = true, subscription = 'current' }: { registered?: boolean; subscription?: 'current' | 'none' | 'old' } = {}) {
  type FakeSubscription = { options: { applicationServerKey: ArrayBuffer }; unsubscribe: () => Promise<boolean> }
  let current: FakeSubscription | null = null
  const subscribe = (bytes: Uint8Array): FakeSubscription => ({
    options: { applicationServerKey: bytes.slice().buffer },
    unsubscribe: async () => {
      h.events.push('unsubscribe')
      current = null
      return true
    },
  })
  if (subscription !== 'none') current = subscribe(subscription === 'current' ? keyBytes : oldKeyBytes)
  const registration = { scope: 'https://www.ohjumwhat.cloud/', active: { state: 'activated' }, pushManager: { getSubscription: async () => current } }
  let existing = registered ? registration : undefined
  vi.stubGlobal('navigator', {
    serviceWorker: {
      getRegistration: async () => existing,
      register: async (url: string, options: unknown) => {
        h.events.push(`sw.register ${url} ${JSON.stringify(options)}`)
        existing = registration
        return registration
      },
    },
  })
  return { registration, subscription: () => current }
}

const only = (prefixes: string[]) => h.events.filter((e) => prefixes.some((p) => e === p || e.startsWith(`${p} `)))
const firebaseCalls = () => only(['onRegistered', 'onUnregistered', 'register', 'unregister'])
const serverCalls = () => only(['PUT', 'DELETE', 'done'])

let push: typeof import('./pushClient.ts')

beforeEach(async () => {
  Object.assign(h, { events: [], onRegistered: null, onUnregistered: null, registerOptions: undefined, unregisterMode: 'ok', apiDelay: 0, apiFail: false })
  // 모듈 안의 상태(등록한 FID, 순서 큐)를 테스트마다 새로 시작한다.
  vi.resetModules()
  push = await import('./pushClient.ts')
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('registerPush', () => {
  it('onRegistered·onUnregistered를 register보다 먼저 걸고, 서비스 워커를 등록해 FID를 서버에 알린다', async () => {
    const { registration } = fakeBrowser()
    await push.registerPush(config)
    expect(h.events).toEqual([
      'onRegistered',
      'onUnregistered',
      'sw.register /firebase-messaging-sw.js {"scope":"/","updateViaCache":"none"}',
      'register',
      `PUT ${DEVICE}`,
      `done PUT ${DEVICE}`,
    ])
    expect(h.registerOptions).toEqual({ vapidKey: config.vapidKey, serviceWorkerRegistration: registration })
  })

  it('다시 열 때마다 등록하면 매번 서버에 알린다(로그인에 묶인 서버의 기기를 다시 만든다)', async () => {
    fakeBrowser()
    await push.registerPush(config)
    await push.registerPush(config)
    expect(serverCalls().filter((e) => e.startsWith('PUT'))).toHaveLength(2)
    expect(firebaseCalls().filter((e) => e === 'onRegistered')).toHaveLength(1)
  })

  it('서비스 워커를 새로 깔았으면 옛 등록을 지우고 다시 등록한다(서버에는 DELETE가 끝난 뒤 PUT)', async () => {
    fakeBrowser({ registered: false })
    h.apiDelay = 5
    await push.registerPush(config)
    expect(firebaseCalls()).toEqual(['onRegistered', 'onUnregistered', 'unregister', 'register'])
    expect(serverCalls()).toEqual([`DELETE ${DEVICE}`, `done DELETE ${DEVICE}`, `PUT ${DEVICE}`, `done PUT ${DEVICE}`])
  })

  it('브라우저 구독이 없어졌으면 옛 등록을 지우고 다시 등록한다', async () => {
    fakeBrowser({ subscription: 'none' })
    await push.registerPush(config)
    expect(firebaseCalls()).toEqual(['onRegistered', 'onUnregistered', 'unregister', 'register'])
  })

  it('처음 켜는 기기라 지울 등록이 없어 unregister가 실패해도 등록한다', async () => {
    fakeBrowser({ registered: false, subscription: 'none' })
    h.unregisterMode = 'fail'
    await push.registerPush(config)
    expect(firebaseCalls()).toEqual(['onRegistered', 'onUnregistered', 'unregister', 'register'])
    expect(serverCalls()).toEqual([`PUT ${DEVICE}`, `done PUT ${DEVICE}`])
  })

  it('VAPID 키가 바뀌었으면 브라우저 구독을 지우고 → FCM 등록을 지우고 → 다시 등록한다', async () => {
    const browser = fakeBrowser({ subscription: 'old' })
    await push.registerPush(config)
    expect(only(['unsubscribe', 'unregister', 'register'])).toEqual(['unsubscribe', 'unregister', 'register'])
    expect(browser.subscription()).toBeNull()
  })

  it('지금 키로 만든 구독이 그대로 있으면 지우지 않는다', async () => {
    fakeBrowser()
    await push.registerPush(config)
    expect(only(['unsubscribe', 'unregister'])).toEqual([])
  })

  it('서버가 기기를 받지 못하면 실패한다(켜기 오류)', async () => {
    fakeBrowser()
    h.apiFail = true
    await expect(push.registerPush(config)).rejects.toThrow('서버 오류')
  })
})

describe('unregisterPush(끄기)', () => {
  it('FCM 등록을 지우고(서버 DELETE) 브라우저 구독도 지운다', async () => {
    const browser = fakeBrowser()
    await push.registerPush(config)
    h.events = []
    await push.unregisterPush(config)
    expect(h.events).toEqual(['unregister', `DELETE ${DEVICE}`, `done DELETE ${DEVICE}`, 'unsubscribe'])
    expect(browser.subscription()).toBeNull()
  })

  it('FCM에서 지우지 못하면 이 페이지에서 받은 FID로 서버의 기기를 지운다', async () => {
    fakeBrowser()
    await push.registerPush(config)
    h.events = []
    h.unregisterMode = 'fail'
    await push.unregisterPush(config)
    expect(h.events).toEqual(['unregister', `DELETE ${DEVICE}`, `done DELETE ${DEVICE}`, 'unsubscribe'])
  })

  it('FID도 모르면 실패하지만 브라우저 구독은 지운다', async () => {
    const browser = fakeBrowser()
    h.unregisterMode = 'fail'
    await expect(push.unregisterPush(config)).rejects.toThrow('fid-unregister-failed')
    expect(browser.subscription()).toBeNull()
  })
})

describe('releaseOnLogout', () => {
  it('이 페이지에서 등록하지 않았으면 아무것도 하지 않는다(SDK도 부르지 않는다)', async () => {
    fakeBrowser()
    await push.releaseOnLogout()
    expect(h.events).toEqual([])
  })

  it('등록했으면 FCM 등록을 풀고 서버에 알린다', async () => {
    fakeBrowser()
    await push.registerPush(config)
    h.events = []
    await push.releaseOnLogout()
    expect(h.events).toEqual(['unregister', `DELETE ${DEVICE}`, `done DELETE ${DEVICE}`])
  })

  it('FCM·서버가 모두 실패해도 throw하지 않는다', async () => {
    fakeBrowser()
    await push.registerPush(config)
    h.unregisterMode = 'fail'
    h.apiFail = true
    await expect(push.releaseOnLogout()).resolves.toBeUndefined()
  })

  it('1.5초 안에 끝나지 않으면 기다리지 않고 돌아온다', async () => {
    fakeBrowser()
    await push.registerPush(config)
    vi.useFakeTimers()
    h.unregisterMode = 'hang'
    let done = false
    const release = push.releaseOnLogout().then(() => (done = true))
    await vi.advanceTimersByTimeAsync(1_499)
    expect(done).toBe(false)
    await vi.advanceTimersByTimeAsync(1)
    await release
    expect(done).toBe(true)
  })
})
