import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { internalPath, pushInvalidations, pushSupport, readPushEnv, readPushSetting } from '../lib/push.ts'
import { registerPush } from '../lib/pushClient.ts'
import { useClientConfig } from '../queries/config.ts'
import { useMe } from '../queries/me.ts'

/**
 * 앱 전체(AppLayout)에서 웹 푸시를 맞춘다.
 * - 이 사람이 이 브라우저에서 알림을 켜 두었고 권한이 있으면 앱을 열 때 조용히 다시 등록한다
 *   (서버의 기기는 로그인에 묶여 있어 다시 로그인하면 새로 만들어야 한다).
 * - 서비스 워커가 알린 푸시(ohjumwhat:push)로 방명록·쪽지를 다시 받고, 알림을 누르면(ohjumwhat:navigate) 그 화면으로 옮긴다.
 */
export function usePushSync() {
  const { data: config } = useClientConfig()
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const push = config?.push ?? null
  const userId = me?.id ?? null

  useEffect(() => {
    if (!push || userId === null || !readPushSetting(userId)) return
    const env = readPushEnv()
    if (pushSupport(env) !== 'available' || env.permission !== 'granted') return
    // 실패하면 다음에 앱을 열 때 다시 한다. 마이페이지의 스위치는 켜진 채로 둔다.
    registerPush(push).catch(() => {})
  }, [push, userId])

  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    const container = navigator.serviceWorker
    const onMessage = (event: MessageEvent) => {
      if (event.origin && event.origin !== window.location.origin) return
      const data: unknown = event.data
      if (!data || typeof data !== 'object' || !('type' in data)) return
      if (data.type === 'ohjumwhat:push' && 'kind' in data) {
        for (const queryKey of pushInvalidations(data.kind)) queryClient.invalidateQueries({ queryKey })
      } else if (data.type === 'ohjumwhat:navigate' && 'url' in data) {
        const url = internalPath(data.url)
        if (url) navigate(url)
      }
    }
    container.addEventListener('message', onMessage)
    return () => container.removeEventListener('message', onMessage)
  }, [queryClient, navigate])
}
