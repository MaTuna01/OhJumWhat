import { afterEach, describe, expect, it, vi } from 'vitest'
import { guestbookKeys } from '../queries/guestbook.ts'
import { letterKeys } from '../queries/letters.ts'
import {
  type PushEnv,
  type PushSupport,
  internalPath,
  pushInvalidations,
  pushSupport,
  readPushSetting,
  sameVapidKey,
  withTimeout,
  writePushSetting,
} from './push.ts'

const UA = {
  iphoneSafari: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1',
  iphoneChrome: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/141.0.7390.41 Mobile/15E148 Safari/604.1',
  iphoneKakao: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 25.8.0',
  iphoneNaver: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 NAVER(inapp; search; 2000; 12.15.4)',
  iphoneInstagram: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 400.0.0.0.0',
  // iPadOS는 데스크톱 Safari와 같은 UA를 쓴다.
  macSafari: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Safari/605.1.15',
  macChrome: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36',
  windowsEdge: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0',
  firefox: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0',
  androidChrome: 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36',
  androidKakao: 'Mozilla/5.0 (Linux; Android 14; SM-S921N Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36;KAKAOTALK 2511420',
  androidWebView: 'Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36',
}

// 데스크톱 크롬, 권한 묻기 전
const base: PushEnv = {
  userAgent: UA.macChrome,
  platform: 'MacIntel',
  maxTouchPoints: 0,
  standalone: false,
  notification: true,
  serviceWorker: true,
  pushManager: true,
  permission: 'default',
}

// iOS Safari 탭에는 Notification·PushManager가 없다(홈 화면 앱에만 있다).
const iphoneTab: Partial<PushEnv> = { userAgent: UA.iphoneSafari, platform: 'iPhone', maxTouchPoints: 5, notification: false, pushManager: false, permission: null }

describe('pushSupport', () => {
  const cases: [string, Partial<PushEnv>, PushSupport][] = [
    ['데스크톱 크롬', {}, 'available'],
    ['데스크톱 크롬, 이미 허용', { permission: 'granted' }, 'available'],
    ['데스크톱 크롬, 거부함', { permission: 'denied' }, 'denied'],
    ['윈도 엣지', { userAgent: UA.windowsEdge, platform: 'Win32' }, 'available'],
    ['파이어폭스', { userAgent: UA.firefox, platform: 'Win32' }, 'available'],
    ['파이어폭스 사생활 보호 창(서비스 워커 없음)', { userAgent: UA.firefox, platform: 'Win32', serviceWorker: false }, 'unsupported'],
    ['macOS Safari', { userAgent: UA.macSafari }, 'available'],
    ['안드로이드 크롬', { userAgent: UA.androidChrome, platform: 'Linux armv81', maxTouchPoints: 5 }, 'available'],
    ['안드로이드 카카오톡 인앱', { userAgent: UA.androidKakao, platform: 'Linux armv81', maxTouchPoints: 5 }, 'unsupported'],
    ['안드로이드 WebView', { userAgent: UA.androidWebView, platform: 'Linux armv81', maxTouchPoints: 5 }, 'unsupported'],
    ['알림 API가 없는 브라우저', { notification: false, permission: null }, 'unsupported'],
    ['푸시 API가 없는 브라우저', { pushManager: false }, 'unsupported'],
    ['아이폰 Safari 탭', iphoneTab, 'ios-install'],
    ['아이폰 크롬 탭', { ...iphoneTab, userAgent: UA.iphoneChrome }, 'ios-install'],
    ['아이폰 카카오톡 인앱', { ...iphoneTab, userAgent: UA.iphoneKakao }, 'ios-inapp'],
    ['아이폰 네이버 인앱', { ...iphoneTab, userAgent: UA.iphoneNaver }, 'ios-inapp'],
    ['아이폰 인스타그램 인앱', { ...iphoneTab, userAgent: UA.iphoneInstagram }, 'ios-inapp'],
    ['아이패드(MacIntel + 터치) Safari 탭', { userAgent: UA.macSafari, platform: 'MacIntel', maxTouchPoints: 5, notification: false, pushManager: false, permission: null }, 'ios-install'],
    ['맥 크롬의 안드로이드 기기 흉내(MacIntel + 터치)는 iOS가 아니다', { userAgent: UA.androidChrome, platform: 'MacIntel', maxTouchPoints: 5 }, 'available'],
    ['터치스크린 맥 크롬(MacIntel + 터치)', { userAgent: UA.macChrome, platform: 'MacIntel', maxTouchPoints: 10 }, 'available'],
    ['아이폰 홈 화면 앱(iOS 16.4 이상)', { ...iphoneTab, standalone: true, notification: true, pushManager: true, permission: 'default' }, 'available'],
    ['아이폰 홈 화면 앱, 거부함', { ...iphoneTab, standalone: true, notification: true, pushManager: true, permission: 'denied' }, 'denied'],
    ['아이패드 홈 화면 앱', { userAgent: UA.macSafari, maxTouchPoints: 5, standalone: true }, 'available'],
    ['아이폰 홈 화면 앱(iOS 16.3 이하, 푸시 없음)', { ...iphoneTab, standalone: true }, 'unsupported'],
    ['아이폰 Safari 탭은 권한이 거부돼 있어도 홈 화면 안내', { ...iphoneTab, notification: true, pushManager: true, permission: 'denied' }, 'ios-install'],
  ]

  it.each(cases)('%s', (_, env, expected) => {
    expect(pushSupport({ ...base, ...env })).toBe(expected)
  })
})

describe('알림 설정(사람별)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function stubStorage() {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
      removeItem: (key: string) => store.delete(key),
    })
    return store
  }

  it('켠 사람만 켜진 것으로 본다(같은 브라우저의 다른 계정은 꺼져 있다)', () => {
    stubStorage()
    expect(readPushSetting(1)).toBe(false)
    writePushSetting(1, true)
    expect(readPushSetting(1)).toBe(true)
    expect(readPushSetting(2)).toBe(false)
    writePushSetting(1, false)
    expect(readPushSetting(1)).toBe(false)
  })

  it('저장소를 못 쓰면 꺼진 것으로 보고 오류를 내지 않는다', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('SecurityError')
      },
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
      removeItem: () => {
        throw new Error('SecurityError')
      },
    })
    expect(() => writePushSetting(1, true)).not.toThrow()
    expect(() => writePushSetting(1, false)).not.toThrow()
    expect(readPushSetting(1)).toBe(false)
  })
})

describe('pushInvalidations', () => {
  it('방명록 알림은 방명록 점·목록·경고를, 쪽지 알림은 안 읽은 수와 받은 쪽지함을 다시 받는다', () => {
    expect(pushInvalidations('GUESTBOOK')).toEqual([guestbookKeys.all])
    expect(pushInvalidations('GUESTBOOK_RESTRICTED')).toEqual([guestbookKeys.all])
    expect(pushInvalidations('LETTER')).toEqual([letterKeys.unread, letterKeys.box('RECEIVED')])
  })

  it('모르는 종류는 아무것도 다시 받지 않는다', () => {
    expect(pushInvalidations('UNKNOWN')).toEqual([])
    expect(pushInvalidations(undefined)).toEqual([])
  })
})

describe('internalPath', () => {
  const origin = 'https://www.ohjumwhat.cloud'

  it('같은 출처의 경로만 받는다', () => {
    expect(internalPath('/me#guestbook', origin)).toBe('/me#guestbook')
    expect(internalPath('/letters?box=sent', origin)).toBe('/letters?box=sent')
    expect(internalPath(`${origin}/letters`, origin)).toBe('/letters')
  })

  it('URL 해석이 다른 출처로 바꾸는 주소는 거절한다(탭·줄바꿈·역슬래시·스킴 없는 주소)', () => {
    const urls = ['/\t/evil.example', '/\n/evil.example', '/\r/evil.example', '//evil.example', '/\\evil.example', 'https://evil.example', 'javascript:alert(1)', 'http://[', '', null, 1]
    for (const url of urls) {
      expect(internalPath(url, origin), String(url)).toBeNull()
    }
  })
})

describe('sameVapidKey', () => {
  // 공개 키는 65바이트(비압축 P-256 점)
  const bytes = Uint8Array.from({ length: 65 }, (_, i) => (i * 37 + 4) % 256)
  const base64url = (b: Uint8Array) => btoa(String.fromCharCode(...b)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  const vapidKey = base64url(bytes)

  it('구독의 키와 VAPID 키(base64url)를 바이트로 비교한다', () => {
    expect(vapidKey).toMatch(/[-_]/)
    expect(sameVapidKey(bytes.slice().buffer, vapidKey)).toBe(true)
    const other = bytes.slice()
    other[10] ^= 1
    expect(sameVapidKey(other.buffer, vapidKey)).toBe(false)
    expect(sameVapidKey(bytes.slice(0, 64).buffer, vapidKey)).toBe(false)
  })

  it('브라우저가 키를 알려 주지 않거나 키를 읽을 수 없으면 같다고 본다', () => {
    expect(sameVapidKey(null, vapidKey)).toBe(true)
    expect(sameVapidKey(undefined, vapidKey)).toBe(true)
    expect(sameVapidKey(bytes.slice().buffer, '%%%')).toBe(true)
  })
})

describe('withTimeout', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('시간 안에 끝나면 그 값을, 넘기면 실패를 준다', async () => {
    vi.useFakeTimers()
    await expect(withTimeout(Promise.resolve('ok'), 1500)).resolves.toBe('ok')
    await expect(withTimeout(Promise.reject(new Error('boom')), 1500)).rejects.toThrow('boom')

    const slow = withTimeout(new Promise(() => {}), 1500)
    const settled = expect(slow).rejects.toThrow()
    await vi.advanceTimersByTimeAsync(1500)
    await settled
  })
})
