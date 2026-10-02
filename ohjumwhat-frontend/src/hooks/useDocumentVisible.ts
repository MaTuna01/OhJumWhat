import { useSyncExternalStore } from 'react'

/** 탭이 화면에 보이는지. 숨긴 탭에서 연결·폴링을 멈출 때 쓴다. */
export function useDocumentVisible(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      document.addEventListener('visibilitychange', onChange)
      return () => document.removeEventListener('visibilitychange', onChange)
    },
    () => document.visibilityState === 'visible',
    () => true,
  )
}
