import { useEffect, useState } from 'react'
import { isOutdated } from '../lib/build.ts'

const CHECK_INTERVAL_MS = 5 * 60_000

/**
 * 배포 전부터 열려 있던 탭이면 true(운영 빌드에서만 확인한다).
 * 창이 다시 보일 때와 5분마다 /version.json을 읽어 지금 화면 코드의 빌드 ID와 비교하고, 한 번 다르면 더 확인하지 않는다.
 */
export function useNewBuild(): boolean {
  const [outdated, setOutdated] = useState(false)

  useEffect(() => {
    if (!import.meta.env.PROD || outdated) return
    let cancelled = false
    const check = async () => {
      if (document.hidden) return
      try {
        const response = await fetch('/version.json', { cache: 'no-store' })
        if (!response.ok) return
        const remote: unknown = await response.json()
        if (!cancelled && isOutdated(__BUILD_ID__, remote)) setOutdated(true)
      } catch {
        // 배포 중(컨테이너 교체)이거나 오프라인이면 다음에 다시 확인한다.
      }
    }
    const onVisibilityChange = () => {
      if (!document.hidden) void check()
    }
    const timer = setInterval(check, CHECK_INTERVAL_MS)
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      cancelled = true
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [outdated])

  return outdated
}
