import { guestbookKeys } from '../queries/guestbook.ts'
import { letterKeys } from '../queries/letters.ts'
import { meQueryKey } from '../queries/me.ts'
import { sanctionKeys } from '../queries/sanctions.ts'

/**
 * 이 기기에서 웹 푸시를 켤 수 있는지(마이페이지 「알림」 카드의 안내).
 * - ios-install: 아이폰·아이패드의 브라우저 탭. 홈 화면에 추가한 오점왓(iOS 16.4 이상)에서만 푸시를 받는다
 * - ios-inapp: 아이폰·아이패드의 인앱 브라우저(카카오톡 등). 먼저 Safari로 열어야 홈 화면에 추가할 수 있다
 * - denied: 브라우저에서 알림 권한을 거부했다
 * - unsupported: 알림·서비스 워커·푸시 API가 없다(안드로이드 인앱 브라우저 포함)
 */
export type PushSupport = 'unsupported' | 'ios-install' | 'ios-inapp' | 'denied' | 'available'

/** pushSupport가 보는 브라우저 환경. 실제 값은 readPushEnv()가 읽는다. */
export type PushEnv = {
  userAgent: string
  platform: string
  maxTouchPoints: number
  /** 홈 화면 앱으로 열렸다(display-mode: standalone 또는 iOS navigator.standalone) */
  standalone: boolean
  notification: boolean
  serviceWorker: boolean
  pushManager: boolean
  /** Notification.permission. Notification이 없으면 null */
  permission: NotificationPermission | null
}

// 카카오톡·네이버·인스타그램·페이스북·라인·밴드·에브리타임의 인앱 브라우저, 안드로이드 WebView(; wv)
const IN_APP = /KAKAOTALK|NAVER\(inapp|DaumApps|Instagram|FBAN|FBAV|FB_IAB|\bLine\/|BAND\/|everytimeApp|; wv\)/

/**
 * 아이폰·아이패드인지. iPadOS는 데스크톱 Safari처럼 MacIntel로 보이므로 터치 지점으로 가린다
 * (크롬·파이어폭스 엔진이나 안드로이드 UA는 iOS일 수 없다: 맥 크롬의 기기 흉내 등).
 */
export function isIos(env: Pick<PushEnv, 'userAgent' | 'platform' | 'maxTouchPoints'>): boolean {
  if (/iPhone|iPad|iPod/.test(env.userAgent)) return true
  return env.platform === 'MacIntel' && env.maxTouchPoints > 1 && !/Android|\b(Chrome|Chromium|Firefox)\//.test(env.userAgent)
}

export function pushSupport(env: PushEnv): PushSupport {
  const inApp = IN_APP.test(env.userAgent)
  if (isIos(env) && !env.standalone) return inApp ? 'ios-inapp' : 'ios-install'
  if (inApp || !env.notification || !env.serviceWorker || !env.pushManager) return 'unsupported'
  if (env.permission === 'denied') return 'denied'
  return 'available'
}

/** 지금 브라우저의 PushEnv(화면에서만 부른다) */
export function readPushEnv(): PushEnv {
  const notification = typeof Notification !== 'undefined'
  return {
    userAgent: navigator.userAgent,
    platform: navigator.platform,
    maxTouchPoints: navigator.maxTouchPoints ?? 0,
    standalone:
      window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true,
    notification,
    serviceWorker: 'serviceWorker' in navigator,
    pushManager: 'PushManager' in window,
    permission: notification ? Notification.permission : null,
  }
}

// 켜고 끄는 설정은 사람마다 따로 둔다. 같은 브라우저에서 다른 계정이 로그인해도 알림이 저절로 켜지지 않게.
function settingKey(userId: number) {
  return `ohjumwhat.push.${userId}`
}

/** 이 사람이 이 브라우저에서 알림을 켰는지. 저장소를 못 쓰면 끈 것으로 본다. */
export function readPushSetting(userId: number): boolean {
  try {
    return localStorage.getItem(settingKey(userId)) === 'on'
  } catch {
    return false
  }
}

export function writePushSetting(userId: number, on: boolean) {
  try {
    if (on) localStorage.setItem(settingKey(userId), 'on')
    else localStorage.removeItem(settingKey(userId))
  } catch {
    // 저장하지 못하면 다음에 열 때 꺼진 것으로 보인다.
  }
}

/** 푸시가 오면(열린 창이 있을 때) 다시 받을 쿼리. kind는 서버 PushKind */
export function pushInvalidations(kind: unknown): readonly (readonly unknown[])[] {
  switch (kind) {
    case 'GUESTBOOK':
    case 'GUESTBOOK_RESTRICTED':
      // 새 방명록 점·배지와 목록, 글 제한 경고 안내
      return [guestbookKeys.all]
    case 'LETTER':
      return [letterKeys.unread, letterKeys.box('RECEIVED')]
    case 'SANCTION':
      // 막힌 자리(내 정보의 제재)와 안내 창
      return [meQueryKey, sanctionKeys.alerts]
    case 'REPORT_RESULT':
      // 사람 신고의 처리 결과 창(제재 안내와 같은 응답에 온다)
      return [sanctionKeys.alerts]
    default:
      return []
  }
}

/**
 * 브라우저 푸시 구독의 applicationServerKey가 지금 VAPID 키(base64url)와 같은지.
 * 키를 바꾸면 옛 구독으로는 보낼 수 없어서 구독을 새로 만들어야 한다. 브라우저가 키를 알려 주지 않거나 키를 읽을 수 없으면 같다고 본다.
 */
export function sameVapidKey(applicationServerKey: ArrayBuffer | null | undefined, vapidKey: string): boolean {
  if (!applicationServerKey) return true
  let expected: Uint8Array
  try {
    const base64 = vapidKey.replace(/-/g, '+').replace(/_/g, '/')
    expected = Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) => c.charCodeAt(0))
  } catch {
    return true
  }
  const actual = new Uint8Array(applicationServerKey)
  return actual.length === expected.length && actual.every((byte, i) => byte === expected[i])
}

/** ms 안에 끝나지 않으면 실패한다(원래 작업은 멈추지 않는다). */
export function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${ms}ms 안에 끝나지 않았어요`)), ms)
    promise.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (error: unknown) => {
        clearTimeout(timer)
        reject(error)
      },
    )
  })
}

/**
 * 알림 클릭으로 옮겨 갈 경로. 같은 출처의 경로만 받는다(서비스 워커의 internalPath와 같은 규칙).
 * 글자로만 보면 URL 해석이 지우는 탭·줄바꿈("/\t/evil.example" → //evil.example)을 놓치므로 실제로 해석한 출처를 비교한다.
 */
export function internalPath(url: unknown, origin: string = window.location.origin): string | null {
  if (typeof url !== 'string' || !url) return null
  try {
    const target = new URL(url, origin)
    return target.origin === origin ? target.pathname + target.search + target.hash : null
  } catch {
    return null
  }
}
