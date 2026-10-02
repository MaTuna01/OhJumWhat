import { useSyncExternalStore } from 'react'

/** Tailwind lg(1024px) 이상: 데스크톱 2단 레이아웃 */
export const DESKTOP_QUERY = '(min-width: 64rem)'

/** 미디어 쿼리가 맞는지. 화면 크기가 바뀌면 다시 그린다(한 곳에만 마운트해야 하는 지도 같은 요소용). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const mql = window.matchMedia(query)
      mql.addEventListener('change', onChange)
      return () => mql.removeEventListener('change', onChange)
    },
    () => window.matchMedia(query).matches,
    () => false,
  )
}
