import type { Messaging } from 'firebase/messaging'
import type { PushWebConfig } from '../queries/config.ts'
import { api } from './api.ts'
import { sameVapidKey, withTimeout } from './push.ts'

// 웹 푸시(FCM) 등록. Firebase SDK는 알림을 켠 사람만 받도록 dynamic import로 따로 둔다.
// 서버에는 기기를 FID(Firebase 설치 ID)로 알린다: onRegistered → PUT, onUnregistered → DELETE /api/push/devices/{fid}

const SW_URL = '/firebase-messaging-sw.js'
const SW_ACTIVE_TIMEOUT_MS = 10_000
const LOGOUT_TIMEOUT_MS = 1_500

type Sdk = {
  app: typeof import('firebase/app')
  messaging: typeof import('firebase/messaging')
}

type Client = { sdk: Sdk; messaging: Messaging }

let sdkPromise: Promise<Sdk> | null = null
let client: Promise<Client> | null = null
// 서버에 보내는 PUT·DELETE를 부른 순서대로 보낸다(unregister 바로 뒤의 register는 DELETE → PUT이어야 한다).
let serverSync: Promise<unknown> = Promise.resolve()
// 마지막으로 서버에 알린 FID(끄기에서 FCM 해제가 실패해도 서버의 기기는 지운다)
let lastFid: string | null = null
// 켜기·끄기·다시 등록이 서로 섞이지 않게 한 번에 하나씩 한다.
let flow: Promise<unknown> = Promise.resolve()

function exclusive<T>(task: () => Promise<T>): Promise<T> {
  const next = flow.then(task, task)
  flow = next.catch(() => {})
  return next
}

function toServer(task: () => Promise<unknown>) {
  const next = serverSync.catch(() => {}).then(task)
  // 기다리는 쪽(켜기·끄기)이 없을 때(FID가 바뀌어 SDK가 스스로 다시 등록할 때 등) 실패가 처리되지 않은 오류로 남지 않게
  next.catch(() => {})
  serverSync = next
}

function loadSdk(): Promise<Sdk> {
  if (!sdkPromise) {
    sdkPromise = Promise.all([import('firebase/app'), import('firebase/messaging')]).then(([app, messaging]) => ({ app, messaging }))
    // 네트워크 문제로 못 받았으면 다음에 다시 받는다.
    sdkPromise.catch(() => {
      sdkPromise = null
    })
  }
  return sdkPromise
}

/** Firebase가 이 브라우저를 지원하는지(IndexedDB·서비스 워커·푸시 API 등). 알림 카드를 그릴 때 불러 SDK 청크를 미리 받아 둔다. */
export async function pushSdkSupported(): Promise<boolean> {
  const sdk = await loadSdk()
  return sdk.messaging.isSupported()
}

function getClient(config: PushWebConfig): Promise<Client> {
  if (!client) {
    client = loadSdk().then(async (sdk) => {
      // 지원하지 않는 브라우저에서 getMessaging을 부르면 처리되지 않은 오류가 남는다.
      if (!(await sdk.messaging.isSupported())) throw new Error('이 브라우저에서는 푸시를 받을 수 없어요')
      const options = {
        apiKey: config.apiKey,
        projectId: config.projectId,
        appId: config.appId,
        messagingSenderId: config.messagingSenderId,
      }
      const app = sdk.app.getApps()[0] ?? sdk.app.initializeApp(options)
      const messaging = sdk.messaging.getMessaging(app)
      // register·unregister가 부르기 전에 걸어 둬야 한다(register는 onRegistered가 없으면 실패한다).
      sdk.messaging.onRegistered(messaging, (fid) => {
        lastFid = fid
        toServer(() => api<{ created: boolean }>(`/api/push/devices/${encodeURIComponent(fid)}`, { method: 'PUT' }))
      })
      sdk.messaging.onUnregistered(messaging, (fid) => {
        if (lastFid === fid) lastFid = null
        toServer(() => api<void>(`/api/push/devices/${encodeURIComponent(fid)}`, { method: 'DELETE' }))
      })
      return { sdk, messaging }
    })
    client.catch(() => {
      client = null
    })
  }
  return client
}

/** 서비스 워커를 등록하고 켜질 때까지 기다린다. 처음 등록했으면 fresh */
async function registerWorker(): Promise<{ registration: ServiceWorkerRegistration; fresh: boolean }> {
  const before = await navigator.serviceWorker.getRegistration('/')
  // updateViaCache 'none': 서비스 워커 파일을 HTTP 캐시 없이 확인해 고친 파일이 바로 반영되게 한다.
  const registration = await navigator.serviceWorker.register(SW_URL, { scope: '/', updateViaCache: 'none' })
  await withTimeout(activated(registration), SW_ACTIVE_TIMEOUT_MS)
  return { registration, fresh: !before?.active }
}

// 푸시 구독(pushManager.subscribe)은 켜진(active) 서비스 워커가 있어야 한다.
function activated(registration: ServiceWorkerRegistration): Promise<void> {
  return new Promise((resolve, reject) => {
    if (registration.active) {
      resolve()
      return
    }
    const worker = registration.installing ?? registration.waiting
    if (!worker) {
      reject(new Error('서비스 워커가 없어요'))
      return
    }
    const onChange = () => {
      if (worker.state === 'activated') {
        worker.removeEventListener('statechange', onChange)
        resolve()
      } else if (worker.state === 'redundant') {
        worker.removeEventListener('statechange', onChange)
        reject(new Error('서비스 워커를 켜지 못했어요'))
      }
    }
    worker.addEventListener('statechange', onChange)
  })
}

/**
 * 이 기기를 FCM에 등록하고 서버에 알린다(켜기, 그리고 켜 둔 사람이 앱을 열 때마다 조용히 다시).
 * register는 부를 때마다 onRegistered로 FID를 주므로 서버의 기기(로그인에 묶여 있다)도 매번 다시 만든다.
 * 알림 권한은 부르는 쪽이 먼저 받는다.
 */
export function registerPush(config: PushWebConfig): Promise<void> {
  return exclusive(async () => {
    const { sdk, messaging } = await getClient(config)
    const { registration, fresh } = await registerWorker()
    const subscription = await registration.pushManager.getSubscription()
    // VAPID 키를 바꿨으면 옛 키로 만든 구독으로는 보낼 수 없다. SDK는 있는 구독을 그대로 다시 쓰므로 브라우저 구독부터 지운다.
    const keyChanged = subscription !== null && !sameVapidKey(subscription.options.applicationServerKey, config.vapidKey)
    if (keyChanged) await subscription.unsubscribe().catch(() => false)
    // SDK는 FCM 등록을 7일에 한 번만 새로 한다. 서비스 워커를 새로 깔았거나 구독이 없거나 키가 바뀌었으면
    // 남아 있는 옛 등록을 지워서 이번 구독으로 다시 등록하게 한다. 처음 켜는 기기면 지울 것이 없어 실패해도 된다.
    if (fresh || !subscription || keyChanged) {
      await sdk.messaging.unregister(messaging).catch(() => {})
    }
    await sdk.messaging.register(messaging, { vapidKey: config.vapidKey, serviceWorkerRegistration: registration })
    await serverSync
  })
}

/**
 * 끄기: FCM 등록을 지우고(onUnregistered → 서버의 기기도 지운다) 브라우저 푸시 구독도 지운다.
 * 다시 켜면 새 구독(지금 VAPID 키)으로 등록되므로, 구독이 꼬인 기기도 껐다 켜면 고쳐진다.
 */
export function unregisterPush(config: PushWebConfig): Promise<void> {
  return exclusive(async () => {
    const { sdk, messaging } = await getClient(config)
    try {
      await release(sdk, messaging)
    } finally {
      await unsubscribeBrowser()
    }
  })
}

// 실패해도 다음에 켤 때 구독을 다시 확인하므로 넘어간다.
async function unsubscribeBrowser() {
  try {
    const registration = await navigator.serviceWorker.getRegistration('/')
    const subscription = await registration?.pushManager.getSubscription()
    await subscription?.unsubscribe()
  } catch {
    // 무시
  }
}

async function release(sdk: Sdk, messaging: Messaging) {
  try {
    await sdk.messaging.unregister(messaging)
  } catch (error) {
    // FCM에서 지우지 못했어도 서버에서 기기를 지우면 더는 보내지 않는다.
    const fid = lastFid
    if (!fid) throw error
    lastFid = null
    toServer(() => api<void>(`/api/push/devices/${encodeURIComponent(fid)}`, { method: 'DELETE' }))
  }
  await serverSync
}

/**
 * 로그아웃 전에 이 기기의 FCM 등록을 푼다(같은 브라우저를 쓰는 다음 사람이 알림을 받지 않게).
 * 서버의 기기는 로그아웃(세션 삭제)과 함께 지워지므로, 이 페이지에서 푸시를 쓰지 않았으면 아무것도 하지 않는다.
 * 1.5초 안에 끝나지 않거나 실패해도 로그아웃을 막지 않는다(절대 throw하지 않는다).
 */
export async function releaseOnLogout(): Promise<void> {
  const current = client
  // 이 페이지에서 FCM에 등록하지 않았으면(lastFid 없음) 풀 것이 없다.
  if (!current || !lastFid) return
  try {
    await withTimeout(
      exclusive(async () => {
        const { sdk, messaging } = await current
        await release(sdk, messaging)
      }),
      LOGOUT_TIMEOUT_MS,
    )
  } catch {
    // 로그아웃은 그대로 한다.
  }
}
