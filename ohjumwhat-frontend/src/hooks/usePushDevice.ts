import { useEffect, useReducer, useState } from 'react'
import { pushSupport, readPushEnv, readPushSetting, writePushSetting } from '../lib/push.ts'
import { pushSdkSupported, registerPush, unregisterPush } from '../lib/pushClient.ts'
import type { PushWebConfig } from '../queries/config.ts'

/**
 * 마이페이지 「알림」 카드의 상태(Figma PushSettingsCard).
 * checking: Firebase가 이 브라우저를 지원하는지 확인하는 중, ios-install·ios-inapp: 아이폰·아이패드 홈 화면 안내,
 * denied: 권한 거부, unsupported: 지원 안 함, error: 켜지 못했다(다시 누를 수 있다)
 */
export type PushDeviceState = 'checking' | 'unsupported' | 'ios-install' | 'ios-inapp' | 'denied' | 'off' | 'on' | 'error'

/** 이 기기에서 알림 받기를 켜고 끈다. 설정은 사람별로 이 브라우저에 둔다. */
export function usePushDevice(userId: number, push: PushWebConfig) {
  // 권한이 바뀌면(요청 결과) 환경을 다시 읽는다.
  const [, refresh] = useReducer((n: number) => n + 1, 0)
  const [sdkSupported, setSdkSupported] = useState<boolean | null>(null)
  const [enabled, setEnabled] = useState(() => readPushSetting(userId))
  const [failed, setFailed] = useState(false)
  // 켜거나 끄는 중이면 그 목표(스위치를 먼저 그쪽으로 그린다)
  const [pending, setPending] = useState<boolean | null>(null)

  const env = readPushEnv()
  const support = pushSupport(env)

  // 켤 수 있는 기기면 카드를 그릴 때 Firebase SDK 청크를 미리 받아 지원 여부를 확인한다(켤 때 기다리지 않게).
  useEffect(() => {
    if (support !== 'available') return
    let active = true
    pushSdkSupported()
      // 청크를 받지 못했으면(네트워크) 켜 볼 수 있게 두고, 켤 때 실패하면 오류를 보여준다.
      .catch(() => true)
      .then((supported) => {
        if (active) setSdkSupported(supported)
      })
    return () => {
      active = false
    }
  }, [support])

  let state: PushDeviceState
  if (support !== 'available') state = support
  else if (sdkSupported === null) state = 'checking'
  else if (!sdkSupported) state = 'unsupported'
  else if (failed) state = 'error'
  else state = enabled && env.permission === 'granted' ? 'on' : 'off'

  const enable = async () => {
    // 첫 문장이어야 한다: 앞에 다른 await(import 포함)가 있으면 iOS Safari가 사용자 동작으로 보지 않아 권한 창이 뜨지 않는다.
    const permission = await Notification.requestPermission()
    refresh()
    setFailed(false)
    if (permission !== 'granted') return
    setPending(true)
    try {
      await registerPush(push)
      writePushSetting(userId, true)
      setEnabled(true)
    } catch {
      setFailed(true)
    } finally {
      setPending(null)
    }
  }

  const disable = async () => {
    setPending(false)
    setFailed(false)
    // 먼저 끈 것으로 저장해서, 정리가 실패해도 다음에 앱을 열 때 다시 등록하지 않게 한다.
    writePushSetting(userId, false)
    setEnabled(false)
    try {
      await unregisterPush(push)
    } catch {
      // FCM·서버 정리에 실패해도 스위치는 꺼진 채로 둔다(로그아웃하면 서버의 기기도 지워진다).
    } finally {
      setPending(null)
    }
  }

  return { state, pending, enable, disable }
}
