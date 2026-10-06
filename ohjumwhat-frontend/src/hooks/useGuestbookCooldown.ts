import { useSyncExternalStore } from 'react'
import { cooldownSecondsLeft, GUESTBOOK_COOLDOWN_MS } from '../lib/guestbook.ts'

// 서버가 모든 방명록을 합쳐 5초에 한 번만 받으므로(429), 대기 시간도 앱 전체에 하나만 둔다.
// 다른 사람 방명록으로 옮겨 가도 같은 대기가 이어진다.
let until = 0
let secondsLeft = 0
let timer: ReturnType<typeof setTimeout> | undefined
const listeners = new Set<() => void>()

function update() {
  const next = cooldownSecondsLeft(until, Date.now())
  if (next === secondsLeft) return
  secondsLeft = next
  listeners.forEach((listener) => listener())
}

/** 남은 초가 바뀌는 순간(1초 경계)에 맞춰 다시 센다. 대기가 끝나면 멈춘다. */
function schedule() {
  clearTimeout(timer)
  const remaining = until - Date.now()
  if (remaining <= 0) return
  timer = setTimeout(() => {
    update()
    schedule()
  }, remaining % 1000 || 1000)
}

/** 방명록을 남긴 뒤(또는 서버가 429로 거절한 뒤) 5초 대기를 시작한다. */
export function startGuestbookCooldown() {
  until = Date.now() + GUESTBOOK_COOLDOWN_MS
  update()
  schedule()
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** 다시 남길 수 있을 때까지 남은 초(0이면 지금 쓸 수 있다). */
export function useGuestbookCooldown(): number {
  return useSyncExternalStore(
    subscribe,
    () => secondsLeft,
    () => 0,
  )
}
