import { guestbookKeys } from '../queries/guestbook.ts'
import { letterKeys } from '../queries/letters.ts'

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
    default:
      return []
  }
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

/** 알림 클릭으로 옮겨 갈 경로. 같은 출처의 경로(/로 시작, //는 아님)만 받는다. */
export function internalPath(url: unknown): string | null {
  return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\') ? url : null
}
